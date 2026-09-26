package com.sohum.bandlog.ui.nutrition

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.InfoIcon
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.DietModes
import com.sohum.bandlog.util.Goals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * §4 diet modes (and the §5 adaptive toggle) inside Nutrition goals. Picking a mode shows what it
 * does to protein / carbs / fat (calories stay), asks to confirm, then offers Undo. Under 18 the
 * adult-only modes read "Not recommended under 18" and can't be picked.
 */
@Composable
fun DietModeSection(vm: AppViewModel) {
    val nvm: NutritionViewModel = viewModel()
    val p = palette
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { nvm.loadSettings(force = true) }
    if (!nvm.settingsLoaded) return
    if (!nvm.settings.supported) {
        ComingSoonCard("Diet modes and weekly targets", "Vegetarian, vegan, Jain, high protein, keto and more, plus targets that adapt to your weekly weight trend.")
        return
    }
    val prof = vm.profile
    val age = Goals.ageYears(prof.dob)
    val current = nvm.dietMode(prof)
    var confirm by remember { mutableStateOf<String?>(null) }
    var science by remember { mutableStateOf<DietModes.Mode?>(null) }
    var undo by remember { mutableStateOf<Pair<String, NutritionViewModel.ModeUndo>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(undo) { if (undo != null) { delay(10_000); undo = null } }

    Card(padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Diet mode", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                Text(DietModes.byKey(current).label, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted)
            }
            Text("Sets protein and how carbs and fat split. Calories stay the same. Food suggestions follow it; logging never does.", fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            undo?.let { (label, u) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).background(p.btn, RoundedCornerShape(14.dp)).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Switched to $label", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.btnInk, modifier = Modifier.weight(1f))
                    Box(Modifier.heightIn(min = 44.dp).clickable(enabled = !busy) { scope.launch { busy = true; nvm.undoDietMode(vm, u); undo = null; busy = false } }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Undo", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.btnInk)
                    }
                }
            }
            DietModes.ALL.forEachIndexed { i, m ->
                if (i > 0) Hair()
                val allowed = DietModes.allowed(m.key, age)
                val sel = m.key == current
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = sel, enabled = allowed && !busy, role = Role.RadioButton) { if (!sel) confirm = m.key }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp).background(if (sel) p.btn else p.card2, CircleShape), contentAlignment = Alignment.Center) {
                        if (sel) Icon(CheckIcon, null, tint = p.btnInk, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).alpha(if (allowed) 1f else 0.5f)) {
                        Text(m.label, fontSize = 15.sp, fontWeight = FontWeight(if (sel) 800 else 600), color = p.ink)
                        Text(if (allowed) m.short else DietModes.NOT_FOR_TEENS, fontSize = 12.sp, color = if (allowed) p.muted else p.orange, lineHeight = 16.sp)
                    }
                    Box(
                        Modifier.size(44.dp).clickable { science = m }.semantics { contentDescription = "The science behind ${m.label}" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(InfoIcon, null, tint = p.muted, modifier = Modifier.size(16.dp)) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    AdaptiveCard(vm, nvm)

    confirm?.let { key ->
        val m = DietModes.byKey(key)
        val t = nvm.previewMode(prof, key)
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = p.card,
            title = { Text("Switch to ${m.label}?", fontWeight = FontWeight(800), color = p.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    run {
                        Text("Calories stay at ${prof.calorieTarget} kcal.", color = p.muted, fontSize = 13.sp)
                        Compare("Protein", prof.proteinTargetG, t.protein)
                        Compare("Carbs", prof.carbTargetG, t.carbs)
                        Compare("Fat", prof.fatTargetG, t.fat)
                    }
                    m.warning?.let { Text(it, color = p.red, fontSize = 13.sp, fontWeight = FontWeight(600), lineHeight = 18.sp) }
                    DietModes.excludes(key)?.let { Text("Suggestions will leave out $it.", color = p.muted, fontSize = 12.sp) }
                    Text("You can undo this right after.", color = p.muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirm = null
                    scope.launch {
                        busy = true
                        nvm.setDietMode(vm, key)?.let { undo = m.label to it }
                        busy = false
                    }
                }) { Text("Switch", color = p.ink, fontWeight = FontWeight(800)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = p.muted) } },
        )
    }
    science?.let { m ->
        BottomSheet(title = "${m.label}: the science", subtitle = "General guidance, not medical advice.", onDismiss = { science = null }, primary = "Got it", onPrimary = { science = null }) {
            Text(m.science, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text("Source", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted)
            Text(m.source, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
            if (m.adultsOnly) Text("Adults only. " + DietModes.NOT_FOR_TEENS + ".", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.orange, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Compare(label: String, before: Int, after: Int) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
        Text("$before g", fontSize = 14.sp, color = p.muted)
        Text("  →  ", fontSize = 14.sp, color = p.muted)
        Text("$after g", fontSize = 14.sp, fontWeight = FontWeight(800), color = if (after == before) p.ink else p.purple)
    }
}

/** §5 the adaptive toggle (off by default) and what the latest check-in said. */
@Composable
private fun AdaptiveCard(vm: AppViewModel, nvm: NutritionViewModel) {
    val p = palette
    val scope = rememberCoroutineScope()
    val on = nvm.settings.adaptiveTargets
    LaunchedEffect(on) { if (on) nvm.ensureCheckin(vm) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Adaptive weekly targets", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("Every Monday we compare what you ate with your weight trend and suggest a new calorie target. You choose whether to apply it.", fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = on, onCheckedChange = { v -> scope.launch { nvm.setAdaptive(v) } },
                colors = SwitchDefaults.colors(checkedThumbColor = p.btnInk, checkedTrackColor = p.btn, uncheckedTrackColor = p.card2, uncheckedBorderColor = p.hair),
            )
        }
        if (on) {
            val r = nvm.checkinResult
            val c = nvm.checkin
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    c?.newTarget != null && c.applied -> "Applied this week: ${c.newTarget} kcal."
                    c != null -> c.reason
                    r != null -> if (r.ok) r.reason else r.missing
                    else -> "Needs 10 of the last 14 days logged and 4 weigh-ins."
                },
                fontSize = 12.sp, color = p.ink, lineHeight = 17.sp,
            )
        }
        NoticeLine(nvm)
    }
}
