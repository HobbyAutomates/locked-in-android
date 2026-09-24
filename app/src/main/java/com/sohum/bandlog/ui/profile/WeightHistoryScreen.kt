package com.sohum.bandlog.ui.profile

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.IconTile
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScaleIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

/** Big current number, the movement since the first weigh-in, and the full log. */
@Composable
fun WeightHistoryScreen(vm: AppViewModel, onBack: () -> Unit, openLog: Boolean = false) {
    val p = palette
    val rows = vm.weights
    var showLog by remember { mutableStateOf(openLog) }

    LaunchedEffect(Unit) { vm.loadWeights() }

    val latest = rows.firstOrNull()?.weightKg ?: vm.profile.weightKg
    val first = rows.lastOrNull()
    val delta = if (latest != null && first != null) latest - first.weightKg else null

    SubPage("Weight history", onBack) {
        Rise(0) {
            Card(padding = 20.dp) {
                Text("Current weight", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
                Spacer(Modifier.height(6.dp))
                Text(
                    latest?.let { "${fmt(it)} kg" } ?: "—",
                    fontSize = 44.sp, fontWeight = FontWeight(800), letterSpacing = (-1.8).sp, color = p.ink, lineHeight = 46.sp,
                )
                if (delta != null && first != null) {
                    val since = runCatching { LocalDate.parse(first.date).format(monthFmt) }.getOrDefault(first.date)
                    val tint = when { delta > 0.05 -> p.green; delta < -0.05 -> p.blue; else -> p.muted }
                    Text(
                        "${if (delta > 0) "+" else ""}${fmt(delta)} kg since $since",
                        fontSize = 14.sp, fontWeight = FontWeight(600), color = tint,
                    )
                }
                vm.profile.goalWeightKg?.let { g ->
                    Text("Goal ${fmt(g)} kg", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Rise(1) { PillButton("+  Log Weight", { showLog = true }) }
        Rise(1) { ErrorNote(vm.error) }

        Rise(2) {
            if (rows.isEmpty()) {
                Text("No weigh-ins yet. Log one to start the chart on Progress.", fontSize = 13.sp, color = p.muted)
            } else {
                Card(padding = 0.dp) {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        rows.forEachIndexed { i, r ->
                            if (i > 0) Hair()
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(ScaleIcon, p.ink, p.card2)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${fmt(r.weightKg)} kg", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                                    Text(
                                        Dates.relative(r.date) + if (r.note.isNotBlank()) " · ${r.note}" else "",
                                        fontSize = 12.sp, color = p.muted, maxLines = 1,
                                    )
                                }
                                // Movement against the previous (older) entry.
                                rows.getOrNull(i + 1)?.let { prev ->
                                    val d = r.weightKg - prev.weightKg
                                    Text(
                                        "${if (d > 0) "+" else ""}${fmt(d)}",
                                        fontSize = 13.sp, fontWeight = FontWeight(700),
                                        color = when { d > 0.05 -> p.green; d < -0.05 -> p.blue; else -> p.muted },
                                    )
                                }
                                IconButton(onClick = { vm.launch { vm.deleteWeight(r.id) } }, Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.Delete, "Delete", tint = p.muted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (showLog) LogWeightDialog(vm) { showLog = false }
}

@Composable
private fun LogWeightDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var date by remember { mutableStateOf(Dates.today()) }
    var kg by remember { mutableStateOf(vm.profile.weightKg?.let { fmt(it) } ?: "") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(28.dp)).padding(22.dp)) {
            Text("Log weight", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
            Spacer(Modifier.height(14.dp))
            RowSpaceBetween {
                Text("Date", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                Box(
                    Modifier.background(p.card2, RoundedCornerShape(10.dp)).clickable {
                        val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now(com.sohum.bandlog.util.Dates.ZONE))
                        runCatching {
                            DatePickerDialog(ctx, { _, y, m, dd -> date = LocalDate.of(y, m + 1, dd).toString() }, d.year, d.monthValue - 1, d.dayOfMonth)
                                .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                        }
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text(Dates.short(date), fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink) }
            }
            Spacer(Modifier.height(10.dp))
            RowSpaceBetween {
                Text("Weight", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                NumberField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' } }, "kg", imeAction = androidx.compose.ui.text.input.ImeAction.Next)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                BasicTextField(
                    note, { note = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                    textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (note.isEmpty()) Text("Note (optional)", fontSize = 14.sp, color = p.muted); inner() },
                )
            }
            Spacer(Modifier.height(18.dp))
            PillButton(if (busy) "Saving…" else "Save", enabled = !busy && kg.toDoubleOrNull() != null, onClick = {
                scope.launch {
                    busy = true
                    val ok = vm.logWeight(date, kg.toDouble(), note.trim())
                    busy = false
                    if (ok) onDismiss()
                }
            })
            Spacer(Modifier.height(8.dp))
            PillButton("Cancel", onDismiss, height = 44.dp, bg = p.card2, fg = p.ink)
        }
    }
}
