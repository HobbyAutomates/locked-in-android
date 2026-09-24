package com.sohum.bandlog.ui.today

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.sohum.bandlog.ui.components.IconTile
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(vm: AppViewModel, onOpenWorkout: (Workout?) -> Unit, onLogExercise: () -> Unit = {}, onOpenCalendar: () -> Unit = {}, wrapTick: Int = 0) {
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
    // Steps and burned calories go stale while the app is backgrounded; re-read on every resume.
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshHealth(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Rise(0) {
                RowSpaceBetween {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(com.sohum.bandlog.ui.components.LockIcon, null, tint = p.ink, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Locked In", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Compose 1.6's PullToRefresh is still experimental, so Home gets an
                        // explicit refresh button instead — same job, no API risk.
                        Box(
                            Modifier.size(34.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                                .background(p.card, CircleShape)
                                .clickable(enabled = !vm.loading) { vm.refresh(); vm.refreshHealth(ctx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (vm.loading) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = p.muted)
                            else Icon(Icons.Outlined.Refresh, "Refresh", tint = p.ink, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier.shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Flame(p.flame, 16.dp)
                            Spacer(Modifier.width(5.dp))
                            Text("${vm.weekStreak}", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        }
                    }
                }
                ErrorNote(vm.error, Modifier.padding(top = 8.dp))
            }
        }
        if (latestNudge != null && !nudgeHidden) item(key = "nudge") {
            Rise(1) { NudgeBanner(vm.nudges) { com.sohum.bandlog.util.Wrap.dismissNudge(ctx, latestNudge.id); nudgeHidden = true } }
        }
        if (showWrap) item(key = "wrap") {
            Rise(1) { WrapCard(vm.wrap()) { com.sohum.bandlog.util.Wrap.dismiss(ctx, wrapDate); wrapHidden = true } }
        }
        item { Rise(1) { WeekStrip(today, selected, trained) { selected = it } } }
        item {
            Rise(2) {
                Card(padding = 20.dp) {
                    RowSpaceBetween {
                        Column {
                            Text("${(prof.calorieTarget + (if (isToday) vm.burnedKcal else 0.0) - totals.calories).toInt().coerceAtLeast(0)}", fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 40.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isToday) "Calories left" else "Calories left · ${Dates.short(selected)}", fontSize = 14.sp, fontWeight = FontWeight(500), color = p.muted)
                                if (isToday && vm.burnedKcal > 0) Box(Modifier.padding(start = 8.dp).background(p.card2, CircleShape).padding(8.dp, 3.dp)) { Text("+${vm.burnedKcal.toInt()}", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.ink) }
                            }
                        }
                        Ring((totals.calories / prof.calorieTarget).toFloat(), p.ink, 96.dp, 9.dp) { Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(26.dp)) }
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
                Rise(4) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (vm.healthConnected) Card(Modifier.weight(1f), padding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Ring(((h?.steps ?: 0L) / prof.stepGoal.toFloat()).coerceIn(0f, 1f), p.green, 44.dp, 5.dp) { Icon(com.sohum.bandlog.ui.components.StepsIcon, null, tint = p.green, modifier = Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(String.format(Locale.US, "%,d", h?.steps ?: 0L), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                                        Text("of ${String.format(Locale.US, "%,d", prof.stepGoal.toLong())} steps", fontSize = 12.sp, color = p.muted)
                                    }
                                }
                            }
                            // Health Connect active kcal (when connected) + logged exercise, deduplicated in the view model.
                            Card(Modifier.weight(1f), padding = 14.dp, onClick = onLogExercise) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Ring((burned / 400.0).toFloat().coerceIn(0f, 1f), p.orange, 44.dp, 5.dp) { Icon(FlameIcon, null, tint = p.orange, modifier = Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${burned.toInt()}", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                                        Text(if (vm.healthConnected) "kcal burned" else "kcal burned · log exercise", fontSize = 12.sp, color = p.muted, maxLines = 1)
                                    }
                                    if (!vm.healthConnected) Text("›", fontSize = 18.sp, color = p.muted)
                                }
                            }
                        }
                        val err = vm.healthError
                        if (vm.healthConnected) {
                            if (err != null) Text(err, fontSize = 12.sp, color = p.red, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                            else if ((h?.steps ?: 0L) == 0L) Text("No steps in Health Connect yet. In Samsung Health / Google Fit, turn on syncing to Health Connect (Settings → Health Connect), then pull to refresh here.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Rise(4) {
                // Calendar left the tab bar for Squad in v2.0; it lives one tap away here.
                Card(padding = 14.dp, onClick = onOpenCalendar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(p.card2, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = p.ink, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Calendar", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text("Every session and meal, month by month", fontSize = 12.sp, color = p.muted)
                        }
                        Text("→", fontSize = 18.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
            }
        }
        item {
            Rise(4) {
                RowSpaceBetween {
                    Text(if (isToday) "Recently logged" else Dates.long(selected), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                }
            }
        }
        if (isToday) items(vm.pendingMeals, key = { "p$it" }) { txt -> Rise(5) { PendingMealRow(txt) } }
        if (todayWorkouts.isEmpty() && todayMeals.isEmpty() && todayExercises.isEmpty() && (!isToday || vm.pendingMeals.isEmpty())) item {
            Rise(5) { Text(if (isToday) "Nothing yet today. Tap + to log a workout, a meal or some exercise." else "Nothing logged on ${Dates.long(selected)}.", color = p.muted, fontSize = 13.sp) }
        }
        items(todayWorkouts, key = { "w" + it.id }) { w -> Rise(5) { WorkoutRow(w, burnKcal = workoutBurn[w.id]) { onOpenWorkout(w) } } }
        items(todayExercises, key = { "e" + it.id }) { e -> Rise(5) { ExerciseRow(e) { vm.launch { vm.deleteExercise(e.id) } } } }
        items(todayMeals, key = { "m" + it.id }) { m -> Rise(6) { MealRow(m, onDelete = { vm.launch { vm.deleteMeal(m.id) } }, onFeedback = { r -> vm.launch { runCatching { com.sohum.bandlog.data.Api.feedback(r, m.rawText, m.id) } } }) } }
    }
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

/** Shimmering placeholder while Haiku prices a quick-logged meal in the background. */
@Composable
private fun PendingMealRow(text: String) {
    val p = palette
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(p.card2.copy(alpha = a), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.muted) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(p.card2.copy(alpha = a), CircleShape))
                Text("Working out the calories… you can leave the app.", fontSize = 12.sp, color = p.muted)
            }
        }
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

@Composable
fun WorkoutRow(w: Workout, burnKcal: Double? = null, onClick: () -> Unit) {
    val p = palette
    Card(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(DumbbellIcon, p.ink, p.card2)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RowSpaceBetween {
                    Text(Dates.relative(w.date), fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                    Text(w.bandLevel + (w.resistanceKg?.let { " · ${fmt(it)} kg" } ?: ""), fontSize = 12.sp, color = p.muted)
                }
                Text(w.muscles.joinToString(" · "), fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (burnKcal != null && burnKcal > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(13.dp))
                        Text(" ${burnKcal.toInt()} kcal", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
                    }
                    w.minutes?.let { Text("$it mins", fontSize = 12.sp, color = p.muted) }
                    if (w.exercises.isNotBlank()) Text(w.exercises, fontSize = 12.sp, color = p.muted, maxLines = 1)
                }
            }
        }
    }
}

/** A logged burn (run / activity / described / manual): flame + calories, then intensity and minutes. */
@Composable
fun ExerciseRow(e: com.sohum.bandlog.data.ExerciseEntry, onDelete: () -> Unit) {
    val p = palette
    val isRun = e.activityCode == com.sohum.bandlog.util.Burn.RUN_CODE || e.name.contains("run", ignoreCase = true) || e.name.contains("jog", ignoreCase = true)
    val isBands = e.activityCode?.startsWith("LI-BAND") == true || e.name.contains("lifting", ignoreCase = true) || e.name.contains("band", ignoreCase = true)
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (isBands) DumbbellIcon else com.sohum.bandlog.ui.components.RunIcon, if (isRun || isBands) p.ink else p.green, if (isRun || isBands) p.card2 else p.greenBg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RowSpaceBetween {
                    Text(e.name.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(timeOf(e.createdAt), fontSize = 12.sp, color = p.muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(15.dp))
                    Text(" ${e.kcal.toInt()} calories", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                }
                Text(
                    (if (e.source == "manual" && e.activityCode == null) "Manual" else "Intensity: ${com.sohum.bandlog.util.Burn.intensityLabel(e.intensity)}") + " · ${e.minutes} mins",
                    fontSize = 12.sp, color = p.muted,
                )
            }
            IconButton(onClick = onDelete, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = p.muted) }
        }
    }
}

@Composable
fun MealRow(m: Meal, onDelete: () -> Unit, onFeedback: ((String) -> Unit)? = null) {
    val p = palette
    var voted by androidx.compose.runtime.remember(m.id) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.photoPath != null) com.sohum.bandlog.ui.components.RemoteImage(storagePath = m.photoPath, size = 56.dp, fallback = BowlIcon, fallbackTint = p.orange, fallbackBg = p.orangeBg)
            else IconTile(BowlIcon, p.orange, p.orangeBg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RowSpaceBetween {
                    Text(m.items.joinToString(", ") { it.name }.ifBlank { "Meal" }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(timeOf(m.createdAt), fontSize = 12.sp, color = p.muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(15.dp))
                    Text(" ${m.calories.toInt()} calories", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroDot("${fmt(m.protein)}g", p.red)
                    MacroDot("${fmt(m.items.sumOf { it.carbsG })}g", p.orange)
                    MacroDot("${fmt(m.items.sumOf { it.fatG })}g", p.blue)
                }
            }
            IconButton(onClick = onDelete, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = p.muted) }
        }
        if (onFeedback != null) {
            Spacer(Modifier.height(8.dp))
            RowSpaceBetween {
                Text(if (voted == null) "How did the AI do?" else "Thanks — noted", fontSize = 12.sp, color = p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("up" to com.sohum.bandlog.ui.components.ThumbUpIcon, "down" to com.sohum.bandlog.ui.components.ThumbDownIcon).forEach { (r, icon) ->
                        val sel = voted == r
                        Box(Modifier.size(30.dp).background(if (sel) p.btn else p.card2, CircleShape).then(Modifier.clickable(enabled = voted == null) { voted = r; onFeedback(r) }), contentAlignment = Alignment.Center) {
                            Icon(icon, r, tint = if (sel) p.btnInk else p.muted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun timeOf(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" })
        .atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}.getOrDefault("")

fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
