package com.sohum.bandlog.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v2.13 §6 "What should I eat?" — pure ranking, the twin of the web's src/lib/whatToEat.ts.
 *
 * Candidates are Indian dishes from the presets / foods DB at their default portion. Each is scored
 *   0.5 × protein per 100 kcal  (normalised: 10 g per 100 kcal or more = 1)
 * + 0.3 × fits the remaining kcal (1 inside it, falling to 0 at twice it)
 * + 0.2 × closeness to this time of day's meal type (see [mealFit]).
 * The diet mode's food filter drops anything it rules out (suggestions only; logging is never blocked).
 */
object WhatToEat {

    data class Food(
        val name: String,
        /** Preset category: breakfast | staple | dal | sabzi | protein | snack | drink | sweet | fruit | restaurant … */
        val category: String?,
        /** "1 katori", "2 roti", "100 g". */
        val portion: String,
        val grams: Double,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val foodId: String? = null,
        val micros: Map<String, Double> = emptyMap(),
        val imageUrl: String? = null,
    )

    data class Remaining(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

    data class Pick(val food: Food, val score: Double, val why: String)

    const val W_PROTEIN = 0.5
    const val W_FIT = 0.3
    const val W_MEAL = 0.2
    /** Protein density that scores a full 1.0. */
    const val FULL_PROTEIN_PER_100_KCAL = 10.0

    fun proteinPer100Kcal(f: Food): Double = if (f.kcal <= 0) 0.0 else f.protein / f.kcal * 100

    fun proteinScore(f: Food): Double = min(1.0, proteinPer100Kcal(f) / FULL_PROTEIN_PER_100_KCAL)

    /** 1 when the portion fits the kcal left; linearly down to 0 at twice what's left (nothing left: small portions win). */
    fun fitScore(kcal: Double, remainingKcal: Double): Double {
        if (remainingKcal <= 0) return max(0.0, 1 - kcal / 300.0)
        if (kcal <= remainingKcal) return 1.0
        return max(0.0, 1 - (kcal - remainingKcal) / remainingKcal)
    }

    /** How well a preset category suits [mealType] (breakfast | lunch | dinner | snack), 0..1. */
    fun mealFit(category: String?, mealType: String): Double = when (category) {
        "breakfast" -> if (mealType == "breakfast") 1.0 else if (mealType == "snack") 0.6 else 0.3
        "staple", "dal", "sabzi", "restaurant" -> if (mealType == "lunch" || mealType == "dinner") 1.0 else 0.3
        "protein" -> if (mealType == "lunch" || mealType == "dinner") 1.0 else 0.7
        "snack", "fruit", "drink" -> if (mealType == "snack") 1.0 else if (mealType == "breakfast") 0.7 else 0.4
        "sweet" -> if (mealType == "snack") 0.5 else 0.2
        else -> 0.5
    }

    fun score(f: Food, r: Remaining, mealType: String): Double =
        W_PROTEIN * proteinScore(f) + W_FIT * fitScore(f.kcal, r.kcal) + W_MEAL * mealFit(f.category, mealType)

    private fun why(f: Food, r: Remaining): String {
        val p = proteinPer100Kcal(f)
        val bits = mutableListOf<String>()
        if (p >= 8) bits += "high protein" else if (p >= 5) bits += "good protein"
        if (r.kcal > 0 && f.kcal <= r.kcal) bits += "fits your ${r.kcal.roundToInt()} kcal left"
        else if (r.kcal > 0) bits += "a bit over what's left"
        return bits.joinToString(" · ").replaceFirstChar { it.uppercase() }.ifBlank { "Fits this time of day" }
    }

    /**
     * Top [n] picks for what's left today, filtered by the diet mode. Fats / oils and zero-kcal rows are
     * skipped; ties break on protein, then name, so both apps list the same order.
     */
    fun rank(foods: List<Food>, remaining: Remaining, mealType: String, dietMode: String, n: Int = 5): List<Pick> =
        foods.asSequence()
            .filter { it.kcal > 0 && it.category != "fat" }
            .filter { DietModes.allows(dietMode, it.name, it.category) }
            .distinctBy { (it.foodId ?: it.name).lowercase() }
            .map { Pick(it, score(it, remaining, mealType), why(it, remaining)) }
            .sortedWith(compareByDescending<Pick> { (it.score * 1e6).roundToInt() }.thenByDescending { it.food.protein }.thenBy { it.food.name })
            .take(n)
            .toList()

    /** One of the user's own foods: how often it was logged, and the last portion. */
    data class Usual(val food: Food, val times: Int)

    /**
     * "Your usual": the user's most-logged foods (2+ times), newest portion, filtered by the diet mode,
     * most frequent first.
     */
    fun usual(logged: List<Pair<String, Food>>, dietMode: String, n: Int = 3): List<Usual> {
        // [logged] is (createdAt, food), any order.
        val groups = logged.groupBy { it.second.name.trim().lowercase() }
        return groups.values.asSequence()
            .filter { it.size >= 2 }
            .map { g -> Usual(g.maxBy { it.first }.second, g.size) }
            .filter { it.food.kcal > 0 && DietModes.allows(dietMode, it.food.name, it.food.category) }
            .sortedWith(compareByDescending<Usual> { it.times }.thenBy { it.food.name })
            .take(n)
            .toList()
    }

    /** "Try dal, paneer bhurji or curd" — the protein nudge's body (§3), from up to three picks. */
    fun tryLine(picks: List<String>): String = when (picks.size) {
        0 -> ""
        1 -> "Try ${picks[0]}"
        2 -> "Try ${picks[0]} or ${picks[1]}"
        else -> "Try ${picks[0]}, ${picks[1]} or ${picks[2]}"
    }
}
