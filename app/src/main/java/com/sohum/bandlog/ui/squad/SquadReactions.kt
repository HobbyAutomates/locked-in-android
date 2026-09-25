package com.sohum.bandlog.ui.squad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.PostReactor
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Names
import com.sohum.bandlog.util.Reactions

/*
 * v2.11 squad reactions + read receipts (Android). Same six emojis, rules and wording as the web
 * (src/components/SquadReactions.tsx): one reaction per person per post, another emoji swaps it,
 * the same one removes it. Pure rules live in util/Reactions.kt.
 */

/** Places the pop-up above its anchor (below when there's no room), aligned to its start or end. */
private class AboveAnchor(private val alignEnd: Boolean, private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val rawX = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = rawX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (above >= 0) above else (anchorBounds.bottom + gapPx).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/**
 * The long-press pop-up: the six emojis (mine ringed) and, for posts you may delete, "Delete"
 * below them — the v2.9 long-press Delete now lives here.
 */
@Composable
internal fun ReactionPopup(mine: String?, alignEnd: Boolean, onPick: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val p = palette
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Popup(popupPositionProvider = remember(alignEnd, gap) { AboveAnchor(alignEnd, gap) }, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.padding(4.dp).shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = p.shadow, spotColor = p.shadow)
                .background(p.card, RoundedCornerShape(24.dp)).padding(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Reactions.ALL.forEach { e ->
                    val sel = e == mine
                    Box(
                        Modifier.size(46.dp).background(if (sel) p.card2 else Color.Transparent, CircleShape)
                            .clickable { onDismiss(); onPick(e) }
                            .semantics { contentDescription = if (sel) "Remove $e" else "React $e" },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 26.sp) }
                }
            }
            if (onDelete != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(p.hair))
                Text(
                    "Delete", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.red,
                    modifier = Modifier.fillMaxWidth().clickable { onDismiss(); onDelete() }.padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
        }
    }
}

/** "❤️ 3  🔥 2" under a post; mine highlighted. Tapping any chip opens who reacted. */
@Composable
internal fun ReactionChipsRow(state: Reactions.State, alignEnd: Boolean = false, onOpen: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val p = palette
    val chips = Reactions.chips(state)
    if (chips.isEmpty() && trailing == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, if (alignEnd) Alignment.End else Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
        chips.forEach { c ->
            Row(
                Modifier.height(28.dp).background(if (c.mine) p.blueBg else p.card2, CircleShape)
                    .border(1.5.dp, if (c.mine) p.blue else Color.Transparent, CircleShape)
                    .clickable(onClick = onOpen).padding(horizontal = 8.dp)
                    .semantics { contentDescription = "${c.emoji} ${c.count}${if (c.mine) ", including you" else ""}. See who reacted" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.emoji, fontSize = 13.sp)
                Text(" ${c.count}", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (c.mine) p.blue else p.ink)
            }
        }
        trailing?.invoke()
    }
}

/** The small "+😊" that opens the bar (Feed posts). */
@Composable
internal fun AddReactionChip(onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.height(28.dp).pressable().background(p.card2, CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp)
            .semantics { contentDescription = "Add a reaction" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.muted)
        Text("😊", fontSize = 13.sp)
    }
}

/** Who reacted with what; your own row can be tapped to remove your reaction. */
@Composable
internal fun ReactorsSheet(rows: List<PostReactor>?, me: String?, onRemoveMine: () -> Unit, onDismiss: () -> Unit) {
    val p = palette
    var filter by remember { mutableStateOf<String?>(null) }
    BottomSheet(title = "Reactions", onDismiss = onDismiss) {
        if (rows == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.muted) }
            return@BottomSheet
        }
        if (rows.isEmpty()) { Text("No reactions yet", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(vertical = 16.dp)); return@BottomSheet }
        val counts = rows.groupingBy { it.emoji }.eachCount()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip("All ${rows.size}", filter == null) { filter = null }
            Reactions.ALL.filter { (counts[it] ?: 0) > 0 }.forEach { e -> FilterChip("$e ${counts[e]}", filter == e) { filter = e } }
        }
        rows.filter { filter == null || it.emoji == filter }.sortedBy { if (it.userId == me) 0 else 1 }.forEach { r ->
            val mine = r.userId == me
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (mine) Modifier.clickable { onRemoveMine(); onDismiss() } else Modifier).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(Api.avatarUrl(r.avatarPath), Names.initials(r.name), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (mine) "You" else r.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (mine) "Tap to remove" else r.username?.let { "@$it" } ?: timeAgo(r.createdAt), fontSize = 12.sp, color = p.muted, maxLines = 1)
                }
                Text(r.emoji, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(Modifier.height(32.dp).background(if (selected) p.btn else p.card2, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (selected) p.btnInk else p.ink, maxLines = 1)
    }
}

/** Single grey ✓ (sent) or blue ✓✓ (seen by some / everyone). */
@Composable
internal fun Ticks(state: Reactions.Seen, size: Dp = 12.dp) {
    val p = palette
    val color = if (state == Reactions.Seen.SENT) p.muted else p.blue
    val double = state != Reactions.Seen.SENT
    Canvas(Modifier.size(width = if (double) size * 19f / 16f else size, height = size)) {
        val u = this.size.height / 16f
        fun tick(pts: List<Pair<Float, Float>>) {
            val path = Path().apply { moveTo(pts[0].first * u, pts[0].second * u); pts.drop(1).forEach { lineTo(it.first * u, it.second * u) } }
            drawPath(path, color, style = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        tick(listOf(1.5f to 8.5f, 5f to 12f, 12.5f to 4f))
        if (double) tick(listOf(8.5f to 11f, 9.5f to 12f, 17f to 4f))
    }
}

/** Who has seen my latest message (with their read time) and who hasn't yet. */
@Composable
internal fun SeenSheet(seen: Reactions.SeenBy, onDismiss: () -> Unit) {
    val p = palette
    BottomSheet(title = "Message info", onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Ticks(if (seen.seen.isEmpty()) Reactions.Seen.SENT else Reactions.Seen.ALL, 13.dp)
            Text("  Seen by ${seen.seen.size}", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted)
        }
        if (seen.seen.isEmpty()) Text("Nobody yet", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(vertical = 8.dp))
        seen.seen.forEach { r -> SeenRow(r, r.lastReadAt?.let { timeAgo(it) }.orEmpty(), dim = false) }
        if (seen.unseen.isNotEmpty()) {
            Text("Not seen yet", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            seen.unseen.forEach { r -> SeenRow(r, "", dim = true) }
        }
    }
}

@Composable
private fun SeenRow(r: Reactions.ReadRow, time: String, dim: Boolean) {
    val p = palette
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (dim) 0.7f else 1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(Api.avatarUrl(r.avatarPath), Names.initials(r.name), 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(r.name, fontSize = 15.sp, fontWeight = FontWeight(if (dim) 600 else 700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (time.isNotEmpty()) Text(time, fontSize = 12.sp, color = p.muted)
    }
}

/** A small count badge (squad list, the Chat tab). */
@Composable
internal fun UnreadBadge(text: String, description: String, modifier: Modifier = Modifier) {
    val p = palette
    Box(
        modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).background(p.btn, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight(800), color = p.btnInk, maxLines = 1) }
}
