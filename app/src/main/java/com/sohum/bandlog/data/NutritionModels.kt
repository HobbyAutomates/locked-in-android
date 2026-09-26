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

/**
 * One row of `bandlog.recipes` (the web's src/lib/recipes.ts Recipe): items
 * [{name, grams, kcal, protein_g, carbs_g, fat_g, fiber_g, food_id, micros, per100?}] and per_serving.
 */
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
                    val o = JSONObject().put("name", i.name).put("grams", i.grams).put("kcal", i.kcal).put("protein_g", i.protein)
                        .put("carbs_g", i.carbs).put("fat_g", i.fat).put("fiber_g", i.fiber ?: 0.0)
                        .put("food_id", i.foodId ?: JSONObject.NULL).put("micros", JSONObject(i.micros))
                    i.per100?.let { p -> o.put("per100", JSONObject().put("kcal", p.kcal).put("protein_g", p.protein).put("carbs_g", p.carbs).put("fat_g", p.fat).put("micros", JSONObject(p.micros))) }
                    put(o)
                }
            })
            .put("per_serving", JSONObject().put("kcal", ps.kcal).put("protein_g", ps.protein).put("carbs_g", ps.carbs)
                .put("fat_g", ps.fat).put("fiber_g", ps.fiber).put("grams", ps.grams).put("micros", JSONObject(ps.micros)))
            .put("note", note.ifBlank { JSONObject.NULL })
    }

    /** "Log a serving": [count] servings as one meal item named after the recipe (unit "recipe"). */
    fun servingItem(count: Double = 1.0): MealItem = Recipes.mealItem(name, perServing, count).copy(
        sourceInfo = SourceInfo("recipe", "Your recipe", "Worked out from the ingredients you added, divided by ${com.sohum.bandlog.ui.today.fmt(servings)} servings."),
    )

    companion object {
        fun from(o: JSONObject): Recipe {
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i ->
                val x = arr.optJSONObject(i) ?: return@mapNotNull null
                val p = x.optJSONObject("per100")?.let { pp ->
                    Recipes.Per100(pp.optDouble("kcal", 0.0), pp.optDouble("protein_g", 0.0), pp.optDouble("carbs_g", 0.0), pp.optDouble("fat_g", 0.0), MealItem.micros(pp.optJSONObject("micros")))
                }
                Recipes.Ingredient(
                    name = x.optString("name"), grams = x.optDouble("grams", 0.0), kcal = x.optDouble("kcal", x.optDouble("calories", 0.0)),
                    protein = x.optDouble("protein_g", 0.0), carbs = x.optDouble("carbs_g", 0.0), fat = x.optDouble("fat_g", 0.0),
                    fiber = x.dblOrNull("fiber_g"), foodId = x.strOrNull("food_id"), micros = MealItem.micros(x.optJSONObject("micros")), per100 = p,
                )
            }
            return Recipe(
                id = o.strOrNull("id"), name = o.optString("name").ifBlank { "Recipe" }, servings = (o.dblOrNull("servings") ?: 1.0).takeIf { it > 0 } ?: 1.0,
                cookedWeightG = o.dblOrNull("cooked_weight_g")?.takeIf { it > 0 }, items = items, note = o.strOrNull("note").orEmpty(),
                updatedAt = o.optString("updated_at"),
            )
        }
    }
}

/**
 * One dish from `POST /api/scan-menu` (the web's src/lib/menuScan.ts MenuDish): ranges per portion,
 * the server's diet-mode check and best pick.
 */
