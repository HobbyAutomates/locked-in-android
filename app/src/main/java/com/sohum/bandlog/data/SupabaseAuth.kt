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
    suspend fun signUp(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
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

    suspend fun refresh() = refreshLock.withLock {
        withContext(Dispatchers.IO) {
            val rt = Session.refreshToken ?: throw AuthException("Not signed in")
            val body = JSONObject().put("refresh_token", rt).toString()
            val r = req("token?grant_type=refresh_token").post(json(body)).build()
            client.newCall(r).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.code == 400 || res.code == 401) { Session.clear(); throw AuthException("Session expired, sign in again") }
                storeSession(res.code, text)
            }
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val at = Session.accessToken
        Session.clear()
        if (at != null) runCatching {
            val r = req("logout").header("Authorization", "Bearer $at").post(json("{}")).build()
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
