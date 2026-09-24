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
    val source: String, // table | estimated | scan
    val confidence: Double?,
    /** Only set on freshly parsed (not yet saved) items: the words it came from. */
    val input: String = "",
    /** Fibre / sugar / sodium / iron / calcium / vit C / potassium … already scaled to [grams]. */
    val micros: Map<String, Double> = emptyMap(),
    /** v1.9: how the quantity was entered — g | ml | kg | serving — and how many servings that was. */
    val unit: String? = null,
    val servings: Double? = null,
    /** v1.9: the fat preset this dish was cooked in (the fat itself is a separate item). */
    val cookedIn: String? = null,
) {
    fun toJson(mealId: String, userId: String): JSONObject = JSONObject()
        .put("meal_id", mealId).put("user_id", userId)
        .put("food_id", foodId ?: JSONObject.NULL).put("name", name).put("grams", grams)
        .put("calories", calories).put("protein_g", proteinG).put("carbs_g", carbsG).put("fat_g", fatG)
        .put("source", source).put("confidence", confidence ?: JSONObject.NULL)
        .put("micros", JSONObject(micros))
        .put("unit", unit ?: JSONObject.NULL).put("servings", servings ?: JSONObject.NULL).put("cooked_in", cookedIn ?: JSONObject.NULL)

    /** Re-price after the user edits grams (scales linearly, micros included). */
    fun withGrams(g: Double): MealItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(grams = g, calories = calories * k, proteinG = proteinG * k, carbsG = carbsG * k, fatG = fatG * k, micros = micros.mapValues { it.value * k }, servings = servings?.let { it * k })
    }

    /** "1.5 servings" when logged by serving, else "150 g". */
    val quantityLabel: String
        get() = if (unit == "serving" && servings != null && servings > 0) "${com.sohum.bandlog.ui.today.fmt(servings)} serving${if (servings == 1.0) "" else "s"}" else "${grams.toInt()} g"

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
            unit = if (o.isNull("unit")) null else o.optString("unit").ifBlank { null },
            servings = if (o.isNull("servings")) null else o.optDouble("servings").takeIf { !it.isNaN() },
            cookedIn = if (o.isNull("cooked_in")) null else o.optString("cooked_in").ifBlank { null },
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
    /** v1.9: protein | goal | snack | cutting | bulking — the lens a scan report opens on. */
    val lensDefault: String = "protein",
    /** Squads: share protein & calories with squad-mates, or streaks only. */
    val shareStats: Boolean = true,
    /** `<uid>/avatar.jpg?v=<millis>` in the public avatars bucket; null shows initials. */
    val avatarPath: String? = null,
    // ---- v2.3 (columns may not exist yet on an older database: null / defaults then) ----
    val fiberTarget: Int = 30,
    val sugarTarget: Int = 50,
    /** Null when the column isn't there yet; the app then falls back to its local toggle. */
    val addBurnedToGoal: Boolean? = null,
    val rolloverCalories: Boolean? = null,
    val waterGoalMl: Int = 2500,
) {
    /** The lens a report opens on: `goal` follows the weight goal (lose → cutting, gain → bulking, else protein). */
    val initialLens: String
        get() = when (lensDefault) {
            "goal" -> when (goalType) { "lose" -> "cutting"; "gain" -> "bulking"; else -> "protein" }
            "snack", "cutting", "bulking" -> lensDefault
            else -> "protein"
        }

    /** Macro targets (Cal AI-style cards): explicit if set, else fat 25% of calories and carbs the remainder. */
    val fatTargetG: Int get() = fatTargetGSet ?: (calorieTarget * 0.25 / 9).toInt()
    val carbTargetG: Int get() = carbTargetGSet ?: ((calorieTarget - proteinTargetG * 4 - fatTargetG * 9) / 4).coerceAtLeast(0)

    /** Whole years from [dob], or null when no birthday is set. */
    val age: Int?
        get() = dob?.let {
            runCatching { java.time.Period.between(java.time.LocalDate.parse(it), java.time.LocalDate.now(com.sohum.bandlog.util.Dates.ZONE)).years }
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
            lensDefault = o.str("lens_default") ?: "protein",
            shareStats = if (o.isNull("share_stats")) true else o.optBoolean("share_stats", true),
            avatarPath = o.str("avatar_path"),
            fiberTarget = if (!o.has("fiber_target") || o.isNull("fiber_target")) 30 else o.optInt("fiber_target", 30).coerceAtLeast(1),
            sugarTarget = if (!o.has("sugar_target") || o.isNull("sugar_target")) 50 else o.optInt("sugar_target", 50).coerceAtLeast(1),
            addBurnedToGoal = if (!o.has("add_burned_to_goal")) null else o.optBoolean("add_burned_to_goal", false),
            rolloverCalories = if (!o.has("rollover_calories")) null else o.optBoolean("rollover_calories", false),
            waterGoalMl = if (!o.has("water_goal_ml") || o.isNull("water_goal_ml")) 2500 else o.optInt("water_goal_ml", 2500).coerceIn(250, 10_000),
        )
    }
}

