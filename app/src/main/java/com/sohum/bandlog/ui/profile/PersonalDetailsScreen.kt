package com.sohum.bandlog.ui.profile

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.RulerIcon
import com.sohum.bandlog.ui.components.ScaleIcon
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.StepsIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.TargetIcon
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GENDERS = listOf("male", "female", "other")

/** Everything the goal generator needs: weight, height, birthday, gender, step goal. */
@Composable
fun PersonalDetailsScreen(vm: AppViewModel, onBack: () -> Unit, onChangeGoal: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prof = vm.profile

    var weight by remember(prof) { mutableStateOf(prof.weightKg?.let { fmt(it) } ?: "") }
    var height by remember(prof) { mutableStateOf(prof.heightCm?.let { fmt(it) } ?: "") }
    var dob by remember(prof) { mutableStateOf(prof.dob) }
    var gender by remember(prof) { mutableStateOf(prof.gender) }
    var steps by remember(prof) { mutableStateOf(prof.stepGoal.toString()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    fun edited(): Profile = prof.copy(
        weightKg = weight.toDoubleOrNull(),
        heightCm = height.toDoubleOrNull(),
        dob = dob,
        gender = gender,
        stepGoal = steps.toIntOrNull()?.coerceIn(500, 100_000) ?: 8000,
    )
    val dirty = edited() != prof

    SubPage("Personal details", onBack) {
        // Goal weight headline, mirroring Cal AI's card at the top of this page.
        Rise(0) {
            Card(padding = 20.dp) {
                RowSpaceBetween {
                    Column {
                        Text("Goal weight", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            prof.goalWeightKg?.let { "${fmt(it)} kg" } ?: "Not set",
                            fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink, lineHeight = 36.sp,
                        )
                        Text(
                            prof.goalType.replaceFirstChar { it.uppercase() } + " · ${fmt(prof.goalSpeedKgWk)} kg/week",
                            fontSize = 12.sp, color = p.muted,
                        )
                    }
                    Box(
                        Modifier.width(128.dp).height(40.dp).pressable().background(p.btn, CircleShape).clickable(onClick = onChangeGoal),
                        contentAlignment = Alignment.Center,
                    ) { Text("Change Goal", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk) }
                }
            }
        }

        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(ScaleIcon, p.ink, "Current weight") { NumberField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, "kg") }
                    Hair()
                    SettingRow(RulerIcon, p.ink, "Height") { NumberField(height, { height = it.filter { c -> c.isDigit() || c == '.' } }, "cm") }
                    Hair()
                    SettingRow(TargetIcon, p.ink, "Date of birth", onClick = {
                        val start = runCatching { LocalDate.parse(dob) }.getOrDefault(LocalDate.now().minusYears(17))
                        runCatching {
                            DatePickerDialog(
                                ctx,
                                { _, y, m, d -> dob = LocalDate.of(y, m + 1, d).toString() },
                                start.year, start.monthValue - 1, start.dayOfMonth,
                            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                        }
                    }) {
                        Text(
                            dob?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrDefault(it) } ?: "Set",
                            fontSize = 14.sp, fontWeight = FontWeight(600), color = if (dob == null) p.muted else p.ink,
                        )
                    }
                    Hair()
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Gender", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                        Spacer(Modifier.height(8.dp))
                        Segmented(
                            GENDERS.map { it.replaceFirstChar { c -> c.uppercase() } },
                            GENDERS.indexOf(gender).coerceAtLeast(0),
                            { gender = GENDERS[it] },
                        )
                        if (gender == null) Text("Used only for the BMR formula.", fontSize = 11.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                    }
                    Hair()
                    SettingRow(StepsIcon, p.green, "Daily step goal") { NumberField(steps, { steps = it.filter(Char::isDigit).take(6) }, "steps") }
                }
            }
        }

        Rise(2) { ErrorNote(vm.error) }
        Rise(2) {
            PillButton(
                if (busy) "Saving…" else if (saved && !dirty) "Saved" else "Save details",
                enabled = !busy && dirty,
                onClick = {
                    scope.launch {
                        busy = true; saved = false
                        saved = vm.saveProfile(edited())
                        busy = false
                    }
                },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
