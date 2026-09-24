package com.sohum.bandlog.util

import com.sohum.bandlog.data.FoodHit
import com.sohum.bandlog.data.FoodPreset
import com.sohum.bandlog.data.LabelReport
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Serving
import kotlin.math.roundToInt

/**
 * The quantity model behind the Quantity sheet (mirrors the web's lib/quantity.ts): a food known
 * per 100 g, optional household servings, and a chosen amount in g / ml / kg / servings.
 */
enum class QUnit(val label: String) { G("g"), ML("ml"), KG("kg"), SERVING("serving") }

data class Quantity(val unit: QUnit, val value: Double)

/** Everything the sheet needs to price a quantity. Per-100 g numbers, servings in grams. */
data class QuantityFood(
    val name: String,
    val nameHi: String? = null,
    val foodId: String?,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    /** Per 100 g micros (scaled by the sheet). */
    val micros: Map<String, Double> = emptyMap(),
    val servings: List<Serving> = emptyList(),
    /** The serving the stepper counts in (defaults to the first of [servings]). */
    val defaultServing: String? = null,
    /** g per ml when known (defaults to 1.0). */
    val density: Double? = null,
    /** Whole-pack weight when known (barcode / label scans). */
    val packGrams: Double? = null,
    /** table | estimated | scan */
    val source: String = "table",
    /** v2.0: the preset category (dal / sabzi / protein / restaurant …) — decides the restaurant oil. */
    val category: String? = null,
) {
    val serving: Serving? get() = servings.firstOrNull { it.label == defaultServing } ?: servings.firstOrNull()
    val servingGrams: Double? get() = serving?.grams?.takeIf { it > 0 }

    /** Grams for a quantity: ml → g at density (1.0 unknown), kg × 1000, servings × serving size. */
    fun grams(q: Quantity): Double {
        val v = q.value.coerceAtLeast(0.0)
        return when (q.unit) {
            QUnit.KG -> v * 1000
            QUnit.ML -> v * (density?.takeIf { it > 0 } ?: 1.0)
            QUnit.SERVING -> v * (servingGrams ?: 100.0)
            QUnit.G -> v
        }
    }

    /** How many default servings [g] is, or null without a serving size. */
    fun servingsFor(g: Double): Double? = servingGrams?.let { (g / it * 100).roundToInt() / 100.0 }

    /** A priced MealItem for this quantity (macros linear, micros included). */
    fun item(q: Quantity): MealItem {
        val g = (grams(q) * 10).roundToInt() / 10.0
        val k = g / 100.0
        return MealItem(
            foodId = foodId, name = name, grams = g,
            calories = (calories * k * 10).roundToInt() / 10.0,
            proteinG = (proteinG * k * 10).roundToInt() / 10.0,
            carbsG = (carbsG * k * 10).roundToInt() / 10.0,
            fatG = (fatG * k * 10).roundToInt() / 10.0,
            source = source,
            confidence = when (source) { "table" -> 1.0; "scan" -> 0.9; else -> null },
            micros = micros.mapValues { (it.value * k * 10).roundToInt() / 10.0 },
            unit = q.unit.label,
            servings = if (q.unit == QUnit.SERVING) q.value else servingsFor(g),
        )
    }

    /** ½ · 1 · 2 servings, 50 g, 100 g, ½ pack, 1 pack. */
    fun quickChips(): List<Pair<String, Quantity>> {
        val out = mutableListOf<Pair<String, Quantity>>()
        val sl = serving?.label ?: "serving"
        if (servingGrams != null) {
            out += "½ $sl" to Quantity(QUnit.SERVING, 0.5)
            out += "1 $sl" to Quantity(QUnit.SERVING, 1.0)
            out += "2 $sl" to Quantity(QUnit.SERVING, 2.0)
        }
        out += "50 g" to Quantity(QUnit.G, 50.0)
        out += "100 g" to Quantity(QUnit.G, 100.0)
        packGrams?.takeIf { it > 0 }?.let { pg ->
            out += "½ pack" to Quantity(QUnit.G, (pg / 2).roundToInt().toDouble())
            out += "1 pack" to Quantity(QUnit.G, pg.roundToInt().toDouble())
        }
        return out
    }

    companion object {
        fun from(p: FoodPreset, serving: String? = p.defaultServing) = QuantityFood(
            name = p.label, nameHi = p.labelHi, foodId = p.foodId,
            calories = p.calories, proteinG = p.proteinG, carbsG = p.carbsG, fatG = p.fatG,
            micros = p.micros, servings = p.servings, defaultServing = serving, source = "table", category = p.category,
        )

        fun from(h: FoodHit) = QuantityFood(
            name = h.name, nameHi = h.nameHi, foodId = h.id,
            calories = h.calories, proteinG = h.proteinG, carbsG = h.carbsG, fatG = h.fatG,
            micros = h.micros, servings = h.units, source = "table",
        )

        /** A scan report as a food: per-100 g from the label, one serving = serving_g. Null without numbers. */
        fun from(r: LabelReport): QuantityFood? {
            val kcal = r.per100["calories"]
            val prot = r.per100["protein_g"]
            if (kcal == null && prot == null) return null
            val serving = r.servingG?.takeIf { it > 0 }?.let { listOf(Serving("1 serving", it)) } ?: emptyList()
            return QuantityFood(
                name = r.product.ifBlank { "Scanned product" }, foodId = null,
                calories = kcal ?: 0.0, proteinG = prot ?: 0.0, carbsG = r.per100["carbs_g"] ?: 0.0, fatG = r.per100["fat_g"] ?: 0.0,
                micros = listOf("sugar_g", "fiber_g", "sodium_mg").mapNotNull { k -> r.per100[k]?.let { k to it } }.toMap(),
                servings = serving, defaultServing = serving.firstOrNull()?.label, source = "scan",
            )
        }

        /** Wrap an existing review row so its amount can be re-entered through the sheet. */
        fun from(it: MealItem, servings: List<Serving> = emptyList()): QuantityFood {
            val k = if (it.grams > 0) 100.0 / it.grams else 0.0
            return QuantityFood(
                name = it.name, foodId = it.foodId,
                calories = it.calories * k, proteinG = it.proteinG * k, carbsG = it.carbsG * k, fatG = it.fatG * k,
                micros = it.micros.mapValues { m -> m.value * k }, servings = servings, source = it.source,
            )
        }

        /** Dishes that usually carry cooking fat — the "Cooked in…" row shows after these. */
        private val COOKED = Regex("\\b(dal|daal|dhal|sambar|rajma|chole|chana|sabzi|sabji|bhindi|gobi|paneer|bharta|paratha|omelette|omelet|egg|anda|bhurji|curry|matar|aloo|palak|khichdi|poha|upma|pulao|biryani)\\b", RegexOption.IGNORE_CASE)
        fun wantsCookedIn(name: String, category: String? = null): Boolean = category == "dal" || category == "sabzi" || COOKED.containsMatchIn(name)
    }
}

