package com.sohum.bandlog.util

import com.sohum.bandlog.data.FoodHit
import com.sohum.bandlog.data.FoodVariant
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.PlateItem
import com.sohum.bandlog.data.SourceInfo
import com.sohum.bandlog.data.SourceLink
import kotlin.math.roundToInt

/**
 * v2.9 "Where's this from?" + "Which one?" — the Android twin of src/lib/sourceInfo.ts and the
 * client half of src/lib/variants.ts. Variant DETECTION happens on the server (parse-meal, the
 * plate scan, /api/food-source); this file only labels, formats and swaps.
 */
object Sources {
    private const val OIL_KCAL_PER_G = 8.84

    fun offUrl(barcode: String) = "https://world.openfoodfacts.org/product/$barcode"

    /** A local label for a row that arrived without `source_info` and has no food row (an estimate or a scan). */
    fun fallback(item: MealItem): SourceInfo? = when {
        item.source == "scan" -> SourceInfo("label", "Nutrition label", "Read off the pack's nutrition table, then sanity-checked.")
        item.foodId == null || item.source == "estimated" -> SourceInfo("estimate", "AI estimate", "Estimated by the AI from a typical recipe — no food-table row matched.")
        else -> null
    }

    /** Label / barcode report → provenance, when the server didn't send one (older web builds). */
    fun forReport(nutritionSource: String?, barcode: String?): SourceInfo {
        val links = barcode?.filter { it.isDigit() }?.takeIf { it.length >= 8 }?.let { listOf(SourceLink("Open Food Facts product page", offUrl(it))) } ?: emptyList()
        return when (nutritionSource) {
            "openfoodfacts" -> SourceInfo("off", "Open Food Facts", "Numbers from the product's Open Food Facts record, sanity-checked.", links)
            "label" -> SourceInfo("label", "Nutrition label", "Numbers read off the pack's nutrition table, sanity-checked.", links)
            else -> SourceInfo("estimate", "No trusted numbers", "The numbers couldn't be read or didn't check out — scan the nutrition table.")
        }
    }

    /** The label for a food-table row we know the dataset of (a chip or search pick). */
    fun forRow(source: String?, name: String): SourceInfo = when (source) {
        "ifct" -> SourceInfo("ifct", "IFCT 2017 (ICMR-NIN)", "Indian Food Composition Tables 2017, National Institute of Nutrition, Hyderabad.", listOf(SourceLink("IFCT 2017 reference (NIN)", "https://www.ifct2017.com/")))
        "usda", "usda_foundation" -> SourceInfo("usda", "USDA FoodData Central", "USDA FoodData Central (SR Legacy / Foundation Foods).", listOf(SourceLink("Search FoodData Central", "https://fdc.nal.usda.gov/food-search?query=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"))))
        "off" -> SourceInfo("off", "Open Food Facts", "A packaged product's record on Open Food Facts.")
        "dish" -> SourceInfo("dish", "Locked In dish recipe", "Worked out from a standard recipe in the Indian Nutrient Databank (INDB), per 100 g cooked.", listOf(SourceLink("Indian Nutrient Databank (INDB)", "https://doi.org/10.1016/j.cdnut.2024.103790")))
        "ai" -> SourceInfo("ai", "AI estimate", "Looked up by the AI when the food table had no match, then sanity-checked.")
        else -> SourceInfo("custom", "Locked In food list", "Hand-checked everyday values, per 100 g as usually eaten.")
    }

    /** "High" / "Medium" / "Low" from a 0–1 score, or the plate's own word. */
    fun confidenceLabel(c: Double?, source: String?): String = when {
        c == null || c.isNaN() -> if (source == "estimated") "Low" else "High"
        c >= 0.8 -> "High"
        c >= 0.5 -> "Medium"
        else -> "Low"
    }
    fun confidenceLabel(word: String): String = when (word) { "high" -> "High"; "low" -> "Low"; else -> "Medium" }

    /** Low confidence or a "Which one?" choice → the ⓘ reads "Check". */
    fun needsCheck(item: MealItem): Boolean = item.variants.size > 1 || confidenceLabel(item.confidence, item.source) == "Low"
    fun needsCheck(item: PlateItem): Boolean = item.variants.size > 1 || item.confidence == "low"

    private fun r1(d: Double) = (d * 10).roundToInt() / 10.0
    private fun num(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    /** "Numbers per 100 g: 297 kcal · P 9.8 · C 46 · F 7.5". */
    fun per100Note(grams: Double, kcal: Double, p: Double, c: Double, f: Double): String {
        if (grams <= 0) return ""
        val k = 100 / grams
        return "Numbers per 100 g: ${(kcal * k).roundToInt()} kcal · P ${num(r1(p * k))} · C ${num(r1(c * k))} · F ${num(r1(f * k))}"
    }

    fun variantOf(h: FoodHit) = FoodVariant(h.id, h.name, h.calories, h.proteinG, h.carbsG, h.fatG, h.micros, h.source)

    /** The same row swapped to [v] at the SAME grams (a restaurant portion keeps its hidden oil). */
    fun swap(item: MealItem, v: FoodVariant, keepChips: Boolean = true): MealItem {
        val k = item.grams / 100
        val oil = if (item.cookedIn == "restaurant" && Restaurant.oily(v.name, null)) Restaurant.OIL_G else 0.0
        val restaurant = item.cookedIn == "restaurant" && item.name.endsWith("(restaurant)")
        return item.copy(
            name = if (restaurant) "${v.name} (restaurant)" else v.name,
            foodId = v.foodId, source = "table", confidence = 1.0,
            calories = (v.kcalPer100g * k + oil * OIL_KCAL_PER_G).roundToInt().toDouble(),
            proteinG = r1(v.proteinPer100g * k), carbsG = r1(v.carbsPer100g * k), fatG = r1(v.fatPer100g * k + oil),
            micros = v.micros.mapValues { r1(it.value * k) },
            imageUrl = null,
            variants = if (keepChips) item.variants else emptyList(),
            sourceInfo = forRow(v.source, v.name),
        )
    }

    fun swap(item: PlateItem, v: FoodVariant): PlateItem {
        val k = item.grams / 100
        val oil = if (item.cookedIn == "restaurant" && Restaurant.oily(v.name, null)) Restaurant.OIL_G else 0.0
        return item.copy(
            name = v.name, foodId = v.foodId, source = "table", confidence = "high",
            calories = (v.kcalPer100g * k + oil * OIL_KCAL_PER_G).roundToInt().toDouble(),
            proteinG = r1(v.proteinPer100g * k), carbsG = r1(v.carbsPer100g * k), fatG = r1(v.fatPer100g * k + oil),
            micros = v.micros.mapValues { r1(it.value * k) },
            sourceInfo = forRow(v.source, v.name),
        )
    }
}
