package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.util.OnbStore
import com.sohum.bandlog.util.OnboardingV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ---- v2.14 models (web docs/v214-spec.md) ----

data class CoachNote(val date: String, val text: String, val style: String, val kind: String, val createdAt: String?) {
    companion object {
        fun from(o: JSONObject) = CoachNote(o.optString("date"), o.optString("text"), o.optString("style", "balanced"), o.optString("kind", "morning"), o.optString("created_at").ifBlank { null })
    }
}

/** One coach chat message; [cards] are the tool results (`{type: "meal_logged", …}`), rendered under the bubble. */
data class CoachMessage(val id: String, val role: String, val text: String, val cards: List<JSONObject>, val safety: Boolean, val createdAt: String?) {
    val fromCoach: Boolean get() = role == "coach"

    companion object {
        fun from(o: JSONObject): CoachMessage {
            val tool = o.optJSONObject("tool")
            val cards = tool?.optJSONArray("cards")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
            return CoachMessage(
                o.optString("id").ifBlank { "m-${System.nanoTime()}" }, o.optString("role", "coach"), o.optString("text"),
                cards, tool?.optBoolean("safety", false) == true, o.optString("created_at").ifBlank { null },
            )
        }
    }
}

/** What the coach knows. kept = false is a "Learned: … Keep / Forget" proposal. */
data class CoachMemory(val id: String, val kind: String, val text: String, val source: String, val pinned: Boolean, val kept: Boolean, val createdAt: String?) {
    companion object {
        fun from(o: JSONObject) = CoachMemory(
            o.optString("id"), o.optString("kind", "life"), o.optString("text"), o.optString("source", "chat"),
            o.optBoolean("pinned", false), o.optBoolean("kept", true), o.optString("created_at").ifBlank { null },
        )
    }
}

data class MemoryState(val remember: Boolean, val memories: List<CoachMemory>)

/** GET /api/coach/note: today's morning note, the Sunday roast and the 8 pm nudge (each may be null). */
data class NoteBundle(val note: CoachNote?, val roast: CoachNote?, val evening: CoachNote?, val noteTime: String?) {
    /** What Home shows, the roast first (the evening nudge replaces the morning note). */
    val shown: List<CoachNote> get() = listOfNotNull(roast, evening ?: note)
}

/** GET /api/coach/chat. */
data class ChatState(val style: String, val remember: Boolean, val teen: Boolean, val messages: List<CoachMessage>)

data class ChatReply(val user: CoachMessage?, val reply: CoachMessage?, val learned: List<CoachMemory>, val safety: Boolean)