/**
 * v2.0 restaurant portions (mirrors the web's lib/quantity.ts): outside kitchens serve about 1.4x a
 * home katori, and curries / dal / sabzi carry roughly a teaspoon more oil than the home recipe.
 */
object Restaurant {
    const val MULTIPLIER = 1.4
    /** 1 tsp of oil, added to the item's fat (and its 44 kcal) rather than as a row of its own. */
    const val OIL_G = 5.0
    private const val OIL_KCAL_PER_G = 8.84
    private val FAST_FOOD = Regex("\\b(pizza|burger|fries|momo|momos|sandwich)\\b", RegexOption.IGNORE_CASE)

    /** Whether the toggle also adds the hidden teaspoon of oil. */
    fun oily(name: String, category: String?): Boolean = when (category) {
        "dal", "sabzi", "protein" -> true
        "restaurant" -> !FAST_FOOD.containsMatchIn(name)
        null -> QuantityFood.wantsCookedIn(name)
        else -> false
    }

    /** Packaged scans come with a printed serving; everything else can be a restaurant portion. */
    fun allowed(food: QuantityFood): Boolean = food.source != "scan"

    /** x1.4 the amount, +1 tsp oil when [oily], "(restaurant)" on the name, cooked_in = "restaurant". */
    fun apply(item: MealItem, oily: Boolean): MealItem {
        val scaled = item.withGrams(((item.grams * MULTIPLIER) * 10).roundToInt() / 10.0)
        val oil = if (oily) OIL_G else 0.0
        return scaled.copy(
            name = if (item.name.endsWith("(restaurant)", ignoreCase = true)) item.name else "${item.name} (restaurant)",
            calories = ((scaled.calories + oil * OIL_KCAL_PER_G) * 10).roundToInt() / 10.0,
            proteinG = (scaled.proteinG * 10).roundToInt() / 10.0,
            carbsG = (scaled.carbsG * 10).roundToInt() / 10.0,
            fatG = ((scaled.fatG + oil) * 10).roundToInt() / 10.0,
            micros = scaled.micros.mapValues { (it.value * 10).roundToInt() / 10.0 },
            cookedIn = "restaurant",
        )
    }
}
