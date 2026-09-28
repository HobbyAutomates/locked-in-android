package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.PlateItem
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mirrors the web's src/lib/food/honesty.ts (v2.18 A3 honest confidence; the web copy wins). Every
 * logged item gets a ± kcal range and every day an accuracy score (0–100) with the vaguest entries
 * to fix first.
 *
 * The range is the stored kcal_low / kcal_high when there is one; otherwise it comes from the source:
 * your own numbers ±5 %, a scanned label ±8 %, your recipe ±10 %, the food table ±12 %, web-checked
 * ±12 / 20 / 30 % and an unsourced AI estimate ±20 / 30 / 40 % by confidence. A meal's ± adds the
 * items' half-widths in quadrature. Score = 100 × (1 − kcal-weighted relative half-width ÷ 0.4).
 * Same cases as scripts/check-v218-food.ts ↔ FoodHonestyTest.
 */
object FoodHonesty {
    const val WORST_REL = 0.4

    private fun jsRound(v: Double) = floor(v + 0.5)

    data class Range(val low: Int, val high: Int, val plusMinus: Int, val rel: Double)

    /** Relative half-width (0.05 = ±5 %) of an item's calories. */
    fun relUncertainty(it: MealItem): Double {
        if (it.userVerified) return 0.05
        if (it.source == "scan") return 0.08
        if (it.unit == "recipe") return 0.1
        if (it.source == "table") return 0.12
        val c = it.confidence?.takeIf { v -> v.isFinite() } ?: 0.5
        val web = it.sourceUrls.isNotEmpty()
        if (c >= 0.85) return if (web) 0.12 else 0.2
        if (c >= 0.55) return if (web) 0.2 else 0.3
        return if (web) 0.3 else WORST_REL
    }

    fun kcalRange(it: MealItem): Range {
        val kcal = max(0.0, if (it.calories.isFinite()) it.calories else 0.0)
        val lo = it.kcalLow ?: Double.NaN
        val hi = it.kcalHigh ?: Double.NaN
        // A stored range only counts while it still brackets the calories (an edited amount falls back).
        if (lo.isFinite() && hi.isFinite() && hi >= lo && lo >= 0 && hi > 0 && kcal >= lo - 1 && kcal <= hi + 1) {
            val pm = jsRound((hi - lo) / 2)
            return Range(jsRound(lo).toInt(), jsRound(hi).toInt(), pm.toInt(), if (kcal > 0) pm / kcal else 0.0)
        }
        val rel = relUncertainty(it)
        val pm = jsRound(kcal * rel)
        return Range(max(0.0, jsRound(kcal - pm)).toInt(), jsRound(kcal + pm).toInt(), pm.toInt(), rel)
    }

    /** "±40" for display (a range under 5 kcal isn't worth showing). */
    fun plusMinusLabel(it: MealItem): String {
        val r = kcalRange(it)
        return if (r.plusMinus >= 5) "±${r.plusMinus}" else ""
    }

    /** Several items' total ± (quadrature). */
    fun totalPlusMinus(items: List<MealItem>): Int =
        jsRound(sqrt(items.sumOf { val pm = kcalRange(it).plusMinus.toDouble(); pm * pm })).toInt()

    data class VagueItem(val mealId: String, val index: Int, val name: String, val kcal: Int, val plusMinus: Int)
    data class DayAccuracy(val score: Int?, val items: Int, val vague: List<VagueItem>, val label: String)
    data class AccMeal(val id: String, val items: List<MealItem>)

    /** The day's accuracy score and the entries worth fixing (biggest ± first, max 3). null score = nothing logged. */
    fun dayAccuracy(meals: List<AccMeal>): DayAccuracy {
        var kcal = 0.0
        var weighted = 0.0
        var n = 0
        val vague = ArrayList<VagueItem>()
        for (m in meals) {
            m.items.forEachIndexed { index, it ->
                val c = max(0.0, if (it.calories.isFinite()) it.calories else 0.0)
                val r = kcalRange(it)
                n++
                kcal += c
                weighted += c * min(WORST_REL, r.rel)
                // "Vague": at least ±25 % AND at least ±40 kcal.
                if (r.rel >= 0.25 && r.plusMinus >= 40) vague.add(VagueItem(m.id, index, it.name, jsRound(c).toInt(), r.plusMinus))
            }
        }
        if (n == 0 || kcal <= 0) return DayAccuracy(null, n, emptyList(), "")
        val score = max(0.0, min(100.0, jsRound(100 * (1 - weighted / kcal / WORST_REL)))).toInt()
        val sorted = vague.sortedByDescending { it.plusMinus }
        return DayAccuracy(score, n, sorted.take(3), accuracyWord(score))
    }

    fun accuracyWord(score: Int): String = when {
        score >= 80 -> "Sharp"
        score >= 60 -> "Good"
        score >= 40 -> "Rough"
        else -> "Guessy"
    }

    data class KcalLowHigh(val kcalLow: Int, val kcalHigh: Int)

    /** kcal_low / kcal_high to store for a photo item from its gram range (null when there is none). */
    fun rangeFromGrams(calories: Double, grams: Double, gramsLow: Double?, gramsHigh: Double?): KcalLowHigh? {
        if (!(grams > 0) || gramsLow == null || gramsHigh == null || !(gramsHigh > gramsLow)) return null
        // A range that no longer brackets the amount (the grams were edited) says nothing about it.
        if (grams < gramsLow || grams > gramsHigh) return null
        val rate = calories / grams
        return KcalLowHigh(jsRound(rate * gramsLow).toInt(), jsRound(rate * gramsHigh).toInt())
    }

    fun rangeFromGrams(it: PlateItem): KcalLowHigh? = rangeFromGrams(it.calories, it.grams, it.gramsLow, it.gramsHigh)
}
