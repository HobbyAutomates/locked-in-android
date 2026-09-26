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

    /** What a switch would do: new macro targets at the same calories, or null without weight / DOB. */
    fun previewMode(p: Profile, mode: String): Goals.Targets? =
        DietModes.targets(mode, p.calorieTarget, Goals.ageYears(p.dob), p.weightKg, p.gender, p.weeklyWorkoutTarget)

    data class ModeUndo(val mode: String, val profile: Profile)

    /**
     * Switches the diet mode and recomputes protein / carbs / fat for it (calories unchanged). Returns
     * the undo snapshot, or null when it couldn't save (the reason is in [message]).
     */
    suspend fun setDietMode(vm: AppViewModel, mode: String): ModeUndo? {
        val p = vm.profile
        val age = Goals.ageYears(p.dob)
        if (!DietModes.allowed(mode, age)) { message = DietModes.TEEN_NOT_RECOMMENDED; return null }
        val t = previewMode(p, mode) ?: run { message = "Add your weight and date of birth in Personal details first."; return null }
        val before = ModeUndo(settings.dietMode, p)
        try {
            NutritionApi.patchSettings(JSONObject().put("diet_mode", mode))
        } catch (e: NotYetAvailable) { message = NotYetAvailable.COMING; return null }
        catch (e: Exception) { message = e.message ?: "Couldn't save that"; return null }
        settings = settings.copy(dietMode = mode)
        val ok = vm.saveProfile(p.copy(proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat))
        if (!ok) {
            runCatching { NutritionApi.patchSettings(JSONObject().put("diet_mode", before.mode)) }
            settings = settings.copy(dietMode = before.mode)
            message = vm.error ?: "Couldn't save the new targets"
            return null
        }
        return before
    }

    /** Puts the previous mode and the previous targets back. */
    suspend fun undoDietMode(vm: AppViewModel, u: ModeUndo): Boolean {
        runCatching { NutritionApi.patchSettings(JSONObject().put("diet_mode", u.mode)) }.onFailure { message = it.message; return false }
        settings = settings.copy(dietMode = u.mode)
        val cur = vm.profile
        return vm.saveProfile(cur.copy(proteinTargetG = u.profile.proteinTargetG, carbTargetGSet = u.profile.carbTargetGSet, fatTargetGSet = u.profile.fatTargetGSet))
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
    fun checkinHandled(ctx: Context, week: String): Boolean = prefs(ctx).getBoolean("checkin_done_$week", false)
    private fun markHandled(ctx: Context, week: String) { prefs(ctx).edit().putBoolean("checkin_done_$week", true).apply() }

    /** Floor / ceiling for a profile: Goals' floor; under 18 never under maintenance (no deficit), at most maintenance + the growth surplus. */
    private fun bounds(p: Profile): Pair<Int, Int> {
        val floor = Goals.floorFor(p)
        val plan = Goals.plan(p) ?: return floor to 6000
        if (!plan.teen) return floor to 6000
        val eer = plan.maintenance
        return maxOf(floor, eer.roundToInt()) to (eer + Goals.teenGainSurplus(eer)).roundToInt()
    }

    fun inputFor(p: Profile, meals: List<Meal>, weights: List<com.sohum.bandlog.data.WeightEntry>, weekStart: String): Adaptive.Input {
        val end = Adaptive.endFor(weekStart)
        val days = (0 until Adaptive.SERIES_DAYS).map { Dates.addDays(end, -it.toLong()) }
        val kcal = days.associateWith { d -> totalsFor(meals, d).calories }.filterValues { it > 0 }
        val (floor, ceiling) = bounds(p)
        val teen = Goals.isTeen(Goals.ageYears(p.dob))
        return Adaptive.Input(
            endDate = end,
            weights = weights.map { Adaptive.WeighIn(it.date, it.weightKg) },
            kcalByDay = kcal,
            goalRateKgPerWeek = Adaptive.goalRate(Goals.effectiveGoal(p.goalType, Goals.ageYears(p.dob)), p.goalSpeedKgWk, p.weightKg, teen),
            oldTarget = p.calorieTarget,
            floor = floor,
            ceiling = ceiling,
        )
    }

    /**
     * Monday (or the first open after it): computes this week's check-in once and stores it in
     * weekly_checkins. Only when adaptive targets are on and schema_v36 is there.
     */
    fun ensureCheckin(vm: AppViewModel) {
        if (!settings.supported || !settings.adaptiveTargets || !vm.loadedOnce) return
        val week = Adaptive.weekStart(vm.today)
        if (checkinWeek == week) return
        checkinWeek = week
        viewModelScope.launch {
            val weights = if (vm.weights.isEmpty()) runCatching { com.sohum.bandlog.data.Api.weights() }.getOrDefault(emptyList()) else vm.weights
            val res = Adaptive.check(inputFor(vm.profile, vm.meals, weights, week))
            checkinResult = res
            val existing = runCatching { NutritionApi.checkins(4) }.getOrNull()?.firstOrNull { it.weekStart == week }
            if (existing != null) { checkin = existing; return@launch }
            val row = WeeklyCheckin(
                null, week, res.avgWeightKg, res.trendKgPerWeek, res.avgKcal?.roundToInt(), res.oldTarget, res.newTarget, res.reason, applied = false,
            )
            checkin = runCatching { NutritionApi.upsertCheckin(row) }.getOrDefault(row)
        }
    }

    /** Apply: the new calorie target, macros recomputed per the diet mode; applied = true. */
    suspend fun applyCheckin(ctx: Context, vm: AppViewModel): Boolean {
        val c = checkin ?: return false
        val target = c.newTarget ?: return false
        val p = vm.profile
        val mode = dietMode(p)
        val age = Goals.ageYears(p.dob)
        val t = DietModes.targets(mode, target, age, p.weightKg, p.gender, p.weeklyWorkoutTarget) ?: Goals.macrosFor(target.toDouble(), p.proteinTargetG)
        val ok = vm.saveProfile(p.copy(calorieTarget = target, proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat))
        if (!ok) { message = vm.error ?: "Couldn't apply it"; return false }
        runCatching { NutritionApi.markCheckinApplied(c.weekStart, true) }
        checkin = c.copy(applied = true)
        markHandled(ctx, c.weekStart)
        return true
    }

    fun keepCheckin(ctx: Context) {
        val c = checkin ?: return
        markHandled(ctx, c.weekStart)
        checkin = c.copy()
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

    /** Today's remaining macros (never below 0). */
    fun remaining(vm: AppViewModel): WhatToEat.Remaining {
        val t = totalsFor(vm.meals, vm.today)
        val p = vm.profile
        return WhatToEat.Remaining(
            (vm.budgetToday - t.calories).coerceAtLeast(0.0), (p.proteinTargetG - t.protein).coerceAtLeast(0.0),
            (p.carbTargetG - t.carbs).coerceAtLeast(0.0), (p.fatTargetG - t.fat).coerceAtLeast(0.0),
        )
    }

    /** Presets as what-to-eat candidates at their default serving. */
    fun candidates(vm: AppViewModel): List<Pair<WhatToEat.Food, com.sohum.bandlog.data.FoodPreset>> = vm.presets.filter { it.category != "fat" }.map { pr ->
        val s = pr.default
        val g = s?.grams ?: 100.0
        val k = g / 100
        WhatToEat.Food(
            pr.label, pr.category, s?.label ?: "100 g", g, pr.calories * k, pr.proteinG * k, pr.carbsG * k, pr.fatG * k,
            pr.foodId, pr.micros.mapValues { it.value * k }, pr.imageUrl,
        ) to pr
    }

    fun picks(vm: AppViewModel, n: Int = 5): List<WhatToEat.Pick> =
        WhatToEat.rank(candidates(vm).map { it.first }, remaining(vm), MealTypes.default(), dietMode(vm.profile), n)

    /** "Your usual": the user's own frequent foods over the loaded window. */
    fun usual(vm: AppViewModel): List<WhatToEat.Usual> {
        val logged = vm.meals.filter { Dates.daysBetween(it.date, vm.today) <= 60 }.flatMap { m ->
            m.items.filter { it.grams > 0 && it.calories > 0 }.map { i ->
                m.createdAt to WhatToEat.Food(i.name, null, i.quantityLabel, i.grams, i.calories, i.proteinG, i.carbsG, i.fatG, i.foodId, i.micros, i.imageUrl)
            }
        }
        return WhatToEat.usual(logged, dietMode(vm.profile))
    }

    fun itemFor(f: WhatToEat.Food, vm: AppViewModel): MealItem {
        val pr = vm.presets.firstOrNull { it.foodId == f.foodId && it.category != "fat" }
        if (pr != null) return com.sohum.bandlog.util.QuantityFood.from(pr).item(com.sohum.bandlog.util.Quantity(com.sohum.bandlog.util.QUnit.G, f.grams))
        return MealItem(
            foodId = f.foodId, name = f.name, grams = f.grams, calories = f.kcal, proteinG = f.protein, carbsG = f.carbs, fatG = f.fat,
            source = "table", confidence = 1.0, micros = f.micros, imageUrl = f.imageUrl,
        )
    }

    /** The meal the sheet logs into when opened from Add food (its chosen type); null = the hour rule. */
    var pickType by mutableStateOf<String?>(null)

    /** One tap: logs [f] into [pickType], else the meal type for this time of day. */
    suspend fun logPick(vm: AppViewModel, f: WhatToEat.Food): Boolean {
        val type = pickType?.takeIf { MealTypes.isType(it) } ?: MealTypes.default()
        val ok = vm.saveMeal(vm.today, f.name, listOf(itemFor(f, vm)), mealType = type, method = "what_to_eat")
        message = if (ok) "Logged ${f.name} to ${MealTypes.label(type)}" else vm.error
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
            } catch (e: Exception) {
                moved = moved - meal.id
                message = "Couldn't move it: ${e.message ?: "try again"}"
            }
        }
    }
}
