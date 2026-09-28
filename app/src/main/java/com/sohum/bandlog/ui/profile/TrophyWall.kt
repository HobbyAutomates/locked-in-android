package com.sohum.bandlog.ui.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.BattleRepo
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.drawPathTrimmed
import com.sohum.bandlog.ui.motion.growFromLeft
import com.sohum.bandlog.ui.motion.popIn
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.progress.Jewel
import com.sohum.bandlog.ui.progress.NextUpRow
import com.sohum.bandlog.ui.progress.jewelFramePath
import com.sohum.bandlog.ui.progress.nextBadge
import com.sohum.bandlog.util.Jewels
import kotlin.math.roundToInt

/*
 * v2.17 Trophy wall on Profile (replaces the v2.16 badge shelf and the v2.7 "Graffiti wall" card;
 * mirrors the web's TrophyWall.tsx): a dark gallery wall with a brushed-concrete grain and soft
 * spotlight pools. Earned badges hang as the v2.16 jewellery shields on small lit plinths along
 * shelves edged with a thin gold rule; locked ones are engraved outlines with their progress traced
 * in ember. Food Battle crowns get their own shelf once there is one. Same data and taps as before:
 * every badge (and the count) opens Badges, "Next up" sits underneath. The wall stays dark in both
 * themes (a lit gallery reads as premium on bone too).
 */

private val WallInk = Color(0xFFF4F1EA)
private val Gold = Color(0xFFD9B872)
private val GoldLight = Color(0xFFFBE7A8)
private val GoldDeep = Color(0xFFA8823A)
private val GoldDark = Color(0xFF5E4518)
private val Ember = Color(0xFFFF5B1F)
private val EmberLight = Color(0xFFFF8B5E)
private const val PER_SHELF = 4

private val framePaths = HashMap<Jewels.Shape, Path>()
private fun frameOf(shape: Jewels.Shape): Path = framePaths.getOrPut(shape) { PathParser().parsePathString(jewelFramePath(shape)).toPath() }

/** Deterministic brushed streaks (x, y, length, alpha) in 0..1 units. */
private val streaks: List<FloatArray> by lazy {
    val r = java.util.Random(217)
    List(140) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.08f + r.nextFloat() * 0.35f, 0.015f + r.nextFloat() * 0.035f) }
}

@Composable
internal fun TrophyWall(vm: AppViewModel, graffiti: BattleRepo.Graffiti?, onOpen: () -> Unit) {
    val progress = vm.badgeProgress
    val items = Jewels.items(progress)
        .sortedWith(compareByDescending<Jewels.Item> { it.got }.thenByDescending { if (it.got) it.tier.rank.toFloat() else it.fraction }.thenBy { it.badge.need })
    val got = items.count { it.got }
    val total = items.size
    val next = nextBadge(progress)
    val line = rememberMotion("trophy-line", 300, PremiumMotion.GROW_X_MS)
    val shape = RoundedCornerShape(26.dp)

    Box(
        Modifier.fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .drawBehind {
                // Wall: warm top light falling to near-black, then the brushed grain.
                drawRect(Brush.radialGradient(listOf(Color(0xFF2A2420), Color(0xFF151315), Color(0xFF0B0A0C)), center = Offset(size.width / 2, -size.height * 0.1f), radius = size.maxDimension * 0.9f))
                streaks.forEach { s ->
                    val y = s[1] * size.height
                    val x = s[0] * size.width
                    drawLine(Color.White.copy(alpha = s[3]), Offset(x, y), Offset(x + s[2] * size.width, y), strokeWidth = 0.6.dp.toPx())
                }
            }
            .border(1.dp, Gold.copy(alpha = 0.16f), shape)
            .semantics { contentDescription = "Trophy wall: $got of $total badges earned" },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp)) {
            // ---- header ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("COLLECTION", fontSize = 10.sp, fontWeight = FontWeight(600), letterSpacing = 2.2.sp, color = Gold)
                    Text("Trophy wall", fontSize = 21.sp, fontWeight = FontWeight(600), letterSpacing = (-0.4).sp, color = WallInk, modifier = Modifier.semantics { heading() })
                }
                Row(
                    Modifier.heightIn(min = 44.dp).clickable(onClickLabel = "All badges, $got of $total earned", onClick = onOpen).padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$got", fontSize = 13.5.sp, fontWeight = FontWeight(600), color = WallInk)
                    Text(" of $total  ›", fontSize = 13.5.sp, color = WallInk.copy(alpha = 0.72f))
                }
            }
            // Thin progress line, gold into ember.
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 10.dp).height(1.dp).background(WallInk.copy(alpha = 0.1f))) {
                val f = if (total == 0) 0f else (got.toFloat() / total).coerceIn(0f, 1f)
                if (f > 0f) Box(
                    Modifier.fillMaxWidth(f.coerceAtLeast(0.02f)).height(1.dp).growFromLeft(line)
                        .background(Brush.horizontalGradient(listOf(GoldDeep, Gold, EmberLight))),
                )
            }

            // ---- shelves ----
            items.chunked(PER_SHELF).forEachIndexed { r, row ->
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, x -> Slot(x, Modifier.weight(1f), "trophy${r * PER_SHELF + i}", 400 + (r * PER_SHELF + i) * 70, onOpen) }
                        repeat(PER_SHELF - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    ShelfRule()
                }
            }
            graffiti?.takeIf { it.total > 0 }?.let { CrownShelf(it) }

            // ---- next up ----
            if (next != null) Box(
                Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp, end = 4.dp)
                    .background(WallInk.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .border(1.dp, WallInk.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Open badges", onClick = onOpen)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) { NextUpRow(next, progress, ink = WallInk, muted = WallInk.copy(alpha = 0.6f), track = WallInk.copy(alpha = 0.12f)) }
        }
    }
}

