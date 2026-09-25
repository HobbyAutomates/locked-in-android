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
    /** v2.5: bands | gym | bodyweight | cardio | sport | yoga (null column = bands, every pre-2.5 row). */
    val kind: String = BANDS,
    /** v2.5: gym / bodyweight lifts from exercises_json. */
    val lifts: List<Lift> = emptyList(),
) {
    val isBands: Boolean get() = kind == BANDS

    /** "Gym · 5 exercises · 42 min", "Bands · Chest · Back", "Yoga / Stretch · 30 min". */
    val summary: String
        get() = when {
            isBands -> (listOf("Bands") + muscles).joinToString(" · ")
            lifts.isEmpty() && exercises.isNotBlank() -> listOfNotNull(exercises.replaceFirstChar { it.uppercase() }, minutes?.let { "$it min" }).joinToString(" · ")
            else -> listOfNotNull(
                kindLabel(kind),
                lifts.size.takeIf { it > 0 }?.let { "$it exercise${if (it == 1) "" else "s"}" },
                minutes?.let { "$it min" },
            ).joinToString(" · ")
        }

    companion object {
        const val BANDS = "bands"
        val KINDS = listOf("gym", "bodyweight", BANDS, "cardio", "sport", "yoga")
        fun kindLabel(k: String): String = when (k) {
            "gym" -> "Gym"; "bodyweight" -> "Bodyweight"; "cardio" -> "Cardio"; "sport" -> "Sport"; "yoga" -> "Yoga / Stretch"; else -> "Bands"
        }

        fun from(o: JSONObject) = Workout(
            id = o.getString("id"),
            date = o.getString("date"),
            muscles = o.optJSONArray("muscles")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            bandLevel = o.optString("band_level", "Medium"),
            resistanceKg = if (o.isNull("resistance_kg")) null else o.optDouble("resistance_kg"),
            minutes = if (o.isNull("minutes")) null else o.optInt("minutes"),
            exercises = o.optString("exercises", ""),
            notes = o.optString("notes", ""),
            kind = if (!o.has("kind") || o.isNull("kind")) BANDS else o.optString("kind").ifBlank { BANDS },
            lifts = Lift.list(if (!o.has("exercises_json") || o.isNull("exercises_json")) null else o.opt("exercises_json")),
        )
    }
}

/** v2.5 one set of a lift: kg (null for bodyweight) x reps. */
data class LiftSet(val kg: Double?, val reps: Int?)

/** v2.5 one gym / bodyweight exercise and its sets: an element of workouts.exercises_json. */
data class Lift(val name: String, val sets: List<LiftSet>) {
    companion object {
        /** [{"name": "Bench press", "sets": [{"kg": 40, "reps": 8}, ...]}, ...] */
        fun toJson(lifts: List<Lift>): JSONArray = JSONArray().apply {
            lifts.forEach { l ->
                put(JSONObject().put("name", l.name).put("sets", JSONArray().apply {
                    l.sets.forEach { s -> put(JSONObject().put("kg", s.kg ?: JSONObject.NULL).put("reps", s.reps ?: JSONObject.NULL)) }
                }))
            }
        }

        /** Reads the column whether PostgREST hands back a JSON array or a string. */
        fun list(v: Any?): List<Lift> {
            val arr = when (v) { is JSONArray -> v; is String -> runCatching { JSONArray(v) }.getOrNull(); else -> null } ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim().ifBlank { return@mapNotNull null }
                val sets = o.optJSONArray("sets")?.let { sa ->
                    (0 until sa.length()).mapNotNull { j ->
                        val so = sa.optJSONObject(j) ?: return@mapNotNull null
                        LiftSet(
                            kg = if (!so.has("kg") || so.isNull("kg")) null else so.optDouble("kg").takeIf { !it.isNaN() },
                            reps = if (!so.has("reps") || so.isNull("reps")) null else so.optInt("reps").takeIf { it > 0 },
                        )
                    }
                } ?: emptyList()
                Lift(name, sets)
            }
        }

        private fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

