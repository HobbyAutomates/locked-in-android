package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** v2.18 E4: only newer Health Connect weigh-ins on days without an entry are copied. */
class WeightImportTest {
    @Test fun newerDaysOnlyRoundedOldestFirst() {
        val hc = listOf("2026-09-20" to 80.04, "2026-09-26" to 79.66, "2026-09-25" to 79.9, "2026-09-27" to 5.0)
        assertEquals(listOf("2026-09-25" to 79.9, "2026-09-26" to 79.7), WeightImport.weightsToImport(hc, listOf("2026-09-22", "2026-09-10")))
        assertEquals(listOf("2026-09-20" to 80.0, "2026-09-25" to 79.9, "2026-09-26" to 79.7), WeightImport.weightsToImport(hc, emptyList()))
        assertEquals(emptyList<Pair<String, Double>>(), WeightImport.weightsToImport(hc, listOf("2026-09-26")))
    }
}
