package com.sohum.bandlog.util

import com.sohum.bandlog.data.Profile
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v2.13 §5 adaptive weekly targets (`profiles.adaptive_targets`, `weekly_checkins`). Pure Kotlin —
 * a line-for-line port of the web's src/lib/adaptive.ts; the spec's test vectors and the web's
 * scripts/check-adaptive.ts cases are in app/src/test/.../AdaptiveTest.kt.
 *
 *   1. A daily weight series for the 21 days ending `asOf`, forward-filling gaps. Days before the
 *      first weigh-in in that window stay empty (no extrapolation backwards). Several weigh-ins on
 *      one day count as their mean.
 *   2. Trend = EWMA, alpha 0.1, seeded with the first weigh-in.
 *   3. slope (kg/day) = least-squares slope of the trend over the last 14 days; × 7 = kg/week.
 *   4. avg_kcal = mean kcal over the days with at least one food log among the last 14.
 *   5. TDEE_est = avg_kcal − slope × 7700.
 *   6. raw = TDEE_est + goal_rate (kg/week, negative for loss) × 7700 / 7.
 *   7. new = clamp(raw, old − 150, old + 150), then clamp to the safety floor / ceiling, then
 *      rounded to the nearest 10.
 *
 * `asOf` is the Sunday before the check-in's Monday. Needs ≥ 10 of those 14 days with a food log
 * and ≥ 4 weigh-ins in them. A check-in never applies itself.
 */
object Adaptive {
    const val ALPHA = 0.1
    const val WEIGHT_DAYS = 21
    const val WINDOW_DAYS = 14
    const val MIN_LOGGED_DAYS = 10
    const val MIN_WEIGH_INS = 4
    /** Most a single check-in moves the target, either way. */
    const val MAX_STEP_KCAL = 150
    /** Highest target a check-in will ever suggest (a sanity cap; Goals has no ceiling of its own). */
    const val ADAPTIVE_CEILING = 5000

    data class WeighIn(val date: String, val kg: Double)

    private fun day(s: String): LocalDate = LocalDate.parse(s.take(10))
    private fun dayNum(s: String): Long = day(s).toEpochDay()
    fun shiftDay(iso: String, n: Long): String = day(iso).plusDays(n).toString()

    /** Monday of the week containing [iso]. */
    fun mondayOf(iso: String): String = day(iso).with(java.time.DayOfWeek.MONDAY).toString()

    /** The check-in week for [today]: its Monday, and the Sunday before it (the last day counted). */
    fun checkinWeek(today: String): Pair<String, String> {
        val ws = mondayOf(today)
        return ws to shiftDay(ws, -1)
    }

    // ---------------------------------------------------------------- steps 1–3

    /** Step 1: one value per day for the [days] days ending [asOf] (oldest first); null before the first weigh-in. */
    fun dailyWeights(weighIns: List<WeighIn>, asOf: String, days: Int = WEIGHT_DAYS): List<Double?> {
        val end = dayNum(asOf)
        val start = end - days + 1
        val byDay = HashMap<Long, MutableList<Double>>()
        for (w in weighIns) {
            val d = dayNum(w.date)
            if (!(w.kg > 0) || d < start || d > end) continue
            byDay.getOrPut(d) { mutableListOf() }.add(w.kg)
        }
        var last: Double? = null
        return (start..end).map { d ->
            byDay[d]?.let { v -> last = v.sum() / v.size }
            last
        }
    }

    /** Step 2: EWMA seeded with the first value; nulls before it stay null. */
    fun ewma(series: List<Double?>, alpha: Double = ALPHA): List<Double?> {
        var t: Double? = null
        return series.map { v ->
            if (v != null) t = t?.let { it + alpha * (v - it) } ?: v
            t
        }
    }

    /** Least-squares slope of y against its index, over the non-null points (0 with fewer than 2). */
    fun lsSlope(values: List<Double?>): Double {
        val pts = values.mapIndexedNotNull { x, y -> y?.let { x.toDouble() to it } }
        if (pts.size < 2) return 0.0
        val mx = pts.sumOf { it.first } / pts.size
        val my = pts.sumOf { it.second } / pts.size
        var num = 0.0
        var den = 0.0
        for ((x, y) in pts) { num += (x - mx) * (y - my); den += (x - mx) * (x - mx) }
        return if (den == 0.0) 0.0 else num / den
    }

