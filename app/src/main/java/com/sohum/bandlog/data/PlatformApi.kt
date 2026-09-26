package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.util.Routines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A v36 table / column isn't there yet: the feature shows "Coming with the next update". */
class SchemaMissingException(message: String) : Exception(message)

/**
 * v2.13 platform calls (schema_v36): Pro config, the notifications inbox, push dispatch through the
 * web server, body measurements, richer progress photos, and routines. Same conventions as [Api]
 * (user token, `bandlog` schema, RLS) in its own file so the nutrition half never conflicts.
 * Anything that hits a missing v36 table or column throws [SchemaMissingException].
 */
object PlatformApi {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private const val SCHEMA = "bandlog"
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())

    private suspend fun rest(path: String): Request.Builder {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        return Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", key).header("Authorization", "Bearer $token")
            .header("Accept-Profile", SCHEMA).header("Content-Profile", SCHEMA)
    }

    /** PostgREST's "no such table / column" answers (PGRST205 / 42P01 / PGRST204 / 42703). */
    fun isMissing(code: Int, body: String): Boolean =
        "PGRST205" in body || "42P01" in body || "PGRST204" in body || "42703" in body ||
            (code == 404 && ("Could not find the table" in body || "does not exist" in body)) ||
            ("column" in body && "does not exist" in body) || ("Could not find the" in body && "column" in body)

    private fun run(r: Request, label: String): String = client.newCall(r).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            android.util.Log.w("LockedIn", "$label failed (${res.code}) ${r.method} ${r.url.encodedPath}: ${body.take(400)}")
            if (isMissing(res.code, body)) throw SchemaMissingException("$label: needs the server update")
            val msg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
            throw ApiException("$label failed (${res.code})${if (msg.isNotBlank()) ": $msg" else ""}")
        }
        body
    }

    private fun uid(): String = Session.userId ?: throw AuthException("Not signed in")

    // ---- profile extras + Pro ----

    /** The v36 profile fields, from `select=*` so a missing column never breaks the read. */
    suspend fun profileExtras(): PlatformProfile = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("profiles?select=*&limit=1").get().build(), "Load profile"))
        if (arr.length() == 0) PlatformProfile() else PlatformProfile.from(arr.getJSONObject(0))
    }

    /** `app_config.pro`; fallback ₹700 / month when the table or row is missing. */
    suspend fun proConfig(): com.sohum.bandlog.util.Pro.Config = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(run(rest("app_config?select=value&key=eq.pro").get().build(), "Load pricing"))
            com.sohum.bandlog.util.Pro.parseConfig(arr.optJSONObject(0)?.optJSONObject("value"))
        }.getOrDefault(com.sohum.bandlog.util.Pro.DEFAULT_CONFIG)
    }

    /** Protein-nudge settings on the profile; false when the v36 columns aren't there (the caller keeps them local). */
    suspend fun saveProteinNudge(on: Boolean, time: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            run(rest("profiles?id=eq.${uid()}").header("Prefer", "return=minimal")
                .patch(json(JSONObject().put("protein_nudge", on).put("protein_nudge_time", time).toString())).build(), "Save protein nudge")
        }.isSuccess
    }

    // ---- notifications inbox ----

    suspend fun inbox(limit: Int = 50): List<InboxItem> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("notifications?select=*&order=created_at.desc&limit=$limit").get().build(), "Load notifications"))
        (0 until arr.length()).map { InboxItem.from(arr.getJSONObject(it)) }
    }

    /** Unread rows from the last [days] days (the background check). */
    suspend fun unreadSince(days: Long = 3): List<InboxItem> = withContext(Dispatchers.IO) {
        val since = java.net.URLEncoder.encode(java.time.Instant.now().minusSeconds(days * 86_400).toString(), "UTF-8")
        val arr = JSONArray(run(rest("notifications?select=*&read_at=is.null&created_at=gte.$since&order=created_at.asc&limit=20").get().build(), "Load notifications"))
        (0 until arr.length()).map { InboxItem.from(arr.getJSONObject(it)) }
    }

    suspend fun markRead(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = java.time.Instant.now().toString()
        run(rest("notifications?id=in.(${ids.joinToString(",")})&read_at=is.null").header("Prefer", "return=minimal")
            .patch(json(JSONObject().put("read_at", now).toString())).build(), "Mark read")
        Unit
    }

    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        run(rest("notifications?user_id=eq.${uid()}&read_at=is.null").header("Prefer", "return=minimal")
            .patch(json(JSONObject().put("read_at", java.time.Instant.now().toString()).toString())).build(), "Mark read")
        Unit
    }

    /** Records a locally-posted protein / fasting / checkin notice in the inbox (RLS allows those kinds). */
    suspend fun insertNotice(kind: String, title: String, body: String, url: String?, pushed: Boolean = true) = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toString()
        val row = JSONObject().put("user_id", uid()).put("kind", kind).put("title", title).put("body", body).put("url", url ?: JSONObject.NULL)
        // pushed_at so the web dispatcher doesn't push the same thing to this user's other devices twice.
        if (pushed) row.put("pushed_at", now)
        run(rest("notifications").header("Prefer", "return=minimal").post(json(row.toString())).build(), "Save notification"); Unit
    }

    /**
     * After a nudge: ask the web server to push the target's pending notifications (their iPhone
     * home-screen app has no other way to hear about it). Best-effort, never throws.
     */
    suspend fun dispatchPush(targetUserId: String) = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseAuth.ensureFresh()
            val token = Session.accessToken ?: return@runCatching
            val apiBase = BuildConfig.API_BASE.trimEnd('/')
            if (apiBase.isBlank()) return@runCatching
            val r = Request.Builder().url("$apiBase/api/push/dispatch").header("Authorization", "Bearer $token")
                .post(json(JSONObject().put("userId", targetUserId).toString())).build()
            client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build().newCall(r).execute().use { res ->
                if (!res.isSuccessful) android.util.Log.i("LockedIn", "Push dispatch answered ${res.code}")
            }
        }.onFailure { android.util.Log.i("LockedIn", "Push dispatch skipped: ${it.message}") }
        Unit
    }

    // ---- body measurements ----

    suspend fun measurements(): List<BodyMeasurement> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("body_measurements?select=*&order=date.desc,created_at.desc&limit=400").get().build(), "Load measurements"))
        (0 until arr.length()).map { BodyMeasurement.from(arr.getJSONObject(it)) }
    }

    private fun measurementJson(m: BodyMeasurement): JSONObject {
        val o = JSONObject().put("date", m.date).put("note", m.note.ifBlank { JSONObject.NULL })
        BodyMeasurement.FIELDS.forEach { (k, _, _) -> o.put(k, m.values[k] ?: JSONObject.NULL) }
        return o
    }

    suspend fun saveMeasurement(m: BodyMeasurement) = withContext(Dispatchers.IO) {
        val o = measurementJson(m)
        if (m.id == null) run(rest("body_measurements").header("Prefer", "return=minimal").post(json(o.put("user_id", uid()).toString())).build(), "Save measurements")
        else run(rest("body_measurements?id=eq.${m.id}").header("Prefer", "return=minimal").patch(json(o.toString())).build(), "Update measurements")
        Unit
    }

    suspend fun deleteMeasurement(id: String) = withContext(Dispatchers.IO) {
        run(rest("body_measurements?id=eq.$id").delete().build(), "Delete measurements"); Unit
    }

    // ---- progress photos (v36 extras) ----

    suspend fun photos(): List<PhotoV2> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("progress_photos?select=*&order=date.desc,created_at.desc&limit=500").get().build(), "Load progress photos"))
        (0 until arr.length()).map { PhotoV2.from(arr.getJSONObject(it)) }
    }

    /** Uploads a ≤1024 px JPEG and records the row, with weight + pose when v36 is there. Returns the path. */
    suspend fun uploadPhoto(jpeg: ByteArray, date: String, note: String, weightKg: Double?, pose: String?): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val u = uid()
        val path = "$u/$date-${System.currentTimeMillis()}.jpg"
        val up = Request.Builder().url("$base/storage/v1/object/progress-photos/$path")
            .header("apikey", key).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(up, "Upload photo")
        val row = JSONObject().put("user_id", u).put("date", date).put("path", path).put("note", note.ifBlank { JSONObject.NULL })
        val full = JSONObject(row.toString()).put("weight_kg", weightKg ?: JSONObject.NULL).put("pose", pose ?: JSONObject.NULL)
        try { run(rest("progress_photos").header("Prefer", "return=minimal").post(json(full.toString())).build(), "Save progress photo") }
        catch (e: SchemaMissingException) { run(rest("progress_photos").header("Prefer", "return=minimal").post(json(row.toString())).build(), "Save progress photo") }
        path
    }

    /** Edits date / note (always) and weight / pose (v36). Returns false when only date + note could be saved. */
    suspend fun updatePhoto(id: String, date: String, note: String, weightKg: Double?, pose: String?): Boolean = withContext(Dispatchers.IO) {
        val base = JSONObject().put("date", date).put("note", note.ifBlank { JSONObject.NULL })
        val full = JSONObject(base.toString()).put("weight_kg", weightKg ?: JSONObject.NULL).put("pose", pose ?: JSONObject.NULL)
            .put("updated_at", java.time.Instant.now().toString())
        try {
            run(rest("progress_photos?id=eq.$id").header("Prefer", "return=minimal").patch(json(full.toString())).build(), "Update photo"); true
        } catch (e: SchemaMissingException) {
            run(rest("progress_photos?id=eq.$id").header("Prefer", "return=minimal").patch(json(base.toString())).build(), "Update photo"); false
        }
    }

    /** Deletes the row, then the Storage object (a leftover object is only logged). */
    suspend fun deletePhoto(id: String, path: String) = withContext(Dispatchers.IO) {
        run(rest("progress_photos?id=eq.$id").delete().build(), "Delete photo")
        runCatching {
            SupabaseAuth.ensureFresh()
            val token = Session.accessToken ?: return@runCatching
            val r = Request.Builder().url("$base/storage/v1/object/progress-photos/$path")
                .header("apikey", key).header("Authorization", "Bearer $token").delete().build()
            client.newCall(r).execute().use { res -> if (!res.isSuccessful) android.util.Log.w("LockedIn", "Photo object delete failed (${res.code})") }
        }
        Unit
    }

    /** Raw bytes of a private Storage object (for re-posting a progress photo to a squad). */
    suspend fun storageBytes(bucket: String, path: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseAuth.ensureFresh()
            val token = Session.accessToken ?: return@runCatching null
            val r = Request.Builder().url("$base/storage/v1/object/authenticated/$bucket/$path").header("apikey", key).header("Authorization", "Bearer $token").get().build()
            client.newCall(r).execute().use { res -> if (res.isSuccessful) res.body?.bytes() else null }
        }.getOrNull()
    }

    // ---- routines ----

    suspend fun routines(): List<Routines.Routine> = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("routines?select=*&order=updated_at.desc").get().build(), "Load routines"))
        (0 until arr.length()).map { Routines.from(arr.getJSONObject(it)) }
    }

    /** Insert or update; returns the id. Activating one clears the others first (one active per user). */
    suspend fun saveRoutine(r: Routines.Routine): String = withContext(Dispatchers.IO) {
        val u = uid()
        if (r.active) clearActive(except = r.id)
        val o = JSONObject().put("name", r.name.take(60)).put("days", Routines.daysToJson(r.days)).put("active", r.active)
            .put("updated_at", java.time.Instant.now().toString())
        if (r.id == null) {
            val created = run(rest("routines").header("Prefer", "return=representation").post(json(o.put("user_id", u).toString())).build(), "Save routine")
            JSONArray(created).getJSONObject(0).getString("id")
        } else {
            run(rest("routines?id=eq.${r.id}").header("Prefer", "return=minimal").patch(json(o.toString())).build(), "Save routine"); r.id
        }
    }

    private suspend fun clearActive(except: String?) {
        val filter = "routines?user_id=eq.${uid()}&active=is.true" + (except?.let { "&id=neq.$it" } ?: "")
        run(rest(filter).header("Prefer", "return=minimal").patch(json(JSONObject().put("active", false).toString())).build(), "Save routine")
    }

    suspend fun setActiveRoutine(id: String?) = withContext(Dispatchers.IO) {
        clearActive(except = id)
        if (id != null) run(rest("routines?id=eq.$id").header("Prefer", "return=minimal").patch(json(JSONObject().put("active", true).toString())).build(), "Activate routine")
        Unit
    }

    suspend fun deleteRoutine(id: String) = withContext(Dispatchers.IO) {
        run(rest("routines?id=eq.$id").delete().build(), "Delete routine"); Unit
    }
}
