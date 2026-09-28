package com.sohum.bandlog.ui.tour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.16 guided tour (boards TourF1–TourF5). */
class TourTest {
    @Test fun fiveStopsInBoardOrder() {
        assertEquals(listOf("calories", "coach", "plus", "scan", "squad"), TourStop.entries.map { it.key })
        assertEquals("Your day at a glance", TourStop.CALORIES.title)
        assertEquals("Your squad", TourStop.SQUAD.title)
    }

    @Test fun startsOnceOverAClearHome() {
        assertTrue(shouldStartTour(seenOnDevice = false, seenOnProfile = false, onHome = true, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = true, seenOnProfile = false, onHome = true, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = false, seenOnProfile = true, onHome = true, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = false, seenOnProfile = false, onHome = false, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = false, seenOnProfile = false, onHome = true, overlayOpen = true))
    }
}
