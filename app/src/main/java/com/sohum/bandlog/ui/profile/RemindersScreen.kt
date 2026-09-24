package com.sohum.bandlog.ui.profile

import android.app.TimePickerDialog
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.alarm.MealAlarms
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Reminders
import kotlinx.coroutines.launch

/**
 * Five daily nudges. Toggling or re-timing one writes SharedPreferences and re-arms its alarm
 * immediately; the same map is pushed to `profiles.reminders` so a reinstall restores it.
 */
@Composable
fun RemindersScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prof = vm.profile

    // Server value wins on first open, then the local mirror tracks edits.
    var prefs by remember(prof.remindersJson) {
        mutableStateOf(if (prof.remindersJson.isBlank()) Reminders.load(ctx) else Reminders.parse(prof.remindersJson))
    }
    var notifGranted by remember { mutableStateOf(hasNotifPermission(ctx)) }
    var exactOk by remember { mutableStateOf(MealAlarms.canScheduleExact(ctx)) }
    var busy by remember { mutableStateOf(false) }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { notifGranted = it }

    // Keep the local mirror and the armed alarms in step with whatever is on screen.
    LaunchedEffect(prefs) {
        Reminders.save(ctx, prefs)
        MealAlarms.rescheduleAll(ctx)
        com.sohum.bandlog.notify.Notifications.ensureChannel(ctx)
    }

    fun set(key: String, next: Reminders.Pref) {
        prefs = prefs.toMutableMap().apply { put(key, next) }
        if (next.on && !notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        exactOk = MealAlarms.canScheduleExact(ctx)
    }

    SubPage("Tracking reminders", onBack) {
        Rise(0) {
            Text(
                "A quiet nudge at each meal so nothing goes unlogged, and a 9 pm wrap of your day. Tap a time to change it.",
                fontSize = 13.sp, color = p.muted, lineHeight = 18.sp,
            )
        }

        if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Rise(0) {
                Warning("Notifications are off for Locked In — reminders can't show up.") {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (!exactOk) {
            Rise(0) {
                Warning("Exact alarms are blocked, so reminders may arrive late.") {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${ctx.packageName}")),
                        )
                    }
                }
            }
        }

        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Reminders.SLOTS.forEachIndexed { i, slot ->
                        if (i > 0) Hair()
                        val pref = prefs[slot.key] ?: Reminders.Pref(slot.defaultOn, slot.defaultTime)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(slot.label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                                Text("Locked In — ${slot.prompt}", fontSize = 11.sp, color = p.muted)
                            }
                            Box(
                                Modifier.background(p.card2, RoundedCornerShape(10.dp))
                                    .clickable {
                                        runCatching {
                                            TimePickerDialog(
                                                ctx,
                                                { _, h, m -> set(slot.key, pref.copy(time = Reminders.fmt(h, m))) },
                                                pref.hour, pref.minute, false,
                                            ).show()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    Reminders.pretty(pref.time),
                                    fontSize = 14.sp, fontWeight = FontWeight(700),
                                    color = if (pref.on) p.ink else p.muted,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                pref.on,
                                { set(slot.key, pref.copy(on = it)) },
                                colors = SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk),
                            )
                        }
                    }
                }
            }
        }

        Rise(2) { ErrorNote(vm.error) }
        Rise(2) {
            PillButton(if (busy) "Saving…" else "Save reminders", enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    vm.saveProfile(prof.copy(remindersJson = Reminders.toJson(prefs)))
                    busy = false
                }
            })
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Warning(text: String, onFix: () -> Unit) {
    val p = palette
    Box(Modifier.fillMaxWidth().background(p.orangeBg, RoundedCornerShape(14.dp)).clickable(onClick = onFix).padding(14.dp)) {
        Column {
            Text(text, fontSize = 13.sp, color = p.orange, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text("Tap to fix ›", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.orange)
        }
    }
}

private fun hasNotifPermission(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
    else androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
