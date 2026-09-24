package com.sohum.bandlog.ui.squad

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.GroupPost
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Squad
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.CopyIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FistIcon
import com.sohum.bandlog.ui.components.Flame
import com.sohum.bandlog.ui.components.PeopleIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.ShareIcon
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Names
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The shell renders this above the tabs: the squad profile flow, the create flow, and the open
 * squad (tabs page, or its invite / members page). Each owns its Back.
 */
@Composable
fun SquadOverlays(vm: AppViewModel) {
    val sq: SquadViewModel = viewModel()
    val display = Names.display(vm.profile.name, Session.email, "Member")
    val open = sq.open
    when {
        sq.profileFlow -> { BackHandler { sq.profileFlow = false; sq.profileFlowDismissed = true }; UsernameFlow(vm) { sq.profileFlow = false; sq.profileFlowDismissed = true } }
        sq.creating -> { BackHandler { sq.creating = false }; CreateSquadFlow(sq, display) { sq.creating = false } }
        open != null && sq.infoOpen -> { BackHandler { sq.infoOpen = false }; SquadInfoPage(sq, open) { sq.infoOpen = false } }
        open != null && sq.challengeOpenId != null -> { BackHandler { sq.closeChallenge() }; ChallengeDetailPage(sq, open) }
        open != null -> { BackHandler { sq.close() }; SquadPage(sq, open, proteinGoal = vm.profile.proteinTargetG.takeIf { vm.loadedOnce }) }
    }
}

/** v2.7: Challenges sits after the first tab; [weight] keeps the two long labels on one line. */
private enum class SquadTab(val label: String, val weight: Float) {
    CHAT("Chat", 0.8f), CHALLENGES("Challenges", 1.3f), FEED("Feed", 0.8f), BOARD("Leaderboard", 1.35f)
}

/** Feed kinds (everything but chat messages); v2.7 adds challenge start / finish posts. */
val FEED_KINDS = listOf("meal", "workout", "pr", "photo", "challenge")

