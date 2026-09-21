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
) {
    fun toJson(mealId: String, userId: String): JSONObject = JSONObject()
        .put("meal_id", mealId).put("user_id", userId)
        .put("food_id", foodId ?: JSONObject.NULL).put("name", name).put("grams", grams)
        .put("calories", calories).put("protein_g", proteinG).put("carbs_g", carbsG).put("fat_g", fatG)
        .put("source", source).put("confidence", confidence ?: JSONObject.NULL)

    /** Re-price after the user edits grams (scales linearly). */
    fun withGrams(g: Double): MealItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(grams = g, calories = calories * k, proteinG = proteinG * k, carbsG = carbsG * k, fatG = fatG * k)
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
        )
    }
}

data class Meal(
    val id: String,
    val date: String,
    val rawText: String,
    val createdAt: String,
    val items: List<MealItem>,
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
            )
        }
    }
}

data class Profile(
    val weeklyWorkoutTarget: Int = 3,
    val proteinTargetG: Int = 120,
    val calorieTarget: Int = 2200,
) {
    companion object {
        fun from(o: JSONObject) = Profile(
            weeklyWorkoutTarget = o.optInt("weekly_workout_target", 3),
            proteinTargetG = o.optInt("protein_target_g", 120),
            calorieTarget = o.optInt("calorie_target", 2200),
        )
    }
}

data class ParseResult(val items: List<MealItem>, val assumptions: List<String>, val unparsed: List<String>)

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
