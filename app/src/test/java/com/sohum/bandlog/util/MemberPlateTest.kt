package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v2.17 member plate: FOUNDER for the owner, "OG #nn" for the first 50, else none. */
class MemberPlateTest {
    @Test fun founderWins() {
        assertEquals(MemberPlate.Founder, MemberPlate.of(isFounder = true, memberNo = 1))
        assertEquals(MemberPlate.Founder, MemberPlate.of(isFounder = true, memberNo = 300))
        assertEquals(MemberPlate.Founder, MemberPlate.of(isFounder = true, memberNo = null))
        assertEquals("FOUNDER", MemberPlate.Founder.label)
    }

    @Test fun firstFiftyAreOg() {
        assertEquals("OG #07", MemberPlate.of(false, 7)?.label)
        assertEquals("OG #01", MemberPlate.of(false, 1)?.label)
        assertEquals("OG #50", MemberPlate.of(false, 50)?.label)
        assertEquals(MemberPlate.Og(12), MemberPlate.of(null, 12))
    }

    @Test fun laterOrUnknownGetsNothing() {
        assertNull(MemberPlate.of(false, 51))
        assertNull(MemberPlate.of(false, 0))
        assertNull(MemberPlate.of(false, null))
        // Pre-v41: both columns missing → no plate at all (not FOUNDER).
        assertNull(MemberPlate.of(null, null))
    }

    @Test fun profileParsing() {
        fun parse(json: String) = com.sohum.bandlog.data.Profile.from(org.json.JSONObject(json))
        assertNull(parse("""{"name":"A"}""").plate)
        assertEquals(MemberPlate.Og(3), parse("""{"member_no":3,"is_founder":false}""").plate)
        assertEquals(MemberPlate.Founder, parse("""{"member_no":2,"is_founder":true}""").plate)
        assertNull(parse("""{"member_no":null,"is_founder":false}""").plate)
    }
}
