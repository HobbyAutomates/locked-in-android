package com.sohum.bandlog.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.UpdateViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.DumbbellIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, updateVm: UpdateViewModel, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val prof = vm.profile
    var weekly by remember(prof) { mutableStateOf(prof.weeklyWorkoutTarget.toString()) }
    var protein by remember(prof) { mutableStateOf(prof.proteinTargetG.toString()) }
    var calories by remember(prof) { mutableStateOf(prof.calorieTarget.toString()) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val dirty = weekly != prof.weeklyWorkoutTarget.toString() || protein != prof.proteinTargetG.toString() || calories != prof.calorieTarget.toString()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 12.dp, 16.dp, 110.dp)), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Rise(0) { ScreenTitle("Settings") }
        Rise(1) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) { Text((Session.email ?: "?").take(1).uppercase(), fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink) }
                    Spacer(Modifier.width(14.dp))
                    Column { Text("Sohum", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink); Text(Session.email ?: "—", fontSize = 13.sp, color = p.muted) }
                }
            }
        }
        Rise(2) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Daily targets", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    SettingRow(DumbbellIcon, p.ink, "Workouts per week") { NumberField(weekly, { weekly = it.filter(Char::isDigit) }, "") }
                    Hair()
                    SettingRow(FlameIcon, p.red, "Protein") { NumberField(protein, { protein = it.filter(Char::isDigit) }, "g") }
                    Hair()
                    SettingRow(FlameIcon, p.ink, "Calories") { NumberField(calories, { calories = it.filter(Char::isDigit) }, "kcal") }
                    ErrorNote(vm.error, Modifier.padding(bottom = 12.dp))
                }
            }
        }
        if (dirty || saved) Rise(2) {
            PillButton(if (busy) "Saving…" else if (saved && !dirty) "Saved" else "Save targets", enabled = !busy && dirty, onClick = {
                scope.launch {
                    busy = true; saved = false
                    saved = vm.saveTargets(Profile(weekly.toIntOrNull() ?: 3, protein.toIntOrNull() ?: 120, calories.toIntOrNull() ?: 2200))
                    busy = false
                }
            })
        }
        Rise(3) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.DarkMode, p.ink, "Appearance") {
                        Row(Modifier.background(p.card2, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThemeMode.entries.forEach { m ->
                                val sel = m == themeMode
                                Box(Modifier.height(28.dp).background(if (sel) p.btn else Color.Transparent, CircleShape).clickable { onThemeMode(m) }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.btnInk else p.muted)
                                }
                            }
                        }
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Refresh, p.ink, "Check for updates", onClick = { updateVm.check() }) {
                        Text(
                            when { updateVm.checking -> "Checking…"; updateVm.upToDate -> "v${BuildConfig.VERSION_NAME} · up to date"; else -> "v${BuildConfig.VERSION_NAME}" },
                            fontSize = 13.sp, fontWeight = FontWeight(600), color = if (updateVm.upToDate) p.green else p.muted,
                        )
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Logout, p.ink, "Sign out", onClick = { vm.signOut() }) { Text("›", fontSize = 16.sp, color = p.muted) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, tint: Color, label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val p = palette
    Box(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        RowSpaceBetween {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 13.dp)) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
            }
            trailing()
        }
    }
}
