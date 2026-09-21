package com.sohum.bandlog.data

import com.sohum.bandlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/**
 * PostgREST calls against the `bandlog` schema of the shared Supabase project, plus the meal
 * parser hosted by the web app. Every request carries the user's access token so RLS applies.
 */
object Api {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private const val SCHEMA = "bandlog"
    private fun json(s: String) = s.toRequestBody("application/json".toMediaType())

    private suspend fun rest(path: String): Request.Builder {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        return Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", key)
            .header("Authorization", "Bearer $token")
            .header("Accept-Profile", SCHEMA)
            .header("Content-Profile", SCHEMA)
    }

    private fun run(r: Request, label: String): String = client.newCall(r).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            val msg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
            throw ApiException("$label failed (${res.code})${if (msg.isNotBlank()) ": $msg" else ""}")
        }
        body
    }

    // ---- profile ----

    suspend fun profile(): Profile = withContext(Dispatchers.IO) {
        val body = run(rest("profiles?select=weekly_workout_target,protein_target_g,calorie_target&limit=1").get().build(), "Load profile")
        val arr = JSONArray(body)
        if (arr.length() == 0) Profile() else Profile.from(arr.getJSONObject(0))
    }

    suspend fun saveProfile(p: Profile) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject().put("id", uid)
            .put("weekly_workout_target", p.weeklyWorkoutTarget)
            .put("protein_target_g", p.proteinTargetG)
            .put("calorie_target", p.calorieTarget).toString()
        run(rest("profiles").header("Prefer", "resolution=merge-duplicates").post(json(payload)).build(), "Save targets")
        Unit
    }

    // ---- workouts ----

    suspend fun workouts(from: String, to: String): List<Workout> = withContext(Dispatchers.IO) {
        val body = run(
            rest("workouts?select=id,date,muscles,band_level,resistance_kg,minutes,exercises,notes&date=gte.$from&date=lte.$to&order=date.desc,created_at.desc").get().build(),
            "Load workouts",
        )
        val arr = JSONArray(body)
        (0 until arr.length()).map { Workout.from(arr.getJSONObject(it)) }
    }

    suspend fun saveWorkout(
        id: String?, date: String, muscles: List<String>, bandLevel: String,
        resistanceKg: Double?, minutes: Int?, exercises: String, notes: String,
    ) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject()
            .put("user_id", uid).put("date", date)
            .put("muscles", JSONArray(muscles)).put("band_level", bandLevel)
            .put("resistance_kg", resistanceKg ?: JSONObject.NULL).put("minutes", minutes ?: JSONObject.NULL)
            .put("exercises", exercises).put("notes", notes)
        if (id == null) {
            run(rest("workouts").post(json(payload.toString())).build(), "Save workout")
        } else {
            run(rest("workouts?id=eq.$id").patch(json(payload.toString())).build(), "Update workout")
        }
        Unit
    }

    suspend fun deleteWorkout(id: String) = withContext(Dispatchers.IO) {
        run(rest("workouts?id=eq.$id").delete().build(), "Delete workout"); Unit
    }

    // ---- meals ----

    suspend fun meals(from: String, to: String): List<Meal> = withContext(Dispatchers.IO) {
        val sel = "id,date,raw_text,created_at,meal_items(id,food_id,name,grams,calories,protein_g,carbs_g,fat_g,source,confidence)"
        val body = run(rest("meals?select=$sel&date=gte.$from&date=lte.$to&order=created_at.desc").get().build(), "Load meals")
        val arr = JSONArray(body)
        (0 until arr.length()).map { Meal.from(arr.getJSONObject(it)) }
    }

    suspend fun saveMeal(date: String, rawText: String, items: List<MealItem>) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val mealPayload = JSONObject().put("user_id", uid).put("date", date).put("raw_text", rawText).toString()
        val created = run(rest("meals").header("Prefer", "return=representation").post(json(mealPayload)).build(), "Save meal")
        val mealId = JSONArray(created).getJSONObject(0).getString("id")
        if (items.isNotEmpty()) {
            val arr = JSONArray().apply { items.forEach { put(it.toJson(mealId, uid)) } }
            run(rest("meal_items").post(json(arr.toString())).build(), "Save meal items")
        }
        Unit
    }

    suspend fun deleteMeal(id: String) = withContext(Dispatchers.IO) {
        run(rest("meals?id=eq.$id").delete().build(), "Delete meal"); Unit
    }

    // ---- meal parsing (web app → Haiku) ----

    suspend fun parseMeal(text: String): ParseResult = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val apiBase = BuildConfig.API_BASE.trimEnd('/')
        if (apiBase.isBlank()) throw ApiException("API_BASE is not set in this build")
        val r = Request.Builder().url("$apiBase/api/parse-meal")
            .header("Authorization", "Bearer $token")
            .post(json(JSONObject().put("text", text).toString())).build()
        val body = client.newCall(r).execute().use { res ->
            val b = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(b).optString("error") }.getOrNull().orEmpty()
                throw ApiException(if (msg.isNotBlank()) msg else "Parse failed (${res.code})")
            }
            b
        }
        val o = JSONObject(body)
        val items = o.optJSONArray("items") ?: JSONArray()
        fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        ParseResult(
            items = (0 until items.length()).map { MealItem.from(items.getJSONObject(it)) },
            assumptions = strings("assumptions"),
            unparsed = strings("unparsed"),
        )
    }
}
