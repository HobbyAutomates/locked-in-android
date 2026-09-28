package com.sohum.bandlog.util

/** v2.15 buddy requests: the pure bits (the code in a notification URL, the RPC's text reply). */
object BuddyLinks {
    /** "/buddy/ABC234" (or a full …/buddy/abc234 link) → "ABC234"; null for anything else. */
    fun codeFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val segs = url.substringBefore('?').substringBefore('#').split('/').filter { it.isNotBlank() }
        val at = segs.indexOf("buddy")
        if (at < 0) return null
        return segs.getOrNull(at + 1)?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.length == 6 }
    }

    /** A `returns text` RPC body: `"ABC234"` → ABC234, `null` / empty → null. */
    fun textReply(body: String): String? {
        val t = body.trim()
        if (t.isEmpty() || t == "null") return null
        return t.trim('"').ifBlank { null }
    }

    /** The first name for "Buddy up with Ayaan?". */
    fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { "your squadmate" }
}
