package com.sohum.bandlog.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Overline
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.SectionGap
import com.sohum.bandlog.ui.today.MealCard
import com.sohum.bandlog.ui.today.WorkoutCard
import com.sohum.bandlog.ui.workout.WorkoutSheet
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(Dates.today()) }
    var sheetFor by remember { mutableStateOf<Workout?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val byDate = remember(vm.workouts) { vm.workouts.groupBy { it.date } }
    val mealDates = remember(vm.meals) { vm.meals.map { it.date }.toSet() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp)) {
        item {
            Overline("Calendar", color = cs.primary)
            RowSpaceBetween {
                Text(Dates.month(month.atDay(1)), fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp)
                Row {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Next month") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, Modifier.weight(1f), fontSize = 11.sp, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(4.dp))
            val first = month.atDay(1)
            val lead = (first.dayOfWeek.value + 6) % 7
            val days = month.lengthOfMonth()
            val cells = List(lead) { null } + (1..days).map { first.withDayOfMonth(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { d -> Box(Modifier.weight(1f)) { if (d != null) DayCell(d, d.toString() == selected, byDate[d.toString()].orEmpty(), d.toString() in mealDates) { selected = d.toString() } } }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(4.dp))
            }
            SectionGap()
            RowSpaceBetween {
                Text(Dates.long(selected), fontWeight = FontWeight(700), fontSize = 16.sp)
                TextButton(onClick = { sheetFor = null; showSheet = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Log") }
            }
        }
        val dayWorkouts = byDate[selected].orEmpty()
        val dayMeals = vm.meals.filter { it.date == selected }
        if (dayWorkouts.isEmpty() && dayMeals.isEmpty()) item { Text("Nothing logged.", color = cs.onSurfaceVariant, fontSize = 13.sp) }
        items(dayWorkouts, key = { it.id }) { w -> WorkoutCard(w) { sheetFor = w; showSheet = true }; Spacer(Modifier.height(8.dp)) }
        items(dayMeals, key = { it.id }) { m -> MealCard(m) { vm.launch { vm.deleteMeal(m.id) } }; Spacer(Modifier.height(8.dp)) }
    }

    if (showSheet) WorkoutSheet(vm, sheetFor, selected, onClose = { showSheet = false })
}

@Composable
private fun DayCell(d: LocalDate, selected: Boolean, workouts: List<Workout>, hasMeal: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isToday = d.toString() == Dates.today()
    val muscles = workouts.flatMap { it.muscles }.distinct().take(4)
    val bg = when {
        selected -> cs.primary.copy(alpha = 0.22f)
        workouts.isNotEmpty() -> cs.surfaceVariant
        else -> cs.surfaceContainer
    }
    Column(
        Modifier.fillMaxWidth().aspectRatio(0.85f)
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${d.dayOfMonth}", fontSize = 13.sp,
            fontWeight = if (isToday || selected) FontWeight(800) else FontWeight(500),
            color = if (isToday) cs.primary else cs.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            muscles.forEach { Box(Modifier.size(5.dp).background(Muscles.color(it), CircleShape)) }
        }
        if (hasMeal) { Spacer(Modifier.height(2.dp)); Box(Modifier.size(4.dp).background(cs.onSurfaceVariant, CircleShape)) }
    }
}
