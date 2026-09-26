package com.sohum.bandlog.util

import android.content.Context
import com.sohum.bandlog.data.Workout

/**
 * v2.14 "Milestone flood" (brand v1, "Ember is earned" idea 01): the whole screen turns ember only
 * for real moments — a 7 / 30 / 100-day streak, the goal weight reached, or a PR set in the last
 * 2 days. Each fires once. A port of the web's src/lib/milestones.ts (same keys and rules):
 *  - streak_7 / streak_30 / streak_100 flood only within 7 days of reaching them; older ones are
 *    marked seen silently, and showing a higher step marks the lower ones seen too;
 *  - goal_reached:<goal kg>;
 *  - pr:<exercise lower-case>:<yyyy-MM-dd> (a session beating every earlier session's best e1RM).
 * Seen keys live in profiles.milestones_seen (schema_v37) and on the device as the fallback.
 */
object Milestones {
    val STREAK_STEPS = listOf(7, 30, 100)
    const val STREAK_WINDOW = 7

    data class Milestone(val key: String, val eyebrow: String, val big: String, val line: String, val sub: String, val share: String)

    private val LINES = mapOf(7 to "One week. Locked in.", 30 to "A month of showing up.", 100 to "Triple digits. Unreal.")

    private fun num(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

    /** Everything that's true right now, most special first, plus older streak keys to mark seen silently. */
    fun current(today: String, dayStreak: Int, weightKg: Double?, goalWeightKg: Double?, goalType: String, workouts: List<Workout>): Pair<List<Milestone>, List<String>> {
        val fire = mutableListOf<Milestone>()
        val silent = mutableListOf<String>()
        if (goalWeightKg != null && weightKg != null && goalType != "maintain") {
            val hit = if (goalType == "lose") weightKg <= goalWeightKg else weightKg >= goalWeightKg
            if (hit) fire += Milestone("goal_reached:${num(goalWeightKg)}", "Goal reached", num(goalWeightKg), "kg. You said it, you did it.", "Pick your next goal when you're ready.", "goal")
        }
        val since = Dates.addDays(today, -2)
        for ((name, pt) in Training.prsBetween(workouts, since, today)) {
            fire += Milestone("pr:${name.lowercase()}:${pt.date}", "New PR · $name", num(pt.kg), "kg × ${pt.reps}. Personal record.", "Strongest you've ever been at this.", "pr")
        }
        for (n in STREAK_STEPS.reversed()) {
            if (dayStreak < n) continue
            if (dayStreak < n + STREAK_WINDOW) fire += Milestone("streak_$n", "Milestone", "$n", "days locked in.", LINES.getValue(n), "streak")
            else silent += "streak_$n"
        }
        fire.firstOrNull { it.key.startsWith("streak_") }?.let { top ->
            val topN = top.key.removePrefix("streak_").toInt()
            STREAK_STEPS.filter { it < topN }.forEach { n -> if ("streak_$n" !in silent) silent += "streak_$n" }
        }
        return fire to silent
    }

    /** The one milestone to show now (the first unseen) and the silent keys not yet seen. */
    fun next(today: String, dayStreak: Int, weightKg: Double?, goalWeightKg: Double?, goalType: String, workouts: List<Workout>, seen: Set<String>): Pair<Milestone?, List<String>> {
        val (fire, silent) = current(today, dayStreak, weightKg, goalWeightKg, goalType, workouts)
        return fire.firstOrNull { it.key !in seen } to silent.filter { it !in seen }
    }

    // ---- on-device seen keys (the fallback while profiles.milestones_seen is missing) ----

    private const val PREFS = "milestones"
    private const val SEEN = "seen"

    fun localSeen(ctx: Context): Set<String> = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(SEEN, emptySet()).orEmpty()

    fun markLocal(ctx: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putStringSet(SEEN, localSeen(ctx) + keys).apply()
    }
}
