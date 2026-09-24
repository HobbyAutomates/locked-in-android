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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Lift
import com.sohum.bandlog.data.LiftSet
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.HistoryIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.components.workoutKindIcon
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Lifts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One tile of the Workout type picker. */
private data class Kind(val key: String, val label: String, val sub: String)

private val KIND_TILES = listOf(
    Kind("gym", "Gym", "Weights · sets × reps"),
    Kind("bodyweight", "Bodyweight", "Push-ups, pull-ups, plank"),
    Kind(Workout.BANDS, "Bands", "Resistance bands"),
    Kind("cardio", "Cardio", "Run, walk, cycle, swim"),
    Kind("sport", "Sport", "Cricket, football, badminton"),
    Kind("yoga", "Yoga / Stretch", "Yoga, stretching"),
)

/**
 * v2.5 the Workout segment, general first (Google Fit / Cal AI): a type picker — Gym · Bodyweight ·
 * Bands · Cardio · Sport · Yoga — then the form for that kind. Bands opens the band form unchanged
 * ([bandForm]); Gym / Bodyweight get the sets grid ([LiftForm]); Cardio / Sport / Yoga open the
 * Exercise form filtered to that kind and save as a workout of that kind. An existing workout
 * opens straight into its own kind's form.
 */
@Composable
fun WorkoutTab(
    vm: AppViewModel, existing: Workout?, initialDate: String, onClose: () -> Unit,
    /** Start on a kind / with lifts already listed (layout screenshots; not used by the app itself). */
    initialKind: String? = null, prefill: List<Lift>? = null,
    bandForm: @Composable () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: initialKind) }
    val k = kind
    if (k == null) KindPicker(vm) { kind = it }
    else Column(Modifier.fillMaxSize()) {
        if (existing == null) KindHeader(k) { kind = null }
        when {
            k == Workout.BANDS -> bandForm()
            k == "gym" || k == "bodyweight" -> LiftForm(vm, k, existing, initialDate, onClose, prefill)
            existing != null -> KindEditForm(vm, existing, onClose)
            else -> ExerciseForm(vm, initialDate, onClose, kind = k)
        }
    }
}

