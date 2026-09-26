package com.sohum.bandlog.util

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * v2.13 §14 edit-meal item cards: the richer ⓘ facts about one logged row — where the numbers came
 * from (AI estimate vs a database match), the confidence with a one-line why, a gram range and the
 * row's own macros. Pure (twin of the web's src/lib/itemInfo.ts).
 */
object ItemInfo {
    data class Facts(
        /** "AI estimate" | "Database match" | "Nutrition label" | "Your recipe". */
        val kind: String,
        /** "high" | "medium" | "low". */
        val level: String,
        val why: String,
        val gramsLow: Int,
        val gramsHigh: Int,
    )

    /** How far the true amount could plausibly be from the logged grams, by confidence. */
    fun spread(level: String): Double = when (level) { "high" -> 0.10; "medium" -> 0.20; else -> 0.35 }

    private fun round5(v: Double): Int = max(0, (v / 5).roundToInt() * 5)

    fun level(confidence: Double?, source: String?): String = when (Sources.confidenceLabel(confidence, source)) { "High" -> "high"; "Medium" -> "medium"; else -> "low" }

    /**
     * [sourceKind] is the SourceInfo kind when known (ifct, usda, dish, custom, off, label, estimate, ai, recipe).
     * An explicit [gramsLow]/[gramsHigh] (plate scans) wins over the confidence-based spread.
     */
    fun facts(grams: Double, confidence: Double?, source: String?, foodId: String?, sourceKind: String?, gramsLow: Double? = null, gramsHigh: Double? = null): Facts {
        val lvl = level(confidence, source)
        val kind = when {
            sourceKind == "recipe" -> "Your recipe"
            source == "scan" || sourceKind == "label" || sourceKind == "off" -> "Nutrition label"
            source == "estimated" || foodId == null || sourceKind == "estimate" || sourceKind == "ai" -> "AI estimate"
            else -> "Database match"
        }
        val why = when (kind) {
            "Your recipe" -> "Worked out from your own ingredients and servings."
            "Nutrition label" -> "Read off the pack's nutrition table, so the numbers are the brand's own."
            "AI estimate" -> when (lvl) {
                "high" -> "The AI was sure what this is; the amount is the main guess."
                "medium" -> "The AI matched a typical recipe; oil and portion size can vary."
                else -> "The AI wasn't sure what this is or how much. Worth a quick check."
            }
            else -> when (lvl) {
                "high" -> "Matched a food-table row by name; only the amount is estimated."
                "medium" -> "Matched a close food-table row; the exact recipe may differ."
                else -> "A loose match in the food table. Pick the right one if it's off."
            }
        }
        val s = spread(lvl)
        val lo = gramsLow?.takeIf { gramsHigh != null && gramsHigh > it } ?: grams * (1 - s)
        val hi = gramsHigh?.takeIf { gramsLow != null && it > gramsLow } ?: grams * (1 + s)
        return Facts(kind, lvl, why, round5(lo), max(round5(hi), round5(lo)))
    }

    /** "120–160 g". */
    fun rangeLabel(f: Facts): String = if (f.gramsHigh <= f.gramsLow) "${f.gramsLow} g" else "${f.gramsLow}–${f.gramsHigh} g"
}
