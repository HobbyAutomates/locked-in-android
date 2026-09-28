package com.sohum.bandlog.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Test

/** v2.16: a motion whose frame loop never ran still reads its true value (no frozen count-ups / blurs). */
class LiveProgressTest {
    @Test fun finishedEvenWithoutTheFrameLoop() {
        val p = LiveProgress(MotionSession.nowMs() - 10_000, 1800)
        assertEquals(1f, p.value, 0f)
    }

    @Test fun notStartedYetIsZero() {
        val p = LiveProgress(MotionSession.nowMs() + 60_000, 1800)
        assertEquals(0f, p.value, 0f)
    }
}
