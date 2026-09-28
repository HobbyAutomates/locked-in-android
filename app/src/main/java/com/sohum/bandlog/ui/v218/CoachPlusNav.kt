package com.sohum.bandlog.ui.v218

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.AuthException
import com.sohum.bandlog.data.DailyV
import com.sohum.bandlog.data.InsightsV
import com.sohum.bandlog.data.ModesV
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.SupplementsV
import com.sohum.bandlog.data.V218Api
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.util.Festival
import com.sohum.bandlog.util.HealthRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** v2.18 coach-stream pages, pushed over the tab shell by [CoachPlusOverlays]. */
enum class CoachPlusPage { HUB, SUPPLEMENTS, SPORTS, FORM_CHECK }

/**
 * v2.18 coach stream navigation (its own little stack, like CoachNav / PlatformNav, so MainActivity
 * needs one overlay line) plus the two bits of state Home reads: today's calorie bump (check-in
 * buffer / festival maintenance) and the festival dates that protect the day streak.
 */
object CoachPlusNav {
    const val OPEN_SUPPLEMENTS = "supplements"

    val stack = mutableStateListOf<CoachPlusPage>()
    val page: CoachPlusPage? get() = stack.lastOrNull()

    /** kcal added to today's target (0 until /api/coach/daily says otherwise). */
    var todayBump by mutableIntStateOf(0)
    /** Festival-mode dates up to today; they count toward the day streak. */
    val protectedDates = mutableStateListOf<String>()
    /** Bumped when the voice coach should open (Home's "Talk to coach"). */
    var voiceTick by mutableIntStateOf(0)

    fun open(p: CoachPlusPage) { if (stack.lastOrNull() == p) return; stack.remove(p); stack.add(p) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun closeAll() { stack.clear() }

    /** Opens the voice coach: the chat page, then the voice overlay on top. */
    fun openVoice() {
        closeAll()
        com.sohum.bandlog.ui.coach.CoachNav.open(com.sohum.bandlog.ui.coach.CoachPage.CHAT)
        voiceTick++
    }
}

@Composable
fun CoachPlusOverlays(vm: AppViewModel) {
    val cp: CoachPlusViewModel = viewModel()
    AnimatedContent(
        targetState = CoachPlusNav.page, label = "coachPlus",
        transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
    ) { page ->
        if (page == null) return@AnimatedContent
        val back: () -> Unit = { CoachPlusNav.back() }
        BackHandler { back() }
        when (page) {
            CoachPlusPage.HUB -> CoachHubScreen(vm, cp, back)
            CoachPlusPage.SUPPLEMENTS -> SupplementsScreen(cp, back)
            CoachPlusPage.SPORTS -> SportsScreen(vm, back)
            CoachPlusPage.FORM_CHECK -> FormCheckScreen(back)
        }
    }
}

/** State for the v2.18 coach surfaces. `null` availability = not loaded; false = hidden (not deployed / v43 missing). */
class CoachPlusViewModel : ViewModel() {
    var daily by mutableStateOf<DailyV?>(null); private set
    var dailyAvailable by mutableStateOf<Boolean?>(null); private set
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false); private set
    private var loadedAt = 0L

    private fun apply(d: DailyV) {
        daily = d
        dailyAvailable = true
        CoachPlusNav.todayBump = d.bump
        d.supplements?.let { supplements = it }
    }

    fun loadDaily(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - loadedAt < 5 * 60_000L && dailyAvailable != null) return
        loadedAt = now
        viewModelScope.launch {
            try { apply(V218Api.daily()) }
            catch (e: NotYetAvailable) { dailyAvailable = false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.i("LockedIn", "Coach daily: ${e.message}"); if (dailyAvailable == null) dailyAvailable = false }
            // Festival dates for the streak (cheap; hidden without v43).
            runCatching { V218Api.modes() }.getOrNull()?.let { m -> modes = m; CoachPlusNav.protectedDates.clear(); CoachPlusNav.protectedDates.addAll(Festival.protectedDates(m.modes, m.date)) }
        }
    }

    /** Saves the check-in, with last night's Health Connect sleep / resting HR when permitted. */
    fun checkin(ctx: Context, sleep: Double?, stress: Int?, mood: Int?, onSaved: () -> Unit = {}) {
        if (saving) return
        saving = true; error = null
        viewModelScope.launch {
            try {
                val night = if (HealthRecovery.available(ctx) && HealthRecovery.granted(ctx)) HealthRecovery.read(ctx) else null
                apply(V218Api.checkin(sleep ?: night?.sleepMin?.let { Math.round(it / 30.0) / 2.0 }, stress, mood, night?.sleepMin, night?.restingHr))
                onSaved()
            } catch (e: NotYetAvailable) { dailyAvailable = false }
            catch (e: CancellationException) { throw e }
            catch (e: AuthException) { error = e.message }
            catch (e: Exception) { error = e.message ?: "Couldn't save the check-in" }
            finally { saving = false }
        }
    }

    // ---- supplements ----
    var supplements by mutableStateOf<SupplementsV?>(null); private set
    var supplementsAvailable by mutableStateOf<Boolean?>(null); private set

    fun loadSupplements() {
        viewModelScope.launch {
            try { supplements = V218Api.supplements(); supplementsAvailable = true }
            catch (e: NotYetAvailable) { supplementsAvailable = false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message; if (supplementsAvailable == null) supplementsAvailable = false }
        }
    }

    fun supplementCall(ctx: Context, fn: suspend () -> SupplementsV) {
        viewModelScope.launch {
            try {
                val s = fn()
                supplements = s; supplementsAvailable = true
                daily = daily?.copy(supplements = s)
                com.sohum.bandlog.alarm.SupplementAlarms.reschedule(ctx, s.items)
            } catch (e: NotYetAvailable) { supplementsAvailable = false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Something went wrong" }
        }
    }

    // ---- insights ----
    var insights by mutableStateOf<InsightsV?>(null); private set
    var insightsAvailable by mutableStateOf<Boolean?>(null); private set

    fun loadInsights(generate: Boolean) {
        viewModelScope.launch {
            try { insights = V218Api.insights(generate); insightsAvailable = true }
            catch (e: NotYetAvailable) { insightsAvailable = false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.i("LockedIn", "Insights: ${e.message}"); if (insightsAvailable == null) insightsAvailable = false }
        }
    }

    // ---- festival / cycle ----
    var modes by mutableStateOf<ModesV?>(null); private set

    fun modesCall(fn: suspend () -> ModesV) {
        error = null
        viewModelScope.launch {
            try {
                val m = fn()
                modes = m
                CoachPlusNav.protectedDates.clear(); CoachPlusNav.protectedDates.addAll(Festival.protectedDates(m.modes, m.date))
                loadDaily(force = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Couldn't save that" }
        }
    }

    fun loadModes() { modesCall { V218Api.modes() } }
}
