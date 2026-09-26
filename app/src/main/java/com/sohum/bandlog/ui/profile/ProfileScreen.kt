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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
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
import androidx.compose.material.icons.outlined.NewReleases
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.growFromLeft
import com.sohum.bandlog.ui.motion.popIn
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.progress.BadgeMedal
import com.sohum.bandlog.ui.progress.NextUpRow
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.progress.nextBadge
import com.sohum.bandlog.ui.progress.tierOf
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Reminders
import kotlin.math.roundToInt

/** Where a Profile row can take you. Rendered as full-screen pages by MainActivity. */
enum class ProfilePage { PERSONAL, GOALS, GOAL_WEIGHT, REMINDERS, WEIGHT_HISTORY, BADGES, PREFERENCES, APPEARANCE, TRACKING, PRIVACY, ACCOUNT, USERNAME }

private const val INVITE_TEXT =
    "Locked In — workouts, meals by voice, label scanner. " +
        "Android: https://evizkfvltacrfngsgbuu.supabase.co/storage/v1/object/public/app/LockedIn-18.apk · " +
        "iPhone: https://web-production-ff1cf.up.railway.app (Safari → Add to Home Screen)"

/**
 * v2.12 Profile (design canvas18 "ProfileFinal"): a monochrome weight-plates cover with settings
 * and share buttons on its edge, the avatar overlapping it, a "Founding member" chip, name and
 * "@username · Joined · squad", three dials (streak ticks, protein wave, weight arc), Edit profile
 * + Invite, the Medals v2 badge shelf, the goal card and one clean list. The warm accent is used
 * sparingly. Motion comes from ui/motion (sections rise one after another; dials draw in).
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
    val accent = accentColor
    var showChangelog by remember { mutableStateOf(false) }
    var avatarSheet by remember { mutableStateOf(false) }
    var editSheet by remember { mutableStateOf(false) }
    var avatarBusy by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val displayName = Names.display(prof.name, Session.email)

    // v2.7 Squad Food Battle: total crowns + last 7 wins (docs/food-battle-spec.md). Quiet (no card)
    // for anyone who's never won one — matches the web's GraffitiWall.tsx.
    var graffiti by remember { mutableStateOf<com.sohum.bandlog.data.BattleRepo.Graffiti?>(null) }
    // v2.12: the first squad's name for the "@username · Joined · squad" line (a read, like the board).
    var squadName by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Session.userId) {
        graffiti = runCatching { com.sohum.bandlog.data.BattleRepo.myGraffiti() }.getOrElse { com.sohum.bandlog.data.BattleRepo.Graffiti(0, emptyList()) }
        squadName = runCatching { Api.mySquads().firstOrNull()?.name }.getOrNull()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadBadgeTotals() }

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
    val invite: () -> Unit = {
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
    }

    // ---- numbers for the dials, goal card and rows ----
    val logged = remember(vm.workouts, vm.exercises, vm.meals) {
        (vm.workouts.map { it.date } + vm.exercises.map { it.date } + vm.meals.map { it.date }).toSet()
    }
    val streak = vm.dayStreak
    val bestStreak = maxOf(com.sohum.bandlog.util.Badges.longestDayRun(logged), streak)
    val proteinToday = com.sohum.bandlog.data.totalsFor(vm.meals, vm.today).protein.roundToInt()
    val current = vm.weights.firstOrNull()?.weightKg ?: prof.weightKg
    val startKg = vm.weights.lastOrNull()?.weightKg ?: current
    val goalKg = prof.goalWeightKg
    val goalFrac = if (goalKg == null || current == null || startKg == null) 0f else {
        val span = startKg - goalKg
        if (kotlin.math.abs(span) < 0.05) (if (kotlin.math.abs(current - goalKg) < 0.2) 1f else 0f) else ((startKg - current) / span).toFloat().coerceIn(0f, 1f)
    }
    val remindersOn = Reminders.parse(prof.remindersJson).count { it.value.on }
    val joined = prof.createdAt?.take(10)?.let { runCatching { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH)) }.getOrNull() }
    val isAdmin = Session.email?.trim()?.lowercase()?.let { e -> BuildConfig.ADMIN_EMAILS.split(",").map { it.trim().lowercase() }.contains(e) } == true

    MotionScreen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            // ---- cover, cover buttons, avatar ----
            Entrance(0, key = "cover") {
                Box(Modifier.fillMaxWidth().height(364.dp)) {
                    WeightPlatesCover(
                        Modifier.fillMaxWidth().height(300.dp)
                            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)),
                    )
                    CoverButton(LineIcons.Settings, "Preferences", { onOpen(ProfilePage.PREFERENCES) }, Modifier.align(Alignment.TopStart).padding(start = 34.dp, top = 274.dp))
                    CoverButton(LineIcons.Share, "Invite friends", invite, Modifier.align(Alignment.TopEnd).padding(end = 34.dp, top = 274.dp))
                    val pop = rememberMotion("avatar", 380, PremiumMotion.POP_MS)
                    Box(
                        Modifier.align(Alignment.TopCenter).padding(top = 252.dp).size(112.dp).popIn(pop)
                            .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                            .background(p.bg, CircleShape)
                            .border(1.dp, if (isDarkTheme) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.12f), CircleShape)
                            .clip(CircleShape).clickable(enabled = !avatarBusy, onClickLabel = "Change profile photo") { avatarSheet = true }
                            .semantics { contentDescription = "Profile photo, $displayName" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Avatar(Api.avatarUrl(prof.avatarPath), Names.initials(displayName), 102.dp)
                        if (avatarBusy) Box(Modifier.size(102.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ---- identity ----
                Entrance(2, key = "identity") {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Every current user is a founding member (v2.12 launch cohort).
                        Row(
                            Modifier.border(1.dp, accent, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(LineIcons.Star, null, tint = accent, modifier = Modifier.size(14.dp))
                            Text("Founding member", fontSize = 13.sp, fontWeight = FontWeight(500), letterSpacing = 0.3.sp, color = accent)
                        }
                        Text(
                            displayName.ifBlank { "Your name" }, fontSize = 30.sp, fontWeight = FontWeight(600), letterSpacing = (-0.8).sp, color = p.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                        val handle = when {
                            prof.username != null -> "@${prof.username}"
                            else -> null
                        }
                        val bits = listOfNotNull(handle, joined?.let { "Joined $it" }, squadName)
                        if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), fontSize = 14.sp, color = p.muted, textAlign = TextAlign.Center)
                        // v2.6: create the squad handle from here (username + photo flow).
                        if (prof.usernameSupported && prof.username == null) Text(
                            "Create your squad username ›", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.blue,
                            modifier = Modifier.heightIn(min = 48.dp).clickable { onOpen(ProfilePage.USERNAME) }.padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                        avatarError?.let { ErrorNote(it) }
                    }
                }
                ErrorNote(vm.error)

                // ---- dials ----
                Entrance(3, key = "dials") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StreakDial(streak, bestStreak)
                            DialLabel(LineIcons.Flame, "Streak")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProteinWaveDial(proteinToday, prof.proteinTargetG)
                            DialLabel(LineIcons.Drop, "Protein")
                        }
                        Column(
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = current?.let { "Weight ${fmt(it)} kilograms${goalKg?.let { g -> ", ${(goalFrac * 100).roundToInt()} percent of the way to ${fmt(g)}" } ?: ""}" } ?: "No weight logged"
                            },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            WeightArcDial(current?.let { fmt((it * 10).roundToInt() / 10.0) } ?: "—", goalFrac)
                            DialLabel(LineIcons.Balance, "Weight")
                        }
                    }
                }

                // ---- actions ----
                Entrance(4, key = "actions") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).height(52.dp).pressable().background(accent, RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Edit profile") { editSheet = true },
                            contentAlignment = Alignment.Center,
                        ) { Text("Edit profile", fontSize = 16.sp, fontWeight = FontWeight(600), color = Color.White) }
                        Row(
                            Modifier.weight(1f).height(52.dp).pressable().border(1.5.dp, accent, RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Invite friends", onClick = invite),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Invite", fontSize = 16.sp, fontWeight = FontWeight(600), color = accent)
                            Spacer(Modifier.width(8.dp))
                            Icon(LineIcons.Plus, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // ---- v2.7: graffiti wall (Squad Food Battle crowns) — hidden until the first win ----
                graffiti?.takeIf { it.total > 0 }?.let { g -> Entrance(5, key = "graffiti") { GraffitiWallCard(g) } }
                // v2.10: an under-18 "lose" goal moves to maintain here too, with its one-time card.
                com.sohum.bandlog.ui.components.TeenGoalMigration(vm)

                // ---- badges shelf ----
                Entrance(5, key = "badges") { BadgesShelf(vm) { onOpen(ProfilePage.BADGES) } }

                // ---- goal ----
                Entrance(6, key = "goal") {
                    ProfileCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Goal", fontSize = 14.sp, color = p.muted, modifier = Modifier.weight(1f))
                            Text(
                                "Edit", fontSize = 14.sp, fontWeight = FontWeight(500), color = accent,
                                modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Edit goal weight") { onOpen(ProfilePage.GOAL_WEIGHT) }.padding(horizontal = 8.dp, vertical = 14.dp),
                            )
                        }
                        if (goalKg != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(current?.let { fmt((it * 10).roundToInt() / 10.0) } ?: "—", fontSize = 30.sp, fontWeight = FontWeight(400), letterSpacing = (-1).sp, color = p.ink)
                                Spacer(Modifier.width(8.dp))
                                Text("→ ${fmt(goalKg)} kg${goalEtaShort(vm, current)}", fontSize = 15.sp, color = p.muted, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
                            }
                            Spacer(Modifier.height(10.dp))
                            val grow = rememberMotion("goal-bar", 1080, PremiumMotion.GROW_X_MS)
                            Box(Modifier.fillMaxWidth().height(6.dp).background(if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE7E7EA), RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(goalFrac.coerceAtLeast(0.02f)).height(6.dp).growFromLeft(grow).background(accent, RoundedCornerShape(3.dp)))
                            }
                        } else {
                            Text(
                                "Set a goal weight ›", fontSize = 17.sp, fontWeight = FontWeight(600), color = p.ink,
                                modifier = Modifier.heightIn(min = 48.dp).clickable { onOpen(ProfilePage.GOAL_WEIGHT) }.padding(vertical = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            if (prof.hideNumbers != true) GoalStat(String.format(java.util.Locale.US, "%,d", prof.calorieTarget), "kcal") { onOpen(ProfilePage.GOALS) }
                            GoalStat("${prof.proteinTargetG} g", "protein") { onOpen(ProfilePage.GOALS) }
                            GoalStat("$remindersOn", if (remindersOn == 1) "reminder" else "reminders") { onOpen(ProfilePage.REMINDERS) }
                        }
                    }
                }

                // ---- the list ----
                Entrance(7, key = "list") {
                    ProfileCard(padding = 0.dp) {
                        // v2.13 platform: Locked In Pro (everyone's on the beta, so every Pro feature is free).
                        ListRow(LineIcons.Crown, "Locked In Pro", "Beta · all features", valueColor = accent) { com.sohum.bandlog.ui.platform.PlatformNav.open(com.sohum.bandlog.ui.platform.PlatformPage.PRO) }
                        ListDivider()
                        ListRow(LineIcons.User, "Personal details", listOfNotNull(prof.age?.toString(), prof.heightCm?.let { "${it.roundToInt()} cm" }).joinToString(" · ")) { onOpen(ProfilePage.PERSONAL) }
                        ListDivider()
                        ListRow(LineIcons.Target, "Nutrition goals", if (prof.hideNumbers == true) "Set" else "${prof.calorieTarget} kcal") { onOpen(ProfilePage.GOALS) }
                        ListDivider()
                        ListRow(LineIcons.Flag, "Goal weight", prof.goalType.replaceFirstChar { it.uppercase() }) { onOpen(ProfilePage.GOAL_WEIGHT) }
                        ListDivider()
                        ListRow(LineIcons.Chart, "Weight history", if (vm.weights.isEmpty()) "" else "${vm.weights.size} ${if (vm.weights.size == 1) "entry" else "entries"}") { onOpen(ProfilePage.WEIGHT_HISTORY) }
                        ListDivider()
                        ListRow(LineIcons.Bell, "Reminders", "$remindersOn on") { onOpen(ProfilePage.REMINDERS) }
                        ListDivider()
                        ListRow(LineIcons.Sliders, "Preferences", "") { onOpen(ProfilePage.PREFERENCES) }
                        ListDivider()
                        ListRow(LineIcons.Star, "What's new", BuildConfig.VERSION_NAME) { showChangelog = true }
                        ListDivider()
                        ListRow(
                            LineIcons.Refresh, "Check for updates",
                            when {
                                updateVm.checking -> "Checking…"
                                updateVm.upToDate -> "v${BuildConfig.VERSION_NAME} · up to date"
                                else -> "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                            },
                            valueColor = if (updateVm.upToDate) p.green else null, chevron = false,
                        ) { updateVm.check() }
                        ListDivider()
                        ListRow(LineIcons.Message, "Request a feature", "") {
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:sohumai.team@gmail.com")
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Locked In — feature request")
                                    },
                                )
                            }
                        }
                        // v2.11: only the owner sees this; the web panel still checks on the server.
                        if (isAdmin && BuildConfig.API_BASE.isNotBlank()) {
                            ListDivider()
                            ListRow(LineIcons.Shield, "Admin", "Opens in browser") {
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(BuildConfig.API_BASE.trimEnd('/') + "/admin"))) }
                            }
                        }
                        ListDivider()
                        ListRow(LineIcons.LogOut, "Sign out", "", chevron = false) { vm.signOut() }
                    }
                }
            }
        }
    }

    if (showChangelog) ChangelogSheet { showChangelog = false }
    if (editSheet) BottomSheet(title = "Edit profile", subtitle = "Your photo, name and squad handle", onDismiss = { editSheet = false }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editSheet = false; avatarSheet = true }, verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.Camera, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Profile photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
            Chevron()
        }
        Hair()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.User, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) {
                NameField(prof.name, placeholder = Names.nameFromEmail(Session.email).ifBlank { "Enter your name" }) { newName -> vm.launch { vm.saveProfile(vm.profile.copy(name = newName)) } }
            }
        }
        Session.email?.let { Text(it, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 34.dp, bottom = 8.dp)) }
        if (prof.usernameSupported) {
            Hair()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editSheet = false; onOpen(ProfilePage.USERNAME) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(LineIcons.AtSign, null, tint = p.ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(prof.username?.let { "@$it" } ?: "Create your squad username", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                Chevron()
            }
        }
        Hair()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editSheet = false; onOpen(ProfilePage.PERSONAL) }, verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.Sliders, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Personal details", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
            Chevron()
        }
    }
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
}

/** " · about Nov 20" for the goal card, from the planned pace (the Progress weight card uses the trend). */
private fun goalEtaShort(vm: AppViewModel, current: Double?): String {
    val goal = vm.profile.goalWeightKg ?: return ""
    if (current == null) return ""
    val remaining = kotlin.math.abs(goal - current)
    if (remaining < 0.2) return " · reached"
    val asc = vm.weights.sortedBy { it.date }
    val recent = asc.filter { it.date >= com.sohum.bandlog.util.Dates.addDays(vm.today, -30) }
    val trend = if (recent.size >= 2) (recent.last().weightKg - recent.first().weightKg) / maxOf(1L, com.sohum.bandlog.util.Dates.daysBetween(recent.first().date, recent.last().date)).toDouble() else 0.0
    val perDay = if (trend * (goal - current) > 0 && kotlin.math.abs(trend) > 0.005) kotlin.math.abs(trend) else vm.profile.goalSpeedKgWk.coerceAtLeast(0.1) / 7.0
    val days = (remaining / perDay).roundToInt()
    if (days > 730) return ""
    return " · about " + java.time.LocalDate.now(com.sohum.bandlog.util.Dates.ZONE).plusDays(days.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))
}

