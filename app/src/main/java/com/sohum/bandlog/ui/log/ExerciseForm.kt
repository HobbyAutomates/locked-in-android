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
import com.sohum.bandlog.data.ExerciseEntry
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

/** v2.8 quick picks per Log activity type (same labels and METs as the web app). */
private val KIND_QUICK: Map<String, List<Pair<String, Picked>>> = mapOf(
    "cardio" to listOf(
        "Run" to Picked("12150", Burn.RUN_CODE, "Running", Burn.RUN_MET), "Walk" to Picked("LI-17190", "LI-17190", "Walking", 3.5),
        "Cycle" to Picked("01015", "01015", "Cycling", 7.5), "Swim" to Picked("swim", null, "Swimming", 6.0),
        "Skipping" to Picked("LI-15551", "LI-15551", "Jump rope", 11.0), "Stairs" to Picked("LI-17133", "LI-17133", "Stair climbing", 8.8),
        "Elliptical" to Picked("elliptical", null, "Elliptical trainer", 5.0), "Rowing" to Picked("rowing", null, "Rowing machine", 7.0),
        "HIIT" to Picked("hiit", null, "HIIT / circuit", 8.0), "Dance" to Picked("dance", null, "Dancing", 5.0),
    ),
    "sport" to listOf(
        "Cricket" to Picked("LI-15150", "LI-15150", "Cricket", 4.8), "Badminton" to Picked("LI-15030", "LI-15030", "Badminton", 5.5),
        "Football" to Picked("football", null, "Football", 7.0), "Tennis" to Picked("tennis", null, "Tennis", 7.3),
        "Basketball" to Picked("basketball", null, "Basketball", 6.5), "Table tennis" to Picked("table-tennis", null, "Table tennis", 4.0),
        "Volleyball" to Picked("volleyball", null, "Volleyball", 4.0), "Kabaddi" to Picked("kabaddi", null, "Kabaddi", 6.0),
        "Squash" to Picked("squash", null, "Squash", 7.3),
    ),
    "yoga" to listOf(
        "Yoga" to Picked("LI-02101", "LI-02101", "Yoga", 2.5), "Stretching" to Picked("stretch", null, "Stretching", 2.3),
        "Pilates" to Picked("pilates", null, "Pilates", 3.0), "Surya namaskar" to Picked("surya", null, "Surya namaskar", 3.8),
    ),
    "other" to listOf(
        "Walk" to Picked("LI-17190", "LI-17190", "Walking", 3.5), "Run" to Picked("12150", Burn.RUN_CODE, "Running", Burn.RUN_MET),
        "Cycle" to Picked("01015", "01015", "Cycling", 7.5), "Swim" to Picked("swim", null, "Swimming", 6.0),
        "Cricket" to Picked("LI-15150", "LI-15150", "Cricket", 4.8), "Badminton" to Picked("LI-15030", "LI-15030", "Badminton", 5.5),
        "Football" to Picked("football", null, "Football", 7.0), "Skipping" to Picked("LI-15551", "LI-15551", "Jump rope", 11.0),
        "Stairs" to Picked("LI-17133", "LI-17133", "Stair climbing", 8.8), "Dance" to Picked("dance", null, "Dancing", 5.0),
    ),
)

private fun placeholder(kind: String): String = when (kind) {
    "cardio" -> "Search cardio or describe your run…"
    "sport" -> "Search a sport — cricket, football…"
    "yoga" -> "Search yoga, stretching, pilates…"
    else -> "Search or describe your activity…"
}

/** Distance only makes sense for things you cover ground in. */
private fun hasDistance(x: Picked): Boolean {
    val n = x.name.lowercase()
    return x.code == Burn.RUN_CODE || x.code == "LI-17190" || x.code == "01015" ||
        listOf("run", "jog", "walk", "hike", "cycl", "bicycl", "bike").any { it in n }
}

/** Entered by hand: a name and a number, nothing to re-price. */
private fun handEntered(e: ExerciseEntry) = e.source == "manual" && e.activityCode == null && e.intensityPct == null

