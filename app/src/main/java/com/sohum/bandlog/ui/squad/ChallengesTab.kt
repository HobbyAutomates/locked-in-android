package com.sohum.bandlog.ui.squad

import com.sohum.bandlog.ui.components.hatchTrack

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.Challenge
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Squad
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ChevronDownIcon
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.Palette
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.ChallengeMath
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Names

/** A kind's accent: train = blue (workouts in the feed), protein = red (the protein macro), logging = green (meals). */
private fun Palette.kindColor(kind: String): Color = when (kind) {
    ChallengeMath.PROTEIN -> red
    ChallengeMath.LOG -> green
    else -> blue
}

/** "12 Sep – 25 Sep" */
private fun dateRange(a: String, b: String): String = runCatching {
    val f = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
    "${Dates.parse(a).format(f)} – ${Dates.parse(b).format(f)}"
}.getOrDefault("")

/**
 * v2.7 Challenges tab: open (active + upcoming) challenges as cards, "Start a challenge" (off at 3
 * open, with a hint), and a collapsed "Past" section. Opening the tab reloads the list and posts 🏆
 * for anything I've just finished.
 */
@Composable
internal fun ChallengesTab(sq: SquadViewModel, squad: Squad, proteinGoal: Int?) {
    val p = palette
    LaunchedEffect(sq.openId) { sq.loadChallenges() }
    var creating by remember(squad.id) { mutableStateOf(false) }
    var pastOpen by remember(squad.id) { mutableStateOf(false) }
    val list = sq.challenges
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
        return
    }
    if (sq.challengesSupported == false && list.isEmpty()) {
        EmptyState("Challenges are almost here", "They switch on once the server's updated. Check back soon 👀")
        return
    }
    val open = list.filter { it.isOpen }
    val past = list.filter { !it.isOpen }
    val full = open.size >= ChallengeMath.MAX_OPEN
    val today = ChallengeMath.today()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "start") {
            Column {
                PillButton("Start a challenge", { sq.error = null; creating = true }, enabled = !full, icon = com.sohum.bandlog.ui.components.PlusIcon)
                if (full) Text(
                    "3 challenges running already, that's the max. Start the next one when one wraps.",
                    fontSize = 12.sp, color = p.muted, textAlign = TextAlign.Center, lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
        if (open.isEmpty()) item(key = "empty") {
            Card(padding = 20.dp) {
                Text("No challenges yet", fontSize = 20.sp, fontWeight = FontWeight(800), color = p.ink)
                Text(
                    "Start one and the whole squad's in automatically. Train days, protein days or a logging streak. First to the target flexes 🏆",
                    fontSize = 14.sp, color = p.muted, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        items(open, key = { it.id }) { c -> ChallengeCard(sq, c, today) { sq.openChallenge(c.id) } }
        if (past.isNotEmpty()) {
            item(key = "past-head") {
                val turn by animateFloatAsState(if (pastOpen) 180f else 0f, Motion.spatialFast(), label = "past")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { pastOpen = !pastOpen }.padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Past", fontSize = 17.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink)
                    Text("  ${past.size}", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.weight(1f))
                    Icon(ChevronDownIcon, if (pastOpen) "Hide past challenges" else "Show past challenges", tint = p.muted, modifier = Modifier.size(20.dp).rotate(turn))
                }
            }
            if (pastOpen) items(past, key = { "past-" + it.id }) { c -> PastChallengeRow(c) { sq.openChallenge(c.id) } }
        }
    }
    if (creating) StartChallengeSheet(sq, proteinGoal) { creating = false }
}

/** Leader's avatar from the squad's member / leaderboard data: by leader_user_id, else by name. */
private fun leaderAvatar(sq: SquadViewModel, c: Challenge): String? {
    c.leaderUserId?.let { id ->
        (sq.members.firstOrNull { it.userId == id }?.avatarPath ?: sq.leaders.firstOrNull { it.userId == id }?.avatarPath)?.let { return it }
    }
    return c.leaderName?.let { n -> sq.members.firstOrNull { it.name == n }?.avatarPath ?: sq.leaders.firstOrNull { it.name == n }?.avatarPath }
}

@Composable
private fun KindTag(kind: String) {
    val p = palette
    val tint = p.kindColor(kind)
    Row(Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(com.sohum.bandlog.ui.components.challengeKindIcon(kind), null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(ChallengeMath.kindLabel(kind), fontSize = 11.sp, fontWeight = FontWeight(800), color = tint, maxLines = 1)
    }
}

/** An open challenge: kind tag, title, "N days left" / "starts in N days", my ring, and the leader. */
@Composable
private fun ChallengeCard(sq: SquadViewModel, c: Challenge, today: String, onClick: () -> Unit) {
    val p = palette
    val tint = p.kindColor(c.kind)
    val done = c.myProgress >= c.targetDays
    Card(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KindTag(c.kind)
                Text(c.title, fontSize = 18.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ChallengeMath.timeLine(today, c.startsOn, c.endsOn), fontSize = 13.sp, fontWeight = FontWeight(600), color = if (c.status == "upcoming") p.muted else tint, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (done) "You did it" else ChallengeMath.progressLine(c.myProgress, c.targetDays), fontSize = 13.sp, color = p.muted)
                    StatusIcon(done, c.myProgress, 13.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Ring(ChallengeMath.fraction(c.myProgress, c.targetDays), if (done) p.green else tint, 84.dp, 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${c.myProgress}/${c.targetDays}", fontSize = 18.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink)
                    Text("days", fontSize = 11.sp, color = p.muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val leader = c.leaderName?.takeIf { c.leaderProgress > 0 }
            if (leader != null) {
                Avatar(Api.avatarUrl(leaderAvatar(sq, c)), Names.initials(leader), 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${leader.substringBefore(' ')} leads · ${c.leaderProgress}/${c.targetDays}",
                    fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    if (c.status == "upcoming") "Everyone's in. Warm up 😤" else "No one's on the board yet. Be first 👀",
                    fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            if (c.completedCount > 0) {
                Icon(com.sohum.bandlog.ui.components.TrophyIcon, null, tint = p.muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (c.completedCount > 0) "${c.completedCount} done" else "${c.participants} in",
                fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted,
            )
        }
    }
}

/** An ended challenge in "Past": title, dates, and who finished. */
@Composable
private fun PastChallengeRow(c: Challenge, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val range = dateRange(c.startsOn, c.endsOn)
                if (range.isNotBlank()) Text("$range · ", fontSize = 12.sp, color = p.muted, maxLines = 1)
                if (c.completedCount > 0) {
                    Icon(com.sohum.bandlog.ui.components.TrophyIcon, null, tint = p.muted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(if (c.completedCount == 0) "no finishers this time" else "${c.completedCount} finished", fontSize = 12.sp, color = p.muted, maxLines = 1)
            }
            c.leaderName?.takeIf { c.leaderProgress > 0 }?.let { l ->
                Text("Top: $l · ${c.leaderProgress}/${c.targetDays}", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("${c.myProgress}/${c.targetDays}", fontSize = 15.sp, fontWeight = FontWeight(800), color = if (c.myProgress >= c.targetDays) p.green else p.muted)
    }
}

/** −  value  + on a grey track, with the QuantitySheet's round step buttons. */
@Composable
private fun Stepper(label: String, value: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    val p = palette
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(22.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StepCircle("−", "Less", canDown, onDown)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            StepCircle("+", "More", canUp, onUp)
        }
    }
}

@Composable
private fun StepCircle(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.35f).pressable().background(p.card, CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight(700), color = p.ink) }
}

@Composable
private fun SheetLabel(text: String) =
    Text(text, fontSize = 13.sp, fontWeight = FontWeight(700), color = palette.muted, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))

/**
 * "Start a challenge": template chips, length chips (7 / 14 / 30), Today or Tomorrow, the target
 * stepper (defaults per kind), the protein stepper for protein days, and an editable title that
 * follows the template until it's edited.
 */
@Composable
private fun StartChallengeSheet(sq: SquadViewModel, proteinGoal: Int?, onDismiss: () -> Unit) {
    val p = palette
    var kind by remember { mutableStateOf(ChallengeMath.TRAIN) }
    var length by remember { mutableIntStateOf(14) }
    var tomorrow by remember { mutableStateOf(false) }
    var target by remember { mutableIntStateOf(ChallengeMath.defaultTarget(ChallengeMath.TRAIN, 14)) }
    var protein by remember { mutableIntStateOf(ChallengeMath.defaultProtein(proteinGoal)) }
    var title by remember { mutableStateOf(ChallengeMath.defaultTitle(ChallengeMath.TRAIN, target, 14, null)) }
    var titleEdited by remember { mutableStateOf(false) }

    fun retitle() { if (!titleEdited) title = ChallengeMath.defaultTitle(kind, target, length, if (kind == ChallengeMath.PROTEIN) protein else null) }
    fun pick(k: String, len: Int) { kind = k; length = len; target = ChallengeMath.defaultTarget(k, len); retitle() }

    val start = if (tomorrow) Dates.addDays(ChallengeMath.today(), 1) else ChallengeMath.today()
    BottomSheet(
        title = "Start a challenge",
        subtitle = "The whole squad's in automatically",
        onDismiss = onDismiss,
        primary = if (sq.busy) "Starting…" else "Start it",
        primaryEnabled = title.isNotBlank() && !sq.busy,
        onPrimary = {
            sq.startChallenge(kind, title.trim(), target, protein, start, ChallengeMath.endsOn(start, length), onDismiss)
        },
    ) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ChallengeMath.TRAIN, ChallengeMath.PROTEIN, ChallengeMath.LOG).forEach { k ->
                    Chip(ChallengeMath.kindLabel(k), kind == k, { pick(k, length) }, icon = com.sohum.bandlog.ui.components.challengeKindIcon(k))
                }
            }
            SheetLabel("How long")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeMath.LENGTHS.forEach { len -> Chip("$len days", length == len, { pick(kind, len) }, Modifier.weight(1f)) }
            }
            SheetLabel("Starts")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Today", !tomorrow, { tomorrow = false }, Modifier.weight(1f))
                Chip("Tomorrow", tomorrow, { tomorrow = true }, Modifier.weight(1f))
            }
            Text(dateRange(start, ChallengeMath.endsOn(start, length)), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Stepper(
                when (kind) { ChallengeMath.PROTEIN -> "Protein days to hit"; ChallengeMath.LOG -> "Days to log"; else -> "Days to train" },
                "$target of $length days", target > 1, target < length,
                onDown = { target -= 1; retitle() }, onUp = { target += 1; retitle() },
            )
            if (kind == ChallengeMath.PROTEIN) {
                Spacer(Modifier.height(12.dp))
                Stepper(
                    "Protein per day", "$protein g", protein > ChallengeMath.MIN_PROTEIN, protein < ChallengeMath.MAX_PROTEIN,
                    onDown = { protein = (protein - 5).coerceAtLeast(ChallengeMath.MIN_PROTEIN); retitle() },
                    onUp = { protein = (protein + 5).coerceAtMost(ChallengeMath.MAX_PROTEIN); retitle() },
                )
            }
            SheetLabel("Title")
            FilledField(title, { title = it.replace('\n', ' ').take(ChallengeMath.MAX_TITLE); titleEdited = true }, "Name it", maxLines = 1)
            if (titleEdited) Text(
                "Reset to suggested", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp).clickable { titleEdited = false; retitle() },
            )
            ErrorNote(sq.error, Modifier.padding(top = 10.dp))
        }
    }
}

/** A thin rounded progress bar that fills with a spring. */
@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val p = palette
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, Motion.spatialSlow(), label = "bar")
    Box(modifier.fillMaxWidth().height(6.dp).hatchTrack(CircleShape)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(6.dp).clip(CircleShape).background(color))
    }
}