@Composable
private fun KindPicker(vm: AppViewModel, onPick: (String) -> Unit) {
    val p = palette
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 6.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("What did you do?", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink)
        KIND_TILES.chunked(2).forEachIndexed { i, row ->
            Rise(i) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t -> KindTile(t, vm.lastWorkout(t.key), Modifier.weight(1f)) { onPick(t.key) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Text("Streaks count every kind of workout.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun KindTile(t: Kind, last: Workout?, modifier: Modifier, onClick: () -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.heightIn(min = 112.dp).shadow(10.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).pressable().background(p.card, shape).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(40.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(workoutKindIcon(t.key), null, tint = p.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(t.label, fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            last?.let { "Last: " + it.date.let { d -> com.sohum.bandlog.util.Dates.short(d) } } ?: t.sub,
            fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
        )
    }
}

/** "‹  Gym" — back to the type picker. */
@Composable
private fun KindHeader(kind: String, onChange: () -> Unit) {
    val p = palette
    Row(Modifier.padding(horizontal = 16.dp).heightIn(min = 44.dp).clickable(onClick = onChange).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", fontSize = 20.sp, fontWeight = FontWeight(600), color = p.muted)
        Spacer(Modifier.width(8.dp))
        Icon(workoutKindIcon(kind), null, tint = p.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(Workout.kindLabel(kind), fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
        Text("  · change", fontSize = 13.sp, color = p.muted, maxLines = 1)
    }
}

// ---- Gym / Bodyweight: exercises with a weight × reps grid ----

private data class SetDraft(val kg: String = "", val reps: String = "")
private data class LiftDraft(val name: String, val sets: List<SetDraft>)

private fun numStr(d: Double?): String = d?.let { fmt(it) } ?: ""

private fun Lift.draft(): LiftDraft = LiftDraft(name, sets.map { SetDraft(numStr(it.kg), it.reps?.toString() ?: "") }.ifEmpty { List(3) { SetDraft() } })

/**
 * v2.5 Gym / Bodyweight session: "Add exercise" (search over ~70 common moves), each with a compact
 * weight × reps grid — 3 rows to start, "+ set", and last session's numbers as ghost hints (tap a
 * set's number to copy them in). Duration runs from opening the form to Save unless you type one.
 */
@Composable
private fun LiftForm(vm: AppViewModel, kind: String, existing: Workout?, initialDate: String, onClose: () -> Unit, prefill: List<Lift>? = null) {
    val p = palette
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bodyweight = kind == "bodyweight"
    val date = existing?.date ?: initialDate
    var lifts by remember { mutableStateOf<List<LiftDraft>>((existing?.lifts ?: prefill)?.map { it.draft() } ?: emptyList()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val startedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val autoMin = ((now - startedAt) / 60_000L).toInt().coerceAtLeast(1)
    var minutesText by remember { mutableStateOf(existing?.minutes?.toString() ?: "") }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val last = remember(vm.workouts) { vm.lastWorkout(kind, except = existing?.id) }

    fun ghost(name: String): Lift? = vm.lastLift(name, except = existing?.id)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing == null && last != null && last.lifts.isNotEmpty()) {
                val applied = lifts.map { it.name } == last.lifts.map { it.name }
                SameAsLast(applied, last.summary) { lifts = last.lifts.map { it.draft() }; error = null }
            }
            lifts.forEachIndexed { i, l ->
                LiftCard(
                    draft = l, bodyweight = bodyweight && Lifts.find(l.name)?.bodyweight != false, ghost = ghost(l.name),
                    onChange = { d -> lifts = lifts.toMutableList().also { it[i] = d } },
                    onRemove = { lifts = lifts.filterIndexed { j, _ -> j != i } },
                )
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable().background(if (lifts.isEmpty()) p.btn else p.card2, CircleShape).clickable { adding = true },
                contentAlignment = Alignment.Center,
            ) { Text("+ Add exercise", fontSize = 15.sp, fontWeight = FontWeight(700), color = if (lifts.isEmpty()) p.btnInk else p.ink) }
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    RowSpaceBetween {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text("Duration", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                            if (existing == null && minutesText.isEmpty()) Text("Timing from when you opened this · $autoMin min", fontSize = 11.sp, color = p.muted, lineHeight = 14.sp)
                        }
                        NumberField(minutesText.ifEmpty { if (existing == null) "$autoMin" else "" }, { minutesText = it.filter(Char::isDigit).take(3) }, "min")
                    }
                    Hair()
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Notes", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        PlainField(notes, { notes = it.take(300) }, "How did it feel?")
                    }
                }
            }
            ErrorNote(error)
            if (existing != null) {
                TextButton(onClick = { scope.launch { busy = true; if (vm.deleteWorkout(existing.id)) onClose() else { error = vm.error; busy = false } } }, enabled = !busy) { Text("Delete workout", color = p.red) }
            }
        }
        Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
            val n = lifts.size
            PillButton(if (busy) "Saving…" else if (n == 0) "Save workout" else "Save · $n exercise${if (n == 1) "" else "s"}", enabled = !busy, onClick = {
                // Blank fields take last session's number (the faint ghost hint): an untouched set
                // saves as last time's; a set with no history and nothing typed is dropped.
                val out = lifts.mapNotNull { d ->
                    val g = ghost(d.name)
                    val sets = d.sets.mapIndexedNotNull { si, s ->
                        val gs = g?.sets?.getOrNull(si)
                        if (s.kg.isBlank() && s.reps.isBlank()) return@mapIndexedNotNull gs?.let { LiftSet(it.kg, it.reps) }
                        LiftSet(kg = s.kg.toDoubleOrNull() ?: gs?.kg?.takeIf { s.kg.isBlank() && s.reps.isNotBlank() }, reps = s.reps.toIntOrNull() ?: gs?.reps?.takeIf { s.reps.isBlank() })
                    }
                    d.name.trim().takeIf { it.isNotEmpty() }?.let { Lift(it, sets) }
                }
                if (out.isEmpty()) { error = "Add at least one exercise"; return@PillButton }
                val mins = (minutesText.toIntOrNull() ?: if (existing == null) autoMin else existing.minutes ?: autoMin).coerceAtLeast(1)
                scope.launch {
                    busy = true; error = null
                    val muscles = Lifts.musclesFor(out.map { it.name })
                    val ok = vm.saveWorkout(existing?.id, date, muscles, "Medium", null, mins, "", notes.trim(), kind = kind, lifts = out)
                    if (ok) {
                        if (existing == null) vm.pushSessionToHealth(ctx, muscles, mins, date, title = Workout.kindLabel(kind) + ": " + out.joinToString(", ") { it.name })
                        onClose()
                    } else { error = vm.error; busy = false }
                }
            })
        }
    }

    if (adding) ExercisePicker(vm, bodyweight, onDismiss = { adding = false }) { name ->
        adding = false
        val g = ghost(name)
        val rows = (g?.sets?.size ?: 3).coerceIn(1, 6)
        lifts = lifts + LiftDraft(name, List(rows) { SetDraft() })
    }
}

