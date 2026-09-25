package com.sohum.bandlog.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Straighten
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.Chevron
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.GlassIcon
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ScanFilledIcon
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.StepsIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Names
import com.sohum.bandlog.util.ThemeMode
import kotlinx.coroutines.launch

/** v2.4: Profile → Preferences is one row; each category below opens its own page. */
@Composable
fun PreferencesScreen(vm: AppViewModel, themeMode: ThemeMode, onBack: () -> Unit, onOpen: (ProfilePage) -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val prof = vm.profile
    SubPage("Preferences", onBack) {
        Rise(0) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.DarkMode, p.ink, "Appearance", subtitle = "Theme, celebrations, ring colours", onClick = { onOpen(ProfilePage.APPEARANCE) }) {
                        PrefValue(themeLabel(themeMode))
                    }
                    Hair()
                    SettingRow(StepsIcon, p.ink, "Tracking", subtitle = "Water & step goals, burned calories, scans", onClick = { onOpen(ProfilePage.TRACKING) }) { Chevron() }
                    Hair()
                    SettingRow(Icons.Outlined.Notifications, p.ink, "Reminders", subtitle = "Meals, water, workouts", onClick = { onOpen(ProfilePage.REMINDERS) }) {
                        val on = remember(prof.remindersJson) { com.sohum.bandlog.util.Reminders.load(ctx).values.count { it.on } }
                        PrefValue(if (on == 0) "Off" else "$on on", if (on == 0) p.muted else p.green)
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Lock, p.ink, "Privacy", subtitle = "Squad sharing: what your squads see", onClick = { onOpen(ProfilePage.PRIVACY) }) {
                        PrefValue(if (prof.shareStats) "Sharing stats" else "Streaks only")
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Person, p.ink, "Account", subtitle = Session.email ?: "Email, name, sign out", onClick = { onOpen(ProfilePage.ACCOUNT) }) { Chevron() }
                }
            }
        }
    }
}

@Composable
private fun PrefValue(text: String, color: Color = palette.muted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight(600), color = color, maxLines = 1)
        Text("  ", fontSize = 13.sp)
        Chevron()
    }
}

private fun themeLabel(m: ThemeMode) = when (m) { ThemeMode.AUTO -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }

@Composable
private fun switchColors() = palette.let { p ->
    SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair)
}

/** Appearance: Light / Dark / System, the streak pop-up, and the ring legend. */
@Composable
fun AppearanceScreen(vm: AppViewModel, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    var showRings by remember { mutableStateOf(false) }
    SubPage("Appearance", onBack) {
        Rise(0) { GroupLabel("Theme") }
        Rise(0) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO).forEachIndexed { i, m ->
                        if (i > 0) Hair()
                        SettingRow(
                            label = themeLabel(m),
                            subtitle = if (m == ThemeMode.AUTO) "Follows your phone" else null,
                            onClick = { onThemeMode(m) },
                        ) { if (m == themeMode) androidx.compose.material3.Icon(CheckIcon, "Selected", tint = p.ink, modifier = Modifier.height(18.dp)) }
                    }
                }
            }
        }
        Rise(1) { GroupLabel("Extras") }
        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(CheckIcon, p.ink, "Badge celebrations", subtitle = "Streak pop-up after a workout") {
                        Switch(vm.celebrationsOn, { vm.celebrationsOn = it; com.sohum.bandlog.util.ThemePrefs.setCelebrations(ctx, it) }, colors = switchColors())
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Palette, p.ink, "Ring colours explained", onClick = { showRings = true }) { Chevron() }
                }
            }
        }
    }
    if (showRings) RingColoursSheet { showRings = false }
}

