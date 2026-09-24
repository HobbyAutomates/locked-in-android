package com.sohum.bandlog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.AuthException
import com.sohum.bandlog.data.ExerciseEntry
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SupabaseAuth
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.util.Burn
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Streaks
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One source of truth for the whole app: profile + last 120 days of workouts and meals. */
class AppViewModel : ViewModel() {

    var signedIn by mutableStateOf(Session.signedIn); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loadedOnce by mutableStateOf(false); private set

    var profile by mutableStateOf(Profile()); private set
    var workouts by mutableStateOf<List<Workout>>(emptyList()); private set
    var meals by mutableStateOf<List<Meal>>(emptyList()); private set
    /** Calories-burned rows (logged exercise + the auto-burn each band workout writes). */
    var exercises by mutableStateOf<List<ExerciseEntry>>(emptyList()); private set
    var weights by mutableStateOf<List<com.sohum.bandlog.data.WeightEntry>>(emptyList()); private set

    /** Lifetime figures behind the badges; loaded lazily when the Badges page opens. */
    var totalMeals by mutableStateOf(0); private set
    var allWorkoutDates by mutableStateOf<List<String>>(emptyList()); private set
    var badgesLoading by mutableStateOf(false); private set

    val today: String get() = Dates.today()
    val workoutDates: List<String> get() = workouts.map { it.date }.distinct()
    val mealDates: List<String> get() = meals.map { it.date }.distinct()
    val weekStreak: Int get() = Streaks.workoutWeekStreak(workoutDates, profile.weeklyWorkoutTarget)
    /** v2.2: consecutive (India) days with ANY log — a workout, an exercise_log row of any source, or a meal. */
    val dayStreak: Int get() = Streaks.dayStreak(workouts.map { it.date } + exercises.map { it.date } + meals.map { it.date })
    val mealStreak: Int get() = Streaks.dayStreak(mealDates)
    val thisWeek: Int get() = Streaks.thisWeekCount(workoutDates)

    /** Meals saved while their parse / photo was still running. Shown as the pending banner on Home. */
    var pendingMeals by mutableStateOf<List<String>>(emptyList()); private set
    /** Set when a workout save bumps the week count / streak; the shell shows the celebration modal. */
    var celebrate by mutableStateOf<Celebration?>(null); private set
    var savedMeals by mutableStateOf<List<com.sohum.bandlog.data.SavedMeal>>(emptyList()); private set

    data class Celebration(val streakWeeks: Int, val thisWeek: Int, val target: Int, val hitTarget: Boolean)

    fun dismissCelebration() { celebrate = null }

    /** A short, non-blocking message for Home ("Workout saved", or a burn row that failed to log). */
    var notice by mutableStateOf<String?>(null); private set
    fun dismissNotice() { notice = null }

    /**
     * v2.2: the refresh token was rejected while the user was mid-task. Instead of swapping the
     * whole UI to the sign-in page (and silently discarding an open form), the form shows the
     * error and the shell signs out once the form / page is closed ([finishAuthLost]).
     */
    var authLost by mutableStateOf(false); private set
    /** Why the sign-in page is showing, when it wasn't the user's own sign-out. */
    var signOutReason by mutableStateOf<String?>(null); private set

    private fun onAuthLost(msg: String?) {
        authLost = true
        error = msg ?: "Your session expired — sign in again."
    }

    fun finishAuthLost() {
        if (!authLost) return
        signOutReason = error ?: "Your session expired — sign in again."
        authLost = false; signedIn = false; loadedOnce = false; error = null
    }

    // ---- first run ----

    /** Set by the onboarding flow's "Skip for now"; lasts until the next sign-in. */
    var onboardingSkipped by mutableStateOf(false)

    /**
     * Whether to show the onboarding flow instead of the tab shell. Completion isn't a column of
     * its own — a profile that has a weight, a height and a birthday has been through the flow
     * (or filled them in by hand), which is exactly what the plan generator needs.
     */
    val needsOnboarding: Boolean
        get() = signedIn && loadedOnce && !onboardingSkipped &&
            (profile.weightKg == null || profile.heightCm == null || profile.dob == null)

