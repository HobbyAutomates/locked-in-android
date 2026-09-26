package com.sohum.bandlog.util

import com.sohum.bandlog.data.InboxItem
import com.sohum.bandlog.data.Lift
import com.sohum.bandlog.data.LiftSet
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.notify.InboxWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** v2.13 platform: the same vectors as the web's scripts/check-platform.ts. */
class ProTest {
    private val now = Instant.parse("2026-09-26T10:00:00Z")

    @Test fun betaAndProHavePro() {
        assertTrue(Pro.hasPro("beta", null, now = now))
        assertTrue(Pro.hasPro("pro", null, now = now))
        assertTrue(Pro.hasPro("PRO ", null, now = now))
    }

    @Test fun freeHasNoPro() {
        assertFalse(Pro.hasPro("free", null, now = now))
        assertFalse(Pro.hasPro(null, null, now = now))
        assertFalse(Pro.hasPro("free", "2030-01-01T00:00:00Z", now = now))
    }

    @Test fun proUntilMustBeInTheFuture() {
        assertTrue(Pro.hasPro("pro", "2026-10-01T00:00:00+00:00", now = now))
        assertFalse(Pro.hasPro("pro", "2026-09-01T00:00:00+00:00", now = now))
        assertFalse(Pro.hasPro("beta", "2026-09-26 09:59:59+00", now = now))
        assertTrue(Pro.hasPro("beta", "2026-09-26 10:00:01+00", now = now))
    }

    @Test fun missingColumnMeansPro() {
        assertTrue(Pro.hasPro(null, null, planPresent = false, now = now))
        assertTrue(Pro.hasPro("free", "2000-01-01T00:00:00Z", planPresent = false, now = now))
    }

    @Test fun configFallbackAndParse() {
        assertEquals("₹700 / month", Pro.DEFAULT_CONFIG.priceLabel)
        assertEquals(Pro.DEFAULT_CONFIG, Pro.parseConfig(null))
        val c = Pro.parseConfig(org.json.JSONObject("""{"price_inr": 900, "period": "month", "beta_all_pro": true, "payments_enabled": true}"""))
        assertEquals(900, c.priceInr)
        assertTrue(c.paymentsEnabled)
    }

    @Test fun paywallAndButton() {
        Pro.Feature.entries.forEach { assertTrue(Pro.canUse(it, hasPro = true)); assertFalse(Pro.canUse(it, hasPro = false)) }
        assertEquals("You're in the beta", Pro.buttonLabel("beta", true, Pro.DEFAULT_CONFIG))
        assertFalse(Pro.buttonEnabled(true, Pro.DEFAULT_CONFIG))
        // Payments off: even a free user can't press it.
        assertFalse(Pro.buttonEnabled(false, Pro.DEFAULT_CONFIG))
        assertTrue(Pro.buttonEnabled(false, Pro.DEFAULT_CONFIG.copy(paymentsEnabled = true)))
    }
}

class TrainingTest {
    private fun w(id: String, date: String, vararg sets: Pair<Double, Int>, name: String = "Bench press") =
        Workout(id, date, emptyList(), "Medium", null, 45, "", "", "gym", listOf(Lift(name, sets.map { LiftSet(it.first, it.second) })))

    @Test fun epley() {
        assertEquals(100.0, Training.epley(100.0, 1), 1e-9)
        assertEquals(133.333, Training.epley(100.0, 10), 1e-3)
        assertEquals(116.667, Training.epley(100.0, 5), 1e-3)
        assertEquals(68.0, Training.epley(60.0, 4), 1e-9)
        assertEquals(0.0, Training.epley(0.0, 5), 1e-9)
        assertEquals(0.0, Training.epley(80.0, 0), 1e-9)
    }

    @Test fun bestSetIsHighestE1rm() {
        val b = Training.bestSet(listOf(LiftSet(100.0, 3), LiftSet(90.0, 8), LiftSet(null, 12)))!!
        // 90 × 8 = 114.0 beats 100 × 3 = 110.0
        assertEquals(90.0, b.kg!!, 1e-9)
        assertNull(Training.bestSet(listOf(LiftSet(null, 10), LiftSet(40.0, null))))
    }

