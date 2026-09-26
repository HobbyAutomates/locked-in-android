package com.sohum.bandlog.data

import com.sohum.bandlog.util.Recipes
import org.json.JSONArray
import org.json.JSONObject

/*
 * v2.13 nutrition rows (schema_v36). Kept out of Models.kt so the platform half can edit that file
 * freely; every parser tolerates missing keys (the columns / tables may not exist yet).
 */

private fun JSONObject.dblOrNull(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
private fun JSONObject.strOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
private fun JSONObject.intOrNull(k: String): Int? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }?.let { Math.round(it).toInt() }

/**
 * The v36 profile settings. [supported] is false while schema_v36 isn't applied (the columns are
 * missing): the screens then show "Coming with the next update" instead of the controls.
 */
data class NutritionSettings(
    val supported: Boolean = false,
    val dietMode: String = "balanced",
    val adaptiveTargets: Boolean = false,
    val proteinNudge: Boolean = true,
    /** "HH:mm". */
    val proteinNudgeTime: String = "16:00",
    /** The default fasting length (hours); null = never set. */
    val fastingHours: Double? = null,
) {
    companion object {
        const val COLS = "diet_mode,adaptive_targets,protein_nudge,protein_nudge_time,fasting_hours"

        fun from(o: JSONObject): NutritionSettings = NutritionSettings(
            supported = o.has("diet_mode"),
            dietMode = o.strOrNull("diet_mode") ?: "balanced",
            adaptiveTargets = o.has("adaptive_targets") && !o.isNull("adaptive_targets") && o.optBoolean("adaptive_targets", false),
            proteinNudge = !o.has("protein_nudge") || o.isNull("protein_nudge") || o.optBoolean("protein_nudge", true),
            proteinNudgeTime = o.strOrNull("protein_nudge_time")?.take(5) ?: "16:00",
            fastingHours = o.dblOrNull("fasting_hours"),
        )
    }
}

/** One row of `bandlog.weekly_checkins`. */
data class WeeklyCheckin(
    val id: String?,
    val weekStart: String,
    val avgWeightKg: Double?,
    val trendKgPerWeek: Double?,
    val avgKcal: Int?,
    val oldTarget: Int?,
    val newTarget: Int?,
    val reason: String,
    val applied: Boolean,
) {
    fun toJson(userId: String): JSONObject = JSONObject()
        .put("user_id", userId).put("week_start", weekStart)
        .put("avg_weight_kg", avgWeightKg?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL)
        .put("trend_kg_per_week", trendKgPerWeek?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL)
        .put("avg_kcal", avgKcal ?: JSONObject.NULL)
        .put("old_target", oldTarget ?: JSONObject.NULL)
        .put("new_target", newTarget ?: JSONObject.NULL)
        .put("reason", reason)
        .put("applied", applied)

    companion object {
        fun from(o: JSONObject) = WeeklyCheckin(
            id = o.strOrNull("id"),
            weekStart = o.optString("week_start").take(10),
            avgWeightKg = o.dblOrNull("avg_weight_kg"),
            trendKgPerWeek = o.dblOrNull("trend_kg_per_week"),
            avgKcal = o.intOrNull("avg_kcal"),
            oldTarget = o.intOrNull("old_target"),
            newTarget = o.intOrNull("new_target"),
            reason = o.optString("reason"),
            applied = o.optBoolean("applied", false),
        )
    }
}

/** One row of `bandlog.fasting_sessions`. Times are epoch millis on the client. */
data class FastingSession(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val targetHours: Double,
    val note: String = "",
) {
    val running: Boolean get() = endedAtMs == null
    fun reachedGoal(now: Long = System.currentTimeMillis()): Boolean = ((endedAtMs ?: now) - startedAtMs) >= targetHours * 3_600_000

    companion object {
        fun parseTime(s: String?): Long? = s?.let { raw ->
            runCatching { java.time.OffsetDateTime.parse(raw.replace(" ", "T").let { x -> if (Regex("[+-]\\d\\d$").containsMatchIn(x)) "$x:00" else x }).toInstant().toEpochMilli() }
                .recoverCatching { java.time.Instant.parse(raw).toEpochMilli() }
                .getOrNull()
        }

        fun iso(ms: Long): String = java.time.Instant.ofEpochMilli(ms).toString()

        fun from(o: JSONObject): FastingSession? {
            val start = parseTime(o.strOrNull("started_at")) ?: return null
            return FastingSession(
                id = o.optString("id"),
                startedAtMs = start,
                endedAtMs = parseTime(o.strOrNull("ended_at")),
                targetHours = o.dblOrNull("target_hours") ?: 16.0,
                note = o.strOrNull("note").orEmpty(),
            )
        }
    }
}

