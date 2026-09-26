package com.sohum.bandlog.util

import com.sohum.bandlog.data.FoodPreset
import com.sohum.bandlog.data.MealItem
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * v2.13 §6 "What should I eat?" — a line-for-line port of the web's src/lib/whatToEat.ts. Ranks the
 * Indian presets for what's left today:
 *
 *   score = 0.5 × protein density + 0.3 × fits the kcal left + 0.2 × suits this meal time
 *
 *   protein density  protein g per 100 kcal of the portion, divided by 12 and capped at 1.
 *   fits             1 when the portion's kcal is inside what's left; otherwise it falls off
 *                    linearly and reaches 0 when the portion is twice what's left (or nothing is left).
 *   meal time        the preset category's affinity for the meal type ([MEAL_FIT]).
 *
 * The portion is what one tap adds everywhere else (the preset's count unit at its default count,
 * else 100 g). Fats are never suggested; the diet mode's food filter applies. Ties break on the label.
 */
object WhatToEat {

    data class Remaining(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

    data class Suggestion(
        val preset: FoodPreset,
        val portion: String,
        /** Ready to save: priced like a one-tap preset add. */
        val item: MealItem,
        val score: Double,
        val usual: Boolean = false,
    )

    const val W_PROTEIN = 0.5
    const val W_FIT = 0.3
    const val W_MEAL = 0.2
    const val PROTEIN_DENSITY_TOP = 12.0

    /** How well a preset category suits each meal time (0–1); unknown categories score 0.3. */
    val MEAL_FIT: Map<String, Map<String, Double>> = mapOf(
        "breakfast" to mapOf("breakfast" to 1.0, "lunch" to 0.3, "dinner" to 0.3, "snack" to 0.6),
        "staple" to mapOf("breakfast" to 0.4, "lunch" to 1.0, "dinner" to 1.0, "snack" to 0.2),
        "dal" to mapOf("breakfast" to 0.2, "lunch" to 1.0, "dinner" to 1.0, "snack" to 0.2),
        "sabzi" to mapOf("breakfast" to 0.3, "lunch" to 1.0, "dinner" to 1.0, "snack" to 0.2),
        "protein" to mapOf("breakfast" to 0.8, "lunch" to 1.0, "dinner" to 1.0, "snack" to 0.7),
        "snack" to mapOf("breakfast" to 0.5, "lunch" to 0.3, "dinner" to 0.2, "snack" to 1.0),
        "drink" to mapOf("breakfast" to 0.8, "lunch" to 0.3, "dinner" to 0.2, "snack" to 0.9),
        "sweet" to mapOf("breakfast" to 0.1, "lunch" to 0.3, "dinner" to 0.3, "snack" to 0.5),
        "fruit" to mapOf("breakfast" to 0.9, "lunch" to 0.3, "dinner" to 0.2, "snack" to 1.0),
        "fat" to mapOf("breakfast" to 0.0, "lunch" to 0.0, "dinner" to 0.0, "snack" to 0.0),
        "restaurant" to mapOf("breakfast" to 0.2, "lunch" to 0.8, "dinner" to 0.8, "snack" to 0.3),
    )

    private fun jsRound(v: Double): Double = floor(v + 0.5)
    private fun r1(v: Double) = jsRound(v * 10) / 10

    data class Portion(val grams: Double, val label: String, val count: Double?)

    /** The portion one tap adds, from the preset's servings. */
    fun portionOf(p: FoodPreset): Portion {
        val cu = Counting.unitFor(p.servings, p.defaultServing) ?: return Portion(100.0, "100 g", null)
        return Portion(jsRound(cu.grams * cu.defaultCount * 10) / 10, Counting.countLabel(cu, cu.defaultCount), cu.defaultCount)
    }

    fun presetItem(p: FoodPreset): MealItem {
        val por = portionOf(p)
        val k = por.grams / 100
        return MealItem(
            foodId = p.foodId, name = p.label, grams = por.grams,
            calories = jsRound(p.calories * k), proteinG = r1(p.proteinG * k), carbsG = r1(p.carbsG * k), fatG = r1(p.fatG * k),
            source = "table", confidence = 1.0,
            micros = p.micros.filterValues { it.isFinite() }.mapValues { r1(it.value * k) },
            unit = if (por.count != null) "serving" else "g", servings = por.count,
            imageUrl = p.imageUrl,
        )
    }

    fun proteinScore(protein: Double, kcal: Double): Double = if (!(kcal > 0)) 0.0 else min(1.0, protein / kcal * 100 / PROTEIN_DENSITY_TOP)

    fun fitScore(kcal: Double, remainingKcal: Double): Double {
        if (remainingKcal <= 0) return 0.0
        if (kcal <= remainingKcal) return 1.0
        return max(0.0, 1 - (kcal - remainingKcal) / remainingKcal)
    }

    fun scorePreset(p: FoodPreset, remaining: Remaining, mealType: String): Double {
        val item = presetItem(p)
        val s = W_PROTEIN * proteinScore(item.proteinG, item.calories) + W_FIT * fitScore(item.calories, remaining.kcal) + W_MEAL * (MEAL_FIT[p.category]?.get(mealType) ?: 0.3)
        return jsRound(s * 1000) / 1000
    }

    private fun toSuggestion(p: FoodPreset, score: Double, usual: Boolean = false) = Suggestion(p, portionOf(p).label, presetItem(p), score, usual)

    private val collator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.ENGLISH)

