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
        assertTrue(shouldStartTour(seenOnDevice = false, eligible = true, onHome = true, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = true, eligible = true, onHome = true, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = false, eligible = false, onHome = true, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = false, eligible = true, onHome = false, overlayOpen = false))
        assertFalse(shouldStartTour(seenOnDevice = false, eligible = true, onHome = true, overlayOpen = true))
    }

    /** v2.17: new accounts only (created after the v2.16 release) and tour_seen_at still null. */
    @Test fun newAccountsOnly() {
        assertTrue(isTourEligible("2026-09-29T08:15:30.123456+00:00", null))
        assertTrue(isTourEligible("2026-09-28T21:00:01Z", null))
        assertTrue(isTourEligible("2026-09-29T02:31:00+05:30", null))
        // Existing users (created before the release, or at it) never see it.
        assertFalse(isTourEligible("2026-09-28T21:00:00Z", null))
        assertFalse(isTourEligible("2026-08-01T10:00:00+00:00", null))
        assertFalse(isTourEligible("2026-09-29T02:29:00+05:30", null))
        // Unknown created_at counts as old.
        assertFalse(isTourEligible(null, null))
        assertFalse(isTourEligible("not a date", null))
    }

    @Test fun seenOnTheProfileStopsIt() {
        assertFalse(isTourEligible("2026-09-30T08:00:00+00:00", "2026-09-30T08:05:00+00:00"))
        assertFalse(isTourEligible("2026-09-30T08:00:00+00:00", null, seenFlagV216 = true))
        assertTrue(isTourEligible("2026-09-30T08:00:00+00:00", ""))
    }

    @Test fun parsesSupabaseTimestamps() {
        assertEquals(java.time.Instant.parse("2026-09-29T08:15:30Z"), parseInstant("2026-09-29 08:15:30+00"))
        assertEquals(java.time.Instant.parse("2026-09-29T08:15:30.5Z"), parseInstant("2026-09-29T08:15:30.5+00:00"))
    }
}
