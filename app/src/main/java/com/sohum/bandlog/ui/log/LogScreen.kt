package com.sohum.bandlog.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.components.Chip as SelChip
import androidx.compose.material3.TextButton as M3TextButton
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Full-screen Log page: Workout / Meal / Exercise segments (opened from the + FAB or a workout row). */
@Composable
fun LogScreen(vm: AppViewModel, existing: Workout?, initialDate: String, startOnMeal: Boolean, onClose: () -> Unit, startOnExercise: Boolean = false) {
    val p = palette
    var seg by rememberSaveable { mutableStateOf(if (startOnExercise) 2 else if (startOnMeal) 1 else 0) }
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
            Box(Modifier.padding(16.dp, 8.dp)) { Segmented(listOf("Workout", "Meal", "Exercise"), seg, { seg = it }) }
        }
        when {
            // v2.5: the Workout segment is a type picker first; Bands keeps the band form below.
            existing != null || seg == 0 -> WorkoutTab(vm, existing, initialDate, onClose) { WorkoutForm(vm, existing, initialDate, onClose) }
            seg == 1 -> MealForm(vm, initialDate, onClose)
            else -> ExerciseForm(vm, initialDate, onClose)
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
    var pendingDelete by remember { mutableStateOf(false) }
    var deleteJob by remember { mutableStateOf<Job?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // v2.1: one tap repeats the last session; everything stays editable.
            val last = vm.lastWorkout
            if (existing == null && last != null) Rise(0) {
                val applied = muscles == last.muscles.toSet() && band == last.bandLevel && exercises == last.exercises
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable().background(if (applied) p.card2 else p.btn, CircleShape)
                        .clickable(enabled = !applied) {
                            muscles = last.muscles.toSet(); band = last.bandLevel
                            last.resistanceKg?.let { kg = fmt(it) }; last.minutes?.let { minutes = it.toString() }
                            exercises = last.exercises; error = null
                        }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(com.sohum.bandlog.ui.components.HistoryIcon, null, tint = if (applied) p.muted else p.btnInk, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (applied) "Same as last time · filled in" else "Same as last time", fontSize = 14.sp, fontWeight = FontWeight(700), color = if (applied) p.muted else p.btnInk)
                    Text("  " + last.muscles.joinToString(" · "), fontSize = 13.sp, color = (if (applied) p.muted else p.btnInk).copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
            Rise(0) {
                Card {
                    Text("Muscles", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(10.dp))
                    // v2.5: wraps instead of clipping "Forearms" / "Other" at large font sizes.
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Muscles.ALL.forEach { m -> Chip(m, m in muscles, { muscles = if (m in muscles) muscles - m else muscles + m }) }
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
                        SettingRow("Resistance") { NumberField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' } }, "kg", imeAction = ImeAction.Next) }
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
                if (pendingDelete) {
                    RowSpaceBetween {
                        Text("Deleted · Undo", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.muted)
                        TextButton(onClick = { deleteJob?.cancel(); deleteJob = null; pendingDelete = false }) { Text("Undo", color = p.btn) }
                    }
                } else {
                    TextButton(onClick = {
                        pendingDelete = true
                        deleteJob = scope.launch {
                            delay(5000)
                            busy = true
                            if (vm.deleteWorkout(existing.id)) onClose() else { error = vm.error; busy = false; pendingDelete = false }
                        }
                    }, enabled = !busy) { Text("Delete workout", color = p.red) }
                }
            }
        }
        Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
            PillButton(if (busy) "Saving…" else "Save workout", enabled = !busy, onClick = {
                if (muscles.isEmpty()) { error = "Pick at least one muscle"; return@PillButton }
                scope.launch {
                    busy = true; error = null
                    val picked = Muscles.ALL.filter { it in muscles }
                    val ok = vm.saveWorkout(existing?.id, date, picked, band, kg.toDoubleOrNull(), minutes.toIntOrNull(), exercises.trim(), notes.trim())
                    if (ok) { if (existing == null) vm.pushSessionToHealth(ctx, picked, minutes.toIntOrNull(), date); onClose() } else { error = vm.error; busy = false }
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
private fun SettingRow(label: String, trailing: @Composable () -> Unit) {
    RowSpaceBetween {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight(500), color = palette.ink, modifier = Modifier.padding(vertical = 12.dp))
        trailing()
    }
}

/**
 * Small right-aligned numeric box with a unit suffix. Sizes to its text (72–140 dp) so "10000"
 * never clips, and the keyboard's Done key (or Next in multi-field forms) closes it / moves on.
 */
@Composable
fun NumberField(value: String, onChange: (String) -> Unit, unit: String, imeAction: ImeAction = ImeAction.Done, onDone: (() -> Unit)? = null) {
    val p = palette
    val focus = LocalFocusManager.current
    val style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = TextAlign.End)
    val measurer = rememberTextMeasurer()
    val textW = with(LocalDensity.current) { measurer.measure(value.ifEmpty { "0000" }, style).size.width.toDp() }
    val boxW = (textW + 26.dp).coerceIn(72.dp, 140.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(boxW).height(36.dp).background(p.card2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
            BasicTextField(
                value, onChange, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone = { focus.clearFocus(); onDone?.invoke() },
                    onNext = { focus.moveFocus(FocusDirection.Down) },
                ),
                textStyle = style, cursorBrush = SolidColor(p.ink),
            )
        }
        Text("  $unit", fontSize = 13.sp, color = p.muted)
    }
}

/** A borderless text line; Done closes the keyboard. */
@Composable
internal fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val p = palette
    val focus = LocalFocusManager.current
    BasicTextField(
        value, onChange, Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = p.muted); inner() },
    )
}
