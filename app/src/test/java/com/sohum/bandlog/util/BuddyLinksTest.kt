package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuddyLinksTest {
    @Test fun codeFromNotificationUrl() {
        assertEquals("ABC234", BuddyLinks.codeFromUrl("/buddy/ABC234"))
        assertEquals("ABC234", BuddyLinks.codeFromUrl("https://example.app/buddy/abc234?x=1"))
        assertNull(BuddyLinks.codeFromUrl("/buddy"))
        assertNull(BuddyLinks.codeFromUrl("/squad/ABC234"))
        assertNull(BuddyLinks.codeFromUrl("/buddy/ABC"))
        assertNull(BuddyLinks.codeFromUrl(null))
    }

    @Test fun textReply() {
        assertEquals("ABC234", BuddyLinks.textReply("\"ABC234\""))
        assertNull(BuddyLinks.textReply("null"))
        assertNull(BuddyLinks.textReply(" "))
    }

    @Test fun firstName() {
        assertEquals("Ayaan", BuddyLinks.firstName("Ayaan Shah"))
        assertEquals("your squadmate", BuddyLinks.firstName(" "))
    }
}