/** One row of `bandlog.weight_log`. */
data class WeightEntry(val id: String, val date: String, val weightKg: Double, val note: String, val photoPath: String? = null) {
    companion object {
        fun from(o: JSONObject) = WeightEntry(
            id = o.getString("id"),
            date = o.getString("date"),
            weightKg = o.optDouble("weight_kg", 0.0),
            note = if (o.isNull("note")) "" else o.optString("note"),
            photoPath = if (!o.has("photo_path") || o.isNull("photo_path")) null else o.optString("photo_path").ifBlank { null },
        )
    }
}

/** One row of `bandlog.water_log` (v2.3). */
data class WaterEntry(val id: String, val date: String, val ml: Int, val createdAt: String) {
    companion object {
        fun from(o: JSONObject) = WaterEntry(o.getString("id"), o.optString("date"), o.optInt("ml", 0), o.optString("created_at"))
    }
}

/** One row of `bandlog.progress_photos` (v2.3); [path] is inside the private progress-photos bucket. */
data class ProgressPhoto(val id: String, val date: String, val path: String, val note: String) {
    companion object {
        fun from(o: JSONObject) = ProgressPhoto(
            o.getString("id"), o.optString("date"), o.optString("path"),
            if (!o.has("note") || o.isNull("note")) "" else o.optString("note"),
        )
    }
}

/** A public squad from `bandlog.public_groups()` (v2.3 Discover). */
data class PublicSquad(val id: String, val name: String, val tagline: String, val coverUrl: String?, val memberCount: Int) {
    companion object {
        fun from(o: JSONObject) = PublicSquad(
            id = o.getString("id"),
            name = o.optString("name"),
            tagline = if (!o.has("tagline") || o.isNull("tagline")) "" else o.optString("tagline"),
            coverUrl = if (!o.has("cover_url") || o.isNull("cover_url")) null else o.optString("cover_url").ifBlank { null },
            memberCount = o.optInt("member_count", 0),
        )
    }
}

data class ParseResult(val items: List<MealItem>, val assumptions: List<String>, val unparsed: List<String>)

/** A household serving: "1 katori" = 150 g. */
data class Serving(val label: String, val grams: Double) {
    companion object {
        fun list(a: JSONArray?): List<Serving> = a?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val g = o.optDouble("grams", 0.0)
                val label = o.optString("label").ifBlank { o.optString("name") }
                if (g > 0 && label.isNotBlank()) Serving(label, g) else null
            }
        } ?: emptyList()
    }
}

