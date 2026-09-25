package com.sohum.bandlog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.UpdateDialog
import com.sohum.bandlog.ui.UpdateViewModel
import com.sohum.bandlog.ui.calendar.CalendarScreen
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.log.LogScreen
import com.sohum.bandlog.ui.login.LoginScreen
import com.sohum.bandlog.ui.onboarding.OnboardingScreen
import com.sohum.bandlog.ui.profile.GoalWeightScreen
import com.sohum.bandlog.ui.profile.NutritionGoalsScreen
import com.sohum.bandlog.ui.profile.PersonalDetailsScreen
import com.sohum.bandlog.ui.profile.ProfilePage
import com.sohum.bandlog.ui.profile.ProfileScreen
import com.sohum.bandlog.ui.profile.RemindersScreen
import com.sohum.bandlog.ui.profile.WeightHistoryScreen
import com.sohum.bandlog.ui.progress.BadgesScreen
import com.sohum.bandlog.ui.progress.ProgressScreen
import com.sohum.bandlog.ui.theme.BandLogTheme
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.TodayScreen
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.ThemeMode
import com.sohum.bandlog.util.ThemePrefs

class MainActivity : ComponentActivity() {

    /** Bumped when a meal-reminder notification is tapped; the shell opens Log → Meal. */
    private val openMealTick = mutableIntStateOf(0)
    /** Bumped when the 9 pm wrap notification is tapped; the shell lands on Home with the Wrap card. */
    private val openWrapTick = mutableIntStateOf(0)
    /** v2.6: bumped when a water reminder is tapped; the shell opens the Water page. */
    private val openWaterTick = mutableIntStateOf(0)
    /** v2.6: the code of a tapped invite link (…/join/<code>); the shell joins it on the Squad tab. */
    private val joinCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        handleIntent(intent)
        // A reboot or update clears AlarmManager; re-arm here too in case the receiver was missed.
        runCatching { com.sohum.bandlog.alarm.MealAlarms.rescheduleAll(this) }
        runCatching { com.sohum.bandlog.alarm.WaterAlarms.reschedule(this) }
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(ThemePrefs.get(this)) }
            val dark = when (themeMode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.AUTO -> isSystemInDarkTheme() }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            }
            BandLogTheme(dark = dark) {
                val vm: AppViewModel = viewModel()
                val updateVm: UpdateViewModel = viewModel()
                LaunchedEffect(Unit) { updateVm.checkOnce(); vm.localBurned = ThemePrefs.burned(this@MainActivity); vm.localRollover = ThemePrefs.rollover(this@MainActivity); vm.celebrationsOn = ThemePrefs.celebrations(this@MainActivity); if (vm.signedIn) { vm.refresh(); vm.refreshHealth(this@MainActivity) } }
                Surface(Modifier.fillMaxSize(), color = palette.bg) {
                    when {
                        !vm.signedIn -> LoginScreen(reason = vm.signOutReason, onSignedIn = { vm.onSignedIn() })
                        // Hold the mark up for the moment between sign-in and the first profile read,
                        // so a brand-new account never flashes the empty tab shell.
                        !vm.loadedOnce && vm.error == null -> BootSplash()
                        vm.needsOnboarding -> OnboardingScreen(vm, onDone = {}, onSkip = { vm.onboardingSkipped = true })
                        else -> MainShell(vm, updateVm, themeMode, openMealTick.intValue, openWrapTick.intValue, openWaterTick.intValue, joinCode.value, { joinCode.value = null }) { themeMode = it; ThemePrefs.set(this, it) }
                    }
                }
                UpdateDialog(updateVm)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: android.content.Intent?) {
        when (i?.getStringExtra(EXTRA_OPEN)) {
            OPEN_MEAL -> openMealTick.intValue++
            OPEN_WRAP -> { runCatching { com.sohum.bandlog.util.Wrap.undismiss(this) }; openWrapTick.intValue++ }
            OPEN_WATER -> openWaterTick.intValue++
        }
        // v2.6 invite link: https://web-production-ff1cf.up.railway.app/join/<code>
        i?.data?.takeIf { i.action == android.content.Intent.ACTION_VIEW }?.let { uri ->
            val segs = uri.pathSegments
            val at = segs.indexOf("join")
            segs.getOrNull(at + 1)?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { at >= 0 && it.length in 4..12 }?.let { joinCode.value = it }
            i.data = null
        }
        // Consume it so a rotation / re-delivery doesn't reopen the same thing.
        i?.removeExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_MEAL = "meal"
        const val OPEN_WRAP = "wrap"
        const val OPEN_WATER = "water"
    }
}