    /** Steps 1–3: the trend's slope in kg/day over the last 14 days of the 21-day series. */
    fun trendSlopeKgPerDay(weighIns: List<WeighIn>, asOf: String): Double = lsSlope(ewma(dailyWeights(weighIns, asOf)).takeLast(WINDOW_DAYS))

    // ---------------------------------------------------------------- steps 5–7

    data class TargetMaths(val tdee: Double, val raw: Double, val newTarget: Int)

    private fun jsRound(v: Double): Long = Math.floor(v + 0.5).toLong()

    /** Steps 5–7 from the averages alone (the spec's test vectors start here). */
    fun adaptiveTarget(avgKcal: Double, slopeKgPerDay: Double, goalRateKgPerWeek: Double, oldTarget: Int, floor: Int, ceiling: Int = ADAPTIVE_CEILING): TargetMaths {
        val tdee = avgKcal - slopeKgPerDay * Goals.KCAL_PER_KG
        val raw = tdee + goalRateKgPerWeek * Goals.KCAL_PER_KG / 7
        val stepped = min((oldTarget + MAX_STEP_KCAL).toDouble(), max((oldTarget - MAX_STEP_KCAL).toDouble(), raw))
        val safe = min(ceiling.toDouble(), max(floor.toDouble(), stepped))
        return TargetMaths(tdee, raw, (jsRound(safe / 10) * 10).toInt())
    }

    // ---------------------------------------------------------------- the goal rate and bounds from a profile

    /**
     * kg/week the target aims for: the goal speed after Goals' safe cap (negative for loss, 0 for
     * maintain). Under 18 there's no loss; a teen "gain" is the small EER surplus as a rate.
     */
    fun goalRateFor(p: Profile, today: String = Goals.todayIso()): Double {
        val age = Goals.ageYears(p.dob, today)
        val goal = Goals.effectiveGoal(p.goalType, age)
        if (Goals.isTeen(age)) {
            if (goal != "gain") return 0.0
            val pl = Goals.plan(p, today) ?: return 0.0
            return Goals.teenGainSurplus(pl.maintenance) * 7 / Goals.KCAL_PER_KG
        }
        val requested = max(0.1, if (p.goalSpeedKgWk > 0) p.goalSpeedKgWk else 0.5)
        val kg = p.weightKg ?: 0.0
        return when (goal) {
            "lose" -> -min(requested, if (kg > 0) Goals.maxSafeWeeklyLossKg(kg) else 1.0)
            "gain" -> min(requested, if (kg > 0) Goals.maxSafeWeeklyGainKg(kg) else 0.5)
            else -> 0.0
        }
    }

    /** The floor a check-in can't go under: Goals.floorFor; under 18 the teen's own energy need (EER) when known. */
    fun adaptiveFloor(p: Profile, today: String = Goals.todayIso()): Int {
        val base = Goals.floorFor(p, today)
        if (!Goals.isTeen(Goals.ageYears(p.dob, today))) return base
        val pl = Goals.plan(p, today) ?: return base
        return max(base, jsRound(pl.maintenance).toInt())
    }

    // ---------------------------------------------------------------- the check-in

    data class Result(
        val ok: Boolean,
        val asOf: String,
        val loggedDays: Int,
        val weighIns: Int,
        /** What's still needed when not [ok] ("Log food on 3 more days and …"). */
        val missing: String = "",
        val avgWeightKg: Double = 0.0,
        val trendKgPerWeek: Double = 0.0,
        val avgKcal: Int = 0,
        val tdee: Int = 0,
        val raw: Int = 0,
        val oldTarget: Int = 0,
        val newTarget: Int = 0,
        val goalRateKgPerWeek: Double = 0.0,
        val reason: String = "",
    )

    /** Days with a food log and weigh-ins inside the 14 days ending [asOf]. */
    fun readiness(asOf: String, weighIns: List<WeighIn>, dayKcal: Map<String, Double>): Pair<Int, Int> {
        val end = dayNum(asOf)
        fun inWindow(iso: String): Boolean { val d = dayNum(iso); return d > end - WINDOW_DAYS && d <= end }
        return dayKcal.keys.count { inWindow(it) } to weighIns.count { it.kg > 0 && inWindow(it.date) }
    }

