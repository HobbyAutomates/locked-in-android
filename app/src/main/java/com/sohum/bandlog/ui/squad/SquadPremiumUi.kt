package com.sohum.bandlog.ui.squad

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Squad
import com.sohum.bandlog.data.SquadMember
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.FistIcon
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.PeopleIcon
import com.sohum.bandlog.ui.components.SQUAD_ICONS
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.progress.Jewel
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Jewels
import com.sohum.bandlog.util.Names
import com.sohum.bandlog.util.SquadPremium

/*
 * v2.16 premium Squad (boards PremiumSquad + SquadLeaderboard). Same list and data as before, on
 * premium cards: icon tile (gold for your #1 squad) with the unread count, member avatars, the
 * latest activity, the squad streak pill and a "#1" chip; a "N friends logged today" strip on top.
 * The leaderboard (locked by the owner): Members / Leaderboard tabs, a frosted-glass 2-1-3 podium
 * with a crown and each person's top jewel, then glass rows with an ember ring.
 */

internal val Ember = Color(0xFFFF5B1F)
internal val EmberLight = Color(0xFFFF8B5E)
internal val GoldLight = Color(0xFFD9B872)
internal val Gold = Color(0xFFA8823A)
internal val GoldDeep = Color(0xFF5E4518)

@Composable
internal fun Pill(text: String, bg: Color, fg: Color, icon: (@Composable () -> Unit)? = null) {
    Row(Modifier.height(26.dp).background(bg, CircleShape).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        icon?.invoke()
        Text(text, fontSize = 12.sp, fontWeight = FontWeight(600), color = fg, maxLines = 1)
    }
}

/** A row of overlapping avatars, ringed in the card colour. */
@Composable
internal fun AvatarStack(people: List<Pair<String, String?>>, size: Dp, max: Int = 3) {
    val p = palette
    val shown = people.take(max)
    val step = size - 4.dp
    Box(Modifier.width(size + 4.dp + step * (shown.size - 1).coerceAtLeast(0)).height(size + 4.dp)) {
        shown.forEachIndexed { i, (name, path) ->
            Box(Modifier.offset(x = step * i).size(size + 4.dp).background(p.card, CircleShape), contentAlignment = Alignment.Center) {
                Avatar(Api.avatarUrl(path), Names.initials(name), size)
            }
        }
    }
}

/** "2 friends logged today · Ayaan and Himanshu · tap to cheer". */
@Composable
internal fun CheerStrip(friends: List<SquadMember>, onClick: () -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Ember.copy(alpha = 0.16f), Gold.copy(alpha = 0.10f))), shape)
            .border(1.dp, EmberLight.copy(alpha = 0.18f), shape)
            .pressable().clickable(onClickLabel = "Cheer them on", onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(friends.map { it.name to it.avatarPath }, 30.dp, 2)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${friends.size} ${if (friends.size == 1) "friend" else "friends"} logged today", fontSize = 14.5.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("${SquadPremium.names(friends.map { it.name })} · tap to cheer", fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("→", fontSize = 16.sp, color = p.ink)
    }
}

