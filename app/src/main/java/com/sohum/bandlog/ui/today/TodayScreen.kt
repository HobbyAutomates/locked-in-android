package com.sohum.bandlog.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.ParseResult
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Overline
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.SectionGap
import com.sohum.bandlog.ui.components.Stat
import com.sohum.bandlog.ui.components.Tag
import com.sohum.bandlog.ui.theme.Ok
import com.sohum.bandlog.ui.theme.Warn
import com.sohum.bandlog.ui.workout.WorkoutSheet
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val today = vm.today
    val totals = totalsFor(vm.meals, today)
    val todayMeals = vm.meals.filter { it.date == today }
    val todayWorkouts = vm.workouts.filter { it.date == today }
    var sheetFor by remember { mutableStateOf<Workout?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 96.dp)) {
        item {
            RowSpaceBetween {
                Column {
                    Overline(Dates.long(today), color = cs.primary)
                    Text("Today", fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp)
                }
                IconButton(onClick = { vm.refresh() }) {
                    if (vm.loading) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Refresh", tint = cs.onSurfaceVariant)
                }
            }
            ErrorNote(vm.error, Modifier.padding(top = 8.dp))
            SectionGap()
        }

        item {
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Ring(totals.protein, vm.profile.proteinTargetG.toDouble(), "Protein", "g", Ok)
                    Ring(totals.calories, vm.profile.calorieTarget.toDouble(), "Calories", "kcal", cs.primary)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${vm.thisWeek}/${vm.profile.weeklyWorkoutTarget}", "sessions this week", accent = if (vm.thisWeek >= vm.profile.weeklyWorkoutTarget) Ok else null)
                    Stat("${vm.weekStreak}w", "week streak", accent = if (vm.weekStreak > 0) Warn else null)
                    Stat("${vm.mealStreak}d", "meals logged")
                }
            }
            SectionGap()
        }

        item {
            VoiceMealBox(vm, today)
            SectionGap()
        }

        item {
            RowSpaceBetween {
                Overline("Workout")
                TextButton(onClick = { sheetFor = null; showSheet = true }) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Log")
                }
            }
            if (todayWorkouts.isEmpty()) {
                Card(onClick = { sheetFor = null; showSheet = true }) {
                    Text("No session yet today", fontWeight = FontWeight(600))
                    Text("Tap to log one — muscles, band, minutes.", color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        items(todayWorkouts, key = { it.id }) { w ->
            WorkoutCard(w, onClick = { sheetFor = w; showSheet = true })
            Spacer(Modifier.height(8.dp))
        }

        item { SectionGap(); Overline("Meals"); Spacer(Modifier.height(8.dp)) }
        if (todayMeals.isEmpty()) item {
            Text("Nothing logged yet. Dictate what you ate above.", color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
        items(todayMeals, key = { it.id }) { m ->
            MealCard(m, onDelete = { vm.launch { vm.deleteMeal(m.id) } })
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSheet) WorkoutSheet(vm, sheetFor, today, onClose = { showSheet = false })
}

@Composable
fun WorkoutCard(w: Workout, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(onClick = onClick) {
        RowSpaceBetween {
            Text(Dates.relative(w.date), fontWeight = FontWeight(700))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(w.bandLevel + (w.resistanceKg?.let { " · ${fmt(it)} kg" } ?: ""), Muscles.bandColor(w.bandLevel))
                w.minutes?.let { Spacer(Modifier.width(6.dp)); Text("$it min", color = cs.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        w.muscles.chunked(4).forEach { row ->
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { Tag(it, Muscles.color(it)) }
            }
        }
        if (w.exercises.isNotBlank()) Text(w.exercises, color = cs.onSurfaceVariant, fontSize = 13.sp)
        if (w.notes.isNotBlank()) Text(w.notes, color = cs.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
fun MealCard(m: Meal, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card {
        RowSpaceBetween {
            Text("${m.calories.toInt()} kcal · ${fmt(m.protein)} g protein", fontWeight = FontWeight(700))
            IconButton(onClick = onDelete, Modifier.width(32.dp).height(32.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = cs.onSurfaceVariant) }
        }
        m.items.forEach { i ->
            RowSpaceBetween {
                Text("${i.name} · ${fmt(i.grams)} g" + if (i.source == "estimated") " ~" else "", fontSize = 13.sp, color = cs.onSurface)
                Text("${i.calories.toInt()} kcal · ${fmt(i.proteinG)} g", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
        if (m.rawText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("“${m.rawText}”", fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun VoiceMealBox(vm: AppViewModel, date: String) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Card {
        Overline("What did you eat?")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth(), minLines = 3,
            placeholder = { Text("Tap here, then dictate with Wispr Flow: “150 g rice, 100 g dal, 2 eggs, 1 scoop whey…”") },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        parsing = true; error = null; result = null
                        try { result = Api.parseMeal(text) } catch (e: Exception) { error = e.message } finally { parsing = false }
                    }
                },
                enabled = text.isNotBlank() && !parsing,
            ) { Text(if (parsing) "Working out calories…" else "Log meal", fontWeight = FontWeight(700)) }
            if (text.isNotBlank() && !parsing) OutlinedButton(onClick = { text = ""; result = null; error = null }) { Text("Clear") }
        }
        if (parsing) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        ErrorNote(error, Modifier.padding(top = 10.dp))

        val r = result
        if (r != null) {
            Spacer(Modifier.height(14.dp))
            Overline("Review", color = cs.primary)
            Spacer(Modifier.height(6.dp))
            var items by remember(r) { mutableStateOf(r.items) }
            items.forEachIndexed { idx, it ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.name + if (it.source == "estimated") "  ~est" else "", fontWeight = FontWeight(600), fontSize = 14.sp)
                        Text("${it.calories.toInt()} kcal · P ${fmt(it.proteinG)} · C ${fmt(it.carbsG)} · F ${fmt(it.fatG)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    var g by remember(it.id, idx) { mutableStateOf(fmt(it.grams)) }
                    OutlinedTextField(
                        g, { v -> g = v.filter { c -> c.isDigit() || c == '.' }; v.toDoubleOrNull()?.let { d -> items = items.toMutableList().also { l -> l[idx] = it.withGrams(d) } } },
                        Modifier.width(88.dp), singleLine = true, suffix = { Text("g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    IconButton(onClick = { items = items.filterIndexed { i, _ -> i != idx } }) { Icon(Icons.Outlined.Delete, "Remove", tint = cs.onSurfaceVariant) }
                }
            }
            if (r.assumptions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                r.assumptions.forEach { Text("• $it", fontSize = 12.sp, color = cs.onSurfaceVariant) }
            }
            if (r.unparsed.isNotEmpty()) Text("Ignored: ${r.unparsed.joinToString()}", fontSize = 12.sp, color = Warn)
            Spacer(Modifier.height(10.dp))
            RowSpaceBetween {
                Text("${items.sumOf { it.calories }.toInt()} kcal · ${fmt(items.sumOf { it.proteinG })} g protein", fontWeight = FontWeight(700))
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            if (vm.saveMeal(date, text.trim(), items)) { text = ""; result = null } else error = vm.error
                            saving = false
                        }
                    },
                    enabled = items.isNotEmpty() && !saving,
                ) { Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight(700)) }
            }
        }
    }
}

fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)