/** One row of `bandlog.recipes`: items [{name, grams, kcal, protein_g, carbs_g, fat_g, fiber_g, food_id?, micros?}]. */
data class Recipe(
    val id: String?,
    val name: String,
    val servings: Double,
    val cookedWeightG: Double?,
    val items: List<Recipes.Ingredient>,
    val note: String = "",
    val updatedAt: String = "",
) {
    val perServing: Recipes.Totals get() = Recipes.perServing(items, servings, cookedWeightG)

    fun toJson(userId: String): JSONObject {
        val ps = perServing
        return JSONObject()
            .put("user_id", userId).put("name", name).put("servings", servings)
            .put("cooked_weight_g", cookedWeightG ?: JSONObject.NULL)
            .put("items", JSONArray().apply {
                items.forEach { i ->
                    put(
                        JSONObject().put("name", i.name).put("grams", i.grams).put("kcal", i.kcal).put("protein_g", i.protein)
                            .put("carbs_g", i.carbs).put("fat_g", i.fat).put("fiber_g", i.fiber)
                            .put("food_id", i.foodId ?: JSONObject.NULL).put("micros", JSONObject(i.micros)),
                    )
                }
            })
            .put("per_serving", JSONObject().put("grams", ps.grams).put("kcal", ps.kcal).put("protein_g", ps.protein).put("carbs_g", ps.carbs)
                .put("fat_g", ps.fat).put("fiber_g", ps.fiber).put("micros", JSONObject(ps.micros)))
            .put("note", note.ifBlank { JSONObject.NULL })
    }

    /** One serving as a meal row, named after the recipe (source "table": the DB only allows table / estimated / scan). */
    fun servingItem(count: Double = 1.0): MealItem {
        val ps = perServing
        return MealItem(
            foodId = null, name = name, grams = Math.round(ps.grams * count * 10) / 10.0,
            calories = ps.kcal * count, proteinG = ps.protein * count, carbsG = ps.carbs * count, fatG = ps.fat * count,
            source = "table", confidence = 1.0,
            micros = (ps.micros + (if (ps.fiber > 0) mapOf("fiber_g" to ps.fiber) else emptyMap())).mapValues { it.value * count },
            unit = "serving", servings = count,
            sourceInfo = SourceInfo("recipe", "Your recipe", "Worked out from the ingredients you added, divided by ${com.sohum.bandlog.ui.today.fmt(servings)} servings."),
        )
    }

    companion object {
        fun from(o: JSONObject): Recipe {
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i ->
                val x = arr.optJSONObject(i) ?: return@mapNotNull null
                Recipes.Ingredient(
                    name = x.optString("name"), grams = x.optDouble("grams", 0.0), kcal = x.optDouble("kcal", x.optDouble("calories", 0.0)),
                    protein = x.optDouble("protein_g", 0.0), carbs = x.optDouble("carbs_g", 0.0), fat = x.optDouble("fat_g", 0.0),
                    fiber = x.optDouble("fiber_g", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                    foodId = x.strOrNull("food_id"), micros = MealItem.micros(x.optJSONObject("micros")),
                )
            }
            return Recipe(
                id = o.strOrNull("id"), name = o.optString("name"), servings = o.dblOrNull("servings") ?: 1.0,
                cookedWeightG = o.dblOrNull("cooked_weight_g"), items = items, note = o.strOrNull("note").orEmpty(),
                updatedAt = o.optString("updated_at"),
            )
        }
    }
}