/** One mount: a spotlight pool from above, the jewel (or its engraved outline), a small plinth, the name. */
@Composable
private fun Slot(x: Jewels.Item, modifier: Modifier, key: String, delayMs: Int, onOpen: () -> Unit) {
    val pop = rememberMotion("$key-pop", delayMs, PremiumMotion.POP_MS)
    val ring = rememberMotion("$key-ring", delayMs + 300, PremiumMotion.DRAW_MS - 600)
    val label = if (x.got) "${x.badge.name}, ${x.tier.label.lowercase()} badge" else "${x.badge.name}, locked: ${x.value.coerceAtMost(x.badge.need)} of ${x.badge.need}"
    Column(
        modifier.clickable(onClickLabel = "Open badges", onClick = onOpen).semantics(mergeDescendants = true) { contentDescription = label }
            .drawBehind {
                // Spotlight pool: warmer and brighter over an earned piece.
                val c = Offset(size.width / 2, 22.dp.toPx())
                val r = 60.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        if (x.got) listOf(Color(0x33FFE8C4), Color(0x10FFE8C4), Color.Transparent) else listOf(Color(0x10FFFFFF), Color.Transparent),
                        center = c, radius = r,
                    ),
                    radius = r, center = c,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
            if (x.got) Jewel(x.category, x.tier, 56.dp, Modifier.popIn(pop))
            else Engraved(x, Modifier.size(52.dp).popIn(pop), com.sohum.bandlog.ui.progress.penEased(ring.value))
        }
        // Plinth.
        Box(
            Modifier.padding(top = 4.dp).size(42.dp, 7.dp)
                .shadow(4.dp, RoundedCornerShape(2.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .background(Brush.verticalGradient(if (x.got) listOf(Color(0xFF3A332C), Color(0xFF1C1917)) else listOf(Color(0xFF26221F), Color(0xFF151315))), RoundedCornerShape(2.dp))
                .drawBehind { drawLine(if (x.got) GoldLight.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f), Offset(1f, 0.5f), Offset(size.width - 1f, 0.5f), strokeWidth = 1.dp.toPx()) },
        )
        Text(
            x.badge.name, fontSize = 10.5.sp, fontWeight = FontWeight(500), lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (x.got) WallInk else WallInk.copy(alpha = 0.45f), modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp).heightIn(min = 26.dp),
        )
        Text(
            if (x.got) x.tier.label.uppercase() else "${x.value.coerceAtMost(x.badge.need)} / ${x.badge.need}",
            fontSize = 9.sp, fontWeight = FontWeight(600), letterSpacing = 1.4.sp, color = if (x.got) Gold else EmberLight,
        )
    }
}