        /** "Bench press 40x8, 40x8; Push-up 15, 12": the plain-text fallback for the exercises column. */
        fun summaryText(lifts: List<Lift>): String = lifts.joinToString("; ") { l ->
            l.name + " " + l.sets.filter { it.reps != null || it.kg != null }.joinToString(", ") { s -> listOfNotNull(s.kg?.let { num(it) }, s.reps?.toString()).joinToString("×") }
        }
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
    /** v2.4: a picture of the food when the parser / food row has one (never saved on meal_items). */
    val imageUrl: String? = null,
    /** v2.5: parse-meal's `default_count` ("2 roti" → 2) — where the Quantity sheet's stepper starts (never saved). */
    val defaultCount: Double? = null,
    /** v2.9: "Which one?" options when the name is ambiguous (display only, never saved). */
    val variants: List<FoodVariant> = emptyList(),
    /** v2.9: where the numbers came from, for the ⓘ sheet (display only, never saved). */
    val sourceInfo: SourceInfo? = null,
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

    /**
     * v2.8 (B4): how the amount reads — "2 roti", "1½ katori", "180 g". Restaurant portions and
     * anything not counted read in grams, never "1.4 servings".
     */
    val quantityLabel: String
        get() = com.sohum.bandlog.util.Counting.itemLabel(this)

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
            imageUrl = urlOf(o),
            defaultCount = if (!o.has("default_count") || o.isNull("default_count")) null else o.optDouble("default_count").takeIf { !it.isNaN() && it > 0 },
            variants = FoodVariant.list(o.optJSONArray("variants")),
            sourceInfo = SourceInfo.from(o.optJSONObject("source_info")),
        )

        /** `image_url` when present and non-blank, else null. */
        fun urlOf(o: JSONObject?, key: String = "image_url"): String? =
            if (o == null || !o.has(key) || o.isNull(key)) null else o.optString(key).trim().takeIf { it.startsWith("http") }

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
    /** v2.8: breakfast | lunch | dinner | snack (schema_v30); null / no column → the hour rule (util/MealTypes). */
    val mealType: String? = null,
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
                mealType = if (!o.has("meal_type") || o.isNull("meal_type")) null else o.optString("meal_type").takeIf { com.sohum.bandlog.util.MealTypes.isType(it) },
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
    // ---- v2.6 (null / defaults while the web agent's columns aren't there yet) ----
    /** Unique handle for squads; null until the one-time "Create a username" flow runs. */
    val username: String? = null,
    /** True when the profiles row has a `username` column at all (the flow is skipped otherwise). */
    val usernameSupported: Boolean = false,
    /** "HH:mm" window for water reminders; null = column missing / never set. */
    val waterReminderFrom: String? = null,
    val waterReminderTo: String? = null,
    /** 0 (never) / 30 / 60 / 120 / 180 / 240; null = column missing / never set. */
    val waterReminderEveryMin: Int? = null,
    /** One "glass" in mL (the + / − beside the bottle, the goal in glasses). */
    val waterGlassMl: Int = 250,
    /** v2.9 Squad sharing: kinds that auto-post (meal / workout / pr); null = column missing (schema_v31 not applied, everything posts). */
    val autoShare: List<String>? = null,
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
            username = if (!o.has("username")) null else o.str("username"),
            usernameSupported = o.has("username"),
            waterReminderFrom = if (!o.has("water_reminder_from")) null else o.str("water_reminder_from")?.take(5),
            waterReminderTo = if (!o.has("water_reminder_to")) null else o.str("water_reminder_to")?.take(5),
            waterReminderEveryMin = if (!o.has("water_reminder_every_min") || o.isNull("water_reminder_every_min")) null else o.optInt("water_reminder_every_min", 0),
            waterGlassMl = if (!o.has("water_glass_ml") || o.isNull("water_glass_ml")) 250 else o.optInt("water_glass_ml", 250).coerceIn(50, 2000),
            autoShare = if (!o.has("auto_share")) null else com.sohum.bandlog.util.SquadSharing.parseAutoShare(o.optJSONArray("auto_share")) ?: com.sohum.bandlog.util.SquadSharing.KINDS,
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
data class WaterEntry(val id: String, val date: String, val ml: Int, val createdAt: String, val vessel: String? = null) {
    companion object {
        fun from(o: JSONObject) = WaterEntry(
            o.getString("id"), o.optString("date"), o.optInt("ml", 0), o.optString("created_at"),
            if (!o.has("vessel") || o.isNull("vessel")) null else o.optString("vessel").ifBlank { null },
        )
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
data class PublicSquad(
    val id: String, val name: String, val tagline: String, val coverUrl: String?, val memberCount: Int,
    val icon: String? = null, val description: String = "", val joinPolicy: String = "open",
) {
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = PublicSquad(
            id = o.getString("id"),
            name = o.optString("name"),
            tagline = o.s("tagline") ?: o.s("description").orEmpty(),
            coverUrl = o.s("cover_url"),
            memberCount = o.optInt("member_count", 0),
            icon = o.s("icon"), description = o.s("description").orEmpty(), joinPolicy = o.s("join_policy") ?: "open",
        )
    }
}

/** v2.7: plain water pulled out of the dictated text BEFORE the LLM ever sees it (see the web's src/lib/waterParse.ts). */
data class ParsedWater(val ml: Int, val glasses: Double, val phrase: String) {
    companion object {
        fun from(o: JSONObject?): ParsedWater? {
            if (o == null) return null
            val ml = o.optInt("ml", 0)
            if (ml <= 0) return null
            return ParsedWater(ml, o.optDouble("glasses", 0.0), o.optString("phrase"))
        }
    }
}

data class ParseResult(val items: List<MealItem>, val assumptions: List<String>, val unparsed: List<String>, val water: ParsedWater? = null)

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
    /** v2.4: the preset's picture, else its food's. */
    val imageUrl: String? = null,
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
                imageUrl = MealItem.urlOf(o) ?: MealItem.urlOf(f),
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
    /** v2.4: foods.image_url when the RPC returns it. */
    val imageUrl: String? = null,
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
                imageUrl = MealItem.urlOf(o),
            )
        }
    }
}