/** Cal AI group page: header (icon, name, members button) and the Chat · Challenges · Feed · Leaderboard tabs. */
@Composable
fun SquadPage(sq: SquadViewModel, squad: Squad, initialTab: Int? = null, proteinGoal: Int? = null) {
    val p = palette
    val tabs = if (sq.feedSupported == false) listOf(SquadTab.BOARD) else SquadTab.entries.toList()
    var tab by remember(squad.id) { mutableIntStateOf(initialTab ?: if (sq.feedSupported == false) 0 else tabs.indexOf(SquadTab.FEED)) }
    val current = tabs.getOrElse(tab) { tabs.last() }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Column(Modifier.background(p.card)) {
            Row(Modifier.fillMaxWidth().padding(8.dp, 8.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { sq.close() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(24.dp))
                }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { sq.infoOpen = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SquadIconView(squad.icon, squad.name, 40.dp, cover = squad.coverUrl)
                    Spacer(Modifier.width(12.dp))
                    Text(squad.name, fontSize = 20.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { sq.infoOpen = true }, contentAlignment = Alignment.Center) {
                    Icon(PeopleIcon, "Members and invite", tint = p.ink, modifier = Modifier.size(22.dp))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, t ->
                    val sel = t == current
                    Column(Modifier.weight(t.weight).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.label, fontSize = 15.sp, fontWeight = if (sel) FontWeight(800) else FontWeight(500), color = if (sel) p.ink else p.muted, modifier = Modifier.padding(vertical = 12.dp), maxLines = 1, softWrap = false)
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (sel) p.ink else Color.Transparent))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
        }
        ErrorNote(sq.error, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Box(Modifier.weight(1f)) {
            when (current) {
                SquadTab.CHAT -> ChatTab(sq)
                SquadTab.CHALLENGES -> ChallengesTab(sq, squad, proteinGoal)
                SquadTab.FEED -> FeedTab(sq)
                SquadTab.BOARD -> LeaderboardTab(sq)
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, sub: String) {
    val p = palette
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight(800), color = p.ink, textAlign = TextAlign.Center)
        Text(sub, fontSize = 15.sp, color = p.muted, textAlign = TextAlign.Center, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Chat: kind=message posts, newest at the bottom; polls every 5 s while on screen. */
@Composable
private fun ChatTab(sq: SquadViewModel) {
    val p = palette
    val me = Session.userId
    LaunchedEffect(sq.openId) { while (true) { delay(5_000); sq.refreshFeed() } }
    val messages = sq.posts.filter { it.kind == "message" }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !sq.pageLoading) EmptyState("Say hi 👋", "Messages stay in the squad. Plan a session, call out a PR, keep each other honest.")
            LazyColumn(Modifier.fillMaxSize(), reverseLayout = true, contentPadding = PaddingValues(12.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { m -> ChatBubble(m, mine = m.userId == me) }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(p.card).navigationBarsPadding().padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).heightIn(min = 46.dp).background(p.card2, RoundedCornerShape(23.dp)).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    text, { text = it.take(500) }, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = TextStyle(fontSize = 16.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (text.isEmpty()) Text("Message", fontSize = 16.sp, color = p.muted); inner() },
                )
            }
            Spacer(Modifier.width(8.dp))
            val can = text.isNotBlank() && !sq.posting
            Box(
                Modifier.size(46.dp).pressable().background(if (can) p.btn else p.card2, CircleShape).clickable(enabled = can) { sq.send(text); text = "" },
                contentAlignment = Alignment.Center,
            ) {
                if (sq.posting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.muted)
                else Text("↑", fontSize = 20.sp, fontWeight = FontWeight(800), color = if (can) p.btnInk else p.muted)
            }
        }
    }
}

@Composable
private fun ChatBubble(m: GroupPost, mine: Boolean) {
    val p = palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            Avatar(Api.avatarUrl(m.authorAvatar), Names.initials(m.authorName), 30.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine) Text(m.authorName, fontSize = 11.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Box(
                Modifier.widthIn(max = 280.dp).background(if (mine) p.btn else p.card2, RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text(m.body, fontSize = 15.sp, color = if (mine) p.btnInk else p.ink, lineHeight = 20.sp) }
            Text(timeAgo(m.createdAt), fontSize = 10.sp, color = p.muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** Feed: meals, workouts, PRs and photos from the save paths, plus a "+ Photo" post. */
@Composable
private fun FeedTab(sq: SquadViewModel) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(sq.openId) { sq.refreshFeed() }
    val items = sq.posts.filter { it.kind in FEED_KINDS }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 1600) }.getOrNull() }
            if (bmp != null) sq.postPhoto(bmp) else sq.error = "Couldn't open that photo"
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty() && !sq.pageLoading) EmptyState("No Posts Yet", "Be the first to share what you're eating with your squad! Meals, workouts and PRs you log show up here.")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { post ->
                // A challenge post opens its challenge while it's still listed (open, or ended in the last 30 days).
                val target = post.refId?.takeIf { post.kind == "challenge" }?.let { ref -> sq.challenges?.firstOrNull { it.id == ref } }
                FeedCard(post, onClick = target?.let { c -> { sq.openChallenge(c.id) } })
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).height(52.dp).pressable()
                .shadow(10.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.btn, CircleShape)
                .clickable(enabled = !sq.posting) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sq.posting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.btnInk)
                else Icon(CameraIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp))
                Text(if (sq.posting) "  Posting…" else "  Photo", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk)
            }
        }
    }
}

