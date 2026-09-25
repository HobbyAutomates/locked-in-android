package com.sohum.bandlog.util

/**
 * v2.11 squad reactions + chat read receipts (web supabase/schema_v35.sql). Pure logic; mirrors
 * the web's src/lib/reactions.ts (checked by scripts/check-reactions.ts there).
 *
 * Readers must cope with schema_v35 not being applied yet: group_feed then has no `reactions` /
 * `my_reaction` (no reactions), and the read-status RPC is missing (no ticks, no unread badges).
 */
object Reactions {
    /** The six, in bar order. ❤️ is U+2764 U+FE0F, exactly as the database check stores it. */
    val ALL = listOf("❤️", "🔥", "👍", "😂", "😮", "💪")
    const val HEART = "❤️"

    /** Messages from one person less than this apart form one run (one name, one avatar). */
    const val RUN_GAP_MS = 5 * 60_000L

    data class State(val counts: Map<String, Int>, val mine: String?)
    data class Chip(val emoji: String, val count: Int, val mine: Boolean)
    /** [save] null = delete my row. */
    data class Change(val state: State, val save: String?)

    /** "❤" (no variation selector) maps onto "❤️"; anything outside the six is null. */
    fun normalize(e: String?): String? {
        val t = e?.trim() ?: return null
        if (t in ALL) return t
        val bare = t.replace("️", "")
        return ALL.firstOrNull { it.replace("️", "") == bare }
    }

    /** group_feed's `reactions` jsonb → clean counts (known emojis, positive). Null (pre-v35) = none. */
    fun parseCounts(o: org.json.JSONObject?): Map<String, Int> {
        if (o == null) return emptyMap()
        val out = linkedMapOf<String, Int>()
        for (k in o.keys()) {
            val e = normalize(k) ?: continue
            val c = o.optDouble(k, 0.0).toInt()
            if (c > 0) out[e] = (out[e] ?: 0) + c
        }
        return out
    }

    /** Same emoji again removes mine; another one replaces it (its count moves); none yet adds it. */
    fun toggle(state: State, emoji: String): Change {
        val e = normalize(emoji) ?: return Change(state, normalize(state.mine))
        val counts = state.counts.toMutableMap()
        fun bump(k: String, d: Int) { val n = (counts[k] ?: 0) + d; if (n > 0) counts[k] = n else counts.remove(k) }
        val mine = normalize(state.mine)
        if (mine != null) bump(mine, -1)
        if (mine == e) return Change(State(counts, null), null)
        bump(e, 1)
        return Change(State(counts, e), e)
    }

    /** Double-tap on a Feed post: sets ❤️ (replacing another emoji), never removes it; null = nothing to do. */
    fun quickHeart(state: State): Change? = if (normalize(state.mine) == HEART) null else toggle(state, HEART)

    /** Chips under a post: most reactions first, ties in bar order, mine flagged. */
    fun chips(state: State): List<Chip> {
        val mine = normalize(state.mine)
        return ALL.withIndex().map { (i, e) -> Triple(i, e, state.counts[e] ?: 0) }
            .filter { it.third > 0 }
            .sortedWith(compareByDescending<Triple<Int, String, Int>> { it.third }.thenBy { it.first })
            .map { Chip(it.second, it.third, it.second == mine) }
    }

    /** For Chat in oldest → newest order: is each message the first (name) / last (avatar, time) of its run? */
    fun runs(users: List<String>, times: List<Long>): List<Pair<Boolean, Boolean>> {
        fun same(a: Int, b: Int) = a >= 0 && b < users.size && users[a] == users[b] && kotlin.math.abs(times[b] - times[a]) < RUN_GAP_MS
        return users.indices.map { i -> !same(i - 1, i) to !same(i, i + 1) }
    }

    /** One row of `group_read_status(g)`. */
    data class ReadRow(val userId: String, val name: String, val username: String?, val avatarPath: String?, val lastReadAt: String?)

    enum class Seen { SENT, SOME, ALL }
    data class SeenBy(val state: Seen, val seen: List<ReadRow>, val unseen: List<ReadRow>, val label: String)

    fun millis(iso: String?): Long? = iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    /**
     * "Seen by" for one of my messages: other members whose last_read_at is at or after its
     * created_at. SENT (grey ✓) when nobody has (or I'm alone), SOME ("Seen by N"), ALL ("Seen by
     * everyone"). Seen people newest read first.
     */
    fun seenBy(rows: List<ReadRow>, me: String?, createdAt: String): SeenBy {
        val at = millis(createdAt) ?: 0L
        val others = rows.filter { it.userId != me }
        val (seen, unseen) = others.partition { r -> millis(r.lastReadAt)?.let { it >= at } == true }
        val sorted = seen.sortedByDescending { millis(it.lastReadAt) ?: 0L }
        val state = when { sorted.isEmpty() -> Seen.SENT; unseen.isNotEmpty() -> Seen.SOME; else -> Seen.ALL }
        val label = when (state) { Seen.SENT -> "Sent"; Seen.ALL -> "Seen by everyone"; Seen.SOME -> "Seen by ${sorted.size}" }
        return SeenBy(state, sorted, unseen, label)
    }

    /** Badge text: null for 0, "99+" past 99. */
    fun unreadLabel(n: Int?): String? = when { n == null || n <= 0 -> null; n > 99 -> "99+"; else -> n.toString() }

    /** The server saying a v35 table / function isn't there yet (404 / PGRST202 / 42P01). */
    fun missingV35(msg: String?): Boolean {
        val m = msg ?: return false
        return m.contains("(404)") || m.contains("PGRST202") || m.contains("PGRST205") || m.contains("42P01") ||
            Regex("(post_reactions|group_reads|mark_read|group_read_status|post_reactors|my_unread_counts).*(does not exist|could not find)", RegexOption.IGNORE_CASE).containsMatchIn(m) ||
            m.contains("Could not find the", ignoreCase = true)
    }
}
