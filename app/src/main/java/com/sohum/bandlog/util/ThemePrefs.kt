package com.sohum.bandlog.util

import android.content.Context

enum class ThemeMode { AUTO, LIGHT, DARK }

/** Appearance choice from Settings; AUTO follows the phone. */
object ThemePrefs {
    private const val FILE = "bandlog_prefs"
    fun get(context: Context): ThemeMode = runCatching {
        ThemeMode.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("theme", "AUTO") ?: "AUTO")
    }.getOrDefault(ThemeMode.AUTO)

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme", mode.name).apply()
    }
}