/** Tracking: daily goals, how calories are counted, how scans are judged, units, Health Connect. */
@Composable
fun TrackingScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prof = vm.profile
    var water by remember(prof.waterGoalMl) { mutableStateOf(prof.waterGoalMl.toString()) }
    var steps by remember(prof.stepGoal) { mutableStateOf(prof.stepGoal.toString()) }
    var busy by remember { mutableStateOf(false) }
    var showLens by remember { mutableStateOf(false) }
    val nextWater = water.toIntOrNull()?.coerceIn(250, 10_000) ?: prof.waterGoalMl
    val nextSteps = steps.toIntOrNull()?.coerceIn(500, 100_000) ?: prof.stepGoal
    val dirty = nextWater != prof.waterGoalMl || nextSteps != prof.stepGoal
    fun saveGoals() {
        if (!dirty || busy) return
        scope.launch { busy = true; vm.saveProfile(vm.profile.copy(waterGoalMl = nextWater, stepGoal = nextSteps)); busy = false }
    }

    val healthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshHealth(ctx) }
    val hcAvailable = remember { com.sohum.bandlog.util.Health.available(ctx) }

    SubPage("Tracking", onBack) {
        Rise(0) { ErrorNote(vm.error) }
        Rise(0) { GroupLabel("Daily goals") }
        Rise(0) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(GlassIcon, com.sohum.bandlog.ui.today.WaterBlue, "Water goal") {
                        NumberField(water, { water = it.filter(Char::isDigit).take(5) }, "mL", onDone = { saveGoals() })
                    }
                    Hair()
                    SettingRow(StepsIcon, p.green, "Step goal") {
                        NumberField(steps, { steps = it.filter(Char::isDigit).take(6) }, "steps", onDone = { saveGoals() })
                    }
                }
            }
        }
        if (dirty) Rise(0) { PillButton(if (busy) "Saving…" else "Save goals", { saveGoals() }, enabled = !busy, height = 48.dp) }

        Rise(1) { GroupLabel("Calories") }
        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(FlameIcon, p.flame, "Add burned calories to goal", subtitle = "Exercise raises today's calorie budget") {
                        Switch(vm.addBurnedBack, { vm.setAddBurned(ctx, it) }, colors = switchColors())
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Refresh, p.ink, "Rollover calories", subtitle = "Up to 200 unused from yesterday") {
                        Switch(vm.rolloverOn, { vm.setRollover(ctx, it) }, colors = switchColors())
                    }
                    Hair()
                    // v2.10: opt-in only. hideNumbers null = schema_v34 not applied yet, so the switch waits.
                    SettingRow(
                        com.sohum.bandlog.ui.components.LockIcon, p.ink, "Hide calorie numbers",
                        subtitle = if (vm.profile.hideNumbers == null) "Coming with the next update" else "Show progress bars and words instead of kcal",
                    ) {
                        Switch(vm.profile.hideNumbers == true, { vm.setHideNumbers(it) }, enabled = vm.profile.hideNumbers != null, colors = switchColors())
                    }
                }
            }
        }

        Rise(2) { GroupLabel("Scans & units") }
        Rise(2) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    // The lens every scan report opens on. Saved straight to profiles.lens_default.
                    SettingRow(ScanFilledIcon, p.ink, "Default lens", subtitle = "What scan reports judge for first", onClick = { showLens = true }) {
                        PrefValue(LENS_OPTIONS.firstOrNull { it.first == prof.lensDefault }?.second?.first ?: "Protein")
                    }
                    Hair()
                    SettingRow(Icons.Outlined.Straighten, p.ink, "Units", subtitle = "Weight, height, water, food") {
                        Text("Metric · kg, cm, mL, g", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
            }
        }

        Rise(3) { GroupLabel("Connected") }
        Rise(3) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.MonitorHeart, p.red, "Health Connect", subtitle = "Steps and active calories", onClick = {
                        if (!hcAvailable) runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"))) }
                        else healthLauncher.launch(com.sohum.bandlog.util.Health.PERMISSIONS)
                    }) {
                        Text(
                            when { !hcAvailable -> "Install"; vm.healthConnected -> "Connected"; else -> "Connect" },
                            fontSize = 13.sp, fontWeight = FontWeight(600), color = if (vm.healthConnected) p.green else p.muted,
                        )
                    }
                }
            }
        }
    }
    if (showLens) LensSheet(prof.lensDefault, onPick = { key -> showLens = false; vm.launch { vm.saveProfile(vm.profile.copy(lensDefault = key)) } }) { showLens = false }
}

/**
 * Privacy → Squad sharing (v2.9): one card. "Share my calories & protein" is share_stats (off =
 * streaks only, and nothing auto-posts, as before); the three auto-post switches are
 * profiles.auto_share and show once schema_v31 is in (autoShare null = not yet). Per-squad mutes
 * live on each squad's info page.
 */
