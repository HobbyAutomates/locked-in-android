package com.sohum.bandlog.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Chevron
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BowlIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.DumbbellIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Flame
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    vm: AppViewModel, onOpenWorkout: (Workout?) -> Unit, onLogExercise: () -> Unit = {}, onOpenCalendar: () -> Unit = {}, onLog: (String) -> Unit = {}, wrapTick: Int = 0, onLogWater: () -> Unit = {},
    /** v2.8: a meal section's "+ Add" (date, meal type) and a tapped meal row (the editor). */
    onAddMeal: (String, String) -> Unit = { _, _ -> }, onOpenMeal: (Meal) -> Unit = {},
) {
    val p = palette
    val today = vm.today
    var selected by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(today) }
    val isToday = selected == today
    val totals = totalsFor(vm.meals, selected)
    val prof = vm.profile
    val todayMeals = vm.meals.filter { it.date == selected }
    val todayWorkouts = vm.workouts.filter { it.date == selected }
    // Band-workout burns ride on the workout row itself; everything else gets a row of its own.
    val todayExercises = vm.exercises.filter { it.date == selected && it.source != "workout" }
    val workoutBurn = vm.exercises.filter { it.date == selected && it.source == "workout" }.associate { it.note to it.kcal }
    val trained = vm.workoutDates.toSet()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // v2.0: the 9 pm wrap (21:00–04:00, dismissible per day; a tapped notification brings it back) and the nudge banner.
    val wrapDate = com.sohum.bandlog.util.Wrap.wrapDate()
    var wrapHidden by androidx.compose.runtime.remember(wrapTick, wrapDate) { androidx.compose.runtime.mutableStateOf(com.sohum.bandlog.util.Wrap.dismissed(ctx, wrapDate)) }
    val showWrap = com.sohum.bandlog.util.Wrap.inWindow() && !wrapHidden && vm.loadedOnce
    val latestNudge = vm.nudges.firstOrNull()
    var nudgeHidden by androidx.compose.runtime.remember(latestNudge?.id) { androidx.compose.runtime.mutableStateOf(latestNudge?.let { com.sohum.bandlog.util.Wrap.nudgeDismissed(ctx, it.id) } ?: true) }
    // v2.8: one card, never a carousel — the most relevant of a meal still being saved, a squad
    // nudge, then the 9 pm wrap (a tapped wrap notification puts the wrap first). Dismissing one lets the next show.
    val banner = when {
        wrapTick > 0 && showWrap -> "wrap"
        vm.pendingMeals.isNotEmpty() -> "pending"
        latestNudge != null && !nudgeHidden -> "nudge"
        showWrap -> "wrap"
        else -> null
    }
    // Steps and burned calories go stale while the app is backgrounded; re-read on every resume.
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // v2.8: Home re-reads on resume (at most once a minute) instead of a refresh button in the header.
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refreshHealth(ctx); vm.refreshIfStale() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // v2.3: today's budget honours "Add burned calories" and "Rollover calories" (both from Preferences).
    val budget = if (isToday) vm.budgetToday else prof.calorieTarget.toDouble()
    val caloriesLeft = (budget - totals.calories).toInt().coerceAtLeast(0)
    if (isToday && vm.loadedOnce) androidx.compose.runtime.LaunchedEffect(caloriesLeft) { com.sohum.bandlog.widget.CaloriesWidget.publish(ctx, caloriesLeft) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Rise(0) {
                RowSpaceBetween {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(com.sohum.bandlog.ui.components.LockIcon, null, tint = p.ink, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Locked In", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                    }
                    // v2.8 declutter: the header keeps only Calendar. Refresh happens on resume and after
                    // every save; the week streak lives on Calendar and Progress.
                    HeaderButton(onClick = onOpenCalendar) { Icon(Icons.Outlined.CalendarMonth, "Calendar", tint = p.ink, modifier = Modifier.size(18.dp)) }
                }
                ErrorNote(vm.error, Modifier.padding(top = 8.dp))
            }
        }
        if (banner != null) item(key = "banner") {
            Rise(1) {
                when (banner) {
                    "nudge" -> NudgeBanner(vm.nudges) { latestNudge?.let { com.sohum.bandlog.util.Wrap.dismissNudge(ctx, it.id) }; nudgeHidden = true }
                    "wrap" -> WrapCard(vm.wrap()) { com.sohum.bandlog.util.Wrap.dismiss(ctx, wrapDate); wrapHidden = true }
                    else -> PendingBanner(vm.pendingMeals)
                }
            }
        }
        item(key = "streak") { Rise(1) { DayStreakRow(vm.dayStreak, vm.notice) { vm.dismissNotice() } } }
        item { Rise(1) { WeekStrip(today, selected, trained) { selected = it } } }
        item {
            Rise(2) {
                Card(padding = 20.dp) {
                    RowSpaceBetween {
                        Column {
                            Text("$caloriesLeft", fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 40.sp)
                            Text(if (isToday) "Calories left" else "Calories left · ${Dates.short(selected)}", fontSize = 14.sp, fontWeight = FontWeight(500), color = p.muted)
                            if (isToday && (vm.burnedKcal > 0 || vm.rolloverKcal > 0)) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (vm.burnedKcal > 0) Box(Modifier.background(p.card2, CircleShape).padding(8.dp, 3.dp)) { Text("+${vm.burnedKcal.toInt()} burned", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1) }
                                if (vm.rolloverKcal > 0) Box(Modifier.background(p.card2, CircleShape).padding(8.dp, 3.dp)) { Text("+${vm.rolloverKcal.toInt()} rollover", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1) }
                            }
                        }
                        Ring((totals.calories / budget.coerceAtLeast(1.0)).toFloat(), p.ink, 96.dp, 9.dp) { Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(26.dp)) }
                    }
                }
            }
        }
        item {
            Rise(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroCard(Modifier.weight(1f), "Protein", totals.protein, prof.proteinTargetG.toDouble(), p.red)
                    MacroCard(Modifier.weight(1f), "Carbs", totals.carbs, prof.carbTargetG.toDouble(), p.orange)
                    MacroCard(Modifier.weight(1f), "Fat", totals.fat, prof.fatTargetG.toDouble(), p.blue)
                }
            }
        }
        if (isToday) {
            val h = vm.healthToday
            val burned = vm.burnedToday
            item {
                Rise(4) { Column {
                    // v2.1: steps and calories burned are one card, split down the middle.
                    Card(padding = 0.dp) {
                        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            if (vm.healthConnected) {
                                Row(Modifier.weight(1f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Ring(((h?.steps ?: 0L) / prof.stepGoal.toFloat()).coerceIn(0f, 1f), p.green, 44.dp, 5.dp) { Icon(com.sohum.bandlog.ui.components.StepsIcon, null, tint = p.green, modifier = Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(String.format(Locale.US, "%,d", h?.steps ?: 0L), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, maxLines = 1)
                                        Text("of ${String.format(Locale.US, "%,d", prof.stepGoal.toLong())} steps", fontSize = 12.sp, color = p.muted, maxLines = 1)
                                    }
                                }
                                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp).background(p.hair))
                            }
                            // Health Connect active kcal (when connected) + logged exercise, deduplicated in the view model.
                            Row(Modifier.weight(1f).clickable(onClick = onLogExercise).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Ring((burned / 400.0).toFloat().coerceIn(0f, 1f), p.orange, 44.dp, 5.dp) { Icon(FlameIcon, null, tint = p.orange, modifier = Modifier.size(16.dp)) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${burned.toInt()}", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                                    Text(if (vm.healthConnected) "kcal burned" else "kcal burned · log exercise", fontSize = 12.sp, color = p.muted, maxLines = 1)
                                }
                                Chevron()
                            }
                        }
                    }
                    val err = vm.healthError
                    if (vm.healthConnected && err != null) Text(err, fontSize = 12.sp, color = p.red, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    else if (vm.healthConnected && (h?.steps ?: 0L) == 0L) Text("No steps yet — turn on syncing to Health Connect in Samsung Health or Google Fit, then refresh.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    WaterCard(vm, onLogWater)
                } }
            }
        }
        item {
            Rise(4) {
                RowSpaceBetween {
                    Text(if (isToday) "Today" else Dates.long(selected), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                }
            }
        }
        // v2.8: no "Log something" button here — the + button logs; each meal section has its own "+ Add".
        items(todayWorkouts, key = { "w" + it.id }) { w -> Rise(5) { WorkoutRow(w, burnKcal = workoutBurn[w.id]) { onOpenWorkout(w) } } }
        items(todayExercises, key = { "e" + it.id }) { e -> Rise(5) { ExerciseRow(e) { vm.launch { vm.deleteExercise(e.id) } } } }
        // v2.8: Breakfast · Lunch · Dinner · Snacks, each with its totals and "+ Add"; tap a meal to edit it.
        items(com.sohum.bandlog.util.MealTypes.group(todayMeals), key = { "s" + it.type.key }) { s -> Rise(6) { MealSection(s, onAdd = { t -> onAddMeal(selected, t) }, onOpen = onOpenMeal) } }
    }
}

