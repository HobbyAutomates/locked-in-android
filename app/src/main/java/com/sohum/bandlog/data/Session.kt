package com.sohum.bandlog.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The signed-in Supabase session, persisted in app-private SharedPreferences so one sign-in on
 * the phone lasts until the user signs out. Refresh tokens are rotated by [SupabaseAuth.refresh].
 */
object Session {
    private const val FILE = "bandlog_session"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    val accessToken: String? get() = prefs.getString("access_token", null)
    val refreshToken: String? get() = prefs.getString("refresh_token", null)
    val userId: String? get() = prefs.getString("user_id", null)
    val email: String? get() = prefs.getString("email", null)
    /** Epoch seconds when the access token expires. */
    val expiresAt: Long get() = prefs.getLong("expires_at", 0L)

    val signedIn: Boolean get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun save(accessToken: String, refreshToken: String, expiresIn: Long, userId: String, email: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() / 1000 + expiresIn)
            .putString("user_id", userId)
            .putString("email", email)
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