/** One row of `bandlog.food_presets`, joined to its foods row (per 100 g). */
data class FoodPreset(
    val id: String,
    val foodId: String,
    val label: String,
    val labelHi: String?,
    /** breakfast | staple | dal | sabzi | protein | snack | drink | sweet | fruit | fat */
    val category: String,
    val servings: List<Serving>,
    val defaultServing: String?,
    val sort: Int,
    val icon: String?,
    val foodName: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val micros: Map<String, Double>,
) {
    val default: Serving? get() = servings.firstOrNull { it.label == defaultServing } ?: servings.firstOrNull()

    companion object {
        fun from(o: JSONObject): FoodPreset? {
            val f = o.optJSONObject("foods") ?: return null
            return FoodPreset(
                id = o.getString("id"),
                foodId = o.optString("food_id"),
                label = o.optString("label"),
                labelHi = if (o.isNull("label_hi")) null else o.optString("label_hi").ifBlank { null },
                category = o.optString("category"),
                servings = Serving.list(o.optJSONArray("servings")),
                defaultServing = if (o.isNull("default_serving")) null else o.optString("default_serving").ifBlank { null },
                sort = o.optInt("sort", 100),
                icon = if (o.isNull("icon")) null else o.optString("icon").ifBlank { null },
                foodName = f.optString("name"),
                calories = f.optDouble("calories", 0.0),
                proteinG = f.optDouble("protein_g", 0.0),
                carbsG = f.optDouble("carbs_g", 0.0),
                fatG = f.optDouble("fat_g", 0.0),
                micros = MealItem.micros(f.optJSONObject("micros")),
            )
        }
    }
}

/** One hit from the `search_foods` RPC (per 100 g), for the Search tab. */
data class FoodHit(
    val id: String,
    val name: String,
    val nameHi: String?,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    /** custom | dish | ifct | usda | off */
    val source: String,
    val units: List<Serving>,
    val micros: Map<String, Double>,
    val score: Double,
) {
    companion object {
        fun from(o: JSONObject): FoodHit {
            val micros = MealItem.micros(o.optJSONObject("micros")).toMutableMap()
            listOf("fiber_g", "sugar_g", "sodium_mg").forEach { k -> if (!o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() }?.let { micros[k] = it } }
            return FoodHit(
                id = o.getString("id"),
                name = o.optString("name"),
                nameHi = o.optJSONObject("names_local")?.optString("hi")?.ifBlank { null },
                calories = o.optDouble("calories", 0.0),
                proteinG = o.optDouble("protein_g", 0.0),
                carbsG = o.optDouble("carbs_g", 0.0),
                fatG = o.optDouble("fat_g", 0.0),
                source = o.optString("source", "custom"),
                units = Serving.list(o.optJSONArray("units")),
                micros = micros,
                score = o.optDouble("score", 0.0),
            )
        }
    }
}

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
    /** v2.0: "restaurant" when the server scaled the portion and added the hidden oil. */
    val cookedIn: String? = null,
) {
    fun withGrams(g: Double): PlateItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(grams = g, calories = calories * k, proteinG = proteinG * k, carbsG = carbsG * k, fatG = fatG * k, micros = micros.mapValues { it.value * k })
    }

    fun toMealItem() = MealItem(
        foodId = foodId, name = name, grams = grams, calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
        source = source, confidence = when (confidence) { "high" -> 0.9; "medium" -> 0.6; else -> 0.3 }, micros = micros, cookedIn = cookedIn,
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
            cookedIn = if (o.isNull("cooked_in")) null else o.optString("cooked_in").ifBlank { null },
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
    /** v2.0: "restaurant" when the note / plate description said it was eaten out. */
    val portionHint: String? = null,
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
                portionHint = if (o.isNull("portion_hint")) null else o.optString("portion_hint").ifBlank { null },
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
    /** Open Food Facts picture for barcode scans (column, else the one inside the report). */
    val imageUrl: String?,
    /** meal-photos path for plate scans (needs signing). */
    val imagePath: String?,
    /** scan-photos/<uid>/<id>.jpg thumbnail (private; fetched with the user's token). */
    val thumbPath: String? = null,
    /** The report's one-line "what it is". */
    val whatItIs: String = "",
    /** Never blank or "<UNKNOWN>": product → first ingredient / first food → "Unnamed label" / "Plate photo". */
    val displayName: String = product,
) {
    val isPlate: Boolean get() = kind == "photo" || kind == "plate"

    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).trim().ifBlank { null }

        /** Model placeholders that must never reach the list. */
        fun isJunkName(n: String?): Boolean {
            val t = n?.trim()?.lowercase().orEmpty()
            return t.isEmpty() || t == "<unknown>" || t == "unknown" || t == "n/a" || t == "na" || t == "null" || t == "unnamed"
        }

        fun from(o: JSONObject): ScanHistoryItem {
            val kind = o.optString("kind", "label").ifBlank { "label" }
            val product = o.optString("product")
            val plate = kind == "photo" || kind == "plate"
            val name = listOf(product, o.s("first_ingredient").takeIf { !plate }, o.s("first_item").takeIf { plate })
                .firstOrNull { !isJunkName(it) }?.trim()
                ?: if (plate) "Plate photo" else "Unnamed label"
            return ScanHistoryItem(
                id = o.getString("id"),
                kind = kind,
                lens = o.optString("lens", "protein").ifBlank { "protein" },
                product = product,
                verdict = o.optString("verdict"),
                createdAt = o.optString("created_at"),
                score = o.s("score")?.toIntOrNull(),
                imageUrl = o.s("image_url") ?: o.s("report_image_url"),
                imagePath = o.s("image_path"),
                thumbPath = o.s("thumb_path"),
                whatItIs = o.s("what_it_is").orEmpty(),
                displayName = name.replaceFirstChar { it.uppercase() },
            )
        }
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