    // ---- Health Connect (Google Fit / Samsung Health) ----
    var healthConnected by mutableStateOf(false); private set
    var healthToday by mutableStateOf<com.sohum.bandlog.util.Health.Today?>(null); private set
    var healthError by mutableStateOf<String?>(null); private set
    /** Local copies of the two budget toggles, used while the profile columns don't exist yet. */
    var localBurned by mutableStateOf(false)
    var localRollover by mutableStateOf(false)
    var celebrationsOn by mutableStateOf(true)

    /** v2.3: saved on the profile (add_burned_to_goal), local pref as the fallback. */
    val addBurnedBack: Boolean get() = profile.addBurnedToGoal ?: localBurned
    /** v2.3: rollover_calories — up to 200 kcal of yesterday's unused budget joins today's. */
    val rolloverOn: Boolean get() = profile.rolloverCalories ?: localRollover

    fun setAddBurned(context: android.content.Context, on: Boolean) {
        localBurned = on; com.sohum.bandlog.util.ThemePrefs.setBurned(context, on)
        profile = profile.copy(addBurnedToGoal = if (profile.addBurnedToGoal != null) on else null)
        viewModelScope.launch { runCatching { Api.patchProfile(org.json.JSONObject().put("add_burned_to_goal", on)) } }
    }

    fun setRollover(context: android.content.Context, on: Boolean) {
        localRollover = on; com.sohum.bandlog.util.ThemePrefs.setRollover(context, on)
        profile = profile.copy(rolloverCalories = if (profile.rolloverCalories != null) on else null)
        viewModelScope.launch { runCatching { Api.patchProfile(org.json.JSONObject().put("rollover_calories", on)) } }
    }

    /** Yesterday's unused calories, capped at 200; 0 when rollover is off or nothing was logged yesterday. */
    val rolloverKcal: Double
        get() {
            if (!rolloverOn) return 0.0
            val y = Dates.addDays(today, -1)
            if (meals.none { it.date == y }) return 0.0
            val eaten = com.sohum.bandlog.data.totalsFor(meals, y).calories
            return (profile.calorieTarget - eaten).coerceIn(0.0, 200.0)
        }

    /** Today's calorie budget: target + burned (if on) + rollover (if on). */
    val budgetToday: Double get() = profile.calorieTarget + burnedKcal + rolloverKcal

    // ---- v2.3: water ----
    var water by mutableStateOf<List<com.sohum.bandlog.data.WaterEntry>>(emptyList()); private set
    val waterToday: Int get() = water.filter { it.date == today }.sumOf { it.ml }

    /** v2.6: today's rows, newest first (the − button undoes the first). */
    val waterTodayRows: List<com.sohum.bandlog.data.WaterEntry> get() = water.filter { it.date == today }.sortedByDescending { it.createdAt }

    /** Bumped when an add takes today's total across the goal; the shell decides whether to celebrate (once a day). */
    var waterGoalTick by mutableStateOf(0); private set

    /**
     * v2.6: one water_log row per add, tagged with its [vessel]. The bottle fills at once (the row
     * is added locally first) and the list is re-read after the insert.
     */
    suspend fun logWater(ml: Int, date: String = today, vessel: String? = null, quiet: Boolean = false): Boolean {
        if (ml <= 0) return false
        val goal = profile.waterGoalMl
        val before = waterToday
        val temp = com.sohum.bandlog.data.WaterEntry("local-${System.nanoTime()}", date, ml, java.time.OffsetDateTime.now().toString(), vessel)
        water = listOf(temp) + water
        return try {
            Api.logWater(date, ml, vessel)
            runCatching { water = Api.water(Dates.addDays(today, -30), today) }
            if (!quiet) notice = "Logged $ml mL water"
            if (date == today && before < goal && waterToday >= goal) waterGoalTick++
            true
        } catch (e: AuthException) { water = water - temp; onAuthLost(e.message); false }
        catch (e: kotlinx.coroutines.CancellationException) { water = water - temp; throw e }
        catch (e: Exception) { water = water - temp; error = e.message ?: "Couldn't log water"; false }
    }

