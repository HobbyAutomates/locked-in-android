package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.util.Cycle
import com.sohum.bandlog.util.Festival
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ---- v2.18 coach stream models (web src/app/api/coach/{daily,supplements,insights,modes,form-check}) ----

private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }
private fun JSONObject.i(k: String): Int? = if (!has(k) || isNull(k)) null else optInt(k)
private fun JSONObject.d(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
private fun JSONArray?.objects(): List<JSONObject> = this?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

data class CheckinV(val sleepHours: Double?, val sleepQuality: Int?, val stress: Int?, val mood: Int?, val restingHr: Int?) {
    companion object { fun from(o: JSONObject?) = o?.let { CheckinV(it.d("sleep_hours"), it.i("sleep_quality"), it.i("stress"), it.i("mood"), it.i("resting_hr")) } }
}

data class RecoveryV(val score: Int, val band: String, val label: String, val advice: String) {
    companion object { fun from(o: JSONObject?) = o?.let { RecoveryV(it.optInt("score"), it.optString("band"), it.optString("label"), it.optString("advice")) } }
}

data class SupplementV(
    val id: String, val name: String, val kind: String, val dose: Double?, val unit: String, val remindAt: String?, val active: Boolean,
    val takenToday: Boolean, val current: Int, val best: Int, val due: Boolean,
) {
    companion object {
        fun from(o: JSONObject) = SupplementV(
            o.optString("id"), o.optString("name"), o.optString("kind", "custom"), o.d("dose"), o.optString("unit", "serving"), o.s("remind_at")?.take(5),
            o.optBoolean("active", true), o.optBoolean("takenToday"), o.optInt("current"), o.optInt("best"), o.optBoolean("due"),
        )
    }
}

data class SupplementsV(val available: Boolean, val items: List<SupplementV>, val allStreak: Int) {
    companion object { fun from(o: JSONObject) = SupplementsV(o.optBoolean("available", true), o.optJSONArray("items").objects().map { SupplementV.from(it) }, o.optInt("allStreak")) }
}

private fun modeFrom(o: JSONObject) = Festival.Mode(o.optString("id"), o.optString("kind"), o.optString("name"), o.optString("start_date"), o.optString("end_date"))

data class DailyV(
    val date: String, val checkinAvailable: Boolean, val checkin: CheckinV?, val summary: String, val training: String, val recovery: RecoveryV?,
    val baseKcal: Int, val kcal: Int, val bump: Int, val reasons: List<String>,
    val festivalLine: String?, val festivalActive: Festival.Mode?, val festivalUpcoming: String?,
    val cycle: Cycle.Today?, val supplements: SupplementsV?,
) {
    companion object {
        fun from(o: JSONObject): DailyV {
            val adj = o.optJSONObject("adjust") ?: JSONObject()
            val t = o.optJSONObject("targets") ?: JSONObject()
            val f = o.optJSONObject("festival") ?: JSONObject()
            val c = o.optJSONObject("cycle")?.optJSONObject("today")
            val sup = o.optJSONObject("supplements")
            return DailyV(
                o.optString("date"), o.optBoolean("checkinAvailable", false), CheckinV.from(o.optJSONObject("checkin")),
                adj.optString("summary"), adj.optString("training"), RecoveryV.from(o.optJSONObject("recovery")),
                t.optInt("base"), t.optInt("kcal"), t.optInt("bump"), t.optJSONArray("reasons").strings(),
                f.s("line"), f.optJSONObject("active")?.let { modeFrom(it) }, f.optJSONObject("upcoming")?.optJSONObject("mode")?.optString("name"),
                c?.let { Cycle.Today(it.optInt("day"), it.optString("phase"), it.optString("label"), it.optString("hunger"), it.optInt("waterMl"), it.optString("training"), it.optString("scale"), it.optInt("nextPeriodIn")) },
                sup?.takeIf { it.optBoolean("available", false) }?.let { SupplementsV.from(it) },
            )
        }
    }
}

data class WeeklyV(val weekStart: String, val text: String, val planTitle: String, val plan: String, val loggedDays: Int, val avgProtein: Double?, val workouts: Int, val workoutTarget: Int, val avgSleep: Double?)
data class CauseV(val title: String, val detail: String, val fix: String)
data class PlateauV(val days: Int, val trend: Double, val causes: List<CauseV>)
data class PartV(val label: String, val pct: Int)
data class InsightsV(
    val score: Int, val label: String, val tip: String, val parts: List<PartV>,
    val plateau: PlateauV?, val weekly: WeeklyV?,
    val whyEn: List<String>, val whyHi: List<String>, val whyOld: Int?, val whyNew: Int?,
) {
    companion object {
        fun from(o: JSONObject): InsightsV {
            val c = o.optJSONObject("consistency") ?: JSONObject()
            val p = o.optJSONObject("plateau")
            val w = o.optJSONObject("weekly")
            val y = o.optJSONObject("why")
            val f = w?.optJSONObject("facts") ?: JSONObject()
            return InsightsV(
                c.optInt("score"), c.optString("label"), c.optString("tip"), c.optJSONArray("parts").objects().map { PartV(it.optString("label"), it.optInt("pct")) },
                p?.takeIf { it.optBoolean("flat") }?.let { PlateauV(it.optInt("days"), it.optDouble("trendKgPerWeek"), it.optJSONArray("causes").objects().map { x -> CauseV(x.optString("title"), x.optString("detail"), x.optString("fix")) }) },
                w?.let { WeeklyV(it.optString("weekStart"), it.optString("text"), it.optJSONObject("plan")?.optString("title").orEmpty(), it.optJSONObject("plan")?.optString("plan").orEmpty(), f.optInt("loggedDays"), f.d("avgProtein"), f.optInt("workouts"), f.optInt("workoutTarget"), f.d("avgSleep")) },
                y?.optJSONArray("en").strings(), y?.optJSONArray("hi").strings(), y?.i("oldTarget"), y?.i("newTarget"),
            )
        }
    }
}

data class ModesV(val date: String, val festivalAvailable: Boolean, val modes: List<Festival.Mode>, val cycleAvailable: Boolean, val cycle: Cycle.Settings, val cycleToday: Cycle.Today?) {
    companion object {
        fun from(o: JSONObject): ModesV {
            val f = o.optJSONObject("festival") ?: JSONObject()
            val c = o.optJSONObject("cycle") ?: JSONObject()
            val s = c.optJSONObject("settings") ?: JSONObject()
            val settings = Cycle.Settings(s.optBoolean("enabled"), s.s("last_period_start"), s.optInt("cycle_length", 28), s.optInt("period_length", 5))
            val date = o.optString("date")
            return ModesV(date, f.optBoolean("available", false), f.optJSONArray("modes").objects().map { modeFrom(it) }, c.optBoolean("available", false), settings, if (date.isNotBlank()) Cycle.today(settings, date) else null)
        }
    }
}

/**
 * v2.18 coach stream calls. Everything goes through the web routes (same server logic as the web
 * app), bearer-authenticated like V214Api. A 404 route or `available: false` = [NotYetAvailable]
 * (not deployed / schema_v43 not applied): the cards hide.
 */
object V218Api {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())
    private fun apiBase(): String = BuildConfig.API_BASE.trimEnd('/').ifBlank { throw ApiException("API_BASE is not set in this build") }

    private suspend fun web(method: String, path: String, body: JSONObject? = null, label: String): JSONObject {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val b = Request.Builder().url("${apiBase()}/api/coach/$path").header("Authorization", "Bearer $token")
        val rb = body?.let { json(it.toString()) }
        when (method) {
            "GET" -> b.get()
            "DELETE" -> if (rb != null) b.delete(rb) else b.delete()
            "PATCH" -> b.patch(rb ?: json("{}"))
            else -> b.post(rb ?: json("{}"))
        }
        return withContext(Dispatchers.IO) {
            client.newCall(b.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val o = runCatching { JSONObject(text) }.getOrNull()
                if (!res.isSuccessful) {
                    android.util.Log.w("LockedIn", "$label failed (${res.code}) /api/coach/$path: ${text.take(300)}")
                    if (res.code == 404 || o?.optBoolean("available", true) == false) throw NotYetAvailable()
                    if (res.code == 401) throw AuthException(o?.optString("error").orEmpty().ifBlank { "Not signed in" })
                    throw ApiException(o?.optString("error").orEmpty().ifBlank { "$label failed (${res.code})" })
                }
                o ?: throw NotYetAvailable()
            }
        }
    }

    suspend fun daily(): DailyV = DailyV.from(web("GET", "daily", label = "Today"))

    /** Saves the check-in (plus Health Connect sleep minutes / resting HR when we have them). */
    suspend fun checkin(sleepHours: Double?, stress: Int?, mood: Int?, hcSleepMin: Int? = null, restingHr: Int? = null): DailyV {
        val b = JSONObject()
        b.put("sleep_hours", sleepHours ?: JSONObject.NULL).put("stress", stress ?: JSONObject.NULL).put("mood", mood ?: JSONObject.NULL)
        if (hcSleepMin != null) b.put("hc_sleep_min", hcSleepMin)
        if (restingHr != null) b.put("resting_hr", restingHr)
        return DailyV.from(web("POST", "daily", b, "Check-in"))
    }

    private fun sup(o: JSONObject): SupplementsV { if (!o.optBoolean("available", true)) throw NotYetAvailable(); return SupplementsV.from(o) }
    suspend fun supplements(): SupplementsV = sup(web("GET", "supplements", label = "Supplements"))
    suspend fun addSupplement(kind: String, name: String, dose: Double?, unit: String, remindAt: String?): SupplementsV =
        sup(web("POST", "supplements", JSONObject().put("kind", kind).put("name", name).put("dose", dose ?: JSONObject.NULL).put("unit", unit).put("remind_at", remindAt ?: JSONObject.NULL), "Add supplement"))
    suspend fun tick(id: String, taken: Boolean): SupplementsV = sup(web("PATCH", "supplements", JSONObject().put("id", id).put("taken", taken), "Tick supplement"))
    suspend fun setActive(s: SupplementV, active: Boolean): SupplementsV =
        sup(web("PATCH", "supplements", JSONObject().put("id", s.id).put("kind", s.kind).put("name", s.name).put("dose", s.dose ?: JSONObject.NULL).put("unit", s.unit).put("remind_at", s.remindAt ?: JSONObject.NULL).put("active", active), "Update supplement"))
    suspend fun removeSupplement(id: String): SupplementsV = sup(web("DELETE", "supplements?id=$id", label = "Remove supplement"))

    suspend fun insights(generate: Boolean = false): InsightsV = InsightsV.from(web("GET", if (generate) "insights?generate=1" else "insights", label = "Insights"))

    suspend fun modes(): ModesV = ModesV.from(web("GET", "modes", label = "Modes"))
    suspend fun addFestival(kind: String, name: String, start: String, end: String): ModesV =
        ModesV.from(web("POST", "modes", JSONObject().put("type", "festival").put("kind", kind).put("name", name).put("start_date", start).put("end_date", end), "Festival mode"))
    suspend fun removeFestival(id: String): ModesV = ModesV.from(web("DELETE", "modes?id=$id", label = "Festival mode"))
    suspend fun saveCycle(s: Cycle.Settings): ModesV =
        ModesV.from(web("POST", "modes", JSONObject().put("type", "cycle").put("enabled", s.enabled).put("last_period_start", s.lastPeriodStart ?: JSONObject.NULL).put("cycle_length", s.cycleLength).put("period_length", s.periodLength), "Cycle"))

    /** True when stored (false = schema_v43 not applied; the set still counted on the phone). */
    suspend fun saveFormCheck(exercise: String, reps: Int, cleanPct: Int, tips: List<String>): Boolean =
        runCatching { web("POST", "form-check", JSONObject().put("exercise", exercise).put("reps", reps).put("clean_pct", cleanPct).put("tips", JSONArray(tips)).put("platform", "android"), "Form check").optBoolean("saved", false) }.getOrDefault(false)
}
