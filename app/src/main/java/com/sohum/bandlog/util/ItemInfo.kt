package com.sohum.bandlog.util

import kotlin.math.floor
import kotlin.math.max

/**
 * v2.13 §14 edit-meal item cards — a port of the web's src/lib/itemInfo.ts: what the richer ⓘ
 * sheet and the item card say about one row — AI estimate vs database match, the confidence with a
 * one-line why, and a gram range.
 */
object ItemInfo {
    data class Row(
        val grams: Double,
        val foodId: String?,
        val source: String,
        val confidence: Double?,
        val unit: String? = null,
        val cookedIn: String? = null,
        val gramsLow: Double? = null,
        val gramsHigh: Double? = null,
    )

    /** database | ai | scan | recipe */
    fun origin(r: Row): String = when {
        Recipes.isRecipeItem(r.unit) -> "recipe"
        r.source == "scan" -> "scan"
        r.source == "estimated" || r.foodId == null -> "ai"
        else -> "database"
    }

    val ORIGIN_LABEL = mapOf("database" to "Database match", "ai" to "AI estimate", "scan" to "From the label", "recipe" to "Your recipe")

    /** "High" | "Medium" | "Low". */
    fun level(r: Row): String = if (origin(r) == "recipe") "High" else Sources.confidenceLabel(r.confidence, r.source)

    /** One line on why the confidence is what it is. */
    fun why(r: Row): String {
        val o = origin(r)
        val lvl = level(r)
        return when (o) {
            "recipe" -> "Worked out from your own recipe's ingredients."
            "scan" -> "Read off the pack's nutrition table, so only the amount can differ."
            "database" -> when {
                r.cookedIn == "restaurant" -> "A food-table match with extra oil for a restaurant portion; the oil is the big unknown."
                lvl == "High" -> "Matched to a row in the food table; only the amount is an estimate."
                else -> "Matched to a similar food in the table; the exact dish may differ."
            }
            else -> when (lvl) {
                "High" -> "A common dish the AI recognised clearly; home recipes still vary a little."
                "Medium" -> "A typical recipe: oil, ghee and portion size can move it either way."
                else -> "Hard to tell from the words or the photo, so worth a quick check."
            }
        }
    }

    private fun round5(v: Double): Int = max(0, (floor(v / 5 + 0.5) * 5).toInt())

    /** How far the amount could be off, as a share of the grams, by confidence (no model range). */
    val RANGE_SHARE = mapOf("High" to 0.1, "Medium" to 0.25, "Low" to 0.4)

    data class Range(val low: Int, val high: Int, val fromModel: Boolean)

    /** A likely gram range: the model's own when the row came with one (plate photos), else ± a share of the grams. */
    fun gramRange(r: Row): Range {
        val lo = r.gramsLow
        val hi = r.gramsHigh
        if (lo != null && hi != null && hi >= lo && hi > 0) return Range(round5(lo), round5(hi), true)
        val share = if (origin(r) == "recipe") 0.1 else RANGE_SHARE.getValue(level(r))
        return Range(round5(r.grams * (1 - share)), round5(r.grams * (1 + share)), false)
    }

    fun gramRangeText(r: Row): String { val g = gramRange(r); return if (g.low == g.high) "${g.low} g" else "${g.low}–${g.high} g" }
}
