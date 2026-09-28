package com.sohum.bandlog.util

/**
 * v2.18 D5 streak freeze tokens (web docs/schema_v44.sql streak_freezes / freeze_events, RPCs
 * freeze_sync and freeze_gift). Pure: the server is the source of truth, these mirror its rules so
 * the app can show the state, explain it and test it. Port of the web's src/lib/social/freezes.ts
 * (same names, same results; FreezesTest pins the numbers of scripts/check-v218-social.ts).
 *
 *   - Earn 1 per perfect Mon–Sun (IST) week, last 2 completed weeks per sync, max 3.
 *   - Auto-use: the run of empty days ending yesterday, only when the tokens cover all of it and
 *     the day before it was active or frozen. Today never counts as missed.
 *   - A frozen day counts as active for the day streak.
 *   - Gift one to a squadmate with room (< 3), once per person per 7 days.
 */
object Freezes {
    const val MAX_FREEZES = 3
    const val EARN_LOOKBACK_WEEKS = 2
    const val GIFT_COOLDOWN_DAYS = 7
    /** Local key: the last day this device ran freeze_sync (once a day is plenty). */
    const val FREEZE_SYNC_KEY = "li-freeze-sync"

    fun clampTokens(n: Any?): Int {
        val v = when (n) {
            is Number -> n.toDouble()
            is String -> n.toDoubleOrNull()
            else -> null
        } ?: return 0
        if (v.isNaN() || v.isInfinite()) return 0
        return kotlin.math.floor(v).toInt().coerceIn(0, MAX_FREEZES)
    }

    /** Week starts (Mondays) of the last [lookback] COMPLETED weeks where all 7 days were active. Newest first. */
    fun perfectWeeks(active: Collection<String>, today: String, lookback: Int = EARN_LOOKBACK_WEEKS): List<String> {
        val set = active.toSet()
        val thisMonday = Dates.weekStart(today)
        return (1..lookback).mapNotNull { k ->
            val ws = Dates.addDays(thisMonday, -7L * k)
            if ((0 until 7).all { set.contains(Dates.addDays(ws, it.toLong())) }) ws else null
        }
    }

    /**
     * The days the next sync would freeze: the empty run ending yesterday, when [tokens] cover it
     * and the day before it is active or frozen. [firstDay] = the first day anything was logged.
     * Newest first.
     */
    fun planFreezeUse(active: Collection<String>, frozen: Collection<String>, tokens: Int, today: String, firstDay: String?): List<String> {
        if (firstDay == null) return emptyList()
        val a = active.toSet()
        val f = frozen.toSet()
        val gap = mutableListOf<String>()
        var d = Dates.addDays(today, -1)
        while (gap.size < MAX_FREEZES + 1 && d > firstDay && d !in a && d !in f) {
            gap.add(d)
            d = Dates.addDays(d, -1)
        }
        if (gap.isEmpty() || gap.size > clampTokens(tokens)) return emptyList()
        if (d !in a && d !in f) return emptyList()
        return gap
    }

    data class SyncInput(val tokens: Int, val earnedWeeks: List<String>, val usedDays: List<String>, val active: Collection<String>, val firstDay: String?)
    data class SyncResult(val tokens: Int, val earnedNow: List<String>, val usedNow: List<String>, val earnedWeeks: List<String>, val usedDays: List<String>)

    /** What one freeze_sync does, given what the server has already recorded. */
    fun simulateSync(input: SyncInput, today: String): SyncResult {
        val active = input.active.toSet()
        var tokens = clampTokens(input.tokens)
        val recorded = input.earnedWeeks.toSet()
        val earnedNow = mutableListOf<String>()
        val earnedWeeks = input.earnedWeeks.toMutableList()
        for (ws in perfectWeeks(active, today)) {
            if (ws in recorded) continue
            earnedWeeks.add(ws)
            if (tokens < MAX_FREEZES) { tokens++; earnedNow.add(ws) }
        }
        val usedNow = planFreezeUse(active, input.usedDays, tokens, today, input.firstDay)
        tokens -= usedNow.size
        return SyncResult(tokens, earnedNow, usedNow, earnedWeeks, usedNow + input.usedDays)
    }

    /** Streak input: active days plus frozen days. */
    fun withFrozen(activeLists: List<List<String>>, frozen: List<String>): List<List<String>> =
        if (frozen.isNotEmpty()) activeLists + listOf(frozen) else activeLists

    enum class GiftBlock(val text: String) {
        NONE("You have no freezes to gift"),
        FULL("They already have 3 freezes"),
        COOLDOWN("You gifted them one this week"),
        SELF("Pick a squadmate"),
    }

    /** null = OK to gift. */
    fun canGift(my: Int, theirs: Int?, lastGiftAt: String?, nowMs: Long = System.currentTimeMillis(), self: Boolean = false): GiftBlock? {
        if (self) return GiftBlock.SELF
        if (clampTokens(my) < 1) return GiftBlock.NONE
        if (theirs != null && clampTokens(theirs) >= MAX_FREEZES) return GiftBlock.FULL
        if (lastGiftAt != null) {
            val t = runCatching { java.time.OffsetDateTime.parse(lastGiftAt).toInstant().toEpochMilli() }
                .recoverCatching { java.time.Instant.parse(lastGiftAt).toEpochMilli() }.getOrNull()
            if (t != null && nowMs - t < GIFT_COOLDOWN_DAYS * 86_400_000L) return GiftBlock.COOLDOWN
        }
        return null
    }

    /** "2 freezes" / "1 freeze" / "No freezes". */
    fun freezeCountText(n: Int): String {
        val c = clampTokens(n)
        return if (c == 0) "No freezes" else "$c ${if (c == 1) "freeze" else "freezes"}"
    }

    /** One line under the tokens: how to earn the next one, from which days of THIS week are active. */
    fun earnHint(tokens: Int, active: Collection<String>, today: String): String {
        if (clampTokens(tokens) >= MAX_FREEZES) return "You're at the max of 3. Use one, then earn it back."
        val set = active.toSet()
        val ws = Dates.weekStart(today)
        val dayIdx = Dates.daysBetween(ws, today).toInt()
        val missed = (0 until dayIdx).any { !set.contains(Dates.addDays(ws, it.toLong())) }
        if (missed) return "Log all 7 days of a week (Mon to Sun) to earn one."
        val left = 7 - dayIdx - (if (set.contains(today)) 1 else 0)
        if (left == 0) return "Perfect week in the bag. Your freeze lands on Monday."
        return "Log every day to Sunday ($left to go) to earn one."
    }

    data class Event(val kind: String?, val ref: String?, val otherUser: String?, val createdAt: String?)
    data class Parsed(val usedDays: List<String>, val earnedWeeks: List<String>, val lastGiftTo: Map<String, String>)

    private val DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Rows of freeze_events → the used days, earned weeks and when I last gifted each person. */
    fun parseFreezeEvents(rows: List<Event>): Parsed {
        val used = mutableListOf<String>()
        val earned = mutableListOf<String>()
        val last = linkedMapOf<String, String>()
        for (r in rows) {
            when {
                r.kind == "use" && r.ref != null && DAY.matches(r.ref) -> used.add(r.ref)
                r.kind == "earn" && r.ref != null && DAY.matches(r.ref) -> earned.add(r.ref)
                r.kind == "gift_out" && r.otherUser != null && r.createdAt != null -> {
                    val prev = last[r.otherUser]
                    if (prev == null || prev < r.createdAt) last[r.otherUser] = r.createdAt
                }
            }
        }
        return Parsed(used.sortedDescending(), earned.sortedDescending(), last)
    }
}