data class MenuDish(
    val name: String,
    val description: String = "",
    val section: String? = null,
    /** "1 plate (about 350 g)". */
    val portion: String = "1 serving",
    val grams: Double = 0.0,
    val kcalLow: Double,
    val kcalHigh: Double,
    val proteinLow: Double,
    val proteinHigh: Double,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    /** high | medium | low */
    val confidence: String = "medium",
    /** One line on how sure the estimate is and why. */
    val why: String = "",
    val veg: Boolean? = null,
    val price: String? = null,
    val fitsDiet: Boolean = true,
    /** Why it doesn't fit the diet mode ("meat", "egg"…). */
    val dietConflicts: List<String> = emptyList(),
    val score: Double = 0.0,
    val bestPick: Boolean = false,
) {
    private fun jsRound(v: Double) = kotlin.math.floor(v + 0.5)
    val midKcal: Double get() = jsRound((kcalLow + kcalHigh) / 2)
    val midProtein: Double get() = jsRound((proteinLow + proteinHigh) / 2 * 10) / 10

    /** A tapped dish as a meal item: the middle of each range, an AI estimate (restaurant portion). Same maths as the web's dishMealItem. */
    fun toMealItem(): MealItem {
        val kcal = midKcal
        val protein = midProtein
        // Carbs and fat scaled so the macros agree with the mid calories when the model's pair is off.
        val macroKcal = protein * 4 + carbsG * 4 + fatG * 9
        val k = if (macroKcal > 0) maxOf(0.0, kcal - protein * 4) / maxOf(1.0, carbsG * 4 + fatG * 9) else 0.0
        return MealItem(
            foodId = null, name = name, grams = if (grams > 0) grams else maxOf(100.0, jsRound(kcal / 1.8)),
            calories = kcal, proteinG = protein, carbsG = jsRound(carbsG * k * 10) / 10, fatG = jsRound(fatG * k * 10) / 10,
            source = "estimated", confidence = when (confidence) { "high" -> 0.85; "low" -> 0.35; else -> 0.6 },
            unit = "g", servings = null, cookedIn = "restaurant",
        )
    }

    companion object {
        fun from(o: JSONObject): MenuDish? {
            val name = o.strOrNull("name")?.trim() ?: return null
            val kh = o.dblOrNull("kcal_high") ?: o.dblOrNull("kcal") ?: return null
            val kl = o.dblOrNull("kcal_low") ?: kh
            val ph = o.dblOrNull("protein_high") ?: o.dblOrNull("protein_g") ?: 0.0
            val pl = o.dblOrNull("protein_low") ?: ph
            val conflicts = o.optJSONArray("diet_conflicts")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            return MenuDish(
                name = name, description = o.strOrNull("description").orEmpty(), section = o.strOrNull("section"),
                portion = o.strOrNull("portion") ?: "1 serving", grams = o.dblOrNull("grams") ?: 0.0,
                kcalLow = minOf(kl, kh), kcalHigh = maxOf(kl, kh), proteinLow = minOf(pl, ph), proteinHigh = maxOf(pl, ph),
                carbsG = o.dblOrNull("carbs_g") ?: 0.0, fatG = o.dblOrNull("fat_g") ?: 0.0,
                confidence = o.strOrNull("confidence")?.lowercase()?.takeIf { it == "high" || it == "low" || it == "medium" } ?: "medium",
                why = o.strOrNull("why").orEmpty(),
                veg = if (!o.has("veg") || o.isNull("veg")) null else o.optBoolean("veg"),
                price = o.strOrNull("price"),
                fitsDiet = !o.has("fits_diet") || o.optBoolean("fits_diet", true),
                dietConflicts = conflicts,
                score = o.dblOrNull("score") ?: 0.0,
                bestPick = o.optBoolean("best_pick", false),
            )
        }
    }
}

/** The `/api/scan-menu` result (kind "menu"); [id] is null until schema_v36 lets the server save it. */
data class MenuScan(val id: String?, val dishes: List<MenuDish>, val note: String, val restaurant: String?, val dietMode: String?) {
    companion object {
        fun from(o: JSONObject): MenuScan {
            val arr = o.optJSONArray("dishes") ?: JSONArray()
            val dishes = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { d -> MenuDish.from(d) } }
            return MenuScan(o.strOrNull("id"), dishes, o.strOrNull("note").orEmpty(), o.strOrNull("restaurant"), o.strOrNull("diet_mode"))
        }
    }
}
