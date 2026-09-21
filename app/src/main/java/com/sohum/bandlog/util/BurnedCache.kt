package com.sohum.bandlog.util

import android.content.Context

/**
 * Health Connect only gives us *today's* active calories cheaply, so every refresh stamps the
 * number into SharedPreferences keyed by date. The Weekly Energy chart then has a real history
 * to draw instead of a single point. Purely local — nothing here goes to Supabase.
 */
object BurnedCache {
    private const val FILE = "bandlog_burned"

    fun put(context: Context, date: String, kcal: Double) {
        if (kcal <= 0.0) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat(date, kcal.toFloat()).apply()
    }

    fun get(context: Context, date: String): Double =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(date, 0f).toDouble()

    fun forDates(context: Context, dates: List<String>): List<Double> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return dates.map { prefs.getFloat(it, 0f).toDouble() }
    }
}
