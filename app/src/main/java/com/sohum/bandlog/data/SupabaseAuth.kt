package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AuthException(message: String) : Exception(message)

/** Supabase GoTrue over plain HTTPS: password sign-in / sign-up / refresh / sign-out. */
object SupabaseAuth {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val refreshLock = Mutex()

    val configured: Boolean get() = base.isNotBlank() && key.isNotBlank()

    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())
    private fun req(path: String) = Request.Builder().url("$base/auth/v1/$path").header("apikey", key)

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
        val r = req("token?grant_type=password").post(json(body)).build()
        client.newCall(r).execute().use { res -> storeSession(res.code, res.body?.string().orEmpty()) }
    }

    /** Returns true when the account is created AND signed in; false when email confirmation is pending. */
    suspend fun signUp(email: String, password: String, name: String = ""): Boolean = withContext(Dispatchers.IO) {
        // The signup trigger copies data.name into profiles.name; the confirmation email greets with it.
        val display = name.trim().ifBlank { com.sohum.bandlog.util.Names.nameFromEmail(email.trim()) }
        val body = JSONObject().put("email", email.trim()).put("password", password)
            .apply { if (display.isNotBlank()) put("data", JSONObject().put("name", display)) }
            .toString()
        val r = req("signup").post(json(body)).build()
        client.newCall(r).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw AuthException(errorMessage(text, res.code))
            val o = JSONObject(text)
            if (o.has("access_token")) { storeSession(res.code, text); true } else false
        }
    }

    /** Refresh if the access token expires within 60 s. Safe to call often. */
    suspend fun ensureFresh() {
        if (!Session.signedIn) return
        if (Session.expiresAt - System.currentTimeMillis() / 1000 > 60) return
        refresh()
    }

    /**
     * Rotates the refresh token. Serialised: several requests fire at once on every refresh of
     * Home, and each would otherwise spend the same (single-use) refresh token. Inside the lock we
     * re-check expiry, so callers queued behind a refresh that just succeeded return straight away.
     * The session is only cleared when GoTrue says the refresh token itself is invalid; a network
     * blip, a 5xx or a rate limit throws without logging the user out.
     */
    suspend fun refresh() = refreshLock.withLock {
        withContext(Dispatchers.IO) {
            if (Session.signedIn && Session.expiresAt - System.currentTimeMillis() / 1000 > 60) return@withContext
            val rt = Session.refreshToken ?: throw AuthException("Not signed in")
            val body = JSONObject().put("refresh_token", rt).toString()
            val r = req("token?grant_type=refresh_token").post(json(body)).build()
            client.newCall(r).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    android.util.Log.w("LockedIn", "Token refresh failed (${res.code}): ${text.take(500)}")
                    if (invalidGrant(res.code, text)) { Session.clear(); throw AuthException("Your session expired — sign in again.") }
                    throw java.io.IOException("Couldn't refresh your session (${res.code}). Check your connection and try again.")
                }
                storeSession(res.code, text)
            }
        }
    }

    /** True when GoTrue rejected the refresh token itself (revoked, reused, unknown), not a transient failure. */
    private fun invalidGrant(code: Int, text: String): Boolean {
        if (code == 401 || code == 403) return true
        if (code != 400) return false
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return true
        val err = o.optString("error") + " " + o.optString("error_code") + " " + o.optString("code") + " " + o.optString("msg") + " " + o.optString("error_description")
        val e = err.lowercase()
        return "invalid_grant" in e || "refresh_token" in e || "refresh token" in e || "session_not_found" in e || "user_not_found" in e
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val at = Session.accessToken
        Session.clear()
        if (at != null) runCatching {
            val r = req("logout?scope=local").header("Authorization", "Bearer $at").post(json("{}")).build()
            client.newCall(r).execute().close()
        }
        Unit
    }

    private fun storeSession(code: Int, text: String) {
        if (code !in 200..299) throw AuthException(errorMessage(text, code))
        val o = JSONObject(text)
        val user = o.optJSONObject("user") ?: JSONObject()
        Session.save(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresIn = o.optLong("expires_in", 3600L),
            userId = user.optString("id"),
            email = user.optString("email"),
        )
    }

    private fun errorMessage(text: String, code: Int): String = runCatching {
        val o = JSONObject(text)
        o.optString("msg").ifBlank { o.optString("error_description").ifBlank { o.optString("message").ifBlank { o.optString("error") } } }
    }.getOrNull()?.ifBlank { null } ?: "Auth error $code"
}