/** One row of `bandlog.activities` — a MET-table entry the user can log against. */
data class Activity(
    val code: String,
    val name: String,
    val description: String,
    val met: Double,
    val category: String,
    val tags: List<String>,
) {
    /** "walking · brisk, 3.5-4 mph"; plain name when the description adds nothing. */
    val label: String get() = if (description.isBlank() || description == "general") name else "$name · $description"

    companion object {
        fun from(o: JSONObject) = Activity(
            code = o.getString("code"),
            name = o.optString("name"),
            description = if (o.isNull("description")) "" else o.optString("description"),
            met = o.optDouble("met", 0.0),
            category = o.optString("category"),
            tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        )
    }
}

/** One row of `bandlog.exercise_log`: a burn the user logged (or a band workout wrote for them). */
data class ExerciseEntry(
    val id: String,
    val date: String,
    val activityCode: String?,
    val name: String,
    val minutes: Int,
    /** low | medium | high */
    val intensity: String,
    val kcal: Double,
    /** manual | workout | health | describe */
    val source: String,
    /** For source=workout this holds the workout id so a delete can find its row. */
    val note: String,
    val createdAt: String,
    // ---- v2.3 Google-Fit-style extras (null on older rows / databases) ----
    val startedAt: String? = null,
    val intensityPct: Int? = null,
    val distanceKm: Double? = null,
    val steps: Int? = null,
) {
    companion object {
        private fun JSONObject.has0(k: String) = has(k) && !isNull(k)
        fun from(o: JSONObject) = ExerciseEntry(
            id = o.getString("id"),
            date = o.getString("date"),
            activityCode = if (o.isNull("activity_code")) null else o.optString("activity_code").ifBlank { null },
            name = o.optString("name"),
            minutes = o.optInt("minutes", 0),
            intensity = o.optString("intensity", "medium").ifBlank { "medium" },
            kcal = o.optDouble("kcal", 0.0),
            source = o.optString("source", "manual").ifBlank { "manual" },
            note = if (o.isNull("note")) "" else o.optString("note"),
            createdAt = o.optString("created_at", ""),
            startedAt = if (o.has0("started_at")) o.optString("started_at") else null,
            intensityPct = if (o.has0("intensity_pct")) o.optInt("intensity_pct") else null,
            distanceKm = if (o.has0("distance_km")) o.optDouble("distance_km").takeIf { !it.isNaN() } else null,
            steps = if (o.has0("steps")) o.optInt("steps") else null,
        )
    }
}

