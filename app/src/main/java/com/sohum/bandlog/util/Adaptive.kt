package com.sohum.bandlog.util

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v2.13 §5 adaptive weekly targets (`profiles.adaptive_targets`, `weekly_checkins`). Pure Kotlin —
 * the twin of the web's src/lib/adaptive.ts, same numbers (the spec's test vectors are in
 * app/src/test/.../AdaptiveTest.kt).
 *
 * 1. A daily weight series for the 21 days ending [Input.endDate], forward-filling gaps (several
 *    weigh-ins on a day are averaged; nothing before the first weigh-in in the window).
 * 2. Trend = EWMA, alpha 0.1, seeded with the first weigh-in.
 * 3. slope = least-squares slope (kg/day) of the trend over the last 14 days; × 7 = kg/week.
 * 4. avg kcal = mean kcal over the logged days among the last 14.
 * 5. TDEE = avg kcal − slope × 7700.
 * 6. raw = TDEE + goal rate (kg/week, negative for loss) × 7700 / 7.
 * 7. new = clamp(raw, old ± 150), then clamp to the safety floor / ceiling (Goals, teen rules
 *    included), then rounded to the nearest 10.
 *
 * Needs ≥ 10 of the last 14 days with a food log and ≥ 4 weigh-ins in the last 14 days; otherwise
 * [check] says what's missing. The check-in never applies itself.
 */
object Adaptive {
    const val ALPHA = 0.1
    const val SERIES_DAYS = 21
    const val WINDOW_DAYS = 14
    const val MIN_LOGGED_DAYS = 10
    const val MIN_WEIGH_INS = 4
    const val MAX_STEP = 150
    const val KCAL_PER_KG = 7700.0

    data class WeighIn(val date: String, val kg: Double)

    data class Input(
        /** Last day of the window (the Sunday before the check-in's Monday). */
        val endDate: String,
        val weights: List<WeighIn>,
        /** kcal per day that has at least one food log (days with no log are simply absent). */
        val kcalByDay: Map<String, Double>,
        /** Current goal speed in kg/week: negative for loss, 0 for maintain, positive for gain. */
        val goalRateKgPerWeek: Double,
        val oldTarget: Int,
        /** The lowest target the app will set (Goals.floorFor; a teen's maintenance). */
        val floor: Int,
        /** The highest (a teen's maintenance + growth surplus; else a sanity cap). */
        val ceiling: Int = 6000,
    )

    data class Result(
        val ready: Boolean,
        /** What's missing when not ready, e.g. "Log food on 3 more days". */
        val missing: List<String>,
        val loggedDays: Int,
        val weighIns: Int,
        val avgWeightKg: Double?,
        val trendKgPerWeek: Double?,
        val avgKcal: Double?,
        val tdee: Double?,
        val raw: Double?,
        val oldTarget: Int,
        val newTarget: Int?,
        val reason: String,
    )

    private fun day(s: String): LocalDate = LocalDate.parse(s.take(10))

    /** Step 1: 21 values (null before the first weigh-in), oldest first. */
    fun dailySeries(weights: List<WeighIn>, endDate: String, days: Int = SERIES_DAYS): List<Double?> {
        val end = day(endDate)
        val start = end.minusDays((days - 1).toLong())
        val byDay = weights.filter { it.kg > 0 }
            .map { day(it.date) to it.kg }
            .filter { (d, _) -> !d.isBefore(start) && !d.isAfter(end) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.average() }
        var last: Double? = null
        return (0 until days).map { i ->
            byDay[start.plusDays(i.toLong())]?.let { last = it }
            last
        }
    }

    /** Step 2: EWMA over the non-null tail, seeded with the first weigh-in (nulls stay null). */
    fun ewma(series: List<Double?>, alpha: Double = ALPHA): List<Double?> {
        var t: Double? = null
        return series.map { v ->
            if (v == null) null else {
                t = t?.let { it + alpha * (v - it) } ?: v
                t
            }
        }
    }

    /** Least-squares slope of y over x = 0, 1, 2… (null values skipped); null with < 2 points. */
    fun slope(values: List<Double?>): Double? {
        val pts = values.mapIndexedNotNull { i, v -> v?.let { i.toDouble() to it } }
        if (pts.size < 2) return null
        val mx = pts.sumOf { it.first } / pts.size
        val my = pts.sumOf { it.second } / pts.size
        val sxx = pts.sumOf { (x, _) -> (x - mx) * (x - mx) }
        if (sxx == 0.0) return null
        return pts.sumOf { (x, y) -> (x - mx) * (y - my) } / sxx
    }

    /** Steps 5–7 on their own (the spec's test vectors). */
    fun target(avgKcal: Double, slopeKgPerDay: Double, goalRateKgPerWeek: Double, oldTarget: Int, floor: Int, ceiling: Int = 6000): Triple<Double, Double, Int> {
        val tdee = avgKcal - slopeKgPerDay * KCAL_PER_KG
        val raw = tdee + goalRateKgPerWeek * KCAL_PER_KG / 7
        var t = raw.coerceIn((oldTarget - MAX_STEP).toDouble(), (oldTarget + MAX_STEP).toDouble())
        t = t.coerceIn(floor.toDouble(), max(floor, ceiling).toDouble())
        return Triple(tdee, raw, round10(t))
    }