/** One dish from `/api/scan-menu`. Ranges are per portion; either end may be missing on an older server. */
data class MenuDish(
    val name: String,
    val portion: String,
    val kcalLow: Double,
    val kcalHigh: Double,
    val proteinLow: Double,
    val proteinHigh: Double,
    val carbsG: Double?,
    val fatG: Double?,
    /** high | medium | low */
    val confidence: String,
    val bestPick: Boolean,
    val why: String,
    val grams: Double?,
) {
    val kcalMid: Double get() = (kcalLow + kcalHigh) / 2
    val proteinMid: Double get() = (proteinLow + proteinHigh) / 2

    /** The dish as one meal row at the middle of its ranges (an AI estimate). */
    fun toMealItem(): MealItem {
        val kcal = kcalMid
        val p = proteinMid
        val c = carbsG ?: ((kcal - p * 4 - (fatG ?: kcal * 0.3 / 9) * 9) / 4).coerceAtLeast(0.0)
        val f = fatG ?: (kcal * 0.3 / 9)
        return MealItem(
            foodId = null, name = name, grams = grams ?: 0.0, calories = Math.round(kcal).toDouble(), proteinG = Math.round(p * 10) / 10.0,
            carbsG = Math.round(c * 10) / 10.0, fatG = Math.round(f * 10) / 10.0, source = "estimated",
            confidence = when (confidence) { "high" -> 0.9; "medium" -> 0.6; else -> 0.3 },
            sourceInfo = SourceInfo("estimate", "AI estimate from a menu", "Estimated by the AI from the dish name on the menu and a typical restaurant portion."),
        )
    }

    companion object {
        /** A range from "kcal_low"/"kcal_high", "kcal_min"/"kcal_max", a [lo, hi] array, or one number. */
        private fun range(o: JSONObject, vararg bases: String): Pair<Double, Double>? {
            for (b in bases) {
                val lo = o.dblOrNull("${b}_low") ?: o.dblOrNull("${b}_min")
                val hi = o.dblOrNull("${b}_high") ?: o.dblOrNull("${b}_max")
                if (lo != null && hi != null) return minOf(lo, hi) to maxOf(lo, hi)
                o.optJSONArray("${b}_range")?.let { a -> if (a.length() >= 2) return minOf(a.optDouble(0), a.optDouble(1)) to maxOf(a.optDouble(0), a.optDouble(1)) }
                o.optJSONObject("${b}_range")?.let { r -> val l = r.dblOrNull("low") ?: r.dblOrNull("min"); val h = r.dblOrNull("high") ?: r.dblOrNull("max"); if (l != null && h != null) return l to h }
                val one = o.dblOrNull(b) ?: lo ?: hi
                if (one != null) return one to one
            }
            return null
        }

        fun from(o: JSONObject): MenuDish? {
            val name = (o.strOrNull("name") ?: o.strOrNull("dish"))?.trim() ?: return null
            val kcal = range(o, "kcal", "calories") ?: return null
            val protein = range(o, "protein", "protein_g") ?: (0.0 to 0.0)
            val conf = when (val c = o.opt("confidence")) {
                is Number -> if (c.toDouble() >= 0.8) "high" else if (c.toDouble() >= 0.5) "medium" else "low"
                is String -> c.lowercase().takeIf { it in setOf("high", "medium", "low") } ?: "medium"
                else -> "medium"
            }
            return MenuDish(
                name = name,
                portion = o.strOrNull("portion") ?: o.strOrNull("serving") ?: "1 portion",
                kcalLow = kcal.first, kcalHigh = kcal.second,
                proteinLow = protein.first, proteinHigh = protein.second,
                carbsG = o.dblOrNull("carbs_g") ?: o.dblOrNull("carbs"),
                fatG = o.dblOrNull("fat_g") ?: o.dblOrNull("fat"),
                confidence = conf,
                bestPick = o.optBoolean("best_pick", o.optBoolean("best", false)),
                why = o.strOrNull("why") ?: o.strOrNull("reason") ?: o.strOrNull("note").orEmpty(),
                grams = o.dblOrNull("grams"),
            )
        }
    }
}

/** The `/api/scan-menu` result. */
data class MenuScan(val id: String?, val dishes: List<MenuDish>, val note: String, val restaurant: String?) {
    companion object {
        fun from(o: JSONObject): MenuScan {
            val arr = o.optJSONArray("dishes") ?: o.optJSONArray("items") ?: JSONArray()
            val dishes = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { d -> MenuDish.from(d) } }
            // No best pick flagged by the server: nothing is marked (the UI never invents one).
            return MenuScan(o.strOrNull("id") ?: o.strOrNull("scan_id"), dishes, o.strOrNull("note") ?: o.strOrNull("summary").orEmpty(), o.strOrNull("restaurant"))
        }
    }
}
