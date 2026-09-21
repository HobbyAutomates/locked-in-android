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
import androidx.compose.material.icons.outlined.Delete
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
fun TodayScreen(vm: AppViewModel, onOpenWorkout: (Workout?) -> Unit) {
    val p = palette
    val today = vm.today
    val totals = totalsFor(vm.meals, today)
    val prof = vm.profile
    val todayMeals = vm.meals.filter { it.date == today }
    val todayWorkouts = vm.workouts.filter { it.date == today }
    val trained = vm.workoutDates.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Rise(0) {
                RowSpaceBetween {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(com.sohum.bandlog.ui.components.LockIcon, null, tint = p.ink, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Locked In", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                    }
                    Row(
                        Modifier.shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Flame(p.flame, 16.dp)
                        Spacer(Modifier.width(5.dp))
                        Text("${vm.weekStreak}", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                    }
                }
                ErrorNote(vm.error, Modifier.padding(top = 8.dp))
            }
        }
        item { Rise(1) { WeekStrip(today, trained) } }
        item {
            Rise(2) {
                Card(padding = 20.dp) {
                    RowSpaceBetween {
                        Column {
                            Text("${(prof.calorieTarget + vm.burnedKcal - totals.calories).toInt().coerceAtLeast(0)}", fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 40.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Calories left", fontSize = 14.sp, fontWeight = FontWeight(500), color = p.muted)
                                if (vm.burnedKcal > 0) Box(Modifier.padding(start = 8.dp).background(p.card2, CircleShape).padding(8.dp, 3.dp)) { Text("+${vm.burnedKcal.toInt()}", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.ink) }
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
                    MacroCard(Modifier.weight(1f), "${(prof.proteinTargetG - totals.protein).toInt().coerceAtLeast(0)}g", "Protein left", (totals.protein / prof.proteinTargetG).toFloat(), p.red)
                    MacroCard(Modifier.weight(1f), "${(prof.carbTargetG - totals.carbs).toInt().coerceAtLeast(0)}g", "Carbs left", (totals.carbs / prof.carbTargetG.coerceAtLeast(1)).toFloat(), p.orange)
                    MacroCard(Modifier.weight(1f), "${(prof.fatTargetG - totals.fat).toInt().coerceAtLeast(0)}g", "Fat left", (totals.fat / prof.fatTargetG.coerceAtLeast(1)).toFloat(), p.blue)
                }
            }
        }
        vm.healthToday?.let { h ->
            item {
                Rise(4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(Modifier.weight(1f), padding = 14.dp) {
                            Text(String.format(Locale.US, "%,d", h.steps), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
                            Text("Steps today", fontSize = 12.sp, color = p.muted)
                        }
                        Card(Modifier.weight(1f), padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(16.dp)); Text(" ${h.activeKcal.toInt()}", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink) }
                            Text("Calories burned", fontSize = 12.sp, color = p.muted)
                        }
                    }
                }
            }
        }
        item {
            Rise(4) {
                RowSpaceBetween {
                    Text("Recently logged", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                }
            }
        }
        items(vm.pendingMeals, key = { "p$it" }) { txt -> Rise(5) { PendingMealRow(txt) } }
        if (todayWorkouts.isEmpty() && todayMeals.isEmpty() && vm.pendingMeals.isEmpty()) item {
            Rise(5) { Text("Nothing yet today. Tap + to log a workout or a meal.", color = p.muted, fontSize = 13.sp) }
        }
        items(todayWorkouts, key = { "w" + it.id }) { w -> Rise(5) { WorkoutRow(w) { onOpenWorkout(w) } } }
        items(todayMeals, key = { "m" + it.id }) { m -> Rise(6) { MealRow(m, onDelete = { vm.launch { vm.deleteMeal(m.id) } }, onFeedback = { r -> vm.launch { runCatching { com.sohum.bandlog.data.Api.feedback(r, m.rawText, m.id) } } }) } }
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
private fun WeekStrip(today: String, trained: Set<String>) {
    val p = palette
    val start = Dates.addDays(today, -6)
    val fmt = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = Dates.addDays(start, i.toLong())
            val isToday = d == today
            val did = d in trained
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val circle = when {
                    isToday -> Modifier.border(2.dp, p.ink, CircleShape)
                    did -> Modifier.background(p.greenBg, CircleShape).border(1.5.dp, p.green, CircleShape)
                    else -> Modifier.border(1.5.dp, p.hair, CircleShape)
                }
                Box(circle.size(30.dp), contentAlignment = Alignment.Center) {
                    Text(Dates.parse(d).format(fmt), fontSize = 12.sp, fontWeight = if (isToday) FontWeight(700) else FontWeight(600), color = if (isToday) p.ink else if (did) p.green else p.muted)
                }
                Text("${Dates.parse(d).dayOfMonth}", fontSize = 13.sp, fontWeight = if (isToday) FontWeight(700) else FontWeight(500), color = if (isToday) p.ink else p.muted)
            }
        }
    }
}

@Composable
private fun MacroCard(modifier: Modifier, value: String, label: String, fraction: Float, color: Color) {
    val p = palette
    Card(modifier, padding = 12.dp) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
        Text(label, fontSize = 12.sp, color = p.muted)
        Spacer(Modifier.height(10.dp))
        Ring(fraction, color, 56.dp, 6.dp, Modifier.align(Alignment.CenterHorizontally)) { Box(Modifier.size(8.dp).background(color, CircleShape)) }
    }
}

@Composable
fun WorkoutRow(w: Workout, onClick: () -> Unit) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    w.minutes?.let { Text("$it mins", fontSize = 12.sp, color = p.muted) }
                    if (w.exercises.isNotBlank()) Text(w.exercises, fontSize = 12.sp, color = p.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun MealRow(m: Meal, onDelete: () -> Unit, onFeedback: ((String) -> Unit)? = null) {
    val p = palette
    var voted by androidx.compose.runtime.remember(m.id) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(BowlIcon, p.orange, p.orangeBg)
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
