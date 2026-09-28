package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Mirrors the web's src/lib/food/foodBits.ts (v2.18 Area A small pure rules; the web copy wins):
 *
 *   A5 portion reference  what the photo was sized against ("Sized using: katori").
 *   A6 leftovers          "Ate part of it": the eaten fraction and the rest (suggested for 3 days).
 *   A7 meal split         one dish, shares across people (equal by default).
 *   A8 label vs reality   a label whose numbers differ from the web by > 20 % (and a real amount).
 *   A9 swaps              one smart swap after logging, priced at the same grams.
 *   A10 water from food   dal, chaas, fruit… count toward water when the setting is on.
 *
 * Same cases as scripts/check-v218-food.ts ↔ FoodBitsTest.
 */
object FoodBits {
    private fun jsRound(v: Double) = floor(v + 0.5)
    private fun r1(v: Double) = jsRound(v * 10) / 10

    /** A meal item at [k] × its amount (grams, macros, micros, servings, stored range all scale). */
    fun scaleMealItem(item: MealItem, k: Double): MealItem {
        val f = max(0.0, k)
        return item.copy(
            id = null,
            grams = r1(item.grams * f),
            calories = jsRound(item.calories * f),
            proteinG = r1(item.proteinG * f),
            carbsG = r1(item.carbsG * f),
            fatG = r1(item.fatG * f),
            micros = item.micros.filterValues { it.isFinite() }.mapValues { r1(it.value * f) },
            servings = item.servings?.let { s -> if (s > 0) jsRound(s * f * 100) / 100 else s },
            kcalLow = item.kcalLow?.let { jsRound(it * f) },
            kcalHigh = item.kcalHigh?.let { jsRound(it * f) },
        )
    }

    // ---- A5 portion reference ----

    val SCALE_REFS = listOf("katori", "plate", "spoon", "hand", "roti", "glass", "cup", "bowl")

    /** The model's scale reference, cleaned: one of [SCALE_REFS] or null ("none", unknown, empty). */
    fun cleanScaleRef(v: String?): String? {
        val s = v.orEmpty().lowercase().trim()
        if (s.isEmpty() || s == "none") return null
        return when {
            Regex("katori|steel bowl").containsMatchIn(s) -> "katori"
            Regex("spoon|chammach|tbsp|tsp").containsMatchIn(s) -> "spoon"
            Regex("hand|palm|fist|finger").containsMatchIn(s) -> "hand"
            Regex("plate|thali").containsMatchIn(s) -> "plate"
            Regex("roti|chapati").containsMatchIn(s) -> "roti"
            Regex("glass|tumbler").containsMatchIn(s) -> "glass"
            Regex("cup|mug").containsMatchIn(s) -> "cup"
            Regex("bowl").containsMatchIn(s) -> "bowl"
            else -> null
        }
    }

    fun sizedUsingLabel(ref: String?): String? = cleanScaleRef(ref)?.let { "Sized using: $it" }

    // ---- A6 leftovers ----

    val LEFTOVER_FRACTIONS = listOf(1.0, 0.75, 0.5, 0.25)
    const val LEFTOVER_DAYS = 3

    fun fractionLabel(f: Double): String = when {
        f >= 0.999 -> "All of it"
        abs(f - 0.75) < 0.01 -> "¾"
        abs(f - 0.5) < 0.01 -> "½"
        abs(f - 0.25) < 0.01 -> "¼"
        abs(f - 1.0 / 3) < 0.01 -> "⅓"
        abs(f - 2.0 / 3) < 0.01 -> "⅔"
        else -> "${jsRound(f * 100).toLong()}%"
    }

    data class Eaten(val eaten: List<MealItem>, val left: List<MealItem>, val leftKcal: Double, val leftFraction: Double)

    /** Ate [eaten] (0 < eaten ≤ 1) of these items: what to log now and what's left over. */
    fun splitEaten(items: List<MealItem>, eaten: Double): Eaten {
        val e = min(1.0, max(0.05, eaten))
        val left = if (e >= 0.999) emptyList() else items.map { scaleMealItem(it, 1 - e) }
        return Eaten(
            eaten = if (e >= 0.999) items else items.map { scaleMealItem(it, e) },
            left = left,
            leftKcal = left.sumOf { it.calories },
            leftFraction = jsRound((1 - e) * 1000) / 1000,
        )
    }

