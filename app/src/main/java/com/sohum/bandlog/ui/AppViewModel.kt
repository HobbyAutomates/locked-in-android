package com.sohum.bandlog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    val dayStreak: Int get() = Streaks.dayStreak(workoutDates)
    val mealStreak: Int get() = Streaks.dayStreak(mealDates)
    val thisWeek: Int get() = Streaks.thisWeekCount(workoutDates)

    /** Meals being parsed + saved in the background ("log now, review later"). Shown as shimmer rows. */
    var pendingMeals by mutableStateOf<List<String>>(emptyList()); private set
    /** Set when a workout save bumps the week count / streak; the shell shows the celebration modal. */
    var celebrate by mutableStateOf<Celebration?>(null); private set
    var savedMeals by mutableStateOf<List<com.sohum.bandlog.data.SavedMeal>>(emptyList()); private set

    data class Celebration(val streakWeeks: Int, val thisWeek: Int, val target: Int, val hitTarget: Boolean)

    fun dismissCelebration() { celebrate = null }

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
    var addBurnedBack by mutableStateOf(false)

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
            if (!com.sohum.bandlog.util.Health.available(ctx)) { healthConnected = false; return@launch }
            healthConnected = com.sohum.bandlog.util.Health.hasPermissions(ctx)
            if (healthConnected) {
                val r = com.sohum.bandlog.util.Health.todayResult(ctx)
                healthToday = r.getOrNull(); healthError = r.exceptionOrNull()?.let { "Health Connect read failed: ${it.message}" }
                // Stamp today's burn so the Weekly Energy chart has a history to draw.
                healthToday?.let { com.sohum.bandlog.util.BurnedCache.put(ctx, Dates.today(), it.activeKcal) }
            } else { healthToday = null; healthError = null }
        }
    }

    /** Called after a workout save; mirrors it into Health Connect when connected. */
    fun pushSessionToHealth(context: android.content.Context, muscles: List<String>, minutes: Int?, date: String) {
        if (!healthConnected) return
        viewModelScope.launch { com.sohum.bandlog.util.Health.writeSession(context.applicationContext, "Bands: " + muscles.joinToString(", "), minutes ?: 30, date) }
    }

    /** Cal AI-style optimistic log: the row appears instantly; Haiku prices it in the background. */
    fun quickLogMeal(text: String, date: String) {
        pendingMeals = pendingMeals + text
        viewModelScope.launch {
            try {
                val parsed = Api.parseMeal(text)
                if (parsed.items.isNotEmpty()) Api.saveMeal(date, text, parsed.items)
                else error = "Couldn't find any food in “${text.take(40)}”"
                refresh()
            } catch (e: Exception) { error = e.message ?: "Couldn't log that meal" }
            finally { pendingMeals = pendingMeals - text }
        }
    }

    fun loadSavedMeals() { viewModelScope.launch { runCatching { savedMeals = Api.savedMeals() } } }

    fun onSignedIn() { signedIn = true; refresh() }

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
                    profile = p.await(); workouts = w.await(); meals = m.await(); weights = g.await(); exercises = x.await()
                }
                loadedOnce = true
            } catch (e: AuthException) {
                signedIn = false; error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load"
            } finally { loading = false }
        }
    }

    fun clearError() { error = null }

    /** Runs [block] then refreshes; surfaces failures in [error]. Returns true on success. */
    private suspend fun mutate(block: suspend () -> Unit): Boolean = try {
        block(); refresh(); true
    } catch (e: AuthException) { signedIn = false; error = e.message; false }
    catch (e: Exception) { error = e.message ?: "Something went wrong"; false }

    suspend fun saveWorkout(
        id: String?, date: String, muscles: List<String>, band: String,
        kg: Double?, minutes: Int?, exercises: String, notes: String,
    ): Boolean {
        val before = thisWeek; val beforeStreak = weekStreak
        val ok = mutate {
            val wid = Api.saveWorkout(id, date, muscles, band, kg, minutes, exercises, notes)
            // Auto-burn: one exercise_log row per workout, tagged with the workout id in `note`.
            // An edit replaces the row so minutes / band changes flow through to the burn.
            runCatching {
                if (id != null) Api.deleteWorkoutBurn(wid)
                val mins = (minutes ?: 30).coerceAtLeast(1)
                Api.saveExercise(date, Burn.bandCode(band), "Bands: " + muscles.joinToString(", "), mins, Burn.bandIntensity(band), Burn.bandKcal(band, profile.weightKg, mins), "workout", wid)
            }
        }
        // refresh() runs async inside mutate; wait for it so the counts below are fresh.
        if (ok && id == null) {
            kotlinx.coroutines.delay(50)
            while (loading) kotlinx.coroutines.delay(50)
            val target = profile.weeklyWorkoutTarget
            if (thisWeek > before) celebrate = Celebration(weekStreak, thisWeek, target, hitTarget = thisWeek >= target && before < target || weekStreak > beforeStreak)
        }
        return ok
    }

    suspend fun deleteWorkout(id: String) = mutate { runCatching { Api.deleteWorkoutBurn(id) }; Api.deleteWorkout(id) }

    /** A burn logged from the Exercise tab (run, bands, an activity, a description, or a manual number). */
    suspend fun saveExercise(date: String, activityCode: String?, name: String, minutes: Int, intensity: String, kcal: Double, source: String) =
        mutate { Api.saveExercise(date, activityCode, name, minutes, intensity, kcal, source) }
    suspend fun deleteExercise(id: String) = mutate { Api.deleteExercise(id) }
    /** Every activity Haiku found in a description, saved as its own row. */
    suspend fun saveDescribed(date: String, items: List<com.sohum.bandlog.data.DescribedExercise>) = mutate {
        items.forEach { Api.saveExercise(date, it.activityCode, it.name, it.minutes, it.intensity, it.kcal, "describe") }
    }
    suspend fun saveMeal(date: String, raw: String, items: List<MealItem>, photoPath: String? = null) = mutate { Api.saveMeal(date, raw, items, photoPath) }
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
            signedIn = false; workouts = emptyList(); meals = emptyList(); weights = emptyList(); exercises = emptyList()
            profile = Profile(); loadedOnce = false; totalMeals = 0; allWorkoutDates = emptyList()
            onboardingSkipped = false
        }
    }
}
