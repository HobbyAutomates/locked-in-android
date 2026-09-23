package com.sohum.bandlog.data

import org.json.JSONArray
import org.json.JSONObject

data class Workout(
    val id: String,
    val date: String,
    val muscles: List<String>,
    val bandLevel: String,
    val resistanceKg: Double?,
    val minutes: Int?,
    val exercises: String,
    val notes: String,
) {
    companion object {
        fun from(o: JSONObject) = Workout(
            id = o.getString("id"),
            date = o.getString("date"),
            muscles = o.optJSONArray("muscles")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            bandLevel = o.optString("band_level", "Medium"),
            resistanceKg = if (o.isNull("resistance_kg")) null else o.optDouble("resistance_kg"),
            minutes = if (o.isNull("minutes")) null else o.optInt("minutes"),
            exercises = o.optString("exercises", ""),
            notes = o.optString("notes", ""),
        )
    }
}

data class MealItem(
    val id: String? = null,
    val foodId: String?,
    val name: String,
    val grams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String, // table | estimated
    val confidence: Double?,
    /** Only set on freshly parsed (not yet saved) items: the words it came from. */
    val input: String = "",
    /** Fibre / sugar / sodium / iron / calcium / vit C / potassium … already scaled to [grams]. */
    val micros: Map<String, Double> = emptyMap(),
) {
    fun toJson(mealId: String, userId: String): JSONObject = JSONObject()
        .put("meal_id", mealId).put("user_id", userId)
        .put("food_id", foodId ?: JSONObject.NULL).put("name", name).put("grams", grams)
        .put("calories", calories).put("protein_g", proteinG).put("carbs_g", carbsG).put("fat_g", fatG)
        .put("source", source).put("confidence", confidence ?: JSONObject.NULL)
        .put("micros", JSONObject(micros))

    /** Re-price after the user edits grams (scales linearly, micros included). */
    fun withGrams(g: Double): MealItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(grams = g, calories = calories * k, proteinG = proteinG * k, carbsG = carbsG * k, fatG = fatG * k, micros = micros.mapValues { it.value * k })
    }

    companion object {
        fun from(o: JSONObject) = MealItem(
            id = o.optString("id").ifBlank { null },
            foodId = if (o.isNull("food_id")) null else o.optString("food_id"),
            name = o.optString("name"),
            grams = o.optDouble("grams", 0.0),
            calories = o.optDouble("calories", 0.0),
            proteinG = o.optDouble("protein_g", 0.0),
            carbsG = o.optDouble("carbs_g", 0.0),
            fatG = o.optDouble("fat_g", 0.0),
            source = o.optString("source", "table"),
            confidence = if (o.isNull("confidence")) null else o.optDouble("confidence"),
            input = o.optString("input", ""),
            micros = micros(o.optJSONObject("micros")),
        )

        fun micros(o: JSONObject?): Map<String, Double> {
            if (o == null) return emptyMap()
            return o.keys().asSequence().associateWith { o.optDouble(it) }.filterValues { !it.isNaN() }
        }
    }
}

data class Meal(
    val id: String,
    val date: String,
    val rawText: String,
    val createdAt: String,
    val items: List<MealItem>,
    /** Storage path under meal-photos/ when the meal came from a plate photo. */
    val photoPath: String? = null,
) {
    val calories get() = items.sumOf { it.calories }
    val protein get() = items.sumOf { it.proteinG }

    companion object {
        fun from(o: JSONObject): Meal {
            val arr: JSONArray = o.optJSONArray("meal_items") ?: JSONArray()
            return Meal(
                id = o.getString("id"),
                date = o.getString("date"),
                rawText = o.optString("raw_text", ""),
                createdAt = o.optString("created_at", ""),
                items = (0 until arr.length()).map { MealItem.from(arr.getJSONObject(it)) },
                photoPath = if (o.isNull("photo_path")) null else o.optString("photo_path").ifBlank { null },
            )
        }
    }
}

