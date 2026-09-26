package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.BodyMeasurement
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.UndoRow
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.progress.WeightTrendLine
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import kotlinx.coroutines.launch
import java.util.Locale

internal fun num1(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

/** Opens the system date picker on [iso]; calls [onPick] with the new ISO date (never in the future). */
internal fun pickDate(ctx: android.content.Context, iso: String, onPick: (String) -> Unit) {
    val d = runCatching { java.time.LocalDate.parse(iso) }.getOrDefault(java.time.LocalDate.now(Dates.ZONE))
    android.app.DatePickerDialog(ctx, { _, y, m, day -> onPick(java.time.LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

/**
 * v2.13 body measurements (spec §11, free): an entry sheet, a small chart per measure, and the
 * history with edit and delete (Undo). The Progress BMI card's waist uses the newest entry.
 */
@Composable
fun MeasurementsScreen(pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    LaunchedEffect(Unit) { pvm.loadMeasurements() }
    var sheet by remember { mutableStateOf<BodyMeasurement?>(null) }
    var deleting by remember { mutableStateOf<BodyMeasurement?>(null) }
    SubPage("Body measurements", onBack) {
        if (pvm.measurementsSupported == false) {
            ComingSoonCard("Body measurements", "Waist, chest, hips, arms and more, with a chart for each, arrive with the next server update.")
            return@SubPage
        }
        PillButton("Add measurements", { sheet = BodyMeasurement(null, Dates.today(), emptyMap()) }, icon = LineIcons.Plus)
        val rows = pvm.measurements
        if (rows.isEmpty() && pvm.measurementsSupported == true) {
            Card {
                Text("Track more than the scale", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("Measure waist, hips and arms every couple of weeks, same time of day. Inches lost often show before kilos do.", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        MotionScreen {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BodyMeasurement.FIELDS.filter { (k, _, _) -> rows.any { it.get(k) != null } }.forEachIndexed { i, (k, label, unit) ->
                    Entrance(i, key = "m-$k") { MeasureCard(rows, k, label, unit) }
                }
            }
        }
        if (rows.isNotEmpty()) {
            Text("History", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            rows.forEach { m ->
                if (deleting?.id == m.id) {
                    UndoRow(onUndo = { deleting = null }, onExpire = { m.id?.let { pvm.hideMeasurement(it); pvm.commitDeleteMeasurement(it) }; deleting = null })
                } else Card(onClick = { sheet = m }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Dates.relative(m.date), fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(
                                BodyMeasurement.FIELDS.mapNotNull { (k, l, u) -> m.get(k)?.let { "$l ${num1(it)}${if (u == "%") "%" else ""}" } }.joinToString(" · ").ifBlank { "No values" },
                                fontSize = 13.sp, color = p.muted, lineHeight = 18.sp,
                            )
                            if (m.note.isNotBlank()) Text(m.note, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(LineIcons.ChevronRight, null, tint = p.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        ErrorNote(pvm.error)
    }
    sheet?.let { m -> MeasurementSheet(pvm, m, onDelete = { deleting = m; sheet = null }) { sheet = null } }
}

@Composable
private fun MeasureCard(rows: List<BodyMeasurement>, key: String, label: String, unit: String) {
    val p = palette
    val pts = rows.mapNotNull { r -> r.get(key)?.let { r.date to it } }.sortedBy { it.first }
    val latest = pts.last().second
    val first = pts.first().second
    val change = latest - first
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(num1(latest), fontSize = 28.sp, fontWeight = FontWeight(400), letterSpacing = (-0.8).sp, color = p.ink)
                    Text(" $unit", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            if (pts.size > 1) Text(
                (if (change > 0) "+" else if (change < 0) "−" else "±") + num1(kotlin.math.abs(change)) + " $unit since ${Dates.short(pts.first().first)}",
                fontSize = 12.sp, color = p.muted,
            )
        }
        if (pts.size >= 2) {
            val d0 = Dates.parse(pts.first().first).toEpochDay().toFloat()
            val span = (Dates.parse(pts.last().first).toEpochDay() - d0).coerceAtLeast(1f)
            Spacer(Modifier.height(8.dp))
            WeightTrendLine(pts.map { (Dates.parse(it.first).toEpochDay() - d0) / span }, pts.map { it.second }, accentColor, "measure-$key", Modifier.fillMaxWidth().height(90.dp))
        } else Text("Add another entry to see the trend.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MeasurementSheet(pvm: PlatformViewModel, start: BodyMeasurement, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(start.date) }
    val text = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { start.values.forEach { (k, v) -> put(k, num1(v)) } } }
    var note by remember { mutableStateOf(start.note) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    fun parsed(): Map<String, Double>? {
        val out = mutableMapOf<String, Double>()
        for ((k, label, _) in BodyMeasurement.FIELDS) {
            val raw = text[k]?.trim()?.replace(',', '.').orEmpty()
            if (raw.isEmpty()) continue
            val v = raw.toDoubleOrNull()
            if (v == null || v !in BodyMeasurement.range(k)) { err = "$label looks off. Check the number."; return null }
            out[k] = v
        }
        if (out.isEmpty()) { err = "Enter at least one measurement."; return null }
        return out
    }
    BottomSheet(
        title = if (start.id == null) "Add measurements" else "Edit measurements",
        subtitle = "Leave blank what you didn't measure", onDismiss = onDismiss,
        primary = if (busy) "Saving…" else "Save", primaryEnabled = !busy,
        onPrimary = {
            err = null
            val vals = parsed() ?: return@BottomSheet
            busy = true
            scope.launch {
                val ok = pvm.saveMeasurement(start.copy(date = date, values = vals, note = note.trim()))
                busy = false
                if (ok) onDismiss() else err = pvm.error ?: "Couldn't save. Try again."
            }
        },
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { pickDate(ctx, date) { date = it } }, verticalAlignment = Alignment.CenterVertically) {
                Text("Date", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                Text(Dates.relative(date), fontSize = 15.sp, color = p.muted)
            }
            Hair()
            BodyMeasurement.FIELDS.forEach { (k, label, unit) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
                    com.sohum.bandlog.ui.log.NumberField(text[k].orEmpty(), { v -> text[k] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, unit)
                }
            }
            Hair()
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                com.sohum.bandlog.ui.log.PlainField(note, { note = it.take(200) }, "Note (optional)")
            }
            ErrorNote(err)
            if (start.id != null) Text(
                "Delete this entry", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.red,
                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onDelete).padding(vertical = 12.dp),
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
