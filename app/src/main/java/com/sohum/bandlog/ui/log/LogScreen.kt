package com.sohum.bandlog.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.ParseResult
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.MicIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.components.Chip as SelChip
import com.sohum.bandlog.ui.scan.ScanForm
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.TextButton as M3TextButton
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Full-screen Log page: Workout / Meal segments (opened from the + FAB or a workout row). */
@Composable
fun LogScreen(vm: AppViewModel, existing: Workout?, initialDate: String, startOnMeal: Boolean, onClose: () -> Unit) {
    val p = palette
    var seg by rememberSaveable { mutableStateOf(if (startOnMeal) 1 else 0) }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(18.dp)) }
            Text(if (existing != null) "Edit workout" else "Log", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.width(40.dp))
        }
        if (existing == null) {
            Box(Modifier.padding(16.dp, 8.dp)) { Segmented(listOf("Workout", "Meal", "Scan label"), seg, { seg = it }) }
        }
        when {
            existing != null || seg == 0 -> WorkoutForm(vm, existing, initialDate, onClose)
            seg == 1 -> MealForm(vm, initialDate, onClose)
            else -> ScanForm()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutForm(vm: AppViewModel, existing: Workout?, initialDate: String, onClose: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(existing?.date ?: initialDate) }
    var muscles by remember { mutableStateOf(existing?.muscles?.toSet() ?: emptySet()) }
    var band by remember { mutableStateOf(existing?.bandLevel ?: "Medium") }
    var kg by remember { mutableStateOf(existing?.resistanceKg?.let { fmt(it) } ?: "9") }
    var minutes by remember { mutableStateOf(existing?.minutes?.toString() ?: "30") }
    var exercises by remember { mutableStateOf(existing?.exercises ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Rise(0) {
                Card {
                    Text("Muscles", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(10.dp))
                    Muscles.ALL.chunked(3).forEach { row ->
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m -> Chip(m, m in muscles, { muscles = if (m in muscles) muscles - m else muscles + m }) }
                        }
                    }
                }
            }
            Rise(1) {
                Card(padding = 0.dp) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SettingRow("Date") {
                            Row(Modifier.clickable { pickDate = true }, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (date == Dates.today()) "Today, ${Dates.short(date).drop(4)}" else Dates.short(date), fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                                Text("  ›", fontSize = 15.sp, color = p.muted)
                            }
                        }
                        Hair()
                        SettingRow("Band") {
                            Row(Modifier.background(p.card2, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Muscles.BAND_LEVELS.forEach { l ->
                                    val sel = l == band
                                    Box(Modifier.height(30.dp).background(if (sel) p.btn else androidx.compose.ui.graphics.Color.Transparent, CircleShape).clickable { band = l }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(l, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.btnInk else p.muted)
                                    }
                                }
                            }
                        }
                        Hair()
                        SettingRow("Resistance") { NumberField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' } }, "kg") }
                        Hair()
                        SettingRow("Duration") { NumberField(minutes, { minutes = it.filter(Char::isDigit) }, "min") }
                    }
                }
            }
            Rise(2) {
                Card {
                    Text("Exercises", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    PlainField(exercises, { exercises = it }, "Rows, chest press, lateral raise")
                }
            }
            Rise(3) {
                Card {
                    Text("Notes", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    PlainField(notes, { notes = it }, "Rows felt heavy, try heavy band next time")
                }
            }
            ErrorNote(error)
            if (existing != null) {
                TextButton(onClick = { scope.launch { busy = true; if (vm.deleteWorkout(existing.id)) onClose() else { error = vm.error; busy = false } } }, enabled = !busy) { Text("Delete workout", color = p.red) }
            }
        }
        Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
            PillButton(if (busy) "Saving…" else "Save workout", enabled = !busy, onClick = {
                if (muscles.isEmpty()) { error = "Pick at least one muscle"; return@PillButton }
                scope.launch {
                    busy = true; error = null
                    val ok = vm.saveWorkout(existing?.id, date, Muscles.ALL.filter { it in muscles }, band, kg.toDoubleOrNull(), minutes.toIntOrNull(), exercises.trim(), notes.trim())
                    if (ok) onClose() else { error = vm.error; busy = false }
                }
            })
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { ms -> date = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC).toString() }; pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun MealForm(vm: AppViewModel, date: String, onClose: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var items by remember { mutableStateOf<List<MealItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var fixText by remember { mutableStateOf("") }
    var fixing by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf("") }
    var showSaveAs by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadSavedMeals() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (vm.savedMeals.isNotEmpty() && result == null) Rise(0) {
                Column {
                    Text("Saved meals · one tap", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    vm.savedMeals.chunked(2).forEach { row ->
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { sm ->
                                Card(Modifier.weight(1f), padding = 12.dp, onClick = { text = sm.name; items = sm.items; result = ParseResult(sm.items, emptyList(), emptyList()) }) {
                                    Text(sm.name, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                                    Text("${sm.calories.toInt()} kcal · ${fmt(sm.proteinG)} g P", fontSize = 12.sp, color = p.muted)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Rise(0) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(MicIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Describe what you ate", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                            Text("Tap the box and dictate with Wispr Flow", fontSize = 12.sp, color = p.muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        BasicTextField(
                            text, { text = it }, Modifier.fillMaxWidth().height(64.dp),
                            textStyle = TextStyle(fontSize = 15.sp, color = p.ink, lineHeight = 22.sp), cursorBrush = SolidColor(p.ink),
                            decorationBox = { inner -> if (text.isEmpty()) Text("150 g rice, 100 g dal, 2 eggs and a scoop of whey", fontSize = 15.sp, color = p.muted); inner() },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PillButton(if (parsing) "Working out the calories…" else "Work out the calories", enabled = text.isNotBlank() && !parsing, height = 48.dp, onClick = {
                        scope.launch {
                            parsing = true; error = null; result = null
                            try { result = Api.parseMeal(text); items = result!!.items } catch (e: Exception) { error = e.message } finally { parsing = false }
                        }
                    })
                    if (result == null) {
                        Spacer(Modifier.height(8.dp))
                        // Cal AI-style: log instantly, Haiku prices it in the background, row appears on Home right away.
                        PillButton("Log now, review later", enabled = text.isNotBlank() && !parsing, height = 44.dp, bg = p.card2, fg = p.ink, onClick = { vm.quickLogMeal(text.trim(), date); onClose() })
                    }
                    if (parsing) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track) }
                }
            }
            ErrorNote(error)
            val r = result
            if (r != null) {
                Rise(1) { Text("Review · edit grams if needed", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(horizontal = 4.dp)) }
                Rise(2) {
                    Card(padding = 0.dp) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            items.forEachIndexed { idx, it ->
                                if (idx > 0) Hair()
                                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(it.name + if (it.source == "estimated") " ~" else "", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${it.calories.toInt()} kcal", fontSize = 12.sp, color = p.muted)
                                            MacroDot("${fmt(it.proteinG)}g", p.red); MacroDot("${fmt(it.carbsG)}g", p.orange); MacroDot("${fmt(it.fatG)}g", p.blue)
                                        }
                                    }
                                    var g by remember(idx, r) { mutableStateOf(fmt(it.grams)) }
                                    // Delta badge: shows "+99 kcal" in black for a moment after a grams edit.
                                    var delta by remember(idx, r) { mutableStateOf(0) }
                                    var showDelta by remember(idx, r) { mutableStateOf(false) }
                                    LaunchedEffect(delta) { if (delta != 0) { showDelta = true; kotlinx.coroutines.delay(1400); showDelta = false } }
                                    AnimatedVisibility(showDelta, enter = scaleIn() + fadeIn(), exit = fadeOut() + scaleOut()) {
                                        Box(Modifier.padding(end = 6.dp).background(if (delta > 0) p.btn else p.card2, CircleShape).padding(8.dp, 3.dp)) {
                                            Text((if (delta > 0) "+" else "") + "$delta kcal", fontSize = 11.sp, fontWeight = FontWeight(700), color = if (delta > 0) p.btnInk else p.ink)
                                        }
                                    }
                                    NumberField(g, { v ->
                                        g = v.filter { c -> c.isDigit() || c == '.' }
                                        g.toDoubleOrNull()?.let { d -> val next = it.withGrams(d); delta = (next.calories - it.calories).toInt(); items = items.toMutableList().also { l -> l[idx] = next } }
                                    }, "g")
                                    IconButton(onClick = { items = items.filterIndexed { i, _ -> i != idx } }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Remove", tint = p.muted) }
                                }
                            }
                        }
                    }
                }
                if (r.assumptions.isNotEmpty() || r.unparsed.isNotEmpty()) Rise(3) {
                    Text((r.assumptions + r.unparsed.map { "Ignored: $it" }).joinToString(" · "), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
                // "Fix issue": say what's wrong in plain words; Haiku re-parses with that in view.
                Rise(4) {
                    Card(padding = 12.dp) {
                        Text("Something wrong? Tell me and I'll redo it", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(10.dp)) {
                                BasicTextField(fixText, { fixText = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                    decorationBox = { inner -> if (fixText.isEmpty()) Text("e.g. it was two scoops, and the rice was raw", fontSize = 14.sp, color = p.muted); inner() })
                            }
                            Spacer(Modifier.width(8.dp))
                            PillButton(if (fixing) "…" else "Fix", enabled = fixText.isNotBlank() && !fixing, modifier = Modifier.width(64.dp), height = 40.dp, onClick = {
                                scope.launch {
                                    fixing = true; error = null
                                    try { val fixed = Api.parseMeal(text, fixText, items); result = fixed; items = fixed.items; fixText = "" } catch (e: Exception) { error = e.message } finally { fixing = false }
                                }
                            })
                        }
                    }
                }
                Rise(5) {
                    if (showSaveAs) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            BasicTextField(savedName, { savedName = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (savedName.isEmpty()) Text("Name it, e.g. Dinner usual", fontSize = 14.sp, color = p.muted); inner() })
                        }
                        Spacer(Modifier.width(8.dp))
                        PillButton("Save", enabled = savedName.isNotBlank(), modifier = Modifier.width(72.dp), height = 40.dp, onClick = {
                            scope.launch { runCatching { Api.saveSavedMeal(savedName.trim(), items); vm.loadSavedMeals(); showSaveAs = false; savedName = "" }.onFailure { error = it.message } }
                        })
                    } else M3TextButton(onClick = { showSaveAs = true }) { Text("Save as a repeat meal", color = p.muted, fontSize = 13.sp) }
                }
            }
        }
        if (result != null) {
            Row(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${items.sumOf { it.calories }.toInt()} kcal", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    Text("${fmt(items.sumOf { it.proteinG })} g protein", fontSize = 12.sp, color = p.muted)
                }
                Spacer(Modifier.width(12.dp))
                PillButton(if (saving) "Saving…" else "Save meal", enabled = items.isNotEmpty() && !saving, modifier = Modifier.weight(1f), onClick = {
                    scope.launch { saving = true; if (vm.saveMeal(date, text.trim(), items)) onClose() else { error = vm.error; saving = false } }
                })
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, trailing: @Composable () -> Unit) {
    RowSpaceBetween {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight(500), color = palette.ink, modifier = Modifier.padding(vertical = 12.dp))
        trailing()
    }
}

/** Small right-aligned numeric box with a unit suffix. */
@Composable
fun NumberField(value: String, onChange: (String) -> Unit, unit: String) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(60.dp).height(36.dp).background(p.card2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
            BasicTextField(
                value, onChange, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(p.ink),
            )
        }
        Text("  $unit", fontSize = 13.sp, color = p.muted)
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val p = palette
    BasicTextField(
        value, onChange, Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = p.muted); inner() },
    )
}
