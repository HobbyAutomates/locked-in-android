package com.sohum.bandlog.ui.nutrition

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohum.bandlog.alarm.FastingAlarm
import com.sohum.bandlog.data.FastingSession
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.NutritionApi
import com.sohum.bandlog.data.NutritionSettings
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Recipe
import com.sohum.bandlog.data.WeeklyCheckin
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.util.Adaptive
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.DietModes
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.WhatToEat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/** Full-screen nutrition pages drawn by [NutritionOverlays] over the tab shell. */
sealed class NutritionPage {
    data object Fasting : NutritionPage()
    data object Recipes : NutritionPage()
    data class RecipeEdit(val recipe: Recipe?) : NutritionPage()
    data object Micros : NutritionPage()
    data object WhatToEat : NutritionPage()
}

/**
 * v2.13 nutrition state (diet mode, weekly check-in, fasting, recipes, meal moves). Lives beside
 * [AppViewModel] (same activity store, `viewModel()` anywhere returns this one) and reads the
 * profile / meals / weights from it, so AppViewModel.kt stays untouched for the platform half.
 */
class NutritionViewModel : ViewModel() {

    var page by mutableStateOf<NutritionPage?>(null)
    /** A short message for the page that's open ("Moved to Lunch", "Couldn't …"). */
    var message by mutableStateOf<String?>(null)

    // ---------------------------------------------------------------- settings (§4, §5, §7)

    var settings by mutableStateOf(NutritionSettings()); private set
    var settingsLoaded by mutableStateOf(false); private set

    fun loadSettings(force: Boolean = false) {
        if (settingsLoaded && !force) return
        viewModelScope.launch {
            runCatching { NutritionApi.settings() }.onSuccess { settings = it; settingsLoaded = true }
        }
    }

    /** The mode the maths uses for this profile (adult-only modes fall back to balanced under 18). */
    fun dietMode(p: Profile): String = DietModes.effective(settings.dietMode, Goals.ageYears(p.dob))

    /** What a switch would do: new macro targets at the same calories (DietModes.targets, as the web's dietTargets). */
    fun previewMode(p: Profile, mode: String): Goals.Targets = DietModes.targets(p, p.calorieTarget.toDouble(), mode)

    data class ModeUndo(val mode: String, val protein: Int, val carbs: Int, val fat: Int)

    /**
     * Switches the diet mode and recomputes protein / carbs / fat for it (calories unchanged), in one
     * profile update like the web's setDietMode. Returns the undo snapshot, or null (reason in [message]).
     */
    suspend fun setDietMode(vm: AppViewModel, mode: String): ModeUndo? {
        if (!DietModes.isMode(mode)) { message = "Pick a diet from the list"; return null }
        val p = vm.profile
        if (!DietModes.allowed(mode, Goals.ageYears(p.dob))) { message = DietModes.NOT_FOR_TEENS; return null }
        val before = ModeUndo(settings.dietMode, p.proteinTargetG, p.carbTargetG, p.fatTargetG)
        val t = previewMode(p, mode)
        try {
            NutritionApi.patchSettings(JSONObject().put("diet_mode", mode).put("protein_target_g", t.protein).put("carb_target_g", t.carbs).put("fat_target_g", t.fat))
        } catch (e: NotYetAvailable) { message = NotYetAvailable.COMING; return null }
        catch (e: Exception) { message = e.message ?: "Could not switch the diet"; return null }
        settings = settings.copy(dietMode = mode)
        // Same values again through the normal save, so every screen shows them at once.
        vm.saveProfile(p.copy(proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat))
        return before
    }

    /** Undo a switch: the old mode and the old macro targets exactly as they were. */
    suspend fun undoDietMode(vm: AppViewModel, u: ModeUndo): Boolean {
        try {
            NutritionApi.patchSettings(JSONObject().put("diet_mode", u.mode).put("protein_target_g", u.protein).put("carb_target_g", u.carbs).put("fat_target_g", u.fat))
        } catch (e: Exception) { message = if (e is NotYetAvailable) NotYetAvailable.COMING else e.message ?: "Could not undo that"; return false }
        settings = settings.copy(dietMode = u.mode)
        return vm.saveProfile(vm.profile.copy(proteinTargetG = u.protein, carbTargetGSet = u.carbs, fatTargetGSet = u.fat))
    }

