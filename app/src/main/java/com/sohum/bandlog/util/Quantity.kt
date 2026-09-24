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
    /** v2.4: the food's picture, carried onto the plate row. */
    val imageUrl: String? = null,
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
            imageUrl = imageUrl,
        )
    }

    /** ½ · 1 · 2 servings, 50 g, 100 g, ½ pack, 1 pack. */
    fun quickChips(): List<Pair<String, Quantity>> {
        val out = mutableListOf<Pair<String, Quantity>>()
        val sl = serving?.label?.removePrefix("1 ")?.trim()?.ifBlank { null } ?: "serving"
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
            imageUrl = p.imageUrl,
        )

        fun from(h: FoodHit) = QuantityFood(
            name = h.name, nameHi = h.nameHi, foodId = h.id,
            calories = h.calories, proteinG = h.proteinG, carbsG = h.carbsG, fatG = h.fatG,
            micros = h.micros, servings = h.units, source = "table", imageUrl = h.imageUrl,
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
                servings = serving, defaultServing = serving.firstOrNull()?.label, source = "scan", imageUrl = r.imageUrl,
            )
        }

        /** Wrap an existing review row so its amount can be re-entered through the sheet. */
        fun from(it: MealItem, servings: List<Serving> = emptyList()): QuantityFood {
            val k = if (it.grams > 0) 100.0 / it.grams else 0.0
            return QuantityFood(
                name = it.name, foodId = it.foodId,
                calories = it.calories * k, proteinG = it.proteinG * k, carbsG = it.carbsG * k, fatG = it.fatG * k,
                micros = it.micros.mapValues { m -> m.value * k }, servings = servings, source = it.source, imageUrl = it.imageUrl,
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

/**
 * v2.5: how the Quantity sheet counts a food — "1 roti = 40 g" in whole steps, "1 katori = 150 g"
 * in ½ steps. [label] is set when the serving could not be split into single pieces ("5-6 pieces"),
 * in which case the stepper counts that serving as a whole.
 */
data class CountUnit(val noun: String, val grams: Double, val step: Double, val defaultCount: Double = 1.0, val label: String? = null) {
    /** "roti", or "× 5-6 pieces" for a serving that isn't a single piece. */
    val display: String get() = label?.let { "× $it" } ?: noun
    fun serving(): Serving = Serving(label ?: "1 $noun", grams)
    /** Rounds [n] to this unit's step, never below one step. */
    fun snap(n: Double): Double = (kotlin.math.round(n / step) * step).coerceAtLeast(step)
}

object Counting {
    /** "2 roti", "1-egg omelette", "½ katori", "1 katori (2 pcs)" → count + noun. The noun must start with a letter ("5-6 pieces" doesn't parse). */
    private val LEAD = Regex("^\\s*(\\d+(?:\\.\\d+)?|½|¼|¾)\\s*-?\\s*(\\p{L}.*?)\\s*$")
    private val WEIGHT = setOf("g", "gm", "gms", "gram", "grams", "kg", "ml", "l", "litre", "liter", "oz", "mg")
    /** Household measures you'd eat half of — everything else (roti, egg, idli, scoop, piece, slice …) counts in whole numbers. */
    private val HALVES = setOf(
        "katori", "katoris", "bowl", "bowls", "glass", "glasses", "cup", "cups", "plate", "plates", "tumbler", "ladle",
        "handful", "handfuls", "tub", "tbsp", "tsp", "serving", "servings", "pack", "packs", "bar", "coconut",
    )

    private fun num(s: String): Double? = when (s) { "½" -> 0.5; "¼" -> 0.25; "¾" -> 0.75; else -> s.toDoubleOrNull() }

    /** "slices" → "slice", "egg whites" → "egg white", "sandwiches" → "sandwich". Only the last word. */
    internal fun singular(noun: String): String {
        val words = noun.split(' ').toMutableList()
        val w = words.last()
        words[words.size - 1] = when {
            w.length > 4 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("sses")) -> w.dropLast(2)
            w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }
        return words.joinToString(" ")
    }

    private data class Parsed(val count: Double, val noun: String, val grams: Double, val label: String)

    private fun parse(s: Serving): Parsed? {
        val m = LEAD.find(s.label) ?: return null
        val n = num(m.groupValues[1]) ?: return null
        // "1 katori (2 pcs)" → "katori"; "1 pack (50 g)" → "pack".
        val noun = m.groupValues[2].replace(Regex("\\s*\\(.*?\\)"), "").trim().lowercase()
        if (noun.isEmpty() || n <= 0) return null
        if (noun.split(' ').first() in WEIGHT) return null
        return Parsed(n, if (n > 1) singular(noun) else noun, s.grams, s.label)
    }

    private fun stepFor(noun: String): Double = if (noun.split(' ').any { it in HALVES }) 0.5 else 1.0

    /**
     * The count unit for a food's servings, or null for a loose food (no servings, or only gram
     * weights like "100 g" / "200 g pack"). Prefers a single piece of the default serving's noun
     * ("1 roti" over "2 roti"), then the default split per piece ("2 idli = 80 g" → 40 g), then any
     * single piece. The default count is 1 — the preset's "2 roti" never decides it — except for
     * small things you count in handfuls ("10 almonds", "6 momos").
     */
    fun unitFor(servings: List<Serving>, defaultLabel: String? = null): CountUnit? {
        if (servings.isEmpty()) return null
        val def = servings.firstOrNull { it.label == defaultLabel } ?: servings.first()
        val parsed = servings.mapNotNull { parse(it) }
        val pd = parse(def)
        val one = parsed.firstOrNull { it.count == 1.0 && (pd == null || it.noun == pd.noun) }
        val base = when {
            one != null -> CountUnit(one.noun, one.grams, stepFor(one.noun))
            pd != null && pd.count >= 1 -> CountUnit(pd.noun, pd.grams / pd.count, stepFor(pd.noun))
            else -> parsed.firstOrNull { it.count == 1.0 }?.let { CountUnit(it.noun, it.grams, stepFor(it.noun)) }
        }
        if (base == null) {
            // Nothing splits into pieces: "5-6 pieces" counts as a whole serving, unless every label is a weight.
            if (parsed.isEmpty() && servings.all { s -> s.label.trim().split(Regex("\\s+")).getOrNull(1)?.lowercase() in WEIGHT }) return null
            return CountUnit(def.label.lowercase(), def.grams, 0.5, label = def.label)
        }
        val dc = if (pd != null && pd.noun == base.noun && pd.count >= 5) pd.count else 1.0
        return base.copy(grams = (base.grams * 10).roundToInt() / 10.0, defaultCount = dc)
    }

    fun unitFor(food: QuantityFood): CountUnit? = unitFor(food.servings, food.defaultServing)

    /** Whey and other supplements: counted in scoops, stepper only (no chips, no restaurant portion). */
    fun isSupplement(food: QuantityFood, unit: CountUnit?): Boolean =
        unit != null && unit.noun.startsWith("scoop") && (food.category == "protein" || food.category == null || Regex("whey|protein powder|creatine|mass gainer", RegexOption.IGNORE_CASE).containsMatchIn(food.name))
}
