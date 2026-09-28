package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v2.18 D10 transformation story (port of the web's src/lib/social/story.ts). */
class StoryTest {
    @Test fun framesOldestFirstWithWeightsAndDeltas() {
        val photos = listOf(Story.Photo("2026-09-01", "b", null), Story.Photo("2026-08-01", "a", 82.0), Story.Photo("2026-09-10", null, null))
        val weights = listOf(Story.Weight("2026-08-28", 80.1), Story.Weight("2026-09-20", 79.0))
        val f = Story.frames(photos, weights)
        assertEquals(listOf("2026-08-01", "2026-09-01"), f.map { it.date })
        assertEquals(80.1, f[1].kg!!, 0.0)
        assertEquals(-1.9, f[1].delta!!, 0.0)
        assertEquals(31, f[1].dayIndex)
        assertEquals("Day 1" to null, Story.caption(f[0]))
        assertEquals("Day 32" to "−1.9 kg", Story.caption(f[1]))
        assertNull(Story.weightNear("2026-09-10", listOf(Story.Weight("2026-09-01", 80.0))))
        assertEquals(0, Story.reelLength(0))
        assertEquals(1600 + 2 * 1400 + 2200, Story.reelLength(2))
    }

    /** The same values as the web's check-v218-social.ts "story" group. */
    @Test fun webCheckValues() {
        val weights = listOf(Story.Weight("2026-06-01", 84.0), Story.Weight("2026-09-25", 79.8))
        assertEquals(84.0, Story.weightNear("2026-06-04", weights)!!, 0.0)
        assertNull(Story.weightNear("2026-06-20", weights))
        val f = Story.frames(listOf(Story.Photo("2026-09-28", "b", null), Story.Photo("2026-06-01", "a", null), Story.Photo("2026-07-01", null, 82.0)), weights)
        assertEquals(listOf(listOf<Any?>("2026-06-01", 84.0, 0.0, 0), listOf<Any?>("2026-09-28", 79.8, -4.2, 119)), f.map { listOf(it.date, it.kg, it.delta, it.dayIndex) })
        assertEquals("Day 120" to "−4.2 kg", Story.caption(f[1]))
        assertEquals("Day 1" to null, Story.caption(f[0]))
        val many = (0 until 60).map { Story.Photo(Dates.addDays("2026-01-01", it.toLong()), "u$it", null) }
        val thin = Story.frames(many, emptyList())
        assertEquals(Story.MAX_FRAMES, thin.size)
        assertEquals(Dates.addDays("2026-01-01", 59), thin.last().date)
    }

    @Test fun thinsToTwentyFourKeepingEnds() {
        val photos = (0 until 50).map { Story.Photo(Dates.addDays("2026-01-01", it.toLong()), "p$it", null) }
        val f = Story.frames(photos, emptyList())
        assertEquals(24, f.size)
        assertEquals("2026-01-01", f.first().date)
        assertEquals(Dates.addDays("2026-01-01", 49), f.last().date)
    }
}