@Composable
private fun FeedCard(post: GroupPost, onClick: (() -> Unit)? = null) {
    val p = palette
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(Api.avatarUrl(post.authorAvatar), Names.initials(post.authorName), 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.authorName, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                Text(listOfNotNull(post.authorUsername?.let { "@$it" }, timeAgo(post.createdAt)).joinToString(" · "), fontSize = 12.sp, color = p.muted, maxLines = 1)
            }
            val (tag, tint) = when (post.kind) {
                "meal" -> "Meal" to p.green
                "workout" -> "Workout" to p.blue
                "pr" -> "PR" to p.flame
                "challenge" -> "Challenge" to p.orange
                else -> "Photo" to p.purple
            }
            Box(Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(tag, fontSize = 11.sp, fontWeight = FontWeight(800), color = tint)
            }
        }
        Spacer(Modifier.height(10.dp))
        when (post.kind) {
            "pr" -> Text(post.body, fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink)
            "workout" -> Text(
                buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight(700))) { append("Trained") }; append(" · "); append(post.body) },
                fontSize = 15.sp, color = p.ink, lineHeight = 20.sp,
            )
            "photo" -> if (post.body.isNotBlank() && post.body != "shared a photo") Text(post.body, fontSize = 15.sp, color = p.ink)
            // 🏁 started "Title" / 🏆 completed "Title": the emoji in a disc, the verb as a kicker, the title as the headline.
            "challenge" -> {
                val lead = post.body.substringBefore(' ')
                val verb = post.body.substringAfter(' ', "").substringBefore(' ')
                val title = post.body.substringAfter('"', "").substringBeforeLast('"').ifBlank { post.body.substringAfter(' ', post.body) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(p.orange.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) { Text(lead, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (verb.lowercase()) { "started" -> "Started a challenge"; "completed" -> "Finished a challenge 🔥"; else -> "Challenge" },
                            fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted,
                        )
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight(800), letterSpacing = (-0.3).sp, color = p.ink, lineHeight = 22.sp)
                    }
                }
            }
            else -> Text(
                buildAnnotatedString {
                    val verb = post.body.substringBefore(' ')
                    if (verb.equals("logged", true)) { withStyle(SpanStyle(fontWeight = FontWeight(700))) { append("Logged") }; append(post.body.removePrefix(verb)) } else append(post.body)
                },
                fontSize = 15.sp, color = p.ink, lineHeight = 20.sp,
            )
        }
        post.photoPath?.let { path ->
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(p.card2)) {
                RemoteImage(privatePath = path, bucket = "group-photos", size = 400.dp, radius = 0.dp, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Leaderboard: #rank, avatar, name, @username, flames — from group_leaderboard (or the v2.0 board). */
@Composable
private fun LeaderboardTab(sq: SquadViewModel) {
    val p = palette
    val me = Session.userId
    if (sq.leaders.isEmpty()) {
        if (sq.pageLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
        else EmptyState("No one ranked yet", "Log a workout to light your first flame.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(sq.leaders.withIndex().toList(), key = { it.value.userId }) { (i, r) ->
            RankRow(i + 1, r.name, r.username, r.avatarPath, isMe = r.userId == me) {
                Flame(if (r.flames > 0) p.flame else p.muted, 20.dp)
                Text(" ${r.flames}", fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
            }
        }
    }
}

/**
 * One leaderboard row: #rank (gold for #1), avatar, name (+ "(you)"), @username, an optional
 * line under the name ([below]) and the trailing score. Shared by the Leaderboard and challenge boards.
 */
@Composable
internal fun RankRow(
    rank: Int, name: String, username: String?, avatarPath: String?, isMe: Boolean,
    below: (@Composable () -> Unit)? = null,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val p = palette
    val gold = Color(0xFFFFC53D)
    Row(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = p.shadow, spotColor = p.shadow).background(p.card, RoundedCornerShape(22.dp))
            .then(if (isMe) Modifier.border(1.5.dp, p.ink.copy(alpha = 0.25f), RoundedCornerShape(22.dp)) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#$rank", fontSize = 17.sp, fontWeight = FontWeight(800), color = if (rank == 1) gold else p.muted, modifier = Modifier.width(40.dp))
        Avatar(Api.avatarUrl(avatarPath), Names.initials(name), 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name + if (isMe) " (you)" else "", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            username?.let { Text("@$it", fontSize = 14.sp, color = p.muted, maxLines = 1) }
            below?.invoke()
        }
        trailing()
    }
}

/** Cal AI group info: big icon, name, member count, the invite card, members (Owner badge, flames), requests. */
@Composable
fun SquadInfoPage(sq: SquadViewModel, squad: Squad, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val me = Session.userId
    val isOwner = squad.ownerId == me
    var copied by remember { mutableStateOf(false) }
    var confirmLeave by remember(squad.id) { mutableStateOf(false) }
    var renaming by remember(squad.id) { mutableStateOf(false) }
    var newName by remember(squad.id) { mutableStateOf(squad.name) }
    val link = inviteLink(squad.code)
    val invite = squadInviteText(squad.name, squad.code)
    val today = Dates.today()
    val trainedToday = sq.board.filter { m -> m.day(today)?.trained == true }.map { it.userId }.toSet()
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        FlowTopBar(null, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 40.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            SquadIconView(squad.icon, squad.name, 150.dp, cover = squad.coverUrl)
            Spacer(Modifier.height(14.dp))
            if (renaming) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledField(newName, { newName = it.take(40) }, "Squad name", Modifier.weight(1f), maxLines = 1)
                    PillButton("Save", { sq.rename(squad.id, newName); renaming = false }, Modifier.width(86.dp), enabled = newName.isNotBlank() && !sq.busy, height = 52.dp)
                }
            } else {
                Text(squad.name, fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp).then(if (isOwner) Modifier.clickable { renaming = true } else Modifier))
            }
            Text(
                "${sq.members.size.coerceAtLeast(1)} member${if (sq.members.size == 1) "" else "s"} · ${if (squad.isPrivate) "Private" else "Public"}",
                fontSize = 17.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp),
            )
            if (squad.description.isNotBlank()) Text(squad.description, fontSize = 14.sp, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))

            Spacer(Modifier.height(26.dp))
            Text("Invite your friends to the squad", fontSize = 20.sp, fontWeight = FontWeight(800), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
            Box(Modifier.padding(20.dp, 14.dp).fillMaxWidth().background(p.card, RoundedCornerShape(22.dp)).border(1.dp, p.hair, RoundedCornerShape(22.dp)).clickable {
                clip.setText(AnnotatedString(link)); copied = true; scope.launch { delay(1500); copied = false }
            }.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(link, fontSize = 16.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                InviteAction("Share", p.card, p.ink, { Icon(ShareIcon, null, tint = p.ink, modifier = Modifier.size(24.dp)) }) { shareInvite(ctx, invite) }
                InviteAction("WhatsApp", Color(0xFF25D366), Color.White, { Text("💬", fontSize = 24.sp) }) { shareInvite(ctx, invite, whatsapp = true) }
                InviteAction(if (copied) "Copied" else "Copy", p.card, p.ink, { Icon(if (copied) CheckIcon else CopyIcon, null, tint = if (copied) p.green else p.ink, modifier = Modifier.size(24.dp)) }) {
                    clip.setText(AnnotatedString(link)); copied = true; scope.launch { delay(1500); copied = false }
                }
            }
            Text("Code ${squad.code}", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 10.dp))

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))

            if (isOwner && sq.requests.isNotEmpty()) {
                SectionTitle("Requests to join")
                sq.requests.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.avatarUrl(r.avatarPath), Names.initials(r.name), 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                            Text(r.username?.let { "@$it" } ?: timeAgo(r.createdAt), fontSize = 13.sp, color = p.muted, maxLines = 1)
                        }
                        SmallPill("Decline", p.card2, p.ink, enabled = !sq.busy) { sq.decline(r) }
                        Spacer(Modifier.width(6.dp))
                        SmallPill("Approve", p.btn, p.btnInk, enabled = !sq.busy) { sq.approve(r) }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(p.hair))
            }

            SectionTitle("Members")
            val members = sq.members.sortedWith(compareByDescending<com.sohum.bandlog.data.MemberDetail> { it.isOwner }.thenByDescending { it.flames })
            members.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(Api.avatarUrl(m.avatarPath), Names.initials(m.name), 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name + if (m.userId == me) " (you)" else "", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (m.isOwner) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.background(p.card2, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Owner", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink) }
                            }
                        }
                        m.username?.let { Text("@$it", fontSize = 14.sp, color = p.muted, maxLines = 1) }
                    }
                    val showNudge = m.userId != me && sq.board.isNotEmpty() && m.userId !in trainedToday
                    if (showNudge) {
                        val already = m.userId in sq.sent
                        Row(
                            Modifier.height(32.dp).pressable().background(if (already) p.card2 else p.btn, CircleShape).clickable(enabled = !already) { sq.nudge(m.userId) }.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(FistIcon, "Nudge", tint = if (already) p.muted else p.btnInk, modifier = Modifier.size(13.dp))
                            Text(if (already) " Nudged" else " Nudge", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (already) p.muted else p.btnInk)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Flame(if (m.flames > 0) p.flame else p.muted, 20.dp)
                    Text(" ${m.flames}", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                }
            }
            if (members.isEmpty() && sq.pageLoading) CircularProgressIndicator(Modifier.padding(16.dp).size(20.dp), strokeWidth = 2.dp, color = p.muted)

            ErrorNote(sq.error, Modifier.padding(20.dp, 8.dp))
            if (isOwner) {
                Text(
                    if (squad.isPrivate) "Make it public (anyone can join from Discover)" else "Make it private (people request to join)",
                    fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !sq.busy) { sq.setPrivate(squad.id, !squad.isPrivate) }.padding(vertical = 14.dp),
                )
            }
            if (confirmLeave) {
                Column(Modifier.padding(20.dp).fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(16.dp)) {
                    Text("Leave ${squad.name}?", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(
                        when { sq.members.size <= 1 -> "You're the last one in, so the squad will be deleted."; isOwner -> "The longest-standing member becomes the owner."; else -> "You can rejoin any time with the invite link." },
                        fontSize = 12.sp, color = p.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Stay", { confirmLeave = false }, Modifier.weight(1f), height = 44.dp, bg = p.card2, fg = p.ink)
                        PillButton("Leave", { confirmLeave = false; sq.leave(squad.id) }, Modifier.weight(1f), enabled = !sq.busy, height = 44.dp, bg = p.red, fg = Color.White)
                    }
                }
            } else {
                Text("Leave squad", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { confirmLeave = true }.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight(800), color = palette.ink, modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 8.dp))
}

@Composable
private fun InviteAction(label: String, bg: Color, fg: Color, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val p = palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).pressable().shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = p.shadow, spotColor = p.shadow).background(bg, RoundedCornerShape(20.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { icon() }
        Text(label, fontSize = 14.sp, color = p.ink, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SmallPill(text: String, bg: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 36.dp).pressable().background(bg, CircleShape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight(700), color = fg, maxLines = 1)
    }
}