private fun pctOf(e: ExerciseEntry): Int = e.intensityPct ?: when (e.intensity) { "low" -> 15; "high" -> 70; else -> Burn.DEFAULT_PCT }

private fun metOf(e: ExerciseEntry, weight: Double?): Double =
    Burn.metOf(e.kcal, weight, e.minutes, e.intensity, e.intensityPct).takeIf { it in 1.0..25.0 } ?: 4.0

private fun hhmmOf(iso: String?): String? = iso?.let {
    runCatching {
        java.time.OffsetDateTime.parse(it.replace(" ", "T")).atZoneSameInstant(Dates.ZONE).toLocalTime().let { t -> String.format(Locale.US, "%02d:%02d", t.hour, t.minute) }
    }.getOrNull()
}

/**
 * v2.8 activity form (Log activity → Cardio / Run, Sport, Yoga, Other, and the editor for a logged
 * run or activity): one bar to search the MET table or describe it ("Work it out"), a "Same as
 * last time" chip ahead of the quick picks, then duration chips, intensity chips and Save. "More
 * options" holds start time, distance, steps, notes and "Enter calories instead". Cardio / Other
 * save to exercise_log; Sport / Yoga save as a workout of that kind (streaks), the burn riding on it.
 */
@Composable
fun ExerciseForm(vm: AppViewModel, date: String, onClose: () -> Unit, kind: String? = null, editing: ExerciseEntry? = null) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val group = kind ?: "other"
    val asWorkout = editing == null && (group == "sport" || group == "yoga")
    val weight = vm.profile.weightKg
    val hand = editing != null && handEntered(editing)
    var query by rememberSaveable { mutableStateOf("") }
    val quick = KIND_QUICK[group] ?: KIND_QUICK.getValue("other")
    var picked by remember {
        mutableStateOf(
            when {
                editing != null && !hand -> Picked("edit", editing.activityCode, editing.name.replaceFirstChar { it.uppercase() }, metOf(editing, weight), editing.activityCode?.startsWith("LI-BAND") == true)
                editing == null && group == "yoga" -> quick.first().second
                else -> null
            },
        )
    }
    var pct by rememberSaveable { mutableIntStateOf(editing?.let { pctOf(it) } ?: Burn.DEFAULT_PCT) }
    var more by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableStateOf(editing?.minutes?.toString() ?: "30") }
    var startTime by rememberSaveable { mutableStateOf(hhmmOf(editing?.startedAt) ?: LocalTime.now(Dates.ZONE).let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }) }
    var distance by rememberSaveable { mutableStateOf(editing?.distanceKm?.let { com.sohum.bandlog.ui.today.fmt(it) } ?: "") }
    var steps by rememberSaveable { mutableStateOf(editing?.steps?.toString() ?: "") }
    var notes by rememberSaveable { mutableStateOf(editing?.takeIf { it.source != "workout" }?.note ?: "") }
    var results by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var described by remember { mutableStateOf<List<DescribedExercise>>(emptyList()) }
    var describing by remember { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(hand) }
    var manualName by rememberSaveable { mutableStateOf(if (hand) editing!!.name else "") }
    var manualKcal by rememberSaveable { mutableStateOf(if (hand) editing!!.kcal.roundToInt().toString() else "") }
    var sameApplied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val del = rememberEditorDelete(editing?.id) { editing?.let { vm.deleteExerciseLater(it.id) } }
    val q = query.trim()
    val sentence = q.split(Regex("\\s+")).count { it.isNotBlank() } >= 3

    // Other: the last few distinct activities you logged yourself (not runs — those are Cardio).
    val recent = remember(vm.exercises, weight) {
        ownExercises(vm.exercises).asSequence()
            .filter { !isCardioName(it.name, it.activityCode) }
            .distinctBy { it.name.trim().lowercase() }
            .take(8)
            .map { e -> Picked("r:" + e.name.trim().lowercase(), e.activityCode, e.name, metOf(e, weight), e.activityCode?.startsWith("LI-BAND") == true) to e.minutes }
            .toList()
    }

    // "Same as last time": Sport / Yoga → the last session of that kind (its burn); Cardio / Other → your last one of that type.
    val last: ExerciseEntry? = remember(vm.exercises, vm.workouts, group) {
        if (group == "sport" || group == "yoga") vm.lastWorkout(group)?.let { w -> vm.burnOf(w.id)?.let { b -> b.copy(name = w.exercises.ifBlank { b.name }, minutes = w.minutes ?: b.minutes) } }
        else ownExercises(vm.exercises).firstOrNull { (group == "cardio") == isCardioName(it.name, it.activityCode) }
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
        picked = x; manual = false; described = emptyList(); query = ""; focus.clearFocus(); error = null; sameApplied = false
        withMinutes?.takeIf { it > 0 }?.let { minutes = it.toString() }
    }

    fun sameAsLast() {
        val l = last ?: return
        if (handEntered(l)) { manual = true; picked = null; manualName = l.name; manualKcal = l.kcal.roundToInt().toString(); minutes = l.minutes.toString() }
        else { pick(Picked("last:" + l.name.lowercase(), l.activityCode, l.name.replaceFirstChar { it.uppercase() }, metOf(l, weight), l.activityCode?.startsWith("LI-BAND") == true), l.minutes); pct = pctOf(l) }
        sameApplied = true
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
                    keyboardActions = KeyboardActions(onSearch = { if (sentence && editing == null) describe() else focus.clearFocus() }),
                    textStyle = TextStyle(fontSize = 16.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (query.isEmpty()) Text(if (editing != null) "Change the activity…" else placeholder(group), fontSize = 16.sp, color = p.muted, maxLines = 1); inner() },
                )
                if (query.isNotEmpty()) Box(Modifier.size(44.dp).clickable { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(CrossIcon, "Clear", tint = p.muted, modifier = Modifier.size(13.dp))
                }
            }
            if (sentence && editing == null) {
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (editing == null && last != null) SameAsLastChip(sameApplied) { sameAsLast() }
                    quick.forEach { (label, x) -> ActivityChip(label, activityIcon(x.name, x.code), picked?.key == x.key) { pick(x) } }
                }
                if (recent.isNotEmpty() && group == "other" && editing == null) {
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
            }

            // Nothing picked yet: say what to do.
            if (picked == null && described.isEmpty() && !manual && q.length < 2) {
                Card(padding = 16.dp) {
                    Text("What did you do?", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Tap a pick above, search the activity table, or describe it — like “45 min cricket then 10 min skipping” — and tap Work it out.", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // The picked activity: duration chips, intensity chips and the live number.
            picked?.let { x ->
                if (!manual) Card(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(activityIcon(x.name, x.code), null, tint = p.ink, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(x.name.replaceFirstChar { it.uppercase() }, fontSize = 17.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, maxLines = 2, modifier = Modifier.weight(1f))
                        NumberField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "min")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DURATIONS.forEach { d -> Chip("$d min", minutes == "$d", { minutes = "$d" }, Modifier.weight(1f).widthIn(min = 52.dp)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Intensity", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    IntensityChips(pct) { pct = it }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FlameIcon, null, tint = p.orange, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${kcal.roundToInt()} kcal", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink)
                        Text("  ·  $mins min · ${if (x.bands) Burn.bandLevelFromPct(pct) + " band" else Burn.pctLabel(pct)}", fontSize = 13.sp, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (weight == null) Text("Using 60 kg — add your weight in Profile for an exact number.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
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
            }

            // Everything past the common case.
            if (described.isEmpty()) {
                val x = picked
                val hint = listOfNotNull(if (x != null && !manual) "Time" else null, if (x != null && !manual && hasDistance(x)) "distance" else null, if (x != null && !manual) "steps" else null, "notes", "calories").joinToString(", ")
                MoreOptions(more, { more = !more }, hint) {
                    if (x != null && !manual) {
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
                        if (hasDistance(x)) {
                            RowSpaceBetween {
                                Text("Distance", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                                NumberField(distance, { distance = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, "km", imeAction = ImeAction.Next)
                            }
                            Hair()
                        }
                        RowSpaceBetween {
                            Text("Steps", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                            NumberField(steps, { steps = it.filter(Char::isDigit).take(6) }, "steps", imeAction = ImeAction.Next)
                        }
                        Hair()
                    }
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
                    PillButton(if (manual) "Pick an activity instead" else "Enter calories instead", {
                        if (!manual && x != null && manualName.isBlank()) manualName = x.name
                        manual = !manual; sameApplied = false
                    }, height = 44.dp, bg = p.card2, fg = p.ink)
                }
            }

            if (editing != null) EditorDelete("Delete activity", del, enabled = !busy, onDelete = { vm.deleteExercise(editing.id).also { if (!it) error = vm.error } }, onDone = onClose)
        }

        // One Save, for whichever way in is showing.
        val manualK = manualKcal.toDoubleOrNull() ?: 0.0
        val x = picked
        val extras = x?.let {
            Api.ExerciseExtras(
                startedAt = "${editing?.date ?: date}T$startTime:00+05:30",
                intensityPct = pct,
                distanceKm = if (hasDistance(it)) distance.toDoubleOrNull()?.takeIf { d -> d > 0 } else null,
                steps = steps.toIntOrNull()?.takeIf { s -> s > 0 },
            )
        }
        val ready = !busy && !del.pending
        when {
            described.isNotEmpty() -> SaveBar("Save · ${described.sumOf { it.kcal }.roundToInt()} kcal", ready, busy) {
                if (asWorkout) {
                    val name = described.joinToString(", ") { it.name.replaceFirstChar { c -> c.uppercase() } }
                    save { vm.saveWorkout(null, date, emptyList(), "Medium", null, described.sumOf { it.minutes }.coerceAtLeast(1), name, notes.trim(), kind = group, burn = AppViewModel.WorkoutBurn(described.first().activityCode, name, "medium", described.sumOf { it.kcal })) }
                } else save { vm.saveDescribed(date, described) }
            }
            manual -> SaveBar("Save · ${manualK.roundToInt()} kcal", ready && manualK > 0, busy) {
                val name = manualName.trim().ifBlank { "Exercise" }
                when {
                    editing != null -> save { vm.updateExercise(editing.id, editing.date, null, name, mins.coerceAtLeast(1), "medium", manualK, notes.trim()) }
                    asWorkout -> save { vm.saveWorkout(null, date, emptyList(), "Medium", null, mins.coerceAtLeast(1), name, notes.trim(), kind = group, burn = AppViewModel.WorkoutBurn(null, name, "medium", manualK)) }
                    else -> save { vm.saveExercise(date, null, name, mins.coerceAtLeast(1), "medium", manualK, "manual", notes.trim()) }
                }
            }
            x != null -> SaveBar("Save · ${kcal.roundToInt()} kcal", ready && mins > 0 && kcal > 0, busy) {
                val code = if (x.bands) Burn.bandCode(Burn.bandLevelFromPct(pct)) else x.code
                val ex = extras ?: Api.ExerciseExtras()
                when {
                    editing != null -> save { vm.updateExercise(editing.id, editing.date, code, x.name, mins, Burn.intensityFromPct(pct), kcal, notes.trim(), ex) }
                    // Sport / Yoga: a workout of that kind (counts for streaks), its burn riding on it.
                    asWorkout -> save { vm.saveWorkout(null, date, emptyList(), "Medium", null, mins, x.name, notes.trim(), kind = group, burn = AppViewModel.WorkoutBurn(code, x.name, Burn.intensityFromPct(pct), kcal, ex)) }
                    else -> save { vm.saveExercise(date, code, x.name, mins, Burn.intensityFromPct(pct), kcal, "manual", notes.trim(), ex) }
                }
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
