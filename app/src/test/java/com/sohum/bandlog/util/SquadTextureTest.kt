package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SquadTextureTest {
    @Test fun fnv1aMatchesReferenceVectors() {
        // Published FNV-1a 32-bit test vectors.
        assertEquals(0x811c9dc5L, SquadTexture.fnv1a(""))
        assertEquals(0xe40c292cL, SquadTexture.fnv1a("a"))
        assertEquals(0xbf9cf968L, SquadTexture.fnv1a("foobar"))
    }

    @Test fun indexIsStableAndInRange() {
        val id = "3f2b8c1e-8d7a-4c55-9a0e-5b6d7e8f9a01"
        val i = SquadTexture.index(id)
        assertEquals(i, SquadTexture.index(id))
        assert(i in 0 until 8)
        assertEquals(SquadTexture.PATTERNS[i], SquadTexture.pattern(id))
    }
}
