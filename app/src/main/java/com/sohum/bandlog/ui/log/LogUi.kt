package com.sohum.bandlog.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.ExerciseEntry
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.HistoryIcon
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.activityIcon
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * v2.8 logging building blocks shared by the Log activity forms, the Water page, Weight history
 * and the FAB (same wording and behaviour as the web app's LogBits.tsx): one "More options"
 * toggle, one "Same as last time" chip, the intensity chips, the in-editor Delete with the v2.7
 * undo, the undo snackbar and the tappable exercise row.
 */

const val SAME_AS_LAST = "Same as last time"

/** v2.8 Log activity types, picked inline. */
val ACTIVITY_TYPES = listOf("gym", "bodyweight", Workout.BANDS, "cardio", "sport", "yoga", "other")

fun activityTypeLabel(t: String): String = when (t) {
    "gym" -> "Gym"; "bodyweight" -> "Bodyweight"; "cardio" -> "Cardio / Run"; "sport" -> "Sport"; "yoga" -> "Yoga"; "other" -> "Other"; else -> "Bands"
}

private val CARDIO_WORDS = Regex("\\b(run|running|jog|walk|walking|hik|cycl|bicycl|bike|spin|swim|skip|jump rope|stair|ellip|row|treadmill|hiit|circuit|danc|cardio)", RegexOption.IGNORE_CASE)
private val CARDIO_CODES = setOf("12150", "LI-17190", "01015", "LI-15551", "LI-17133", "12180", "02090", "02080")

/** Runs, walks, rides and friends: the Cardio / Run type (anything else logged by hand is Other). */
fun isCardioName(name: String, code: String?): Boolean = (code != null && code in CARDIO_CODES) || CARDIO_WORDS.containsMatchIn(name)

/** Your own runs / activities (not workout burns or Health Connect). */
fun ownExercises(list: List<ExerciseEntry>): List<ExerciseEntry> =
    list.filter { (it.source == "manual" || it.source == "describe") && it.minutes > 0 && it.kcal > 0 }.sortedByDescending { it.createdAt }

/** Where Log activity opens: whatever was logged most recently (a workout's kind, or Cardio / Other), Gym for a first-timer. */
fun defaultActivityType(workouts: List<Workout>, exercises: List<ExerciseEntry>): String {
    val w = workouts.maxByOrNull { it.date }
    val e = ownExercises(exercises).firstOrNull()
    if (e != null && (w == null || e.date >= w.date)) return if (isCardioName(e.name, e.activityCode)) "cardio" else "other"
    return w?.kind ?: "gym"
}

/** "More options" — everything past the common case, collapsed by default. */
@Composable
fun MoreOptions(open: Boolean, onToggle: () -> Unit, hint: String, content: @Composable () -> Unit) {
    val p = palette
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "moreTurn")
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).background(p.card2, RoundedCornerShape(16.dp)).clickable(onClick = onToggle).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("More options", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
            Spacer(Modifier.width(8.dp))
            Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(" ⌄", fontSize = 16.sp, color = p.muted, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(open) { Column(Modifier.padding(top = 4.dp)) { content() } }
    }
}

/** The single "Same as last time" chip: black until applied, then grey. */
@Composable
fun SameAsLastChip(applied: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.height(44.dp).pressable().background(if (applied) p.card2 else p.btn, CircleShape).clickable(onClickLabel = SAME_AS_LAST, onClick = onClick).padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HistoryIcon, null, tint = if (applied) p.ink else p.btnInk, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(SAME_AS_LAST, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (applied) p.ink else p.btnInk, maxLines = 1, softWrap = false)
    }
}

/** Easy · Moderate · Hard: the slider's bands as three taps (15 / 40 / 70 %). */
val INTENSITY_CHIPS = listOf("Easy" to 15, "Moderate" to 40, "Hard" to 70)

fun intensityChipFor(pct: Int): Int = when { pct < 25 -> 0; pct < 55 -> 1; else -> 2 }

@Composable
fun IntensityChips(pct: Int, onChange: (Int) -> Unit) {
    val sel = intensityChipFor(pct)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        INTENSITY_CHIPS.forEachIndexed { i, (label, v) ->
            com.sohum.bandlog.ui.components.Chip(label, i == sel, { if (i != sel) onChange(v) }, Modifier.weight(1f))
        }
    }
}

