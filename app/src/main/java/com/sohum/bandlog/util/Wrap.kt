package com.sohum.bandlog.util

import android.content.Context
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Workout
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The 9 pm daily wrap (mirrors the web's lib/wrap.ts): protein hit or miss against the target,
 * calories against target + burn, sessions this week, tomorrow's session (the most-rested muscle
 * groups, from [Streaks.restByMuscle]) and the day's best meal by protein. The same numbers feed the
 * 21:00 notification and the Wrap card on Home (shown 21:00–04:00).
 */
object Wrap {

    data class Result(
        val date: String,
        val protein: Int,
        val proteinTarget: Int,
        val proteinHit: Boolean,
        val calories: Int,
        val calorieBudget: Int,
        val burned: Int,
        val sessions: Int,
        val sessionTarget: Int,
        val tomorrow: String,
        val bestMeal: String?,
        val bestMealProtein: Int,
        /** "Today: 118 g protein ✓ · 1,640 kcal · 2/3 sessions — tomorrow: legs & core" */
        val line: String,
    ) {
        val isYesterday: Boolean get() = date != Dates.today()
        val shareText: String get() = "Locked In · $line"
    }

    /** Muscle groups for "tomorrow: legs & core", in tie-break order. */
    private val GROUPS = listOf(
        "legs" to listOf("Glutes", "Quads", "Hamstrings", "Calves"),
        "core" to listOf("Core"),
        "back" to listOf("Back"),
        "chest" to listOf("Chest"),
        "shoulders" to listOf("Shoulders"),
        "arms" to listOf("Biceps", "Triceps", "Forearms"),
    )

    /** 21:00 to 04:00 — the window the Wrap card is shown in. */
    fun inWindow(now: LocalTime = LocalTime.now(Dates.ZONE)): Boolean = now.hour >= 21 || now.hour < 4

    /** The day being wrapped: today, or yesterday once it is past midnight. */
    fun wrapDate(now: LocalTime = LocalTime.now(Dates.ZONE)): String = if (now.hour < 4) Dates.addDays(Dates.today(), -1) else Dates.today()

    /** The two most-rested muscle groups ("legs & core"), or "full body" with no history at all. */
    fun tomorrow(workouts: List<Workout>, date: String): String {
        val rest = Streaks.restByMuscle(workouts.filter { it.date <= date }).associate { it.muscle to it.days }
        val groups = GROUPS.mapIndexed { i, (label, muscles) -> Triple(label, muscles.minOf { rest[it] ?: Long.MAX_VALUE }, i) }
        if (groups.all { it.second == Long.MAX_VALUE }) return "full body"
        return groups.sortedWith(compareByDescending<Triple<String, Long, Int>> { it.second }.thenBy { it.third }).take(2).joinToString(" & ") { it.first }
    }

    fun compute(profile: Profile, workouts: List<Workout>, meals: List<Meal>, burned: Double, date: String = wrapDate()): Result {
        val dayMeals = meals.filter { it.date == date }
        val protein = dayMeals.sumOf { it.protein }
        val calories = dayMeals.sumOf { it.calories }
        val ws = Dates.weekStart(date)
        val sessions = workouts.filter { it.date >= ws && it.date <= date }.map { it.date }.distinct().size
        val best = dayMeals.maxByOrNull { it.protein }?.takeIf { it.protein > 0 }
        val target = profile.proteinTargetG
        val hit = target > 0 && protein >= target
        val tomorrow = tomorrow(workouts, date)
        val proteinPart = if (hit) "${protein.roundToInt()} g protein ✓" else "${protein.roundToInt()} g protein (${(target - protein).roundToInt().coerceAtLeast(0)} g short)"
        val day = if (date == Dates.today()) "Today" else "Yesterday"
        val line = "$day: $proteinPart · ${String.format(Locale.US, "%,d", calories.roundToInt())} kcal · $sessions/${profile.weeklyWorkoutTarget} sessions — tomorrow: $tomorrow"
        return Result(
            date = date,
            protein = protein.roundToInt(),
            proteinTarget = target,
            proteinHit = hit,
            calories = calories.roundToInt(),
            calorieBudget = (profile.calorieTarget + burned).roundToInt(),
            burned = burned.roundToInt(),
            sessions = sessions,
            sessionTarget = profile.weeklyWorkoutTarget,
            tomorrow = tomorrow,
            bestMeal = best?.let { m -> m.items.take(3).joinToString(", ") { it.name }.ifBlank { m.rawText.ifBlank { "Meal" } } },
            bestMealProtein = best?.protein?.roundToInt() ?: 0,
            line = line,
        )
    }

    // ---- local prefs: dismissals, and what the 21:00 receiver needs without the view model ----

    private const val FILE = "bandlog_wrap"

    fun dismissed(context: Context, date: String): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("dismissed", null) == date

    fun dismiss(context: Context, date: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("dismissed", date).apply()
    }

    /** A tapped wrap notification brings the card back even if it was closed earlier that evening. */
    fun undismiss(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove("dismissed").apply()
    }

    /** Whether Health Connect was connected on the last refresh (the receiver dedupes the burn the same way). */
    fun setHealthConnected(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("health", on).apply()
    }

    fun healthConnected(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("health", false)

    /** The nudge banner on Home: dismissed per newest nudge id. */
    fun nudgeDismissed(context: Context, id: String): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("nudge", null) == id

    fun dismissNudge(context: Context, id: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("nudge", id).apply()
    }
}
