package com.sohum.bandlog.util

import com.sohum.bandlog.util.MuscleMap.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v2.17 "Muscles this week" one-liner. */
class MusclesWeekTest {
    @Test fun topThreeGroupsAndSessions() {
        val sets = mapOf(Region.CHEST to 9.0, Region.LATS to 6.0, Region.UPPER_BACK to 4.0, Region.QUADS to 8.0, Region.BICEPS to 2.0)
        assertEquals("Back, chest, legs · 4 sessions", MuscleMap.weekLine(sets, 4))
    }

    @Test fun singularAndEmpty() {
        assertEquals("Chest · 1 session", MuscleMap.weekLine(mapOf(Region.CHEST to 3.0), 1))
        assertEquals("2 sessions", MuscleMap.weekLine(emptyMap(), 2))
        assertNull(MuscleMap.weekLine(emptyMap(), 0))
    }
}
