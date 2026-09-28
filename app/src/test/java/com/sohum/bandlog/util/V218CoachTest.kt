package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** v2.18 coach stream (areas B + C): the same fixtures as web scripts/check-v218-coach.ts. */
class V218CoachTest {
    // ---- B4 check-in + B8 recovery ----

    @Test fun checkinOnlyEverSoftensACut() {
        val bad = DailyCheckin.Checkin(5.0, null, 5, 1)
        val a = DailyCheckin.adjust(bad, "lose")
        assertEquals(150, a.kcal)
        assertEquals("gentle", a.tone)
        assertTrue(a.training.startsWith("Recovery day"))
        assertEquals(0, DailyCheckin.adjust(bad, "lose", teen = true).kcal)
        assertEquals(0, DailyCheckin.adjust(bad, "gain").kcal)
        assertEquals(100, DailyCheckin.adjust(DailyCheckin.Checkin(5.5, null, null, null), "lose").kcal)
        val good = DailyCheckin.Checkin(8.0, 5, 1, 4)
        assertEquals(0, DailyCheckin.adjust(good, "lose").kcal)
        assertEquals("push", DailyCheckin.adjust(good, "lose").tone)
        assertEquals(0, DailyCheckin.adjust(null, "lose").kcal)
    }

    @Test fun recoveryBands() {
        assertEquals(100, DailyCheckin.hrScore(60, 60))
        assertEquals(30, DailyCheckin.hrScore(70, 60))
        assertEquals(79, DailyCheckin.hrScore(63, 60))
        assertNull(DailyCheckin.recovery(null, null, null, null))
        assertEquals(100, DailyCheckin.recovery(8.0, 5, 1, 5)!!.score)
        assertEquals("push", DailyCheckin.recovery(8.0, 5, 1, 5)!!.band)
        assertEquals("deload", DailyCheckin.recovery(4.0, null, 5, 1)!!.band)
        assertEquals("normal", DailyCheckin.recovery(7.0, null, 3, 3)!!.band)
        assertEquals(90, DailyCheckin.recovery(8.0, 5, 1, 5, trainedDaysInRow = 4)!!.score)
        assertEquals("health_connect", DailyCheckin.recovery(7.0, null, null, null, 72, 60, hcSleep = true)!!.source)
        assertEquals(2, DailyCheckin.trainedInRow(listOf("2026-09-28", "2026-09-27", "2026-09-25"), "2026-09-29"))
        assertEquals(62, DailyCheckin.baseline(listOf(60, 70, 62)))
        assertNull(DailyCheckin.baseline(listOf(60, 70)))
    }

    // ---- B7 supplements ----

    @Test fun supplementStreaks() {
        assertEquals(Supplements.Streak(2, 2, false), Supplements.streak(listOf("2026-09-27", "2026-09-28"), "2026-09-29"))
        assertEquals(Supplements.Streak(3, 3, true), Supplements.streak(listOf("2026-09-27", "2026-09-28", "2026-09-29"), "2026-09-29"))
        assertEquals(Supplements.Streak(1, 3, false), Supplements.streak(listOf("2026-09-20", "2026-09-21", "2026-09-22", "2026-09-28"), "2026-09-29"))
        assertEquals("2 capsules", Supplements.doseText(2.0, "capsule"))
        assertEquals("1 scoop", Supplements.doseText(1.0, "scoop"))
        assertEquals("5 g", Supplements.doseText(5.0, "g"))
        assertEquals("", Supplements.doseText(null, "g"))
        assertTrue(Supplements.dueNow(true, "08:00", false, 8 * 60 + 1))
        assertFalse(Supplements.dueNow(true, "08:00", true, 9 * 60))
        assertFalse(Supplements.dueNow(true, "08:00", false, 7 * 60))
    }

    // ---- B11 consistency ----

