package com.sohum.bandlog.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Chevron
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.widthIn
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
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
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
    /** v2.8: a tapped exercise row opens its editor. */
    onOpenExercise: (com.sohum.bandlog.data.ExerciseEntry) -> Unit = {},
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
    // v2.10: the cards above the hero are a swipeable pager — a meal still being saved, a squad
    // nudge, the 9 pm wrap (a tapped wrap notification puts the wrap first). Dismissing one takes it
    // out; no cards, no pager; one card, no dots. Mirrors the web's BannerCarousel.
    val banners = buildList {
        if (wrapTick > 0 && showWrap) add("wrap")
        if (vm.pendingMeals.isNotEmpty()) add("pending")
        if (latestNudge != null && !nudgeHidden) add("nudge")
        if (wrapTick == 0 && showWrap) add("wrap")
    }
    // v2.10: the calorie + macro cards show "left" or "eaten"; tapping any one flips them all.
    // Remembered per device; a "Tap a card…" hint shows until the first tap (the web keeps both in localStorage).
    val homePrefs = androidx.compose.runtime.remember { ctx.getSharedPreferences(HOME_PREFS, android.content.Context.MODE_PRIVATE) }
    var eatenMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(homePrefs.getString(MACRO_MODE, "left") == "eaten") }
    var hintSeen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(homePrefs.getBoolean(MACRO_HINT_SEEN, false)) }
    var flipped by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val flip: () -> Unit = {
        flipped = true
        eatenMode = !eatenMode
        hintSeen = true
        homePrefs.edit().putString(MACRO_MODE, if (eatenMode) "eaten" else "left").putBoolean(MACRO_HINT_SEEN, true).apply()
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
    if (isToday && vm.loadedOnce) androidx.compose.runtime.LaunchedEffect(caloriesLeft) { com.sohum.bandlog.widget.CaloriesWidget.publish(ctx, caloriesLeft, if (prof.hideNumbers == true) com.sohum.bandlog.util.Goals.calorieWords(totals.calories, budget.toDouble()) else null) }

    val showRecap = com.sohum.bandlog.ui.platform.recapDue(vm) // v2.13 platform
    val showSession = com.sohum.bandlog.ui.platform.todaySessionVisible() // v2.13 platform
    // v2.12: cards rise out of a soft blur one after another, once per visit (ui/motion).
    MotionScreen {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Entrance(0, key = "header") {
                RowSpaceBetween {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(com.sohum.bandlog.ui.components.LockIcon, null, tint = p.ink, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Locked In", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                    }
                    // v2.8 declutter: the header keeps only Calendar. Refresh happens on resume and after
                    // every save; the week streak lives on Calendar and Progress.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.sohum.bandlog.ui.platform.InboxBell() // v2.13 platform
                        HeaderButton(onClick = onOpenCalendar) { Icon(Icons.Outlined.CalendarMonth, "Calendar", tint = p.ink, modifier = Modifier.size(18.dp)) }
                    }
                }
                ErrorNote(vm.error, Modifier.padding(top = 8.dp))
            }
        }
        if (banners.isNotEmpty()) item(key = "banner") {
            Entrance(1, key = "card1") {
                BannerPager(banners) { banner ->
                    when (banner) {
                        "nudge" -> NudgeBanner(vm.nudges) { latestNudge?.let { com.sohum.bandlog.util.Wrap.dismissNudge(ctx, it.id) }; nudgeHidden = true }
                        "wrap" -> WrapCard(vm.wrap(), hideNumbers = prof.hideNumbers == true) { com.sohum.bandlog.util.Wrap.dismiss(ctx, wrapDate); wrapHidden = true }
                        else -> PendingBanner(vm.pendingMeals)
                    }
                }
            }
        }
        if (isToday && showRecap) item(key = "recapPrompt") { com.sohum.bandlog.ui.platform.RecapPrompt(vm) } // v2.13 platform
        item(key = "streak") { Entrance(1, key = "streak") { DayStreakRow(vm.dayStreak, vm.notice) { vm.dismissNotice() } } }
        item { Entrance(1, key = "weekStrip") { WeekStrip(today, selected, trained) { selected = it } } }
        item {
            Entrance(2, key = "card2") {
                val kcalOver = kotlin.math.round(totals.calories - budget).toInt()
                val (kcalValue, kcalWord) = when {
                    eatenMode -> kotlin.math.round(totals.calories).toInt() to "eaten"
                    kcalOver > 0 -> kcalOver to "over"
                    else -> caloriesLeft to "left"
                }
                // v2.10 "Hide calorie numbers" (schema_v34 opt-in): words instead of kcal in both left and
                // eaten modes. The ring stays; macro cards still show grams (science spec).
                val hideNumbers = prof.hideNumbers == true
                val kcalLabel = if (hideNumbers) (if (eatenMode) "Calories eaten" else "Calories left") else "Calories $kcalWord"
                Card(padding = 20.dp, onClick = flip) {
                    RowSpaceBetween {
                        FlipFace(eatenMode, flipped, if (hideNumbers) Modifier.widthIn(max = 210.dp) else Modifier) {
                            if (hideNumbers) {
                                Text(com.sohum.bandlog.util.Goals.calorieWords(totals.calories, budget), fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink, lineHeight = 26.sp)
                            } else {
                                Text(String.format(Locale.US, "%,d", kcalValue), fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 40.sp)
                            }
                            Text(if (isToday) kcalLabel else "$kcalLabel · ${Dates.short(selected)}", fontSize = 14.sp, fontWeight = FontWeight(500), color = p.muted)
                            if (!hideNumbers && isToday && (vm.burnedKcal > 0 || vm.rolloverKcal > 0)) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Entrance(3, key = "card3") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MacroCard(Modifier.weight(1f), "Protein", totals.protein, prof.proteinTargetG.toDouble(), p.red, eatenMode, flipped, flip)
                        MacroCard(Modifier.weight(1f), "Carbs", totals.carbs, prof.carbTargetG.toDouble(), p.orange, eatenMode, flipped, flip)
                        MacroCard(Modifier.weight(1f), "Fat", totals.fat, prof.fatTargetG.toDouble(), p.blue, eatenMode, flipped, flip)
                    }
                    if (!hintSeen) Text(
                        "Tap a card to switch between left and eaten", fontSize = 12.sp, color = p.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
        if (isToday) {
            val h = vm.healthToday
            val burned = vm.burnedToday
            item {
                Entrance(4, key = "card4") { Column {
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
        if (isToday && showSession) item(key = "todaySession") { Entrance(4, key = "todaySession") { com.sohum.bandlog.ui.platform.TodaySessionCard(vm) } } // v2.13 platform
        item {
            Entrance(4, key = "card5") {
                RowSpaceBetween {
                    Text(if (isToday) "Today" else Dates.long(selected), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                }
            }
        }
        // v2.8: no "Log something" button here — the + button logs; each meal section has its own "+ Add".
        items(todayWorkouts, key = { "w" + it.id }) { w -> Entrance(5, key = "w" + w.id) { WorkoutRow(w, burnKcal = workoutBurn[w.id]) { onOpenWorkout(w) } } }
        items(todayExercises, key = { "e" + it.id }) { e -> Entrance(5, key = "e" + e.id) { com.sohum.bandlog.ui.log.ActivityExerciseRow(e) { onOpenExercise(e) } } }
        // v2.8: Breakfast · Lunch · Dinner · Snacks, each with its totals and "+ Add"; tap a meal to edit it.
        items(com.sohum.bandlog.util.MealTypes.group(todayMeals), key = { "s" + it.type.key }) { s -> Entrance(6, key = "s" + s.type.key) { MealSection(s, onAdd = { t -> onAddMeal(selected, t) }, onOpen = onOpenMeal) } }
    }
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
private fun WrapCard(w: com.sohum.bandlog.util.Wrap.Result, hideNumbers: Boolean = false, onDismiss: () -> Unit) {
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
            // v2.11: "Hide calorie numbers" keeps kcal off the wrap too.
            if (!hideNumbers) WrapStat(Modifier.weight(1f), String.format(Locale.US, "%,d", w.calories), "of ${String.format(Locale.US, "%,d", w.calorieBudget)} kcal", p.ink)
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
 * One macro card. In "left" mode it counts down ("34g / Protein left"); once the target is passed
 * it shows the overshoot ("12g / Protein **over**") in the macro's own colour, like Cal AI. In
 * "eaten" mode it shows what's been eaten ("58g / Protein eaten"). Tapping flips every card.
 */
@Composable
private fun MacroCard(modifier: Modifier, macro: String, consumed: Double, target: Double, color: Color, eaten: Boolean, animate: Boolean, onFlip: () -> Unit) {
    val p = palette
    val safeTarget = target.coerceAtLeast(1.0)
    val over = !eaten && consumed > target && target > 0
    val amount = when { eaten -> kotlin.math.round(consumed); over -> kotlin.math.round(consumed - target); else -> kotlin.math.round(target - consumed) }
    val word = when { eaten -> "eaten"; over -> "over"; else -> "left" }
    Card(modifier, padding = 12.dp, onClick = onFlip) {
        FlipFace(eaten, animate) {
            Text(
                "${amount.toInt().coerceAtLeast(0)}g",
                fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp,
                color = if (over) color else p.ink,
            )
            Row {
                Text("$macro ", fontSize = 12.sp, color = p.muted)
                Text(
                    word,
                    fontSize = 12.sp,
                    fontWeight = if (over) FontWeight(700) else FontWeight(400),
                    color = if (over) color else p.muted,
                )
            }
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

private const val HOME_PREFS = "home_prefs"
private const val MACRO_MODE = "macro_mode"
private const val MACRO_HINT_SEEN = "macro_hint_seen"

/** The number + label of a Home card: a short flip-in whenever the left / eaten mode changes (not on first paint), as on the web. */
@Composable
private fun FlipFace(eaten: Boolean, animate: Boolean, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val rot = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(eaten) {
        if (animate) {
            rot.snapTo(-75f)
            rot.animateTo(0f, androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
        }
    }
    Column(
        modifier.graphicsLayer {
            rotationX = rot.value
            alpha = 1f - kotlin.math.abs(rot.value) / 75f
            cameraDistance = 10f * density
        },
        content = content,
    )
}

/**
 * v2.10: Home's top cards as a HorizontalPager with small dots (none for a single card). The pager
 * bleeds into the screen gutter so card shadows aren't clipped and the next card peeks while swiping.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BannerPager(keys: List<String>, card: @Composable (String) -> Unit) {
    val p = palette
    val pager = androidx.compose.foundation.pager.rememberPagerState { keys.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier.bleed(16.dp, 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            pageSpacing = 26.dp,
            verticalAlignment = Alignment.Top,
            key = { keys.getOrElse(it) { "gone$it" } },
        ) { i -> keys.getOrNull(i)?.let { card(it) } }
        if (keys.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            keys.forEachIndexed { i, k ->
                val active = i == pager.currentPage.coerceAtMost(keys.size - 1)
                val w by androidx.compose.animation.core.animateDpAsState(if (active) 14.dp else 6.dp, label = "dot")
                Box(
                    Modifier.size(16.dp).clickable(onClickLabel = "Show card ${i + 1}: $k") { scope.launch { pager.animateScrollToPage(i) } },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(w, 6.dp).background(if (active) p.ink else p.hair, CircleShape)) }
            }
        }
    }
}

/** Draw [h] / [v] past this item's bounds on each side without taking layout space (so shadows and swipes can use the gutter). */
private fun Modifier.bleed(h: androidx.compose.ui.unit.Dp, v: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.layout { measurable, constraints ->
        val hp = h.roundToPx()
        val vp = v.roundToPx()
        val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + 2 * hp, maxWidth = constraints.maxWidth + 2 * hp) else constraints
        val placeable = measurable.measure(wide)
        layout((placeable.width - 2 * hp).coerceAtLeast(0), (placeable.height - 2 * vp).coerceAtLeast(0)) { placeable.place(-hp, -vp) }
    },
)