/** The black "Same as last time" pill (grey once applied). */
@Composable
private fun SameAsLast(applied: Boolean, summary: String, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable().background(if (applied) p.card2 else p.btn, CircleShape)
            .clickable(enabled = !applied, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HistoryIcon, null, tint = if (applied) p.muted else p.btnInk, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (applied) "Filled in from last time" else "Same as last time", fontSize = 14.sp, fontWeight = FontWeight(700), color = if (applied) p.muted else p.btnInk, maxLines = 1)
        Text(
            "  $summary", fontSize = 13.sp, color = (if (applied) p.muted else p.btnInk).copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiftCard(draft: LiftDraft, bodyweight: Boolean, ghost: Lift?, onChange: (LiftDraft) -> Unit, onRemove: () -> Unit) {
    val p = palette
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(draft.name, fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ghost?.let { g ->
                    Text(
                        "Last: " + g.sets.joinToString(", ") { s -> listOfNotNull(s.kg?.let { fmt(it) }, s.reps?.toString()).joinToString("×") },
                        fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.size(44.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(CrossIcon, "Remove ${draft.name}", tint = p.muted, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        draft.sets.forEachIndexed { i, s ->
            val g = ghost?.sets?.getOrNull(i)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The set number: tap to copy last session's numbers into this set.
                Box(
                    Modifier.widthIn(min = 44.dp).height(44.dp).background(p.card2, RoundedCornerShape(12.dp))
                        .clickable(enabled = g != null) { onChange(draft.copy(sets = draft.sets.toMutableList().also { it[i] = SetDraft(numStr(g?.kg), g?.reps?.toString() ?: "") })) },
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1) }
                if (!bodyweight) {
                    SetField(s.kg, numStr(g?.kg), "kg", Modifier.weight(1f), decimal = true) { v -> onChange(draft.copy(sets = draft.sets.toMutableList().also { it[i] = s.copy(kg = v) })) }
                    Text("×", fontSize = 15.sp, color = p.muted)
                }
                SetField(s.reps, g?.reps?.toString() ?: "", "reps", Modifier.weight(1f), decimal = false) { v -> onChange(draft.copy(sets = draft.sets.toMutableList().also { it[i] = s.copy(reps = v) })) }
                Box(
                    Modifier.size(36.dp).clickable(enabled = draft.sets.size > 1) { onChange(draft.copy(sets = draft.sets.filterIndexed { j, _ -> j != i })) },
                    contentAlignment = Alignment.Center,
                ) { if (draft.sets.size > 1) Icon(CrossIcon, "Remove set ${i + 1}", tint = p.muted, modifier = Modifier.size(10.dp)) }
            }
        }
        Box(
            Modifier.heightIn(min = 44.dp).clickable { onChange(draft.copy(sets = draft.sets + (draft.sets.lastOrNull()?.let { SetDraft(it.kg, it.reps) } ?: SetDraft()))) }.padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text("+ set", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink) }
    }
}

/** A grey number box with a unit suffix inside; [hint] is last session's value, shown faint. */
@Composable
private fun SetField(value: String, hint: String, unit: String, modifier: Modifier, decimal: Boolean, onChange: (String) -> Unit) {
    val p = palette
    val focus = LocalFocusManager.current
    Row(modifier.height(44.dp).background(p.card2, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value, { v -> onChange(v.filter { c -> c.isDigit() || (decimal && c == '.') }.take(5)) }, Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }),
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(p.ink),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(hint.ifEmpty { "–" }, fontSize = 16.sp, fontWeight = FontWeight(600), color = p.muted.copy(alpha = 0.55f), maxLines = 1)
                    inner()
                }
            },
        )
        Text(" $unit", fontSize = 12.sp, color = p.muted, maxLines = 1)
    }
}

