package com.sohum.bandlog.util

/**
 * v2.14 squad textures (brand v1, "Ember is earned" idea 03): every squad gets a monochrome pattern
 * instead of a colour, picked from its id so both apps agree. FNV-1a 32-bit over the UTF-8 bytes of
 * the id string (the squad's UUID as Postgres prints it), index = hash mod 8 into [PATTERNS].
 * The web port uses the same function and the same order.
 */
object SquadTexture {
    val PATTERNS = listOf("dots", "stripes", "grid", "waves", "checks", "diagonal", "rings", "zigzag")

    fun fnv1a(s: String): Long {
        var h = 0x811c9dc5L
        for (b in s.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h = (h * 0x01000193L) and 0xffffffffL
        }
        return h
    }

    fun index(id: String): Int = (fnv1a(id) % PATTERNS.size).toInt()

    fun pattern(id: String): String = PATTERNS[index(id)]
}
