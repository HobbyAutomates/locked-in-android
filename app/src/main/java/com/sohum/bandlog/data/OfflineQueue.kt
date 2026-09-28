package com.sohum.bandlog.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sohum.bandlog.util.Burn
import com.sohum.bandlog.util.OfflineRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val Context.offlineStore: DataStore<Preferences> by preferencesDataStore(name = "li-offline")

/**
 * v2.18 E1 offline logging (web: IndexedDB `li-offline`/`queue`; here a DataStore). Meal, workout,
 * activity and water saves go through [orQueue]: offline, or a save that failed before reaching the
 * server (an IOException), is kept on the phone and replayed oldest first when the network comes
 * back (a ConnectivityManager callback) and on every app open. The rules are util/OfflineRules.
 */
object OfflineQueue {
    private val KEY = stringPreferencesKey("queue")
    private var app: Context? = null
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** How many logs are waiting (the "3 waiting to sync" chip). */
    var count by mutableIntStateOf(0); private set
    var syncing by mutableStateOf(false); private set
    var online by mutableStateOf(true); private set
    /** Bumped after a replay saved something, so the app re-reads its lists. */
    var syncedTick by mutableIntStateOf(0); private set
    /** A queued log the server kept rejecting (dropped after MAX_TRIES), shown once. */
    var droppedNote by mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (app != null) return
        val a = context.applicationContext
        app = a
        io.launch { count = read().size }
        runCatching {
            val cm = a.getSystemService(ConnectivityManager::class.java)
            online = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { online = true; replaySoon() }
                override fun onLost(network: Network) { online = cm.activeNetwork != null }
            })
        }
    }

    private suspend fun read(): List<OfflineRules.Item> {
        val a = app ?: return emptyList()
        val text = a.offlineStore.data.first()[KEY] ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                OfflineRules.Item(o.getString("id"), o.getString("kind"), o.getString("payload"), o.getString("created_at"), o.optInt("tries", 0), o.optString("last_error").ifBlank { null })
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun write(items: List<OfflineRules.Item>) {
        val a = app ?: return
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("payload", it.payload).put("created_at", it.createdAt).put("tries", it.tries).put("last_error", it.lastError ?: JSONObject.NULL)) }
        a.offlineStore.edit { it[KEY] = arr.toString() }
        count = items.size
    }

    suspend fun items(): List<OfflineRules.Item> = OfflineRules.ordered(read())

    private suspend fun add(kind: String, payload: JSONObject) = lock.withLock {
        payload.put("uid", Session.userId ?: "")
        write(OfflineRules.enqueue(read(), kind, payload.toString(), java.time.Instant.now().toString()))
    }

    /**
     * Runs [block]; when the phone is offline, or it fails with a network error, queues [payload]
     * instead and returns null. Server errors are rethrown (they'd fail again). [queueable] = false
     * (edits) just runs the block.
     */
    suspend fun <T> orQueue(kind: String, payload: () -> JSONObject, queueable: Boolean = true, block: suspend () -> T): T? {
        if (!queueable || app == null) return block()
        if (!online) { add(kind, payload()); return null }
        return try { block() } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || !OfflineRules.isNetworkError(e)) throw e
            add(kind, payload()); null
        }
    }

    fun queuedText(): String = SocialStore.t("sync.queued")

    fun replaySoon() { io.launch { replay() } }

    /** Replays oldest first; stops at the first network failure. Returns how many were saved. */
    suspend fun replay(): Int {
        if (app == null || !Session.signedIn) return 0
        if (!lock.tryLock()) return 0
        var saved = 0
        try {
            var queue = OfflineRules.ordered(read())
            if (queue.isEmpty()) return 0
            syncing = true
            val me = Session.userId
            for (item in queue.toList()) {
                val payload = runCatching { JSONObject(item.payload) }.getOrNull()
                // A log queued under another account (signed out and back in as someone else) is dropped.
                if (payload == null || payload.optString("uid").let { it.isNotBlank() && it != me }) {
                    queue = queue.filter { it.id != item.id }; write(queue); continue
                }
                val outcome = try { send(item.kind, payload); saved++; OfflineRules.Outcome.OK } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (e is AuthException) break
                    if (OfflineRules.isNetworkError(e)) OfflineRules.Outcome.NETWORK else OfflineRules.Outcome.ERROR.also { android.util.Log.w("LockedIn", "Offline replay failed", e) }
                }
                val r = OfflineRules.afterAttempt(queue, item.id, outcome, null)
                queue = r.queue
                write(queue)
                r.dropped?.let { droppedNote = "Couldn't sync “${OfflineRules.label(it)}”. Please log it again." }
                if (r.stop) { online = false; break }
            }
        } finally {
            syncing = false
            lock.unlock()
            if (saved > 0) syncedTick++
        }
        return saved
    }

    private suspend fun send(kind: String, p: JSONObject) {
        fun s(k: String): String? = if (!p.has(k) || p.isNull(k)) null else p.optString(k).ifBlank { null }
        val date = s("date") ?: com.sohum.bandlog.util.Dates.today()
        when (kind) {
            "water" -> Api.logWater(date, p.optInt("ml", 250), s("vessel"))
            "meal" -> {
                val arr = p.optJSONArray("items") ?: JSONArray()
                val items = (0 until arr.length()).map { MealItem.from(arr.getJSONObject(it)) }
                Api.saveMeal(date, s("raw_text") ?: "Meal", items, s("photo_path"), s("meal_type"))
            }
            "exercise" -> Api.saveExercise(
                date, s("activity_code"), s("name") ?: "Activity", p.optInt("minutes", 30), s("intensity") ?: "medium", p.optDouble("kcal", 0.0), s("source") ?: "manual", s("note").orEmpty(),
                Api.ExerciseExtras(s("started_at"), p.optInt("intensity_pct", -1).takeIf { it >= 0 }, p.optDouble("distance_km").takeIf { !it.isNaN() }, p.optInt("steps", -1).takeIf { it >= 0 }),
            )
            "workout" -> {
                val muscles = p.optJSONArray("muscles")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                val lifts = Lift.list(p.opt("lifts")).ifEmpty { null }
                val band = s("band") ?: "Medium"
                val minutes = if (p.isNull("minutes") || !p.has("minutes")) null else p.optInt("minutes")
                val kind2 = s("kind") ?: Workout.BANDS
                val exercises = s("exercises").orEmpty()
                val wid = Api.saveWorkout(null, date, muscles, band, p.optDouble("kg").takeIf { !it.isNaN() }, minutes, exercises, s("notes").orEmpty(), kind2, lifts)
                // The workout's calories-burned row, as the online save writes it.
                runCatching {
                    val mins = (minutes ?: 30).coerceAtLeast(1)
                    val w = p.optDouble("weight_kg").takeIf { !it.isNaN() }
                    val burn = p.optJSONObject("burn")
                    if (burn != null) Api.saveExercise(date, burn.optString("code").ifBlank { null }, burn.optString("name").take(120), mins, burn.optString("intensity", "medium"), burn.optDouble("kcal", 0.0), "workout", wid)
                    else {
                        val names = lifts.orEmpty().joinToString(", ") { it.name }
                        when (kind2) {
                            Workout.BANDS -> Api.saveExercise(date, Burn.bandCode(band), ("Bands: " + muscles.joinToString(", ")).take(120), mins, Burn.bandIntensity(band), Burn.bandKcal(band, w, mins), "workout", wid)
                            "bodyweight" -> Api.saveExercise(date, null, ("Bodyweight: " + names.ifBlank { "workout" }).take(120), mins, "medium", Burn.kcal(Burn.BODYWEIGHT_MET, w, mins), "workout", wid)
                            "cardio" -> Api.saveExercise(date, null, exercises.ifBlank { "Cardio" }.take(120), mins, "medium", Burn.kcal(7.0, w, mins), "workout", wid)
                            "sport" -> Api.saveExercise(date, null, exercises.ifBlank { "Sport" }.take(120), mins, "medium", Burn.kcal(6.0, w, mins), "workout", wid)
                            "yoga" -> Api.saveExercise(date, null, exercises.ifBlank { "Yoga" }.take(120), mins, "medium", Burn.kcal(2.5, w, mins), "workout", wid)
                            else -> Api.saveExercise(date, null, ("Gym: " + names.ifBlank { "workout" }).take(120), mins, "medium", Burn.kcal(Burn.GYM_MET, w, mins), "workout", wid)
                        }
                    }
                }
            }
        }
    }

    // ---- payload builders (the save call's arguments) ----

    fun mealPayload(date: String, raw: String, items: List<MealItem>, photoPath: String?, mealType: String?): JSONObject =
        JSONObject().put("date", date).put("raw_text", raw).put("photo_path", photoPath ?: JSONObject.NULL).put("meal_type", mealType ?: JSONObject.NULL)
            .put("items", JSONArray().apply { items.forEach { put(it.toJson("", "", extras = true)) } })

    fun waterPayload(date: String, ml: Int, vessel: String?): JSONObject = JSONObject().put("date", date).put("ml", ml).put("vessel", vessel ?: JSONObject.NULL)

    fun exercisePayload(date: String, code: String?, name: String, minutes: Int, intensity: String, kcal: Double, source: String, note: String, extras: Api.ExerciseExtras): JSONObject =
        JSONObject().put("date", date).put("activity_code", code ?: JSONObject.NULL).put("name", name).put("minutes", minutes).put("intensity", intensity).put("kcal", kcal)
            .put("source", source).put("note", note).put("started_at", extras.startedAt ?: JSONObject.NULL)
            .put("intensity_pct", extras.intensityPct ?: JSONObject.NULL).put("distance_km", extras.distanceKm ?: JSONObject.NULL).put("steps", extras.steps ?: JSONObject.NULL)

    fun workoutPayload(
        date: String, muscles: List<String>, band: String, kg: Double?, minutes: Int?, exercises: String, notes: String, kind: String, lifts: List<Lift>?,
        burnCode: String?, burnName: String?, burnIntensity: String?, burnKcal: Double?, weightKg: Double?,
    ): JSONObject = JSONObject().put("date", date).put("muscles", JSONArray(muscles)).put("band", band).put("kg", kg ?: JSONObject.NULL)
        .put("minutes", minutes ?: JSONObject.NULL).put("exercises", exercises).put("notes", notes).put("kind", kind)
        .put("lifts", lifts?.let { Lift.toJson(it) } ?: JSONObject.NULL).put("weight_kg", weightKg ?: JSONObject.NULL)
        .put("burn", if (burnName != null && burnKcal != null) JSONObject().put("code", burnCode ?: "").put("name", burnName).put("intensity", burnIntensity ?: "medium").put("kcal", burnKcal) else JSONObject.NULL)
}
