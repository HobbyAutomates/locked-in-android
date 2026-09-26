package com.sohum.bandlog.ui.platform

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.alarm.ProteinNudgeAlarms
import com.sohum.bandlog.data.PlatformApi
import com.sohum.bandlog.notify.PlatformNotifications
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chevron
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.PlatformPrefs
import com.sohum.bandlog.util.ProteinNudge
import kotlinx.coroutines.launch

/**
 * v2.13 Preferences → Notifications: whether this phone may show notifications (the API 33+
 * permission), the afternoon protein nudge (on/off + time, spec §3) and the inbox. The protein
 * settings live on the profile once schema_v36 is applied; until then they're kept on this phone.
 */
@Composable
fun NotificationsScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(PlatformNotifications.permitted(ctx)) }
    var local by remember { mutableStateOf(PlatformPrefs.proteinNudge(ctx)) }
    var note by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = PlatformNotifications.permitted(ctx) }
    LaunchedEffect(Unit) { pvm.loadPlan() }
    // The server's copy wins once it exists; mirror it locally so the alarm uses it.
    LaunchedEffect(pvm.extras) {
        val e = pvm.extras ?: return@LaunchedEffect
        if (e.proteinNudge != null) {
            val server = PlatformPrefs.Nudge(e.proteinNudge, e.proteinNudgeTime ?: ProteinNudge.DEFAULT_TIME)
            if (server != local) { local = server; PlatformPrefs.setProteinNudge(ctx, server); ProteinNudgeAlarms.reschedule(ctx) }
        }
    }
    val teenLoss = Goals.isTeen(vm.profile.age) && vm.profile.goalType == "lose"

    fun save(n: PlatformPrefs.Nudge) {
        local = n
        PlatformPrefs.setProteinNudge(ctx, n)
        ProteinNudgeAlarms.reschedule(ctx)
        if (n.on && !permitted) PlatformNav.askNotificationsTick++
        scope.launch {
            val synced = PlatformApi.saveProteinNudge(n.on, n.time)
            note = if (synced) null else "Saved on this phone. It syncs to your account after the next update."
        }
    }

    SubPage("Notifications", onBack) {
        GroupLabel("This phone")
        Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(
                    LineIcons.Bell, p.ink, "Allow notifications",
                    subtitle = if (permitted) "On: nudges, reminders and your rest timer" else "Off: you'll miss squad nudges and reminders",
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && !permitted) ask.launch("android.permission.POST_NOTIFICATIONS")
                        else runCatching { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                ) { Text(if (permitted) "On" else "Turn on", fontSize = 13.sp, fontWeight = FontWeight(600), color = if (permitted) p.green else p.red) }
            }
        }

        GroupLabel("Protein nudge")
        Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(
                    label = "Afternoon protein check",
                    subtitle = if (teenLoss) "Off for under-18s with a loss goal" else "If you've logged food but are under 70 % of your protein",
                ) {
                    Switch(
                        checked = local.on && !teenLoss, enabled = !teenLoss, onCheckedChange = { save(local.copy(on = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
                    )
                }
                Hair()
                SettingRow(label = "Time", subtitle = "At most once a day", onClick = {
                    val (h, m) = ProteinNudge.parseTime(local.time)
                    android.app.TimePickerDialog(ctx, { _, hh, mm -> save(local.copy(time = ProteinNudge.formatTime(hh, mm))) }, h, m, false).show()
                }) {
                    androidx.compose.foundation.layout.Row {
                        Text(local.time, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted)
                        Text("  ", fontSize = 14.sp)
                        Chevron()
                    }
                }
            }
        }
        note?.let { Text(it, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp)) }

        GroupLabel("Inbox")
        Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(LineIcons.Message, p.ink, "Notification inbox", subtitle = "Nudges and reminders you've had", onClick = { PlatformNav.open(PlatformPage.INBOX) }) { Chevron() }
            }
        }
        Text(
            "This phone checks for squad nudges every 15 minutes and whenever you open the app.",
            fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
