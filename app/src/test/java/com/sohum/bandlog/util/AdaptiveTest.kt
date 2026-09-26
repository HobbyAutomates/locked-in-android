package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** v2.13 §5: the spec's test vectors (web: scripts/check-adaptive.ts must print the same). */
class AdaptiveTest {

    private val halfKgPerWeek = -0.5 / 7

    @Test fun vector1_onPace() {
        val (tdee, raw, next) = Adaptive.target(avgKcal = 2200.0, slopeKgPerDay = halfKgPerWeek, goalRateKgPerWeek = -0.5, oldTarget = 2200, floor = 1200)
        assertEquals(2750.0, tdee, 1e-9)
        assertEquals(2200.0, raw, 1e-9)
        assertEquals(2200, next)
    }

    @Test fun vector2_fasterGoalClampedTo150() {
        val (tdee, raw, next) = Adaptive.target(2200.0, halfKgPerWeek, -0.75, 2200, 1200)
        assertEquals(2750.0, tdee, 1e-9)
        assertEquals(1925.0, raw, 1e-9)
        assertEquals(2050, next)
    }

    @Test fun vector3_flatTrend() {
        val (tdee, raw, next) = Adaptive.target(2000.0, 0.0, -0.5, 2000, 1200)
        assertEquals(2000.0, tdee, 1e-9)
        assertEquals(1450.0, raw, 1e-9)
        assertEquals(1850, next)
    }

    @Test fun safetyFloorWins() {
        // Same as vector 3 but the person's floor is 1900: never below it.
        assertEquals(1900, Adaptive.target(2000.0, 0.0, -0.5, 2000, 1900).third)
    }

    @Test fun roundsToNearest10() {
        assertEquals(2130, Adaptive.target(2126.0, 0.0, 0.0, 2100, 1200).third)
    }

    /**
     * Full pipeline: daily weigh-ins chosen so the EWMA trend falls exactly 0.5 kg/week
     * (w_t = w0 + r(t−1) + r/α makes e_t = w0 + r·t), 14 logged days of 2200 kcal.
     */
    @Test fun fullCheckMatchesVector1() {
        val end = "2026-09-27"
        val start = LocalDate.parse(end).minusDays(20)
        val r = halfKgPerWeek
        val weights = (0 until 21).map { t ->
            val kg = if (t == 0) 80.0 else 80.0 + r * (t - 1) + r / Adaptive.ALPHA
            Adaptive.WeighIn(start.plusDays(t.toLong()).toString(), kg)
        }
        val kcal = (0 until 14).associate { LocalDate.parse(end).minusDays(it.toLong()).toString() to 2200.0 }
        val res = Adaptive.check(Adaptive.Input(end, weights, kcal, -0.5, 2200, 1200))
        assertTrue(res.ready)
        assertEquals(-0.5, res.trendKgPerWeek!!, 1e-9)
        assertEquals(2750.0, res.tdee!!, 1e-6)
        assertEquals(2200.0, res.raw!!, 1e-6)
        assertEquals(2200, res.newTarget)
        assertEquals(
            "Your weight trend is −0.5 kg/week (goal −0.5) and you ate about 2,200 kcal a day, so your burn is about 2,750. Keeping your target at 2,200 kcal.",
            res.reason,
        )
    }

    @Test fun reasonTextLowering() {
        assertEquals(
            "Your weight trend is −0.3 kg/week (goal −0.5) and you ate about 2,180 kcal a day, so your burn is about 2,510. Lowering your target by 110 kcal.",
            Adaptive.reason(-0.3, -0.5, 2180.0, 2510.0, 2200, 2090),
        )
        assertEquals("goal +0.25", Adaptive.goalWords(0.25))
        assertEquals("goal: hold steady", Adaptive.goalWords(0.0))
    }

    @Test fun notEnoughData() {
        val end = "2026-09-27"
        val weights = listOf(Adaptive.WeighIn("2026-09-20", 80.0), Adaptive.WeighIn("2026-09-25", 79.8))
        val kcal = (0 until 6).associate { LocalDate.parse(end).minusDays(it.toLong()).toString() to 2000.0 }
        val res = Adaptive.check(Adaptive.Input(end, weights, kcal, -0.5, 2000, 1200))
        assertFalse(res.ready)
        assertNull(res.newTarget)
        assertEquals(2, res.missing.size)
        assertTrue(res.missing[0].startsWith("Log food on 4 more days"))
        assertTrue(res.missing[1].startsWith("Weigh in 2 more times"))
    }

    @Test fun seriesForwardFillsWithoutExtrapolating() {
        val s = Adaptive.dailySeries(listOf(Adaptive.WeighIn("2026-09-10", 70.0), Adaptive.WeighIn("2026-09-12", 71.0)), "2026-09-13", days = 5)
        // 09-09 (before the first weigh-in) stays null; 09-11 forward-fills 70; 09-13 forward-fills 71.
        assertEquals(listOf(null, 70.0, 70.0, 71.0, 71.0), s)
        val e = Adaptive.ewma(s)
        assertNull(e[0]); assertEquals(70.0, e[1]!!, 1e-12); assertEquals(70.1, e[3]!!, 1e-12)
    }

    @Test fun goalRateCapsAndTeens() {
        assertEquals(-0.5, Adaptive.goalRate("lose", 0.5, 80.0, false), 1e-12)
        assertEquals(-0.5, Adaptive.goalRate("lose", 1.5, 50.0, false), 1e-12) // 1 % of 50 kg
        assertEquals(0.0, Adaptive.goalRate("lose", 0.5, 60.0, true), 1e-12)
        assertEquals(0.0, Adaptive.goalRate("maintain", 0.5, 60.0, false), 1e-12)
    }
}