    @Test fun historyFlagsPrs() {
        val ws = listOf(
            w("a", "2026-09-01", 60.0 to 8),
            w("b", "2026-09-05", 60.0 to 6),
            w("c", "2026-09-09", 62.5 to 8),
            w("d", "2026-09-12", 65.0 to 5, name = "Squat"),
        )
        val h = Training.history(ws, "bench press")
        assertEquals(listOf("a", "b", "c"), h.map { it.workoutId })
        assertEquals(listOf(false, false, true), h.map { it.pr })
        assertEquals(79.2, h.last().e1rm, 1e-9) // 62.5 × (1 + 8/30) = 79.1667 → 79.2
        assertEquals(listOf("Bench press" to 3, "Squat" to 1), Training.exercisesWithHistory(ws))
        assertEquals(1, Training.prsBetween(ws, "2026-09-06", "2026-09-30").size)
    }
}

class MuscleMapTest {
    @Test fun eighteenRegions() {
        assertEquals(18, MuscleMap.Region.entries.size)
        MuscleMap.Region.entries.forEach { assertTrue("${it.key} on no view", it.front || it.back) }
    }

    @Test fun everyLibraryExerciseIsMapped() {
        Lifts.ALL.forEach { e ->
            val t = MuscleMap.of(e.name)
            assertNotNull("${e.name} has no muscle map", t)
            assertTrue("${e.name} has no primary", t!!.primary.isNotEmpty())
            assertTrue("${e.name} lists a muscle twice", t.primary.intersect(t.secondary.toSet()).isEmpty())
        }
        assertEquals(Lifts.ALL.size, MuscleMap.TABLE.size)
    }

    @Test fun sanity() {
        assertEquals(listOf(MuscleMap.Region.CHEST), MuscleMap.of("Bench press")!!.primary)
        assertTrue(MuscleMap.Region.LATS in MuscleMap.of("pull-up")!!.primary)
        assertTrue(MuscleMap.Region.QUADS in MuscleMap.of("Squat")!!.primary)
        assertTrue(MuscleMap.Region.HAMSTRINGS in MuscleMap.of("Romanian deadlift")!!.primary)
        assertTrue(MuscleMap.Region.SIDE_DELTS in MuscleMap.of("Lateral raise")!!.primary)
        assertNotNull(MuscleMap.of("Farmer's walk"))
        assertNull(MuscleMap.of("Underwater basket weaving"))
    }

    @Test fun weeklySets() {
        val bench = Workout("a", "2026-09-21", emptyList(), "Medium", null, 40, "", "", "gym", listOf(Lift("Bench press", List(4) { LiftSet(60.0, 8) })))
        val bands = Workout("b", "2026-09-22", listOf("Back"), "Medium", null, 30, "", "", Workout.BANDS)
        val s = MuscleMap.setsPerRegion(listOf(bench, bands))
        assertEquals(4.0, s[MuscleMap.Region.CHEST]!!, 1e-9)
        assertEquals(2.0, s[MuscleMap.Region.TRICEPS]!!, 1e-9)
        assertEquals(3.0, s[MuscleMap.Region.LATS]!!, 1e-9)
        assertEquals(0f, MuscleMap.heat(0.0))
        assertEquals(0.75f, MuscleMap.heat(10.0), 1e-6f)
        assertEquals(1f, MuscleMap.heat(25.0))
        assertEquals("In range", MuscleMap.verdict(12.0))
    }
}

class RoutinesTest {
    @Test fun templatesUseLibraryExercises() {
        assertEquals(4, Routines.TEMPLATES.size)
        Routines.TEMPLATES.flatMap { it.days }.flatMap { it.exercises }.forEach { assertNotNull("${it.name} not in library", Lifts.find(it.name)) }
    }

    @Test fun jsonRoundTrip() {
        val t = Routines.TEMPLATES[0]
        val back = Routines.daysFrom(Routines.daysToJson(t.days).toString())
        assertEquals(t.days, back)
        // Tolerates reps as a string range and missing weekday.
        val odd = Routines.daysFrom("""[{"name":"A","exercises":[{"name":"Squat","sets":"4","reps":"8-12"}]}]""")
        assertEquals(Routines.Day("A", null, listOf(Routines.Exercise("Squat", 4, 8, 90))), odd.single())
    }

