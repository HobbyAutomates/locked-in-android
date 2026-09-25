package com.sohum.bandlog.util

import com.sohum.bandlog.data.Meal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * v2.8 meal types (bandlog.meals.meal_type, the web's supabase/schema_v30.sql). Mirrors the web's
 * lib/mealType.ts: the same hour rule, the same null / missing-column fallback, the same sections.
 */
object MealTypes {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    data class Type(val key: String, val label: String, val emoji: String)

    /** Home's section order: Breakfast · Lunch · Dinner · Snacks. */
    val ALL = listOf(
        Type(BREAKFAST, "Breakfast", "🍳"),
        Type(LUNCH, "Lunch", "🍛"),
        Type(DINNER, "Dinner", "🌙"),
        Type(SNACK, "Snacks", "🍿"),
    )

    fun isType(s: String?): Boolean = s == BREAKFAST || s == LUNCH || s == DINNER || s == SNACK
    fun label(key: String): String = ALL.firstOrNull { it.key == key }?.label ?: "Meal"

    /** The hour rule (India time): 04–10:59 breakfast, 11–15:59 lunch, 16–18:59 snack, 19–03:59 dinner. */
    fun forHour(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return when (h) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..18 -> SNACK
            else -> DINNER
        }
    }

    /** The default type for a meal being logged now. */
    fun default(now: ZonedDateTime = ZonedDateTime.now(Dates.ZONE)): String = forHour(now.withZoneSameInstant(Dates.ZONE).hour)

    /** The India-time hour of a Postgres timestamptz ("2026-09-24T08:15:00.1+00:00", "… +00", "…Z"), or null. */
    fun hourOf(createdAt: String?): Int? {
        if (createdAt.isNullOrBlank()) return null
        val raw = createdAt.trim().replace(" ", "T")
        val fixed = when {
            raw.endsWith("Z", ignoreCase = true) -> raw
            Regex("[+-]\\d\\d:\\d\\d$").containsMatchIn(raw) -> raw
            Regex("[+-]\\d\\d\\d\\d$").containsMatchIn(raw) -> raw.dropLast(2) + ":" + raw.takeLast(2)
            Regex("[+-]\\d\\d$").containsMatchIn(raw) -> "$raw:00"
            else -> raw + "Z"
        }
        return runCatching { OffsetDateTime.parse(fixed).atZoneSameInstant(Dates.ZONE).hour }
            .recoverCatching { Instant.parse(fixed).atZone(Dates.ZONE).hour }
            .getOrNull()
    }

    /** A saved meal's type: its column when set, else the hour rule on when it was logged (column missing / null). */
    fun of(meal: Meal): String = meal.mealType?.takeIf { isType(it) } ?: forHour(hourOf(meal.createdAt) ?: 12)

    /**
     * Whether a meal came from the AI (a parsed sentence, a plate photo or a scan) — only those ask
     * "AI right?" in the editor. Preset / search taps are table rows at confidence 1. Mirrors the web's aiLogged.
     */
    fun aiLogged(meal: Meal): Boolean =
        meal.photoPath != null || meal.items.any { it.source == "estimated" || it.source == "scan" || it.confidence == null || it.confidence < 1.0 }

    data class Section(val type: Type, val meals: List<Meal>, val kcal: Int, val protein: Double)

    /** A day's meals in the four sections, Home's order; each section's meals oldest first. */
    fun group(meals: List<Meal>): List<Section> = ALL.map { t ->
        val list = meals.filter { of(it) == t.key }.sortedBy { it.createdAt }
        val items = list.flatMap { it.items }
        Section(t, list, items.sumOf { it.calories }.let { kotlin.math.round(it).toInt() }, kotlin.math.round(items.sumOf { it.proteinG } * 10) / 10.0)
    }

    /** A PostgREST / Postgres error meaning "no meal_type column yet" (schema_v30 not applied). */
    fun missingColumn(message: String?): Boolean {
        val m = message ?: return false
        return m.contains("meal_type", ignoreCase = true) && (m.contains("42703") || m.contains("PGRST204") || m.contains("column", ignoreCase = true) || m.contains("schema cache", ignoreCase = true))
    }
}