/** The mark on the page while the first profile read is in flight. */
@Composable
private fun BootSplash() {
    val p = palette
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Icon(com.sohum.bandlog.ui.components.LockIcon, null, tint = p.ink, modifier = Modifier.size(56.dp))
    }
}

/** What the Log page was opened with. */
private data class LogRequest(val workout: Workout?, val date: String, val meal: Boolean, val exercise: Boolean = false)

private data class Tab(val label: String, val icon: ImageVector)

/** A full-screen page pushed over the tab shell (Profile detail screens, Badges). */
private enum class Page { WATER, PERSONAL, GOALS, GOAL_WEIGHT, REMINDERS, WEIGHT_HISTORY, WEIGHT_LOG, BADGES, CALENDAR, PREFERENCES, APPEARANCE, TRACKING, PRIVACY, ACCOUNT }

/** v2.4: pages opened from Preferences go back to Preferences; everything else closes. */
private fun parentOf(page: Page, fromPrefs: Boolean): Page? = when (page) {
    Page.APPEARANCE, Page.TRACKING, Page.PRIVACY, Page.ACCOUNT -> Page.PREFERENCES
    Page.REMINDERS -> if (fromPrefs) Page.PREFERENCES else null
    else -> null
}

/** v2.3: the + button's speed-dial entries. */
private enum class DialItem(val label: String) { MEAL("Meal"), WORKOUT("Workout"), EXERCISE("Exercise"), WATER("Water"), WEIGHT("Weight") }

