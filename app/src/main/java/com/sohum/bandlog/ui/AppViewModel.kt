package com.sohum.bandlog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.AuthException
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SupabaseAuth
import com.sohum.bandlog.data.Workout
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

    val today: String get() = Dates.today()
    val workoutDates: List<String> get() = workouts.map { it.date }.distinct()
    val mealDates: List<String> get() = meals.map { it.date }.distinct()
    val weekStreak: Int get() = Streaks.workoutWeekStreak(workoutDates, profile.weeklyWorkoutTarget)
    val dayStreak: Int get() = Streaks.dayStreak(workoutDates)
    val mealStreak: Int get() = Streaks.dayStreak(mealDates)
    val thisWeek: Int get() = Streaks.thisWeekCount(workoutDates)

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
                    profile = p.await(); workouts = w.await(); meals = m.await()
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
    ) = mutate { Api.saveWorkout(id, date, muscles, band, kg, minutes, exercises, notes) }

    suspend fun deleteWorkout(id: String) = mutate { Api.deleteWorkout(id) }
    suspend fun saveMeal(date: String, raw: String, items: List<MealItem>) = mutate { Api.saveMeal(date, raw, items) }
    suspend fun deleteMeal(id: String) = mutate { Api.deleteMeal(id) }
    suspend fun saveTargets(p: Profile) = mutate { Api.saveProfile(p) }

    /** Fire a suspend mutation from a composable that has no scope of its own. */
    fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun signOut() {
        viewModelScope.launch {
            SupabaseAuth.signOut()
            signedIn = false; workouts = emptyList(); meals = emptyList(); profile = Profile(); loadedOnce = false
        }
    }
}