data class Profile(
    val weeklyWorkoutTarget: Int = 3,
    val proteinTargetG: Int = 120,
    val calorieTarget: Int = 2200,
    val name: String = "",
    /** ISO yyyy-MM-dd. */
    val dob: String? = null,
    /** male | female | other */
    val gender: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val goalWeightKg: Double? = null,
    /** lose | maintain | gain */
    val goalType: String = "maintain",
    val goalSpeedKgWk: Double = 0.5,
    val stepGoal: Int = 8000,
    /** Explicit macro goals; null falls back to the derived formula. */
    val carbTargetGSet: Int? = null,
    val fatTargetGSet: Int? = null,
    /** Raw `{"breakfast":{"on":true,"time":"08:30"},…}`; parsed by util/Reminders. */
    val remindersJson: String = "",
) {
    /** Macro targets (Cal AI-style cards): explicit if set, else fat 25% of calories and carbs the remainder. */
    val fatTargetG: Int get() = fatTargetGSet ?: (calorieTarget * 0.25 / 9).toInt()
    val carbTargetG: Int get() = carbTargetGSet ?: ((calorieTarget - proteinTargetG * 4 - fatTargetG * 9) / 4).coerceAtLeast(0)

    /** Whole years from [dob], or null when no birthday is set. */
    val age: Int?
        get() = dob?.let {
            runCatching { java.time.Period.between(java.time.LocalDate.parse(it), java.time.LocalDate.now()).years }
                .getOrNull()?.takeIf { y -> y in 1..120 }
        }

    companion object {
        private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }

        fun from(o: JSONObject) = Profile(
            weeklyWorkoutTarget = o.optInt("weekly_workout_target", 3),
            proteinTargetG = o.optInt("protein_target_g", 120),
            calorieTarget = o.optInt("calorie_target", 2200),
            name = o.str("name").orEmpty(),
            dob = o.str("dob")?.take(10),
            gender = o.str("gender"),
            heightCm = o.dbl("height_cm"),
            weightKg = o.dbl("weight_kg"),
            goalWeightKg = o.dbl("goal_weight_kg"),
            goalType = o.str("goal_type") ?: "maintain",
            goalSpeedKgWk = o.dbl("goal_speed_kg_wk") ?: 0.5,
            stepGoal = if (o.isNull("step_goal")) 8000 else o.optInt("step_goal", 8000).coerceAtLeast(500),
            carbTargetGSet = if (o.isNull("carb_target_g")) null else o.optInt("carb_target_g").takeIf { it > 0 },
            fatTargetGSet = if (o.isNull("fat_target_g")) null else o.optInt("fat_target_g").takeIf { it > 0 },
            remindersJson = if (o.isNull("reminders")) "" else o.opt("reminders")?.toString().orEmpty(),
        )
    }
}

/** One row of `bandlog.weight_log`. */
data class WeightEntry(val id: String, val date: String, val weightKg: Double, val note: String) {
    companion object {
        fun from(o: JSONObject) = WeightEntry(
            id = o.getString("id"),
            date = o.getString("date"),
            weightKg = o.optDouble("weight_kg", 0.0),
            note = if (o.isNull("note")) "" else o.optString("note"),
        )
    }
}

data class ParseResult(val items: List<MealItem>, val assumptions: List<String>, val unparsed: List<String>)

/** A repeatable meal ("rice dal eggs whey") saved for one-tap logging. */
data class SavedMeal(val id: String, val name: String, val items: List<MealItem>, val calories: Double, val proteinG: Double) {
    companion object {
        fun from(o: JSONObject): SavedMeal {
            val arr = o.optJSONArray("items") ?: JSONArray()
            return SavedMeal(
                id = o.getString("id"), name = o.optString("name"),
                items = (0 until arr.length()).map { MealItem.from(arr.getJSONObject(it)) },
                calories = o.optDouble("calories", 0.0), proteinG = o.optDouble("protein_g", 0.0),
            )
        }
    }
}

/**
 * The drawable half of a label report: numbers the app can paint straight onto the screen
 * without doing any maths of its own. Everything defaults to zero so an older server (or a
 * report saved before v1.6) still renders.
 */
