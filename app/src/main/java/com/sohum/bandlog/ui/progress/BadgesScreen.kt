package com.sohum.bandlog.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ShareIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Badges
import kotlin.math.cos
import kotlin.math.sin

/** The twelve medals, grouped by tier. Earned ones burn orange and can be shared. */
@Composable
fun BadgesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val progress = vm.badgeProgress
    val earned = Badges.earnedCount(progress)

    LaunchedEffect(Unit) { vm.loadBadgeTotals() }

    SubPage("Badges", onBack) {
        Rise(0) {
            Card(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HexMedal(earned, true, 64.dp)
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(
                            "$earned of ${Badges.ALL.size} earned",
                            fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.7).sp, color = p.ink,
                        )
                        Text(
                            "Longest streak ${progress.streakDays} d · ${progress.meals} meals · ${progress.goalDays} goal days",
                            fontSize = 12.sp, color = p.muted,
                        )
                    }
                }
            }
        }

        Badges.Group.entries.forEachIndexed { gi, group ->
            val list = Badges.ALL.filter { it.group == group }
            Rise(gi + 1) {
                Column {
                    Text(
                        Badges.groupTitle(group),
                        fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    list.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { b ->
                                BadgeTile(Modifier.weight(1f), b, progress) {
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent.createChooser(
                                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, Badges.shareText(b))
                                                },
                                                "Share badge",
                                            ),
                                        )
                                    }
                                }
                            }
                            // Keep the last row aligned to the grid when it isn't full.
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BadgeTile(modifier: Modifier, b: Badges.Badge, progress: Badges.Progress, onShare: () -> Unit) {
    val p = palette
    val got = progress.earned(b)
    Card(modifier, padding = 12.dp) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HexMedal(b.need, got, 56.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    b.name, fontSize = 12.sp, fontWeight = FontWeight(700), color = if (got) p.ink else p.muted,
                    textAlign = TextAlign.Center, lineHeight = 15.sp,
                )
                Text(
                    if (got) "Earned" else "${progress.value(b.group)} / ${b.need}",
                    fontSize = 11.sp, color = if (got) p.flame else p.muted,
                    fontWeight = if (got) FontWeight(700) else FontWeight(400),
                )
            }
            if (got) {
                Icon(
                    ShareIcon, "Share ${b.name}", tint = p.muted,
                    modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clickable(onClick = onShare),
                )
            }
        }
    }
}

/**
 * Hexagonal medal with the threshold inside — flame-orange once earned, flat grey until then.
 * Drawn as a Canvas path so it scales cleanly at any size.
 */
@Composable
fun HexMedal(number: Int, earned: Boolean, size: androidx.compose.ui.unit.Dp) {
    val p = palette
    val fill = if (earned) p.flame.copy(alpha = 0.18f) else p.card2
    val edge = if (earned) p.flame else p.hair
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val path = hexPath(this)
            drawPath(path, fill)
            drawPath(path, edge, style = Stroke(width = this.size.minDimension * 0.06f))
        }
        Text(
            number.toString(),
            fontSize = (size.value * 0.30f).sp,
            fontWeight = FontWeight(800),
            letterSpacing = (-0.5).sp,
            color = if (earned) p.flame else p.muted,
        )
    }
}

/** Flat-top hexagon inscribed in the draw area. */
private fun hexPath(scope: DrawScope): Path {
    val w = scope.size.width
    val h = scope.size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) / 2f * 0.96f
    return Path().apply {
        for (i in 0 until 6) {
            // -90° start gives a point-up hexagon, like Cal AI's medal art.
            val a = Math.toRadians((60.0 * i) - 90.0)
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