    /** v2.6: the − button: deletes today's most recent row. */
    suspend fun undoWater(): Boolean {
        val last = waterTodayRows.firstOrNull() ?: return false
        water = water - last
        return try {
            if (!last.id.startsWith("local-")) Api.deleteWater(last.id)
            runCatching { water = Api.water(Dates.addDays(today, -30), today) }
            true
        } catch (e: AuthException) { water = water + last; onAuthLost(e.message); false }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { water = water + last; error = e.message ?: "Couldn't undo"; false }
    }

    /** v2.6: goal edits from the Water page (glasses × glass size). */
    fun setWaterGoal(ml: Int) {
        val v = ml.coerceIn(250, 10_000)
        profile = profile.copy(waterGoalMl = v)
        viewModelScope.launch { runCatching { Api.patchProfile(org.json.JSONObject().put("water_goal_ml", v)) } }
    }

    /** v2.6: the reminder window and frequency, saved on the profile (the alarm reads the local mirror). */
    fun setWaterReminder(from: String, to: String, every: Int) {
        profile = profile.copy(waterReminderFrom = from, waterReminderTo = to, waterReminderEveryMin = every)
        viewModelScope.launch {
            runCatching { Api.patchProfile(org.json.JSONObject().put("water_reminder_from", from).put("water_reminder_to", to).put("water_reminder_every_min", every)) }
        }
    }

    // ---- v2.6: username ----

    suspend fun setUsername(u: String): Boolean {
        val ok = try { Api.patchProfile(org.json.JSONObject().put("username", u)) } catch (e: Exception) { false }
        if (ok) profile = profile.copy(username = u) else error = "Couldn't save that username"
        return ok
    }

    /** v2.6: after the preset / photo is uploaded in the squad profile flow. */
    fun setAvatarPathLocal(path: String) { profile = profile.copy(avatarPath = path) }

    // ---- v2.6: squad feed posts from the save paths ----

    /**
     * Posts to every squad I'm in, in the background; a failure (no group_posts table yet, no
     * squads) is silent. Meals are only shared when "share with squads" is on.
     */
    private fun shareToSquads(kind: String, body: String, refId: String? = null, mealPhotoPath: String? = null) {
        viewModelScope.launch {
            runCatching {
                // A meal photo lives in the owner-only meal-photos bucket: copy it to group-photos/<uid>/ first.
                val path = mealPhotoPath?.let { mp ->
                    runCatching {
                        val bmp = Api.fetchStorageBitmap("meal-photos", mp) ?: return@runCatching null
                        val bytes = withContext(Dispatchers.Default) { com.sohum.bandlog.util.Images.jpeg(com.sohum.bandlog.util.Images.fitWithin(bmp, 720), 82) }
                        Api.uploadGroupPhoto(bytes)
                    }.getOrNull()
                }
                // post_to_my_groups fans out server-side; fall back to direct inserts on an older database.
                runCatching { Api.postToMyGroups(kind, body, refId, path) }.getOrElse {
                    val groups = Api.mySquadIds()
                    if (groups.isNotEmpty()) Api.postToGroups(groups, kind, body, refId, path)
                }
            }.onFailure { android.util.Log.w("LockedIn", "Squad post skipped: ${it.message}") }
        }
    }

    private fun shareMeal(date: String, raw: String, items: List<MealItem>, photoPath: String?, mealId: String? = null) {
        if (date != today || !profile.shareStats || items.isEmpty()) return
        val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
        val what = (names.take(3).joinToString(" + ") + if (names.size > 3) " + ${names.size - 3} more" else "").ifBlank { raw.take(60) }
        val kcal = items.sumOf { it.calories }.toInt()
        shareToSquads("meal", "logged $what · $kcal kcal", mealId, photoPath)
    }

    private fun fmtKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(java.util.Locale.US, "%.1f", kg)

    /** A new workout's feed line, and a PR post for every lift whose top weight beats its best in the loaded history. */
    private fun shareWorkout(w: Workout, previous: List<Workout>) {
        if (w.date != today) return
        val body = if (w.isBands) listOfNotNull(w.summary, w.minutes?.let { "$it min" }).joinToString(" · ") else w.summary
        shareToSquads("workout", body, w.id)
        w.lifts.forEach { lift ->
            val top = lift.sets.filter { it.kg != null && (it.reps ?: 0) > 0 }.maxWithOrNull(compareBy({ it.kg }, { it.reps })) ?: return@forEach
            val prev = previous.asSequence().flatMap { it.lifts.asSequence() }.filter { it.name.equals(lift.name, ignoreCase = true) }
                .flatMap { it.sets.asSequence() }.mapNotNull { it.kg }.maxOrNull() ?: return@forEach
            if ((top.kg ?: 0.0) > prev) shareToSquads("pr", "🏆 ${lift.name} ${fmtKg(top.kg ?: 0.0)} kg × ${top.reps}", null)
        }
    }

