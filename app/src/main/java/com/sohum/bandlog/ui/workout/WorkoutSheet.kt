package com.sohum.bandlog.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Overline
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** FlowRow-style wrap without the experimental API: chunk chips into rows of [perRow]. */
@Composable
private fun ChipWrap(items: List<String>, selected: (String) -> Boolean, onToggle: (String) -> Unit, color: (String) -> androidx.compose.ui.graphics.Color, perRow: Int = 3) {
    items.chunked(perRow).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { m -> Chip(m, selected(m), { onToggle(m) }, color(m)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSheet(vm: AppViewModel, existing: Workout?, initialDate: String, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var date by remember { mutableStateOf(existing?.date ?: initialDate) }
    var muscles by remember { mutableStateOf(existing?.muscles?.toSet() ?: emptySet()) }
    var band by remember { mutableStateOf(existing?.bandLevel ?: "Medium") }
    var kg by remember { mutableStateOf(existing?.resistanceKg?.let { trim(it) } ?: "9") }
    var minutes by remember { mutableStateOf(existing?.minutes?.toString() ?: "30") }
    var exercises by remember { mutableStateOf(existing?.exercises ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = cs.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
        ) {
            Text(if (existing == null) "Log workout" else "Edit workout", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(14.dp))

            Overline("Date")
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { pickDate = true }) { Text(Dates.long(date)) }
            Spacer(Modifier.height(14.dp))

            Overline("Muscles")
            Spacer(Modifier.height(8.dp))
            ChipWrap(Muscles.ALL, { it in muscles }, { m -> muscles = if (m in muscles) muscles - m else muscles + m }, { Muscles.color(it) })
            Spacer(Modifier.height(6.dp))

            Overline("Band")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Muscles.BAND_LEVELS.forEach { l -> Chip(l, band == l, { band = l }, Muscles.bandColor(l)) }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    kg, { kg = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f),
                    label = { Text("Resistance kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    minutes, { minutes = it.filter { c -> c.isDigit() } }, Modifier.weight(1f),
                    label = { Text("Minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(exercises, { exercises = it }, Modifier.fillMaxWidth(), label = { Text("Exercises (optional)") }, minLines = 2)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2)
            Spacer(Modifier.height(12.dp))
            ErrorNote(error)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) {
                    TextButton(onClick = {
                        scope.launch { busy = true; if (vm.deleteWorkout(existing.id)) onClose() else { error = vm.error; busy = false } }
                    }, enabled = !busy) { Text("Delete", color = cs.error) }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        if (muscles.isEmpty()) { error = "Pick at least one muscle"; return@Button }
                        scope.launch {
                            busy = true; error = null
                            val ok = vm.saveWorkout(
                                existing?.id, date, Muscles.ALL.filter { it in muscles }, band,
                                kg.toDoubleOrNull(), minutes.toIntOrNull(), exercises.trim(), notes.trim(),
                            )
                            if (ok) onClose() else { error = vm.error; busy = false }
                        }
                    },
                    enabled = !busy,
                ) { Text(if (busy) "Saving…" else "Save", fontWeight = FontWeight(700)) }
                Spacer(Modifier.width(0.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> date = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC).toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

private fun trim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