/** "Add exercise": search the built-in list (your recent ones first), or add any name you type. */
@Composable
private fun ExercisePicker(vm: AppViewModel, bodyweight: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val p = palette
    var q by remember { mutableStateOf("") }
    val recent = remember(vm.workouts) {
        vm.workouts.sortedByDescending { it.date }.flatMap { w -> w.lifts.map { it.name } }.distinctBy { it.lowercase() }.take(8)
    }
    val list = remember(q, recent) { Lifts.search(q, bodyweight, recent) }
    BottomSheet(title = "Add exercise", subtitle = if (q.isBlank() && recent.isNotEmpty()) "Your recent ones first" else null, onDismiss = onDismiss) {
        SheetField(q, { q = it.take(40) }, "Search bench press, squat, plank…")
        Spacer(Modifier.height(6.dp))
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            val typed = q.trim()
            if (typed.length >= 2 && list.none { it.name.equals(typed, ignoreCase = true) }) {
                PickRow("Add “${typed.replaceFirstChar { it.uppercase() }}”", "Your own exercise", null) { onPick(typed.replaceFirstChar { it.uppercase() }) }
                Hair()
            }
            list.forEachIndexed { i, e ->
                if (i > 0) Hair()
                PickRow(e.name, e.muscles.joinToString(" · ") + if (e.bodyweight) " · bodyweight" else "", if (recent.any { it.equals(e.name, ignoreCase = true) }) HistoryIcon else null) { onPick(e.name) }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, sub: String, icon: ImageVector?, onClick: () -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (icon != null) Icon(icon, null, tint = p.muted, modifier = Modifier.size(14.dp))
    }
}

// ---- Cardio / Sport / Yoga: editing a saved one ----

/** A saved cardio / sport / yoga session: minutes and notes (the burn follows the minutes), or delete. */
@Composable
private fun KindEditForm(vm: AppViewModel, existing: Workout, onClose: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val burn = remember(vm.exercises) { vm.burnOf(existing.id) }
    var minutes by remember { mutableStateOf(existing.minutes?.toString() ?: burn?.minutes?.toString() ?: "30") }
    var notes by remember { mutableStateOf(existing.notes) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val mins = minutes.toIntOrNull() ?: 0
    val kcal = burn?.let { b -> if (b.minutes > 0) b.kcal / b.minutes * mins else b.kcal } ?: 0.0
    val name = existing.exercises.ifBlank { burn?.name ?: Workout.kindLabel(existing.kind) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(workoutKindIcon(existing.kind, name), null, tint = p.ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(name.replaceFirstChar { it.uppercase() }, fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Hair()
                    RowSpaceBetween {
                        Text("Duration", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                        NumberField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, "min")
                    }
                    if (burn != null) {
                        Hair()
                        RowSpaceBetween {
                            Text("Burned", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                            Text("${kcal.roundToInt()} kcal", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                        }
                    }
                    Hair()
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Notes", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        PlainField(notes, { notes = it.take(300) }, "How did it feel?")
                    }
                }
            }
            ErrorNote(error)
            TextButton(onClick = { scope.launch { busy = true; if (vm.deleteWorkout(existing.id)) onClose() else { error = vm.error; busy = false } } }, enabled = !busy) { Text("Delete workout", color = p.red) }
        }
        Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
            PillButton(if (busy) "Saving…" else "Save", enabled = !busy && mins > 0, onClick = {
                scope.launch {
                    busy = true; error = null
                    val b = burn?.let { AppViewModel.WorkoutBurn(it.activityCode, it.name, it.intensity, (kcal * 10).roundToInt() / 10.0) }
                    val ok = vm.saveWorkout(existing.id, existing.date, existing.muscles, existing.bandLevel, null, mins, existing.exercises, notes.trim(), kind = existing.kind, burn = b)
                    if (ok) onClose() else { error = vm.error; busy = false }
                }
            })
        }
    }
}
