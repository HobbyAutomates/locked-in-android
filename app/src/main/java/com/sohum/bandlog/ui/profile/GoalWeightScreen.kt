package com.sohum.bandlog.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BmiCard
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.SafetyNote
import com.sohum.bandlog.ui.components.TeenGoalMigration
import com.sohum.bandlog.ui.components.TeenNote
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GoalSpeedPicker
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ScaleIcon
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.TargetIcon
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.TargetEdits
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lose / Maintain / Gain, the two weights, and how fast to get there. v2.10: the goal choices
 * follow age (no "lose" under 18), the pace slider stops at the safe max for this body weight,
 * the BMI card sits underneath, and a safety flag shows a kind note without blocking the save.
 */
@Composable
fun GoalWeightScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prof = vm.profile
    val age = Goals.ageYears(prof.dob)
    val teen = Goals.isTeen(age)
    val options = Goals.goalOptions(age)
    androidx.compose.runtime.LaunchedEffect(Unit) { if (vm.weights.isEmpty()) vm.loadWeights() }

    var goalType by remember(prof) { mutableStateOf(Goals.effectiveGoal(prof.goalType, age)) }
    var current by remember(prof) { mutableStateOf(prof.weightKg?.let { fmt(it) } ?: "") }
    var goal by remember(prof) { mutableStateOf(prof.goalWeightKg?.let { fmt(it) } ?: "") }
    var speed by remember(prof) { mutableStateOf(Goals.roundSpeed(prof.goalSpeedKgWk, Goals.speedMax(prof.goalType, prof.weightKg)).toFloat()) }
    var regenerate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var flags by remember { mutableStateOf(emptyList<String>()) }

    val kgNow = current.toDoubleOrNull() ?: prof.weightKg
    val maintaining = goalType == "maintain"
    val max = Goals.speedMax(if (maintaining) "lose" else goalType, kgNow)
    // Slider steps every 0.1 kg; round so the label and the saved value always agree.
    val speedKg = Goals.roundSpeed(speed.toDouble(), max)
    fun edited(): Profile = prof.copy(
        goalType = goalType,
        weightKg = kgNow,
        // Teens don't set a target weight; whatever was saved before stays untouched.
        goalWeightKg = if (teen) prof.goalWeightKg else goal.toDoubleOrNull(),
        goalSpeedKgWk = speedKg,
    )
    val dirty = edited() != prof

    SubPage("Goal & current weight", onBack) {
        TeenGoalMigration(vm)
        Rise(0) {
            Card {
                Text("I want to", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options.map { it.label.substringBefore(" ") },
                    options.indexOfFirst { it.key == goalType }.coerceAtLeast(0),
                    { goalType = options[it].key },
                )
                Text(options.firstOrNull { it.key == goalType }?.sub.orEmpty(), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
                if (teen) { Spacer(Modifier.height(10.dp)); TeenNote() }
            }
        }

        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(ScaleIcon, p.ink, "Current weight") { NumberField(current, { current = it.filter { c -> c.isDigit() || c == '.' } }, "kg", imeAction = androidx.compose.ui.text.input.ImeAction.Next) }
                    if (!teen) {
                        Hair()
                        SettingRow(TargetIcon, p.ink, "Goal weight") { NumberField(goal, { goal = it.filter { c -> c.isDigit() || c == '.' } }, "kg") }
                    }
                }
            }
        }

        // ---- speed ----
        Rise(2) {
            Card {
                if (teen) {
                    Text("Your pace", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (goalType == "gain") "We add a small extra (about 10%) on top of what your body needs to grow and train. No speed to pick."
                        else "Your calories match what your body needs to grow and train.",
                        fontSize = 12.sp, color = p.muted, lineHeight = 17.sp,
                    )
                } else {
                    Text(if (maintaining) "Weekly pace" else "How fast?", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (maintaining) "Only used if you switch to lose or gain." else "Changes how many calories we add or subtract each day.",
                        fontSize = 12.sp, color = p.muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    GoalSpeedPicker(speed, if (maintaining) "lose" else goalType, kgNow) { speed = it }
                }
            }
        }

        Rise(3) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(
                        label = "Update goals too",
                        subtitle = if (Goals.missing(prof).isEmpty()) "Recalculate calories and macros on save" else "Needs ${Goals.missing(prof).joinToString(", ")}",
                    ) {
                        Switch(
                            regenerate,
                            { regenerate = it },
                            enabled = Goals.missing(prof).isEmpty(),
                            colors = SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
                        )
                    }
                }
            }
        }

        Rise(4) { ErrorNote(vm.error) }
        Rise(4) {
            PillButton(
                if (busy) "Saving…" else if (saved && !dirty) "Saved" else "Save goal",
                enabled = !busy && (dirty || regenerate),
                onClick = {
                    scope.launch {
                        busy = true; saved = false
                        val next = edited().let { if (regenerate) Goals.applyTo(it) else it }
                        saved = vm.saveProfile(next)
                        if (saved) {
                            var edits = TargetEdits.record(ctx, "goal_weight", prof.goalWeightKg, next.goalWeightKg)
                            if (regenerate) edits = TargetEdits.record(ctx, "calories", prof.calorieTarget.toDouble(), next.calorieTarget.toDouble())
                            flags = Goals.edFlags(Goals.screenInput(next, weights = vm.weights.map { Goals.WeighIn(it.date, it.weightKg) }, edits = edits))
                            regenerate = false
                        }
                        busy = false
                    }
                },
            )
        }
        if (flags.isNotEmpty()) Rise(5) { SafetyNote(vm, flags, onClose = { flags = emptyList() }) }
        Rise(5) { BmiCard(vm, prof.weightKg, waist = true) }
        Spacer(Modifier.height(4.dp))
    }
}
