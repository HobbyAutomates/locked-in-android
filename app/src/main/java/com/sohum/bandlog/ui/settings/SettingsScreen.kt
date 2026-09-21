package com.sohum.bandlog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.UpdateViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Overline
import com.sohum.bandlog.ui.components.SectionGap
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, updateVm: UpdateViewModel) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val p = vm.profile
    var weekly by remember(p) { mutableStateOf(p.weeklyWorkoutTarget.toString()) }
    var protein by remember(p) { mutableStateOf(p.proteinTargetG.toString()) }
    var calories by remember(p) { mutableStateOf(p.calorieTarget.toString()) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 12.dp, 16.dp, 96.dp))) {
        Overline("Settings", color = cs.primary)
        Text("Targets", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp)
        SectionGap()
        Card {
            OutlinedTextField(weekly, { weekly = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Workouts per week") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(protein, { protein = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Protein per day (g)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Calories per day (kcal)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(12.dp))
            ErrorNote(vm.error)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; saved = false
                            val ok = vm.saveTargets(Profile(weekly.toIntOrNull() ?: 3, protein.toIntOrNull() ?: 120, calories.toIntOrNull() ?: 2200))
                            saved = ok; busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text(if (busy) "Saving…" else "Save targets", fontWeight = FontWeight(700)) }
                if (saved) Text("Saved", color = cs.tertiary, modifier = Modifier.padding(top = 12.dp))
            }
        }
        SectionGap()
        Card {
            Overline("Account")
            Spacer(Modifier.height(6.dp))
            Text(Session.email ?: "—", fontWeight = FontWeight(600))
            Text("Signed in on this phone. Sign out only if you want to switch accounts.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.signOut() }) { Text("Sign out", color = cs.error) }
        }
        SectionGap()
        Card {
            Overline("App")
            Spacer(Modifier.height(6.dp))
            Text("Band Log ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontWeight = FontWeight(600))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { updateVm.check() }, enabled = !updateVm.checking) { Text(if (updateVm.checking) "Checking…" else "Check for updates") }
                if (updateVm.upToDate) Text("Up to date", color = cs.tertiary, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