data class Infographic(
    val proteinPct: Int = 0,
    val carbsPct: Int = 0,
    val fatPct: Int = 0,
    val caloriesPct: Int = 0,
    val sugarTsp: Double = 0.0,
    val sodiumPct: Int = 0,
    val score: Int = 0,
    val oneLiner: String = "",
    /** yes | sometimes | skip */
    val eatIt: String = "sometimes",
) {
    /** True once the server actually filled it in; older reports fall back to the plain layout. */
    val present: Boolean get() = oneLiner.isNotBlank() || score > 0 || caloriesPct > 0

    companion object {
        fun from(o: JSONObject?): Infographic {
            if (o == null) return Infographic()
            val share = o.optJSONObject("serving_share") ?: JSONObject()
            fun pct(k: String) = share.optInt(k, 0).coerceIn(0, 100)
            return Infographic(
                proteinPct = pct("protein_pct"),
                carbsPct = pct("carbs_pct"),
                fatPct = pct("fat_pct"),
                caloriesPct = pct("calories_pct"),
                sugarTsp = o.optDouble("sugar_teaspoons_per_serving", 0.0).let { if (it.isNaN() || it < 0) 0.0 else it },
                sodiumPct = o.optInt("sodium_pct_of_2000mg", 0).coerceIn(0, 100),
                score = o.optInt("score_out_of_10", 0).coerceIn(0, 10),
                oneLiner = o.optString("one_liner").orEmpty(),
                eatIt = o.optString("eat_it", "sometimes").ifBlank { "sometimes" },
            )
        }
    }
}

/** How a product fits one way of eating: great | ok | weak, and the number that decides it. */
data class Fit(val verdict: String, val why: String)

/** Result of scanning a packaged food's label or barcode. */
data class LabelReport(
    val id: String?,
    /** label | barcode */
    val kind: String,
    /** protein | snack | cutting | bulking — the lens the report was written for. */
    val lens: String,
    val product: String,
    val readable: Boolean,
    /** Two neutral sentences: what it is, what it's made of, who it suits. */
    val whatItIs: String,
    /** Safety / authenticity only — shown as "Trust". safe | caution | unsafe | misleading | fake */
    val verdict: String,
    val verdictReason: String,
    /** Keyed by lens. Empty on reports saved before v1.7. */
    val fits: Map<String, Fit>,
    val imageUrl: String?,
    val barcode: String?,
    val per100: Map<String, Double>,
    val servingG: Double?,
    val proteinRating: String,    // excellent | good | average | poor
    val proteinPerServing: Double?,
    val proteinQuality: String,
    val proteinNote: String,
    val concerns: List<Triple<String, String, String>>, // ingredient, issue, severity
    val claims: List<Triple<String, String, String>>,   // claim, status, why
    val research: List<String>,
    val suggestions: List<String>,
    val alternatives: List<String>,
    val infographic: Infographic,
) {
    companion object {
        fun from(o: JSONObject): LabelReport {
            fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            fun triples(k: String, a: String, b: String, c: String) = o.optJSONArray(k)?.let { arr ->
                (0 until arr.length()).map { i -> val x = arr.getJSONObject(i); Triple(x.optString(a), x.optString(b), x.optString(c)) }
            } ?: emptyList()
            val p = o.optJSONObject("protein") ?: JSONObject()
            val n = o.optJSONObject("per_100g") ?: JSONObject()
            val f = o.optJSONObject("fits") ?: JSONObject()
            return LabelReport(
                id = o.optString("id").ifBlank { null },
                kind = o.optString("kind", "label").ifBlank { "label" },
                lens = o.optString("lens", "protein").ifBlank { "protein" },
                product = o.optString("product"), readable = o.optBoolean("readable", true),
                whatItIs = o.optString("what_it_is"),
                verdict = o.optString("verdict", "caution"), verdictReason = o.optString("verdict_reason"),
                fits = f.keys().asSequence().mapNotNull { k -> f.optJSONObject(k)?.let { k to Fit(it.optString("verdict", "ok"), it.optString("why")) } }.toMap(),
                imageUrl = if (o.isNull("image_url")) null else o.optString("image_url").ifBlank { null },
                barcode = if (o.isNull("barcode")) null else o.optString("barcode").ifBlank { null },
                per100 = n.keys().asSequence().associateWith { n.optDouble(it) }.filterValues { !it.isNaN() },
                servingG = if (o.isNull("serving_g")) null else o.optDouble("serving_g"),
                proteinRating = p.optString("rating", "average"),
                proteinPerServing = if (p.isNull("per_serving_g")) null else p.optDouble("per_serving_g"),
                proteinQuality = p.optString("quality"), proteinNote = p.optString("note"),
                concerns = triples("concerns", "ingredient", "issue", "severity"),
                claims = triples("claims", "claim", "status", "why"),
                research = strings("research"), suggestions = strings("suggestions"), alternatives = strings("alternatives"),
                infographic = Infographic.from(o.optJSONObject("infographic")),
            )
        }
    }
}

