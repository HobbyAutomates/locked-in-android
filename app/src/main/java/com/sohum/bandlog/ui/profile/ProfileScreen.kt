package com.sohum.bandlog.ui.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sohum.bandlog.BuildConfig
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.UpdateViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chevron
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ScaleIcon
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.TargetIcon
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.ThemeMode

/** Where a Profile row can take you. Rendered as full-screen pages by MainActivity. */
enum class ProfilePage { PERSONAL, GOALS, GOAL_WEIGHT, REMINDERS, WEIGHT_HISTORY }

private const val INVITE_TEXT =
    "Locked In — workouts, meals by voice, label scanner. " +
        "Android: https://evizkfvltacrfngsgbuu.supabase.co/storage/v1/object/public/app/LockedIn-11.apk · " +
        "iPhone: https://web-production-ff1cf.up.railway.app (Safari → Add to Home Screen)"

/**
 * The Profile tab, laid out like Cal AI's: identity card, invite banner, then Account,
 * Goals & Tracking and Support groups. Every row either edits in place or pushes a sub-page.
 */
@Composable
fun ProfileScreen(
    vm: AppViewModel,
    updateVm: UpdateViewModel,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOpen: (ProfilePage) -> Unit,
) {
    val p = palette
    val ctx = LocalContext.current
    val prof = vm.profile
    var showRings by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 12.dp, 16.dp, 110.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Rise(0) { ScreenTitle("Profile") }
        Rise(1) { ErrorNote(vm.error) }

        // ---- identity ----
        Rise(1) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            (prof.name.ifBlank { Session.email ?: "?" }).take(1).uppercase(),
                            fontSize = 22.sp, fontWeight = FontWeight(700), color = p.ink,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        NameField(prof.name) { newName ->
                            vm.launch { vm.saveProfile(prof.copy(name = newName)) }
                        }
                        Text(
                            prof.age?.let { "$it years old" } ?: (Session.email ?: "—"),
                            fontSize = 13.sp, color = p.muted,
                        )
                    }
                }
            }
        }

        // ---- invite ----
        Rise(2) {
            Card(onClick = {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, INVITE_TEXT)
                            },
                            "Invite friends",
                        ),
                    )
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(p.orangeBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Share, null, tint = p.orange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Invite friends", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("Send them the app — Android APK or the iPhone web app", fontSize = 12.sp, color = p.muted)
                    }
                    Chevron()
                }
            }
        }

        // ---- account ----
        Rise(3) { GroupLabel("Account") }
        Rise(3) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.Person, p.ink, "Personal details", onClick = { onOpen(ProfilePage.PERSONAL) }) { Chevron() }
                    Hair()
                    SettingRow(Icons.Outlined.Tune, p.ink, "Preferences", subtitle = "Appearance, Health Connect, scans, burned calories, groups") { }
                    Hair()
                    PreferencesRows(vm, themeMode, onThemeMode)
                }
            }
        }

        // ---- goals & tracking ----
        Rise(4) { GroupLabel("Goals & tracking") }
        Rise(4) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(TargetIcon, p.ink, "Edit Nutrition Goals", onClick = { onOpen(ProfilePage.GOALS) }) {
                        Text("${prof.calorieTarget} kcal", fontSize = 13.sp, color = p.muted)
                    }
                    Hair()
                    SettingRow(FlameIcon, p.flame, "Goal & current weight", onClick = { onOpen(ProfilePage.GOAL_WEIGHT) }) {
                        Text(
                            prof.goalType.replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp, color = p.muted,
                        )
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Notifications, p.ink, "Tracking Reminders", onClick = { onOpen(ProfilePage.REMINDERS) }) {
                        val on = com.sohum.bandlog.util.Reminders.load(ctx).values.count { it.on }
                        Text(if (on == 0) "Off" else "$on on", fontSize = 13.sp, fontWeight = FontWeight(600), color = if (on == 0) p.muted else p.green)
                    }
                    Hair()
                    SettingRow(ScaleIcon, p.ink, "Weight history", onClick = { onOpen(ProfilePage.WEIGHT_HISTORY) }) {
                        Text(prof.weightKg?.let { "${com.sohum.bandlog.ui.today.fmt(it)} kg" } ?: "—", fontSize = 13.sp, color = p.muted)
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Palette, p.ink, "Ring colours explained", onClick = { showRings = true }) { Chevron() }
                }
            }
        }

        // ---- support ----
        Rise(5) { GroupLabel("Support") }
        Rise(5) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.MailOutline, p.ink, "Request a feature", onClick = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:sohumai.team@gmail.com")
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Locked In — feature request")
                                },
                            )
                        }
                    }) { Chevron() }
                    Hair()
                    SettingRow(Icons.Outlined.Refresh, p.ink, "Check for updates", onClick = { updateVm.check() }) {
                        Text(
                            when {
                                updateVm.checking -> "Checking…"
                                updateVm.upToDate -> "v${BuildConfig.VERSION_NAME} · up to date"
                                else -> "v${BuildConfig.VERSION_NAME}"
                            },
                            fontSize = 13.sp, fontWeight = FontWeight(600), color = if (updateVm.upToDate) p.green else p.muted,
                        )
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Logout, p.red, "Sign out", onClick = { vm.signOut() }) { Chevron() }
                }
            }
        }
    }

    if (showRings) RingColoursSheet { showRings = false }
}