@Composable
private fun MainShell(vm: AppViewModel, updateVm: UpdateViewModel, themeMode: ThemeMode, openMealTick: Int, openWrapTick: Int, openWaterTick: Int, joinCode: String?, onJoinHandled: () -> Unit, onThemeMode: (ThemeMode) -> Unit) {
    val p = palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sq: com.sohum.bandlog.ui.squad.SquadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var log by remember { mutableStateOf<LogRequest?>(null) }
    // v2.8: Add food for a meal type, or a logged meal opened in the editor (ui/log/MealScreen).
    var meal by remember { mutableStateOf<com.sohum.bandlog.ui.log.MealRequest?>(null) }
    var page by remember { mutableStateOf<Page?>(null) }
    // Reminders is reachable from Preferences; remember that so Back returns there.
    var fromPrefs by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(false) }
    var waterParty by remember { mutableStateOf(false) }
    // v2.0: Squad takes Calendar's slot; since v2.1 Calendar is an icon in Home's header, pushed as a page.
    val tabs = listOf(Tab("Home", Icons.Outlined.Home), Tab("Squad", com.sohum.bandlog.ui.components.PeopleIcon), Tab("Scan", com.sohum.bandlog.ui.components.ScanFilledIcon), Tab("Progress", Icons.Outlined.SignalCellularAlt), Tab("Profile", Icons.Outlined.Person))

    // v2.2: a rejected refresh token signs out only once no form or page is open, so an open
    // workout form keeps its fields and shows why the save failed instead of vanishing.
    LaunchedEffect(vm.authLost, log, page) {
        if (vm.authLost && log == null && page == null) vm.finishAuthLost()
    }
    // A tapped meal reminder lands straight on the Meal form.
    LaunchedEffect(openMealTick) {
        if (openMealTick > 0) { page = null; log = LogRequest(null, Dates.today(), true) }
    }
    // v2.4: a scan's "Log 1 serving" opens Add food with that serving on the plate.
    var seenAddFood by rememberSaveable { mutableIntStateOf(vm.addFoodTick) }
    LaunchedEffect(vm.addFoodTick) {
        if (vm.addFoodTick > seenAddFood) { seenAddFood = vm.addFoodTick; page = null; log = LogRequest(null, Dates.today(), true) }
    }
    // v2.6: a tapped water reminder opens the Water page.
    LaunchedEffect(openWaterTick) {
        if (openWaterTick > 0) { log = null; page = Page.WATER }
    }
    // v2.6: a tapped invite link joins (or requests to join) on the Squad tab.
    LaunchedEffect(joinCode, vm.loadedOnce) {
        if (joinCode != null && vm.loadedOnce) {
            page = null; log = null; tab = 1
            sq.joinByCode(joinCode, com.sohum.bandlog.util.Names.display(vm.profile.name, com.sohum.bandlog.data.Session.email, "Member"))
            onJoinHandled()
        }
    }
    // v2.6 water: keep the reminder receiver's mirror current (today's total, goal, glass, window).
    LaunchedEffect(vm.waterToday, vm.profile.waterGoalMl, vm.profile.waterGlassMl, vm.loadedOnce) {
        if (!vm.loadedOnce) return@LaunchedEffect
        com.sohum.bandlog.util.WaterPrefs.cacheTotal(ctx, Dates.today(), vm.waterToday)
        val cur = com.sohum.bandlog.util.WaterPrefs.load(ctx)
        com.sohum.bandlog.util.WaterPrefs.save(ctx, cur.copy(goalMl = vm.profile.waterGoalMl, glassMl = vm.profile.waterGlassMl))
    }
    LaunchedEffect(vm.profile.waterReminderEveryMin, vm.profile.waterReminderFrom, vm.profile.waterReminderTo) {
        val every = vm.profile.waterReminderEveryMin ?: return@LaunchedEffect
        val cur = com.sohum.bandlog.util.WaterPrefs.load(ctx)
        val next = cur.copy(from = vm.profile.waterReminderFrom ?: cur.from, to = vm.profile.waterReminderTo ?: cur.to, every = every)
        if (next != cur) { com.sohum.bandlog.util.WaterPrefs.save(ctx, next); com.sohum.bandlog.alarm.WaterAlarms.reschedule(ctx) }
    }
    // v2.6: confetti the first time today's water crosses the goal.
    var seenGoalTick by rememberSaveable { mutableIntStateOf(vm.waterGoalTick) }
    LaunchedEffect(vm.waterGoalTick) {
        if (vm.waterGoalTick > seenGoalTick) {
            seenGoalTick = vm.waterGoalTick
            val d = Dates.today()
            if (vm.celebrationsOn && !com.sohum.bandlog.util.WaterPrefs.celebrated(ctx, d)) { com.sohum.bandlog.util.WaterPrefs.markCelebrated(ctx, d); waterParty = true }
        }
    }
    // A tapped 9 pm wrap lands on Home, where the Wrap card sits on top.
    LaunchedEffect(openWrapTick) {
        if (openWrapTick > 0) { page = null; log = null; tab = 0 }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayScreen(
                        vm, onOpenWorkout = { w -> log = LogRequest(w, Dates.today(), false) }, onLogExercise = { log = LogRequest(null, Dates.today(), false, exercise = true) }, onOpenCalendar = { page = Page.CALENDAR }, onLog = { d -> log = LogRequest(null, d, false) }, wrapTick = openWrapTick, onLogWater = { page = Page.WATER },
                        onAddMeal = { d, t -> meal = com.sohum.bandlog.ui.log.MealRequest(d, mealType = t) }, onOpenMeal = { m -> meal = com.sohum.bandlog.ui.log.MealRequest(m.date, meal = m) },
                    )
                    1 -> com.sohum.bandlog.ui.squad.SquadScreen(vm, onOpenProfile = { tab = 4 })
                    2 -> com.sohum.bandlog.ui.scan.ScanTab(vm)
                    3 -> ProgressScreen(vm) { page = Page.BADGES }
                    else -> ProfileScreen(vm, updateVm, themeMode, onThemeMode) { target ->
                        page = when (target) {
                            ProfilePage.PERSONAL -> Page.PERSONAL
                            ProfilePage.GOALS -> Page.GOALS
                            ProfilePage.GOAL_WEIGHT -> Page.GOAL_WEIGHT
                            ProfilePage.REMINDERS -> Page.REMINDERS
                            ProfilePage.WEIGHT_HISTORY -> Page.WEIGHT_HISTORY
                            ProfilePage.BADGES -> Page.BADGES
                            ProfilePage.PREFERENCES -> Page.PREFERENCES
                            ProfilePage.APPEARANCE -> Page.APPEARANCE
                            ProfilePage.TRACKING -> Page.TRACKING
                            ProfilePage.PRIVACY -> Page.PRIVACY
                            ProfilePage.ACCOUNT -> Page.ACCOUNT
                            ProfilePage.USERNAME -> { sq.profileFlow = true; null }
                        }
                        fromPrefs = false
                    }
                }
            }
        }
        // v2.3 speed dial: a scrim over the page and five actions stacked above the + button.
        if (dial) BackHandler { dial = false }
        androidx.compose.animation.AnimatedVisibility(dial, enter = fadeIn(Motion.effects()), exit = fadeOut(Motion.effectsFast())) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { dial = false })
        }
        androidx.compose.animation.AnimatedVisibility(
            dial, modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(Motion.effects()) + slideInVertically(Motion.spatial()) { it / 4 },
            exit = fadeOut(Motion.effectsFast()) + slideOutVertically(Motion.spatialFast()) { it / 4 },
        ) {
            SpeedDial(Modifier.navigationBarsPadding().padding(end = 20.dp, bottom = 104.dp)) { item ->
                dial = false
                val today = Dates.today()
                when (item) {
                    DialItem.MEAL -> log = LogRequest(null, today, true)
                    DialItem.WORKOUT -> log = LogRequest(null, today, false)
                    DialItem.EXERCISE -> log = LogRequest(null, today, false, exercise = true)
                    DialItem.WATER -> page = Page.WATER
                    DialItem.WEIGHT -> page = Page.WEIGHT_LOG
                }
            }
        }

        // Bottom bar + FAB, overlaid so screens scroll under it.
        Column(Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().background(p.card)) {
                    Hair()
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 76.dp, top = 10.dp).navigationBarsPadding().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        tabs.forEachIndexed { i, t ->
                            val sel = tab == i
                            Column(Modifier.clickable { tab = i }.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(t.icon, t.label, tint = if (sel) p.ink else p.muted, modifier = Modifier.size(24.dp))
                                Text(t.label, fontSize = 11.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted)
                            }
                        }
                    }
                }
                // Tap opens the dial; long-press keeps the old straight-to-Log behaviour.
                Fab(Modifier.align(Alignment.TopEnd).offset(x = (-20).dp, y = (-30).dp), open = dial, onLongClick = { dial = false; log = LogRequest(null, Dates.today(), false) }) { dial = !dial }
            }
        }

        vm.celebrate?.let { c -> CelebrationModal(c) { vm.dismissCelebration() } }

        // Profile detail pages, pushed with the same motion as the Log page.
        AnimatedContent(
            targetState = page, label = "page",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { current ->
            if (current != null) {
                val back: () -> Unit = { page = parentOf(current, fromPrefs) }
                BackHandler { back() }
                when (current) {
                    Page.WATER -> com.sohum.bandlog.ui.today.WaterScreen(vm, back)
                    Page.PERSONAL -> PersonalDetailsScreen(vm, back) { page = Page.GOAL_WEIGHT }
                    Page.GOALS -> NutritionGoalsScreen(vm, back) { page = Page.PERSONAL }
                    Page.GOAL_WEIGHT -> GoalWeightScreen(vm, back)
                    Page.REMINDERS -> RemindersScreen(vm, back)
                    Page.WEIGHT_HISTORY -> WeightHistoryScreen(vm, back)
                    Page.WEIGHT_LOG -> WeightHistoryScreen(vm, back, openLog = true)
                    Page.BADGES -> BadgesScreen(vm, back)
                    Page.CALENDAR -> CalendarPage(
                        vm, back,
                        onAddMeal = { d, t -> meal = com.sohum.bandlog.ui.log.MealRequest(d, mealType = t) },
                        onOpenMeal = { m -> meal = com.sohum.bandlog.ui.log.MealRequest(m.date, meal = m) },
                    ) { w, d -> log = LogRequest(w, d, false) }
                    Page.PREFERENCES -> com.sohum.bandlog.ui.profile.PreferencesScreen(vm, themeMode, back) { target ->
                        fromPrefs = true
                        page = when (target) {
                            ProfilePage.APPEARANCE -> Page.APPEARANCE
                            ProfilePage.TRACKING -> Page.TRACKING
                            ProfilePage.REMINDERS -> Page.REMINDERS
                            ProfilePage.PRIVACY -> Page.PRIVACY
                            else -> Page.ACCOUNT
                        }
                    }
                    Page.APPEARANCE -> com.sohum.bandlog.ui.profile.AppearanceScreen(vm, themeMode, onThemeMode, back)
                    Page.TRACKING -> com.sohum.bandlog.ui.profile.TrackingScreen(vm, back)
                    Page.PRIVACY -> com.sohum.bandlog.ui.profile.PrivacyScreen(vm, back)
                    Page.ACCOUNT -> com.sohum.bandlog.ui.profile.AccountScreen(vm, back)
                }
            }
        }

        AnimatedContent(
            targetState = log, label = "log",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { req ->
            if (req != null) {
                BackHandler { log = null }
                LogScreen(vm, req.workout, req.date, req.meal, onClose = { log = null }, startOnExercise = req.exercise)
            }
        }

        // v2.8: the meal page (a section's "+ Add", or the meal editor), over Home or Calendar.
        AnimatedContent(
            targetState = meal, label = "meal",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { req ->
            if (req != null) {
                BackHandler { meal = null }
                com.sohum.bandlog.ui.log.MealScreen(vm, req) { meal = null }
            }
        }

        // v2.6 squads: the profile / create flows and the open squad, full screen over the tabs.
        com.sohum.bandlog.ui.squad.SquadOverlays(vm)

        if (waterParty) com.sohum.bandlog.ui.today.WaterGoalParty { waterParty = false }
    }
}

/** The Calendar, pushed from the calendar icon in Home's header, with a back pill over it. */
@Composable
private fun CalendarPage(
    vm: AppViewModel, onBack: () -> Unit,
    onAddMeal: (String, String) -> Unit = { _, _ -> }, onOpenMeal: (com.sohum.bandlog.data.Meal) -> Unit = {},
    onOpen: (Workout?, String) -> Unit,
) {
    val p = palette
    Box(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(18.dp)) }
            }
            Box(Modifier.weight(1f)) { CalendarScreen(vm, { w, d -> onOpen(w, d) }, onAddMeal = onAddMeal, onOpenMeal = onOpenMeal) }
        }
    }
}