/** One squad on the list (board PremiumSquad). */
@Composable
internal fun PremiumSquadCard(squad: Squad, summary: SquadViewModel.Summary?, unread: Int?, top: Boolean, onClick: () -> Unit) {
    val p = palette
    val me = Session.userId
    val shape = RoundedCornerShape(24.dp)
    val members = summary?.members.orEmpty()
    val rank = SquadPremium.myRank(members, me)
    val streak = SquadPremium.squadStreak(members)
    val today = com.sohum.bandlog.util.Dates.today()
    val line = SquadPremium.activityLine(summary?.latest, me)?.let { l -> summary?.latest?.createdAt?.let { "$l · ${timeAgo(it)}" } ?: l }
        ?: when {
            summary == null -> squad.description.ifBlank { "Tap for chat, feed and the leaderboard" }
            members.none { SquadPremium.loggedOn(it, today) } -> "Quiet today · nudge someone"
            else -> squad.description.ifBlank { "Tap for chat, feed and the leaderboard" }
        }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(p.card, shape).border(1.dp, p.hair, shape)
            .pressable().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SquadTile(squad, top)
            if (unread != null && unread > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).heightIn(min = 20.dp).background(p.card, CircleShape).padding(2.dp)
                        .background(Ember, CircleShape).padding(horizontal = 6.dp)
                        .semantics { contentDescription = "$unread unread" },
                    contentAlignment = Alignment.Center,
                ) { Text(com.sohum.bandlog.util.Reactions.unreadLabel(unread) ?: "$unread", fontSize = 11.sp, fontWeight = FontWeight(700), color = Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(squad.name, fontSize = 16.5.sp, fontWeight = FontWeight(750), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (members.isNotEmpty()) AvatarStack(members.map { it.name to it.avatarPath }, 22.dp)
                if (summary != null) Pill("$streak", Ember.copy(alpha = 0.14f), EmberLight) { Icon(FlameIcon, null, tint = EmberLight, modifier = Modifier.size(11.dp)) }
                if (rank == 1 && members.size > 1) Pill("#1", Gold.copy(alpha = 0.18f), GoldLight)
            }
        }
        Text("→", fontSize = 16.sp, color = p.muted, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The squad's 56 dp rounded tile: its photo, or its icon on graphite (gold for your #1 squad). */
@Composable
private fun SquadTile(squad: Squad, gold: Boolean) {
    val icon = squad.icon
    val shape = RoundedCornerShape(18.dp)
    val photo = (icon != null && icon.startsWith("photo:")) || (SQUAD_ICONS.none { it.key == icon } && !squad.coverUrl.isNullOrBlank())
    if (photo && !gold) {
        Box(Modifier.size(56.dp).clip(shape)) { SquadIconView(icon, squad.name, 56.dp, cover = squad.coverUrl) }
        return
    }
    val preset = SQUAD_ICONS.firstOrNull { it.key == icon }
    Box(
        Modifier.size(56.dp).clip(shape)
            .background(if (gold) Brush.linearGradient(0f to GoldLight, 0.55f to Gold, 1f to GoldDeep) else Brush.linearGradient(listOf(Color(0xFF26262A), Color(0xFF151517))), shape)
            .then(if (gold) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape)),
        contentAlignment = Alignment.Center,
    ) { Icon(preset?.icon ?: PeopleIcon, null, tint = if (gold) Color.Black else Color(0xFFF5F5F7), modifier = Modifier.size(22.dp)) }
}

// ---------------------------------------------------------------- leaderboard (board SquadLeaderboard)

/** The Leaderboard tab: Members / Leaderboard segmented tabs over the podium and rows, or the members list. */
@Composable
internal fun PremiumLeaderboardTab(sq: SquadViewModel, onOpenMember: (com.sohum.bandlog.data.LeaderRow, Int) -> Unit) {
    val p = palette
    var seg by remember(sq.openId) { mutableStateOf(1) }
    val places = SquadPremium.places(sq.leaders, sq.podiumChallenge)
    val challenge = !sq.podiumChallenge.isNullOrEmpty()
    Column(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(if (p.dark) Color(0xFF17130E) else Color(0xFFF7EEE6), p.bg), center = Offset(540f, 0f), radius = 1200f))) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().background(p.ink.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .border(1.dp, p.ink.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(4.dp),
        ) {
            listOf("Members", "Leaderboard").forEachIndexed { i, label ->
                val sel = seg == i
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) p.ink.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { seg = i },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted) }
            }
        }
        if (sq.leaders.isEmpty() && places.isEmpty()) {
            if (sq.pageLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
            else EmptyState("No one ranked yet", "Log a workout to light your first flame.")
            return@Column
        }
        if (seg == 0) { MembersList(sq); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 40.dp)) {
            item(key = "podium") { Podium(places, challenge) { uid -> sq.leaders.withIndex().firstOrNull { it.value.userId == uid }?.let { onOpenMember(it.value, places.indexOfFirst { pl -> pl.userId == uid } + 1) } } }
            item(key = "label") {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink.copy(alpha = 0.08f)))
                    Text(
                        if (challenge) "CHALLENGE · POINTS" else "THIS WEEK · STREAK DAYS", fontSize = 9.sp, fontWeight = FontWeight(500), letterSpacing = 2.5.sp, color = Color(0xFF8C8C92),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
            }
            items(places.drop(3).withIndex().toList(), key = { it.value.userId }) { (i, pl) ->
                GlassRow(i + 4, pl, challenge, pl.userId == Session.userId) {
                    sq.leaders.firstOrNull { it.userId == pl.userId }?.let { onOpenMember(it, i + 4) }
                }
            }
        }
    }
}

