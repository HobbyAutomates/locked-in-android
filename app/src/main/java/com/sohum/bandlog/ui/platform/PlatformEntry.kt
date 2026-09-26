package com.sohum.bandlog.ui.platform

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.DumbbellIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.MuscleMap
import com.sohum.bandlog.util.PlatformPrefs
import com.sohum.bandlog.util.Recaps
import com.sohum.bandlog.util.Routines
import com.sohum.bandlog.util.Training

/**
 * v2.13 platform pages over the tab shell, plus the one-time POST_NOTIFICATIONS ask. MainActivity
 * calls this once (next to SquadOverlays).
 */
@Composable
fun PlatformOverlays(vm: AppViewModel) {
    val ctx = LocalContext.current
    val pvm: PlatformViewModel = viewModel()
    LaunchedEffect(vm.signedIn) { if (vm.signedIn) { pvm.loadPlan(); pvm.loadMeasurements() } }
    // Keep the facts the background protein check needs on this phone.
    LaunchedEffect(vm.profile.dob, vm.profile.goalType, pvm.extras?.dietMode) {
        PlatformPrefs.setProfileFacts(ctx, vm.profile.age, vm.profile.goalType, pvm.extras?.dietMode)
    }

    // API 33+: ask once, at a moment that explains itself (after a nudge, turning a notice on, opening the inbox, a rest timer).
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(PlatformNav.askNotificationsTick) {
        if (PlatformNav.askNotificationsTick == 0 || Build.VERSION.SDK_INT < 33) return@LaunchedEffect
        if (com.sohum.bandlog.notify.PlatformNotifications.permitted(ctx) || PlatformPrefs.askedNotifications(ctx)) return@LaunchedEffect
        PlatformPrefs.markAskedNotifications(ctx)
        runCatching { ask.launch("android.permission.POST_NOTIFICATIONS") }
    }

    AnimatedContent(
        targetState = PlatformNav.page, label = "platform",
        transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
    ) { page ->
        if (page == null) return@AnimatedContent
        val back: () -> Unit = { PlatformNav.back() }
        androidx.activity.compose.BackHandler { if (page == PlatformPage.PR_CHARTS && PlatformNav.prExercise != null) PlatformNav.prExercise = null else back() }
        when (page) {
            PlatformPage.PRO -> ProScreen(pvm, back)
            PlatformPage.INBOX -> InboxScreen(pvm, back)
            PlatformPage.NOTIFICATIONS -> NotificationsScreen(vm, pvm, back)
            PlatformPage.MEASUREMENTS -> MeasurementsScreen(pvm, back)
            PlatformPage.PHOTOS -> PhotosScreen(vm, pvm, back)
            PlatformPage.BEFORE_AFTER -> BeforeAfterScreen(vm, pvm, back)
            PlatformPage.ROUTINES -> RoutinesScreen(vm, pvm, back)
            PlatformPage.ROUTINE_EDIT -> RoutineEditorScreen(pvm, back)
            PlatformPage.WORKOUT -> LiveWorkoutScreen(vm, pvm, back)
            PlatformPage.PR_CHARTS -> PrChartsScreen(vm, pvm, back)
            PlatformPage.MUSCLE_MAP -> MuscleMapScreen(vm, pvm, back)
            PlatformPage.RECAPS -> RecapsScreen(vm, pvm, back)
            PlatformPage.RECAP -> RecapPlayer(vm, back)
        }
    }
}

/**
 * v2.13 Home "Today's session" (spec §12): the active routine's day for today (or a rest day and
 * what's next), its muscles, and Start, which opens the live workout pre-filled. A workout in
 * progress shows Resume instead. Hidden when there's no active routine.
 */
