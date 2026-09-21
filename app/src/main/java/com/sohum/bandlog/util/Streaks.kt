package com.sohum.bandlog.util

import com.sohum.bandlog.data.Workout

/** Same rules as the web app's streaks.ts so both surfaces agree. */
object Streaks {

    /** Consecutive weeks (ending this week or last) that met the weekly target. */
    fun workoutWeekStreak(dates: List<String>, target: Int): Int {
        val perWeek = dates.groupingBy { Dates.weekStart(it) }.eachCount()
        var w = Dates.weekStart(Dates.today())
        if ((perWeek[w] ?: 0) < target) w = Dates.addDays(w, -7)
        var streak = 0
        while ((perWeek[w] ?: 0) >= target) { streak++; w = Dates.addDays(w, -7) }
        return streak
    }

    /** Consecutive days with an entry, ending today or yesterday. */
    fun dayStreak(dates: Collection<String>): Int {
        val set = dates.toSet()
        val t = Dates.today()
        var cur = if (set.contains(t)) t else Dates.addDays(t, -1)
        var n = 0
        while (set.contains(cur)) { n++; cur = Dates.addDays(cur, -1) }
        return n
    }

    fun thisWeekCount(dates: List<String>): Int {
        val t = Dates.today()
        val ws = Dates.weekStart(t)
        return dates.count { it >= ws && it <= t }
    }

    data class Rest(val muscle: String, val last: String?, val days: Long)

    /** Days since each muscle was last trained, most-rested first. */
    fun restByMuscle(workouts: List<Workout>): List<Rest> {
        val t = Dates.today()
        val sorted = workouts.sortedByDescending { it.date }
        return Muscles.ALL.map { m ->
            val hit = sorted.firstOrNull { it.muscles.contains(m) }
            Rest(m, hit?.date, if (hit != null) Dates.daysBetween(hit.date, t) else Long.MAX_VALUE)
        }.sortedByDescending { it.days }
    }
}