/** A repeatable meal ("rice dal eggs whey") saved for one-tap logging. */
data class SavedMeal(val id: String, val name: String, val items: List<MealItem>, val calories: Double, val proteinG: Double, val imageUrl: String? = null) {
    /** The item the picture search should be about: the biggest one on the plate, else the name. */
    val pictureName: String get() = items.maxByOrNull { it.calories }?.name ?: name

    companion object {
        fun from(o: JSONObject): SavedMeal {
            val arr = o.optJSONArray("items") ?: JSONArray()
            return SavedMeal(
                id = o.getString("id"), name = o.optString("name"),
                items = (0 until arr.length()).map { MealItem.from(arr.getJSONObject(it)) },
                calories = o.optDouble("calories", 0.0), proteinG = o.optDouble("protein_g", 0.0),
                imageUrl = MealItem.urlOf(o),
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

/** v2.8: one deterministic sanity-check flag from the server's src/lib/ai/validate/label.ts
 *  (kJ read as kcal, a decimal slip, ...) — shown as a small non-blocking "Check this" banner. */
data class ValidationFlag(val field: String, val issue: String, val suggestion: String) {
    companion object {
        fun from(o: JSONObject) = ValidationFlag(o.optString("field"), o.optString("issue"), o.optString("suggestion"))
    }
}

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
    /** Where per100 came from: "label" (parsed off the transcript) | "openfoodfacts" | null when there is none. */
    val nutritionSource: String?,
    /** True when the transcript/barcode record had no usable nutrition table — per100 is empty, ask for a label scan. */
    val needsBackOfPack: Boolean,
    val concerns: List<Triple<String, String, String>>, // ingredient, issue, severity
    val claims: List<Triple<String, String, String>>,   // claim, status, why
    val research: List<String>,
    val suggestions: List<String>,
    val alternatives: List<String>,
    val infographic: Infographic,
    /** v2.8: deterministic sanity-check flags — never blocks the scan, just a heads-up. */
    val validation: List<ValidationFlag> = emptyList(),
    /** v2.9: where the numbers came from (label / Open Food Facts + research links). */
    val sourceInfo: SourceInfo? = null,
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
            val validation = o.optJSONArray("validation")?.let { arr -> (0 until arr.length()).map { ValidationFlag.from(arr.getJSONObject(it)) } } ?: emptyList()
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
                nutritionSource = if (o.isNull("nutrition_source")) null else o.optString("nutrition_source").ifBlank { null },
                needsBackOfPack = o.optBoolean("needs_back_of_pack", false),
                concerns = triples("concerns", "ingredient", "issue", "severity"),
                claims = triples("claims", "claim", "status", "why"),
                research = strings("research"), suggestions = strings("suggestions"), alternatives = strings("alternatives"),
                infographic = Infographic.from(o.optJSONObject("infographic")),
                validation = validation,
                sourceInfo = SourceInfo.from(o.optJSONObject("source_info")),
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
    /** v2.8: a plausible gram range for this portion, from the model — null on older reports. */
    val gramsLow: Double? = null,
    val gramsHigh: Double? = null,
    /** v2.8: what's uncertain about this item, e.g. "oil amount unclear". */
    val uncertainties: List<String> = emptyList(),
    /** v2.9: "Which one?" options when the name is ambiguous — empty on older reports. */
    val variants: List<FoodVariant> = emptyList(),
    /** v2.9: where the numbers came from, for the ⓘ sheet — null on older reports. */
    val sourceInfo: SourceInfo? = null,
) {
    fun withGrams(g: Double): PlateItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(
            grams = g, calories = calories * k, proteinG = proteinG * k, carbsG = carbsG * k, fatG = fatG * k, micros = micros.mapValues { it.value * k },
            gramsLow = gramsLow?.let { it * k }, gramsHigh = gramsHigh?.let { it * k },
        )
    }

    fun toMealItem() = MealItem(
        foodId = foodId, name = name, grams = grams, calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
        source = source, confidence = when (confidence) { "high" -> 0.9; "medium" -> 0.6; else -> 0.3 }, micros = micros, cookedIn = cookedIn,
        variants = variants, sourceInfo = sourceInfo,
    )

    /** "150 g (120-190)", or just "150 g" when there's no range. */
    fun gramsRangeLabel(): String {
        val g = Math.round(grams)
        val lo = gramsLow; val hi = gramsHigh
        if (lo == null || hi == null || hi <= lo) return "$g g"
        return "$g g (${Math.round(lo)}–${Math.round(hi)})"
    }

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
            gramsLow = if (o.has("grams_low") && !o.isNull("grams_low")) o.optDouble("grams_low") else null,
            gramsHigh = if (o.has("grams_high") && !o.isNull("grams_high")) o.optDouble("grams_high") else null,
            uncertainties = o.optJSONArray("uncertainties")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            variants = FoodVariant.list(o.optJSONArray("variants")),
            sourceInfo = SourceInfo.from(o.optJSONObject("source_info")),
        )
    }
}

