package com.sohum.bandlog.util

import android.content.Context

/**
 * v2.6 water: the reminder window + frequency, glass size and goal, mirrored locally so the alarm
 * receiver can decide without a network call, plus today's cached total (the receiver's fallback
 * when it can't reach the server) and the once-a-day goal celebration stamp.
 */
object WaterPrefs {
    private const val FILE = "bandlog_water"

    /** Never, 30 min, 1 h, 2 h, 3 h, 4 h. */
    val FREQUENCIES = listOf(0, 30, 60, 120, 180, 240)

    fun label(every: Int): String = when (every) {
        0 -> "Never"
        30 -> "Every 30 minutes"
        60 -> "Every 60 minutes"
        else -> "Every ${every / 60} hours"
    }

    data class Settings(val from: String = "09:00", val to: String = "21:00", val every: Int = 0, val glassMl: Int = 250, val goalMl: Int = 2500)

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context): Settings {
        val p = prefs(c)
        return Settings(
            p.getString("from", "09:00") ?: "09:00", p.getString("to", "21:00") ?: "21:00",
            p.getInt("every", 0), p.getInt("glass", 250), p.getInt("goal", 2500),
        )
    }

    fun save(c: Context, s: Settings) {
        prefs(c).edit().putString("from", s.from).putString("to", s.to).putInt("every", s.every)
            .putInt("glass", s.glassMl).putInt("goal", s.goalMl).apply()
    }

    /** Today's total as the app (or the notification's +1 glass) last saw it. */
    fun cacheTotal(c: Context, date: String, ml: Int) { prefs(c).edit().putString("cache_date", date).putInt("cache_ml", ml).apply() }
    fun cachedTotal(c: Context, date: String): Int? = prefs(c).let { if (it.getString("cache_date", null) == date) it.getInt("cache_ml", 0) else null }

    fun celebrated(c: Context, date: String): Boolean = prefs(c).getString("celebrated", null) == date
    fun markCelebrated(c: Context, date: String) { prefs(c).edit().putString("celebrated", date).apply() }

    fun hm(time: String): Pair<Int, Int> =
        (time.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 9) to (time.substringAfter(':', "0").take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0)

    /** "1.75 L", "1.5 L", "2 L". */
    fun litres(ml: Int): String {
        val s = String.format(java.util.Locale.US, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.')
        return "$s L"
    }
}