@Composable
fun PrivacyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val prof = vm.profile
    SubPage("Privacy", onBack) {
        Rise(0) { ErrorNote(vm.error) }
        Rise(0) { GroupLabel("Squad sharing") }
        Rise(0) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.Share, p.ink, "Share my calories & protein", subtitle = if (prof.shareStats) "Squads see today's calories & protein" else "Streaks only, and nothing auto-posts") {
                        Switch(prof.shareStats, { on -> vm.launch { vm.saveProfile(vm.profile.copy(shareStats = on)) } }, colors = switchColors())
                    }
                    prof.autoShare?.let { kinds ->
                        com.sohum.bandlog.util.SquadSharing.KINDS.forEach { k ->
                            Hair()
                            val icon = when (k) { "meal" -> com.sohum.bandlog.ui.components.BowlIcon; "workout" -> com.sohum.bandlog.ui.components.DumbbellIcon; else -> FlameIcon }
                            SettingRow(icon, p.ink, com.sohum.bandlog.util.SquadSharing.LABELS[k].orEmpty(), subtitle = if (prof.shareStats) null else "Off while you share streaks only") {
                                Switch(prof.shareStats && k in kinds, { on -> vm.setAutoShare(k, on) }, enabled = prof.shareStats, colors = switchColors())
                            }
                        }
                    }
                }
            }
        }
        Rise(1) {
            Text(
                "Your squads always see your name, photo and streak. Auto-posts put what you log in each squad's Feed: meal names with kcal, workouts and gym PRs. To stop posting in one squad, open it, tap its name and turn off \"Auto-post my logs here\". Weight and scans are never shared.",
                fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Account: who you're signed in as, your name, sign out, and a data-deletion request. */
@Composable
fun AccountScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val prof = vm.profile
    SubPage("Account", onBack) {
        Rise(0) { ErrorNote(vm.error) }
        Rise(0) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.MailOutline, p.ink, "Email") {
                        Text(Session.email ?: "—", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                    }
                    Hair()
                    Row(Modifier.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Outlined.Person, null, tint = p.ink, modifier = Modifier.height(20.dp))
                        Box(Modifier.padding(start = 10.dp).weight(1f)) {
                            NameField(prof.name, placeholder = Names.nameFromEmail(Session.email).ifBlank { "Enter your name" }) { newName -> vm.launch { vm.saveProfile(vm.profile.copy(name = newName)) } }
                        }
                    }
                }
            }
        }
        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.Logout, p.ink, "Sign out", onClick = { vm.signOut() }) { Chevron() }
                    Hair()
                    SettingRow(Icons.Outlined.DeleteOutline, p.red, "Delete my data", subtitle = "Emails Sohum to erase your account and logs", onClick = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:sohumai.team@gmail.com")
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Locked In — delete my data")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Please delete my Locked In account and all my logs.\n\nAccount: ${Session.email.orEmpty()}")
                                },
                            )
                        }
                    }) { Chevron() }
                }
            }
        }
    }
}

/** "What's new": the last few releases, newest first. */
@Composable
internal fun ChangelogSheet(onDismiss: () -> Unit) {
    val p = palette
    com.sohum.bandlog.ui.components.BottomSheet(title = "What's new", subtitle = "Recent Locked In releases", onDismiss = onDismiss, primary = "Got it", onPrimary = onDismiss) {
        CHANGELOG.forEachIndexed { i, (ver, lines) ->
            if (i > 0) Hair()
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("v$ver", fontSize = 14.sp, fontWeight = FontWeight(800), color = p.ink)
                lines.forEach { Text("•  $it", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp) }
            }
        }
    }
}

private val CHANGELOG = listOf(
    "2.9" to listOf("Deleting a meal or workout also removes its squad posts; delete your own posts with a long-press", "Choose what auto-posts to squads in Privacy → Squad sharing, and mute any squad", "Not sure which roti? Pick the right one, and tap ⓘ to see where the numbers come from"),
    "2.8" to listOf("Home groups your food into Breakfast, Lunch, Dinner and Snacks", "Tap anything you logged to edit it: meals, activities, water, weigh-ins", "One Log activity button, one-tap water, simpler Progress, gram ranges on photo scans"),
    "2.7" to listOf("Squad Challenges: train days, protein days or log every day, with a ranked board and 🏆 when you finish", "Squad Food Battle: a daily crown for whoever eats closest to their own goal", "Fixes: editing grams on a scan keeps the macros, \"4 idli\" logs 4, deletes can be undone, sharper label and barcode scans"),
    "2.6" to listOf("Water page with a filling bottle, vessels and reminders", "Squads v2: usernames, photos, invites, Chat · Feed · Leaderboard"),
    "2.5" to listOf("One big stepper for amounts", "Milk types, Gym and Bodyweight workouts"),
    "2.4" to listOf("Real food pictures on presets, search, your plate and meals", "Scanning has its own tab; Log 1 serving opens Add food", "Preferences sorted into categories"),
    "2.3" to listOf("Google Fit-style exercise form", "Progress rebuilt, water tracking", "Discover squads, + button speed dial, home-screen widget"),
    "2.2" to listOf("Day streak, avatar, birthday and name", "Macro split, scan previews"),
    "2.1" to listOf("One Add-food screen, one Scan button, cleaner Home"),
)