/** Cal AI-style streak celebration after a saved session: flame, count, this week's dots. */
@Composable
private fun CelebrationModal(c: AppViewModel.Celebration, onDismiss: () -> Unit) {
    val p = palette
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(p.card, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                com.sohum.bandlog.ui.components.Flame(p.flame, 96.dp)
                Text("${c.thisWeek}", fontSize = 26.sp, fontWeight = FontWeight(800), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(top = 18.dp))
            }
            Text(
                if (c.hitTarget) "Week target hit!" else "Session ${c.thisWeek} of ${c.target}",
                fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.flame, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (c.hitTarget) "${c.streakWeeks}-week streak. You're on fire — keep it rolling." else "${c.target - c.thisWeek} more this week keeps the ${c.streakWeeks}-week streak alive.",
                fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(c.target) { i ->
                    Box(Modifier.size(14.dp).let { m -> if (i < c.thisWeek) m.background(p.flame, CircleShape) else m.border(2.dp, p.flame, CircleShape) })
                }
            }
            Spacer(Modifier.height(20.dp))
            com.sohum.bandlog.ui.components.PillButton("Continue", onDismiss)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Fab(modifier: Modifier, open: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val p = palette
    val t = rememberInfiniteTransition(label = "fab")
    val glow by t.animateFloat(10f, 16f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "fabGlow")
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 45f else 0f, Motion.spatialFast(), label = "fabTurn")
    Box(
        modifier.size(60.dp).shadow(glow.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.btn, CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, if (open) "Close" else "Log", tint = p.btnInk, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = turn }) }
}

/** The five speed-dial actions: a label pill beside a round icon, right-aligned above the + button. */
@Composable
private fun SpeedDial(modifier: Modifier, onPick: (DialItem) -> Unit) {
    val p = palette
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DialItem.entries.forEach { item ->
            val icon = when (item) {
                DialItem.MEAL -> com.sohum.bandlog.ui.components.BowlIcon
                DialItem.WORKOUT -> com.sohum.bandlog.ui.components.BandIcon
                DialItem.EXERCISE -> com.sohum.bandlog.ui.components.RunIcon
                DialItem.WATER -> com.sohum.bandlog.ui.components.GlassIcon
                DialItem.WEIGHT -> com.sohum.bandlog.ui.components.ScaleIcon
            }
            Row(
                Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onPick(item) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.height(36.dp).background(p.card, CircleShape).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(item.label, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(48.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = p.ink, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