/**
 * v2.2: the day streak (any log — workout, exercise or meal — on consecutive India days) as a
 * small flame pill, with a short save confirmation beside it that fades after a few seconds.
 */
@Composable
private fun DayStreakRow(days: Int, notice: String?, onDismissNotice: () -> Unit) {
    val p = palette
    if (notice != null) androidx.compose.runtime.LaunchedEffect(notice) { kotlinx.coroutines.delay(if (notice.length > 20) 7000 else 3500); onDismissNotice() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.height(34.dp).background(if (days > 0) p.flame.copy(alpha = 0.14f) else p.card2, CircleShape).padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FlameIcon, null, tint = if (days > 0) p.flame else p.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (days > 0) "$days-day streak" else "Start a streak today",
                fontSize = 13.sp, fontWeight = FontWeight(700), color = if (days > 0) p.ink else p.muted, maxLines = 1,
            )
        }
        if (notice != null) Row(
            Modifier.weight(1f, fill = false).height(34.dp).background(p.btn, CircleShape).clickable(onClick = onDismissNotice).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(com.sohum.bandlog.ui.components.CheckIcon, null, tint = p.btnInk, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(notice, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.btnInk, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** A 40 dp round header button on the card colour. */
@Composable
private fun HeaderButton(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = palette
    Box(
        Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "Sohum nudged you" — squad-mates poking you to train, dismissible. Text with an inline icon, no emoji. */
@Composable
private fun NudgeBanner(nudges: List<com.sohum.bandlog.data.Nudge>, onDismiss: () -> Unit) {
    val p = palette
    val names = nudges.map { it.fromName }.distinct()
    val who = when (names.size) { 1 -> names[0]; 2 -> "${names[0]} and ${names[1]}"; else -> "${names[0]} and ${names.size - 1} others" }
    Row(
        Modifier.fillMaxWidth().background(p.btn, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(p.btnInk.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(com.sohum.bandlog.ui.components.FistIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$who nudged you", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk)
            Text("${nudges.first().groupName} · get a session in today", fontSize = 12.sp, color = p.btnInk.copy(alpha = 0.72f), maxLines = 1)
        }
        IconButton(onClick = onDismiss, Modifier.size(36.dp)) { Icon(com.sohum.bandlog.ui.components.CrossIcon, "Dismiss", tint = p.btnInk, modifier = Modifier.size(16.dp)) }
    }
}

/** The 9 pm daily wrap card: protein, calories vs budget, sessions, tomorrow's session, best meal, share. */
@Composable
private fun WrapCard(w: com.sohum.bandlog.util.Wrap.Result, onDismiss: () -> Unit) {
    val p = palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Card(padding = 18.dp) {
        RowSpaceBetween {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.sohum.bandlog.ui.components.MoonStarIcon, null, tint = p.muted, modifier = Modifier.size(16.dp))
                Text(if (w.isYesterday) "  Yesterday's wrap" else "  Today's wrap", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted)
            }
            Box(Modifier.size(30.dp).background(p.card2, CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(com.sohum.bandlog.ui.components.CrossIcon, "Dismiss the wrap", tint = p.muted, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WrapStat(Modifier.weight(1f), "${w.protein} g", if (w.proteinHit) "protein, hit" else "protein · ${(w.proteinTarget - w.protein).coerceAtLeast(0)} short", if (w.proteinHit) p.green else p.red, check = w.proteinHit)
            WrapStat(Modifier.weight(1f), String.format(Locale.US, "%,d", w.calories), "of ${String.format(Locale.US, "%,d", w.calorieBudget)} kcal", p.ink)
            WrapStat(Modifier.weight(1f), "${w.sessions}/${w.sessionTarget}", "sessions this week", p.ink)
        }
        Spacer(Modifier.height(12.dp))
        val frac = (w.protein.toFloat() / w.proteinTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(6.dp).background(p.track, CircleShape)) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).background(if (w.proteinHit) p.green else p.red, CircleShape))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text("Tomorrow: ", fontSize = 14.sp, color = p.muted)
            Text(w.tomorrow, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
        }
        w.bestMeal?.let { Text("Best meal: $it · ${w.bestMealProtein} g protein", fontSize = 13.sp, color = p.muted, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(42.dp).background(p.card2, CircleShape).clickable {
                runCatching {
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, w.shareText) }, "Share my day"))
                }
            },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(com.sohum.bandlog.ui.components.ShareIcon, null, tint = p.ink, modifier = Modifier.size(16.dp))
            Text("  Share my day", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
        }
    }
}

@Composable
private fun WrapStat(modifier: Modifier, value: String, label: String, color: Color, check: Boolean = false) {
    val p = palette
    Column(modifier.background(p.card2, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = color, maxLines = 1)
            if (check) Icon(com.sohum.bandlog.ui.components.CheckIcon, null, tint = color, modifier = Modifier.padding(start = 3.dp).size(13.dp))
        }
        Text(label, fontSize = 11.sp, color = p.muted, lineHeight = 13.sp, maxLines = 2)
    }
}

@Composable
private fun WeekStrip(today: String, selected: String, trained: Set<String>, onSelect: (String) -> Unit) {
    val p = palette
    val start = Dates.addDays(today, -6)
    val fmt = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = Dates.addDays(start, i.toLong())
            val isToday = d == today
            val isSel = d == selected
            val did = d in trained
            Column(
                Modifier.weight(1f).clickable { onSelect(d) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val circle = when {
                    isSel -> Modifier.background(p.btn, CircleShape)
                    did -> Modifier.background(p.greenBg, CircleShape).border(1.5.dp, p.green, CircleShape)
                    isToday -> Modifier.border(2.dp, p.ink, CircleShape)
                    else -> Modifier.border(1.5.dp, p.hair, CircleShape)
                }
                Box(circle.size(30.dp), contentAlignment = Alignment.Center) {
                    Text(Dates.parse(d).format(fmt), fontSize = 12.sp, fontWeight = if (isSel || isToday) FontWeight(700) else FontWeight(600), color = when { isSel -> p.btnInk; did -> p.green; isToday -> p.ink; else -> p.muted })
                }
                Text("${Dates.parse(d).dayOfMonth}", fontSize = 13.sp, fontWeight = if (isSel || isToday) FontWeight(700) else FontWeight(500), color = if (isSel || isToday) p.ink else p.muted)
            }
        }
    }
}

/**
 * One macro card. Below target it counts down ("34g / Protein left"); once the target is passed
 * it flips to the overshoot ("12g / Protein **over**") in the macro's own colour, like Cal AI.
 */
@Composable
private fun MacroCard(modifier: Modifier, macro: String, consumed: Double, target: Double, color: Color) {
    val p = palette
    val safeTarget = target.coerceAtLeast(1.0)
    val over = consumed > target && target > 0
    val amount = if (over) consumed - target else target - consumed
    Card(modifier, padding = 12.dp) {
        Text(
            "${amount.toInt().coerceAtLeast(0)}g",
            fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp,
            color = if (over) color else p.ink,
        )
        Row {
            Text("$macro ", fontSize = 12.sp, color = p.muted)
            Text(
                if (over) "over" else "left",
                fontSize = 12.sp,
                fontWeight = if (over) FontWeight(700) else FontWeight(400),
                color = if (over) color else p.muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        Ring((consumed / safeTarget).toFloat(), color, 56.dp, 6.dp, Modifier.align(Alignment.CenterHorizontally)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
    }
}

/** The banner for meals saved while their parse / photo was still running. */
@Composable
private fun PendingBanner(texts: List<String>) {
    val p = palette
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(p.card2.copy(alpha = a), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(texts.first() + if (texts.size > 1) " + ${texts.size - 1} more" else "", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                Text("Working out the calories — it saves on its own.", fontSize = 12.sp, color = p.muted, maxLines = 1)
            }
        }
    }
}

/** One line per entry: tile, what it was, the number, a chevron. Tapping opens the details. */
@Composable
private fun CompactRow(
    tile: @Composable () -> Unit,
    title: String,
    value: String,
    open: Boolean,
    onClick: () -> Unit,
    details: (@Composable () -> Unit)? = null,
) {
    val p = palette
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, com.sohum.bandlog.ui.components.Motion.spatialFast(), label = "chev")
    Card(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            tile()
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("›", fontSize = 20.sp, fontWeight = FontWeight(500), color = p.muted, modifier = Modifier.graphicsLayer { rotationZ = rot })
        }
        androidx.compose.animation.AnimatedVisibility(open && details != null) {
            Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 12.dp, bottom = 10.dp)) { details?.invoke() }
        }
    }
}

