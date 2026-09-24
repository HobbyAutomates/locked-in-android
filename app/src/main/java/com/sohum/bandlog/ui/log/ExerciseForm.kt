package com.sohum.bandlog.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Activity
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.DescribedExercise
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.HistoryIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.SearchIcon
import com.sohum.bandlog.ui.components.activityIcon
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Burn
import com.sohum.bandlog.util.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

private val DURATIONS = listOf(15, 30, 45, 60)

/** What's picked: a quick chip, a recent activity or a searched one. [bands] prices it as a band workout. */
private data class Picked(val key: String, val code: String?, val name: String, val met: Double, val bands: Boolean = false)

/** The everyday picks, straight from the MET table (bandlog.activities) so no lookup is needed. */
private val QUICK = listOf(
    "Bands" to Picked("bands", null, "Weight lifting / Bands", 5.0, bands = true),
    "Run" to Picked("12150", Burn.RUN_CODE, "Running", Burn.RUN_MET),
    "Walk" to Picked("LI-17190", "LI-17190", "Walking", 3.5),
    "Cycle" to Picked("01015", "01015", "Cycling", 7.5),
    "Cricket" to Picked("LI-15150", "LI-15150", "Cricket", 4.8),
    "Badminton" to Picked("LI-15030", "LI-15030", "Badminton", 5.5),
    "Football" to Picked("football", null, "Football", 7.0),
    "Swim" to Picked("swim", null, "Swimming", 6.0),
    "Yoga" to Picked("LI-02101", "LI-02101", "Yoga", 2.5),
    "Gym" to Picked("gym", null, "Gym workout", 5.0),
    "Skipping" to Picked("LI-15551", "LI-15551", "Skipping", 11.0),
    "Stairs" to Picked("LI-17133", "LI-17133", "Stair climbing", 8.8),
)

/** Distance only makes sense for things you cover ground in. */
private fun hasDistance(x: Picked): Boolean {
    val n = x.name.lowercase()
    return x.code == Burn.RUN_CODE || x.code == "LI-17190" || x.code == "01015" ||
        listOf("run", "jog", "walk", "hike", "cycl", "bicycl", "bike").any { it in n }
}

/**
 * v2.3 Log exercise: one search bar, a Recent row, icon chips for the everyday activities,
 * duration chips and a live kcal line. "More" (Google Fit style) opens start time, a Low → High
 * intensity slider, distance (run / walk / cycle), steps and notes. Three words or more in the
 * bar → "Work it out" (describe-exercise). A burn you already know goes in through
 * "Enter calories instead".
 */
