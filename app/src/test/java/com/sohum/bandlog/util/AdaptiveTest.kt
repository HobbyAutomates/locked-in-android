package com.sohum.bandlog.util

import com.sohum.bandlog.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.13 §5 adaptive weekly targets: the spec's three test vectors plus every case in the web's
 * scripts/check-adaptive.ts, against util/Adaptive.kt. Both apps must print the same numbers.
 */
class AdaptiveTest {

    private val halfKgWeek = -0.5 / 7

    // ---- the spec's three test vectors (steps 5–7) ----

    @Test fun vector1() {
        val r = Adaptive.adaptiveTarget(avgKcal = 2200.0, slopeKgPerDay = halfKgWeek, goalRateKgPerWeek = -0.5, oldTarget = 2200, floor = 1500)
        assertEquals(2750.0, r.tdee, 1e-6); assertEquals(2200.0, r.raw, 1e-6); assertEquals(2200, r.newTarget)
    }

    @Test fun vector2() {
        val r = Adaptive.adaptiveTarget(2200.0, halfKgWeek, -0.75, 2200, 1500)
        assertEquals(2750.0, r.tdee, 1e-6); assertEquals(1925.0, r.raw, 1e-6); assertEquals(2050, r.newTarget)
    }

    @Test fun vector3() {
        val r = Adaptive.adaptiveTarget(2000.0, 0.0, -0.5, 2000, 1500)
        assertEquals(2000.0, r.tdee, 1e-6); assertEquals(1450.0, r.raw, 1e-6); assertEquals(1850, r.newTarget)
    }

    // ---- safety floor / ceiling and rounding ----

    @Test fun floorCeilingRounding() {
        assertEquals(1500, Adaptive.adaptiveTarget(1300.0, 0.0, -0.5, 1600, 1500).newTarget)
        assertEquals(Adaptive.ADAPTIVE_CEILING, Adaptive.adaptiveTarget(6000.0, 0.0, 0.0, 4950, 1500).newTarget)
        assertEquals(2100, Adaptive.adaptiveTarget(2104.0, 0.0, 0.0, 2100, 1500).newTarget)
        assertEquals(2110, Adaptive.adaptiveTarget(2106.0, 0.0, 0.0, 2100, 1500).newTarget)
        assertEquals(2550, Adaptive.adaptiveTarget(2500.0, 0.0, 0.25, 2400, 1500).newTarget)
    }

    // ---- steps 1–3 ----

    @Test fun seriesEwmaSlope() {
        assertEquals(listOf(null, 70.0, 70.0, 71.0, 71.0), Adaptive.dailyWeights(listOf(Adaptive.WeighIn("2026-09-03", 70.0), Adaptive.WeighIn("2026-09-05", 71.0)), "2026-09-06", 5))
        assertEquals(listOf(70.5), Adaptive.dailyWeights(listOf(Adaptive.WeighIn("2026-09-06", 70.0), Adaptive.WeighIn("2026-09-06", 71.0)), "2026-09-06", 1))
        assertEquals(listOf(null, null), Adaptive.dailyWeights(listOf(Adaptive.WeighIn("2026-08-01", 60.0), Adaptive.WeighIn("2026-09-07", 99.0)), "2026-09-06", 2))
        assertEquals(listOf(null, 70.0, 71.0), Adaptive.ewma(listOf(null, 70.0, 80.0)))
        assertEquals(2.0, Adaptive.lsSlope(listOf(1.0, 3.0, 5.0, 7.0)), 1e-9)
        assertEquals(1.0, Adaptive.lsSlope(listOf(null, 1.0, 2.0, 3.0)), 1e-9)
        assertEquals(0.0, Adaptive.lsSlope(listOf(null, 5.0)), 0.0)
    }

    /** A weigh-in series whose EWMA trend is exactly a line falling 0.5 kg/week (w_t = T0 + b·t + b(1 − α)/α). */
    private val asOf = "2026-09-27" // a Sunday
    private val series = (0 until 21).map { i ->
        Adaptive.WeighIn(Adaptive.shiftDay(asOf, (i - 20).toLong()), if (i == 0) 80.0 else 80.0 + halfKgWeek * i + (halfKgWeek * 0.9) / 0.1)
    }
    private val dayKcal = (0 until 14).associate { Adaptive.shiftDay(asOf, -it.toLong()) to 2200.0 }