/** One activity Haiku pulled out of a free-text description ("played badminton for an hour"). */
data class DescribedExercise(
    val activityCode: String?,
    val name: String,
    val minutes: Int,
    val intensity: String,
    val met: Double,
    val kcal: Double,
) {
    companion object {
        fun from(o: JSONObject) = DescribedExercise(
            activityCode = if (o.isNull("activity_code")) null else o.optString("activity_code").ifBlank { null },
            name = o.optString("name"),
            minutes = o.optInt("minutes", 0),
            intensity = o.optString("intensity", "medium").ifBlank { "medium" },
            met = o.optDouble("met", 0.0),
            kcal = o.optDouble("kcal", 0.0),
        )
    }
}

// ---- v2.0: squads ----

/** A squad the signed-in user belongs to (`bandlog.groups`). */
data class Squad(val id: String, val name: String, val code: String, val ownerId: String) {
    companion object {
        fun from(o: JSONObject) = Squad(o.getString("id"), o.optString("name"), o.optString("code"), o.optString("owner_id"))
    }
}

/** One day of a squad-mate's rollup; the numbers are null for members who share streaks only. */
data class SquadDay(
    val date: String,
    val trained: Boolean,
    val weekStreak: Int,
    val proteinG: Double?,
    val calories: Double?,
    val burned: Double?,
    val meals: Int?,
) {
    companion object {
        private fun JSONObject.dblOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k).takeIf { !it.isNaN() }
        fun from(o: JSONObject) = SquadDay(
            date = o.optString("date").take(10),
            trained = o.optBoolean("trained", false),
            weekStreak = o.optInt("week_streak", 0),
            proteinG = o.dblOrNull("protein_g"),
            calories = o.dblOrNull("calories"),
            burned = o.dblOrNull("burned"),
            meals = if (o.isNull("meals") || !o.has("meals")) null else o.optInt("meals"),
        )
    }
}

/** One row of `bandlog.squad_board(g)`. */
data class SquadMember(
    val userId: String,
    val name: String,
    val shareStats: Boolean,
    val isOwner: Boolean,
    val days: List<SquadDay>,
    /** Same format as profiles.avatar_path; null shows initials. */
    val avatarPath: String? = null,
) {
    /** The streak from their newest rollup (0 when they haven't opened the app this week). */
    val weekStreak: Int get() = days.maxByOrNull { it.date }?.weekStreak ?: 0
    fun day(date: String): SquadDay? = days.firstOrNull { it.date == date }

    companion object {
        fun from(o: JSONObject): SquadMember {
            val arr = o.optJSONArray("days") ?: JSONArray()
            return SquadMember(
                userId = o.getString("user_id"),
                name = o.optString("name").ifBlank { "Member" },
                shareStats = if (o.isNull("share_stats")) true else o.optBoolean("share_stats", true),
                isOwner = o.optBoolean("is_owner", false),
                days = (0 until arr.length()).map { SquadDay.from(arr.getJSONObject(it)) },
                avatarPath = if (!o.has("avatar_path") || o.isNull("avatar_path")) null else o.optString("avatar_path").ifBlank { null },
            )
        }
    }
}

/** A nudge a squad-mate sent me in the last 24 h (`bandlog.my_nudges()`). */
data class Nudge(val id: String, val groupName: String, val fromName: String, val createdAt: String) {
    companion object {
        fun from(o: JSONObject) = Nudge(o.getString("id"), o.optString("group_name"), o.optString("from_name").ifBlank { "A squad-mate" }, o.optString("created_at"))
    }
}