    suspend fun setAdaptive(on: Boolean): Boolean {
        val before = settings
        settings = settings.copy(adaptiveTargets = on)
        return try { NutritionApi.patchSettings(JSONObject().put("adaptive_targets", on)); true }
        catch (e: Exception) { settings = before; message = if (e is NotYetAvailable) NotYetAvailable.COMING else e.message; false }
    }

    // ---------------------------------------------------------------- §5 weekly check-in

    var checkin by mutableStateOf<WeeklyCheckin?>(null); private set
    var checkinResult by mutableStateOf<Adaptive.Result?>(null); private set
    private var checkinWeek: String? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nutrition_v213", Context.MODE_PRIVATE)
    /** Bumped when a check-in is answered, so the card (which reads SharedPreferences) recomposes. */
    private var handledTick by androidx.compose.runtime.mutableIntStateOf(0)
    fun checkinHandled(ctx: Context, week: String): Boolean { handledTick; return prefs(ctx).getBoolean("checkin_done_$week", false) }
    private fun markHandled(ctx: Context, week: String) { prefs(ctx).edit().putBoolean("checkin_done_$week", true).apply(); handledTick++ }

    /** Daily kcal (rounded) on the days with at least one food log, from [from] to [to]. Same as the web's dayKcalOf. */
    fun dayKcalOf(meals: List<Meal>, from: String, to: String): Map<String, Double> =
        meals.filter { it.date in from..to && it.items.isNotEmpty() }.map { it.date }.distinct()
            .associateWith { d -> kotlin.math.floor(totalsFor(meals, d).calories + 0.5) }

    /**
     * This week's check-in, computed and stored on the first open on / after Monday when adaptive
     * targets are on and there's enough data (not enough: [checkinResult] says what's missing and
     * nothing is stored). Never applies anything. Mirrors the web's getWeeklyCheckin.
     */
    fun ensureCheckin(vm: AppViewModel) {
        if (!settings.supported || !settings.adaptiveTargets || !vm.loadedOnce) return
        val (weekStart, asOf) = Adaptive.checkinWeek(vm.today)
        if (checkinWeek == weekStart) return
        checkinWeek = weekStart
        viewModelScope.launch {
            val existing = try { NutritionApi.checkins(4).firstOrNull { it.weekStart == weekStart } }
            catch (e: NotYetAvailable) { return@launch } catch (e: Exception) { checkinWeek = null; return@launch }
            if (existing != null) { checkin = existing; return@launch }
            val weights = runCatching { com.sohum.bandlog.data.Api.weights() }.getOrDefault(vm.weights)
            val p = vm.profile
            val res = Adaptive.weeklyCheckin(
                asOf, weights.map { Adaptive.WeighIn(it.date, it.weightKg) }, dayKcalOf(vm.meals, Dates.addDays(asOf, -13), asOf),
                Adaptive.goalRateFor(p), p.calorieTarget, Adaptive.adaptiveFloor(p),
            )
            checkinResult = res
            if (!res.ok) return@launch
            val row = WeeklyCheckin(null, weekStart, res.avgWeightKg, res.trendKgPerWeek, res.avgKcal, res.oldTarget, res.newTarget, res.reason, applied = false)
            checkin = runCatching { NutritionApi.upsertCheckin(row) }.getOrDefault(row)
        }
    }

    /** The not-enough-data note, until it's dismissed for the week. */
    fun pendingVisible(ctx: Context): Boolean = checkin == null && checkinResult?.let { !it.ok && !checkinHandled(ctx, "pending-" + it.asOf) } == true
    fun dismissPending(ctx: Context) { checkinResult?.let { markHandled(ctx, "pending-" + it.asOf) } }

    fun checkinVisible(ctx: Context): Boolean = checkin?.let { !it.applied && !checkinHandled(ctx, it.weekStart) } == true || pendingVisible(ctx)

