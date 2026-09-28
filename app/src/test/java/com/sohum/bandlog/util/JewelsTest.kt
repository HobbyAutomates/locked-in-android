package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.16 jewellery badges: taxonomy shared with the web (src/lib/jewels.ts). */
class JewelsTest {
    private fun badge(name: String) = Badges.ALL.first { it.name == name }

    @Test fun shapesAndCategories() {
        assertEquals(Jewels.Shape.SHIELD, Jewels.shapeOf(Jewels.categoryOf(badge("Rookie"))))
        assertEquals(Jewels.Shape.HEXAGON, Jewels.shapeOf(Jewels.categoryOf(badge("The Logfather"))))
        assertEquals(Jewels.Shape.OCTAGON, Jewels.shapeOf(Jewels.categoryOf(badge("Bullseye"))))
        assertEquals(Jewels.Shape.DIAMOND, Jewels.shapeOf(Jewels.Category.TRAINING))
        assertEquals(Jewels.Shape.ROUND, Jewels.shapeOf(Jewels.Category.SQUAD))
    }

    @Test fun tiersAndIds() {
        assertEquals(Jewels.Tier.PLATINUM, Jewels.tierOf(badge("Immortal")))
        assertEquals(Jewels.Tier.SILVER, Jewels.tierOf(badge("Getting Serious")))
        assertEquals("mission-nutrition", Jewels.idOf(badge("Mission: Nutrition")))
        assertEquals("loyalty-iii", Jewels.idOf(badge("Loyalty III")))
        assertEquals(12, Jewels.items(Badges.Progress(0, 0, 0)).map { it.id }.toSet().size)
    }

    @Test fun unlockLinesMatchTheWebHash() {
        assertEquals("New hardware.", Jewels.unlockLine("rookie"))
        assertEquals("That’s going on the wall.", Jewels.unlockLine("getting-serious"))
        assertEquals("New hardware.", Jewels.unlockLine("bullseye"))
    }

    @Test fun newlyEarnedOnlyAfterFirstRun() {
        val p = Badges.Progress(streakDays = 12, meals = 6, goalDays = 0)
        assertTrue(Jewels.newlyEarned(p, null).isEmpty())
        val fresh = Jewels.newlyEarned(p, setOf("rookie")).map { it.id }
        assertEquals(listOf("getting-serious", "forking-around"), fresh)
    }

    @Test fun topNextAndSquadmateJewel() {
        val p = Badges.Progress(streakDays = 12, meals = 6, goalDays = 0)
        assertEquals("Getting Serious", Jewels.top(p)?.badge?.name)
        assertEquals("Locked In", Jewels.next(p)?.badge?.name) // 12 of 50 is the closest
        assertNull(Jewels.streakJewel(2))
        assertEquals(Jewels.Tier.SILVER, Jewels.streakJewel(19)?.second)
        assertEquals("You logged 10 days in a row and earned Getting Serious, a Silver badge.", Jewels.unlockCaption(badge("Getting Serious")))
    }
}