@Composable
fun ExerciseForm(vm: AppViewModel, date: String, onClose: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var pct by rememberSaveable { mutableIntStateOf(Burn.DEFAULT_PCT) }
    var more by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var startTime by rememberSaveable { mutableStateOf(LocalTime.now(Dates.ZONE).let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }) }
    var distance by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var described by remember { mutableStateOf<List<DescribedExercise>>(emptyList()) }
    var describing by remember { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualKcal by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val weight = vm.profile.weightKg
    val q = query.trim()
    val sentence = q.split(Regex("\\s+")).count { it.isNotBlank() } >= 3

    // Last 5 distinct activities you logged yourself (band-workout auto rows and Health Connect left out).
    val recent = remember(vm.exercises, weight) {
        vm.exercises.asSequence()
            .filter { (it.source == "manual" || it.source == "describe") && it.name.isNotBlank() }
            .sortedByDescending { it.createdAt }
            .distinctBy { it.name.trim().lowercase() }
            .take(5)
            .map { e ->
                val bands = e.activityCode?.startsWith("LI-BAND") == true
                val met = Burn.metOf(e.kcal, weight, e.minutes, e.intensity, e.intensityPct).takeIf { it in 1.0..25.0 } ?: 4.0
                Picked("r:" + e.name.trim().lowercase(), e.activityCode, e.name, met, bands) to e.minutes
            }.toList()
    }

    LaunchedEffect(q) {
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(220)
        searching = true
        results = runCatching { Api.searchActivities(q) }.getOrDefault(emptyList())
        searching = false
    }

    val mins = minutes.toIntOrNull() ?: 0
    val kcal = picked?.let { if (it.bands) Burn.bandKcal(Burn.bandLevelFromPct(pct), weight, mins) else Burn.kcalPct(it.met, weight, mins, pct) } ?: 0.0

    fun pick(x: Picked, withMinutes: Int? = null) {
        picked = x; manual = false; described = emptyList(); query = ""; focus.clearFocus(); error = null
        withMinutes?.takeIf { it > 0 }?.let { minutes = it.toString() }
    }

    fun describe() {
        focus.clearFocus()
        scope.launch {
            describing = true; error = null
            try {
                described = Api.describeExercise(q)
                if (described.isEmpty()) error = "Couldn't find an activity in that — try naming the sport or movement."
                else { picked = null; manual = false; query = "" }
            } catch (e: Exception) { error = e.message } finally { describing = false }
        }
    }

    fun save(block: suspend () -> Boolean) {
        scope.launch {
            busy = true; error = null
            if (block()) onClose() else { error = vm.error; busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The bar.
            val shape = RoundedCornerShape(26.dp)
            Row(
                Modifier.fillMaxWidth().height(52.dp).shadow(10.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, shape).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SearchIcon, null, tint = p.muted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    query, { query = it.take(160) }, Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (sentence) describe() else focus.clearFocus() }),
                    textStyle = TextStyle(fontSize = 16.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search or describe your exercise…", fontSize = 16.sp, color = p.muted, maxLines = 1); inner() },
                )
                if (query.isNotEmpty()) Box(Modifier.size(44.dp).clickable { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(CrossIcon, "Clear", tint = p.muted, modifier = Modifier.size(13.dp))
                }
            }
            if (sentence) {
                PillButton(if (describing) "Working out the burn…" else "Work it out", { describe() }, enabled = !describing, height = 48.dp)
                if (describing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
            }
            ErrorNote(error)

            if (q.length >= 2 && !sentence) {
                if (results.isEmpty() && !searching) Card {
                    Text("Nothing matches “$q”.", fontSize = 14.sp, color = p.ink)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Enter calories instead", { manual = true; manualName = q; query = ""; picked = null }, height = 46.dp, bg = p.card2, fg = p.ink)
                }
                if (results.isNotEmpty()) Card(padding = 0.dp) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        results.forEachIndexed { i, a ->
                            if (i > 0) Hair()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { pick(Picked(a.code, a.code, a.label, a.met)) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(activityIcon(a.name, a.code), null, tint = p.ink, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.name.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                                    if (a.description.isNotBlank() && a.description != "general") Text(a.description, fontSize = 12.sp, color = p.muted, maxLines = 1)
                                }
                                Text("${Burn.kcal(a.met, weight, 30).roundToInt()} kcal / 30 min", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                            }
                        }
                    }
                }
            } else if (!sentence) {
                if (recent.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(HistoryIcon, null, tint = p.muted, modifier = Modifier.size(13.dp))
                        Text("  Recent", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { (x, m) ->
                            ActivityChip(x.name.replaceFirstChar { it.uppercase() }.take(26), activityIcon(x.name, x.code), picked?.key == x.key) { pick(x, m) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK.forEach { (label, x) -> ActivityChip(label, activityIcon(x.name, x.code), picked?.key == x.key) { pick(x) } }
                }
            }

            // Nothing picked yet: say what to do, and make the manual route a real button.
            if (picked == null && described.isEmpty() && !manual && q.length < 2) {
                Card(padding = 16.dp) {
                    Text("Pick an activity above or describe it (e.g. '20 min cricket')", fontSize = 14.sp, color = p.ink, lineHeight = 19.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("Enter calories instead", { manual = true; picked = null }, height = 44.dp, bg = p.card2, fg = p.ink)
                }
            }

            // The picked activity: duration chips + the live number + More.
            picked?.let { x ->
                Card(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(activityIcon(x.name, x.code), null, tint = p.ink, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(x.name.replaceFirstChar { it.uppercase() }, fontSize = 17.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, maxLines = 2)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DURATIONS.forEach { d -> Chip("$d", minutes == "$d", { minutes = "$d" }, Modifier.weight(1f).widthIn(min = 52.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    RowSpaceBetween {
                        Text("Minutes", fontSize = 14.sp, fontWeight = FontWeight(500), color = p.muted)
                        NumberField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "min")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FlameIcon, null, tint = p.orange, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${kcal.roundToInt()} kcal", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink)
                        Text("  ·  $mins min · ${Burn.pctShort(pct)}", fontSize = 13.sp, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                    if (weight == null) Text("Using 60 kg — add your weight in Profile for an exact number.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable().background(p.card2, CircleShape).clickable { more = !more },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (more) "Less" else "More · time, intensity, distance, notes", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1) }
                    if (more) {
                        Spacer(Modifier.height(6.dp))
                        RowSpaceBetween {
                            Text("Start time", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                            Box(
                                Modifier.background(p.card2, RoundedCornerShape(10.dp)).clickable {
                                    val t = runCatching { LocalTime.parse(startTime) }.getOrDefault(LocalTime.now(Dates.ZONE))
                                    runCatching {
                                        android.app.TimePickerDialog(ctx, { _, h, m -> startTime = String.format(Locale.US, "%02d:%02d", h, m) }, t.hour, t.minute, false).show()
                                    }
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(prettyTime(startTime), fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink) }
                        }
                        Hair()
                        Column(Modifier.padding(vertical = 12.dp)) {
                            RowSpaceBetween {
                                Text("Intensity", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                                Text(Burn.pctShort(pct), fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                            }
                            Slider(
                                value = pct.toFloat(), onValueChange = { pct = it.roundToInt() }, valueRange = 0f..100f,
                                colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.track),
                            )
                            RowSpaceBetween {
                                Text("Low", fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted)
                                Text("High", fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted)
                            }
                            Text(Burn.pctLabel(pct), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (hasDistance(x)) {
                            Hair()
                            RowSpaceBetween {
                                Text("Distance", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                                NumberField(distance, { distance = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, "km", imeAction = ImeAction.Next)
                            }
                        }
                        Hair()
                        RowSpaceBetween {
                            Text("Steps", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                            NumberField(steps, { steps = it.filter(Char::isDigit).take(6) }, "steps", imeAction = ImeAction.Next)
                        }
                        Hair()
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Text("Notes", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                            Spacer(Modifier.height(6.dp))
                            BasicTextField(
                                notes, { notes = it.take(200) }, Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (notes.isEmpty()) Text("How did it feel?", fontSize = 15.sp, color = p.muted); inner() },
                            )
                        }
                    }
                }
            }

            // Work it out → what was found.
            if (described.isNotEmpty()) Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Found · remove anything that's wrong", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    described.forEachIndexed { idx, it ->
                        if (idx > 0) Hair()
                        Row(Modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(activityIcon(it.name, it.activityCode), null, tint = p.ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(it.name.replaceFirstChar { c -> c.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                                Text("${Burn.intensityLabel(it.intensity)} · ${it.minutes} min", fontSize = 12.sp, color = p.muted)
                            }
                            Text("${it.kcal.roundToInt()} kcal", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                            Box(Modifier.size(44.dp).clickable { described = described.filterIndexed { i, _ -> i != idx } }, contentAlignment = Alignment.Center) {
                                Icon(CrossIcon, "Remove", tint = p.muted, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }

            // A number you already know.
            if (manual) Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("What did you do?", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        BasicTextField(
                            manualName, { manualName = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                            textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                            decorationBox = { inner -> if (manualName.isEmpty()) Text("Football, swimming, gym class…", fontSize = 15.sp, color = p.muted); inner() },
                        )
                    }
                    Hair()
                    RowSpaceBetween {
                        Text("Calories burned", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                        NumberField(manualKcal, { manualKcal = it.filter(Char::isDigit).take(4) }, "kcal", imeAction = ImeAction.Next)
                    }
                    Hair()
                    RowSpaceBetween {
                        Text("Duration", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                        NumberField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "min")
                    }
                }
            } else if (described.isEmpty() && picked != null) {
                PillButton("Enter calories instead", { manual = true; picked = null }, height = 44.dp, bg = p.card2, fg = p.ink)
            }
        }

        // One Save, for whichever way in is showing.
        val manualK = manualKcal.toDoubleOrNull() ?: 0.0
        when {
            described.isNotEmpty() -> SaveBar("Save · ${described.sumOf { it.kcal }.roundToInt()} kcal", !busy, busy) { save { vm.saveDescribed(date, described) } }
            manual -> SaveBar("Save · ${manualK.roundToInt()} kcal", !busy && manualK > 0, busy) {
                save { vm.saveExercise(date, null, manualName.trim().ifBlank { "Exercise" }, mins.coerceAtLeast(1), "medium", manualK, "manual") }
            }
            picked != null -> SaveBar("Save · ${kcal.roundToInt()} kcal", !busy && mins > 0 && kcal > 0, busy) {
                val x = picked!!
                val code = if (x.bands) Burn.bandCode(Burn.bandLevelFromPct(pct)) else x.code
                val extras = Api.ExerciseExtras(
                    startedAt = "${date}T$startTime:00+05:30",
                    intensityPct = pct,
                    distanceKm = if (hasDistance(x)) distance.toDoubleOrNull()?.takeIf { it > 0 } else null,
                    steps = steps.toIntOrNull()?.takeIf { it > 0 },
                )
                save { vm.saveExercise(date, code, x.name, mins, Burn.intensityFromPct(pct), kcal, "manual", notes.trim(), extras) }
            }
            else -> Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** "07:05" → "7:05 am". */
private fun prettyTime(hhmm: String): String = runCatching {
    val t = LocalTime.parse(hhmm)
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    String.format(Locale.US, "%d:%02d %s", h, t.minute, if (t.hour < 12) "am" else "pm")
}.getOrDefault(hhmm)

/** A pill with the activity's icon; black when selected. */
@Composable
private fun ActivityChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.height(44.dp).pressable().background(if (selected) p.btn else p.card2, CircleShape).clickable(onClick = onClick).padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) p.btnInk else p.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight(600) else FontWeight(500), color = if (selected) p.btnInk else p.ink, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SaveBar(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
        PillButton(if (busy) "Saving…" else label, onClick, enabled = enabled)
    }
}