    @Test fun pipelineVectors() {
        assertEquals(-0.5, Adaptive.trendSlopeKgPerDay(series, asOf) * 7, 1e-9)
        val r = Adaptive.weeklyCheckin(asOf, series, dayKcal, -0.5, 2200, 1500)
        assertTrue(r.ok)
        assertEquals(2750, r.tdee); assertEquals(2200, r.newTarget); assertEquals(-0.5, r.trendKgPerWeek, 0.0); assertEquals(2200, r.avgKcal)
        assertEquals("Your weight trend is −0.5 kg/week (goal −0.5) and you ate about 2,200 kcal a day, so your burn is about 2,750. Keeping your target at 2,200 kcal.", r.reason)
        assertEquals(2050, Adaptive.weeklyCheckin(asOf, series, dayKcal, -0.75, 2200, 1500).newTarget)

        // Flat weight, 2000 kcal: vector 3 end to end (a weigh-in every 3 days is enough).
        val flat = listOf(0, 3, 6, 9, 12, 15, 18).map { Adaptive.WeighIn(Adaptive.shiftDay(asOf, -it.toLong()), 72.0) }
        val kcal = (0 until 12).associate { Adaptive.shiftDay(asOf, -it.toLong()) to 2000.0 }
        val v3 = Adaptive.weeklyCheckin(asOf, flat, kcal, -0.5, 2000, 1500)
        assertEquals(listOf(2000, 1450, 1850), listOf(v3.tdee, v3.raw, v3.newTarget))
        assertEquals(72.0, v3.avgWeightKg, 0.0)
        assertEquals("Your weight trend is 0 kg/week (goal −0.5) and you ate about 2,000 kcal a day, so your burn is about 2,000. Lowering your target by 150 kcal.", v3.reason)
    }

    @Test fun notEnoughData() {
        val few = listOf(Adaptive.WeighIn(asOf, 70.0), Adaptive.WeighIn(Adaptive.shiftDay(asOf, -2), 70.0))
        val kcal = (0 until 7).associate { Adaptive.shiftDay(asOf, -it.toLong()) to 2000.0 } + (Adaptive.shiftDay(asOf, -20) to 2000.0)
        val r = Adaptive.weeklyCheckin(asOf, few, kcal, 0.0, 2000, 1500)
        assertFalse(r.ok)
        assertEquals(listOf(7, 2), listOf(r.loggedDays, r.weighIns))
        assertEquals("Log food on 3 more days and weigh in 2 more times in the last 2 weeks, and your next check-in can adjust your target.", r.missing)
        assertEquals("Log food on 1 more day and weigh in 1 more time in the last 2 weeks, and your next check-in can adjust your target.", Adaptive.missingText(9, 3))
        assertEquals("", Adaptive.missingText(10, 4))
    }

    @Test fun reasonText() {
        assertEquals(
            "Your weight trend is −0.3 kg/week (goal −0.5) and you ate about 2,180 kcal a day, so your burn is about 2,510. Lowering your target by 110 kcal.",
            Adaptive.reasonText(-0.3, -0.5, 2180.0, 2510.0, 2250, 2140),
        )
        assertTrue(Adaptive.reasonText(0.2, 0.0, 2000.0, 1850.0, 1800, 1850).endsWith("Raising your target by 50 kcal."))
        assertEquals("−0.8", Adaptive.signed(-0.75))
    }

    @Test fun week() {
        assertEquals("2026-09-21", Adaptive.mondayOf("2026-09-27"))
        assertEquals("2026-09-28", Adaptive.mondayOf("2026-09-28"))
        assertEquals("2026-09-28" to "2026-09-27", Adaptive.checkinWeek("2026-09-30"))
    }

    @Test fun profileHelpers() {
        val adult = Profile(dob = "1998-01-01", gender = "male", heightCm = 175.0, weightKg = 80.0, goalType = "lose", goalSpeedKgWk = 0.5, weeklyWorkoutTarget = 3)
        assertEquals(-0.5, Adaptive.goalRateFor(adult, "2026-09-26"), 0.0)
        assertEquals(-0.5, Adaptive.goalRateFor(adult.copy(weightKg = 50.0, goalSpeedKgWk = 1.0), "2026-09-26"), 0.0)
        assertEquals(0.0, Adaptive.goalRateFor(adult.copy(goalType = "maintain"), "2026-09-26"), 0.0)
        val teen = adult.copy(dob = "2010-06-01", weightKg = 60.0, heightCm = 168.0)
        assertEquals(0.0, Adaptive.goalRateFor(teen, "2026-09-26"), 0.0)
        assertTrue(Adaptive.adaptiveFloor(teen, "2026-09-26") > 2000)
        assertEquals(1500, Adaptive.adaptiveFloor(adult, "2026-09-26"))
    }
}
