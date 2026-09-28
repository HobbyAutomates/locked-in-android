package com.sohum.bandlog.util

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * v2.18 B4 + B8: the 5-second daily check-in (sleep, stress, mood) and the recovery score.
 * Port of web src/lib/v218/checkin.ts (the server computes the same for the Home card; this copy
 * scores Health Connect sleep / resting heart rate on the phone and backs the unit tests).
 */
object DailyCheckin {
    const val MAX_DAY_BUMP = 150
    val SLEEP_CHOICES = listOf(5, 6, 7, 8, 9)
    val STRESS_LABELS = listOf("Calm", "Okay", "Some", "High", "Maxed")
    val MOOD_LABELS = listOf("Low", "Meh", "Okay", "Good", "Great")

    data class Checkin(val sleepHours: Double?, val sleepQuality: Int?, val stress: Int?, val mood: Int?, val restingHr: Int? = null) {
        val empty: Boolean get() = sleepHours == null && sleepQuality == null && stress == null && mood == null
    }

    fun poorSleep(c: Checkin) = (c.sleepHours != null && c.sleepHours < 6) || (c.sleepQuality != null && c.sleepQuality <= 2)
    fun goodSleep(c: Checkin) = (c.sleepHours == null || c.sleepHours >= 7) && (c.sleepQuality == null || c.sleepQuality >= 4) && (c.sleepHours != null || c.sleepQuality != null)
    fun highStress(c: Checkin) = c.stress != null && c.stress >= 4
    fun lowMood(c: Checkin) = c.mood != null && c.mood <= 2

    data class Adjust(val kcal: Int, val training: String, val tone: String, val reasons: List<String>, val summary: String)

    /** Only ever nudges today's target up (a smaller deficit), capped, and never for teens / non-cutters. */
    fun adjust(c: Checkin?, goal: String, teen: Boolean = false): Adjust {
        if (c == null || c.empty) return Adjust(0, "Train as planned.", "as_chosen", emptyList(), "")
        val reasons = mutableListOf<String>()
        var kcal = 0
        val cutting = goal == "lose" && !teen
        if (poorSleep(c)) { reasons += "Short or poor sleep"; if (cutting) kcal += 100 }
        if (highStress(c)) { reasons += "High stress"; if (cutting) kcal += 75 }
        if (lowMood(c)) reasons += "Low mood"
        kcal = kcal.coerceAtMost(MAX_DAY_BUMP)
        var tone = "as_chosen"
        val training = when {
            poorSleep(c) && highStress(c) -> { tone = "gentle"; "Recovery day: a 20–30 min walk or mobility. Skip heavy sets today." }
            poorSleep(c) -> { tone = "gentle"; "Train, but keep it moderate: same weights, fewer sets, no PR attempts." }
            highStress(c) -> { tone = "gentle"; "Movement helps stress: an easy session or a brisk walk. Don't chase numbers today." }
            goodSleep(c) && (c.stress == null || c.stress <= 2) && (c.mood == null || c.mood >= 3) -> { tone = "push"; "You're fresh: a good day to push a little harder or try a new best." }
            else -> "Train as planned."
        }
        if (lowMood(c)) tone = "gentle"
        val summary = when {
            kcal > 0 -> "+$kcal kcal buffer today (${reasons.joinToString(", ").lowercase()}). $training"
            reasons.isNotEmpty() -> "${reasons.joinToString(", ")}. $training"
            else -> training
        }
        return Adjust(kcal, training, tone, reasons, summary)
    }

    // ---- B8 recovery ----

    data class Recovery(val score: Int, val band: String, val label: String, val advice: String, val source: String)

    private fun hoursScore(h: Double): Double = when {
        h >= 8 -> 100.0
        h >= 7 -> 85 + (h - 7) * 15
        h >= 6 -> 65 + (h - 6) * 20
        h >= 5 -> 40 + (h - 5) * 25
        else -> maxOf(10.0, 40 - (5 - h) * 15)
    }

    fun hrScore(rhr: Int, baseline: Int): Int {
        val d = rhr - baseline
        return when { d <= 0 -> 100; d >= 10 -> 30; else -> (100 - d * 7.0).roundToInt() }
    }

    /** ≥ 75 push, 50–74 normal, < 50 deload. Null with nothing to go on. [hcSleep] = sleep came from Health Connect. */
    fun recovery(sleepHours: Double?, sleepQuality: Int?, stress: Int?, mood: Int?, restingHr: Int? = null, baselineHr: Int? = null, trainedDaysInRow: Int = 0, hcSleep: Boolean = false): Recovery? {
        val parts = mutableListOf<Pair<Double, Int>>()
        val sleep = listOfNotNull(sleepHours?.let { hoursScore(it) }, sleepQuality?.let { it * 20.0 })
        if (sleep.isNotEmpty()) parts += sleep.average() to 45
        if (stress != null) parts += (20.0 * (6 - stress)) to 25
        if (mood != null) parts += (mood * 20.0) to 15
        val hr = restingHr != null && baselineHr != null && baselineHr > 0
        if (hr) parts += hrScore(restingHr!!, baselineHr!!).toDouble() to 15
        if (parts.isEmpty()) return null
        val w = parts.sumOf { it.second }
        var score = parts.sumOf { it.first * it.second } / w
        if (trainedDaysInRow >= 3) score -= 10
        val s = score.coerceIn(0.0, 100.0).roundToInt()
        val band = if (s >= 75) "push" else if (s >= 50) "normal" else "deload"
        val fromCheckin = stress != null || mood != null || (sleepQuality != null && !hcSleep)
        val fromHc = hr || hcSleep
        return Recovery(
            s, band, band.replaceFirstChar { it.uppercase() },
            when (band) {
                "push" -> "Recovered well. Go for a top set or an extra round today."
                "normal" -> "Train as planned and stop 1–2 reps short of failure."
                else -> "Take it easy: lighter weights (about 60 %), a walk, or mobility. Tomorrow you'll be better for it."
            },
            if (fromHc && fromCheckin) "mixed" else if (fromHc) "health_connect" else "checkin",
        )
    }

    /** Consecutive days trained up to yesterday. */
    fun trainedInRow(trained: Collection<String>, today: String): Int {
        val set = trained.toSet()
        var n = 0
        var d = LocalDate.parse(today).minusDays(1)
        while (d.toString() in set) { n++; d = d.minusDays(1) }
        return n
    }

    /** Median of recent resting-HR readings (≥ 3), the personal baseline. */
    fun baseline(values: List<Int>): Int? = values.sorted().takeIf { it.size >= 3 }?.let { it[it.size / 2] }
}
