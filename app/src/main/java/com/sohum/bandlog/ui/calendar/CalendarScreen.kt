package com.sohum.bandlog.ui.calendar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.MealRow
import com.sohum.bandlog.ui.today.WorkoutRow
import com.sohum.bandlog.util.Dates
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(vm: AppViewModel, onOpenWorkout: (Workout?, String) -> Unit) {
    val p = palette
    var month by remember { mutableStateOf(YearMonth.now(com.sohum.bandlog.util.Dates.ZONE)) }
    var selected by remember { mutableStateOf(Dates.today()) }
    val byDate = remember(vm.workouts) { vm.workouts.groupBy { it.date } }
    val mealDates = remember(vm.meals) { vm.meals.map { it.date }.toSet() }
    val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    val inMonth = { d: String -> d.startsWith(month.toString()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Rise(0) {
                RowSpaceBetween {
                    ScreenTitle("Calendar")
                    Row(Modifier.shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronLeft, "Previous month", tint = p.ink) }
                        Text(month.atDay(1).format(monthFmt), fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(onClick = { month = month.plusMonths(1) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronRight, "Next month", tint = p.ink) }
                    }
                }
            }
        }
        item {
            Rise(1) {
                Card(padding = 12.dp) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted, textAlign = TextAlign.Center) }
                    }
                    Spacer(Modifier.height(8.dp))
                    val first = month.atDay(1)
                    val lead = (first.dayOfWeek.value + 6) % 7
                    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { first.withDayOfMonth(it).toString() }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { d ->
                                Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.TopCenter) {
                                    if (d != null) DayCircle(d, d == selected, d in byDate, d in mealDates) { selected = d }
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val sessions = byDate.keys.count(inMonth)
                    val logged = (byDate.keys + mealDates).count(inMonth)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("$sessions", "sessions", p.green); Stat("$logged", "days logged", p.ink); Stat("${vm.weekStreak}", "wk streak", p.flame)
                    }
                }
            }
        }
        item {
            Rise(2) {
                RowSpaceBetween {
                    Text(Dates.long(selected), fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    Text("+ Log", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.background(p.card, CircleShape).clickable { onOpenWorkout(null, selected) }.padding(12.dp, 6.dp))
                }
            }
        }
        val dayWorkouts = byDate[selected].orEmpty()
        val dayMeals = vm.meals.filter { it.date == selected }
        if (dayWorkouts.isEmpty() && dayMeals.isEmpty()) item { Text("Nothing logged.", color = p.muted, fontSize = 13.sp) }
        items(dayWorkouts, key = { "w" + it.id }) { w -> Rise(3) { WorkoutRow(w) { onOpenWorkout(w, selected) } } }
        items(dayMeals, key = { "m" + it.id }) { m -> Rise(4) { MealRow(m, onDelete = { vm.launch { vm.deleteMeal(m.id) } }) } }
    }
}

@Composable
private fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row { Text(value, fontSize = 12.sp, fontWeight = FontWeight(700), color = color); Text(" $label", fontSize = 12.sp, color = palette.muted) }
}

@Composable
private fun DayCircle(d: String, selected: Boolean, trained: Boolean, hasMeal: Boolean, onClick: () -> Unit) {
    val p = palette
    val isToday = d == Dates.today()
    val future = d > Dates.today()
    val m = when {
        selected -> Modifier.background(p.btn, CircleShape)
        trained -> Modifier.background(p.greenBg, CircleShape).border(1.5.dp, p.green, CircleShape)
        isToday -> Modifier.border(2.dp, p.ink, CircleShape)
        future -> Modifier
        else -> Modifier.border(1.5.dp, p.hair, CircleShape)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(m.size(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(
                "${Dates.parse(d).dayOfMonth}", fontSize = 13.sp,
                fontWeight = if (selected || trained || isToday) FontWeight(700) else FontWeight(500),
                color = when { selected -> p.btnInk; trained -> p.green; isToday -> p.ink; else -> p.muted },
            )
        }
        if (hasMeal && !selected) { Spacer(Modifier.height(3.dp)); Box(Modifier.size(4.dp).background(p.orange, CircleShape)) }
    }
}