/**
 * Delete inside an editor, with the v2.7 undo: "Delete" turns into "Deleted · Undo" for ~5 s, then
 * [onDelete] runs (it returns false on failure). Leaving the editor early hands the delete to
 * [onDeleteLater] so it isn't silently dropped.
 */
class EditorDeleteState internal constructor() {
    var pending by mutableStateOf(false)
    var busy by mutableStateOf(false)
    internal var job: Job? = null
}

@Composable
fun rememberEditorDelete(key: Any?, onDeleteLater: () -> Unit): EditorDeleteState {
    val st = remember(key) { EditorDeleteState() }
    val later by rememberUpdatedState(onDeleteLater)
    DisposableEffect(key) { onDispose { if (st.pending && !st.busy) { st.job?.cancel(); later() } } }
    return st
}

@Composable
fun EditorDelete(label: String, state: EditorDeleteState, enabled: Boolean = true, onDelete: suspend () -> Boolean, onDone: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    if (state.pending) {
        RowSpaceBetween {
            Text("Deleted", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(vertical = 12.dp))
            Text("Undo", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btn, modifier = Modifier.clickable { state.job?.cancel(); state.job = null; state.pending = false }.padding(12.dp))
        }
    } else {
        Box(Modifier.heightIn(min = 44.dp).clickable(enabled = enabled && !state.busy) {
            state.pending = true
            state.job = scope.launch {
                delay(5000)
                state.busy = true
                if (onDelete()) onDone() else { state.busy = false; state.pending = false }
            }
        }.padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.red)
        }
    }
}

/** A plain Delete for sheets / dialogs whose undo lives in the list behind them. */
@Composable
fun DeleteLink(label: String = "Delete", enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = palette.red)
    }
}

/** Bottom snackbar with an optional Undo (above the tab bar). */
@Composable
fun UndoSnackbar(text: String, modifier: Modifier = Modifier, undoEnabled: Boolean = true, onUndo: (() -> Unit)?) {
    val p = palette
    Row(
        modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = p.shadow, spotColor = p.shadow).background(p.btn, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.btnInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onUndo != null) Box(Modifier.heightIn(min = 44.dp).clickable(enabled = undoEnabled, onClick = onUndo).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text("Undo", fontSize = 14.sp, fontWeight = FontWeight(800), color = p.btnInk.copy(alpha = if (undoEnabled) 1f else 0.5f))
        }
    }
}

/**
 * v2.8: a logged run / activity row — tapping it opens its editor (delete lives there). Home
 * should render this in place of its own expanding ExerciseRow (see the v2.8 logging notes).
 */
@Composable
fun ActivityExerciseRow(e: ExerciseEntry, onOpen: () -> Unit) {
    val p = palette
    val isBands = e.activityCode?.startsWith("LI-BAND") == true || e.name.contains("lifting", ignoreCase = true) || e.name.contains("band", ignoreCase = true)
    Card(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(enabled = e.source != "health", onClickLabel = "Edit", onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(p.greenBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(if (isBands) com.sohum.bandlog.ui.components.DumbbellIcon else activityIcon(e.name, e.activityCode), null, tint = p.green, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(e.name.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${e.kcal.toInt()} kcal", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("${e.minutes} min", fontSize = 12.sp, color = p.muted, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("›", fontSize = 20.sp, fontWeight = FontWeight(500), color = p.muted)
        }
    }
}

/**
 * A row just deleted from a list inside a card (Water page, Weight history): "Deleted" with Undo
 * for ~5 s, then [onExpire] (the real delete). Leaving the screen early still deletes.
 */
@Composable
fun DeletedRow(onUndo: () -> Unit, onExpire: () -> Unit) {
    val p = palette
    var settled by remember { mutableStateOf(false) }
    val expire by rememberUpdatedState(onExpire)
    androidx.compose.runtime.LaunchedEffect(Unit) { delay(5000); if (!settled) { settled = true; expire() } }
    DisposableEffect(Unit) { onDispose { if (!settled) { settled = true; expire() } } }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Deleted", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.weight(1f))
        Box(Modifier.heightIn(min = 44.dp).clickable { settled = true; onUndo() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text("Undo", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.btn)
        }
    }
}
