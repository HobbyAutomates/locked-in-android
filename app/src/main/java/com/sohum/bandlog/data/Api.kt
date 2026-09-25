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
            android.util.Log.w("LockedIn", "$label failed (${res.code}) ${r.method} ${r.url.encodedPath}: ${body.take(800)}")
            val msg = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } }.getOrNull().orEmpty()
            throw ApiException("$label failed (${res.code})${if (msg.isNotBlank()) ": $msg" else ""}")
        }
        body
    }

    // ---- profile ----

    private const val PROFILE_COLS =
        "weekly_workout_target,protein_target_g,calorie_target,name,dob,gender,height_cm,weight_kg," +
            "goal_weight_kg,goal_type,goal_speed_kg_wk,step_goal,carb_target_g,fat_target_g,reminders,lens_default,share_stats,avatar_path"

    suspend fun profile(): Profile = withContext(Dispatchers.IO) {
        // v2.3: select=* so a column the web agent hasn't added yet can never break the read.
        val body = runCatching { run(rest("profiles?select=*&limit=1").get().build(), "Load profile") }
            .getOrElse { run(rest("profiles?select=$PROFILE_COLS&limit=1").get().build(), "Load profile") }
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
            .put("avatar_path", p.avatarPath ?: JSONObject.NULL)
            .toString()
        run(rest("profiles").header("Prefer", "resolution=merge-duplicates").post(json(payload)).build(), "Save profile")
        // v2.3 columns go in a separate PATCH so an older database (columns missing) still saves the rest.
        val extras = JSONObject()
            .put("fiber_target", p.fiberTarget).put("sugar_target", p.sugarTarget).put("water_goal_ml", p.waterGoalMl)
        p.addBurnedToGoal?.let { extras.put("add_burned_to_goal", it) }
        p.rolloverCalories?.let { extras.put("rollover_calories", it) }
        runCatching { run(rest("profiles?id=eq.$uid").header("Prefer", "return=minimal").patch(json(extras.toString())).build(), "Save goals") }
        Unit
    }

    /** Patches v2.3 profile columns; false when the database doesn't have them yet. */
    suspend fun patchProfile(fields: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        runCatching { run(rest("profiles?id=eq.$uid").header("Prefer", "return=minimal").patch(json(fields.toString())).build(), "Save preference") }.isSuccess
    }

    // ---- weight log ----

    suspend fun weights(): List<WeightEntry> = withContext(Dispatchers.IO) {
        val body = run(rest("weight_log?select=*&order=date.desc,created_at.desc&limit=400").get().build(), "Load weight history")
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

    /** v2.8: edits a weigh-in (kg, date, note); [mirror] also puts the kg on the profile (the newest weigh-in). */
    suspend fun updateWeight(id: String, date: String, kg: Double, note: String, mirror: Boolean) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject().put("date", date).put("weight_kg", kg).put("note", note.ifBlank { JSONObject.NULL }).toString()
        run(rest("weight_log?id=eq.$id").header("Prefer", "return=minimal").patch(json(payload)).build(), "Update weigh-in")
        if (mirror) run(rest("profiles?id=eq.$uid").patch(json(JSONObject().put("weight_kg", kg).toString())).build(), "Update weight")
        Unit
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
            // select=* so the v2.5 kind / exercises_json columns come through when they exist (and nothing breaks when they don't).
            rest("workouts?select=*&date=gte.$from&date=lte.$to&order=date.desc,created_at.desc").get().build(),
            "Load workouts",
        )
        val arr = JSONArray(body)
        (0 until arr.length()).map { Workout.from(arr.getJSONObject(it)) }
    }

    /**
     * Inserts or updates a workout and returns its id. v2.5: [kind] (bands | gym | bodyweight |
     * cardio | sport | yoga) and, for gym / bodyweight, [lifts] -> exercises_json
     * [{"name": "Bench press", "sets": [{"kg": 40, "reps": 8}]}]. Non-band kinds leave the band
     * columns alone (band_level keeps its column default, resistance_kg null). If the new columns
     * aren't there yet the row is saved without them, the lifts written out in `exercises`.
     */
    suspend fun saveWorkout(
        id: String?, date: String, muscles: List<String>, bandLevel: String,
        resistanceKg: Double?, minutes: Int?, exercises: String, notes: String,
        kind: String = Workout.BANDS, lifts: List<Lift>? = null,
    ): String = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val bands = kind == Workout.BANDS
        fun payload(withNew: Boolean): JSONObject {
            val o = JSONObject()
                .put("user_id", uid).put("date", date)
                .put("muscles", JSONArray(muscles))
                .put("minutes", minutes ?: JSONObject.NULL)
                .put("exercises", if (!withNew && !lifts.isNullOrEmpty() && exercises.isBlank()) Lift.summaryText(lifts) else exercises)
                .put("notes", notes)
            if (bands) o.put("band_level", bandLevel).put("resistance_kg", resistanceKg ?: JSONObject.NULL)
            else o.put("resistance_kg", JSONObject.NULL)
            if (withNew) {
                o.put("kind", kind)
                o.put("exercises_json", lifts?.let { Lift.toJson(it) } ?: JSONObject.NULL)
            }
            return o
        }
        suspend fun send(p: JSONObject): String = if (id == null) {
            val created = run(rest("workouts").header("Prefer", "return=representation").post(json(p.toString())).build(), "Save workout")
            JSONArray(created).getJSONObject(0).getString("id")
        } else {
            run(rest("workouts?id=eq.$id").patch(json(p.toString())).build(), "Update workout")
            id
        }
        try { send(payload(withNew = true)) }
        catch (e: ApiException) {
            val m = e.message.orEmpty()
            if ("column" in m || "kind" in m || "exercises_json" in m || "PGRST204" in m) send(payload(withNew = false)) else throw e
        }
    }

    suspend fun deleteWorkout(id: String) = withContext(Dispatchers.IO) {
        run(rest("workouts?id=eq.$id").delete().build(), "Delete workout"); Unit
    }

    // ---- exercise log (calories burned) ----

    suspend fun exercises(from: String, to: String): List<ExerciseEntry> = withContext(Dispatchers.IO) {
        // select=* keeps the v2.3 columns (started_at, intensity_pct, distance_km, steps) optional.
        val body = run(rest("exercise_log?select=*&date=gte.$from&date=lte.$to&order=date.desc,created_at.desc").get().build(), "Load exercise")
        val arr = JSONArray(body)
        (0 until arr.length()).map { ExerciseEntry.from(arr.getJSONObject(it)) }
    }

    /** v2.3 Google-Fit-style extras; every field optional. */
    data class ExerciseExtras(val startedAt: String? = null, val intensityPct: Int? = null, val distanceKm: Double? = null, val steps: Int? = null) {
        val empty: Boolean get() = startedAt == null && intensityPct == null && distanceKm == null && steps == null
    }

    suspend fun saveExercise(
        date: String, activityCode: String?, name: String, minutes: Int, intensity: String, kcal: Double, source: String, note: String = "",
        extras: ExerciseExtras = ExerciseExtras(),
    ) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val base = JSONObject().put("user_id", uid).put("date", date)
            .put("activity_code", activityCode ?: JSONObject.NULL).put("name", name)
            .put("minutes", minutes.coerceAtLeast(1)).put("intensity", intensity).put("kcal", kcal)
            .put("source", source).put("note", note)
        if (extras.empty) {
            run(rest("exercise_log").post(json(base.toString())).build(), "Log exercise")
        } else {
            val full = JSONObject(base.toString())
            extras.startedAt?.let { full.put("started_at", it) }
            extras.intensityPct?.let { full.put("intensity_pct", it) }
            extras.distanceKm?.let { full.put("distance_km", it) }
            extras.steps?.let { full.put("steps", it) }
            try {
                run(rest("exercise_log").post(json(full.toString())).build(), "Log exercise")
            } catch (e: ApiException) {
                // Columns not there yet (older database): save the burn without the extras.
                android.util.Log.w("LockedIn", "Exercise extras rejected, saving without: ${e.message}")
                run(rest("exercise_log").post(json(base.toString())).build(), "Log exercise")
            }
        }
        Unit
    }

    // ---- v2.3: water ----

    suspend fun water(from: String, to: String): List<WaterEntry> = withContext(Dispatchers.IO) {
        val body = run(rest("water_log?select=*&date=gte.$from&date=lte.$to&order=created_at.desc").get().build(), "Load water")
        val arr = JSONArray(body)
        (0 until arr.length()).map { WaterEntry.from(arr.getJSONObject(it)) }
    }

    /** v2.6: one row per add, tagged with its [vessel] (glass | bottle | large | custom | reminder | widget) when the column exists. */
    suspend fun logWater(date: String, ml: Int, vessel: String? = null) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val base = JSONObject().put("user_id", uid).put("date", date).put("ml", ml)
        if (vessel == null) { run(rest("water_log").header("Prefer", "return=minimal").post(json(base.toString())).build(), "Log water"); return@withContext }
        try {
            run(rest("water_log").header("Prefer", "return=minimal").post(json(JSONObject(base.toString()).put("vessel", vessel).toString())).build(), "Log water")
        } catch (e: ApiException) {
            val m = e.message.orEmpty()
            if ("vessel" in m || "column" in m || "PGRST204" in m) run(rest("water_log").header("Prefer", "return=minimal").post(json(base.toString())).build(), "Log water") else throw e
        }
        Unit
    }

    /** Today's water total straight from the server (the reminder receiver checks the goal with it). */
    suspend fun waterTotal(date: String): Int = withContext(Dispatchers.IO) {
        val arr = JSONArray(run(rest("water_log?select=ml&date=eq.$date").get().build(), "Load water"))
        (0 until arr.length()).sumOf { arr.getJSONObject(it).optInt("ml", 0) }
    }

    suspend fun deleteWater(id: String) = withContext(Dispatchers.IO) { run(rest("water_log?id=eq.$id").delete().build(), "Delete water"); Unit }

    /** v2.8: changes one drink's amount (and its vessel, when the column exists). */
    suspend fun updateWater(id: String, ml: Int, vessel: String?) = withContext(Dispatchers.IO) {
        val base = JSONObject().put("ml", ml)
        if (vessel == null) { run(rest("water_log?id=eq.$id").header("Prefer", "return=minimal").patch(json(base.toString())).build(), "Update water"); return@withContext }
        try {
            run(rest("water_log?id=eq.$id").header("Prefer", "return=minimal").patch(json(JSONObject(base.toString()).put("vessel", vessel).toString())).build(), "Update water")
        } catch (e: ApiException) {
            val m = e.message.orEmpty()
            if ("vessel" in m || "column" in m || "PGRST204" in m) run(rest("water_log?id=eq.$id").header("Prefer", "return=minimal").patch(json(base.toString())).build(), "Update water") else throw e
        }
        Unit
    }

    // ---- v2.3: progress photos (private bucket progress-photos/<uid>/<date>-<ts>.jpg) ----

    suspend fun progressPhotos(): List<ProgressPhoto> = withContext(Dispatchers.IO) {
        val body = run(rest("progress_photos?select=*&order=date.desc&limit=60").get().build(), "Load progress photos")
        val arr = JSONArray(body)
        (0 until arr.length()).map { ProgressPhoto.from(arr.getJSONObject(it)) }
    }

    /** Uploads a ≤1024 px JPEG with the user's token and records the row; returns the storage path. */
    suspend fun uploadProgressPhoto(jpeg: ByteArray, date: String, note: String = ""): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val path = "$uid/$date-${System.currentTimeMillis()}.jpg"
        val req = Request.Builder().url("$base/storage/v1/object/progress-photos/$path")
            .header("apikey", key).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(req, "Upload photo")
        val row = JSONObject().put("user_id", uid).put("date", date).put("path", path).put("note", if (note.isBlank()) JSONObject.NULL else note).toString()
        run(rest("progress_photos").header("Prefer", "return=minimal").post(json(row)).build(), "Save progress photo")
        path
    }

    suspend fun deleteExercise(id: String) = withContext(Dispatchers.IO) {
        run(rest("exercise_log?id=eq.$id").delete().build(), "Delete exercise"); Unit
    }

    /** v2.8: saves an edited run / activity in place (extras dropped if the columns aren't there yet). */
    suspend fun updateExercise(
        id: String, activityCode: String?, name: String, minutes: Int, intensity: String, kcal: Double, note: String,
        extras: ExerciseExtras = ExerciseExtras(),
    ) = withContext(Dispatchers.IO) {
        val base = JSONObject().put("activity_code", activityCode ?: JSONObject.NULL).put("name", name)
            .put("minutes", minutes.coerceAtLeast(1)).put("intensity", intensity).put("kcal", kcal).put("note", note)
        val full = JSONObject(base.toString())
            .put("started_at", extras.startedAt ?: JSONObject.NULL)
            .put("intensity_pct", extras.intensityPct ?: JSONObject.NULL)
            .put("distance_km", extras.distanceKm ?: JSONObject.NULL)
            .put("steps", extras.steps ?: JSONObject.NULL)
        try {
            run(rest("exercise_log?id=eq.$id").header("Prefer", "return=minimal").patch(json(full.toString())).build(), "Update exercise")
        } catch (e: ApiException) {
            android.util.Log.w("LockedIn", "Exercise extras rejected, updating without: ${e.message}")
            run(rest("exercise_log?id=eq.$id").header("Prefer", "return=minimal").patch(json(base.toString())).build(), "Update exercise")
        }
        Unit
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

    suspend fun saveMeal(date: String, rawText: String, items: List<MealItem>, photoPath: String? = null): String = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val mealPayload = JSONObject().put("user_id", uid).put("date", date).put("raw_text", rawText).put("photo_path", photoPath ?: JSONObject.NULL).toString()
        val created = run(rest("meals").header("Prefer", "return=representation").post(json(mealPayload)).build(), "Save meal")
        val mealId = JSONArray(created).getJSONObject(0).getString("id")
        if (items.isNotEmpty()) {
            val arr = JSONArray().apply { items.forEach { put(it.toJson(mealId, uid)) } }
            run(rest("meal_items").post(json(arr.toString())).build(), "Save meal items")
        }
        mealId
    }

    suspend fun deleteMeal(id: String) = withContext(Dispatchers.IO) {
        run(rest("meals?id=eq.$id").delete().build(), "Delete meal"); Unit
    }

    // ---- food presets + search (v1.9) ----

    /** Every Indian food preset with its foods row joined, in category sort order. */
    suspend fun presets(): List<FoodPreset> = withContext(Dispatchers.IO) {
        val sel = "id,food_id,label,label_hi,category,servings,default_serving,sort,icon,foods(name,calories,protein_g,carbs_g,fat_g,micros)"
        // v2.4: image_url on the preset and its food; falls back to the old columns if not there yet.
        val selImg = "id,food_id,label,label_hi,category,servings,default_serving,sort,icon,image_url,foods(name,calories,protein_g,carbs_g,fat_g,micros,image_url)"
        val body = runCatching { run(rest("food_presets?select=$selImg&order=sort.asc").get().build(), "Load presets") }
            .getOrElse { run(rest("food_presets?select=$sel&order=sort.asc").get().build(), "Load presets") }
        val arr = JSONArray(body)
        (0 until arr.length()).mapNotNull { FoodPreset.from(arr.getJSONObject(it)) }
            .onEach { com.sohum.bandlog.ui.components.FoodImages.register(it.foodId, it.label, it.imageUrl); com.sohum.bandlog.ui.components.FoodImages.register(null, it.foodName, it.imageUrl) }
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
                android.util.Log.w("LockedIn", "$label failed (${res.code}) /api/$path: ${b.take(800)}")
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
                android.util.Log.w("LockedIn", "$label failed (${res.code}) /api/$path: ${b.take(800)}")
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
            water = ParsedWater.from(o.optJSONObject("water")),
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

    /**
     * Latest scans, newest first. The score, one-liner and name fallbacks come straight out of the
     * report JSON (first flagged ingredient for labels, first recognised food for plates).
     */
    suspend fun scanHistory(limit: Int = 30): List<ScanHistoryItem> = withContext(Dispatchers.IO) {
        val sel = "id,kind,lens,product,verdict,created_at,image_path,thumb_path,image_url,report_image_url:report->>image_url," +
            "score:report->infographic->>score_out_of_10,what_it_is:report->>what_it_is," +
            "first_ingredient:report->concerns->0->>ingredient,first_item:report->items->0->>name"
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

    // ---- scan thumbnails (private bucket scan-photos/<uid>/<scan id>.jpg) ----

    /** Uploads a ≤320 px JPEG for a scan and records it on the row. Callers wrap this in runCatching. */
    suspend fun uploadScanThumb(scanId: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val path = "$uid/$scanId.jpg"
        val req = Request.Builder().url("$base/storage/v1/object/scan-photos/$path")
            .header("apikey", key).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(req, "Upload scan thumbnail")
        run(rest("label_scans?id=eq.$scanId").header("Prefer", "return=minimal").patch(json(JSONObject().put("thumb_path", path).toString())).build(), "Save scan thumbnail")
        path
    }

    /** Records the Open Food Facts picture of a barcode scan on its row (the web server does this too). */
    suspend fun setScanImageUrl(scanId: String, url: String) = withContext(Dispatchers.IO) {
        run(rest("label_scans?id=eq.$scanId").header("Prefer", "return=minimal").patch(json(JSONObject().put("image_url", url).toString())).build(), "Save scan image"); Unit
    }

    /** A private Storage object fetched with the user's token (authenticated endpoint); null on any failure. */
    suspend fun fetchStorageBitmap(bucket: String, path: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseAuth.ensureFresh()
            val token = Session.accessToken ?: return@runCatching null
            val req = Request.Builder().url("$base/storage/v1/object/authenticated/$bucket/$path")
                .header("apikey", key).header("Authorization", "Bearer $token").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) { android.util.Log.w("LockedIn", "Storage read $bucket/$path failed (${res.code})"); null }
                else res.body?.bytes()?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }.getOrNull()
    }

    // ---- avatars (public bucket avatars/<uid>/avatar.jpg) ----

    /** Uploads (upserts) the profile picture; returns the path with a cache-busting `?v=` suffix for profiles.avatar_path. */
    suspend fun uploadAvatar(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val req = Request.Builder().url("$base/storage/v1/object/avatars/$uid/avatar.jpg")
            .header("apikey", key).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(req, "Upload photo")
        "$uid/avatar.jpg?v=${System.currentTimeMillis()}"
    }

    /** v2.6: an uploaded squad photo — public avatars/<uid>/squad-<name>.jpg, returned as its public URL for groups.cover_url (matches the web). */
    suspend fun uploadSquadPhoto(jpeg: ByteArray, name: String): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val path = "$uid/$name.jpg"
        val req = Request.Builder().url("$base/storage/v1/object/avatars/$path")
            .header("apikey", key).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(req, "Upload photo")
        "$base/storage/v1/object/public/avatars/$path"
    }

    /** Public URL for a profiles.avatar_path / squad_board.avatar_path value; null when unset. */
    fun avatarUrl(path: String?): String? = path?.trim()?.trimStart('/')?.ifBlank { null }?.let { "$base/storage/v1/object/public/avatars/$it" }

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

    /** Raw bytes of a public image (food pictures); null on any failure. */
    suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", "LockedIn/2.4").get().build()).execute().use { res ->
                if (!res.isSuccessful) null else res.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    /**
     * v2.4: `GET /api/food-image?q=&kind=preset|product|generic` → `{ "url": … | null }`. The first
     * ask for a name can take ~15 s while the web app finds and stores a picture; later ones are cached.
     */
    suspend fun foodImage(q: String, kind: String): String? = withContext(Dispatchers.IO) {
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        val o = apiGet("food-image?q=$enc&kind=$kind", "Food image")
        if (o.isNull("url")) null else o.optString("url").trim().takeIf { it.startsWith("http") }
    }

    // ---- saved meals (one-tap repeat dinners) ----

    suspend fun savedMeals(): List<SavedMeal> = withContext(Dispatchers.IO) {
        val body = runCatching { run(rest("saved_meals?select=id,name,items,calories,protein_g,image_url&order=created_at.desc").get().build(), "Load saved meals") }
            .getOrElse { run(rest("saved_meals?select=id,name,items,calories,protein_g&order=created_at.desc").get().build(), "Load saved meals") }
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

    // ---- v2.0: squads ----

    /** One day of my rollup for the squad board. */
    data class DailyStat(val date: String, val trained: Boolean, val proteinG: Double, val calories: Double, val burned: Double, val meals: Int, val weekStreak: Int)

    /** Upserts my `daily_stats` rows (PK user_id + date). */
    suspend fun upsertDailyStats(rows: List<DailyStat>) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        if (rows.isEmpty()) return@withContext
        fun r1(d: Double) = Math.round(d * 10) / 10.0
        val arr = JSONArray().apply {
            rows.forEach { r ->
                put(
                    JSONObject().put("user_id", uid).put("date", r.date).put("trained", r.trained)
                        .put("protein_g", r1(r.proteinG)).put("calories", r1(r.calories)).put("burned", r1(r.burned))
                        .put("meals", r.meals).put("week_streak", r.weekStreak)
                        .put("updated_at", java.time.Instant.now().toString()),
                )
            }
        }
        run(rest("daily_stats?on_conflict=user_id,date").header("Prefer", "resolution=merge-duplicates,return=minimal").post(json(arr.toString())).build(), "Save rollup")
        Unit
    }

    /** Every squad I'm in, oldest membership first. */
    suspend fun mySquads(): List<Squad> = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val m = JSONArray(run(rest("group_members?select=group_id,joined_at&user_id=eq.$uid&order=joined_at.asc").get().build(), "Load squads"))
        val ids = (0 until m.length()).map { m.getJSONObject(it).getString("group_id") }
        if (ids.isEmpty()) return@withContext emptyList()
        // v2.6: select=* so description / icon / tags / join_policy come through when they exist.
        val g = JSONArray(run(rest("groups?select=*&id=in.(${ids.joinToString(",")})").get().build(), "Load squads"))
        val byId = (0 until g.length()).map { Squad.from(g.getJSONObject(it)) }.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    private suspend fun rpc(fn: String, payload: JSONObject, label: String): String = withContext(Dispatchers.IO) {
        run(rest("rpc/$fn").post(json(payload.toString())).build(), label)
    }

    /** Creates a squad (the server picks the 6-letter code); returns (id, code). */
    suspend fun createSquad(name: String, displayName: String): Pair<String, String> {
        val body = rpc("create_group", JSONObject().put("p_name", name).put("p_display", displayName), "Create squad")
        val o = runCatching { JSONArray(body).getJSONObject(0) }.getOrElse { JSONObject(body) }
        return o.getString("id") to o.getString("code")
    }

    /** Joins with a code; returns the squad id. */
    suspend fun joinSquad(code: String, displayName: String): String {
        val body = rpc("join_group", JSONObject().put("p_code", code.uppercase().trim()).put("p_name", displayName), "Join squad")
        return body.trim().trim('"')
    }

    /** Public squads for Discover; throws when the RPC doesn't exist yet (the UI then hides the section). */
    suspend fun publicGroups(): List<PublicSquad> {
        val arr = JSONArray(rpc("public_groups", JSONObject(), "Load public squads"))
        return (0 until arr.length()).map { PublicSquad.from(arr.getJSONObject(it)) }
    }

    /**
     * Joins a public squad: the web agent's `join_public_group` RPC under the argument names this
     * schema uses elsewhere, else the squad's code (when RLS lets us read it) through `join_group`.
     */
    suspend fun joinPublicSquad(groupId: String, displayName: String): String {
        val attempts = listOf(
            JSONObject().put("g", groupId).put("p_name", displayName),
            JSONObject().put("g", groupId),
            JSONObject().put("p_group", groupId).put("p_name", displayName),
            JSONObject().put("p_id", groupId),
            JSONObject().put("group_id", groupId),
        )
        var last: Exception? = null
        for (a in attempts) {
            try { rpc("join_public_group", a, "Join squad"); return groupId } catch (e: ApiException) { last = e }
        }
        val code = runCatching {
            val arr = JSONArray(withContext(Dispatchers.IO) { run(rest("groups?select=code&id=eq.$groupId").get().build(), "Load squad") })
            arr.optJSONObject(0)?.optString("code")?.ifBlank { null }
        }.getOrNull()
        if (code != null) return joinSquad(code, displayName)
        throw last ?: ApiException("Couldn't join that squad")
    }

    suspend fun leaveSquad(groupId: String) { rpc("leave_group", JSONObject().put("g", groupId), "Leave squad") }

    /** Owner only (RLS): returns false when nothing was updated. */
    suspend fun renameSquad(groupId: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val body = run(rest("groups?id=eq.$groupId").header("Prefer", "return=representation").patch(json(JSONObject().put("name", name).toString())).build(), "Rename squad")
        JSONArray(body).length() > 0
    }

    suspend fun squadBoard(groupId: String): List<SquadMember> {
        val arr = JSONArray(rpc("squad_board", JSONObject().put("g", groupId), "Load squad"))
        return (0 until arr.length()).map { SquadMember.from(arr.getJSONObject(it)) }
    }

    /** Who I've nudged in the last 20 h (the board shows "Nudged"). */
    suspend fun sentNudges(): Set<String> = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val since = java.net.URLEncoder.encode(java.time.Instant.now().minusSeconds(20 * 3600).toString(), "UTF-8")
        val arr = JSONArray(run(rest("nudges?select=to_user&from_user=eq.$uid&created_at=gte.$since").get().build(), "Load nudges"))
        (0 until arr.length()).map { arr.getJSONObject(it).getString("to_user") }.toSet()
    }

    suspend fun nudge(groupId: String, toUser: String) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val payload = JSONObject().put("group_id", groupId).put("from_user", uid).put("to_user", toUser).put("kind", "nudge").toString()
        run(rest("nudges").header("Prefer", "return=minimal").post(json(payload)).build(), "Nudge"); Unit
    }

    /** Nudges sent to me in the last 24 h, newest first, with the sender's name. */
    suspend fun myNudges(): List<Nudge> {
        val arr = JSONArray(rpc("my_nudges", JSONObject(), "Load nudges"))
        return (0 until arr.length()).map { Nudge.from(arr.getJSONObject(it)) }
    }

    // ---- v2.6: usernames ----

    /** `username_available(u)` → true / false; null when the RPC isn't there yet. */
    suspend fun usernameAvailable(u: String): Boolean? = runCatching {
        val body = rpc("username_available", JSONObject().put("u", u), "Check username").trim()
        when {
            body.equals("true", true) -> true
            body.equals("false", true) -> false
            else -> runCatching { JSONArray(body).opt(0).toString().toBoolean() }.getOrNull()
        }
    }.getOrNull()

    // ---- v2.6: squads v2 ----

    /** Every group id I'm in (the save paths post a feed item to each). */
    suspend fun mySquadIds(): List<String> = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val m = JSONArray(run(rest("group_members?select=group_id&user_id=eq.$uid").get().build(), "Load squads"))
        (0 until m.length()).map { m.getJSONObject(it).getString("group_id") }
    }

    /** Owner PATCH of the v2.6 group columns; false when nothing changed or the columns aren't there. */
    suspend fun patchGroup(groupId: String, fields: JSONObject): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            JSONArray(run(rest("groups?id=eq.$groupId").header("Prefer", "return=representation").patch(json(fields.toString())).build(), "Save squad")).length() > 0
        }.getOrDefault(false)
    }

    /** `group_by_code(p_code)` → the squad behind an invite code (id, name, join_policy, joined, requested…); null when none. */
    suspend fun groupByCode(code: String): JSONObject? {
        val body = rpc("group_by_code", JSONObject().put("p_code", code.uppercase().trim()), "Find squad").trim()
        return runCatching { JSONArray(body).optJSONObject(0) }.getOrNull() ?: runCatching { JSONObject(body) }.getOrNull()?.takeIf { it.has("id") }
    }

    /** The id of the squad behind an invite code: group_by_code, else a direct read when RLS allows it. */
    suspend fun groupIdForCode(code: String): String? =
        runCatching { groupByCode(code)?.optString("id")?.ifBlank { null } }.getOrNull() ?: withContext(Dispatchers.IO) {
            runCatching {
                JSONArray(run(rest("groups?select=id&code=eq.${code.uppercase().trim()}").get().build(), "Load squad")).optJSONObject(0)?.optString("id")?.ifBlank { null }
            }.getOrNull()
        }

    /** `request_join(g)` → 'member' (already in) | 'joined' (open squad) | 'requested' (request-only squad). */
    suspend fun requestJoin(groupId: String): String =
        rpc("request_join", JSONObject().put("g", groupId), "Join squad").trim().trim('"').ifBlank { "joined" }
    suspend fun approveJoin(requestId: String) { rpc("approve_join", JSONObject().put("req", requestId), "Approve") }
    suspend fun declineJoin(requestId: String) { rpc("decline_join", JSONObject().put("req", requestId), "Decline") }

    /** Pending requests for a squad I own: `group_requests(g)` (owner only), else the table itself. */
    suspend fun joinRequests(groupId: String): List<JoinRequest> {
        val arr = runCatching { JSONArray(rpc("group_requests", JSONObject().put("g", groupId), "Load requests")) }.getOrElse {
            withContext(Dispatchers.IO) { JSONArray(run(rest("group_join_requests?select=*&group_id=eq.$groupId&status=eq.pending&order=created_at.asc").get().build(), "Load requests")) }
        }
        return (0 until arr.length()).map { JoinRequest.from(arr.getJSONObject(it)) }
    }

    /** `group_feed(g, before, n)`, newest first. Throws when the RPC isn't there (the tabs are hidden). */
    suspend fun groupFeed(groupId: String, before: String? = null, n: Int = 60, kinds: List<String>? = null): List<GroupPost> {
        val payload = JSONObject().put("g", groupId).put("before", before ?: JSONObject.NULL).put("n", n)
        if (kinds != null) payload.put("kinds", JSONArray(kinds))
        val arr = JSONArray(rpc("group_feed", payload, "Load squad feed"))
        return (0 until arr.length()).map { GroupPost.from(arr.getJSONObject(it)) }
    }

    /**
     * `post_to_my_groups(p_kind, p_body, p_ref, p_photo)` → how many squads got it. The server fans
     * out to every squad I'm in, respects share_stats, and re-posting the same [ref] updates the post.
     */
    suspend fun postToMyGroups(kind: String, body: String, ref: String? = null, photoPath: String? = null): Int {
        val payload = JSONObject().put("p_kind", kind).put("p_body", body.take(500))
            .put("p_ref", ref ?: JSONObject.NULL).put("p_photo", photoPath ?: JSONObject.NULL)
        return rpc("post_to_my_groups", payload, "Post to squads").trim().trim('"').toIntOrNull() ?: 0
    }

    /** Inserts one post (message / meal / workout / pr / photo) into each of [groupIds]. */
    suspend fun postToGroups(groupIds: List<String>, kind: String, body: String, refId: String? = null, photoPath: String? = null) = withContext(Dispatchers.IO) {
        val uid = Session.userId ?: throw AuthException("Not signed in")
        if (groupIds.isEmpty()) return@withContext
        val arr = JSONArray().apply {
            groupIds.forEach { g ->
                put(JSONObject().put("group_id", g).put("user_id", uid).put("kind", kind).put("body", body.take(500))
                    .put("ref_id", refId ?: JSONObject.NULL).put("photo_path", photoPath ?: JSONObject.NULL))
            }
        }
        run(rest("group_posts").header("Prefer", "return=minimal").post(json(arr.toString())).build(), "Post to squad"); Unit
    }

    suspend fun groupLeaderboard(groupId: String): List<LeaderRow> {
        val arr = JSONArray(rpc("group_leaderboard", JSONObject().put("g", groupId), "Load leaderboard"))
        return (0 until arr.length()).map { LeaderRow.from(arr.getJSONObject(it)) }
    }

    suspend fun groupMembersDetail(groupId: String): List<MemberDetail> {
        val arr = JSONArray(rpc("group_members_detail", JSONObject().put("g", groupId), "Load members"))
        return (0 until arr.length()).map { MemberDetail.from(arr.getJSONObject(it)) }
    }

    // ---- v2.7: squad challenges ----

    /** `create_challenge(g, kind, title, target_days, protein_target, starts_on, ends_on)` → the new id; the server posts "🏁 started". */
    suspend fun createChallenge(
        groupId: String, kind: String, title: String, targetDays: Int, proteinTarget: Int?, startsOn: String, endsOn: String,
    ): String {
        val payload = JSONObject().put("g", groupId).put("kind", kind).put("title", title.trim().take(60))
            .put("target_days", targetDays).put("protein_target", proteinTarget ?: JSONObject.NULL)
            .put("starts_on", startsOn).put("ends_on", endsOn)
        return rpc("create_challenge", payload, "Start challenge").trim().trim('"')
    }

    /** `group_challenge_list(g)`: newest first, open ones plus those that ended in the last 30 days. */
    suspend fun groupChallenges(groupId: String): List<Challenge> {
        val arr = JSONArray(rpc("group_challenge_list", JSONObject().put("g", groupId), "Load challenges"))
        return (0 until arr.length()).map { Challenge.from(arr.getJSONObject(it)) }
    }

    /** `challenge_board(c)`: every member, ranked. */
    suspend fun challengeBoard(challengeId: String): List<ChallengeBoardRow> {
        val arr = JSONArray(rpc("challenge_board", JSONObject().put("c", challengeId), "Load challenge"))
        return (0 until arr.length()).map { ChallengeBoardRow.from(arr.getJSONObject(it)) }
    }

    /** Plain delete through RLS (creator or squad owner); false when the policy let nothing go. */
    suspend fun deleteChallenge(id: String): Boolean = withContext(Dispatchers.IO) {
        JSONArray(run(rest("group_challenges?id=eq.$id").header("Prefer", "return=representation").delete().build(), "Delete challenge")).length() > 0
    }

    /** Challenges this app session has already posted (or found) a completion for: "<group>/<challenge>/<user>". */
    private val challengeDone: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /**
     * For each active challenge in [list] where I've reached the target, inserts
     * `🏆 completed "<title>"` (kind challenge, ref_id = challenge id) unless it's already there.
     * The (group, user, kind, ref_id) unique index makes a racing duplicate a 409, which counts as done.
     * Returns how many new posts went up.
     */
    suspend fun postChallengeCompletions(groupId: String, list: List<Challenge>): Int {
        val uid = Session.userId ?: return 0
        var posted = 0
        for (c in list) {
            if (c.status != "active" || c.myProgress < c.targetDays) continue
            val key = "$groupId/${c.id}/$uid"
            if (key in challengeDone) continue
            val trophy = java.net.URLEncoder.encode("🏆*", "UTF-8")
            val exists = withContext(Dispatchers.IO) {
                JSONArray(
                    run(rest("group_posts?select=id&group_id=eq.$groupId&user_id=eq.$uid&kind=eq.challenge&ref_id=eq.${c.id}&body=like.$trophy&limit=1").get().build(), "Check challenge post"),
                ).length() > 0
            }
            if (!exists) {
                try { postToGroups(listOf(groupId), "challenge", com.sohum.bandlog.util.ChallengeMath.completedBody(c.title), c.id); posted++ }
                catch (e: ApiException) { if (e.message?.contains("(409)") != true) throw e }
            }
            challengeDone += key
        }
        return posted
    }

    /** After a daily_stats save: check every squad's active challenges. Silent when v2.7 isn't live. */
    suspend fun checkChallengeCompletions(): Int =
        runCatching { mySquadIds() }.getOrDefault(emptyList()).sumOf { g ->
            runCatching { postChallengeCompletions(g, groupChallenges(g)) }.getOrDefault(0)
        }

    /** Uploads a JPEG to group-photos/<uid>/<name>.jpg (readable by squad-mates); returns the path. */
    suspend fun uploadGroupPhoto(jpeg: ByteArray, name: String = java.util.UUID.randomUUID().toString()): String = withContext(Dispatchers.IO) {
        SupabaseAuth.ensureFresh()
        val token = Session.accessToken ?: throw AuthException("Not signed in")
        val uid = Session.userId ?: throw AuthException("Not signed in")
        val path = "$uid/$name.jpg"
        val req = Request.Builder().url("$base/storage/v1/object/group-photos/$path")
            .header("apikey", key).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
        run(req, "Upload photo")
        path
    }
}