/** v2.8: one clarifying question the server can ask after a plate scan, with a fixed effect
 *  vocabulary the client applies deterministically (see util/ScanFollowUp.kt). */
data class FollowUpOption(val label: String, val effect: String)
data class FollowUp(val question: String, val options: List<FollowUpOption>) {
    companion object {
        fun from(o: JSONObject?): FollowUp? {
            if (o == null) return null
            val question = o.optString("question").trim()
            val options = (o.optJSONArray("options") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { i -> val x = arr.getJSONObject(i); FollowUpOption(x.optString("label"), x.optString("effect")) }
            }.filter { it.label.isNotBlank() && it.effect.isNotBlank() }
            if (question.isBlank() || options.size < 2) return null
            return FollowUp(question, options)
        }
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
    /** v2.8: one optional clarifying question, shown as quick-reply chips. */
    val followUp: FollowUp? = null,
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
                followUp = FollowUp.from(o.optJSONObject("follow_up")),
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
data class Squad(
    val id: String, val name: String, val code: String, val ownerId: String,
    // ---- v2.6 (null / defaults until the columns exist) ----
    val description: String = "",
    /** A preset key from SQUAD_ICONS, or "photo:<path in group-photos>" for an uploaded picture. */
    val icon: String? = null,
    val coverUrl: String? = null,
    val tags: List<String> = emptyList(),
    /** open | request */
    val joinPolicy: String = "open",
) {
    val isPrivate: Boolean get() = joinPolicy == "request"
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = Squad(
            o.getString("id"), o.optString("name"), o.optString("code"), o.optString("owner_id"),
            description = o.s("description").orEmpty(),
            icon = o.s("icon"),
            coverUrl = o.s("cover_url"),
            tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } } ?: emptyList(),
            joinPolicy = o.s("join_policy") ?: "open",
        )
    }
}