@Composable
private fun SmallTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bg: Color) {
    Box(Modifier.size(40.dp).background(bg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

/**
 * A workout: its kind's icon and summary ("Gym · 5 exercises · 42 min", "Bands · Chest · Back"),
 * burn / minutes on the right; tapping opens the editor.
 */
@Composable
fun WorkoutRow(w: Workout, burnKcal: Double? = null, onClick: () -> Unit) {
    val p = palette
    CompactRow(
        tile = { SmallTile(com.sohum.bandlog.ui.components.workoutKindIcon(w.kind, w.exercises), p.ink, p.card2) },
        title = w.summary.ifBlank { "Workout" },
        value = when {
            burnKcal != null && burnKcal > 0 -> "${burnKcal.toInt()} kcal"
            w.minutes != null -> "${w.minutes} min"
            else -> w.bandLevel
        },
        open = false, onClick = onClick,
    )
}

/** A logged burn (run / activity / described / manual). Details: intensity, minutes, delete. */
@Composable
fun ExerciseRow(e: com.sohum.bandlog.data.ExerciseEntry, onDelete: () -> Unit) {
    val p = palette
    var open by androidx.compose.runtime.remember(e.id) { androidx.compose.runtime.mutableStateOf(false) }
    var pendingDelete by androidx.compose.runtime.remember(e.id) { androidx.compose.runtime.mutableStateOf(false) }
    if (pendingDelete) { com.sohum.bandlog.ui.components.UndoRow(onUndo = { pendingDelete = false }, onExpire = onDelete); return }
    val isBands = e.activityCode?.startsWith("LI-BAND") == true || e.name.contains("lifting", ignoreCase = true) || e.name.contains("band", ignoreCase = true)
    CompactRow(
        tile = { SmallTile(if (isBands) DumbbellIcon else com.sohum.bandlog.ui.components.activityIcon(e.name, e.activityCode), p.green, p.greenBg) },
        title = e.name.replaceFirstChar { it.uppercase() },
        value = "${e.kcal.toInt()} kcal",
        open = open, onClick = { open = !open },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (e.source == "manual" && e.activityCode == null && e.intensityPct == null) "Entered by hand"
                else "Intensity: ${e.intensityPct?.let { com.sohum.bandlog.util.Burn.pctShort(it) } ?: com.sohum.bandlog.util.Burn.intensityLabel(e.intensity)}") +
                    " · ${e.minutes} min" + (e.distanceKm?.let { " · ${fmt(it)} km" } ?: "") + (e.steps?.let { " · $it steps" } ?: "") +
                    " · ${timeOf(e.startedAt ?: e.createdAt)}" + (if (e.note.isNotBlank() && e.source != "workout") "\n${e.note}" else ""),
                fontSize = 12.sp, color = p.muted, modifier = Modifier.weight(1f),
            )
            DeleteButton { pendingDelete = true }
        }
    }
}