    /** "Log food on 3 more days and weigh in 2 more times in the last 2 weeks, and your next check-in can adjust your target." */
    fun missingText(loggedDays: Int, weighIns: Int): String {
        val parts = mutableListOf<String>()
        val days = MIN_LOGGED_DAYS - loggedDays
        val weighs = MIN_WEIGH_INS - weighIns
        if (days > 0) parts += "log food on $days more day${if (days == 1) "" else "s"}"
        if (weighs > 0) parts += "weigh in $weighs more time${if (weighs == 1) "" else "s"}"
        if (parts.isEmpty()) return ""
        val s = parts.joinToString(" and ")
        return "${s.replaceFirstChar { it.uppercase() }} in the last 2 weeks, and your next check-in can adjust your target."
    }

    /** JS `Number(v.toFixed(d))` then "−0.5" / "+0.2" / "0" (trailing ".0" dropped). */
    fun signed(v: Double, digits: Int = 1): String {
        val r = String.format(Locale.US, "%.${digits}f", v).toDouble()
        if (r == 0.0) return "0"
        val body = String.format(Locale.US, "%.${digits}f", abs(r)).replace(Regex("\\.0+$"), "")
        return (if (r < 0) "−" else "+") + body
    }

    private fun kcalText(n: Double): String = String.format(Locale.US, "%,d", jsRound(n))

    /**
     * Step 8: "Your weight trend is −0.3 kg/week (goal −0.5) and you ate about 2,180 kcal a day, so
     * your burn is about 2,510. Lowering your target by 110 kcal."
     */
    fun reasonText(trendKgPerWeek: Double, goalRateKgPerWeek: Double, avgKcal: Double, tdee: Double, oldTarget: Int, newTarget: Int): String {
        val burn = jsRound(tdee / 10) * 10
        val delta = newTarget - oldTarget
        val action = when {
            delta < 0 -> "Lowering your target by ${kcalText(-delta.toDouble())} kcal."
            delta > 0 -> "Raising your target by ${kcalText(delta.toDouble())} kcal."
            else -> "Keeping your target at ${kcalText(oldTarget.toDouble())} kcal."
        }
        return "Your weight trend is ${signed(trendKgPerWeek)} kg/week (goal ${signed(goalRateKgPerWeek)}) and you ate about ${kcalText((jsRound(avgKcal / 10) * 10).toDouble())} kcal a day, so your burn is about ${kcalText(burn.toDouble())}. $action"
    }

    /** The whole check-in from raw data ([dayKcal]: kcal per date, only dates with at least one food log). */
    fun weeklyCheckin(asOf: String, weighIns: List<WeighIn>, dayKcal: Map<String, Double>, goalRateKgPerWeek: Double, oldTarget: Int, floor: Int, ceiling: Int = ADAPTIVE_CEILING): Result {
        val (loggedDays, weighs) = readiness(asOf, weighIns, dayKcal)
        if (loggedDays < MIN_LOGGED_DAYS || weighs < MIN_WEIGH_INS) return Result(false, asOf, loggedDays, weighs, missing = missingText(loggedDays, weighs))
        val end = dayNum(asOf)
        val kcals = dayKcal.filter { (d, _) -> dayNum(d) > end - WINDOW_DAYS && dayNum(d) <= end }.values
        val avgKcal = kcals.sum() / kcals.size
        val slope = trendSlopeKgPerDay(weighIns, asOf)
        val recent = weighIns.filter { it.kg > 0 && dayNum(it.date) > end - WINDOW_DAYS && dayNum(it.date) <= end }
        val avgWeightKg = jsRound(recent.sumOf { it.kg } / recent.size * 100) / 100.0
        val m = adaptiveTarget(avgKcal, slope, goalRateKgPerWeek, oldTarget, floor, ceiling)
        val trend = jsRound(slope * 7 * 100) / 100.0
        return Result(
            ok = true, asOf = asOf, loggedDays = loggedDays, weighIns = weighs,
            avgWeightKg = avgWeightKg, trendKgPerWeek = trend, avgKcal = jsRound(avgKcal).toInt(),
            tdee = jsRound(m.tdee).toInt(), raw = jsRound(m.raw).toInt(), oldTarget = oldTarget, newTarget = m.newTarget,
            goalRateKgPerWeek = goalRateKgPerWeek,
            reason = reasonText(trend, goalRateKgPerWeek, avgKcal, m.tdee, oldTarget, m.newTarget),
        )
    }
}
