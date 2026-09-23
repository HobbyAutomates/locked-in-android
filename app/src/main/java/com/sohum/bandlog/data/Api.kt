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

    private const val PROFILE_COLS =
        "weekly_workout_target,protein_target_g,calorie_target,name,dob,gender,height_cm,weight_kg," +
            "goal_weight_kg,goal_type,goal_speed_kg_wk,step_goal,carb_target_g,fat_target_g,reminders,lens_default,share_stats"

    suspend fun profile(): Profile = withContext(Dispatchers.IO) {
        val body = run(rest("profiles?select=$PROFILE_COLS&limit=1").get().build(), "Load profile")
        val arr = JSONArray(body)
        if (arr.length() == 0) Profile() else Profile.from(arr.getJSONObject(0))
    }

    /** Upsert on the profile's PK; every column round-trips so a partial edit never drops the rest. */
    suspend fun saveProfile(p: Profile) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject().put("id", uid)
            .put("weekly_workout_target", p.weeklyWorkoutTarget)
            .put("protein_target_g", p.proteinTargetG)
            .put("calorie_target", p.calorieTarget)
            .put("name", p.name.ifBlank { JSONObject.NULL })
            .put("dob", p.dob ?: JSONObject.NULL)
            .put("gender", p.gender ?: JSONObject.NULL)
            .put("height_cm", p.heightCm ?: JSONObject.NULL)
            .put("weight_kg", p.weightKg ?: JSONObject.NULL)
            .put("goal_weight_kg", p.goalWeightKg ?: JSONObject.NULL)
            .put("goal_type", p.goalType)
            .put("goal_speed_kg_wk", p.goalSpeedKgWk)
            .put("step_goal", p.stepGoal)
            .put("carb_target_g", p.carbTargetGSet ?: JSONObject.NULL)
            .put("fat_target_g", p.fatTargetGSet ?: JSONObject.NULL)
            .put("reminders", if (p.remindersJson.isBlank()) JSONObject.NULL else JSONObject(p.remindersJson))
            .put("lens_default", p.lensDefault.ifBlank { "protein" })
            .put("share_stats", p.shareStats)
            .toString()
        run(rest("profiles").header("Prefer", "resolution=merge-duplicates").post(json(payload)).build(), "Save profile")
        Unit
    }

    // ---- weight log ----

    suspend fun weights(): List<WeightEntry> = withContext(Dispatchers.IO) {
        val body = run(rest("weight_log?select=id,date,weight_kg,note&order=date.desc,created_at.desc&limit=400").get().build(), "Load weight history")
        val arr = JSONArray(body)
        (0 until arr.length()).map { WeightEntry.from(arr.getJSONObject(it)) }
    }

    /** Logs a weigh-in and mirrors it onto `profiles.weight_kg` so every screen agrees. */
    suspend fun logWeight(date: String, kg: Double, note: String) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject().put("user_id", uid).put("date", date).put("weight_kg", kg)
            .put("note", note.ifBlank { JSONObject.NULL }).toString()
        run(rest("weight_log").post(json(payload)).build(), "Log weight")
        run(rest("profiles?id=eq.$uid").patch(json(JSONObject().put("weight_kg", kg).toString())).build(), "Update weight")
        Unit
    }

    suspend fun deleteWeight(id: String) = withContext(Dispatchers.IO) {
        run(rest("weight_log?id=eq.$id").delete().build(), "Delete weigh-in"); Unit
    }

    // ---- badge totals ----

    /** Every workout date ever (badges need the longest run, not just the 120-day window). */
    suspend fun allWorkoutDates(): List<String> = withContext(Dispatchers.IO) {
        val body = run(rest("workouts?select=date&order=date.asc").get().build(), "Load workout history")
        val arr = JSONArray(body)
        (0 until arr.length()).map { arr.getJSONObject(it).getString("date") }
    }

    /** Lifetime row count via PostgREST's `Content-Range` header (no rows transferred). */
    suspend fun countRows(table: String): Int = withContext(Dispatchers.IO) {
        val req = rest("$table?select=id").header("Prefer", "count=exact").header("Range", "0-0").get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) 0 else res.header("Content-Range")?.substringAfter('/')?.trim()?.toIntOrNull() ?: 0
        }
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

    /** Inserts or updates a workout and returns its id. */
    suspend fun saveWorkout(
        id: String?, date: String, muscles: List<String>, bandLevel: String,
        resistanceKg: Double?, minutes: Int?, exercises: String, notes: String,
    ): String = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject()
            .put("user_id", uid).put("date", date)
            .put("muscles", JSONArray(muscles)).put("band_level", bandLevel)
            .put("resistance_kg", resistanceKg ?: JSONObject.NULL).put("minutes", minutes ?: JSONObject.NULL)
            .put("exercises", exercises).put("notes", notes)
        if (id == null) {
            val created = run(rest("workouts").header("Prefer", "return=representation").post(json(payload.toString())).build(), "Save workout")
            JSONArray(created).getJSONObject(0).getString("id")
        } else {
            run(rest("workouts?id=eq.$id").patch(json(payload.toString())).build(), "Update workout")
            id
        }
    }

    suspend fun deleteWorkout(id: String) = withContext(Dispatchers.IO) {
        run(rest("workouts?id=eq.$id").delete().build(), "Delete workout"); Unit
    }

    // ---- exercise log (calories burned) ----

    private const val EXERCISE_COLS = "id,date,activity_code,name,minutes,intensity,kcal,source,note,created_at"

    suspend fun exercises(from: String, to: String): List<ExerciseEntry> = withContext(Dispatchers.IO) {
        val body = run(rest("exercise_log?select=$EXERCISE_COLS&date=gte.$from&date=lte.$to&order=date.desc,created_at.desc").get().build(), "Load exercise")
        val arr = JSONArray(body)
        (0 until arr.length()).map { ExerciseEntry.from(arr.getJSONObject(it)) }
    }

    suspend fun saveExercise(
        date: String, activityCode: String?, name: String, minutes: Int, intensity: String, kcal: Double, source: String, note: String = "",
    ) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject().put("user_id", uid).put("date", date)
            .put("activity_code", activityCode ?: JSONObject.NULL).put("name", name)
            .put("minutes", minutes.coerceAtLeast(1)).put("intensity", intensity).put("kcal", kcal)
            .put("source", source).put("note", note).toString()
        run(rest("exercise_log").post(json(payload)).build(), "Log exercise"); Unit
    }

    suspend fun deleteExercise(id: String) = withContext(Dispatchers.IO) {
        run(rest("exercise_log?id=eq.$id").delete().build(), "Delete exercise"); Unit
    }

    /** Removes the auto-burn row a band workout wrote (its note holds the workout id). */
    suspend fun deleteWorkoutBurn(workoutId: String) = withContext(Dispatchers.IO) {
        run(rest("exercise_log?source=eq.workout&note=eq.$workoutId").delete().build(), "Delete workout burn"); Unit
    }

    /** Codes shown before the user types anything: the everyday picks. */
    private const val POPULAR_ACTIVITIES = "LI-17190,LI-17200,LI-17133,12150,LI-15150,LI-15030,LI-02101,LI-15551,01015,02020,02040,LI-05010"

    /** Activity search: name / description substring or an exact tag. Blank query → the popular set. */
    suspend fun searchActivities(q: String): List<Activity> = withContext(Dispatchers.IO) {
        val term = q.trim().lowercase().replace(Regex("[^a-z0-9 -]"), "")
        val path = if (term.isBlank()) "activities?select=*&code=in.($POPULAR_ACTIVITIES)&order=category.asc,met.asc"
        else {
            val enc = java.net.URLEncoder.encode("(name.ilike.*$term*,description.ilike.*$term*,tags.cs.{$term})", "UTF-8").replace("+", "%20")
            "activities?select=*&or=$enc&order=name.asc,met.asc&limit=40"
        }
        val body = run(rest(path).get().build(), "Search activities")
        val arr = JSONArray(body)
        val rows = (0 until arr.length()).map { Activity.from(arr.getJSONObject(it)) }
        if (term.isBlank()) rows.sortedBy { POPULAR_ACTIVITIES.split(",").indexOf(it.code).let { i -> if (i < 0) 99 else i } } else rows
    }

    /** "played badminton for an hour then walked home" → activities with minutes, intensity and kcal. */
    suspend fun describeExercise(text: String): List<DescribedExercise> = withContext(Dispatchers.IO) {
        val o = api("describe-exercise", JSONObject().put("text", text), "Describe exercise", timeoutSec = 90)
        val arr = o.optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).map { DescribedExercise.from(arr.getJSONObject(it)) }
    }

    // ---- meals ----

    suspend fun meals(from: String, to: String): List<Meal> = withContext(Dispatchers.IO) {
        val sel = "id,date,raw_text,created_at,photo_path,meal_items(id,food_id,name,grams,calories,protein_g,carbs_g,fat_g,source,confidence,micros,unit,servings,cooked_in)"
        val body = run(rest("meals?select=$sel&date=gte.$from&date=lte.$to&order=created_at.desc").get().build(), "Load meals")
        val arr = JSONArray(body)
        (0 until arr.length()).map { Meal.from(arr.getJSONObject(it)) }
    }

    suspend fun saveMeal(date: String, rawText: String, items: List<MealItem>, photoPath: String? = null) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val mealPayload = JSONObject().put("user_id", uid).put("date", date).put("raw_text", rawText).put("photo_path", photoPath ?: JSONObject.NULL).toString()
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

    // ---- food presets + search (v1.9) ----

    /** Every Indian food preset with its foods row joined, in category sort order. */
    suspend fun presets(): List<FoodPreset> = withContext(Dispatchers.IO) {
        val sel = "id,food_id,label,label_hi,category,servings,default_serving,sort,icon,foods(name,calories,protein_g,carbs_g,fat_g,micros)"
        val body = run(rest("food_presets?select=$sel&order=sort.asc").get().build(), "Load presets")
        val arr = JSONArray(body)
        (0 until arr.length()).mapNotNull { FoodPreset.from(arr.getJSONObject(it)) }
    }

    /** Trigram search over bandlog.foods (the same RPC the parser uses). Hinglish and Devanagari both match. */
    suspend fun searchFoods(q: String, n: Int = 14): List<FoodHit> = withContext(Dispatchers.IO) {
        val key = q.trim().lowercase().replace(Regex("\\s+"), " ")
        if (key.length < 2) return@withContext emptyList()
        val payload = JSONObject().put("q", key).put("n", n).toString()
        val body = run(rest("rpc/search_foods").post(json(payload)).build(), "Search foods")
        val arr = JSONArray(body)
        (0 until arr.length()).map { FoodHit.from(arr.getJSONObject(it)) }
    }

    // ---- meal parsing (web app → Haiku) ----

    private suspend fun api(path: String, payload: JSONObject, label: String, timeoutSec: Long = 60): JSONObject {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val apiBase = BuildConfig.API_BASE.trimEnd('/')
        if (apiBase.isBlank()) throw ApiException("API_BASE is not set in this build")
        val r = Request.Builder().url("$apiBase/api/$path").header("Authorization", "Bearer $token").post(json(payload.toString())).build()
        val c = if (timeoutSec == 60L) client else client.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).readTimeout(timeoutSec, TimeUnit.SECONDS).build()
        val body = c.newCall(r).execute().use { res ->
            val b = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(b).optString("error") }.getOrNull().orEmpty()
                throw ApiException(if (msg.isNotBlank()) msg else "$label failed (${res.code})")
            }
            b
        }
        return JSONObject(body)
    }

    private suspend fun apiGet(path: String, label: String): JSONObject {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val apiBase = BuildConfig.API_BASE.trimEnd('/')
        if (apiBase.isBlank()) throw ApiException("API_BASE is not set in this build")
        val r = Request.Builder().url("$apiBase/api/$path").header("Authorization", "Bearer $token").get().build()
        val body = client.newCall(r).execute().use { res ->
            val b = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(b).optString("error") }.getOrNull().orEmpty()
                throw ApiException(if (msg.isNotBlank()) msg else "$label failed (${res.code})")
            }
            b
        }
        return JSONObject(body)
    }

    /** [correction] + [previous] = the "Fix issue" path: re-parse with the user's note about what was wrong. */
    suspend fun parseMeal(text: String, correction: String? = null, previous: List<MealItem>? = null): ParseResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("text", text)
        if (!correction.isNullOrBlank()) {
            payload.put("correction", correction)
            payload.put("previous", JSONArray().apply { previous?.forEach { put(JSONObject().put("name", it.name).put("grams", it.grams).put("calories", it.calories).put("protein_g", it.proteinG)) } })
        }
        val o = api("parse-meal", payload, "Parse")
        val items = o.optJSONArray("items") ?: JSONArray()
        fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        ParseResult(
            items = (0 until items.length()).map { MealItem.from(items.getJSONObject(it)) },
            assumptions = strings("assumptions"),
            unparsed = strings("unparsed"),
        )
    }

    /** 👍/👎 on a parsed meal. */
    suspend fun feedback(rating: String, rawText: String, mealId: String?, correction: String = "") = withContext(Dispatchers.IO) {
        api("feedback", JSONObject().put("rating", rating).put("raw_text", rawText).put("meal_id", mealId ?: JSONObject.NULL).put("correction", correction), "Feedback"); Unit
    }

    /**
     * Label text (read on this phone by ML Kit) → verdict report. [jpegBase64] is only sent when
     * the OCR came up short, in which case the server falls back to reading the photo itself.
     */
    suspend fun scanLabel(text: String, note: String, jpegBase64: String? = null, lens: String = "protein"): LabelReport = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("text", text).put("note", note).put("lens", lens)
        if (jpegBase64 != null) payload.put("image", jpegBase64).put("media_type", "image/jpeg")
        LabelReport.from(api("scan-label", payload, "Scan", timeoutSec = 120))
    }

    /** EAN/UPC → Open Food Facts → the same report as a label. Null when the product isn't on OFF. */
    suspend fun scanBarcode(barcode: String, lens: String, note: String = ""): LabelReport? = withContext(Dispatchers.IO) {
        val o = api("scan-barcode", JSONObject().put("barcode", barcode).put("lens", lens).put("note", note), "Barcode", timeoutSec = 120)
        if (o.has("found") && !o.optBoolean("found", true)) null else LabelReport.from(o)
    }

    /** A plate photo (JPEG, ≤ 1600 px) → per-item grams, macros and micros. */
    suspend fun photoMeal(jpegBase64: String, note: String): PlateEstimate = withContext(Dispatchers.IO) {
        PlateEstimate.from(api("photo-meal", JSONObject().put("image", jpegBase64).put("media_type", "image/jpeg").put("note", note), "Photo", timeoutSec = 120))
    }

    // ---- scan history ----

    /** Latest scans, newest first. The score and OFF image come straight out of the report JSON. */
    suspend fun scanHistory(limit: Int = 30): List<ScanHistoryItem> = withContext(Dispatchers.IO) {
        val sel = "id,kind,lens,product,verdict,created_at,image_path,score:report->infographic->>score_out_of_10,image_url:report->>image_url"
        val body = run(rest("label_scans?select=$sel&order=created_at.desc&limit=$limit").get().build(), "Load scan history")
        val arr = JSONArray(body)
        (0 until arr.length()).map { ScanHistoryItem.from(arr.getJSONObject(it)) }
    }

    /** The full stored report of one scan, with kind/lens/id merged in (and a signed photo URL for plate scans). */
    suspend fun scanReport(id: String): JSONObject = withContext(Dispatchers.IO) {
        apiGet("scans/$id", "Open scan")
    }

    suspend fun deleteScan(id: String) = withContext(Dispatchers.IO) {
        run(rest("label_scans?id=eq.$id").delete().build(), "Delete scan"); Unit
    }

    // ---- meal photos (Supabase Storage, private bucket, one folder per user) ----

    /** Uploads a JPEG to meal-photos/<user>/<uuid>.jpg with the user's own token; returns the path. */
    suspend fun uploadMealPhoto(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val path = "$uid/${java.util.UUID.randomUUID()}.jpg"
        val req = Request.Builder().url("$base/storage/v1/object/meal-photos/$path")
            .header("apikey", key).header("Authorization", "Bearer $token")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(req, "Upload photo")
        path
    }

    /** A 1-hour signed URL for a meal-photos path, or null when signing fails. */
    suspend fun signedPhotoUrl(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseAuth.ensureFresh()
            val token = Session.accessToken ?: throw AuthException("Not signed in")
            val req = Request.Builder().url("$base/storage/v1/object/sign/meal-photos/$path")
                .header("apikey", key).header("Authorization", "Bearer $token")
                .post(json(JSONObject().put("expiresIn", 3600).toString())).build()
            val signed = JSONObject(run(req, "Sign photo")).optString("signedURL")
            if (signed.isBlank()) null else "$base/storage/v1${if (signed.startsWith("/")) signed else "/$signed"}"
        }.getOrNull()
    }

    /** Fetches an image (signed Storage URL or an Open Food Facts picture) as a bitmap; null on any failure. */
    suspend fun fetchBitmap(url: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", "LockedIn/1.9").get().build()).execute().use { res ->
                if (!res.isSuccessful) null else res.body?.bytes()?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }.getOrNull()
    }

    // ---- saved meals (one-tap repeat dinners) ----

    suspend fun savedMeals(): List<SavedMeal> = withContext(Dispatchers.IO) {
        val body = run(rest("saved_meals?select=id,name,items,calories,protein_g&order=created_at.desc").get().build(), "Load saved meals")
        val arr = JSONArray(body)
        (0 until arr.length()).map { SavedMeal.from(arr.getJSONObject(it)) }
    }

    suspend fun saveSavedMeal(name: String, items: List<MealItem>) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val arr = JSONArray().apply {
            items.forEach { put(JSONObject().put("food_id", it.foodId ?: JSONObject.NULL).put("name", it.name).put("grams", it.grams).put("calories", it.calories).put("protein_g", it.proteinG).put("carbs_g", it.carbsG).put("fat_g", it.fatG).put("source", it.source)) }
        }
        val payload = JSONObject().put("user_id", uid).put("name", name).put("items", arr).put("calories", items.sumOf { it.calories }).put("protein_g", items.sumOf { it.proteinG }).toString()
        run(rest("saved_meals").post(json(payload)).build(), "Save meal template"); Unit
    }

    suspend fun deleteSavedMeal(id: String) = withContext(Dispatchers.IO) { run(rest("saved_meals?id=eq.$id").delete().build(), "Delete saved meal"); Unit }
}