    /** Epoch ms of an ISO timestamp / date, or null (the web's Date.parse). */
    internal fun parseTime(s: String?): Long? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        return runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t.replace(' ', 'T')).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(t).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    /** Still worth suggesting: not used, not dismissed, under 3 days old. */
    fun leftoverActive(createdAt: String, usedAt: String? = null, dismissedAt: String? = null, now: Long = System.currentTimeMillis()): Boolean {
        if (!usedAt.isNullOrEmpty() || !dismissedAt.isNullOrEmpty()) return false
        val t = parseTime(createdAt) ?: return false
        return now - t < LEFTOVER_DAYS * 86_400_000L && now >= t - 60_000
    }

    fun leftoverLine(name: String, kcal: Double, fractionLeft: Double): String =
        "${fractionLabel(fractionLeft)} of $name · ${jsRound(kcal).toLong()} kcal"

    // ---- A7 meal split ----

    /** Shares → fractions summing to 1 (non-positive shares drop to 0; all zero → equal). */
    fun normalizeShares(shares: List<Double>): List<Double> {
        val clean = shares.map { if (it.isFinite() && it > 0) it else 0.0 }
        val sum = clean.sum()
        if (!(sum > 0)) return shares.map { 1.0 / shares.size }
        return clean.map { it / sum }
    }

    /** Each person's items at their share (index 0 = the logger). */
    fun splitShares(items: List<MealItem>, shares: List<Double>): List<List<MealItem>> =
        normalizeShares(shares).map { f -> items.map { scaleMealItem(it, f) } }

    // ---- A8 label vs reality ----

    /** Per-100 g numbers; a null field is "not known" (skipped). */
    data class Per100(val calories: Double? = null, val proteinG: Double? = null, val carbsG: Double? = null, val fatG: Double? = null) {
        fun get(field: String): Double? = when (field) { "calories" -> calories; "protein_g" -> proteinG; "carbs_g" -> carbsG; else -> fatG }
    }

    data class RealityGap(val field: String, val label: Double, val web: Double, val pct: Int)

    private val MIN_ABS = mapOf("calories" to 25.0, "protein_g" to 2.0, "carbs_g" to 4.0, "fat_g" to 2.0)
    const val REALITY_THRESHOLD = 0.2

    /** Fields where the label and the web differ by more than 20 % (relative to the web) and a real amount. */
    fun labelRealityGaps(label: Per100, web: Per100): List<RealityGap> {
        val out = ArrayList<RealityGap>()
        for (f in listOf("calories", "protein_g", "carbs_g", "fat_g")) {
            val a = label.get(f) ?: continue
            val b = web.get(f) ?: continue
            if (!a.isFinite() || !b.isFinite() || a < 0 || b < 0) continue
            val base = maxOf(b, a, 1.0)
            val diff = abs(a - b)
            val pct = if (b > 0) diff / b else if (a > 0) 1.0 else 0.0
            if (pct > REALITY_THRESHOLD && diff >= MIN_ABS.getValue(f) && diff / base > 0.12) out.add(RealityGap(f, r1(a), r1(b), jsRound(pct * 100).toInt()))
        }
        return out
    }

    fun realityLine(g: RealityGap): String {
        val (w, u) = when (g.field) { "calories" -> "Calories" to "kcal"; "protein_g" -> "Protein" to "g"; "carbs_g" -> "Carbs" to "g"; else -> "Fat" to "g" }
        return "$w: label says ${num(g.label)} $u per 100 g, the web says ${num(g.web)} $u (${g.pct}% apart)"
    }

    // ---- A9 swaps ----

    class Swap(from: String, not: String? = null, val to: String, val fromPer100: Double, val toPer100: Double, val why: String) {
        val from = JsRe.re(from)
        val not = not?.let { JsRe.re(it) }
    }

    /** kcal per 100 g, home / typical Indian values. The swap keeps the same grams. */
    val SWAPS = listOf(
        Swap("\\b(butter|garlic|plain)?\\s*naan\\b", to = "tandoori roti", fromPer100 = 320.0, toPer100 = 260.0, why = "whole wheat, no butter"),
        Swap("\\bmaida\\b|\\brumali\\b", to = "wheat roti", fromPer100 = 330.0, toPer100 = 264.0, why = "more fibre, fewer kcal"),
        Swap("\\bpuri\\b|\\bpoori\\b", to = "roti", fromPer100 = 360.0, toPer100 = 264.0, why = "not deep-fried"),
        Swap("\\bbhatur", to = "tandoori roti", fromPer100 = 360.0, toPer100 = 260.0, why = "not deep-fried"),
        Swap("\\bparat?ha\\b", not = "stuffed|aloo|paneer|gobi|methi", to = "roti", fromPer100 = 310.0, toPer100 = 264.0, why = "no layers of ghee"),
        Swap("\\bfried rice\\b", to = "veg pulao", fromPer100 = 175.0, toPer100 = 147.0, why = "less oil"),
        Swap("\\bjeera rice\\b|\\bghee rice\\b", to = "plain rice", fromPer100 = 153.0, toPer100 = 130.0, why = "no tempering ghee"),
        Swap("\\bbiryani\\b", to = "pulao with raita", fromPer100 = 190.0, toPer100 = 150.0, why = "less ghee"),
        Swap("\\bdal makhani\\b", to = "dal tadka", fromPer100 = 187.0, toPer100 = 113.0, why = "no butter and cream"),
        Swap("\\b(paneer butter masala|butter paneer|shahi paneer|paneer makhani)\\b", to = "paneer tikka", fromPer100 = 233.0, toPer100 = 220.0, why = "no cream gravy"),
        Swap("\\b(butter chicken|murgh makhani|chicken makhani)\\b", to = "chicken tikka", fromPer100 = 220.0, toPer100 = 167.0, why = "same protein, no cream"),
        Swap("\\bmutton\\b", to = "chicken curry", fromPer100 = 200.0, toPer100 = 160.0, why = "leaner meat"),
        Swap("\\bsamosa\\b", to = "dhokla", fromPer100 = 300.0, toPer100 = 165.0, why = "steamed, not fried"),
        Swap("\\b(pakora|pakoda|bhajji|bhajiya|bhaji)\\b", not = "pav bhaji", to = "dhokla", fromPer100 = 300.0, toPer100 = 165.0, why = "steamed, not fried"),
        Swap("\\bmasala dosa\\b", to = "plain dosa with sambar", fromPer100 = 200.0, toPer100 = 150.0, why = "no potato masala"),
        Swap("\\b(sweet )?lassi\\b", not = "salt|namkeen", to = "chaas", fromPer100 = 80.0, toPer100 = 18.0, why = "no sugar"),
        Swap("\\b(cola|coke|pepsi|soft drink|cold drink|sprite|thums up|fanta)\\b", not = "diet|zero", to = "nimbu pani (no sugar)", fromPer100 = 42.0, toPer100 = 5.0, why = "no added sugar"),
        Swap("\\b(chips|wafers|kurkure)\\b", to = "roasted makhana", fromPer100 = 540.0, toPer100 = 350.0, why = "roasted, not fried"),
        Swap("\\b(fries|french fries)\\b", to = "roasted potato wedges", fromPer100 = 312.0, toPer100 = 150.0, why = "baked, not fried"),
        Swap("\\bgulab jamun\\b", to = "rasgulla", fromPer100 = 380.0, toPer100 = 186.0, why = "not fried in sugar syrup"),
        Swap("\\bice cream\\b", to = "curd with fruit", fromPer100 = 207.0, toPer100 = 90.0, why = "less sugar, more protein"),
        Swap("\\b(white sauce|alfredo|creamy) pasta\\b", to = "red sauce pasta", fromPer100 = 190.0, toPer100 = 150.0, why = "no cream"),
        Swap("\\bchai\\b|\\bmasala tea\\b", not = "no sugar|without sugar|sugar[- ]?free", to = "chai without sugar", fromPer100 = 53.0, toPer100 = 35.0, why = "no sugar"),
    )

    data class SwapHint(val itemName: String, val to: String, val delta: Int, val line: String, val why: String)

    /** The one swap with the biggest saving (≥ 25 kcal) on these items, or null. */
    fun pickSwap(items: List<MealItem>): SwapHint? = pickSwapFor(items.map { it.name to it.grams })

    /** [items] as (name, grams). */
    fun pickSwapFor(items: List<Pair<String, Double>>): SwapHint? {
        var best: SwapHint? = null
        for ((name, grams) in items) {
            if (!(grams > 0)) continue
            val s = SWAPS.find { x -> x.from.containsMatchIn(name) && !(x.not?.containsMatchIn(name) ?: false) && !name.lowercase().contains(x.to.lowercase()) } ?: continue
            val delta = jsRound(grams * (s.toPer100 - s.fromPer100) / 100).toInt()
            if (delta > -25) continue
            if (best == null || delta < best.delta) {
                val to = s.to.replaceFirstChar { it.uppercase() }
                best = SwapHint(name, to, delta, "$to instead of ${name.lowercase()}: −${abs(delta)} kcal", s.why)
            }
        }
        return best
    }

    // ---- A10 water from food ----

    private val WATER: List<Pair<Regex, Double>> = listOf(
        "coconut water|nariyal pani|tender coconut" to 0.95,
        "\\bchaas\\b|buttermilk|chhach|mattha|lassi" to 0.9,
        "\\bsoup\\b|\\brasam\\b" to 0.92,
        "\\bmilk\\b|\\bdoodh\\b" to 0.88,
        "\\b(chai|tea|coffee|kaapi)\\b" to 0.9,
        "juice|shake|smoothie|nimbu pani|lemonade|sharbat" to 0.85,
        "watermelon|tarbooz|cucumber|kheera|kakdi" to 0.93,
        "\\b(orange|mosambi|papaya|pineapple|grapes|strawberr|melon|musk)" to 0.87,
        "\\b(apple|pear|guava|pomegranate|anar|fruit)" to 0.84,
        "\\bsalad\\b" to 0.9,
        "\\b(curd|dahi|yogurt|raita)\\b" to 0.85,
        "\\b(dal|daal|sambar|sambhar|kadhi)\\b" to 0.8,
    ).map { (p, k) -> JsRe.re(p) to k }
    private val DRY = JsRe.re("powder|dry|mix\\b|premix|biscuit|chips")

    /** ml of water in one item (0 for anything not on the list, or a dry product). */
    fun waterMl(name: String, grams: Double): Int {
        if (DRY.containsMatchIn(name)) return 0
        val hit = WATER.find { it.first.containsMatchIn(name) } ?: return 0
        return jsRound((if (grams.isFinite()) grams else 0.0) * hit.second).toInt()
    }

    fun waterMl(item: MealItem): Int = waterMl(item.name, item.grams)

    /** ml of water in a day's meals (each meal a list of items). */
    fun waterFromMeals(meals: List<List<MealItem>>): Int = meals.sumOf { m -> m.sumOf { waterMl(it) } }

    /** Same, from (name, grams) pairs. */
    fun waterFromFoods(meals: List<List<Pair<String, Double>>>): Int = meals.sumOf { m -> m.sumOf { (n, g) -> waterMl(n, g) } }

    /** A JS number as JS prints it (30.0 → "30", 4.4 → "4.4"). */
    internal fun num(d: Double): String = if (d.isFinite() && d == floor(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()
}

/**
 * JS-compatible regexes. JS `\b` is ASCII-word based ([A-Za-z0-9_]); Java's and Android's (ICU) `\b`
 * are not the same (ICU is Unicode-aware), so every `\b` is rewritten to the exact JS boundary.
 */
internal object JsRe {
    private const val W = "[A-Za-z0-9_]"
    private const val B = "(?:(?<=$W)(?!$W)|(?<!$W)(?=$W))"

    fun re(pattern: String, ignoreCase: Boolean = true): Regex {
        val p = pattern.replace("\\b", B)
        return if (ignoreCase) Regex(p, RegexOption.IGNORE_CASE) else Regex(p)
    }
}
