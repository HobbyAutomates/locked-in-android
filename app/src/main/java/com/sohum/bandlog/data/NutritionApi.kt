package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The table / column / route isn't there yet (schema_v36 or the web route not deployed): show "Coming with the next update". */
class NotYetAvailable(message: String = COMING) : Exception(message) {
    companion object { const val COMING = "Coming with the next update" }
}

/**
 * v2.13 nutrition calls: the v36 profile settings, weekly check-ins, fasting sessions, recipes, the
 * restaurant-menu scan and "move a meal". Same auth as [Api] (the user's token, RLS applies); kept
 * in its own file so the platform half can change Api.kt without conflicts. Every read of a v36
 * table turns "table / column missing" into [NotYetAvailable].
 */
object NutritionApi {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())

    private suspend fun rest(path: String): Request.Builder {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        return Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", key).header("Authorization", "Bearer $token")
            .header("Accept-Profile", "bandlog").header("Content-Profile", "bandlog")
    }

    /** PostgREST's "no such table / column" family (42P01, 42703, PGRST204/205, schema cache). */
    internal fun isMissing(code: Int, body: String): Boolean =
        code == 404 || listOf("42P01", "42703", "PGRST204", "PGRST205", "PGRST200", "schema cache", "does not exist").any { body.contains(it, ignoreCase = true) }

    private fun run(r: Request, label: String): String = client.newCall(r).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            android.util.Log.w("LockedIn", "$label failed (${res.code}) ${r.method} ${r.url.encodedPath}: ${body.take(600)}")
            if (isMissing(res.code, body)) throw NotYetAvailable()
            val msg = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } }.getOrNull().orEmpty()
            throw ApiException("$label failed (${res.code})${if (msg.isNotBlank()) ": $msg" else ""}")
        }
        body
    }

    private fun uid(): String = Session.userId ?: throw AuthException("Not signed in")

    // ---- §4 / §5 / §7 profile settings ----

    /** The v36 settings; [NutritionSettings.supported] = false while the columns are missing. */
    suspend fun settings(): NutritionSettings = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray(run(rest("profiles?select=${NutritionSettings.COLS}&limit=1").get().build(), "Load settings"))
            if (arr.length() == 0) NutritionSettings(supported = true) else NutritionSettings.from(arr.getJSONObject(0))
        } catch (e: NotYetAvailable) { NutritionSettings(supported = false) }
    }

    /** Patches v36 profile columns. Throws [NotYetAvailable] when they're missing. */
    suspend fun patchSettings(fields: JSONObject) = withContext(Dispatchers.IO) {
        run(rest("profiles?id=eq.${uid()}").header("Prefer", "return=minimal").patch(json(fields.toString())).build(), "Save setting"); Unit
    }

    // ---- §5 weekly check-ins ----

    suspend fun checkins(limit: Int = 8): List<WeeklyCheckin> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("weekly_checkins?select=*&order=week_start.desc&limit=$limit").get().build(), "Load check-ins"))
        (0 until arr.length()).map { WeeklyCheckin.from(arr.getJSONObject(it)) }
    }

    /** Stores or updates the week's check-in (unique user + week_start). */
    suspend fun upsertCheckin(c: WeeklyCheckin): WeeklyCheckin = withContext(Dispatchers.IO) {
        val body = run(
            rest("weekly_checkins?on_conflict=user_id,week_start").header("Prefer", "resolution=ignore-duplicates,return=representation")
                .post(json(c.toJson(uid()).toString())).build(),
            "Save check-in",
        )
        JSONArray(body).optJSONObject(0)?.let { WeeklyCheckin.from(it) } ?: c
    }

    suspend fun markCheckinApplied(weekStart: String, applied: Boolean) = withContext(Dispatchers.IO) {
        run(rest("weekly_checkins?week_start=eq.$weekStart").header("Prefer", "return=minimal").patch(json(JSONObject().put("applied", applied).toString())).build(), "Update check-in"); Unit
    }

    // ---- §7 fasting ----

    suspend fun fasts(limit: Int = 15): List<FastingSession> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("fasting_sessions?select=*&order=started_at.desc&limit=$limit").get().build(), "Load fasts"))
        (0 until arr.length()).mapNotNull { FastingSession.from(arr.getJSONObject(it)) }
    }

    suspend fun startFast(startedAtMs: Long, targetHours: Double): FastingSession = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("user_id", uid()).put("started_at", FastingSession.iso(startedAtMs)).put("target_hours", targetHours)
        val arr = JSONArray(run(rest("fasting_sessions").header("Prefer", "return=representation").post(json(payload.toString())).build(), "Start fast"))
        FastingSession.from(arr.getJSONObject(0)) ?: FastingSession("", startedAtMs, null, targetHours)
    }

    suspend fun endFast(id: String, endedAtMs: Long) = withContext(Dispatchers.IO) {
        run(rest("fasting_sessions?id=eq.$id").header("Prefer", "return=minimal").patch(json(JSONObject().put("ended_at", FastingSession.iso(endedAtMs)).toString())).build(), "End fast"); Unit
    }

    suspend fun deleteFast(id: String) = withContext(Dispatchers.IO) {
        run(rest("fasting_sessions?id=eq.$id").delete().build(), "Delete fast"); Unit
    }

    // ---- §8 recipes ----

    suspend fun recipes(): List<Recipe> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("recipes?select=*&order=updated_at.desc&limit=100").get().build(), "Load recipes"))
        (0 until arr.length()).map { Recipe.from(arr.getJSONObject(it)) }
    }

    suspend fun saveRecipe(r: Recipe): Recipe = withContext(Dispatchers.IO) {
        val payload = r.toJson(uid()).put("updated_at", java.time.Instant.now().toString())
        val body = if (r.id == null) run(rest("recipes").header("Prefer", "return=representation").post(json(payload.toString())).build(), "Save recipe")
        else run(rest("recipes?id=eq.${r.id}").header("Prefer", "return=representation").patch(json(payload.toString())).build(), "Save recipe")
        JSONArray(body).optJSONObject(0)?.let { Recipe.from(it) } ?: r
    }

    suspend fun deleteRecipe(id: String) = withContext(Dispatchers.IO) {
        run(rest("recipes?id=eq.$id").delete().build(), "Delete recipe"); Unit
    }

    // ---- §14 move a meal ----

    /** Changes only a meal's meal_type (the section it sits in). */
    suspend fun moveMeal(id: String, mealType: String) = withContext(Dispatchers.IO) {
        run(rest("meals?id=eq.$id").header("Prefer", "return=minimal").patch(json(JSONObject().put("meal_type", mealType).toString())).build(), "Move meal"); Unit
    }

    // ---- §10 restaurant menu scan (web route) ----

    /**
     * A menu photo → dishes with kcal / protein ranges, a confidence each and a best pick for what's
     * left today. Calls the web server's POST /api/scan-menu with the user's bearer token, the same
     * way [Api] calls /api/scan-label. A 404 (route not deployed yet) is [NotYetAvailable].
     */
    suspend fun scanMenu(jpegBase64: String, note: String, remaining: JSONObject, dietMode: String, thumbBase64: String? = null): MenuScan = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val apiBase = BuildConfig.API_BASE.trimEnd('/')
        if (apiBase.isBlank()) throw ApiException("API_BASE is not set in this build")
        val payload = JSONObject().put("image", jpegBase64).put("media_type", "image/jpeg").put("note", note)
            .put("remaining", remaining).put("diet_mode", dietMode)
        // A <= 320 px JPEG for the History list (scan-photos/<uid>/<id>.jpg), like the other scans.
        if (thumbBase64 != null) payload.put("thumb", thumbBase64)
        val r = Request.Builder().url("$apiBase/api/scan-menu").header("Authorization", "Bearer $token").post(json(payload.toString())).build()
        val c = client.newBuilder().callTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
        val body = c.newCall(r).execute().use { res ->
            val b = res.body?.string().orEmpty()
            if (res.code == 404 || res.code == 405) throw NotYetAvailable()
            if (!res.isSuccessful) {
                android.util.Log.w("LockedIn", "Menu scan failed (${res.code}): ${b.take(600)}")
                val msg = runCatching { JSONObject(b).optString("error") }.getOrNull().orEmpty()
                throw ApiException(if (msg.isNotBlank()) msg else "Menu scan failed (${res.code})")
            }
            b
        }
        // A web build without the route can still answer 200 with the Next.js HTML 404 page.
        val o = runCatching { JSONObject(body) }.getOrElse { throw NotYetAvailable() }
        MenuScan.from(o)
    }
}
