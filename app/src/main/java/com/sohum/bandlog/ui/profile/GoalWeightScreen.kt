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
import com.sohum.bandlog.ui.components.Card
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GOAL_TYPES = listOf("lose", "maintain", "gain")

/** Lose / Maintain / Gain, the two weights, and how fast to get there. */
@Composable
fun GoalWeightScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val prof = vm.profile

    var goalType by remember(prof) { mutableStateOf(prof.goalType) }
    var current by remember(prof) { mutableStateOf(prof.weightKg?.let { fmt(it) } ?: "") }
    var goal by remember(prof) { mutableStateOf(prof.goalWeightKg?.let { fmt(it) } ?: "") }
    var speed by remember(prof) { mutableStateOf(prof.goalSpeedKgWk.toFloat().coerceIn(0.1f, 1.5f)) }
    var regenerate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    // Slider steps every 0.1 kg; round so the label and the saved value always agree.
    val speedKg = (speed * 10).roundToInt() / 10.0
    fun edited(): Profile = prof.copy(
        goalType = goalType,
        weightKg = current.toDoubleOrNull() ?: prof.weightKg,
        goalWeightKg = goal.toDoubleOrNull(),
        goalSpeedKgWk = speedKg,
    )
    val dirty = edited() != prof
    val maintaining = goalType == "maintain"

    SubPage("Goal & current weight", onBack) {
        Rise(0) {
            Card {
                Text("I want to", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                Spacer(Modifier.height(10.dp))
                Segmented(
                    listOf("Lose", "Maintain", "Gain"),
                    GOAL_TYPES.indexOf(goalType).coerceAtLeast(1),
                    { goalType = GOAL_TYPES[it] },
                )
            }
        }

        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(ScaleIcon, p.ink, "Current weight") { NumberField(current, { current = it.filter { c -> c.isDigit() || c == '.' } }, "kg", imeAction = androidx.compose.ui.text.input.ImeAction.Next) }
                    Hair()
                    SettingRow(TargetIcon, p.ink, "Goal weight") { NumberField(goal, { goal = it.filter { c -> c.isDigit() || c == '.' } }, "kg") }
                }
            }
        }

        // ---- speed ----
        Rise(2) {
            Card {
                Text(if (maintaining) "Weekly pace" else "How fast?", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (maintaining) "Only used if you switch to lose or gain." else "Changes how many calories we add or subtract each day.",
                    fontSize = 12.sp, color = p.muted,
                )
                Spacer(Modifier.height(14.dp))
                GoalSpeedPicker(speed) { speed = it }
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
                            colors = SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk),
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
                        busy = false
                    }
                },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
