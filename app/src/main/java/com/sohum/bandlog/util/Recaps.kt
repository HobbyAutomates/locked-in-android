package com.sohum.bandlog.util

import com.sohum.bandlog.data.ExerciseEntry
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.WeightEntry
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.data.totalsFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * v2.13 weekly and monthly recap stories (spec §13). Weekly appears on Monday for the previous
 * Mon–Sun, monthly on the 1st for the previous month; both are listed under Progress → Recaps.
 * Mirrors the web's lib/recap.ts.
 */
object Recaps {
    enum class Kind { WEEK, MONTH }

    data class Period(val kind: Kind, val from: String, val to: String) {
        val key: String get() = "${kind.name.lowercase()}-$from"
        val days: Int get() = (Dates.daysBetween(from, to) + 1).toInt()
        val label: String get() = when (kind) {
            Kind.WEEK -> "${fmt(from, "d MMM")} – ${fmt(to, "d MMM")}"
            Kind.MONTH -> fmt(from, "MMMM yyyy")
        }
        val title: String get() = if (kind == Kind.WEEK) "Your week" else "Your ${fmt(from, "MMMM")}"
    }

    private fun fmt(iso: String, pattern: String) = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))

    /** The Mon–Sun week before the one containing [today]. */
    fun lastWeek(today: String): Period {
        val ws = Dates.weekStart(today)
        return Period(Kind.WEEK, Dates.addDays(ws, -7), Dates.addDays(ws, -1))
    }

    /** The calendar month before the one containing [today]. */
    fun lastMonth(today: String): Period {
        val first = LocalDate.parse(today).withDayOfMonth(1)
        val prev = first.minusMonths(1)
        return Period(Kind.MONTH, prev.toString(), first.minusDays(1).toString())
    }

    /** The newest [n] finished weeks, newest first. */
    fun weeks(today: String, n: Int): List<Period> = (0 until n).map { i ->
        val ws = Dates.addDays(Dates.weekStart(today), -7L * (i + 1))
        Period(Kind.WEEK, ws, Dates.addDays(ws, 6))
    }

    /** The newest [n] finished months, newest first. */
    fun months(today: String, n: Int): List<Period> = (0 until n).map { i ->
        val first = LocalDate.parse(today).withDayOfMonth(1).minusMonths((i + 1).toLong())
        Period(Kind.MONTH, first.toString(), first.plusMonths(1).minusDays(1).toString())
    }

    /** The recaps that are "new" today: last week on a Monday, last month on the 1st. */
    fun dueToday(today: String): List<Period> = buildList {
        val d = LocalDate.parse(today)
        if (d.dayOfMonth == 1) add(lastMonth(today))
        if (d.dayOfWeek.value == 1) add(lastWeek(today))
    }

    data class Recap(
        val period: Period,
        val daysLogged: Int,
        val workouts: Int,
        val minutes: Int,
        val proteinDaysHit: Int,
        val proteinTarget: Int,
        /** Protein per day across the period (for the bars slide). */
        val proteinByDay: List<Double>,
        /** Best new PR in the period, else the heaviest e1RM lifted in it. */
        val bestLift: Pair<String, Training.Point>?,
        val prCount: Int,
        val weightStart: Double?,
        val weightEnd: Double?,
        /** (date, kg) weigh-ins inside the period, oldest first. */
        val weightSeries: List<Pair<String, Double>>,
        val topFoods: List<Pair<String, Int>>,
        val streak: Int,
        val squadRank: Int?,
        val squadName: String?,
        val nextGoal: String,
    ) {
        val weightChange: Double? get() = if (weightStart != null && weightEnd != null) weightEnd - weightStart else null
        val empty: Boolean get() = daysLogged == 0 && workouts == 0 && weightSeries.isEmpty()
    }

    private fun inRange(d: String, p: Period) = d >= p.from && d <= p.to

    fun compute(
        period: Period,
        workouts: List<Workout>,
        meals: List<Meal>,
        exercises: List<ExerciseEntry>,
        weights: List<WeightEntry>,
        proteinTarget: Int,
        weeklyWorkoutTarget: Int,
        streak: Int,
        squadRank: Int? = null,
        squadName: String? = null,
    ): Recap {
        val days = (0 until period.days).map { Dates.addDays(period.from, it.toLong()) }
        val w = workouts.filter { inRange(it.date, period) }
        val m = meals.filter { inRange(it.date, period) }
        val x = exercises.filter { inRange(it.date, period) }
        val logged = (w.map { it.date } + m.map { it.date } + x.map { it.date }).toSet()
        // Workout minutes, plus activities that aren't a workout's own burn row.
        val minutes = w.sumOf { it.minutes ?: 0 } + x.filter { it.source != "workout" }.sumOf { it.minutes }
        val protein = days.map { totalsFor(m, it).protein }
        val hit = protein.count { it > 0 && it >= proteinTarget }
        val prs = Training.prsBetween(workouts, period.from, period.to)
        val best = prs.firstOrNull() ?: Training.exercisesWithHistory(w).mapNotNull { (name, _) ->
            Training.history(w, name).maxByOrNull { it.e1rm }?.let { name to it }
        }.maxByOrNull { it.second.e1rm }
        val ws = weights.filter { inRange(it.date, period) }.sortedBy { it.date }.map { it.date to it.weightKg }
        // Start weight: the last weigh-in before the period if there is one, else the first inside it.
        val before = weights.filter { it.date < period.from }.maxByOrNull { it.date }?.weightKg
        val start = before ?: ws.firstOrNull()?.second
        val end = ws.lastOrNull()?.second
        val foods = m.flatMap { it.items }.map { it.name.trim() }.filter { it.isNotBlank() }
            .groupBy { it.lowercase() }.map { (_, v) -> v.first().replaceFirstChar { c -> c.uppercase() } to v.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(3)
        val next = if (period.kind == Kind.WEEK) {
            val sessions = w.map { it.date }.distinct().size
            when {
                sessions < weeklyWorkoutTarget -> "Hit $weeklyWorkoutTarget sessions next week"
                hit < 5 -> "Hit your protein on 5 days next week"
                else -> "Keep the streak: $weeklyWorkoutTarget sessions and your protein"
            }
        } else "Log ${(logged.size + 3).coerceAtMost(period.days)} days next month"
        return Recap(
            period = period, daysLogged = logged.size, workouts = w.size, minutes = minutes,
            proteinDaysHit = hit, proteinTarget = proteinTarget, proteinByDay = protein,
            bestLift = best, prCount = prs.size, weightStart = start, weightEnd = end,
            weightSeries = ws, topFoods = foods, streak = streak, squadRank = squadRank, squadName = squadName, nextGoal = next,
        )
    }

    fun kg(v: Double): String = if ((v * 10).roundToInt() % 10 == 0) "${v.roundToInt()}" else String.format(Locale.US, "%.1f", v)
}