/** One `my_buddies()` row. */
data class Buddy(
    val id: String, val partnerId: String, val partnerName: String, val partnerAvatar: String?,
    val streak: Int, val best: Int, val meToday: Boolean, val partnerToday: Boolean, val lastBothLoggedOn: String?,
) {
    companion object {
        private fun JSONObject.s(k: String) = if (isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = Buddy(
            o.optString("id"), o.optString("partner_id"), o.s("partner_name") ?: "Your buddy", o.s("partner_avatar"),
            o.optInt("streak", 0), o.optInt("best", 0), o.optBoolean("me_today", false), o.optBoolean("partner_today", false), o.s("last_both_logged_on"),
        )
    }
}

data class FinishResult(val targets: OnboardingV2.Targets?, val goalDate: String?, val v37: Boolean)

/**
 * v2.14 calls: the onboarding routes (plan preview with no auth, finish with the bearer), the
 * signed-out parse preview, the AI coach routes on the web server, and the buddy RPCs. Kept in its
 * own file like v2.13's PlatformApi / NutritionApi. Anything that isn't deployed / applied yet (a
 * 404 route, `available: false`, a missing v37 function) throws [NotYetAvailable], which the
 * screens show as "Coming with the next update".
 */
object V214Api {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val supa = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())

    private fun apiBase(): String = BuildConfig.API_BASE.trimEnd('/').ifBlank { throw ApiException("API_BASE is not set in this build") }

    /** A web-app route. [auth] adds the user's bearer token. 404 = the route isn't deployed yet. */
    private suspend fun web(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, label: String, timeoutSec: Long = 60, device: Boolean = false): String {
        val b = Request.Builder().url("${apiBase()}/api/$path")
        if (auth) {
            SupabaseAuth.ensureFresh()
            val token = Session.accessToken ?: throw AuthException("Not signed in")
            b.header("Authorization", "Bearer $token")
        }
        if (device) b.header("X-Device-Id", OnbStore.deviceId())
        val rb = body?.let { json(it.toString()) }
        when (method) {
            "GET" -> b.get()
            "DELETE" -> if (rb != null) b.delete(rb) else b.delete()
            "PATCH" -> b.patch(rb ?: json("{}"))
            else -> b.post(rb ?: json("{}"))
        }
        val c = if (timeoutSec == 60L) client else client.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).readTimeout(timeoutSec, TimeUnit.SECONDS).build()
        return withContext(Dispatchers.IO) {
            c.newCall(b.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    android.util.Log.w("LockedIn", "$label failed (${res.code}) /api/$path: ${text.take(400)}")
                    if (res.code == 404) throw NotYetAvailable()
                    if (runCatching { JSONObject(text).optBoolean("available", true) }.getOrDefault(true).not()) throw NotYetAvailable()
                    val msg = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                    if (res.code == 401 && auth) throw AuthException(msg.ifBlank { "Not signed in" })
                    throw ApiException(msg.ifBlank { "$label failed (${res.code})" })
                }
                text
            }
        }
    }

    private fun obj(text: String): JSONObject {
        val o = runCatching { JSONObject(text) }.getOrElse { throw NotYetAvailable() }
        if (o.has("available") && !o.optBoolean("available", true)) throw NotYetAvailable()
        return o
    }

    // ---- onboarding ----

    /** Signed-out first log: the same parse as signed in, no DB writes, rate-limited per device + IP. */
    suspend fun parsePreview(text: String): ParseResult {
        if (Session.signedIn) return Api.parseMeal(text)
        val o = obj(web("POST", "parse-meal", JSONObject().put("text", text).put("preview", true), auth = false, label = "Parse", device = true))
        val items = o.optJSONArray("items") ?: JSONArray()
        fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return ParseResult((0 until items.length()).map { MealItem.from(items.getJSONObject(it)) }, strings("assumptions"), strings("unparsed"), ParsedWater.from(o.optJSONObject("water")))
    }

    /** The reveal (no auth). Null when the server can't make a plan from these answers. */
    suspend fun plan(a: OnboardingV2.Answers): OnboardingV2.Plan? =
        OnboardingV2.Plan.from(obj(web("POST", "onboarding/plan", JSONObject().put("answers", a.toJson()), auth = false, label = "Plan", timeoutSec = 20)))

    /** Saves the onboarding once the account exists. [mode] "full" or "tune". */
    suspend fun finish(mode: String, a: OnboardingV2.Answers, firstLog: com.sohum.bandlog.util.OnbStore.FirstLog?): FinishResult {
        val body = JSONObject().put("mode", mode).put("answers", a.toJson())
        if (firstLog != null) body.put("first_log", firstLog.toJson())
        val o = obj(web("POST", "onboarding/finish", body, label = "Save plan"))
        val t = o.optJSONObject("targets")
        return FinishResult(
            t?.let { OnboardingV2.Targets(it.optInt("calories"), it.optInt("protein"), it.optInt("carbs"), it.optInt("fat"), it.optInt("fiber", 30)) },
            if (o.isNull("goal_date")) null else o.optString("goal_date").ifBlank { null },
            o.optBoolean("v37", false),
        )
    }

    // ---- coach ----

    suspend fun note(): NoteBundle {
        val o = obj(web("GET", "coach/note", label = "Coach note"))
        fun n(k: String) = o.optJSONObject(k)?.let { CoachNote.from(it) }?.takeIf { it.text.isNotBlank() }
        return NoteBundle(n("note"), n("roast"), n("evening"), o.optString("noteTime").ifBlank { null })
    }

    /** History, oldest first, plus the style the server used, the memory switch and the teen cap. */
    suspend fun chat(limit: Int = 60): ChatState {
        val o = obj(web("GET", "coach/chat?limit=$limit", label = "Coach chat"))
        val arr = o.optJSONArray("messages") ?: JSONArray()
        return ChatState(o.optString("style", "balanced"), o.optBoolean("remember", true), o.optBoolean("teen", false), (0 until arr.length()).map { CoachMessage.from(arr.getJSONObject(it)) })
    }

    suspend fun send(message: String, imageBase64: String? = null): ChatReply {
        val body = JSONObject().put("message", message).put("date", com.sohum.bandlog.util.Dates.today())
        if (imageBase64 != null) body.put("image", imageBase64).put("media_type", "image/jpeg")
        val o = obj(web("POST", "coach/chat", body, label = "Coach", timeoutSec = 90))
        val learned = o.optJSONArray("learned")?.let { a -> (0 until a.length()).map { CoachMemory.from(a.getJSONObject(it)) } } ?: emptyList()
        return ChatReply(o.optJSONObject("user")?.let { CoachMessage.from(it) }, o.optJSONObject("reply")?.let { CoachMessage.from(it) }, learned, o.optBoolean("safety", false))
    }

    suspend fun deleteChat() { web("DELETE", "coach/chat", label = "Delete chat") }

    /** The export file's JSON text (chat + memories). */
    suspend fun export(): String = web("GET", "coach/export", label = "Export")

    suspend fun memory(): MemoryState {
        val o = obj(web("GET", "coach/memory", label = "Coach memory"))
        val arr = o.optJSONArray("memories") ?: JSONArray()
        return MemoryState(o.optBoolean("remember", true), (0 until arr.length()).map { CoachMemory.from(arr.getJSONObject(it)) })
    }

    suspend fun addMemory(text: String, kind: String): CoachMemory? {
        val o = obj(web("POST", "coach/memory", JSONObject().put("text", text).put("kind", kind), label = "Add memory"))
        return (o.optJSONObject("memory") ?: o.takeIf { it.has("id") })?.let { CoachMemory.from(it) }
    }

    suspend fun keepMemory(id: String) { web("PATCH", "coach/memory", JSONObject().put("id", id).put("kept", true), label = "Keep memory") }

    suspend fun pinMemory(id: String, pinned: Boolean) { web("PATCH", "coach/memory", JSONObject().put("id", id).put("pinned", pinned), label = "Pin memory") }

    /** `id = "all"` forgets everything. */
    suspend fun forgetMemory(id: String) { web("DELETE", "coach/memory?id=${java.net.URLEncoder.encode(id, "UTF-8")}", label = "Forget memory") }

    // ---- buddies (Supabase RPCs, schema_v37) ----

    private suspend fun rpc(fn: String, payload: JSONObject, label: String): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val r = Request.Builder().url("$supa/rest/v1/rpc/$fn").header("apikey", key).header("Authorization", "Bearer $token")
            .header("Accept-Profile", "bandlog").header("Content-Profile", "bandlog").post(json(payload.toString())).build()
        client.newCall(r).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                android.util.Log.w("LockedIn", "$label failed (${res.code}): ${body.take(400)}")
                if (res.code == 404 || listOf("PGRST202", "42883", "42P01", "PGRST205", "Could not find the function").any { body.contains(it) }) throw NotYetAvailable()
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
                throw ApiException(msg.ifBlank { "$label failed (${res.code})" })
            }
            body
        }
    }

    suspend fun myBuddies(): List<Buddy> {
        val arr = JSONArray(rpc("my_buddies", JSONObject(), "Load buddies"))
        return (0 until arr.length()).map { Buddy.from(arr.getJSONObject(it)) }
    }

    /** The caller's open invite code (6 letters). */
    suspend fun buddyInvite(): String = rpc("buddy_invite", JSONObject(), "Invite").trim().trim('"')

    suspend fun buddyAccept(code: String): String =
        rpc("buddy_accept", JSONObject().put("invite", code.trim().uppercase()), "Accept").trim().trim('"')

    /** False when the nudge was already sent (or the buddy already logged). */
    suspend fun buddyNudge(buddyId: String): Boolean =
        rpc("buddy_nudge", JSONObject().put("buddy", buddyId), "Nudge").trim().equals("true", ignoreCase = true)

    fun buddyLink(code: String): String = "${BuildConfig.API_BASE.trimEnd('/').ifBlank { com.sohum.bandlog.ui.squad.WEB_URL }}/buddy/${code.uppercase()}"
}