    /** Presets that can be suggested at all: no fats, zero-calorie rows or foods the mode leaves out. */
    private fun eligible(presets: List<FoodPreset>, mode: String): List<FoodPreset> {
        val seen = HashSet<String>()
        return presets.filter { p ->
            p.category != "fat" && p.calories > 0 && DietModes.allows(mode, "${p.label} ${p.foodName}") && seen.add(p.foodId)
        }
    }

    /** The top [limit] presets for what's left today, best first. */
    fun suggest(remaining: Remaining, mode: String, mealType: String, presets: List<FoodPreset>, limit: Int = 5): List<Suggestion> =
        eligible(presets, mode).map { it to scorePreset(it, remaining, mealType) }
            .sortedWith { a, b -> if (a.second != b.second) b.second.compareTo(a.second) else collator.compare(a.first.label, b.first.label) }
            .take(limit).map { (p, s) -> toSuggestion(p, s) }

    /**
     * "Your usual": the foods this person eats most ([usage] = food_id → times in the last 60 days)
     * that fit the mode and what's left, most eaten first. Never ones already suggested ([exclude] = preset ids).
     */
    fun usual(remaining: Remaining, mode: String, mealType: String, presets: List<FoodPreset>, usage: Map<String, Int>, exclude: Collection<String> = emptyList(), limit: Int = 3): List<Suggestion> =
        eligible(presets, mode)
            .filter { (usage[it.foodId] ?: 0) > 0 && it.id !in exclude }
            .filter { fitScore(presetItem(it).calories, remaining.kcal) > 0 }
            .sortedWith { a, b -> val d = (usage[b.foodId] ?: 0) - (usage[a.foodId] ?: 0); if (d != 0) d else collator.compare(a.label, b.label) }
            .take(limit).map { toSuggestion(it, scorePreset(it, remaining, mealType), usual = true) }

    /** What's left today (never negative, whole numbers). */
    fun remainingFrom(kcal: Double, protein: Double, carbs: Double, fat: Double, eatenKcal: Double, eatenProtein: Double, eatenCarbs: Double, eatenFat: Double): Remaining = Remaining(
        max(0.0, jsRound(kcal - eatenKcal)), max(0.0, jsRound(protein - eatenProtein)), max(0.0, jsRound(carbs - eatenCarbs)), max(0.0, jsRound(fat - eatenFat)),
    )

    /** "Try dal, paneer bhurji or curd" — the protein nudge's body (§3), from up to three picks. */
    fun tryLine(picks: List<String>): String = when (picks.size) {
        0 -> ""
        1 -> "Try ${picks[0]}"
        2 -> "Try ${picks[0]} or ${picks[1]}"
        else -> "Try ${picks[0]}, ${picks[1]} or ${picks[2]}"
    }
}
