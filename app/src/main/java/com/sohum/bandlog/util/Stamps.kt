package com.sohum.bandlog.util

/**
 * v2.18 D6 clean / cheat stamps on squad meal photos (web docs/schema_v44.sql post_stamps + RPC
 * post_stamp_counts). One stamp per person per post; tapping yours again removes it, the other one
 * switches. Pure. Port of the web's src/lib/social/stamps.ts. The 7 new reactions live in Reactions.
 */
object Stamps {
    const val CLEAN = "clean"
    const val CHEAT = "cheat"
    val ALL = listOf(CLEAN, CHEAT)
    val LABEL = mapOf(CLEAN to "CLEAN", CHEAT to "CHEAT")

    data class State(val clean: Int = 0, val cheat: Int = 0, val mine: String? = null) {
        fun count(s: String) = if (s == CLEAN) clean else cheat
        fun with(s: String, n: Int) = if (s == CLEAN) copy(clean = n) else copy(cheat = n)
    }

    val EMPTY = State()

    data class Change(val state: State, val save: String?)

    fun parseStamp(v: Any?): String? = if (v == CLEAN || v == CHEAT) v as String else null

    private fun count(v: Any?): Int {
        val d = when (v) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null } ?: return 0
        val n = kotlin.math.floor(d)
        return if (n.isFinite() && n > 0) n.toInt() else 0
    }

    /** post_stamp_counts rows → postId → state. */
    fun parseStampRows(rows: List<Map<String, Any?>>): Map<String, State> {
        val out = linkedMapOf<String, State>()
        for (r in rows) {
            val id = r["post_id"] as? String ?: continue
            out[id] = State(count(r["clean"]), count(r["cheat"]), parseStamp(r["mine"]))
        }
        return out
    }

    /** Tap [s]: the new state and what to save (null = delete my stamp). */
    fun toggleStamp(state: State, s: String): Change {
        var next = state
        state.mine?.let { m -> next = next.with(m, (next.count(m) - 1).coerceAtLeast(0)) }
        if (state.mine == s) return Change(next.copy(mine = null), null)
        next = next.with(s, next.count(s) + 1)
        return Change(next.copy(mine = s), s)
    }

    /** The big stamp on the photo: the majority; a tie (or nothing) shows none. */
    fun stampVerdict(s: State): String? {
        if (s.clean == s.cheat) return null
        return if (s.clean > s.cheat) CLEAN else CHEAT
    }

    /** Only meal posts and photo posts with a photo get stamps. */
    fun stampable(kind: String, photoPath: String?): Boolean = (kind == "meal" || kind == "photo") && !photoPath.isNullOrBlank()
}