@Composable
private fun Podium(places: List<SquadPremium.Place>, challenge: Boolean, onOpen: (String) -> Unit) {
    val p = palette
    val rise = rememberMotion("podium-rise", 100, PremiumMotion.GROW_Y_MS)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Brush.radialGradient(listOf(Ember.copy(alpha = 0.18f), Color.Transparent), radius = 520f))
            .padding(start = 12.dp, end = 12.dp, top = 16.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SquadPremium.podium(places).forEach { (rank, pl) ->
            val first = rank == 1
            Column(
                Modifier.weight(if (first) 1.15f else 1f).clickable(onClickLabel = "Open ${pl.name}") { onOpen(pl.userId) }
                    .semantics(mergeDescendants = true) { contentDescription = "Place $rank, ${pl.name}, ${pl.value} ${if (challenge) "points" else "days"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (first) Crown()
                val av = if (first) 64.dp else 54.dp
                Box {
                    Box(
                        Modifier.size(av + 8.dp).background(p.bg, CircleShape).padding(3.dp)
                            .border(if (first) 2.dp else 1.dp, if (first) GoldLight else p.ink.copy(alpha = 0.2f), CircleShape).padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) { Avatar(Api.avatarUrl(pl.avatarPath), Names.initials(pl.name), av) }
                    Jewels.streakJewel(pl.streak)?.let { (cat, tier) ->
                        Jewel(cat, tier, 30.dp, Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 6.dp))
                    }
                }
                Text(pl.name.substringBefore(' '), fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                Row(
                    Modifier.padding(top = 6.dp).height(26.dp).background(p.ink.copy(alpha = 0.06f), CircleShape).border(1.dp, p.ink.copy(alpha = 0.08f), CircleShape).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(FlameIcon, null, tint = EmberLight, modifier = Modifier.size(12.dp))
                    Text(if (challenge) "${pl.value} pts" else "${pl.value} ${if (pl.value == 1) "day" else "days"}", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                }
                val h = when (rank) { 1 -> 170.dp; 2 -> 128.dp; else -> 100.dp }
                val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                Box(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(h * PremiumMotion.growY(rise.value).coerceAtLeast(0.01f)).clip(shape)
                        .background(Brush.verticalGradient(listOf(if (first) GoldLight.copy(alpha = 0.35f) else p.ink.copy(alpha = 0.14f), p.ink.copy(alpha = 0.02f))), shape)
                        .border(1.dp, p.ink.copy(alpha = 0.08f), shape),
                ) {
                    Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent))))
                    Text(
                        "$rank", fontSize = if (first) 54.sp else 42.sp, fontWeight = FontWeight(800), textAlign = TextAlign.Center,
                        color = if (first) (if (p.dark) Color(0xFFF7E8C0) else GoldDeep) else p.ink.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Crown() {
    Canvas(Modifier.size(width = 30.dp, height = 22.dp)) {
        val sx = size.width / 40f; val sy = size.height / 30f
        val path = Path().apply {
            moveTo(2f * sx, 26f * sy); lineTo(6f * sx, 6f * sy); lineTo(14f * sx, 16f * sy); lineTo(20f * sx, 2f * sy)
            lineTo(26f * sx, 16f * sy); lineTo(34f * sx, 6f * sy); lineTo(38f * sx, 26f * sy); close()
        }
        drawPath(path, Brush.linearGradient(listOf(Color(0xFFFBE7A8), Gold), start = Offset.Zero, end = Offset(size.width, size.height)))
    }
}

@Composable
private fun GlassRow(rank: Int, pl: SquadPremium.Place, challenge: Boolean, isMe: Boolean, onClick: () -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(20.dp)
    val ring = rememberMotion("row-ring-${pl.userId}", 300 + rank * 80, PremiumMotion.DRAW_MS - 600)
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(p.ink.copy(alpha = 0.07f), p.ink.copy(alpha = 0.02f))), shape)
            .border(1.dp, if (isMe) EmberLight.copy(alpha = 0.45f) else p.ink.copy(alpha = 0.07f), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$rank", fontSize = 14.sp, fontWeight = FontWeight(600), color = Color(0xFF8C8C92), modifier = Modifier.width(18.dp))
        Avatar(Api.avatarUrl(pl.avatarPath), Names.initials(pl.name), 40.dp)
        Column(Modifier.weight(1f)) {
            Row {
                Text(pl.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isMe) Text(" · you", fontSize = 15.sp, fontWeight = FontWeight(600), color = EmberLight)
            }
            Text(if (challenge) "${pl.value} points" else "${pl.value} ${if (pl.value == 1) "day" else "days"} streak", fontSize = 12.5.sp, color = Color(0xFF8C8C92))
        }
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            val track = p.ink.copy(alpha = 0.1f)
            Canvas(Modifier.size(46.dp)) {
                val sw = 4.dp.toPx(); val r = (size.minDimension - sw) / 2
                val tl = Offset(center.x - r, center.y - r)
                drawCircle(track, r, center, style = Stroke(sw))
                val sweep = 360f * pl.fraction * PremiumMotion.eased(ring.value, PremiumMotion.EasePen)
                if (sweep > 0.5f) drawArc(Ember, -90f, sweep, false, tl, Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("${(pl.fraction * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight(600), color = p.ink)
        }
    }
}

/** The Members segment: everyone in the squad (owner badge, flames) with the nudge button. */
@Composable
private fun MembersList(sq: SquadViewModel) {
    val p = palette
    val me = Session.userId
    val rows = sq.members.ifEmpty { sq.leaders.map { com.sohum.bandlog.data.MemberDetail(it.userId, it.name, it.username, it.avatarPath, it.isOwner, it.flames, "") } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.userId }) { m ->
            val shape = RoundedCornerShape(20.dp)
            val already = m.userId in sq.sent
            Row(
                Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.ink.copy(alpha = 0.07f), p.ink.copy(alpha = 0.02f))), shape)
                    .border(1.dp, p.ink.copy(alpha = 0.07f), shape).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(Api.avatarUrl(m.avatarPath), Names.initials(m.name), 40.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (m.userId == me) Text(" · you", fontSize = 15.sp, fontWeight = FontWeight(600), color = EmberLight)
                        if (m.isOwner) Text("  Owner", fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                    m.username?.let { Text("@$it", fontSize = 12.5.sp, color = Color(0xFF8C8C92), maxLines = 1) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FlameIcon, null, tint = if (m.flames > 0) EmberLight else p.muted, modifier = Modifier.size(14.dp))
                    Text(" ${m.flames}", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                }
                if (m.userId != me) {
                    Row(
                        Modifier.height(30.dp).pressable().background(if (already) p.card2 else p.btn, CircleShape).clip(CircleShape)
                            .clickable(enabled = !already) { sq.nudge(m.userId) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(FistIcon, "Nudge", tint = if (already) p.muted else p.btnInk, modifier = Modifier.size(12.dp))
                        Text(if (already) " Nudged" else " Nudge", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (already) p.muted else p.btnInk)
                    }
                }
            }
        }
    }
}