    // ---- v2.3: progress photos ----
    var progressPhotos by mutableStateOf<List<com.sohum.bandlog.data.ProgressPhoto>>(emptyList()); private set
    var progressPhotosError by mutableStateOf<String?>(null)
    fun loadProgressPhotos() {
        viewModelScope.launch {
            runCatching { Api.progressPhotos() }.onSuccess { progressPhotos = it; progressPhotosError = null }
        }
    }

    suspend fun uploadProgressPhoto(bmp: android.graphics.Bitmap): Boolean = try {
        val bytes = withContext(Dispatchers.Default) { com.sohum.bandlog.util.Images.jpeg(com.sohum.bandlog.util.Images.fitWithin(bmp, 1024), 85) }
        Api.uploadProgressPhoto(bytes, today)
        runCatching { progressPhotos = Api.progressPhotos() }
        progressPhotosError = null
        true
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { android.util.Log.w("LockedIn", "Progress photo upload failed", e); progressPhotosError = e.message ?: "Upload failed"; false }

    /**
     * Logged exercise kcal for [date]. When Health Connect is connected the band-workout rows are
     * left out — we already wrote that session into Health Connect, so its active calories cover it.
     */
    fun exerciseKcal(date: String): Double =
        exercises.filter { it.date == date && !(healthConnected && it.source == "workout") }.sumOf { it.kcal }

    /** Everything burned today: Health Connect active kcal (if connected) plus logged exercise, deduplicated. */
    val burnedToday: Double get() = (healthToday?.activeKcal ?: 0.0) + exerciseKcal(today)

    /** Calories burned today that count toward the target when the toggle is on. */
    val burnedKcal: Double get() = if (addBurnedBack) burnedToday else 0.0

    fun refreshHealth(context: android.content.Context) {
        viewModelScope.launch {
            val ctx = context.applicationContext
            if (!com.sohum.bandlog.util.Health.available(ctx)) { healthConnected = false; com.sohum.bandlog.util.Wrap.setHealthConnected(ctx, false); return@launch }
            healthConnected = com.sohum.bandlog.util.Health.hasPermissions(ctx)
            com.sohum.bandlog.util.Wrap.setHealthConnected(ctx, healthConnected)
            if (healthConnected) {
                val r = com.sohum.bandlog.util.Health.todayResult(ctx)
                healthToday = r.getOrNull(); healthError = r.exceptionOrNull()?.let { "Health Connect read failed: ${it.message}" }
                // Stamp today's burn so the Weekly Energy chart has a history to draw.
                healthToday?.let { com.sohum.bandlog.util.BurnedCache.put(ctx, Dates.today(), it.activeKcal) }
                val y = Dates.addDays(Dates.today(), -1)
                healthBurnHistory = mapOf(y to com.sohum.bandlog.util.BurnedCache.get(ctx, y))
            } else { healthToday = null; healthError = null; healthBurnHistory = emptyMap() }
            pushRollup()
        }
    }

    /** Health Connect active kcal stamped on earlier days (yesterday), for the rollup and a past-midnight wrap. */
    private var healthBurnHistory: Map<String, Double> = emptyMap()

    /** Everything burned on [date], deduplicated exactly as Home does. */
    fun burnedOn(date: String): Double =
        exerciseKcal(date) + if (date == today) (healthToday?.activeKcal ?: 0.0) else if (healthConnected) (healthBurnHistory[date] ?: 0.0) else 0.0

    // ---- v2.0: squads ----

    /** Nudges squad-mates sent me in the last 24 h (Home's banner). */
    var nudges by mutableStateOf<List<com.sohum.bandlog.data.Nudge>>(emptyList()); private set
    private var rolledYesterday = false

    /**
     * Upserts today's `daily_stats` row (and yesterday's on the first push of this app session) so
     * the squad board is current: trained, protein, calories, burned, meals, week streak.
     */
    private fun pushRollup() {
        if (!signedIn || !loadedOnce) return
        val t = today
        val dates = if (rolledYesterday) listOf(t) else listOf(t, Dates.addDays(t, -1))
        val streak = weekStreak
        val trainedDates = workoutDates.toSet()
        val rows = dates.map { d ->
            val tot = com.sohum.bandlog.data.totalsFor(meals, d)
            Api.DailyStat(d, d in trainedDates, tot.protein, tot.calories, burnedOn(d), meals.count { it.date == d }, streak)
        }
        viewModelScope.launch { runCatching { Api.upsertDailyStats(rows); rolledYesterday = true } }
    }

    /** Tonight's wrap (or last night's, after midnight), from the same data as Home. */
    fun wrap(): com.sohum.bandlog.util.Wrap.Result {
        val d = com.sohum.bandlog.util.Wrap.wrapDate()
        return com.sohum.bandlog.util.Wrap.compute(profile, workouts, meals, burnedOn(d), d)
    }

    /** Called after a workout save; mirrors it into Health Connect when connected. */
    fun pushSessionToHealth(context: android.content.Context, muscles: List<String>, minutes: Int?, date: String, title: String? = null) {
        if (!healthConnected) return
        viewModelScope.launch { com.sohum.bandlog.util.Health.writeSession(context.applicationContext, title ?: ("Bands: " + muscles.joinToString(", ")), minutes ?: 30, date) }
    }

    // ---- v2.1: the one Add-food screen ----

    /** What a background parse or plate photo adds to the plate: items, any notes, and the stored photo. */
    data class MealBatch(val items: List<MealItem>, val notes: List<String> = emptyList(), val photoPath: String? = null)

    /**
     * "Work it out": parse-meal, run in the view model's scope so it survives the Log page closing
     * (Save while it's still running saves as soon as it lands — see [saveMealAfter]).
     */
    fun parseAsync(text: String): Deferred<MealBatch> = viewModelScope.async {
        val r = Api.parseMeal(text)
        MealBatch(r.items, r.assumptions + r.unparsed.map { "Ignored: $it" })
    }

    /** The plate photo → per-item estimate; the JPEG is stored (or uploaded) so it saves with the meal. */
    fun photoAsync(photo: android.graphics.Bitmap): Deferred<MealBatch> = viewModelScope.async {
        val b64 = withContext(Dispatchers.Default) { com.sohum.bandlog.ui.scan.toJpegBase64(photo, 85) }
        val est = Api.photoMeal(b64, "")
        val path = est.photoPath ?: runCatching { Api.uploadMealPhoto(withContext(Dispatchers.Default) { com.sohum.bandlog.ui.scan.toJpegBytes(photo, 85) }) }.getOrNull()
        MealBatch(est.items.map { it.toMealItem() }, est.notes, path)
    }

    /**
     * Save pressed while a parse / photo is still working: the page closes, Home shows the pending
     * row, and the meal is saved with everything already on the plate plus whatever the jobs add.
     */
    fun saveMealAfter(date: String, raw: String, items: List<MealItem>, photoPath: String?, jobs: List<Deferred<MealBatch>>) {
        val label = raw.ifBlank { "Meal" }
        pendingMeals = pendingMeals + label
        viewModelScope.launch {
            try {
                val batches = jobs.mapNotNull { runCatching { it.await() }.getOrNull() }
                val all = items + batches.flatMap { it.items }
                if (all.isNotEmpty()) {
                    val photo = photoPath ?: batches.firstNotNullOfOrNull { it.photoPath }
                    val mid = Api.saveMeal(date, label, all, photo)
                    shareMeal(date, label, all, photo, mid)
                }
                else error = "Couldn't find any food in “${label.take(40)}”"
                refresh()
            } catch (e: Exception) { error = e.message ?: "Couldn't log that meal" }
            finally { pendingMeals = pendingMeals - label }
        }
    }

    /** How often each food_id was logged in the last [days] days — orders presets most-used first. */
    fun foodUse(days: Long = 60): Map<String, Int> {
        val from = Dates.addDays(today, -days)
        return meals.asSequence().filter { it.date >= from }.flatMap { it.items.asSequence() }.mapNotNull { it.foodId }.groupingBy { it }.eachCount()
    }

    /** The most recent band workout, for the band form's "Same as last time" pill. */
    val lastWorkout: Workout? get() = lastWorkout(Workout.BANDS)

    /** v2.5: the most recent workout of [kind] ("Same as last time" per kind). */
    fun lastWorkout(kind: String, except: String? = null): Workout? =
        workouts.filter { it.kind == kind && it.id != except }.maxWithOrNull(compareBy<Workout>({ it.date }, { it.id }))

    /** v2.5: the last time [name] was lifted (any gym / bodyweight session) — the ghost hints in its set grid. */
    fun lastLift(name: String, except: String? = null): com.sohum.bandlog.data.Lift? =
        workouts.asSequence().filter { it.id != except && it.lifts.isNotEmpty() }
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
            .firstNotNullOfOrNull { w -> w.lifts.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    /** The burn row a workout wrote (its note holds the workout id). */
    fun burnOf(workoutId: String): ExerciseEntry? = exercises.firstOrNull { it.source == "workout" && it.note == workoutId }

    /** v2.4: items a scan's "Log 1 serving" hands to Add food; the Meal form takes them once. */
    var addFoodPrefill by mutableStateOf<List<MealItem>?>(null); private set
    /** Bumped to make the shell open Add food (Log → Meal). */
    var addFoodTick by mutableStateOf(0); private set
    fun openAddFood(items: List<MealItem>) { addFoodPrefill = items; addFoodTick++ }
    fun takeAddFoodPrefill(): List<MealItem>? = addFoodPrefill.also { addFoodPrefill = null }

    fun loadSavedMeals() { viewModelScope.launch { runCatching { savedMeals = Api.savedMeals() } } }

    /** The Indian food presets (v1.9), fetched once per session; the Meal form's Presets tab reads them. */
    var presets by mutableStateOf<List<com.sohum.bandlog.data.FoodPreset>>(emptyList()); private set
    var presetsLoading by mutableStateOf(false); private set
    fun loadPresets(force: Boolean = false) {
        if (presetsLoading || (presets.isNotEmpty() && !force)) return
        viewModelScope.launch {
            presetsLoading = true
            runCatching { presets = Api.presets() }
            presetsLoading = false
        }
    }

    /** Debug builds only (DebugPreviewActivity): seed presets / workouts so screens render without a session. */
    internal fun debugSeed(presets: List<com.sohum.bandlog.data.FoodPreset>, workouts: List<Workout> = emptyList()) {
        this.presets = presets; this.workouts = workouts
    }

    /** Debug builds only: a profile + today's water rows for the v2.6 Water page. */
    internal fun debugSeedWater(profile: Profile, water: List<com.sohum.bandlog.data.WaterEntry>) {
        this.profile = profile; this.water = water
    }

    fun onSignedIn() { signedIn = true; authLost = false; signOutReason = null; error = null; refresh() }

    fun refresh() {
        if (!signedIn) return
        viewModelScope.launch {
            loading = true; error = null
            try {
                val t = Dates.today()
                val from = Dates.addDays(t, -120)
                coroutineScope {
                    val p = async { Api.profile() }
                    val w = async { Api.workouts(from, t) }
                    val m = async { Api.meals(from, t) }
                    val g = async { runCatching { Api.weights() }.getOrDefault(weights) }
                    val x = async { runCatching { Api.exercises(from, t) }.getOrDefault(exercises) }
                    val n = async { runCatching { Api.myNudges() }.getOrDefault(nudges) }
                    val wa = async { runCatching { Api.water(Dates.addDays(t, -30), t) }.getOrDefault(water) }
                    profile = p.await(); workouts = w.await(); meals = m.await(); weights = g.await(); exercises = x.await(); nudges = n.await(); water = wa.await()
                }
                loadedOnce = true
                pushRollup()
            } catch (e: AuthException) {
                onAuthLost(e.message)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load"
            } finally { loading = false }
        }
    }

    fun clearError() { error = null }

    /**
     * Runs [block] then refreshes; surfaces failures in [error]. Returns true on success. An auth
     * failure keeps the user where they are (see [authLost]) with [authMessage] as the error.
     */
    private suspend fun mutate(authMessage: String = "Your session expired — sign in again. This wasn't saved.", block: suspend () -> Unit): Boolean = try {
        block(); refresh(); true
    } catch (e: AuthException) { android.util.Log.w("LockedIn", "Save blocked by auth: ${e.message}"); onAuthLost(authMessage); false }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { android.util.Log.w("LockedIn", "Save failed", e); error = e.message ?: "Something went wrong"; false }

    /**
     * The burn a workout writes. Bands: the band level's MET; gym: weight lifting (MET 5.0);
     * bodyweight: calisthenics (MET 3.8); cardio / sport / yoga: whatever the Exercise form priced.
     */
    data class WorkoutBurn(val code: String?, val name: String, val intensity: String, val kcal: Double, val extras: Api.ExerciseExtras = Api.ExerciseExtras())

    suspend fun saveWorkout(
        id: String?, date: String, muscles: List<String>, band: String,
        kg: Double?, minutes: Int?, exercises: String, notes: String,
        kind: String = Workout.BANDS, lifts: List<com.sohum.bandlog.data.Lift>? = null, burn: WorkoutBurn? = null,
    ): Boolean {
        val before = thisWeek; val beforeStreak = weekStreak
        val history = workouts
        var burnError: String? = null
        var newId: String? = null
        val ok = mutate("Your session expired — sign in again. This workout wasn't saved.") {
            val wid = Api.saveWorkout(id, date, muscles, band, kg, minutes, exercises, notes, kind, lifts)
            newId = wid
            // Auto-burn: one exercise_log row per workout, tagged with the workout id in `note`.
            // An edit replaces the row so minutes / band changes flow through to the burn.
            // The workout itself is saved at this point, so a failure here is reported, not fatal.
            runCatching {
                if (id != null) Api.deleteWorkoutBurn(wid)
                val mins = (minutes ?: 30).coerceAtLeast(1)
                val b = burn ?: when (kind) {
                    Workout.BANDS -> WorkoutBurn(Burn.bandCode(band), "Bands: " + muscles.joinToString(", "), Burn.bandIntensity(band), Burn.bandKcal(band, profile.weightKg, mins))
                    "bodyweight" -> WorkoutBurn(null, "Bodyweight: " + (lifts.orEmpty().joinToString(", ") { it.name }.ifBlank { "workout" }), "medium", Burn.kcal(Burn.BODYWEIGHT_MET, profile.weightKg, mins))
                    "cardio" -> WorkoutBurn(null, exercises.ifBlank { "Cardio" }, "medium", Burn.kcal(7.0, profile.weightKg, mins))
                    "sport" -> WorkoutBurn(null, exercises.ifBlank { "Sport" }, "medium", Burn.kcal(6.0, profile.weightKg, mins))
                    "yoga" -> WorkoutBurn(null, exercises.ifBlank { "Yoga" }, "medium", Burn.kcal(2.5, profile.weightKg, mins))
                    else -> WorkoutBurn(null, "Gym: " + (lifts.orEmpty().joinToString(", ") { it.name }.ifBlank { "workout" }), "medium", Burn.kcal(Burn.GYM_MET, profile.weightKg, mins))
                }
                Api.saveExercise(date, b.code, b.name.take(120), mins, b.intensity, b.kcal, "workout", wid, b.extras)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("LockedIn", "Workout burn row failed", e)
                burnError = e.message ?: "unknown error"
            }
        }
        if (ok) notice = burnError?.let { "Workout saved, but its calories burned didn't log ($it)." } ?: if (id == null) "Workout saved" else "Workout updated"
        // v2.6: a new session goes on the squads' feed (plus a PR post for any lift beating its best).
        if (ok && id == null) newId?.let { wid -> shareWorkout(Workout(wid, date, muscles, band, kg, minutes, exercises, notes, kind, lifts.orEmpty()), history) }
        // refresh() runs async inside mutate; wait for it so the counts below are fresh.
        if (ok && id == null) {
            kotlinx.coroutines.delay(50)
            while (loading) kotlinx.coroutines.delay(50)
            val target = profile.weeklyWorkoutTarget
            if (thisWeek > before && celebrationsOn) celebrate = Celebration(weekStreak, thisWeek, target, hitTarget = thisWeek >= target && before < target || weekStreak > beforeStreak)
        }
        return ok
    }

    suspend fun deleteWorkout(id: String) = mutate { runCatching { Api.deleteWorkoutBurn(id) }; Api.deleteWorkout(id) }

    /** A burn logged from the Exercise tab (run, bands, an activity, a description, or a manual number). */
    suspend fun saveExercise(
        date: String, activityCode: String?, name: String, minutes: Int, intensity: String, kcal: Double, source: String,
        note: String = "", extras: Api.ExerciseExtras = Api.ExerciseExtras(),
    ) = mutate { Api.saveExercise(date, activityCode, name, minutes, intensity, kcal, source, note, extras) }
    suspend fun deleteExercise(id: String) = mutate { Api.deleteExercise(id) }
    /** Every activity Haiku found in a description, saved as its own row. */
    suspend fun saveDescribed(date: String, items: List<com.sohum.bandlog.data.DescribedExercise>) = mutate {
        items.forEach { Api.saveExercise(date, it.activityCode, it.name, it.minutes, it.intensity, it.kcal, "describe") }
    }
    suspend fun saveMeal(date: String, raw: String, items: List<MealItem>, photoPath: String? = null): Boolean {
        var mid: String? = null
        val ok = mutate { mid = Api.saveMeal(date, raw, items, photoPath) }
        if (ok) shareMeal(date, raw, items, photoPath, mid)
        return ok
    }
    suspend fun deleteMeal(id: String) = mutate { Api.deleteMeal(id) }
    suspend fun saveTargets(p: Profile) = mutate { Api.saveProfile(p) }

    /** Saves the whole profile (Personal details, goals, reminders all go through here). */
    suspend fun saveProfile(p: Profile): Boolean {
        val ok = mutate { Api.saveProfile(p) }
        // Show the new values immediately even if the background refresh is still in flight.
        if (ok) profile = p
        return ok
    }

    // ---- weight log ----

    fun loadWeights() { viewModelScope.launch { runCatching { weights = Api.weights() } } }

    suspend fun logWeight(date: String, kg: Double, note: String): Boolean {
        val ok = mutate { Api.logWeight(date, kg, note) }
        if (ok) runCatching { weights = Api.weights() }
        return ok
    }

    suspend fun deleteWeight(id: String): Boolean {
        val ok = mutate { Api.deleteWeight(id) }
        if (ok) runCatching { weights = Api.weights() }
        return ok
    }

    // ---- badges ----

    /** Days in the loaded window whose logged calories landed within ±10% of the target. */
    val calorieGoalDays: Int
        get() {
            val target = profile.calorieTarget.toDouble()
            if (target <= 0) return 0
            return meals.groupBy { it.date }
                .count { (_, m) ->
                    val kcal = m.sumOf { meal -> meal.calories }
                    kcal > 0 && kotlin.math.abs(kcal - target) <= target * 0.10
                }
        }

    val badgeProgress: com.sohum.bandlog.util.Badges.Progress
        get() = com.sohum.bandlog.util.Badges.Progress(
            streakDays = com.sohum.bandlog.util.Badges.longestDayRun(allWorkoutDates.ifEmpty { workoutDates }),
            meals = maxOf(totalMeals, meals.size),
            goalDays = calorieGoalDays,
        )

    fun loadBadgeTotals() {
        if (badgesLoading) return
        viewModelScope.launch {
            badgesLoading = true
            runCatching {
                coroutineScope {
                    val d = async { Api.allWorkoutDates() }
                    val c = async { Api.countRows("meals") }
                    allWorkoutDates = d.await(); totalMeals = c.await()
                }
            }
            badgesLoading = false
        }
    }

    /** Fire a suspend mutation from a composable that has no scope of its own. */
    fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun signOut() {
        viewModelScope.launch {
            SupabaseAuth.signOut()
            signedIn = false; workouts = emptyList(); meals = emptyList(); weights = emptyList(); exercises = emptyList(); water = emptyList(); progressPhotos = emptyList()
            profile = Profile(); loadedOnce = false; totalMeals = 0; allWorkoutDates = emptyList()
            onboardingSkipped = false; nudges = emptyList(); rolledYesterday = false
        }
    }
}