    @Test fun consistencyScore() {
        fun days(f: (Int) -> Consistency.Day) = (0 until 14).map(f)
        val start = LocalDate.parse("2026-09-16")
        val perfect = Consistency.score(days { Consistency.Day(start.plusDays(it.toLong()).toString(), true, 130.0, it % 2 == 0, 8.0, null) }, 120, 3)
        assertEquals(100, perfect.score)
        assertEquals(4, perfect.parts.size)
        assertEquals("Locked in", perfect.label)
        val none = Consistency.score(days { Consistency.Day(start.plusDays(it.toLong()).toString(), false, 0.0, false, null, null) }, 120, 3)
        assertEquals(0, none.score)
        assertEquals(3, none.parts.size)
        val half = Consistency.score(days { Consistency.Day(start.plusDays(it.toLong()).toString(), it < 7, 130.0, false, null, null) }, 120, 3)
        assertEquals(Math.round((50 * 35 + 50 * 25 + 0 * 25) / 85.0).toInt(), half.score)
        assertEquals("Training", half.weakest)
    }

    // ---- B9 festival + B6 cycle ----

    @Test fun festivalMode() {
        val modes = listOf(Festival.Mode("1", "diwali", "Diwali", "2026-10-17", "2026-10-21"))
        assertEquals("Diwali", Festival.active(modes, "2026-10-18")?.name)
        assertNull(Festival.active(modes, "2026-10-15"))
        assertEquals(2, Festival.upcoming(modes, "2026-10-15")?.second)
        assertNull(Festival.upcoming(modes, "2026-10-10"))
        assertEquals(listOf("2026-10-17", "2026-10-18", "2026-10-19"), Festival.protectedDates(modes, "2026-10-19"))
        assertEquals(550, Festival.maintenanceBump(1800, 2350))
        assertEquals(0, Festival.maintenanceBump(2800, 2350))
        assertNotNull(Festival.validate("2026-10-20", "2026-10-19", "2026-10-15"))
        assertNotNull(Festival.validate("2026-10-01", "2026-10-30", "2026-10-15"))
        assertNull(Festival.validate("2026-10-17", "2026-10-21", "2026-10-15"))
    }

    @Test fun cyclePhases() {
        val s = Cycle.Settings(true, "2026-09-01", 28, 5)
        assertEquals("menstrual", Cycle.today(s, "2026-09-01")!!.phase)
        assertEquals("follicular", Cycle.today(s, "2026-09-08")!!.phase)
        assertEquals("ovulation", Cycle.today(s, "2026-09-14")!!.phase)
        assertEquals("luteal", Cycle.today(s, "2026-09-22")!!.phase)
        assertEquals(1, Cycle.today(s, "2026-09-29")!!.day)
        assertNull(Cycle.today(s, "2026-08-30"))
        assertNull(Cycle.today(s.copy(enabled = false), "2026-09-10"))
    }

    // ---- B3 why, B1 voice ----

    @Test fun targetsWhyBothLanguages() {
        val w = TargetsWhy.why(2200, 2090, -0.1, -0.5, 2180)
        assertEquals("down", w.direction)
        assertEquals(w.en.size, w.hi.size)
        assertTrue(w.en.joinToString(" ").contains("trimming 110 kcal"))
        assertTrue(w.hi.joinToString(" ").contains("कम कर रहे हैं"))
        assertEquals("same", TargetsWhy.why(2000, 2000, -0.5, -0.5, 2000).direction)
    }

    @Test fun voiceText() {
        assertEquals("120 grams protein left, 450 calories", VoiceCoach.speakable("**120 g** protein left 💪 · 450 kcal"))
        assertEquals("2,000 to 2,100 calories", VoiceCoach.speakable("2,000 → 2,100 kcal"))
        assertTrue(VoiceCoach.isStopPhrase("Stop"))
        assertTrue(VoiceCoach.isStopPhrase("bas karo"))
        assertFalse(VoiceCoach.isStopPhrase("stop eating sugar?"))
    }

    // ---- B10 fasting presets ----