    @Test fun todayByWeekday() {
        val ppl = Routines.TEMPLATES[0] // Mon Push, Wed Pull, Fri Legs
        assertEquals("Push", Routines.today(ppl, "2026-09-21").day?.name) // Monday
        val tue = Routines.today(ppl, "2026-09-22")
        assertNull(tue.day)
        assertEquals("Pull", tue.next?.name)
        assertEquals(3, tue.nextWeekday)
        val sat = Routines.today(ppl, "2026-09-26")
        assertEquals("Push", sat.next?.name) // wraps to Monday
    }

    @Test fun todayByRotation() {
        val r = Routines.Routine(name = "AB", days = listOf(Routines.Day("A"), Routines.Day("B")))
        assertEquals("A", Routines.today(r, "2026-09-26", emptyList()).day?.name)
        assertEquals("B", Routines.today(r, "2026-09-26", listOf("2026-09-24")).day?.name)
        assertEquals("A", Routines.today(r, "2026-09-26", listOf("2026-09-24", "2026-09-25", "2026-09-26")).day?.name)
        // Sessions before the routine started don't count.
        assertEquals("A", Routines.today(r, "2026-09-26", listOf("2026-09-01"), startDate = "2026-09-20T08:00:00Z").day?.name)
    }
}

class ProteinNudgeTest {
    @Test fun condition() {
        fun go(p: Double, target: Int = 120, logged: Boolean = true, age: Int? = 30, goal: String = "maintain", sent: Boolean = false, on: Boolean = true) =
            ProteinNudge.shouldNudge(on, p, target, logged, age, goal, sent)
        assertTrue(go(83.9))       // < 84 (70 % of 120)
        assertFalse(go(84.0))
        assertFalse(go(20.0, logged = false))
        assertFalse(go(20.0, sent = true))
        assertFalse(go(20.0, on = false))
        assertFalse(go(20.0, age = 16, goal = "lose")) // never for under-18 loss
        assertTrue(go(20.0, age = 16, goal = "maintain"))
        assertTrue(go(20.0, age = 30, goal = "lose"))
    }

    @Test fun texts() {
        assertEquals(70, ProteinNudge.shortBy(50.4, 120))
        assertEquals("You're 70 g short on protein", ProteinNudge.title(70))
        assertEquals("Try a, b or c", ProteinNudge.body(listOf("a", "b", "c")))
        assertEquals(16 to 0, ProteinNudge.parseTime(null))
        assertEquals(18 to 30, ProteinNudge.parseTime("18:30:00"))
        assertEquals("07:05", ProteinNudge.formatTime(7, 5))
    }

    @Test fun picksRespectDietMode() {
        val veg = ProteinNudge.picks(60, "vegetarian")
        assertEquals(3, veg.size)
        assertTrue(veg.none { "egg" in it || "chicken" in it || "fish" in it })
        val vegan = ProteinNudge.picks(60, "vegan")
        assertTrue(vegan.none { "paneer" in it || "curd" in it || "milk" in it || "egg" in it })
    }
}

class RecapsTest {
    @Test fun periods() {
        val w = Recaps.lastWeek("2026-09-28") // a Monday
        assertEquals("2026-09-21", w.from); assertEquals("2026-09-27", w.to)
        val m = Recaps.lastMonth("2026-10-01")
        assertEquals("2026-09-01", m.from); assertEquals("2026-09-30", m.to)
        assertEquals(listOf("month-2026-09-01"), Recaps.dueToday("2026-10-01").map { it.key }) // a Thursday
        assertEquals(listOf("week-2026-09-21"), Recaps.dueToday("2026-09-28").map { it.key })
        assertTrue(Recaps.dueToday("2026-09-26").isEmpty())
    }
}

class InboxTest {
    private fun item(id: String, kind: String = "nudge", read: String? = null) = InboxItem(id, kind, "t", "b", null, "2026-09-26T10:00:00Z", read)

    @Test fun postsOnlyUnreadUnshown() {
        val rows = listOf(item("1"), item("2"), item("3", read = "2026-09-26T11:00:00Z"), item("4", kind = "protein"))
        assertEquals(listOf("2", "4"), InboxWorker.toPost(rows, setOf("1"), firedToday = { false }).map { it.id })
        // A protein notice this phone already fired today isn't posted twice.
        assertEquals(listOf("2"), InboxWorker.toPost(rows, setOf("1"), firedToday = { it == "protein" }).map { it.id })
    }
}