// ---- v2.6: squads v2 (group_posts, leaderboard, members, join requests) ----

/** One row of `bandlog.group_feed(g, before, n)`: a chat message or a feed post, with its author. */
data class GroupPost(
    val id: String,
    val groupId: String,
    val userId: String,
    /** message | meal | workout | pr | photo */
    val kind: String,
    val body: String,
    val refId: String?,
    val photoPath: String?,
    val createdAt: String,
    val authorName: String,
    val authorUsername: String?,
    val authorAvatar: String?,
) {
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = GroupPost(
            id = o.optString("id"), groupId = o.optString("group_id"), userId = o.optString("user_id"),
            kind = o.s("kind") ?: "message", body = o.s("body").orEmpty(), refId = o.s("ref_id"), photoPath = o.s("photo_path"),
            createdAt = o.optString("created_at"),
            authorName = o.s("name") ?: o.s("author_name") ?: "Member",
            authorUsername = o.s("username") ?: o.s("author_username"),
            authorAvatar = o.s("avatar_path") ?: o.s("author_avatar_path"),
        )
    }
}

/** One row of `bandlog.group_leaderboard(g)`. */
data class LeaderRow(
    val userId: String, val name: String, val username: String?, val avatarPath: String?, val flames: Int, val points: Int,
    val rank: Int = 0, val isOwner: Boolean = false,
) {
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = LeaderRow(
            o.optString("user_id"), o.s("name") ?: "Member", o.s("username"), o.s("avatar_path"), o.optInt("flames", 0),
            if (o.has("week_points")) o.optInt("week_points", 0) else o.optInt("points", 0),
            rank = o.optInt("rank", 0), isOwner = o.optBoolean("is_owner", false),
        )
    }
}

/** One row of `bandlog.group_members_detail(g)`. */
data class MemberDetail(
    val userId: String, val name: String, val username: String?, val avatarPath: String?,
    val isOwner: Boolean, val flames: Int, val joinedAt: String,
) {
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = MemberDetail(
            o.s("user_id") ?: o.optString("id"), o.s("name") ?: "Member", o.s("username"), o.s("avatar_path"),
            o.optBoolean("is_owner", false), o.optInt("flames", 0), o.optString("joined_at"),
        )
    }
}

