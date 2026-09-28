package com.sohum.bandlog.util

/**
 * v2.18 D2 referrals (web docs/schema_v44.sql: profiles.referral_code / referral_pro_days,
 * referrals, RPCs my_referral_code, referral_claim, my_referrals). Pure. Port of the web's
 * src/lib/social/referrals.ts.
 *
 * Invite link: <site>/r/<CODE>. Opening it (the app's /r/ intent filter) stores the code on the
 * device ([REF_STORAGE_KEY]); once signed in, the app claims it once. Both people get 1 week of Pro.
 */
object Referrals {
    const val REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val REF_STORAGE_KEY = "li-ref"
    const val REF_BONUS_DAYS = 7
    const val REF_MAX_ACCOUNT_AGE_DAYS = 30

    /** "ab-c 12x" → "ABC12X"; anything that can't be a code → null. */
    fun normalizeCode(v: Any?): String? {
        if (v !is String) return null
        val c = v.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (c.length != 6) return null
        if (c.any { it !in REF_ALPHABET }) return null
        return c
    }

    fun inviteUrl(origin: String, code: String): String = "${origin.trimEnd('/')}/r/$code"

    /** The share text that goes with the link. */
    fun inviteText(name: String?, url: String): String {
        val who = if (!name.isNullOrBlank()) "${name.trim()} invited you to Locked In." else "Join me on Locked In."
        return "$who Log food in Hinglish, train with a squad, and we both get a week of Pro. $url"
    }

    data class Grant(val kind: String, val plan: String, val proUntil: String?, val bankedDays: Int)

    /**
     * 1 week of Pro for one person. Beta with no end date → 7 days banked. Otherwise
     * pro_until = max(now, pro_until) + 7 days, and a free plan becomes pro.
     */
    fun referralGrant(plan: String, proUntil: String?, nowMs: Long = System.currentTimeMillis(), banked: Int = 0): Grant {
        if (plan == "beta" && proUntil == null) return Grant("banked", plan, null, banked + REF_BONUS_DAYS)
        val cur = LiveSquad.millis(proUntil)
        val base = if (cur != null && cur > nowMs) cur else nowMs
        val until = java.time.Instant.ofEpochMilli(base + REF_BONUS_DAYS * 86_400_000L)
        return Grant("extended", if (plan == "free") "pro" else plan, isoMillis(until), banked)
    }

    /** JS Date.toISOString(): always 3 fraction digits and Z. */
    private fun isoMillis(i: java.time.Instant): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(i)

    fun claimMessage(ok: Boolean, reason: String? = null, referrerName: String? = null): String {
        if (ok) return "You and ${referrerName?.ifBlank { null } ?: "your friend"} both get 1 week of Pro."
        return when (reason) {
            "unknown" -> "That invite code doesn't exist."
            "self" -> "That's your own invite."
            "already" -> "You've already used an invite."
            "too_old" -> "Invites are for new accounts."
            "missing" -> "Invites turn on with the next server update."
            else -> "Couldn't use that invite right now."
        }
    }

    /** "1 week of Pro banked" / "3 weeks of Pro banked" / "10 days of Pro banked" / null. */
    fun bankedText(days: Int?): String? {
        val d = (days ?: 0).coerceAtLeast(0)
        if (d == 0) return null
        if (d % 7 == 0) { val w = d / 7; return "$w ${if (w == 1) "week" else "weeks"} of Pro banked" }
        return "$d ${if (d == 1) "day" else "days"} of Pro banked"
    }

    /** The code in an invite link's path (…/r/<CODE>), or null. */
    fun codeFromPath(segments: List<String>): String? {
        val at = segments.indexOf("r")
        if (at < 0) return null
        return normalizeCode(segments.getOrNull(at + 1))
    }
}
