package com.sohum.bandlog.util

/**
 * v2.9 squad sharing (web supabase/schema_v31.sql): which logs auto-post to squads, per-squad
 * mutes, who may delete a post, the one-time "Posted to your squads · Change" hint and what an
 * edit re-posts. Pure logic; mirrors the web's src/lib/squadSharing.ts (checked by
 * scripts/check-sharing.ts there).
 *
 * Readers must cope with schema_v31 not being applied yet: a missing `auto_share` is the default
 * (all three kinds) and a missing `auto_post` is on, which is the v2.8 behaviour.
 */
object SquadSharing {
    val KINDS = listOf("meal", "workout", "pr")
    val LABELS = mapOf("meal" to "Auto-post my meals", "workout" to "Auto-post my workouts", "pr" to "Auto-post my PRs")

    const val HINT_PREFS = "squad_sharing"
    const val HINT_SEEN = "post_hint_seen"
    const val HINT_TEXT = "Posted to your squads"

    /** profiles.auto_share → the kinds that auto-post; null (column missing) is the default. */
    fun parseAutoShare(v: List<String>?): List<String> = if (v == null) KINDS else KINDS.filter { it in v }

    fun parseAutoShare(arr: org.json.JSONArray?): List<String>? =
        arr?.let { a -> parseAutoShare((0 until a.length()).map { a.optString(it) }) }

    /** Turn one kind on or off, canonical order, no duplicates. */
    fun withKind(list: List<String>, kind: String, on: Boolean): List<String> = KINDS.filter { if (it == kind) on else it in list }

    /** share_stats off stops everything; meal / workout / pr need their switch; other kinds (photo) aren't gated. */
    fun autoPostAllowed(shareStats: Boolean, autoShare: List<String>?, kind: String): Boolean {
        if (!shareStats) return false
        if (kind !in KINDS) return true
        return kind in parseAutoShare(autoShare)
    }

    /** Your own posts, or any post in a squad you own. */
    fun canDelete(postUserId: String, me: String?, isOwner: Boolean): Boolean = me != null && (postUserId == me || isOwner)

    fun shouldShowHint(postedRows: Int, seen: Boolean): Boolean = !seen && postedRows > 0

    /** PostgREST / Postgres "no such column" for [column] (schema_v31 not applied yet). */
    fun missingColumn(message: String?, column: String): Boolean {
        val m = message ?: return false
        return m.contains(column, ignoreCase = true) && (m.contains("42703") || m.contains("PGRST204") || m.contains("column", ignoreCase = true) || m.contains("schema cache", ignoreCase = true))
    }

    data class Plan(val repost: List<Pair<String, String>>, val remove: List<String>)

    /**
     * What an edit re-posts: only kinds already posted for this log ([existing]) are touched, so an
     * edit never creates a new post; a kind whose new body is null (a PR that isn't one any more) is removed.
     */
    fun editRepostPlan(existing: Collection<String>, next: List<Pair<String, String?>>): Plan {
        val repost = mutableListOf<Pair<String, String>>()
        val remove = mutableListOf<String>()
        next.forEach { (kind, body) ->
            if (kind !in existing) return@forEach
            if (!body.isNullOrBlank()) repost += kind to body else remove += kind
        }
        return Plan(repost, remove)
    }
}