/** Appearance + Health Connect + burned-back, grouped under Preferences. */
@Composable
private fun PreferencesRows(vm: AppViewModel, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    SettingRow(Icons.Outlined.DarkMode, p.ink, "Appearance") {
        Row(Modifier.background(p.card2, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeMode.entries.forEach { m ->
                val sel = m == themeMode
                Box(
                    Modifier.height(28.dp).background(if (sel) p.btn else Color.Transparent, CircleShape)
                        .clickable { onThemeMode(m) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.btnInk else p.muted)
                }
            }
        }
    }
    Hair()
    val healthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshHealth(ctx) }
    val hcAvailable = remember { com.sohum.bandlog.util.Health.available(ctx) }
    SettingRow(Icons.Outlined.MonitorHeart, p.red, "Health Connect", onClick = {
        if (!hcAvailable) runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }
        else healthLauncher.launch(com.sohum.bandlog.util.Health.PERMISSIONS)
    }) {
        Text(
            when { !hcAvailable -> "Install"; vm.healthConnected -> "Connected"; else -> "Connect" },
            fontSize = 13.sp, fontWeight = FontWeight(600), color = if (vm.healthConnected) p.green else p.muted,
        )
    }
    Hair()
    SettingRow(FlameIcon, p.ink, "Add burned calories back") {
        androidx.compose.material3.Switch(
            vm.addBurnedBack,
            { vm.addBurnedBack = it; com.sohum.bandlog.util.ThemePrefs.setBurned(ctx, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk),
        )
    }
    Hair()
    // "Judge scans for": the lens every scan report opens on. Saved straight to profiles.lens_default.
    val prof = vm.profile
    Column(Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(com.sohum.bandlog.ui.components.ScanIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Judge scans for", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                Text("“My goal” follows Lose → Cutting, Gain → Bulking", fontSize = 11.sp, color = p.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("protein" to "Protein", "goal" to "My goal", "snack" to "Snack").forEach { (key, label) ->
                com.sohum.bandlog.ui.components.SmallChip(label, { vm.launch { vm.saveProfile(prof.copy(lensDefault = key)) } }, filled = prof.lensDefault == key)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("cutting" to "Cutting", "bulking" to "Bulking").forEach { (key, label) ->
                com.sohum.bandlog.ui.components.SmallChip(label, { vm.launch { vm.saveProfile(prof.copy(lensDefault = key)) } }, filled = prof.lensDefault == key)
            }
        }
    }
    Hair()
    SettingRow(Icons.Outlined.Share, p.ink, "Share with groups", subtitle = (if (prof.shareStats) "Streaks + protein & calories" else "Streaks only") + " · Groups are coming next") {
        androidx.compose.material3.Switch(
            prof.shareStats,
            { on -> vm.launch { vm.saveProfile(prof.copy(shareStats = on)) } },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk),
        )
    }
}

/** Inline-editable display name, shown as "Enter your name ✏️" while empty. */
@Composable
private fun NameField(name: String, onCommit: (String) -> Unit) {
    val p = palette
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var text by remember(name) { mutableStateOf(name) }
    var hadFocus by remember { mutableStateOf(false) }
    fun commit() { if (text.trim() != name) onCommit(text.trim()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { commit(); focus.clearFocus() }),
            textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink),
            cursorBrush = SolidColor(p.ink),
            modifier = Modifier.weight(1f, fill = false).onFocusChanged { st ->
                if (hadFocus && !st.isFocused) commit()
                hadFocus = st.isFocused
            },
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Enter your name", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.muted)
                inner()
            },
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            com.sohum.bandlog.ui.components.PencilIcon, "Save name", tint = if (text != name) p.green else p.muted,
            modifier = Modifier.size(16.dp).clickable { if (text != name) onCommit(text.trim()) },
        )
    }
}

/** The little legend behind every ring in the app. */
@Composable
private fun RingColoursSheet(onDismiss: () -> Unit) {
    val p = palette
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(28.dp)).padding(24.dp)) {
            Text("Ring colours", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
            Spacer(Modifier.height(4.dp))
            Text("What each ring on the home screen is counting.", fontSize = 13.sp, color = p.muted)
            Spacer(Modifier.height(16.dp))
            listOf(
                p.ink to "Calories — everything you ate today against your target",
                p.red to "Protein — the macro that protects muscle on a cut",
                p.orange to "Carbs — the remainder after protein and fat",
                p.blue to "Fat — 25% of your calories by default",
                p.green to "Green — a day you trained, on the week strip and calendar",
            ).forEach { (c, label) ->
                Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(c, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            PillButton("Got it", onDismiss)
        }
    }
}
