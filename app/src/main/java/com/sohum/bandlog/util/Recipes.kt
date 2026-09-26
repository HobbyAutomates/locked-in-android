package com.sohum.bandlog.util

import kotlin.math.roundToInt

/**
 * v2.13 §8 recipe builder maths (twin of the web's src/lib/recipes.ts). Ingredients carry their own
 * macros for the grams used; totals are summed, then divided by servings. Micros sum key by key.
 * An optional cooked weight gives "per 100 g cooked" and the grams in one serving.
 */
object Recipes {
    data class Ingredient(
        val name: String,
        val grams: Double,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double = 0.0,
        val foodId: String? = null,
        val micros: Map<String, Double> = emptyMap(),
    )

    data class Totals(
        val grams: Double,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double,
        val micros: Map<String, Double>,
    )

    private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    fun totals(items: List<Ingredient>): Totals {
        val micros = mutableMapOf<String, Double>()
        items.forEach { i -> i.micros.forEach { (k, v) -> micros[k] = (micros[k] ?: 0.0) + v } }
        return Totals(
            r1(items.sumOf { it.grams }), r1(items.sumOf { it.kcal }), r1(items.sumOf { it.protein }), r1(items.sumOf { it.carbs }),
            r1(items.sumOf { it.fat }), r1(items.sumOf { it.fiber }), micros.mapValues { r1(it.value) },
        )
    }

    /** Per serving: totals ÷ servings (servings ≥ 0.25); grams = cooked weight ÷ servings when given, else raw grams ÷ servings. */
    fun perServing(items: List<Ingredient>, servings: Double, cookedWeightG: Double? = null): Totals {
        val t = totals(items)
        val s = servings.coerceAtLeast(0.25)
        val g = (cookedWeightG?.takeIf { it > 0 } ?: t.grams) / s
        return Totals(r1(g), r1(t.kcal / s), r1(t.protein / s), r1(t.carbs / s), r1(t.fat / s), r1(t.fiber / s), t.micros.mapValues { r1(it.value / s) })
    }

    /** kcal per 100 g of the cooked dish, when a cooked weight is known. */
    fun per100Cooked(items: List<Ingredient>, cookedWeightG: Double?): Double? {
        val w = cookedWeightG?.takeIf { it > 0 } ?: return null
        return r1(totals(items).kcal / w * 100)
    }

    /** An ingredient at [grams] from per-100 g numbers (a food search pick). */
    fun fromPer100(name: String, grams: Double, kcal100: Double, p100: Double, c100: Double, f100: Double, micros100: Map<String, Double> = emptyMap(), foodId: String? = null): Ingredient {
        val k = grams / 100
        return Ingredient(name, grams, r1(kcal100 * k), r1(p100 * k), r1(c100 * k), r1(f100 * k), r1((micros100["fiber_g"] ?: 0.0) * k), foodId, micros100.mapValues { r1(it.value * k) })
    }
}
