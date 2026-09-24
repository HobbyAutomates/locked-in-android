package com.sohum.bandlog.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import com.sohum.bandlog.ui.components.BottomSheet
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ScaleIcon
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.SettingRow
import com.sohum.bandlog.ui.components.TargetIcon
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.ThemeMode
import com.sohum.bandlog.util.Images
import com.sohum.bandlog.util.Names
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.primeImageCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a Profile row can take you. Rendered as full-screen pages by MainActivity. */
enum class ProfilePage { PERSONAL, GOALS, GOAL_WEIGHT, REMINDERS, WEIGHT_HISTORY }

private const val INVITE_TEXT =
    "Locked In — workouts, meals by voice, label scanner. " +
        "Android: https://evizkfvltacrfngsgbuu.supabase.co/storage/v1/object/public/app/LockedIn-14.apk · " +
        "iPhone: https://web-production-ff1cf.up.railway.app (Safari → Add to Home Screen)"

/**
 * v2.1 Profile: three cards. **You** (name, age, details, goals, weight), **Preferences**
 * (appearance, scans, squads, burned calories, reminders, Health Connect) and **App** (version,
 * invite, feature requests, ring colours, sign out). Rows edit in place, open a sheet, or push a page.
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
    var showLens by remember { mutableStateOf(false) }
    var avatarSheet by remember { mutableStateOf(false) }
    var avatarBusy by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val displayName = Names.display(prof.name, Session.email)

    // Avatar: center-crop square, <=512 px, JPEG q85 -> avatars/<uid>/avatar.jpg (upsert) ->
    // profiles.avatar_path = "<uid>/avatar.jpg?v=<millis>" (the ?v busts every cache).
    fun uploadAvatar(src: android.graphics.Bitmap?) {
        if (src == null) { avatarError = "Couldn't open that photo"; return }
        scope.launch {
            avatarBusy = true; avatarError = null
            try {
                val square = withContext(Dispatchers.Default) { Images.squareCrop(src, 512) }
                val bytes = withContext(Dispatchers.Default) { Images.jpeg(square, 85) }
                val path = Api.uploadAvatar(bytes)
                Api.avatarUrl(path)?.let { primeImageCache(it, square) }
                if (!vm.saveProfile(vm.profile.copy(avatarPath = path))) avatarError = vm.error ?: "Couldn't save your photo"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("LockedIn", "Avatar upload failed", e); avatarError = e.message ?: "Upload failed" }
            finally { avatarBusy = false }
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { uploadAvatar(withContext(Dispatchers.IO) { runCatching { com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 1400) }.getOrNull() }) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) uploadAvatar(bmp) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 12.dp, 16.dp, 110.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Rise(0) { ScreenTitle("Profile") }
        Rise(1) { ErrorNote(vm.error) }

        // ---- you ----
        Rise(1) { GroupLabel("You") }
        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape).clickable(enabled = !avatarBusy) { avatarSheet = true }, contentAlignment = Alignment.Center) {
                            Avatar(Api.avatarUrl(prof.avatarPath), Names.initials(displayName), 52.dp)
                            if (avatarBusy) Box(Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            NameField(prof.name, placeholder = Names.nameFromEmail(Session.email).ifBlank { "Enter your name" }) { newName -> vm.launch { vm.saveProfile(vm.profile.copy(name = newName)) } }
                            Session.email?.let { Text(it, fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                            prof.age?.let { Text("$it years old", fontSize = 12.sp, color = p.muted) }
                        }
                    }
                    avatarError?.let { ErrorNote(it, Modifier.padding(bottom = 10.dp)) }
                    Hair()
                    SettingRow(Icons.Outlined.Person, p.ink, "Personal details", onClick = { onOpen(ProfilePage.PERSONAL) }) { Chevron() }
                    Hair()
                    SettingRow(TargetIcon, p.ink, "Nutrition goals", onClick = { onOpen(ProfilePage.GOALS) }) {
                        Text("${prof.calorieTarget} kcal", fontSize = 13.sp, color = p.muted)
                    }
                    Hair()
                    SettingRow(FlameIcon, p.flame, "Goal & weight", onClick = { onOpen(ProfilePage.GOAL_WEIGHT) }) {
                        Text(prof.goalType.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, color = p.muted)
                    }
                    Hair()
                    SettingRow(ScaleIcon, p.ink, "Weight history", onClick = { onOpen(ProfilePage.WEIGHT_HISTORY) }) {
                        Text(prof.weightKg?.let { "${com.sohum.bandlog.ui.today.fmt(it)} kg" } ?: "—", fontSize = 13.sp, color = p.muted)
                    }
                }
            }
        }

        // ---- preferences ----
        Rise(2) { GroupLabel("Preferences") }
        Rise(2) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PreferencesRows(vm, themeMode, onThemeMode, onLens = { showLens = true }, onReminders = { onOpen(ProfilePage.REMINDERS) })
                }
            }
        }

        // ---- app ----
        Rise(3) { GroupLabel("App") }
        Rise(3) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingRow(Icons.Outlined.Refresh, p.ink, "Version", subtitle = "Tap to check for updates", onClick = { updateVm.check() }) {
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
                    SettingRow(Icons.Outlined.Share, p.ink, "Invite friends", subtitle = "Android APK or the iPhone web app", onClick = {
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
                    }) { Chevron() }
                    Hair()
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
                    SettingRow(Icons.Outlined.Palette, p.ink, "Ring colours explained", onClick = { showRings = true }) { Chevron() }
                    Hair()
                    SettingRow(Icons.Outlined.Logout, p.red, "Sign out", onClick = { vm.signOut() }) { Chevron() }
                }
            }
        }
    }

    if (showRings) RingColoursSheet { showRings = false }
    if (avatarSheet) BottomSheet(title = "Profile photo", subtitle = "Shows on your profile and your squads' boards", onDismiss = { avatarSheet = false }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
            avatarSheet = false
            pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(com.sohum.bandlog.ui.components.ScanIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Choose photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
        }
        Hair()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
            avatarSheet = false
            runCatching { takePhoto.launch(null) }.onFailure { avatarError = "No camera app found - choose a photo instead." }
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(com.sohum.bandlog.ui.components.CameraIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Take photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
        }
        if (prof.avatarPath != null) {
            Hair()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                avatarSheet = false
                vm.launch { if (!vm.saveProfile(vm.profile.copy(avatarPath = null))) avatarError = vm.error }
            }, verticalAlignment = Alignment.CenterVertically) {
                Icon(com.sohum.bandlog.ui.components.CrossIcon, null, tint = p.red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("Remove photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.red)
            }
        }
    }
    if (showLens) LensSheet(prof.lensDefault, onPick = { key -> showLens = false; vm.launch { vm.saveProfile(prof.copy(lensDefault = key)) } }) { showLens = false }
}

private val LENS_OPTIONS = listOf(
    "protein" to ("Protein" to "How much protein it gives you"),
    "goal" to ("My goal" to "Follows your goal: Lose → Cutting, Gain → Bulking"),
    "snack" to ("Snack" to "Is it a decent snack"),
    "cutting" to ("Cutting" to "Fits a calorie deficit"),
    "bulking" to ("Bulking" to "Helps a surplus"),
)

/** Appearance, scans, squads, burned calories, reminders and Health Connect, grouped under Preferences. */
@Composable
private fun PreferencesRows(vm: AppViewModel, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit, onLens: () -> Unit, onReminders: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val prof = vm.profile
    SettingRow(Icons.Outlined.DarkMode, p.ink, "Appearance") {
        Row(Modifier.background(p.card2, CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ThemeMode.entries.forEach { m ->
                val sel = m == themeMode
                Box(
                    Modifier.height(36.dp).background(if (sel) p.btn else Color.Transparent, CircleShape)
                        .clickable { onThemeMode(m) }.padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.btnInk else p.muted)
                }
            }
        }
    }
    Hair()
    // "Judge scans for": the lens every scan report opens on. Saved straight to profiles.lens_default.
    SettingRow(com.sohum.bandlog.ui.components.ScanIcon, p.ink, "Judge scans for", onClick = onLens) {
        Text(LENS_OPTIONS.firstOrNull { it.first == prof.lensDefault }?.second?.first ?: "Protein", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
    }
    Hair()
    SettingRow(Icons.Outlined.Share, p.ink, "Share with squads", subtitle = if (prof.shareStats) "Streaks + protein & calories" else "Streaks only") {
        androidx.compose.material3.Switch(
            prof.shareStats,
            { on -> vm.launch { vm.saveProfile(prof.copy(shareStats = on)) } },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
        )
    }
    Hair()
    SettingRow(FlameIcon, p.ink, "Add burned calories back", subtitle = "Exercise raises today's calorie budget") {
        androidx.compose.material3.Switch(
            vm.addBurnedBack,
            { vm.addBurnedBack = it; com.sohum.bandlog.util.ThemePrefs.setBurned(ctx, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
        )
    }
    Hair()
    SettingRow(Icons.Outlined.Notifications, p.ink, "Reminders", onClick = onReminders) {
        val on = com.sohum.bandlog.util.Reminders.load(ctx).values.count { it.on }
        Text(if (on == 0) "Off" else "$on on", fontSize = 13.sp, fontWeight = FontWeight(600), color = if (on == 0) p.muted else p.green)
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
}

/** Which lens scan reports open on. One tap picks and closes. */
@Composable
private fun LensSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = palette
    BottomSheet(title = "Judge scans for", subtitle = "Where every scan report starts — you can switch lens on the report too", onDismiss = onDismiss) {
        LENS_OPTIONS.forEachIndexed { i, (key, lt) ->
            if (i > 0) Hair()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(key) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lt.first, fontSize = 15.sp, fontWeight = FontWeight(if (key == current) 700 else 500), color = p.ink)
                    Text(lt.second, fontSize = 12.sp, color = p.muted)
                }
                if (key == current) Icon(com.sohum.bandlog.ui.components.CheckIcon, null, tint = p.ink, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Inline-editable display name, shown as "Enter your name ✏️" while empty. */
@Composable
private fun NameField(name: String, placeholder: String = "Enter your name", onCommit: (String) -> Unit) {
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
                if (text.isEmpty()) Text(placeholder, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.muted)
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

/** The little legend behind every ring in the app, on the shared bottom sheet. */
@Composable
private fun RingColoursSheet(onDismiss: () -> Unit) {
    val p = palette
    BottomSheet(title = "Ring colours", subtitle = "What each ring on Home is counting", onDismiss = onDismiss, primary = "Got it", onPrimary = onDismiss) {
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
    }
}
