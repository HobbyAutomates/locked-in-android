package com.sohum.bandlog.util

import android.content.Context

enum class ThemeMode { AUTO, LIGHT, DARK }

/** Appearance choice from Settings; AUTO follows the phone. */
object ThemePrefs {
    private const val FILE = "bandlog_prefs"
    fun get(context: Context): ThemeMode = runCatching {
        ThemeMode.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("theme", "AUTO") ?: "AUTO")
    }.getOrDefault(ThemeMode.AUTO)

    fun burned(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("add_burned", false)
    fun setBurned(context: Context, on: Boolean) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("add_burned", on).apply() }

    /** v2.3 local fallbacks for the profile toggles (used until the profile columns exist). */
    fun rollover(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("rollover", false)
    fun setRollover(context: Context, on: Boolean) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("rollover", on).apply() }

    /** Badge / streak celebration modal after a saved workout. On by default. */
    fun celebrations(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("celebrations", true)
    fun setCelebrations(context: Context, on: Boolean) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("celebrations", on).apply() }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme", mode.name).apply()
    }
}
