package com.sohum.bandlog.util

import com.sohum.bandlog.data.PlateItem
import kotlin.math.roundToInt

/**
 * v2.8: client-side follow-up effects and gram-range math for the plate result (mirrors the web's
 * src/lib/scanFollowUp.ts).
 *
 * When a plate scan's `follow_up` is answered, the client applies one of a small, fixed set of
 * deterministic multipliers — never a second model call. Unknown effect strings are ignored: the
 * items come back unchanged.
 */

private fun round1(d: Double) = (d * 10).roundToInt() / 10.0

/** Curries, gravies and fried items — the ones a restaurant serves oilier than a home kitchen. */
private val CURRY_OR_FRIED = Regex(
    "\\b(curry|kadai|kadhai|masala|makhani|korma|gravy|fry|fried|pakora|bhaji|bajji|cutlet|tikka|65|manchurian|biryani|dal|daal|dhal|sabzi|sabji)\\b",
    RegexOption.IGNORE_CASE,
)

/** The fixed effect vocabulary — see V28_SPEC.md. Anything else is a no-op. */
val FOLLOW_UP_EFFECTS = listOf("restaurant", "homemade", "add_ghee", "no_oil", "smaller", "bigger")

private fun scaleWhole(it: PlateItem, k: Double): PlateItem {
    val micros = it.micros.mapValues { (_, v) -> round1(v * k) }
    return it.copy(
        grams = maxOf(1.0, Math.round(it.grams * k).toDouble()),
        gramsLow = it.gramsLow?.let { g -> maxOf(1.0, Math.round(g * k).toDouble()) },
        gramsHigh = it.gramsHigh?.let { g -> maxOf(1.0, Math.round(g * k).toDouble()) },
        calories = Math.round(it.calories * k).toDouble(),
        proteinG = round1(it.proteinG * k),
        carbsG = round1(it.carbsG * k),
        fatG = round1(it.fatG * k),
        micros = micros,
    )
}

/** The single item a follow-up option that "names" an item (e.g. "Add ghee to the dal?") refers to:
 *  whichever item's name appears in the question text, else the biggest item on the plate. */
private fun targetIndex(items: List<PlateItem>, question: String?): Int {
    val q = (question ?: "").lowercase()
    val named = items.indexOfFirst { it.name.isNotBlank() && q.contains(it.name.lowercase()) }
    if (named >= 0) return named
    var best = 0
    for (i in 1 until items.size) if (items[i].calories > items[best].calories) best = i
    return best
}

/**
 * Apply one follow-up effect deterministically. [question] is only used by `add_ghee` to find
 * "the item it names" (falls back to the largest item when the question doesn't name one).
 */
fun applyFollowUpEffect(items: List<PlateItem>, effect: String, question: String? = null): List<PlateItem> {
    if (items.isEmpty()) return items
    return when (effect) {
        "restaurant" -> items.map { if (CURRY_OR_FRIED.containsMatchIn(it.name)) it.copy(fatG = round1(it.fatG * 1.25), calories = Math.round(it.calories * 1.25).toDouble()) else it }
        "homemade" -> items
        "add_ghee" -> {
            val idx = targetIndex(items, question)
            items.mapIndexed { i, it -> if (i == idx) it.copy(calories = it.calories + 45, fatG = round1(it.fatG + 5)) else it }
        }
        "no_oil" -> items.map {
            val newFat = round1(it.fatG * 0.65)
            val removedKcal = Math.round((it.fatG - newFat) * 9).toDouble()
            it.copy(fatG = newFat, calories = maxOf(0.0, it.calories - removedKcal))
        }
        "smaller" -> items.map { scaleWhole(it, 0.8) }
        "bigger" -> items.map { scaleWhole(it, 1.25) }
        else -> items // Unknown effects are ignored.
    }
}

data class KcalTotal(val center: Int, val low: Int, val high: Int, val plusMinus: Int)

/**
 * The plate's total kcal with an uncertainty band, from each item's gram range at its own
 * kcal-per-gram rate: center is the sum of the items' point estimates (what's shown today), low/high
 * scale each item's calories by its own gramsLow/gramsHigh, and plusMinus is half the spread —
 * "~620 kcal ±90". Items without a range contribute the same number to every bound.
 */
fun totalKcalRange(items: List<PlateItem>): KcalTotal {
    var center = 0.0
    var low = 0.0
    var high = 0.0
    for (it in items) {
        center += it.calories
        val rate = if (it.grams > 0) it.calories / it.grams else 0.0
        val gLow = it.gramsLow ?: it.grams
        val gHigh = it.gramsHigh ?: it.grams
        low += rate * gLow
        high += rate * gHigh
    }
    val c = Math.round(center).toInt()
    val l = Math.round(low).toInt()
    val h = Math.round(high).toInt()
    return KcalTotal(c, l, h, Math.round((h - l) / 2.0).toInt())
}
