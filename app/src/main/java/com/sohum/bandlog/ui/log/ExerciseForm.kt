package com.sohum.bandlog.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Activity
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.DescribedExercise
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.DumbbellIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.IconTile
import com.sohum.bandlog.ui.components.KeypadIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.RunIcon
import com.sohum.bandlog.ui.components.TextLinesIcon
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Burn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DURATIONS = listOf(15, 30, 60, 90)

private data class IntensityOption(val key: String, val label: String, val sub: String)
private val INTENSITIES = listOf(
    IntensityOption("high", "High", "Training to failure, breathing heavily"),
    IntensityOption("medium", "Medium", "Breaking a sweat"),
    IntensityOption("low", "Low", "Not breaking a sweat"),
)

/**
 * Log exercise → calories burned, Cal AI-style. Four ways in (Run, Weight lifting / Bands,
 * Describe, Manual) plus a searchable activity list backed by the MET table. Run / Bands / an
 * activity go through the intensity + duration picker with the burn computed live.
 */
@Composable
fun ExerciseForm(vm: AppViewModel, date: String, onClose: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    // null (choose) | run | bands | activity | describe | manual
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var activity by remember { mutableStateOf<Activity?>(null) }
    var intensity by rememberSaveable { mutableStateOf("medium") }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var describeText by rememberSaveable { mutableStateOf("") }
    var described by remember { mutableStateOf<List<DescribedExercise>>(emptyList()) }
    var describing by remember { mutableStateOf(false) }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualKcal by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val weight = vm.profile.weightKg

    // Debounced search; a blank query shows the popular set so the list is never empty.
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(220)
        searching = true
        results = runCatching { Api.searchActivities(query) }.getOrDefault(emptyList())
        searching = false
    }
    BackHandler(enabled = mode != null) { mode = null; error = null }

    val mins = minutes.toIntOrNull() ?: 0
    val kcal = when (mode) {
        "run" -> Burn.kcal(Burn.RUN_MET, weight, mins, intensity)
        "bands" -> Burn.bandKcal(Burn.bandLevel(intensity), weight, mins)
        "activity" -> activity?.let { Burn.kcal(it.met, weight, mins, intensity) } ?: 0.0
        else -> 0.0
    }
    val title = when (mode) { "run" -> "Run"; "bands" -> "Weight lifting / Bands"; "activity" -> activity?.label ?: "Activity"; else -> "" }
    val icon: ImageVector = when (mode) { "run" -> RunIcon; "bands" -> DumbbellIcon; else -> FlameIcon }

    fun saveOne(code: String?, name: String, m: Int, i: String, k: Double, source: String) {
        scope.launch {
            busy = true; error = null
            if (vm.saveExercise(date, code, name, m, i, k, source)) onClose() else { error = vm.error; busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (mode) {
                null -> {
                    Rise(0) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OptionCard(Modifier.weight(1f), RunIcon, "Run", "Running, jogging, sprinting") { intensity = "medium"; mode = "run" }
                                OptionCard(Modifier.weight(1f), DumbbellIcon, "Weight lifting / Bands", "Machines, free weights, bands") { intensity = "medium"; mode = "bands" }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OptionCard(Modifier.weight(1f), TextLinesIcon, "Describe", "Write your workout in text") { mode = "describe" }
                                OptionCard(Modifier.weight(1f), KeypadIcon, "Manual", "Enter exactly how many calories you burned") { mode = "manual" }
                            }
                        }
                    }
                    Rise(1) {
                        Card(padding = 0.dp) {
                            Column(Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp)) {
                                Text("Or pick an activity", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BasicTextField(
                                        query, { query = it.take(40) }, Modifier.weight(1f), singleLine = true,
                                        textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                        decorationBox = { inner -> if (query.isEmpty()) Text("Cricket, badminton, walking, yoga, stairs…", fontSize = 15.sp, color = p.muted); inner() },
                                    )
                                    if (searching) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = p.muted)
                                }
                            }
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                if (results.isEmpty() && !searching) Text("Nothing matches “$query” — try Describe instead.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(vertical = 14.dp))
                                results.forEachIndexed { i, a ->
                                    if (i > 0) Hair()
                                    Row(
                                        Modifier.fillMaxWidth().clickable { activity = a; intensity = "medium"; mode = "activity" }.padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(a.name.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                                            if (a.description.isNotBlank() && a.description != "general") Text(a.description, fontSize = 12.sp, color = p.muted, maxLines = 1)
                                        }
                                        Text("${Burn.kcal(a.met, weight, 30).toInt()} kcal / 30 min", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                                    }
                                }
                            }
                        }
                    }
                }
                "run", "bands", "activity" -> {
                    Rise(0) {
                        Card(padding = 14.dp, onClick = { mode = null }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(icon, p.ink, p.card2)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(title.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 2)
                                    Text("Tap to change", fontSize = 12.sp, color = p.muted)
                                }
                            }
                        }
                    }
                    Rise(1) {
                        Card(padding = 0.dp) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                Text("Intensity", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                                INTENSITIES.forEachIndexed { i, o ->
                                    if (i > 0) Hair()
                                    val sel = intensity == o.key
                                    Row(Modifier.fillMaxWidth().clickable { intensity = o.key }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(22.dp).let { m -> if (sel) m.background(p.btn, CircleShape) else m.border(1.5.dp, p.hair, CircleShape) },
                                            contentAlignment = Alignment.Center,
                                        ) { if (sel) Box(Modifier.size(8.dp).background(p.btnInk, CircleShape)) }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(o.label, fontSize = 15.sp, fontWeight = FontWeight(if (sel) 700 else 500), color = p.ink)
                                            Text(o.sub, fontSize = 12.sp, color = p.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Rise(2) {
                        Card {
                            RowSpaceBetween {
                                Text("Duration", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                                NumberField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "min")
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DURATIONS.forEach { d -> Chip("$d min", minutes == "$d", { minutes = "$d" }, Modifier.weight(1f)) }
                            }
                        }
                    }
                    Rise(3) { BurnPreview(kcal, weight, minutes = mins) }
                    ErrorNote(error)
                }
                "describe" -> {
                    Rise(0) {
                        Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(TextLinesIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp)) }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Describe your workout", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                                    Text("Activities, how long, how hard", fontSize = 12.sp, color = p.muted)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(14.dp)).padding(14.dp)) {
                                BasicTextField(
                                    describeText, { describeText = it }, Modifier.fillMaxWidth().height(64.dp),
                                    textStyle = TextStyle(fontSize = 15.sp, color = p.ink, lineHeight = 22.sp), cursorBrush = SolidColor(p.ink),
                                    decorationBox = { inner -> if (describeText.isEmpty()) Text("Played badminton for an hour then walked home 20 min", fontSize = 15.sp, color = p.muted); inner() },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            PillButton(if (describing) "Working out the burn…" else "Work out the burn", enabled = describeText.isNotBlank() && !describing, height = 48.dp, onClick = {
                                scope.launch {
                                    describing = true; error = null
                                    try {
                                        described = Api.describeExercise(describeText.trim())
                                        if (described.isEmpty()) error = "Couldn't find an activity in that — try naming the sport or movement."
                                    } catch (e: Exception) { error = e.message } finally { describing = false }
                                }
                            })
                            if (describing) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track) }
                        }
                    }
                    ErrorNote(error)
                    if (described.isNotEmpty()) {
                        Rise(1) { Text("Review · remove anything that's wrong", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(horizontal = 4.dp)) }
                        Rise(2) {
                            Card(padding = 0.dp) {
                                Column(Modifier.padding(horizontal = 16.dp)) {
                                    described.forEachIndexed { idx, it ->
                                        if (idx > 0) Hair()
                                        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(it.name.replaceFirstChar { c -> c.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                                                Text("Intensity: ${Burn.intensityLabel(it.intensity)} · ${it.minutes} mins", fontSize = 12.sp, color = p.muted)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(FlameIcon, null, tint = p.ink, modifier = Modifier.size(14.dp))
                                                Text(" ${it.kcal.toInt()}", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                                            }
                                            IconButton(onClick = { described = described.filterIndexed { i, _ -> i != idx } }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Remove", tint = p.muted) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "manual" -> {
                    Rise(0) {
                        Card(padding = 0.dp) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                Column(Modifier.padding(vertical = 12.dp)) {
                                    Text("What did you do?", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                                    Spacer(Modifier.height(6.dp))
                                    BasicTextField(
                                        manualName, { manualName = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
                                        textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                        decorationBox = { inner -> if (manualName.isEmpty()) Text("Football, swimming, gym class…", fontSize = 15.sp, color = p.muted); inner() },
                                    )
                                }
                                Hair()
                                RowSpaceBetween {
                                    Text("Calories burned", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                                    NumberField(manualKcal, { manualKcal = it.filter(Char::isDigit).take(4) }, "kcal")
                                }
                                Hair()
                                RowSpaceBetween {
                                    Text("Duration", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                                    NumberField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "min")
                                }
                            }
                        }
                    }
                    ErrorNote(error)
                }
            }
        }
        // Bottom action, per mode.
        when (mode) {
            "run", "bands", "activity" -> Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
                PillButton(if (busy) "Saving…" else "Save · ${kcal.toInt()} kcal", enabled = !busy && mins > 0 && kcal > 0, onClick = {
                    val code = when (mode) { "run" -> Burn.RUN_CODE; "bands" -> Burn.bandCode(Burn.bandLevel(intensity)); else -> activity?.code }
                    val name = when (mode) { "run" -> "Running"; "bands" -> "Weight lifting / Bands"; else -> activity?.label ?: "Activity" }
                    saveOne(code, name, mins, intensity, kcal, "manual")
                })
            }
            "describe" -> if (described.isNotEmpty()) Row(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${described.sumOf { it.kcal }.toInt()} kcal", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    Text("${described.sumOf { it.minutes }} mins", fontSize = 12.sp, color = p.muted)
                }
                Spacer(Modifier.width(12.dp))
                PillButton(if (busy) "Saving…" else if (described.size == 1) "Save" else "Save ${described.size} activities", enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        busy = true; error = null
                        if (vm.saveDescribed(date, described)) onClose() else { error = vm.error; busy = false }
                    }
                })
            }
            "manual" -> Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
                val k = manualKcal.toDoubleOrNull() ?: 0.0
                PillButton(if (busy) "Saving…" else "Save", enabled = !busy && k > 0, onClick = {
                    saveOne(null, manualName.trim().ifBlank { "Exercise" }, mins.coerceAtLeast(1), "medium", k, "manual")
                })
            }
            else -> {}
        }
    }
}

/** One of the four Cal AI-style entry cards: icon tile, title, one-line hint. */
@Composable
private fun OptionCard(modifier: Modifier, icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val p = palette
    Card(modifier, padding = 14.dp, onClick = onClick) {
        Box(Modifier.size(40.dp).background(p.card2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = p.ink, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, lineHeight = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 12.sp, color = p.muted, lineHeight = 15.sp)
    }
}

/** The live number: big kcal, then the weight and minutes it came from. */
@Composable
private fun BurnPreview(kcal: Double, weightKg: Double?, minutes: Int) {
    val p = palette
    Card(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FlameIcon, null, tint = p.orange, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${kcal.toInt()}", fontSize = 36.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink, lineHeight = 36.sp)
                Text("calories burned · $minutes min at ${fmt(weightKg ?: Burn.DEFAULT_WEIGHT_KG)} kg", fontSize = 12.sp, color = p.muted)
            }
        }
        if (weightKg == null) { Spacer(Modifier.height(8.dp)); Text("Add your weight in Profile → Personal details for an exact number (using 60 kg for now).", fontSize = 12.sp, color = p.muted) }
    }
}
