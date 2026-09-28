package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import kotlin.math.roundToInt

/**
 * v2.15 §1 "Calories per roti" / "Calories per 100 g" (mirrors the web's per-unit rescale).
 *
 * Everything is kept per 100 g, like [QuantityFood]. A per-unit number converts through the unit's
 * grams (1 roti = 40 g → 95 kcal per roti = 237.5 kcal per 100 g). When the user changes the
 * calories, protein / carbs / fat scale by the same ratio unless the user typed those too.
 * Total = count × per-unit.
 */
object PerUnit {
    /** The unit name an override uses for loose foods ("Calories per 100 g"). */
    const val PER_100G = "100g"

    data class Per100(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

    /** Per-unit number → per 100 g, through the unit's weight. 0 for a weightless unit. */
    fun toPer100(perUnit: Double, unitGrams: Double): Double = if (unitGrams > 0) perUnit * 100.0 / unitGrams else 0.0

    /** Per 100 g → per unit. */
    fun fromPer100(per100: Double, unitGrams: Double): Double = per100 * unitGrams / 100.0

    /**
     * New per-100 g numbers after the calories change to [kcal100]. Macros the user edited ([proteinG]
     * / [carbsG] / [fatG], per 100 g, non-null) are kept as typed; the rest scale with the calories.
     * A base with no calories can't be scaled, so its untouched macros stay as they were.
     */
    fun rescale(base: Per100, kcal100: Double, proteinG: Double? = null, carbsG: Double? = null, fatG: Double? = null): Per100 {
        val k = if (base.kcal > 0) kcal100 / base.kcal else 1.0
        return Per100(
            kcal = kcal100.coerceAtLeast(0.0),
            proteinG = (proteinG ?: base.proteinG * k).coerceAtLeast(0.0),
            carbsG = (carbsG ?: base.carbsG * k).coerceAtLeast(0.0),
            fatG = (fatG ?: base.fatG * k).coerceAtLeast(0.0),
        )
    }

    /** The food re-priced with new per-100 g numbers (micros are left alone). */
    fun apply(food: QuantityFood, p: Per100): QuantityFood =
        food.copy(calories = p.kcal, proteinG = p.proteinG, carbsG = p.carbsG, fatG = p.fatG)

    fun per100Of(food: QuantityFood) = Per100(food.calories, food.proteinG, food.carbsG, food.fatG)

    /** Total for a count: count × per-unit, one decimal. */
    fun total(count: Double, perUnit: Double): Double = r1(count * perUnit)

    /**
     * The key a per-user override is stored under — the web's overrideKey: the normalised name
     * ("2 Roti" → "roti"), so a roti logged by text, photo or search shares one override; "id:<food_id>"
     * only when the name normalises to nothing.
     */
    fun foodKey(foodId: String?, name: String): String =
        normName(name).ifEmpty { foodId?.takeIf { it.isNotBlank() }?.let { "id:$it" } ?: "" }

    private const val UNIT = "(?:g|gm|gms|gram|grams|kg|mg|ml|l|ltr|litre|litres|liter|liters|oz|cup|cups|katori|katoris|bowl|bowls|plate|plates|piece|pieces|pc|pcs|slice|slices|tbsp|tsp|tablespoons?|teaspoons?|spoons?|scoop|scoops|glass|glasses|mug|mugs|serving|servings|packet|packets|pack|packs|nos?|handful|handfuls|small|medium|large|big|chhota|bada)"
    private const val NUM = "(?:\\d+(?:[.,/]\\d+)?|½|¼|¾|a|an|one|two|three|four|five|six|half|quarter|ek|do|teen|char|aadha|adha|dedh|dhai)"
    private val LEAD = Regex("^$NUM(?:\\s*(?:x\\s*)?$UNIT(?=\\s|$)|\\s+|$)(?:\\s*$UNIT(?=\\s|$))?(?:\\s*of\\s+)?")
    private val TRAIL = Regex("\\s+(?:x\\s*\\d+(?:\\.\\d+)?|\\d+(?:[.,]\\d+)?\\s*$UNIT|$UNIT)$")

    /**
     * The web's foodKey (src/lib/foodKey.ts), ported exactly: lowercase, brackets and quotes dropped,
     * quantities stripped — "2 Roti" → "roti", "1 katori dal tadka" → "dal tadka", "Paneer (200 g)" → "paneer".
     */
    fun normName(name: String): String {
        var s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC).lowercase()
        s = s.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ")
        s = s.replace(Regex("[“”\"`’']"), "")
        s = s.replace(Regex("[^\\p{L}\\p{N}\\p{M}&+\\s-]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        for (i in 0 until 3) {
            val next = s.replace(LEAD, "").replace(TRAIL, "").replace(Regex("\\s+"), " ").trim()
            if (next.isEmpty() || next == s) break
            s = next
        }
        return s.take(80)
    }

    /**
     * The web's scaleToKcal: calories become round([kcal]); each macro is the typed total when given,
     * else round1(macro × kcal / old kcal) (0 when the old kcal is 0).
     */
    fun scaleToKcal(item: MealItem, kcal: Double, proteinG: Double? = null, carbsG: Double? = null, fatG: Double? = null): MealItem {
        val target = Math.round(kcal.coerceAtLeast(0.0)).toDouble()
        val f = if (item.calories > 0) target / item.calories else 0.0
        fun ok(v: Double?) = v != null && v.isFinite() && v >= 0
        return item.copy(
            calories = target,
            proteinG = if (ok(proteinG)) r1(proteinG!!) else r1(item.proteinG * f),
            carbsG = if (ok(carbsG)) r1(carbsG!!) else r1(item.carbsG * f),
            fatG = if (ok(fatG)) r1(fatG!!) else r1(item.fatG * f),
        )
    }

    /** The web's rescalePerUnit: [count] units at [unitKcal] each; per-unit macros replace the scaling when given. */
    fun rescalePerUnit(item: MealItem, count: Double, unitKcal: Double, unitProtein: Double? = null, unitCarbs: Double? = null, unitFat: Double? = null): MealItem {
        val n = count.coerceAtLeast(0.0)
        return scaleToKcal(item, unitKcal * n, unitProtein?.let { it * n }, unitCarbs?.let { it * n }, unitFat?.let { it * n })
            .copy(perUnitKcal = Math.round(unitKcal).toDouble())
    }

    /** The web's rescalePer100: a loose item at [per100] kcal per 100 g. */
    fun rescalePer100(item: MealItem, per100: Double, protein100: Double? = null, carbs100: Double? = null, fat100: Double? = null): MealItem {
        val k = item.grams / 100.0
        return scaleToKcal(item, per100 * k, protein100?.let { it * k }, carbs100?.let { it * k }, fat100?.let { it * k })
    }

    /** The web's applyCorrection: the person's totals win, blank macros follow the kcal; marks it theirs. */
    fun applyCorrection(item: MealItem, kcal: Double, proteinG: Double? = null, carbsG: Double? = null, fatG: Double? = null): MealItem =
        scaleToKcal(item, kcal, proteinG, carbsG, fatG).copy(userVerified = true)

    /** % error of the app's kcal against the person's (+ = we over-estimated), one decimal. */
    fun pctError(appKcal: Double, userKcal: Double): Double? =
        if (userKcal > 0 && appKcal.isFinite()) Math.round((appKcal - userKcal) / userKcal * 1000) / 10.0 else null

    /** A remembered per-unit number (bandlog.user_food_overrides). Macros are per unit, null = scale with kcal. */
    data class Override(
        val foodKey: String,
        val unit: String,
        val kcalPerUnit: Double,
        val proteinPerUnit: Double? = null,
        val carbsPerUnit: Double? = null,
        val fatPerUnit: Double? = null,
    )

    /**
     * The count and unit noun a logged row is in: "2 roti" → (2, roti) for a row counted in servings,
     * or the parser's default_count with a noun from the name. Null when the row is only by weight.
     */
    fun countOf(item: MealItem): Pair<Double, String>? {
        val n = item.servings
        item.servingUnit?.let { su ->
            val noun = Counting.unitFor(listOf(su), su.label)?.takeIf { it.label == null }?.noun
            if (noun != null && item.unit == "serving" && n != null && n > 0) return n to noun
        }
        if (item.unit == "serving" && n != null && n > 0) {
            val noun = Counting.savedUnitOf(item)?.label?.removePrefix("1 ")?.trim() ?: return null
            return n to noun
        }
        val dc = item.defaultCount ?: return null
        val noun = Counting.savedUnitOf(item.copy(unit = "serving", servings = dc))?.label?.removePrefix("1 ")?.trim() ?: return null
        return dc to noun
    }

    /**
     * The user's remembered number applied to a fresh row: a per-100 g override prices the grams, a
     * per-unit one prices count × per-unit when the row counts in that unit. Idempotent (applying it
     * twice gives the same row). Null when the override doesn't fit this row.
     */
    fun applyOverride(item: MealItem, o: Override): MealItem? {
        if (o.unit == PER_100G) {
            if (item.grams <= 0) return null
            return rescalePer100(item, o.kcalPerUnit, o.proteinPerUnit, o.carbsPerUnit, o.fatPerUnit)
        }
        val (n, noun) = countOf(item) ?: return null
        if (!sameNoun(noun, o.unit)) return null
        return rescalePerUnit(item, n, o.kcalPerUnit, o.proteinPerUnit, o.carbsPerUnit, o.fatPerUnit)
    }

    fun sameNoun(a: String, b: String): Boolean = Counting.singular(a.trim().lowercase()) == Counting.singular(b.trim().lowercase())

    fun r1(d: Double): Double = (d * 10).roundToInt() / 10.0
}