/** One food the plate photo recogniser found. Macros/micros are for THIS portion ([grams]). */
data class PlateItem(
    val name: String,
    val grams: Double,
    /** high | medium | low */
    val confidence: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val micros: Map<String, Double>,
    /** table (numbers from the food database) | estimated (the model's own) */
    val source: String,
    val foodId: String?,
) {
    fun withGrams(g: Double): PlateItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(grams = g, calories = calories * k, proteinG = proteinG * k, carbsG = carbsG * k, fatG = fatG * k, micros = micros.mapValues { it.value * k })
    }

    fun toMealItem() = MealItem(
        foodId = foodId, name = name, grams = grams, calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
        source = source, confidence = when (confidence) { "high" -> 0.9; "medium" -> 0.6; else -> 0.3 }, micros = micros,
    )

    companion object {
        fun from(o: JSONObject) = PlateItem(
            name = o.optString("name", "food"),
            grams = o.optDouble("grams", 0.0),
            confidence = o.optString("confidence", "medium"),
            calories = o.optDouble("calories", 0.0),
            proteinG = o.optDouble("protein_g", 0.0),
            carbsG = o.optDouble("carbs_g", 0.0),
            fatG = o.optDouble("fat_g", 0.0),
            micros = MealItem.micros(o.optJSONObject("micros")),
            source = o.optString("source", "estimated"),
            foodId = if (o.isNull("food_id")) null else o.optString("food_id").ifBlank { null },
        )
    }
}

/** Result of /api/photo-meal: the cross-checked items plus the model's raw view. */
data class PlateEstimate(
    val id: String?,
    val items: List<PlateItem>,
    val notes: List<String>,
    val plateNote: String,
    /** Set when the server stored the JPEG; "Save as meal" reuses it instead of uploading again. */
    val photoPath: String?,
    /** Signed URL for a stored plate photo (only on reports opened from History). */
    val photoUrl: String? = null,
) {
    companion object {
        fun from(o: JSONObject): PlateEstimate {
            val arr = o.optJSONArray("items") ?: JSONArray()
            val notes = o.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            return PlateEstimate(
                id = o.optString("id").ifBlank { null },
                items = (0 until arr.length()).map { PlateItem.from(arr.getJSONObject(it)) },
                notes = notes,
                plateNote = o.optString("plate_note"),
                photoPath = if (o.isNull("photo_path")) null else o.optString("photo_path").ifBlank { null },
                photoUrl = if (o.isNull("photo_url")) null else o.optString("photo_url").ifBlank { null },
            )
        }
    }
}

/** One row of the Scan tab's History list. */
data class ScanHistoryItem(
    val id: String,
    /** label | barcode | photo */
    val kind: String,
    val lens: String,
    val product: String,
    val verdict: String,
    val createdAt: String,
    val score: Int?,
    /** Open Food Facts picture for barcode scans; Storage path (needs signing) for photo scans. */
    val imageUrl: String?,
    val imagePath: String?,
) {
    companion object {
        fun from(o: JSONObject) = ScanHistoryItem(
            id = o.getString("id"),
            kind = o.optString("kind", "label").ifBlank { "label" },
            lens = o.optString("lens", "protein").ifBlank { "protein" },
            product = o.optString("product"),
            verdict = o.optString("verdict"),
            createdAt = o.optString("created_at"),
            score = if (o.isNull("score")) null else o.optString("score").toIntOrNull(),
            imageUrl = if (o.isNull("image_url")) null else o.optString("image_url").ifBlank { null },
            imagePath = if (o.isNull("image_path")) null else o.optString("image_path").ifBlank { null },
        )
    }
}

data class Totals(val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

fun totalsFor(meals: List<Meal>, date: String): Totals {
    val items = meals.filter { it.date == date }.flatMap { it.items }
    return Totals(
        calories = items.sumOf { it.calories },
        protein = items.sumOf { it.proteinG },
        carbs = items.sumOf { it.carbsG },
        fat = items.sumOf { it.fatG },
    )
}
