package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.16 cover presets: ids shared with the web (src/lib/covers.ts). */
class CoversTest {
    @Test fun thirtyFourInBoardOrder() {
        assertEquals(34, Covers.ALL.size)
        assertEquals("plates-light", Covers.ALL[0].id)
        assertEquals(1, Covers.ALL[0].n)
        assertEquals("plates-dark", Covers.ALL[1].id)
        assertEquals("chalk-light", Covers.ALL[10].id)
        assertEquals("ember-dark", Covers.ALL[33].id)
        assertEquals(34, Covers.ALL.map { it.id }.toSet().size)
    }

    @Test fun categoriesCoverEverything() {
        assertEquals(34, Covers.Category.entries.sumOf { Covers.inCategory(it).size })
        assertEquals(8, Covers.inCategory(Covers.Category.IRON).size)
        assertTrue(Covers.inCategory(null).size == 34)
    }

    @Test fun resolveFallsBackToTheDevice() {
        assertEquals("plates-light", Covers.resolve(null, null))
        assertEquals("rings-dark", Covers.resolve(null, "rings-dark"))
        assertEquals("pool-light", Covers.resolve("pool-light", "rings-dark"))
        assertEquals("rings-dark", Covers.resolve("nonsense", "rings-dark"))
        assertEquals("plates-light", Covers.of("nope").id)
    }
}