@Composable
fun TodaySessionCard(vm: AppViewModel) {
    val p = palette
    val pvm: PlatformViewModel = viewModel()
    LaunchedEffect(Unit) { if (pvm.routinesSupported == null) pvm.loadRoutines() }
    val live = pvm.live
    if (live != null) {
        Card(onClick = { PlatformNav.open(PlatformPage.WORKOUT) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Workout in progress", fontSize = 13.sp, fontWeight = FontWeight(600), color = accentColor)
                    Text(live.title, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                }
                Chip("Resume", true, { PlatformNav.open(PlatformPage.WORKOUT) })
            }
        }
        return
    }
    val r = pvm.activeRoutine ?: return
    if (!pvm.hasPro) return
    val trained = vm.workouts.filter { it.kind == "gym" || it.kind == "bodyweight" }.map { it.date }
    val t = Routines.today(r, Dates.today(), trained, r.createdAt)
    val doneToday = trained.contains(Dates.today())
    Card(onClick = { PlatformNav.open(PlatformPage.ROUTINES) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(DumbbellIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            TitleWithChip("Today's session", pro = true, size = 16)
            Spacer(Modifier.weight(1f))
            Text(r.name, fontSize = 12.sp, color = p.muted, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        val day = t.day
        if (day == null) {
            Text("Rest day", fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink)
            t.next?.let { Text("Next: ${Routines.weekdayLabel(t.nextWeekday)} · ${it.name}", fontSize = 13.sp, color = p.muted) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.name, fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(
                        "${day.exercises.size} exercises · ${Routines.setCount(day)} sets · ~${Routines.estimateMinutes(day)} min" + if (doneToday) " · done today" else "",
                        fontSize = 13.sp, color = p.muted,
                    )
                }
                val tg = MuscleMap.targetsFor(day.exercises.map { it.name })
                Row(Modifier.width(92.dp).height(80.dp)) {
                    MuscleFigure(targetFills(tg.primary, tg.secondary), true, Modifier.weight(1f))
                    MuscleFigure(targetFills(tg.primary, tg.secondary), false, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            com.sohum.bandlog.ui.components.PillButton(if (doneToday) "Train again" else "Start workout", { startLive(vm, pvm, day, "${r.name} · ${day.name}") }, height = 46.dp)
        }
    }
}

/** Whether Home should show [TodaySessionCard] (a workout in progress, or an active routine). */
@Composable
fun todaySessionVisible(): Boolean {
    val pvm: PlatformViewModel = viewModel()
    LaunchedEffect(Unit) { if (pvm.routinesSupported == null) pvm.loadRoutines() }
    return pvm.live != null || (pvm.activeRoutine != null && pvm.hasPro)
}

/** v2.13 Progress: the entry to body measurements, photos, training and recaps, plus share cards. */
@Composable
fun BodyTrainingCard(vm: AppViewModel) {
    val p = palette
    val pvm: PlatformViewModel = viewModel()
    var share by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (pvm.measurementsSupported == null) pvm.loadMeasurements() }
    Card(padding = 0.dp) {
        Text("Body & training", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        NavRow(LineIcons.Target, "Body measurements", pvm.measurements.firstOrNull()?.let { Dates.relative(it.date) } ?: "") { PlatformNav.open(PlatformPage.MEASUREMENTS) }
        Hair()
        NavRow(LineIcons.Camera, "Progress photos", if (vm.progressPhotos.isNotEmpty()) "${vm.progressPhotos.size}" else "") { PlatformNav.open(PlatformPage.PHOTOS) }
        Hair()
        NavRow(DumbbellIcon, "Routines & planner", pvm.activeRoutine?.name ?: "", pro = true) { PlatformNav.open(PlatformPage.ROUTINES) }
        Hair()
        NavRow(LineIcons.Chart, "PR charts", "", pro = true) { PlatformNav.prExercise = null; PlatformNav.open(PlatformPage.PR_CHARTS) }
        Hair()
        NavRow(LineIcons.User, "Muscle map", "", pro = true) { PlatformNav.open(PlatformPage.MUSCLE_MAP) }
        Hair()
        NavRow(LineIcons.Star, "Recaps", "", pro = true) { PlatformNav.open(PlatformPage.RECAPS) }
        Hair()
        NavRow(LineIcons.Share, "Share a card", "", pro = true) { if (pvm.hasPro) share = true else PlatformNav.open(PlatformPage.PRO) }
    }
    if (share) ShareSheet(vm) { share = false }
}

/** Pick a story card: today, the streak, your latest PR, or last week's recap. */
@Composable
fun ShareSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val hide = vm.profile.hideNumbers == true
    val today = Dates.today()
    val tot = com.sohum.bandlog.data.totalsFor(vm.meals, today)
    val lastPr = Training.exercisesWithHistory(vm.workouts).mapNotNull { (n, _) -> Training.history(vm.workouts, n).lastOrNull { it.pr }?.let { n to it } }.maxByOrNull { it.second.date }
    val options = buildList<Pair<String, () -> ShareCards.Spec>> {
        add("Today's summary" to {
            ShareCards.today(tot.protein.toInt(), vm.profile.proteinTargetG, tot.calories.toInt(), vm.budgetToday.toInt(), vm.workouts.count { it.date == today }, vm.waterToday)
        })
        add("Streak · ${vm.dayStreak} days" to { ShareCards.streak(vm.dayStreak, vm.thisWeek, vm.profile.weeklyWorkoutTarget) })
        lastPr?.let { (n, pt) -> add("PR · $n" to { ShareCards.pr(n, pt, vm.profile.name) }) }
        add("Last week's recap" to { ShareCards.recap(computeRecap(vm, Recaps.lastWeek(today))) })
    }
    BottomSheet(title = "Share a card", subtitle = if (hide) "Story-sized · calorie numbers hidden" else "Story-sized, for Instagram and more", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            options.forEachIndexed { i, (label, spec) ->
                if (i > 0) Hair()
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { onDismiss(); ShareCards.share(ctx, spec(), hide) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(LineIcons.Share, null, tint = p.ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                }
            }
        }
    }
}
