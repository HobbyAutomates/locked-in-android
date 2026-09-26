package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Lifts
import com.sohum.bandlog.util.MuscleMap
import com.sohum.bandlog.util.Pro
import com.sohum.bandlog.util.Routines
import kotlinx.coroutines.launch

/** "Mon Push · Wed Pull · Fri Legs" */
internal fun daysSummary(r: Routines.Routine): String =
    r.days.joinToString(" · ") { d -> (d.weekday?.let { Routines.weekdayLabel(it) + " " } ?: "") + d.name }

/**
 * v2.13 routines + planner (spec §12, Pro): your routines (one active drives Today's session),
 * the four templates, and a Mon–Sun planner for the active one. Without schema_v36 the templates
 * can still start a live workout; saving routines waits for the update.
 */
@Composable
fun RoutinesScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    LaunchedEffect(Unit) { pvm.loadRoutines() }
    var confirmDelete by remember { mutableStateOf<Routines.Routine?>(null) }
    SubPage("Routines", onBack) {
        ProGate(pvm, Pro.Feature.ROUTINES) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                pvm.live?.let { l ->
                    Card(onClick = { PlatformNav.open(PlatformPage.WORKOUT) }) {
                        Text("Workout in progress", fontSize = 13.sp, fontWeight = FontWeight(600), color = accentColor)
                        Text(l.title, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                    }
                }
                if (pvm.routinesSupported == false) {
                    ComingSoonCard("Saved routines", "Saving routines and the weekly planner arrive with the next server update. You can already start any template below as a live workout.")
                } else {
                    PillButton("New routine", {
                        PlatformNav.editing = Routines.Routine(name = "My routine", days = listOf(Routines.Day("Day 1", 1)), active = pvm.activeRoutine == null)
                        PlatformNav.open(PlatformPage.ROUTINE_EDIT)
                    }, icon = LineIcons.Plus)
                    pvm.activeRoutine?.let { r -> Planner(vm, pvm, r) }
                    pvm.routines.forEach { r ->
                        Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                                    Text(daysSummary(r), fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                                }
                                if (r.active) SoftPill("Active")
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!r.active) Chip("Make active", false, { pvm.setActive(r.id) })
                                Chip("Edit", false, { PlatformNav.editing = r; PlatformNav.open(PlatformPage.ROUTINE_EDIT) })
                                Chip("Delete", false, { confirmDelete = r })
                            }
                        }
                    }
                }
                Text("Templates", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                Routines.TEMPLATES.forEach { t -> TemplateCard(vm, pvm, t) }
                ErrorNote(pvm.error)
            }
        }
    }
    confirmDelete?.let { r ->
        ConfirmDialog("Delete ${r.name}?", "Your logged workouts stay. Only the plan goes.", "Delete", danger = true,
            onConfirm = { r.id?.let { pvm.deleteRoutine(it) }; confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
}

/** Mon–Sun for the active routine; tap a planned day to start it. */
@Composable
private fun Planner(vm: AppViewModel, pvm: PlatformViewModel, r: Routines.Routine) {
    val p = palette
    val a = accentColor
    val todayWd = Routines.weekdayOf(Dates.today())
    val planned = r.days.any { it.weekday != null }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleWithChip("This week", pro = true)
            Spacer(Modifier.weight(1f))
            Text(r.name, fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.height(10.dp))
        if (!planned) Text("Days rotate: whatever's next after your last session. Give days a weekday in Edit to plan the week.", fontSize = 12.sp, color = p.muted, lineHeight = 17.sp)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { wd ->
                val day = r.days.firstOrNull { it.weekday == wd }
                val isToday = wd == todayWd
                Column(
                    Modifier.weight(1f).heightIn(min = 64.dp)
                        .background(if (day != null) a.copy(alpha = if (isToday) 1f else 0.14f) else p.card2, RoundedCornerShape(12.dp))
                        .let { m -> if (isToday && day == null) m.border(1.5.dp, a, RoundedCornerShape(12.dp)) else m }
                        .clickable(enabled = day != null) { day?.let { startLive(vm, pvm, it, "${r.name} · ${it.name}") } }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(Routines.weekdayLabel(wd), fontSize = 11.sp, fontWeight = FontWeight(700), color = if (day != null && isToday) Color.White else p.muted)
                    Text(day?.name ?: "Rest", fontSize = 10.sp, fontWeight = FontWeight(600), color = if (day != null && isToday) Color.White else if (day != null) p.ink else p.muted, maxLines = 2, lineHeight = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(vm: AppViewModel, pvm: PlatformViewModel, t: Routines.Routine) {
    val p = palette
    var open by remember { mutableStateOf(false) }
    Card(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(daysSummary(t), fontSize = 12.sp, color = p.muted)
            }
            Icon(LineIcons.ChevronRight, null, tint = p.muted, modifier = Modifier.size(16.dp))
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            t.days.forEach { d ->
                Hair()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(d.exercises.joinToString(", ") { "${it.name} ${it.sets}×${it.reps}" }, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
                    }
                    Chip("Start", false, { startLive(vm, pvm, d, "${t.name} · ${d.name}") })
                }
            }
            if (pvm.routinesSupported != false) {
                Spacer(Modifier.height(8.dp))
                PillButton("Use this template", {
                    PlatformNav.editing = t.copy(id = null, active = pvm.activeRoutine == null)
                    PlatformNav.open(PlatformPage.ROUTINE_EDIT)
                }, height = 46.dp)
            }
        }
    }
}

/** Build-your-own: name, days (name + weekday), exercises from the library with sets, reps and rest. */
@Composable
fun RoutineEditorScreen(pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val start = PlatformNav.editing ?: Routines.Routine(name = "My routine", days = listOf(Routines.Day("Day 1", 1)))
    var r by remember(start) { mutableStateOf(start) }
    var pickFor by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    fun setDay(i: Int, d: Routines.Day) { r = r.copy(days = r.days.mapIndexed { j, x -> if (j == i) d else x }) }
    SubPage(if (start.id == null) "New routine" else "Edit routine", onBack) {
        Card {
            Text("Name", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
            com.sohum.bandlog.ui.log.PlainField(r.name, { r = r.copy(name = it.take(60)) }, "Routine name")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active routine", fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = r.active, onCheckedChange = { r = r.copy(active = it) })
            }
            Text("The active routine drives Today's session on Home.", fontSize = 12.sp, color = p.muted)
        }
        r.days.forEachIndexed { i, d ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { com.sohum.bandlog.ui.log.PlainField(d.name, { setDay(i, d.copy(name = it.take(30))) }, "Day name") }
                    if (r.days.size > 1) Box(Modifier.size(40.dp).clickable(onClickLabel = "Remove day") { r = r.copy(days = r.days.filterIndexed { j, _ -> j != i }) }, contentAlignment = Alignment.Center) {
                        Icon(CrossIcon, null, tint = p.muted, modifier = Modifier.size(14.dp))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Any", d.weekday == null, { setDay(i, d.copy(weekday = null)) })
                    (1..7).forEach { wd -> Chip(Routines.weekdayLabel(wd), d.weekday == wd, { setDay(i, d.copy(weekday = wd)) }) }
                }
                val t = MuscleMap.targetsFor(d.exercises.map { it.name })
                if (t.all.isNotEmpty()) MuscleFigures(targetFills(t.primary, t.secondary), Modifier.height(130.dp), description = "Muscles for ${d.name}")
                d.exercises.forEachIndexed { ei, e ->
                    Hair()
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                            Box(Modifier.size(36.dp).clickable(onClickLabel = "Remove ${e.name}") { setDay(i, d.copy(exercises = d.exercises.filterIndexed { k, _ -> k != ei })) }, contentAlignment = Alignment.Center) {
                                Icon(CrossIcon, null, tint = p.muted, modifier = Modifier.size(12.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            fun upd(x: Routines.Exercise) = setDay(i, d.copy(exercises = d.exercises.mapIndexed { k, y -> if (k == ei) x else y }))
                            com.sohum.bandlog.ui.log.NumberField("${e.sets}", { v -> v.filter { it.isDigit() }.take(2).toIntOrNull()?.let { upd(e.copy(sets = it.coerceIn(1, 20))) } }, "sets")
                            com.sohum.bandlog.ui.log.NumberField("${e.reps}", { v -> v.filter { it.isDigit() }.take(3).toIntOrNull()?.let { upd(e.copy(reps = it.coerceIn(1, 100))) } }, "reps")
                            com.sohum.bandlog.ui.log.NumberField("${e.restS}", { v -> v.filter { it.isDigit() }.take(3).toIntOrNull()?.let { upd(e.copy(restS = it.coerceIn(0, 900))) } }, "s rest")
                        }
                    }
                }
                Text(
                    "+ Add exercise", fontSize = 14.sp, fontWeight = FontWeight(600), color = accentColor,
                    modifier = Modifier.heightIn(min = 44.dp).clickable { pickFor = i }.padding(vertical = 12.dp),
                )
            }
        }
        PillButton("Add a day", { r = r.copy(days = r.days + Routines.Day("Day ${r.days.size + 1}")) }, bg = p.card2, fg = p.ink, icon = LineIcons.Plus)
        ErrorNote(err)
        PillButton(if (busy) "Saving…" else "Save routine", {
            if (r.days.all { it.exercises.isEmpty() }) { err = "Add at least one exercise."; return@PillButton }
            busy = true; err = null
            scope.launch {
                val ok = pvm.saveRoutine(r.copy(name = r.name.ifBlank { "My routine" }))
                busy = false
                if (ok) onBack() else err = if (pvm.routinesSupported == false) "Saving routines comes with the next update." else pvm.error ?: "Couldn't save"
            }
        }, enabled = !busy)
    }
    pickFor?.let { i ->
        ExercisePicker(onDismiss = { pickFor = null }) { name ->
            val d = r.days[i]
            setDay(i, d.copy(exercises = d.exercises + Routines.Exercise(name)))
            pickFor = null
        }
    }
}

/** Search the exercise library (Lifts.ALL), showing each lift's main muscles. */
@Composable
fun ExercisePicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val p = palette
    var q by remember { mutableStateOf("") }
    BottomSheet(title = "Add exercise", onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
            com.sohum.bandlog.ui.log.PlainField(q, { q = it.take(40) }, "Search exercises")
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            val hits = Lifts.search(q, bodyweightFirst = false)
            hits.forEach { e ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(e.name) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                        Text(MuscleMap.of(e.name)?.primary?.joinToString(", ") { it.label } ?: e.muscles.joinToString(", "), fontSize = 12.sp, color = p.muted)
                    }
                    Box(Modifier.size(28.dp).border(1.dp, p.hair, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(LineIcons.Plus, null, tint = p.ink, modifier = Modifier.size(14.dp))
                    }
                }
            }
            val custom = q.trim()
            if (custom.length >= 3 && hits.none { it.name.equals(custom, ignoreCase = true) }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(custom.replaceFirstChar { it.uppercase() }) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Add \"$custom\"", fontSize = 15.sp, fontWeight = FontWeight(600), color = accentColor)
                }
            }
        }
    }
}
