package com.sohum.bandlog.util

import kotlin.math.roundToInt

/**
 * v2.13 protein nudge (spec §3): at `protein_nudge_time` (default 16:00) when `protein_nudge` is on,
 * if today's protein is under 70 % of target and something was logged today, post
 * "You're {short} g short on protein" / "Try {3 picks}". Never for under-18s whose goal is loss;
 * at most one a day. Mirrors the web cron's lib/proteinNudge.ts.
 */
object ProteinNudge {
    const val DEFAULT_TIME = "16:00"
    const val THRESHOLD = 0.70

    /** True when the nudge should fire. [loggedToday] = at least one meal logged today. */
    fun shouldNudge(
        enabled: Boolean,
        proteinToday: Double,
        target: Int,
        loggedToday: Boolean,
        age: Int?,
        goalType: String,
        alreadySentToday: Boolean,
    ): Boolean {
        if (!enabled || alreadySentToday || !loggedToday || target <= 0) return false
        if (Goals.isTeen(age) && goalType == "lose") return false
        return proteinToday < target * THRESHOLD
    }

    /** Grams short of the full target, rounded (the title's number). */
    fun shortBy(proteinToday: Double, target: Int): Int = (target - proteinToday).roundToInt().coerceAtLeast(0)

    fun title(short: Int): String = "You're $short g short on protein"

    fun body(picks: List<String>): String = when (picks.size) {
        0 -> "A protein-rich snack now keeps you on track."
        1 -> "Try ${picks[0]}"
        2 -> "Try ${picks[0]} or ${picks[1]}"
        else -> "Try ${picks[0]}, ${picks[1]} or ${picks[2]}"
    }

    /** "16:00" / "16:00:00" → (16, 0); anything unreadable is the default. */
    fun parseTime(s: String?): Pair<Int, Int> {
        val m = Regex("^(\\d{1,2}):(\\d{2})").find(s?.trim().orEmpty()) ?: return 16 to 0
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h to mi else 16 to 0
    }

    fun formatTime(h: Int, m: Int): String = String.format(java.util.Locale.US, "%02d:%02d", h, m)

    // ---- picks ----

    /** What a pick contains, for the diet-mode filters (spec §4). */
    enum class Tag { MEAT, FISH, EGG, DAIRY, ROOT, HONEY }

    data class Pick(val name: String, val proteinG: Double, val kcal: Double, val tags: Set<Tag> = emptySet())

    /** Common Indian protein picks (portion in the name). Used when what-to-eat (§6) isn't wired in. */
    val FALLBACK: List<Pick> = listOf(
        Pick("paneer bhurji (100 g)", 18.0, 260.0, setOf(Tag.DAIRY)),
        Pick("2 boiled eggs", 12.6, 155.0, setOf(Tag.EGG)),
        Pick("chicken tikka (150 g)", 38.0, 240.0, setOf(Tag.MEAT)),
        Pick("a bowl of dal (200 g)", 12.0, 230.0),
        Pick("Greek-style curd (200 g)", 18.0, 150.0, setOf(Tag.DAIRY)),
        Pick("roasted chana (50 g)", 10.0, 180.0),
        Pick("soya chunks curry (50 g dry)", 26.0, 170.0),
        Pick("moong sprouts chaat (150 g)", 11.0, 150.0),
        Pick("grilled fish (150 g)", 33.0, 200.0, setOf(Tag.FISH)),
        Pick("tofu stir-fry (150 g)", 18.0, 180.0),
        Pick("a glass of milk (250 ml)", 8.0, 150.0, setOf(Tag.DAIRY)),
        Pick("peanut chikki (40 g)", 7.0, 200.0),
    )

    /** Tags a diet mode leaves out (spec §4 food filters; they never block logging). */
    fun excluded(dietMode: String?): Set<Tag> = when (dietMode) {
        "vegetarian" -> setOf(Tag.MEAT, Tag.FISH, Tag.EGG)
        "eggetarian" -> setOf(Tag.MEAT, Tag.FISH)
        "vegan" -> setOf(Tag.MEAT, Tag.FISH, Tag.EGG, Tag.DAIRY, Tag.HONEY)
        "jain" -> setOf(Tag.MEAT, Tag.FISH, Tag.EGG, Tag.ROOT, Tag.HONEY)
        else -> emptySet()
    }

    /**
     * Up to 3 picks for [shortG], limited to [dietMode]: highest protein per kcal first, skipping
     * anything that alone would blow well past what's missing. [candidates] can be what-to-eat's list.
     */
    fun picks(shortG: Int, dietMode: String?, candidates: List<Pick> = FALLBACK): List<String> {
        val out = excluded(dietMode)
        return candidates.filter { c -> c.tags.none { it in out } && c.kcal > 0 }
            .sortedByDescending { it.proteinG / it.kcal }
            .filter { shortG <= 0 || it.proteinG <= shortG + 25 }
            .take(3).map { it.name }
    }

    /** Set by the what-to-eat module (§6, nutrition half) at merge to feed real ranked picks; null = [FALLBACK]. */
    @Volatile var picksProvider: ((shortG: Int, dietMode: String?) -> List<String>)? = null
}