/** A locked slot: the badge's frame cut into the wall (dark groove + a highlight below), progress in ember. */
@Composable
private fun Engraved(x: Jewels.Item, modifier: Modifier, draw: Float) {
    val frame = remember(x.category) { frameOf(Jewels.shapeOf(x.category)) }
    val f = x.fraction.coerceIn(0f, 1f)
    Canvas(modifier) {
        val s = size.minDimension / 100f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(frame, Color.Black.copy(alpha = 0.28f))
            translate(0f, 1.4f) { drawPath(frame, Color.White.copy(alpha = 0.1f), style = Stroke(2f)) }
            drawPath(frame, Color.Black.copy(alpha = 0.75f), style = Stroke(2.4f))
            scale(0.8f, 0.8f, pivot = Offset(50f, 50f)) {
                drawPath(frame, Color.White.copy(alpha = 0.06f), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f))))
            }
            if (f > 0.005f) drawPathTrimmed(frame, f * draw, Ember.copy(alpha = 0.85f), Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The shelf under each row: a thin gold rule with a soft shadow falling onto the wall. */
@Composable
private fun ShelfRule() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(0f to Color.Transparent, 0.08f to Gold.copy(alpha = 0.25f), 0.5f to Gold, 0.92f to Gold.copy(alpha = 0.25f), 1f to Color.Transparent)),
        )
        Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
    }
}

/** Food Battle crowns (the old Graffiti wall data): a gold crown trophy and the last wins as engraved plaques. */
@Composable
private fun CrownShelf(g: BattleRepo.Graffiti) {
    val pop = rememberMotion("trophy-crown", 1300, PremiumMotion.POP_MS)
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).popIn(pop), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(58.dp).background(Brush.radialGradient(listOf(Color(0x47FFD678), Color.Transparent)), RoundedCornerShape(50)))
                    CrownTrophy(Modifier.size(44.dp))
                }
                Box(
                    Modifier.padding(top = 4.dp).size(42.dp, 7.dp).background(Brush.verticalGradient(listOf(Color(0xFF3A332C), Color(0xFF1C1917))), RoundedCornerShape(2.dp))
                        .drawBehind { drawLine(GoldLight.copy(alpha = 0.35f), Offset(1f, 0.5f), Offset(size.width - 1f, 0.5f), strokeWidth = 1.dp.toPx()) },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                Row {
                    Text("${g.total}", fontSize = 14.sp, fontWeight = FontWeight(600), color = Gold)
                    Text(" food battle crown${if (g.total == 1) "" else "s"}", fontSize = 14.sp, fontWeight = FontWeight(600), color = WallInk)
                }
                if (g.recent.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    g.recent.forEach { w ->
                        Column(
                            Modifier.background(Brush.verticalGradient(listOf(Gold.copy(alpha = 0.14f), Gold.copy(alpha = 0.05f))), RoundedCornerShape(6.dp))
                                .border(1.dp, Gold.copy(alpha = 0.28f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(w.date, fontSize = 9.sp, fontWeight = FontWeight(600), letterSpacing = 1.sp, color = Gold)
                            Text(w.groupName, fontSize = 11.5.sp, fontWeight = FontWeight(600), color = WallInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                            Text("${w.score.roundToInt()} pts · ${w.goalLabel}", fontSize = 10.sp, color = WallInk.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
        ShelfRule()
    }
}

/** A gold crown on a base with an ember gem (48 × 48 units, the web's CrownTrophy). */
@Composable
private fun CrownTrophy(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 48f
        scale(s, s, pivot = Offset.Zero) {
            val gold = Brush.linearGradient(listOf(GoldLight, Gold, GoldDark), start = Offset(0f, 0f), end = Offset(48f, 48f))
            val crown = Path().apply {
                moveTo(8f, 16f); lineTo(16f, 24f); lineTo(24f, 10f); lineTo(32f, 24f); lineTo(40f, 16f); lineTo(37f, 36f); lineTo(11f, 36f); close()
            }
            translate(0f, 3f) { drawPath(crown, Color.Black.copy(alpha = 0.35f)) }
            drawPath(crown, gold)
            drawPath(crown, GoldLight.copy(alpha = 0.7f), style = Stroke(1f, join = StrokeJoin.Round))
            drawRoundRect(gold, Offset(11f, 37.5f), Size(26f, 4f), androidx.compose.ui.geometry.CornerRadius(1.2f))
            drawCircle(Ember, 3.2f, Offset(24f, 27f))
            drawCircle(Color(0xFFFFB08A), 3.2f, Offset(24f, 27f), style = Stroke(0.8f))
            listOf(Offset(8f, 15f), Offset(24f, 9f), Offset(40f, 15f)).forEach { drawCircle(GoldLight, 2f, it) }
        }
    }
}