    /** Apply: calorie_target = new_target (never under the floor), macros recomputed for the diet mode, applied = true. */
    suspend fun applyCheckin(ctx: Context, vm: AppViewModel): Boolean {
        val c = checkin ?: return false
        val target = c.newTarget ?: return false
        val p = vm.profile
        val (cal, _) = Goals.capToFloor(target, Goals.floorFor(p))
        val t = DietModes.targets(p, cal.toDouble(), dietMode(p))
        val ok = vm.saveProfile(p.copy(calorieTarget = t.calories, proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat))
        if (!ok) { message = vm.error ?: "Could not update your target"; return false }
        runCatching { NutritionApi.markCheckinApplied(c.weekStart, true) }.onFailure { message = "Updated your target, but couldn't mark the check-in" }
        checkin = c.copy(applied = true)
        markHandled(ctx, c.weekStart)
        return true
    }

    fun keepCheckin(ctx: Context) {
        val c = checkin ?: return
        markHandled(ctx, c.weekStart)
    }

    // ---------------------------------------------------------------- §7 fasting

    var fasts by mutableStateOf<List<FastingSession>>(emptyList()); private set
    var fastingUnavailable by mutableStateOf(false); private set
    var fastingLoaded by mutableStateOf(false); private set
    /** The running fast (server row, else the local mirror). */
    var active by mutableStateOf<FastingAlarm.Local?>(null); private set

    fun loadFasting(ctx: Context) {
        active = FastingAlarm.load(ctx)
        viewModelScope.launch {
            try {
                val list = NutritionApi.fasts(15)
                fasts = list; fastingUnavailable = false
                val running = list.firstOrNull { it.running }
                val local = running?.let { FastingAlarm.Local(it.id, it.startedAtMs, it.targetHours) }
                // The server is the truth once it answers (a fast ended on the web ends here too).
                FastingAlarm.save(ctx, local); FastingAlarm.schedule(ctx, local); active = local
            } catch (e: NotYetAvailable) { fastingUnavailable = true }
            catch (e: Exception) { message = e.message }
            fastingLoaded = true
        }
    }

    suspend fun startFast(ctx: Context, hours: Double): Boolean {
        val h = com.sohum.bandlog.util.Fasting.clampHours(hours)
        val now = System.currentTimeMillis()
        return try {
            val row = NutritionApi.startFast(now, h)
            val local = FastingAlarm.Local(row.id, row.startedAtMs, row.targetHours)
            FastingAlarm.save(ctx, local); FastingAlarm.schedule(ctx, local); active = local
            fasts = listOf(row) + fasts
            runCatching { NutritionApi.patchSettings(JSONObject().put("fasting_hours", h)) }.onSuccess { settings = settings.copy(fastingHours = h) }
            true
        } catch (e: NotYetAvailable) { fastingUnavailable = true; false }
        catch (e: Exception) { message = e.message ?: "Couldn't start the fast"; false }
    }

    suspend fun endFast(ctx: Context): Boolean {
        val a = active ?: return false
        val now = System.currentTimeMillis()
        return try {
            if (a.id.isNotBlank()) NutritionApi.endFast(a.id, now)
            FastingAlarm.save(ctx, null); FastingAlarm.schedule(ctx, null); active = null
            fasts = fasts.map { if (it.id == a.id) it.copy(endedAtMs = now) else it }
            true
        } catch (e: Exception) { message = e.message ?: "Couldn't end the fast"; false }
    }

    suspend fun deleteFast(id: String) {
        val before = fasts
        fasts = fasts.filter { it.id != id }
        runCatching { NutritionApi.deleteFast(id) }.onFailure { fasts = before; message = it.message }
    }

    // ---------------------------------------------------------------- §8 recipes

    var recipes by mutableStateOf<List<Recipe>>(emptyList()); private set
    var recipesUnavailable by mutableStateOf(false); private set
    var recipesLoaded by mutableStateOf(false); private set

    fun loadRecipes() {
        viewModelScope.launch {
            try { recipes = NutritionApi.recipes(); recipesUnavailable = false }
            catch (e: NotYetAvailable) { recipesUnavailable = true }
            catch (e: Exception) { message = e.message }
            recipesLoaded = true
        }
    }

    suspend fun saveRecipe(r: Recipe): Recipe? = try {
        val saved = NutritionApi.saveRecipe(r)
        recipes = listOf(saved) + recipes.filter { it.id != saved.id && (it.id != null || it.name != saved.name) }
        saved
    } catch (e: NotYetAvailable) { recipesUnavailable = true; message = NotYetAvailable.COMING; null }
    catch (e: Exception) { message = e.message ?: "Couldn't save the recipe"; null }