    @Test fun fastingWindows() {
        val (rise, set) = FastingPresets.sunTimes("2026-09-23", 28.61, 77.21)
        assertTrue("sunrise $rise", abs(rise - (6 * 60 + 12)) <= 12)
        assertTrue("sunset $set", abs(set - (18 * 60 + 17)) <= 12)
        FastingPresets.PRESETS.forEach { p ->
            val w = FastingPresets.window(p, "2026-09-23")
            assertTrue("${p.key} ${w.hours}", w.hours in 10.0..26.0)
            assertEquals(w.hours, Fasting.clampHours(w.hours), 0.0)
        }
        val ek = FastingPresets.window(FastingPresets.PRESETS.first { it.key == "ekadashi" }, "2026-09-23")
        assertTrue(abs(ek.hours - 24) <= 0.5)
        assertNotNull(FastingPresets.window(FastingPresets.PRESETS.first { it.key == "ramadan" }, "2026-09-23").sehri)
        assertEquals(240, FastingPresets.backdateMin(FastingPresets.Window(360, 1080, 12.0, ""), 600))
        assertEquals(0, FastingPresets.backdateMin(FastingPresets.Window(360, 1080, 12.0, ""), 1200))
    }

    // ---- C1 home workouts ----

    @Test fun homeTemplatesUseTheLibrary() {
        assertEquals(4, HomeWorkouts.TEMPLATES.size)
        for (t in HomeWorkouts.TEMPLATES) for (d in t.days) for (x in d.exercises) {
            assertNotNull("${x.name} has no muscle mapping", MuscleMap.of(x.name))
            assertTrue("${x.name} has no regions", x.muscles.isNotEmpty())
        }
        assertTrue(HomeWorkouts.TEMPLATES.filter { it.equipment == "none" }.all { t -> t.days.all { d -> d.exercises.none { it.name.startsWith("Band") } } })
        assertTrue(HomeWorkouts.routine(HomeWorkouts.TEMPLATES[0]).active)
    }

    // ---- C3 sports ----

    @Test fun sportsBurn() {
        assertEquals(listOf("cricket", "football", "badminton", "kabaddi"), Sports.SPORTS.map { it.key })
        assertEquals(490.0, Sports.kcal(7.0, 70.0, 60), 0.001)
        assertEquals(288.0, Sports.kcal(4.8, null, 60), 0.001)
        assertEquals(0.73, Sports.strideM(175.0), 0.0001)
        assertEquals(0.72, Sports.strideM(null), 0.0001)
        val s = Sports.steps(10000, 70.0, 175.0, "brisk")
        assertEquals(7.3, s.km, 0.0001)
        assertEquals(91, s.minutes)
        assertEquals(Math.round((4.3 - 1) * 70 * 91 / 60.0 * 10) / 10.0, s.kcal, 0.0001)
    }

    // ---- C2 form check ----

    private fun pose(knee: Double): Map<Int, FormCheck.P> {
        val rad = Math.toRadians(180 - knee)
        return mapOf(
            11 to FormCheck.P(0.5, 0.2, 0.99), 23 to FormCheck.P(0.5, 0.5, 0.99), 25 to FormCheck.P(0.5, 0.7, 0.99),
            27 to FormCheck.P(0.5 + sin(rad) * 0.2, 0.7 + cos(rad) * 0.2, 0.99),
        )
    }

    @Test fun repCounter() {
        assertEquals(90.0, FormCheck.angle(FormCheck.P(0.0, 0.0), FormCheck.P(1.0, 0.0), FormCheck.P(1.0, 1.0)), 0.001)
        assertEquals(90.0, FormCheck.measure("squat", pose(90.0))!!.main, 0.5)
        var s = FormCheck.State("squat")
        var t = 0L
        fun frames(xs: List<Double>) { xs.forEach { a -> t += 100; s = FormCheck.step(s, FormCheck.measure("squat", pose(a)), t) } }
        repeat(3) { frames(listOf(170.0, 150.0, 120.0, 100.0, 85.0, 85.0, 85.0, 100.0, 130.0, 160.0, 172.0, 172.0, 172.0, 172.0, 172.0)) }
        assertEquals(3, s.reps)
        assertEquals(3, s.goodReps)
        frames(listOf(170.0, 150.0, 125.0, 108.0, 108.0, 108.0, 108.0, 125.0, 150.0, 170.0, 172.0, 172.0, 172.0, 172.0))
        assertEquals(4, s.reps)
        assertEquals("depth", s.last)
        val sum = FormCheck.summary(s)
        assertEquals(75, sum.clean)
        assertEquals(4, FormCheck.step(s, null, t).reps)
        assertNull(FormCheck.measure("squat", emptyMap()))
    }
}