    fun round10(v: Double): Int = (v / 10).roundToInt() * 10

    /** The whole check-in. */
    fun check(i: Input): Result {
        val end = day(i.endDate)
        val windowStart = end.minusDays((WINDOW_DAYS - 1).toLong())
        fun inWindow(s: String) = runCatching { day(s) }.getOrNull()?.let { !it.isBefore(windowStart) && !it.isAfter(end) } ?: false
        val logged = i.kcalByDay.filter { (d, k) -> inWindow(d) && k > 0 }
        val weighIns = i.weights.count { it.kg > 0 && inWindow(it.date) }
        val series = dailySeries(i.weights, i.endDate)
        val trend = ewma(series)
        val last14 = trend.takeLast(WINDOW_DAYS)
        val s = slope(last14)
        val avgWeight = series.takeLast(WINDOW_DAYS).filterNotNull().takeIf { it.isNotEmpty() }?.average()

        val missing = buildList {
            if (logged.size < MIN_LOGGED_DAYS) {
                val n = MIN_LOGGED_DAYS - logged.size
                add("Log food on $n more day${if (n == 1) "" else "s"} (${logged.size} of the last 14 so far, 10 needed)")
            }
            if (weighIns < MIN_WEIGH_INS) {
                val n = MIN_WEIGH_INS - weighIns
                add("Weigh in $n more time${if (n == 1) "" else "s"} ($weighIns in the last 14 days, 4 needed)")
            }
        }
        if (missing.isNotEmpty() || s == null) {
            return Result(
                false, missing.ifEmpty { listOf("Weigh in on a few different days so there's a trend to read") },
                logged.size, weighIns, avgWeight, s?.let { it * 7 }, logged.values.takeIf { it.isNotEmpty() }?.average(), null, null, i.oldTarget, null,
                "Not enough data yet: " + missing.joinToString("; ").ifEmpty { "weigh in on a few different days" } + ".",
            )
        }
        val avgKcal = logged.values.average()
        val (tdee, raw, next) = target(avgKcal, s, i.goalRateKgPerWeek, i.oldTarget, i.floor, i.ceiling)
        return Result(true, emptyList(), logged.size, weighIns, avgWeight, s * 7, avgKcal, tdee, raw, i.oldTarget, next, reason(s * 7, i.goalRateKgPerWeek, avgKcal, tdee, i.oldTarget, next))
    }

    private fun signed1(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        val body = String.format(Locale.US, "%.1f", abs(r))
        return when {
            r < 0 -> "−$body"
            r > 0 -> "+$body"
            else -> body
        }
    }

    /** "goal −0.5", "goal +0.25", "goal: hold steady". */
    fun goalWords(g: Double): String {
        val r = (g * 100).roundToInt() / 100.0
        if (r == 0.0) return "goal: hold steady"
        val txt = String.format(Locale.US, "%.2f", abs(r)).trimEnd('0').trimEnd('.')
        return "goal ${if (r < 0) "−" else "+"}$txt"
    }

    private fun grouped(v: Int): String = String.format(Locale.US, "%,d", v)

    /**
     * "Your weight trend is −0.3 kg/week (goal −0.5) and you ate about 2,180 kcal a day, so your burn
     * is about 2,510. Lowering your target by 110 kcal."
     */
    fun reason(trendKgPerWeek: Double, goalRate: Double, avgKcal: Double, tdee: Double, oldTarget: Int, newTarget: Int): String {
        val head = "Your weight trend is ${signed1(trendKgPerWeek)} kg/week (${goalWords(goalRate)}) and you ate about ${grouped(round10(avgKcal))} kcal a day, so your burn is about ${grouped(round10(tdee))}."
        val d = newTarget - oldTarget
        val tail = when {
            d < 0 -> "Lowering your target by ${grouped(-d)} kcal."
            d > 0 -> "Raising your target by ${grouped(d)} kcal."
            else -> "Keeping your target at ${grouped(oldTarget)} kcal."
        }
        return "$head $tail"
    }

    /** The Monday that starts the week containing [date] (the check-in's week_start). */
    fun weekStart(date: String): String = day(date).with(java.time.DayOfWeek.MONDAY).toString()

    /** The window's last day for a check-in made on [weekStart]: the Sunday before. */
    fun endFor(weekStart: String): String = day(weekStart).minusDays(1).toString()

    /** Signed goal rate from a profile's goal and speed (after the safe caps; 0 for teens, who never get a deficit). */
    fun goalRate(goalType: String, speedKgWk: Double, kg: Double?, teen: Boolean): Double {
        if (teen) return 0.0
        val w = kg ?: 0.0
        return when (goalType) {
            "lose" -> -(if (w > 0) min(speedKgWk, Goals.maxSafeWeeklyLossKg(w)) else min(speedKgWk, 1.0))
            "gain" -> if (w > 0) min(speedKgWk, Goals.maxSafeWeeklyGainKg(w)) else min(speedKgWk, 0.5)
            else -> 0.0
        }
    }
}
