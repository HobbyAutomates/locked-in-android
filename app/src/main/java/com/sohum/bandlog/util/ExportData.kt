package com.sohum.bandlog.util

/**
 * v2.18 E5 data export: CSV (meals with their items, workouts, activities, weights, water) with
 * the same columns as the web's src/lib/social/exportData.ts, shared via FileProvider, plus a PDF
 * summary (ui/social/ExportScreen.kt draws it with PdfDocument). Pure CSV building.
 */
object ExportData {
    data class Csv(val name: String, val header: List<String>, val rows: List<List<Any?>>)

    val KINDS = listOf("meals", "workouts", "activities", "weights", "water")
    val HEADERS = mapOf(
        "meals" to listOf("date", "meal", "item", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "meal_text"),
        "workouts" to listOf("date", "kind", "minutes", "muscles", "exercises", "notes"),
        "activities" to listOf("date", "name", "minutes", "intensity", "kcal", "steps", "distance_km", "source"),
        "weights" to listOf("date", "weight_kg", "note"),
        "water" to listOf("date", "ml", "vessel"),
    )
    const val BOM = "﻿"

    private val FORMULA = Regex("^[=+\\-@\\t\\r]")
    private val NUMERIC = Regex("^-?\\d")
    private val NEEDS_QUOTES = Regex("[\",\\r\\n]")

    private fun cell(v: Any?): String {
        if (v == null) return ""
        val s = when (v) {
            is Double -> if (v.isFinite()) Wrapped.num(Math.round(v * 100) / 100.0) else ""
            is Float -> if (v.isFinite()) Wrapped.num(Math.round(v * 100.0) / 100.0) else ""
            is Number -> v.toString()
            else -> v.toString()
        }
        val safe = if (FORMULA.containsMatchIn(s) && !NUMERIC.containsMatchIn(s)) "'$s" else s
        return if (NEEDS_QUOTES.containsMatchIn(safe)) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** RFC 4180-ish: quote when needed, double the quotes; CRLF line ends; a BOM so Excel reads UTF-8. */
    fun toCsv(header: List<String>, rows: List<List<Any?>>, bom: Boolean = true): String {
        val lines = listOf(header.joinToString(",") { cell(it) }) + rows.map { r -> r.joinToString(",") { cell(it) } }
        return (if (bom) BOM else "") + lines.joinToString("\r\n") + "\r\n"
    }

    data class Item(val name: String?, val grams: Double?, val calories: Double?, val proteinG: Double?, val carbsG: Double?, val fatG: Double?)
    data class MealIn(val date: String, val rawText: String?, val mealType: String?, val items: List<Item>)

    fun mealRows(meals: List<MealIn>): List<List<Any?>> = meals.flatMap { m ->
        val items = m.items.ifEmpty { listOf(Item(null, null, null, null, null, null)) }
        items.map { it -> listOf(m.date, m.mealType ?: "", it.name ?: "", it.grams, it.calories, it.proteinG, it.carbsG, it.fatG, m.rawText ?: "") }
    }

    fun filename(kind: String, today: String, ext: String = "csv") = "locked-in-$kind-$today.$ext"

    /** One CSV with every section stacked under "# section" lines. */
    fun combinedCsv(parts: List<Csv>): String = BOM + parts.joinToString("\r\n") { p -> "# ${p.name}\r\n${toCsv(p.header, p.rows, false)}" }
}