/** A meal: what was in it and the calories; details list every item, macros, thumbs up / down and delete. */
@Composable
fun MealRow(m: Meal, onDelete: () -> Unit, onFeedback: ((String) -> Unit)? = null) {
    val p = palette
    var open by androidx.compose.runtime.remember(m.id) { androidx.compose.runtime.mutableStateOf(false) }
    var voted by androidx.compose.runtime.remember(m.id) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var pendingDelete by androidx.compose.runtime.remember(m.id) { androidx.compose.runtime.mutableStateOf(false) }
    if (pendingDelete) { com.sohum.bandlog.ui.components.UndoRow(onUndo = { pendingDelete = false }, onExpire = onDelete); return }
    CompactRow(
        tile = {
            // Plate photo when the meal has one; else a picture of its biggest item (v2.4).
            if (m.photoPath != null) com.sohum.bandlog.ui.components.RemoteImage(storagePath = m.photoPath, size = 40.dp, radius = 12.dp, fallback = BowlIcon, fallbackTint = p.orange, fallbackBg = p.orangeBg)
            else {
                val big = m.items.maxByOrNull { it.calories }
                if (big == null) SmallTile(BowlIcon, p.orange, p.orangeBg)
                else com.sohum.bandlog.ui.components.FoodImage(
                    big.name, big.imageUrl, kind = com.sohum.bandlog.ui.components.FoodImages.kindFor(big.source), size = 40.dp,
                    foodId = big.foodId, fallbackBg = p.orangeBg,
                )
            }
        },
        title = m.items.joinToString(", ") { it.name }.ifBlank { "Meal" },
        value = "${m.calories.toInt()} kcal",
        open = open, onClick = { open = !open },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            m.items.forEach { it ->
                RowSpaceBetween {
                    Text(it.name, fontSize = 13.sp, color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${it.quantityLabel} · ${it.calories.toInt()} kcal", fontSize = 12.sp, color = p.muted)
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MacroDot("${fmt(m.protein)}g", p.red)
                MacroDot("${fmt(m.items.sumOf { it.carbsG })}g", p.orange)
                MacroDot("${fmt(m.items.sumOf { it.fatG })}g", p.blue)
                Spacer(Modifier.weight(1f))
                Text(timeOf(m.createdAt), fontSize = 12.sp, color = p.muted)
            }
            RowSpaceBetween {
                if (onFeedback != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (voted == null) "How did the AI do?" else "Thanks — noted", fontSize = 12.sp, color = p.muted)
                    listOf("up" to com.sohum.bandlog.ui.components.ThumbUpIcon, "down" to com.sohum.bandlog.ui.components.ThumbDownIcon).forEach { (r, icon) ->
                        val sel = voted == r
                        Box(Modifier.size(44.dp).clickable(enabled = voted == null) { voted = r; onFeedback(r) }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(30.dp).background(if (sel) p.btn else p.card2, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, r, tint = if (sel) p.btnInk else p.muted, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                } else Spacer(Modifier.width(1.dp))
                DeleteButton { pendingDelete = true }
            }
        }
    }
}

@Composable
private fun DeleteButton(onDelete: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Delete, "Delete", tint = palette.muted, modifier = Modifier.size(20.dp))
    }
}

private fun timeOf(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" })
        .atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}.getOrDefault("")

fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
