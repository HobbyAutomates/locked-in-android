package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import kotlin.math.floor
import kotlin.math.max

/**
 * v2.13 §8 recipe builder maths — a port of the web's src/lib/recipes.ts: totals and per-serving
 * numbers computed on the client (stored in recipes.per_serving), and the meal item a logged
 * serving becomes (unit "recipe", so the ⓘ sheet can say "Your recipe").
 */
object Recipes {
    data class Per100(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val micros: Map<String, Double> = emptyMap())

    data class Ingredient(
        val name: String,
        val grams: Double,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double? = null,
        val foodId: String? = null,
        /** Micros already scaled to [grams]. */
        val micros: Map<String, Double> = emptyMap(),
        /** Per-100 g numbers so a grams edit re-prices the row. */
        val per100: Per100? = null,
    )

    data class Totals(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double, val grams: Double, val micros: Map<String, Double>)

    private fun jsRound(v: Double) = floor(v + 0.5)
    private fun r1(v: Double) = jsRound(v * 10) / 10

    /** An ingredient re-priced at [grams] from its per-100 g numbers (scaled linearly when there are none). */
    fun price(it: Ingredient, grams: Double): Ingredient {
        val g = max(0.0, r1(grams))
        val p = it.per100
        if (p == null) {
            if (!(it.grams > 0)) return it.copy(grams = g)
            val k = g / it.grams
            return it.copy(
                grams = g, kcal = jsRound(it.kcal * k), protein = r1(it.protein * k), carbs = r1(it.carbs * k), fat = r1(it.fat * k),
                fiber = r1((it.fiber ?: 0.0) * k), micros = it.micros.filterValues { v -> v.isFinite() }.mapValues { r1(it.value * k) },
            )
        }
        val k = g / 100
        val micros = p.micros.filterValues { v -> v.isFinite() }.mapValues { r1(it.value * k) }
        return it.copy(grams = g, kcal = jsRound(p.kcal * k), protein = r1(p.protein * k), carbs = r1(p.carbs * k), fat = r1(p.fat * k), fiber = r1(micros["fiber_g"] ?: 0.0), micros = micros)
    }

    fun totals(items: List<Ingredient>): Totals {
        val micros = LinkedHashMap<String, Double>()
        var kcal = 0.0; var p = 0.0; var c = 0.0; var f = 0.0; var fib = 0.0; var g = 0.0
        for (it in items) {
            kcal += it.kcal; p += it.protein; c += it.carbs; f += it.fat
            fib += it.fiber ?: it.micros["fiber_g"] ?: 0.0
            g += it.grams
            for ((k, v) in it.micros) if (v.isFinite()) micros[k] = (micros[k] ?: 0.0) + v
        }
        return Totals(jsRound(kcal), r1(p), r1(c), r1(f), r1(fib), r1(g), micros.mapValues { r1(it.value) })
    }

    /** Per serving (servings ≤ 0 counts as one; a missing / zero cooked weight falls back to the raw weight). */
    fun perServing(items: List<Ingredient>, servings: Double, cookedWeightG: Double? = null): Totals {
        val t = totals(items)
        val n = if (servings > 0) servings else 1.0
        val weight = cookedWeightG?.takeIf { it > 0 } ?: t.grams
        return Totals(jsRound(t.kcal / n), r1(t.protein / n), r1(t.carbs / n), r1(t.fat / n), r1(t.fiber / n), r1(weight / n), t.micros.mapValues { r1(it.value / n) })
    }

    /** kcal per 100 g of the cooked dish, when a cooked weight is known. */
    fun per100Cooked(items: List<Ingredient>, cookedWeightG: Double?): Double? {
        val w = cookedWeightG?.takeIf { it > 0 } ?: return null
        return r1(totals(items).kcal / w * 100)
    }

    /** An ingredient at [grams] from a food search pick's per-100 g numbers. */
    fun fromPer100(name: String, grams: Double, kcal100: Double, p100: Double, c100: Double, f100: Double, micros100: Map<String, Double> = emptyMap(), foodId: String? = null): Ingredient =
        price(Ingredient(name, 0.0, 0.0, 0.0, 0.0, 0.0, foodId = foodId, per100 = Per100(kcal100, p100, c100, f100, micros100)), grams)

    /** Marks a meal item logged from a recipe (meal_items.unit). */
    const val RECIPE_UNIT = "recipe"

    /** "Log a serving": [count] servings as one meal item named after the recipe. */
    fun mealItem(name: String, ps: Totals, count: Double = 1.0): MealItem {
        val n = if (count > 0) count else 1.0
        val micros = ps.micros.filterValues { it.isFinite() }.mapValues { r1(it.value * n) }.toMutableMap()
        if (ps.fiber > 0 && micros["fiber_g"] == null) micros["fiber_g"] = r1(ps.fiber * n)
        return MealItem(
            foodId = null, name = name, grams = r1(ps.grams * n), calories = jsRound(ps.kcal * n),
            proteinG = r1(ps.protein * n), carbsG = r1(ps.carbs * n), fatG = r1(ps.fat * n),
            source = "table", confidence = 1.0, micros = micros, unit = RECIPE_UNIT, servings = n,
        )
    }

    fun isRecipeItem(unit: String?): Boolean = unit == RECIPE_UNIT
}
