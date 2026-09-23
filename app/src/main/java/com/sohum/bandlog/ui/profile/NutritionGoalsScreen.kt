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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Goals
import kotlinx.coroutines.launch

/** Four editable macro goals plus the "✨ Auto Generate Goals" shortcut. */
@Composable
fun NutritionGoalsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPersonal: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val prof = vm.profile

    var calories by remember(prof) { mutableStateOf(prof.calorieTarget.toString()) }
    var protein by remember(prof) { mutableStateOf(prof.proteinTargetG.toString()) }
    var carbs by remember(prof) { mutableStateOf(prof.carbTargetG.toString()) }
    var fat by remember(prof) { mutableStateOf(prof.fatTargetG.toString()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun edited(): Profile = prof.copy(
        calorieTarget = calories.toIntOrNull()?.coerceIn(800, 10_000) ?: prof.calorieTarget,
        proteinTargetG = protein.toIntOrNull()?.coerceIn(10, 500) ?: prof.proteinTargetG,
        carbTargetGSet = carbs.toIntOrNull()?.coerceIn(0, 1000),
        fatTargetGSet = fat.toIntOrNull()?.coerceIn(0, 500),
    )
    val dirty = edited() != prof

    SubPage("Edit nutrition goals", onBack) {
        Rise(0) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    GoalRow("Calorie goal", p.ink, calories, last = false) { calories = it.filter(Char::isDigit).take(5) }
                    Hair()
                    GoalRow("Protein goal", p.red, protein, last = false) { protein = it.filter(Char::isDigit).take(4) }
                    Hair()
                    GoalRow("Carb goal", p.orange, carbs, last = false) { carbs = it.filter(Char::isDigit).take(4) }
                    Hair()
                    GoalRow("Fat goal", p.blue, fat, last = true) { fat = it.filter(Char::isDigit).take(4) }
                }
            }
        }

        Rise(1) {
            Card {
                Text("Auto Generate Goals", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mifflin-St Jeor from your weight, height, age and gender, adjusted for " +
                        "${prof.goalType} at ${com.sohum.bandlog.ui.today.fmt(prof.goalSpeedKgWk)} kg/week. " +
                        "Protein 1.8 g/kg, fat a quarter of your calories, carbs the rest.",
                    fontSize = 12.sp, color = p.muted, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                PillButton("✨  Auto Generate Goals", height = 46.dp, onClick = {
                    val gaps = Goals.missing(prof)
                    if (gaps.isNotEmpty()) {
                        note = "Add your ${gaps.joinToString(", ")} in Personal details first."
                    } else {
                        val t = Goals.generate(prof)!!
                        calories = t.calories.toString(); protein = t.protein.toString()
                        carbs = t.carbs.toString(); fat = t.fat.toString()
                        note = "Generated — review, then Save goals."
                    }
                })
                note?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (it.startsWith("Generated")) p.green else p.orange)
                    if (!it.startsWith("Generated")) {
                        Spacer(Modifier.height(8.dp))
                        PillButton("Open Personal details", onOpenPersonal, height = 42.dp, bg = p.card2, fg = p.ink)
                    }
                }
            }
        }

        Rise(2) { ErrorNote(vm.error) }
        Rise(2) {
            PillButton(
                if (busy) "Saving…" else if (saved && !dirty) "Saved" else "Save goals",
                enabled = !busy && dirty,
                onClick = {
                    scope.launch {
                        busy = true; saved = false; note = null
                        saved = vm.saveProfile(edited())
                        busy = false
                    }
                },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** A coloured ring icon, the goal's name, and its editable number. */
@Composable
private fun GoalRow(label: String, color: Color, value: String, last: Boolean, onChange: (String) -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(1f, color, 28.dp, 4.dp) { Box(Modifier.size(7.dp).background(color, CircleShape)) }
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
        }
        NumberField(value, onChange, if (label.startsWith("Calorie")) "kcal" else "g", imeAction = if (last) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Next)
    }
}
