package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.14 pure rules: onboarding maths (port of the web's onboardingV2.ts), milestones, day glow. */
class V214Test {
    private val today = "2026-09-26"

    @Test fun recompIsASlowCutAndTeensNeverLose() {
        assertEquals("lose", OnboardingV2.goalTypeOf("recomp", 25))
        assertEquals("maintain", OnboardingV2.goalTypeOf("lose", 16))
        assertEquals("gain", OnboardingV2.goalTypeOf("gain", 16))
        assertEquals("maintain", OnboardingV2.goalTypeOf("habits", 30))
    }

    @Test fun pacesPerBand() {
        assertEquals(0.5, OnboardingV2.paceKg("lose", "steady"), 1e-9)
        assertEquals(0.75, OnboardingV2.paceKg("lose", "aggressive"), 1e-9)
        assertEquals(0.25, OnboardingV2.paceKg("gain", "steady"), 1e-9)
        assertEquals(0.0, OnboardingV2.paceKg("maintain", "steady"), 1e-9)
    }

    @Test fun coachCappedAtBalancedUnder18() {
        assertEquals("balanced", OnboardingV2.effectiveCoachStyle("no_excuses", 16))
        assertEquals("no_excuses", OnboardingV2.effectiveCoachStyle("no_excuses", 22))
        assertEquals("balanced", OnboardingV2.effectiveCoachStyle("nonsense", 22))
    }

    @Test fun planHasAGoalDateForALoss() {
        val a = OnboardingV2.Answers(goal = "lose", heightCm = 175.0, weightKg = 72.0, dob = "2000-01-01", gender = "male", goalWeightKg = 66.0, pace = "steady", trainingDays = 4)
        val p = OnboardingV2.plan(a, today)
        assertNotNull(p)
        p!!
        assertEquals(12, p.weeks) // 6 kg at 0.5 kg/week
        assertEquals(Dates.addDays(today, 84), p.goalDate)
        assertEquals(3, p.reasons.size)
        assertTrue(p.targets.fiber in 25..40)
    }

    @Test fun teenPlanHasNoDate() {
        val a = OnboardingV2.Answers(goal = "lose", heightCm = 165.0, weightKg = 60.0, dob = "2011-03-01", gender = "female", goalWeightKg = 55.0, pace = "aggressive")
        val p = OnboardingV2.plan(a, today)!!
        assertTrue(p.teen)
        assertNull(p.goalDate)
    }

    @Test fun answersRoundTrip() {
        val a = OnboardingV2.Answers(heardFrom = "friend", goal = "gain", obstacles = listOf("exam_stress"), sports = listOf("Gym", "Cricket"), trainingDays = 4, coachStyle = "calm")
        assertEquals(a, OnboardingV2.Answers.from(a.toJson()))
    }

    @Test fun streakMilestonesFloodOnlyInTheirFirstWeek() {
        val (fire, silent) = Milestones.current(today, 31, null, null, "maintain", emptyList())
        assertEquals(listOf("streak_30"), fire.map { it.key })
        assertTrue("streak_7" in silent)
        val (fire2, silent2) = Milestones.current(today, 40, null, null, "maintain", emptyList())
        assertTrue(fire2.isEmpty())
        assertTrue("streak_30" in silent2 && "streak_7" in silent2)
    }

    @Test fun goalReachedKey() {
        val (fire, _) = Milestones.current(today, 0, 65.8, 66.0, "lose", emptyList())
        assertEquals("goal_reached:66", fire.single().key)
    }
}
