package com.sohum.bandlog.ui.log

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * v2.8 Log activity (replaces the Workout / Exercise split and the separate type screen): Gym ·
 * Bodyweight · Bands · Cardio / Run · Sport · Yoga · Other, picked inline in one chip row, then the
 * form for that type. Gym, Bodyweight, Bands, Sport and Yoga save to `workouts` (streaks); Cardio /
 * Run and Other save to `exercise_log`. An existing workout opens straight into its own form
 * ([existing]); a logged run / activity opens the activity form as its editor ([editingExercise]).
 */
@Composable
fun WorkoutTab(
    vm: AppViewModel, existing: Workout?, initialDate: String, onClose: () -> Unit,
    /** Start on a type / with lifts already listed (layout screenshots; not used by the app itself). */
    initialKind: String? = null, prefill: List<Lift>? = null,
    editingExercise: com.sohum.bandlog.data.ExerciseEntry? = null,
    bandForm: @Composable () -> Unit,
) {
    var kind by rememberSaveable {
        mutableStateOf(
            existing?.kind
                ?: editingExercise?.let { if (isCardioName(it.name, it.activityCode)) "cardio" else "other" }
                ?: initialKind ?: defaultActivityType(vm.workouts, vm.exercises),
        )
    }
    val k = kind
    val liftEdit = existing != null && (existing.kind == "gym" || existing.kind == "bodyweight" || existing.isBands)
    // An existing session can move between Gym / Bodyweight / Bands; nothing else switches type in an editor.
    val types = when {
        existing != null -> if (liftEdit) listOf("gym", "bodyweight", Workout.BANDS) else emptyList()
        editingExercise != null -> emptyList()
        else -> ACTIVITY_TYPES
    }
    Column(Modifier.fillMaxSize()) {
        if (types.isNotEmpty()) TypeChips(types, k) { kind = it }
        androidx.compose.runtime.key(k) {
            when {
                existing != null && !liftEdit -> KindEditForm(vm, existing, onClose)
                k == Workout.BANDS -> bandForm()
                k == "gym" || k == "bodyweight" -> LiftForm(vm, k, existing, initialDate, onClose, prefill)
                else -> ExerciseForm(vm, editingExercise?.date ?: initialDate, onClose, kind = k, editing = editingExercise)
            }
        }
    }
}

/** The seven types as one scrolling row of icon chips (black = picked). */
@Composable
private fun TypeChips(types: List<String>, selected: String, onPick: (String) -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        types.forEach { t ->
            val sel = t == selected
            Row(
                Modifier.height(44.dp).pressable().background(if (sel) p.btn else p.card2, CircleShape).clickable { onPick(t) }.padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (t == "other") com.sohum.bandlog.ui.components.FlameIcon else workoutKindIcon(t), null, tint = if (sel) p.btnInk else p.ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(activityTypeLabel(t), fontSize = 13.sp, fontWeight = if (sel) FontWeight(700) else FontWeight(500), color = if (sel) p.btnInk else p.ink, maxLines = 1, softWrap = false)
            }
        }
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
    var more by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: initialDate) }
    var copied by remember { mutableStateOf(false) }
    val del = rememberEditorDelete(existing?.id) { existing?.let { vm.deleteWorkoutLater(it.id) } }
    val last = remember(vm.workouts) { vm.lastWorkout(kind, except = existing?.id) }

    fun ghost(name: String): Lift? = vm.lastLift(name, except = existing?.id)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing == null && last != null && last.lifts.isNotEmpty()) {
                SameAsLastChip(copied) { lifts = last.lifts.map { it.draft() }; last.minutes?.let { minutesText = it.toString() }; copied = true; error = null }
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
                    DurationChips(minutesText) { minutesText = it }
                }
            }
            MoreOptions(more, { more = !more }, "Date, notes") {
                DateRow(date) { date = it }
                Hair()
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Notes", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    PlainField(notes, { notes = it.take(300) }, "How did it feel?")
                }
            }
            ErrorNote(error)
            if (existing != null) EditorDelete("Delete workout", del, enabled = !busy, onDelete = { vm.deleteWorkout(existing.id).also { if (!it) error = vm.error } }, onDone = onClose)
        }
        Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
            val n = lifts.size
            PillButton(if (busy) "Saving…" else if (n == 0) "Save workout" else "Save · $n exercise${if (n == 1) "" else "s"}", enabled = !busy && !del.pending, onClick = {
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
    var more by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(existing.date) }
    val del = rememberEditorDelete(existing.id) { vm.deleteWorkoutLater(existing.id) }
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
                    DurationChips(minutes) { minutes = it }
                    if (burn != null) {
                        Hair()
                        RowSpaceBetween {
                            Text("Burned", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
                            Text("${kcal.roundToInt()} kcal", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                        }
                    }
                }
            }
            MoreOptions(more, { more = !more }, "Date, notes") {
                DateRow(date) { date = it }
                Hair()
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Notes", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    PlainField(notes, { notes = it.take(300) }, "How did it feel?")
                }
            }
            ErrorNote(error)
            EditorDelete("Delete workout", del, enabled = !busy, onDelete = { vm.deleteWorkout(existing.id).also { if (!it) error = vm.error } }, onDone = onClose)
        }
        Box(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding()) {
            PillButton(if (busy) "Saving…" else "Save", enabled = !busy && !del.pending && mins > 0, onClick = {
                scope.launch {
                    busy = true; error = null
                    val b = burn?.let { AppViewModel.WorkoutBurn(it.activityCode, it.name, it.intensity, (kcal * 10).roundToInt() / 10.0) }
                    val ok = vm.saveWorkout(existing.id, date, existing.muscles, existing.bandLevel, null, mins, existing.exercises, notes.trim(), kind = existing.kind, burn = b)
                    if (ok) onClose() else { error = vm.error; busy = false }
                }
            })
        }
    }
}

/** 15 · 30 · 45 · 60 min, one tap each. */
@Composable
internal fun DurationChips(minutes: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(15, 30, 45, 60).forEach { d -> com.sohum.bandlog.ui.components.Chip("$d min", minutes == "$d", { onPick("$d") }, Modifier.weight(1f)) }
    }
}

/** "Date  Today, 25 Sep ›" with the system date picker (no future days). */
@Composable
internal fun DateRow(date: String, onPick: (String) -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    RowSpaceBetween {
        Text("Date", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
        Row(
            Modifier.clickable {
                val d = runCatching { java.time.LocalDate.parse(date) }.getOrDefault(java.time.LocalDate.now(com.sohum.bandlog.util.Dates.ZONE))
                runCatching {
                    android.app.DatePickerDialog(ctx, { _, y, m, dd -> onPick(java.time.LocalDate.of(y, m + 1, dd).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (date == com.sohum.bandlog.util.Dates.today()) "Today, ${com.sohum.bandlog.util.Dates.short(date).drop(4)}" else com.sohum.bandlog.util.Dates.short(date), fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
            Text("  ›", fontSize = 15.sp, color = p.muted)
        }
    }
}