/** A pending row of `bandlog.group_join_requests` (owner view). */
data class JoinRequest(val id: String, val groupId: String, val userId: String, val name: String, val username: String?, val avatarPath: String?, val createdAt: String) {
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = JoinRequest(
            o.optString("id"), o.optString("group_id"), o.optString("user_id"),
            o.s("name") ?: o.s("display_name") ?: "Someone", o.s("username"), o.s("avatar_path"), o.optString("created_at"),
        )
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

// ---- v2.7: squad challenges ----

/** One row of `bandlog.group_challenge_list(g)`: a challenge with my progress and the current leader. */
data class Challenge(
    val id: String,
    /** train_days | protein_days | log_days */
    val kind: String,
    val title: String,
    val targetDays: Int,
    val proteinTarget: Int?,
    val startsOn: String,
    val endsOn: String,
    val createdBy: String,
    val creatorName: String,
    /** upcoming | active | ended (derived server-side) */
    val status: String,
    val myProgress: Int,
    val leaderName: String?,
    val leaderProgress: Int,
    val participants: Int,
    val completedCount: Int,
    /** v2.7 final contract: the #1 row's user id (null on an older list RPC). */
    val leaderUserId: String? = null,
) {
    val isOpen: Boolean get() = status != "ended"
    val lengthDays: Int get() = com.sohum.bandlog.util.ChallengeMath.lengthDays(startsOn, endsOn)

    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = Challenge(
            id = o.optString("id"),
            kind = o.s("kind") ?: "train_days",
            title = o.s("title") ?: "Challenge",
            targetDays = o.optInt("target_days", 1),
            proteinTarget = if (!o.has("protein_target") || o.isNull("protein_target")) null else o.optInt("protein_target"),
            startsOn = o.optString("starts_on").take(10),
            endsOn = o.optString("ends_on").take(10),
            createdBy = o.optString("created_by"),
            creatorName = o.s("creator_name") ?: "Member",
            status = o.s("status") ?: "active",
            myProgress = o.optInt("my_progress", 0),
            leaderName = o.s("leader_name"),
            leaderProgress = o.optInt("leader_progress", 0),
            participants = o.optInt("participants", 0),
            completedCount = o.optInt("completed_count", 0),
            leaderUserId = o.s("leader_user_id"),
        )
    }
}

/** One row of `bandlog.challenge_board(c)`. */
data class ChallengeBoardRow(
    val userId: String, val name: String, val username: String?, val avatarPath: String?,
    val progress: Int, val completed: Boolean, val completedOn: String?, val rank: Int,
) {
    companion object {
        private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
        fun from(o: JSONObject) = ChallengeBoardRow(
            o.optString("user_id"), o.s("name") ?: "Member", o.s("username"), o.s("avatar_path"),
            o.optInt("progress", 0), o.optBoolean("completed", false), o.s("completed_on")?.take(10), o.optInt("rank", 0),
        )
    }
}

/**
 * v2.9 "Which one?" — one option for an ambiguous item (parse / plate-scan `variants`, or
 * /api/food-source). Per 100 g; choosing it re-prices the row at the same grams.
 */
data class FoodVariant(
    val foodId: String,
    val name: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val micros: Map<String, Double> = emptyMap(),
    val source: String? = null,
    /** The curated chip label ("Maida roti"), else null → the row's name. */
    val label: String? = null,
) {
    val chip: String get() = label ?: name

    companion object {
        fun from(o: JSONObject): FoodVariant? {
            val id = o.optString("food_id").ifBlank { return null }
            return FoodVariant(
                foodId = id,
                name = o.optString("name"),
                kcalPer100g = o.optDouble("kcal_per_100g", 0.0),
                proteinPer100g = o.optDouble("protein_per_100g", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                carbsPer100g = o.optDouble("carbs_per_100g", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                fatPer100g = o.optDouble("fat_per_100g", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                micros = MealItem.micros(o.optJSONObject("micros_per_100g")),
                source = if (o.isNull("source")) null else o.optString("source").ifBlank { null },
                label = if (o.isNull("label")) null else o.optString("label").ifBlank { null },
            )
        }

        fun list(a: JSONArray?): List<FoodVariant> = a?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { from(it) } } } ?: emptyList()
    }
}

/** v2.9 "Where's this from?" — mirrors src/lib/sourceInfo.ts. */
data class SourceLink(val label: String, val url: String)
data class SourceInfo(val kind: String, val label: String, val detail: String, val links: List<SourceLink> = emptyList()) {
    companion object {
        fun from(o: JSONObject?): SourceInfo? {
            if (o == null) return null
            val label = o.optString("label").ifBlank { return null }
            val links = o.optJSONArray("links")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val x = arr.optJSONObject(i) ?: return@mapNotNull null
                    val url = x.optString("url")
                    if (url.startsWith("http")) SourceLink(x.optString("label").ifBlank { url }, url) else null
                }
            } ?: emptyList()
            return SourceInfo(o.optString("kind", "custom"), label, o.optString("detail"), links)
        }
    }
}
