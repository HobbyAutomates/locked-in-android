package com.sohum.bandlog.util

/**
 * v2.18 D7 live squad sessions (web docs/schema_v44.sql live_sessions / live_cheers,
 * profiles.live_share, RPCs squad_live and live_cheer). Opt-in, OFF by default. Pure. Port of the
 * web's src/lib/social/live.ts.
 */
object LiveSquad {
    const val LIVE_HEARTBEAT_MS = 4 * 60_000L
    const val LIVE_FRESH_MIN = 20
    const val LIVE_MAX_HOURS = 4
    const val CHEER_COOLDOWN_MIN = 10
    /** Device copy of the opt-in (so the live workout doesn't wait on a network read). */
    const val LIVE_PREF_KEY = "li-live-share"

    data class Row(val userId: String, val name: String, val avatarPath: String?, val label: String, val startedAt: String, val cheers: Int)

    fun millis(iso: String?): Long? = iso?.let { s ->
        runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
    }

    fun isLiveFresh(seenAt: String, startedAt: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = millis(seenAt) ?: return false
        val st = millis(startedAt) ?: return false
        return nowMs - s <= LIVE_FRESH_MIN * 60_000L && nowMs - st <= LIVE_MAX_HOURS * 3_600_000L
    }

    /** "Ayan is training now 🔥" (first name only); "Ayan is on leg day now 🔥" for a named session. */
    fun liveLine(name: String?, label: String? = null): String {
        val first = (name ?: "").trim().split(Regex("\\s+")).firstOrNull()?.ifBlank { null } ?: "Someone"
        val what = (label ?: "").trim().lowercase()
        val verb = if (what.isEmpty() || what == "training") "training" else "on $what"
        return "$first is $verb now 🔥"
    }

    /** "12 min in" / "1 h 05 min in". */
    fun liveElapsed(startedAt: String, nowMs: Long = System.currentTimeMillis()): String {
        val t = millis(startedAt) ?: return ""
        val m = ((nowMs - t) / 60_000L).coerceAtLeast(0)
        if (m < 60) return "$m min in"
        return "${m / 60} h ${(m % 60).toString().padStart(2, '0')} min in"
    }

    fun parseLiveRows(rows: List<Map<String, Any?>>, me: String?): List<Row> = rows
        .filter { (it["user_id"] as? String) != null && it["user_id"] != me }
        .map { r ->
            Row(
                userId = r["user_id"] as String,
                name = (r["name"] as? String)?.ifBlank { null } ?: "Squadmate",
                avatarPath = r["avatar_path"] as? String,
                label = (r["label"] as? String)?.ifBlank { null } ?: "Training",
                startedAt = (r["started_at"] as? String)?.ifBlank { null } ?: "1970-01-01T00:00:00.000Z",
                cheers = when (val c = r["cheers"]) { is Number -> c.toInt(); is String -> c.toDoubleOrNull()?.toInt() ?: 0; else -> 0 }.coerceAtLeast(0),
            )
        }
}
