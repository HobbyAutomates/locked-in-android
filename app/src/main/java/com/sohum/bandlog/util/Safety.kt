package com.sohum.bandlog.util

/**
 * v2.18 E5 launch must-haves: report + block in squads (web docs/schema_v45.sql user_blocks,
 * content_reports), account deletion and password reset. Pure. Port of the web's
 * src/lib/social/safety.ts.
 */
object Safety {
    data class Reason(val key: String, val label: String)

    val REPORT_REASONS = listOf(
        Reason("spam", "Spam or ads"),
        Reason("abuse", "Harassment or hate"),
        Reason("nudity", "Nudity or sexual content"),
        Reason("self_harm", "Self-harm or an eating disorder worry"),
        Reason("other", "Something else"),
    )

    fun parseReason(v: Any?): String? = REPORT_REASONS.firstOrNull { it.key == v }?.key

    /** Device fallback for the block list (used until schema_v45 exists, and as a fast cache). */
    const val BLOCKS_KEY = "li-blocked"

    /** Drops rows authored by anyone I blocked. */
    fun <T> withoutBlocked(rows: List<T>, blocked: Collection<String>, userId: (T) -> String): List<T> {
        if (blocked.isEmpty()) return rows
        val set = blocked.toSet()
        return rows.filter { userId(it) !in set }
    }

    /** What the report stores about the post (kept short; the post itself may get deleted). */
    fun reportSnapshot(kind: String?, body: String?, authorName: String?, createdAt: String?): String =
        listOfNotNull(authorName?.ifBlank { null }?.let { "by $it" }, kind?.ifBlank { null }?.let { "[$it]" }, createdAt?.ifBlank { null }?.take(16), (body ?: "").take(800).ifBlank { null })
            .joinToString(" ").take(1000)

    const val DELETE_WORD = "DELETE"

    /** The delete button unlocks only when the box says DELETE (any case, trimmed). */
    fun deleteConfirmed(typed: String): Boolean = typed.trim().uppercase() == DELETE_WORD

    val USER_BUCKETS = listOf("avatars", "progress-photos", "meal-photos", "scan-photos", "group-photos")

    const val MIN_PASSWORD = 6

    fun passwordProblem(pw: String, again: String): String? = when {
        pw.length < MIN_PASSWORD -> "At least $MIN_PASSWORD characters"
        pw != again -> "The two passwords don't match"
        else -> null
    }

    /**
     * Google sign-in: only when an OAuth client is configured (none is today).
     * TODO(v2.18 E5): add Credential Manager + a Google OAuth client id, then Supabase
     * /auth/v1/token?grant_type=id_token. Not implemented on purpose: no OAuth client exists.
     */
    const val GOOGLE_SIGN_IN_ENABLED = false
}