@Composable
private fun GoalStat(value: String, label: String, onClick: () -> Unit) {
    val p = palette
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight(500))) { append(value) }
            append(" $label")
        },
        fontSize = 14.sp, color = p.muted,
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Edit $label", onClick = onClick).padding(vertical = 14.dp),
    )
}

/** One row of the Profile list: thin line icon, label, muted value, chevron. 48 dp+ tall. */
@Composable
private fun ListRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color? = null, chevron: Boolean = true, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = p.ink, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.5.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (value.isNotEmpty()) Text(value, fontSize = 13.5.sp, color = valueColor ?: p.muted, maxLines = 1)
        if (chevron) Icon(LineIcons.ChevronRight, null, tint = p.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ListDivider() = Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(1.dp).background(if (isDarkTheme) Color(0xFF2A2A2D) else Color(0xFFE2E2E6)))

/**
 * Medals v2 shelf: up to three earned medals (best tier first) and the next locked one, "N of M ›"
 * to the full grid, then "Next up: X · k more days" with a growing bar.
 */
@Composable
private fun BadgesShelf(vm: AppViewModel, onOpen: () -> Unit) {
    val p = palette
    val progress = vm.badgeProgress
    val all = com.sohum.bandlog.util.Badges.ALL
    val earned = all.filter { progress.earned(it) }.sortedWith(compareBy({ tierOf(it).ordinal }, { -it.need }))
    val locked = all.filter { !progress.earned(it) }.sortedByDescending { progress.value(it.group).toFloat() / it.need }
    val shelf = (earned.take(3) + locked).take(4)
    val next = nextBadge(progress)
    ProfileCard(padding = 0.dp, radius = 24.dp) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Open badges", onClick = onOpen).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Badges", fontSize = 19.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                Text("${earned.size} of ${all.size}  ›", fontSize = 14.sp, color = p.muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                shelf.forEachIndexed { i, b ->
                    val got = progress.earned(b)
                    Column(
                        Modifier.width(80.dp).clickable(onClickLabel = "Open badges", onClick = onOpen)
                            .semantics(mergeDescendants = true) { contentDescription = if (got) "${b.name}, ${tierOf(b).label.lowercase()} medal" else "${b.name}, ${progress.value(b.group).coerceAtMost(b.need)} of ${b.need}" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        BadgeMedal(b, progress, 76.dp, "shelf$i", 450 + i * 120)
                        Text(
                            if (got) tierOf(b).label else "${progress.value(b.group).coerceAtMost(b.need)} OF ${b.need}",
                            fontSize = 9.5.sp, fontWeight = FontWeight(600), letterSpacing = 1.4.sp, color = if (got) tierOf(b).mid else accentColor,
                        )
                        Text(b.name, fontSize = 13.sp, fontWeight = FontWeight(500), color = p.ink, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp)
                    }
                }
            }
            if (next != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(1.dp).background(if (isDarkTheme) Color(0xFF2A2A2D) else Color(0xFFE2E2E6)))
                NextUpRow(next, progress, Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

internal val LENS_OPTIONS = listOf(
    "protein" to ("Protein" to "How much protein it gives you"),
    "goal" to ("My goal" to "Follows your goal: Lose → Cutting, Gain → Bulking"),
    "snack" to ("Snack" to "Is it a decent snack"),
    "cutting" to ("Cutting" to "Fits a calorie deficit"),
    "bulking" to ("Bulking" to "Helps a surplus"),
)

/** Which lens scan reports open on. One tap picks and closes. */
@Composable
internal fun LensSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
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
internal fun NameField(name: String, placeholder: String = "Enter your name", onCommit: (String) -> Unit) {
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
internal fun RingColoursSheet(onDismiss: () -> Unit) {
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

/**
 * v2.7 Squad Food Battle: total crowns + last 7 wins (docs/food-battle-spec.md), matching the
 * web's GraffitiWall.tsx — a bold spray-paint styled strip of recent wins, quiet (not shown at
 * all) for anyone who's never won one.
 */
@Composable
private fun GraffitiWallCard(g: com.sohum.bandlog.data.BattleRepo.Graffiti) {
    val p = palette
    Card(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(com.sohum.bandlog.ui.components.CrownIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Graffiti wall", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink)
                Text(
                    "${g.total} food battle crown${if (g.total == 1) "" else "s"} won",
                    fontSize = 12.sp, color = p.muted,
                )
            }
        }
        if (g.recent.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.width(8.dp))
                g.recent.forEach { w ->
                    val gradient = Brush.linearGradient(listOf(Color(0xFFFF5C8A), Color(0xFFFFC53D), Color(0xFF7C5CFF)))
                    Column(
                        Modifier.width(140.dp).background(gradient, RoundedCornerShape(16.dp)).padding(12.dp),
                    ) {
                        Text(w.date, fontSize = 10.sp, fontWeight = FontWeight(800), color = Color(0xFFFFD23C), letterSpacing = 0.6.sp)
                        Text(w.groupName, fontSize = 15.sp, fontWeight = FontWeight(900), color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        Text("${w.score.toInt()} pts · ${w.goalLabel}", fontSize = 11.sp, fontWeight = FontWeight(700), color = Color.White.copy(alpha = 0.88f), modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