/**
 * Challenge detail: title, dates, my big ring, then the ranked board with the Leaderboard's rows
 * (avatar, #rank, a progress bar per member, 🏆 on finishers). The creator or the squad owner can
 * delete it, after a confirm.
 */
@Composable
fun ChallengeDetailPage(sq: SquadViewModel, squad: Squad) {
    val p = palette
    val c = sq.openChallenge
    val me = Session.userId
    var confirmDelete by remember(sq.challengeOpenId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 8.dp, 12.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { sq.closeChallenge() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            val canDelete = c != null && (c.createdBy == me || squad.ownerId == me)
            if (canDelete) Text(
                "Delete", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.red,
                modifier = Modifier.clip(CircleShape).clickable(enabled = !sq.busy) { confirmDelete = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        if (c == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
            return@Column
        }
        val tint = p.kindColor(c.kind)
        val today = ChallengeMath.today()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    KindTag(c.kind)
                    Text(
                        c.title, fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink, textAlign = TextAlign.Center, lineHeight = 30.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
                    )
                    Text(
                        listOfNotNull(
                            dateRange(c.startsOn, c.endsOn).ifBlank { null },
                            ChallengeMath.timeLine(today, c.startsOn, c.endsOn),
                            c.proteinTarget?.let { "${it} g protein a day" },
                        ).joinToString(" · "),
                        fontSize = 14.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    val done = c.myProgress >= c.targetDays
                    Ring(ChallengeMath.fraction(c.myProgress, c.targetDays), if (done) p.green else tint, 140.dp, 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${c.myProgress}/${c.targetDays}", fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink)
                            Text("days", fontSize = 13.sp, color = p.muted)
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                done -> "You did it"
                                c.status == "upcoming" -> "Starts ${if (ChallengeMath.daysUntil(today, c.startsOn) <= 1) "tomorrow" else Dates.short(c.startsOn)}. Get ready 😤"
                                c.status == "ended" -> "Wrapped at ${ChallengeMath.progressLine(c.myProgress, c.targetDays)}"
                                else -> ChallengeMath.progressLine(c.myProgress, c.targetDays) + " · ${c.targetDays - c.myProgress} to go"
                            },
                            fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink,
                        )
                        if (c.status != "upcoming") StatusIcon(done, c.myProgress, 15.dp)
                    }
                    Text("Started by ${if (c.createdBy == me) "you" else c.creatorName}", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item(key = "board-title") {
                Text("Board", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
            if (sq.challengeBoard.isEmpty()) item(key = "board-empty") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (sq.challengeBoardLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted)
                    else Text("No one on the board yet", fontSize = 14.sp, color = p.muted)
                }
            }
            val rows = sq.challengeBoard.sortedWith(compareBy { if (it.rank > 0) it.rank else Int.MAX_VALUE })
            items(rows.withIndex().toList(), key = { it.value.userId }) { (i, r) ->
                RankRow(
                    rank = if (r.rank > 0) r.rank else i + 1, name = r.name, username = r.username, avatarPath = r.avatarPath, isMe = r.userId == me,
                    below = { ProgressBar(ChallengeMath.fraction(r.progress, c.targetDays), if (r.completed) p.green else tint, Modifier.padding(top = 8.dp)) },
                ) {
                    Spacer(Modifier.width(12.dp))
                    if (r.completed) {
                        Icon(com.sohum.bandlog.ui.components.TrophyIcon, "Completed", tint = p.flame, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("${r.progress}/${c.targetDays}", fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
                }
            }
            item(key = "err") { ErrorNote(sq.error) }
        }
    }
    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = p.card,
            title = { Text("Delete this challenge?", fontWeight = FontWeight(800), color = p.ink) },
            text = { Text("“${c.title}” disappears for the whole squad. Everyone's logged days stay put.", color = p.muted, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; sq.deleteChallenge(c.id) }) { Text("Delete", color = p.red, fontWeight = FontWeight(700)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it", color = p.ink, fontWeight = FontWeight(600)) }
            },
        )
    }
}

/** v2.10: after a progress line, a trophy once it's done, else a flame once there's a day in (was an emoji suffix). */
@Composable
private fun StatusIcon(done: Boolean, progress: Int, size: androidx.compose.ui.unit.Dp) {
    val p = palette
    if (!done && progress <= 0) return
    Spacer(Modifier.width(4.dp))
    Icon(if (done) com.sohum.bandlog.ui.components.TrophyIcon else com.sohum.bandlog.ui.components.FlameIcon, null, tint = p.flame, modifier = Modifier.size(size))
}
