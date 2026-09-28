package com.sohum.bandlog.util

import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.ScanHistoryItem
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * v2.17 "Recent" foods (Scan screen and Add food): the user's past logged foods and label / barcode
 * scans, deduped by name, newest first, up to [LIMIT]. Built from data the app already loads
 * (meals with their items, and scan history); no schema change.
 */
object Recents {
    const val LIMIT = 12

    data class Recent(
        /** Normalised name (the dedupe key). */
        val key: String,
        val name: String,
        /** Ready to log again at the last-used quantity (no row id). */
        val item: MealItem,
        val imageUrl: String?,
        /** label | barcode when it came from a scan; null for a logged food. */
        val scanKind: String?,
        val at: Long,
    ) {
        val kcal: Int get() = item.calories.roundToInt()
    }

    /** "Paneer tikka (scan)" and "paneer  tikka" are the same food. */
    fun key(name: String): String =
        name.trim().removeSuffix("(scan)").trim().lowercase().replace(Regex("""\s+"""), " ")

    fun build(meals: List<Meal>, scans: List<ScanHistoryItem>, limit: Int = LIMIT): List<Recent> {
        val out = ArrayList<Recent>()
        meals.forEach { m ->
            val at = epoch(m.createdAt) ?: dayEpoch(m.date)
            m.items.forEachIndexed { i, it ->
                if (it.name.isBlank() || it.grams <= 0 || Recipes.isRecipeItem(it.unit)) return@forEachIndexed
                // Items of one meal keep their order (first item newest by a hair).
                out += Recent(key(it.name), it.name.trim(), it.copy(id = null), it.imageUrl, null, at - i)
            }
        }
        scans.forEach { s -> scanItem(s)?.let { out += it } }
        return out.sortedByDescending { it.at }.distinctBy { it.key }.filter { it.key.isNotEmpty() }.take(limit)
    }

    /** A label / barcode scan with numbers as one serving (or 100 g); null for plates or scans without numbers. */
    fun scanItem(s: ScanHistoryItem): Recent? {
        if (s.isPlate || s.kind == "menu") return null
        val k100 = s.kcal100 ?: return null
        if (ScanHistoryItem.isJunkName(s.product)) return null
        val g = s.servingG?.takeIf { it > 0 } ?: 100.0
        val f = g / 100.0
        fun r1(d: Double) = (d * 10).roundToInt() / 10.0
        val name = s.displayName
        val item = MealItem(
            foodId = null, name = name, grams = r1(g), calories = r1(k100 * f),
            proteinG = r1((s.protein100 ?: 0.0) * f), carbsG = r1((s.carbs100 ?: 0.0) * f), fatG = r1((s.fat100 ?: 0.0) * f),
            source = "scan", confidence = 0.9,
            unit = if (s.servingG != null && s.servingG > 0) "serving" else "g",
            servings = if (s.servingG != null && s.servingG > 0) 1.0 else null,
            imageUrl = s.imageUrl, inputKind = if (s.kind == "barcode") "barcode" else "label",
        )
        return Recent(key(name), name, item, s.imageUrl, s.kind, epoch(s.createdAt) ?: 0L)
    }

    private fun epoch(ts: String?): Long? {
        val raw = ts?.trim()?.replace(' ', 'T')?.takeIf { it.isNotEmpty() } ?: return null
        val fixed = when {
            raw.endsWith("Z", ignoreCase = true) -> raw
            Regex("""[+-]\d\d:\d\d$""").containsMatchIn(raw) -> raw
            Regex("""[+-]\d\d$""").containsMatchIn(raw) -> "$raw:00"
            else -> raw + "Z"
        }
        return runCatching { OffsetDateTime.parse(fixed).toInstant().toEpochMilli() }.recoverCatching { Instant.parse(fixed).toEpochMilli() }.getOrNull()
    }

    private fun dayEpoch(date: String): Long = runCatching { java.time.LocalDate.parse(date).toEpochDay() * 86_400_000L }.getOrDefault(0L)
}