    suspend fun deleteRecipe(id: String): Boolean = try {
        NutritionApi.deleteRecipe(id); recipes = recipes.filter { it.id != id }; true
    } catch (e: Exception) { message = e.message ?: "Couldn't delete it"; false }

    /** "Log a serving": one meal row named after the recipe, in the meal type for now. */
    suspend fun logRecipe(vm: AppViewModel, r: Recipe, servings: Double = 1.0, mealType: String = MealTypes.default()): Boolean {
        val ok = vm.saveMeal(vm.today, "Your recipe: ${r.name}", listOf(r.servingItem(servings)), mealType = mealType, method = "recipe")
        message = if (ok) "Logged ${com.sohum.bandlog.ui.today.fmt(servings)} serving of ${r.name}" else vm.error
        return ok
    }

    // ---------------------------------------------------------------- §6 what to eat

    /** Today's remaining macros (never below 0, whole numbers): the calorie budget (burned + rollover) and the macro targets. */
    fun remaining(vm: AppViewModel): WhatToEat.Remaining {
        val t = totalsFor(vm.meals, vm.today)
        val p = vm.profile
        return WhatToEat.remainingFrom(vm.budgetToday, p.proteinTargetG.toDouble(), p.carbTargetG.toDouble(), p.fatTargetG.toDouble(), t.calories, t.protein, t.carbs, t.fat)
    }

    /** Home shows the card once something is logged today and at least 150 kcal are left. */
    fun whatToEatVisible(vm: AppViewModel): Boolean =
        vm.meals.any { it.date == vm.today } && remaining(vm).kcal >= 150 && vm.presets.isNotEmpty()

    private fun mealTypeNow(): String = pickType?.takeIf { MealTypes.isType(it) } ?: MealTypes.default()

    fun picks(vm: AppViewModel, n: Int = 5): List<WhatToEat.Suggestion> =
        WhatToEat.suggest(remaining(vm), dietMode(vm.profile), mealTypeNow(), vm.presets, n)

    /** "Your usual": most-eaten presets (60 days) that fit, never ones already in [shown]. */
    fun usual(vm: AppViewModel, shown: List<WhatToEat.Suggestion>): List<WhatToEat.Suggestion> =
        WhatToEat.usual(remaining(vm), dietMode(vm.profile), mealTypeNow(), vm.presets, vm.foodUse(60), shown.map { it.preset.id })

    /** The meal the sheet logs into when opened from Add food (its chosen type); null = the hour rule. */
    var pickType by mutableStateOf<String?>(null)

    /** One tap: logs the suggestion (priced like a one-tap preset add) into [pickType], else the meal type for now. */
    suspend fun logPick(vm: AppViewModel, sg: WhatToEat.Suggestion): Boolean {
        val type = mealTypeNow()
        val ok = vm.saveMeal(vm.today, sg.preset.label, listOf(sg.item), mealType = type, method = "what_to_eat")
        message = if (ok) "Logged ${sg.preset.label} to ${MealTypes.label(type)}" else vm.error
        return ok
    }

    // ---------------------------------------------------------------- §14 move a meal

    /** mealId → the section it was just moved to (shown straight away, until the refresh lands). */
    var moved by mutableStateOf<Map<String, String>>(emptyMap()); private set

    fun withMoves(meals: List<Meal>): List<Meal> = if (moved.isEmpty()) meals else meals.map { m -> moved[m.id]?.let { m.copy(mealType = it) } ?: m }

    fun moveMeal(vm: AppViewModel, meal: Meal, type: String) {
        if (!MealTypes.isType(type) || MealTypes.of(meal) == type) return
        moved = moved + (meal.id to type)
        viewModelScope.launch {
            try {
                NutritionApi.moveMeal(meal.id, type)
                message = "Moved to ${MealTypes.label(type)}"
                vm.refresh()
                // Drop the override once the refreshed list agrees (a later edit must win over it).
                kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    androidx.compose.runtime.snapshotFlow { vm.meals.firstOrNull { it.id == meal.id }?.let { MealTypes.of(it) } }.first { it == type || it == null }
                }
                moved = moved - meal.id
            } catch (e: Exception) {
                moved = moved - meal.id
                message = "Couldn't move it: ${e.message ?: "try again"}"
            }
        }
    }
}
