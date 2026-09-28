package com.sohum.bandlog.ui.progress

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.ShareIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.growFromLeft
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Badges

/** The twelve badges as Medals v2, grouped by tier. Earned medals are metal and can be shared. */
@Composable
fun BadgesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val progress = vm.badgeProgress
    val earned = Badges.earnedCount(progress)
    val next = nextBadge(progress)

    LaunchedEffect(Unit) { vm.loadBadgeTotals() }

    MotionScreen {
        SubPage("Badges", onBack) {
            Entrance(0) {
                Card(padding = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val best = com.sohum.bandlog.util.Jewels.top(progress)?.badge
                        if (best != null) BadgeMedal(best, progress, 64.dp, "hero", 300)
                        else Jewel(com.sohum.bandlog.util.Jewels.Category.STREAK, com.sohum.bandlog.util.Jewels.Tier.BRONZE, 64.dp, locked = true)
                        Spacer(Modifier.size(16.dp))
                        Column {
                            Text(
                                "$earned of ${Badges.ALL.size} earned",
                                fontSize = 22.sp, fontWeight = FontWeight(700), letterSpacing = (-0.7).sp, color = p.ink,
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
                Entrance(gi + 1) {
                    Column {
                        Text(
                            Badges.groupTitle(group),
                            fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                        list.chunked(3).forEachIndexed { ri, row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { ci, b ->
                                    BadgeTile(Modifier.weight(1f), b, progress, 300 + (ri * 3 + ci) * 120) {
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
            // v2.16: "Next up" sits under the grid.
            if (next != null) Entrance(Badges.Group.entries.size + 1) { Card(padding = 18.dp) { NextUpRow(next, progress) } }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** The unearned badge closest to done (by fraction), or null when every badge is earned. */
fun nextBadge(progress: Badges.Progress): Badges.Badge? = com.sohum.bandlog.util.Jewels.next(progress)?.badge

/** "k more days / meals" for a badge still to earn. */
fun badgeRemaining(b: Badges.Badge, progress: Badges.Progress): String {
    val k = (b.need - progress.value(b.group)).coerceAtLeast(0)
    return when (b.group) {
        Badges.Group.STREAK -> if (k == 1) "1 more day" else "$k more days"
        Badges.Group.MEALS -> if (k == 1) "1 more meal" else "$k more meals"
        Badges.Group.CALORIES -> if (k == 1) "1 more goal day" else "$k more goal days"
    }
}

/** "Next up: X · k more days" and an accent bar that grows from the left. */
@Composable
fun NextUpRow(
    b: Badges.Badge, progress: Badges.Progress, modifier: Modifier = Modifier, delayMs: Int = 1050,
    /** v2.17: colours for the dark Trophy wall (defaults: the theme's ink / muted / track). */
    ink: androidx.compose.ui.graphics.Color? = null, muted: androidx.compose.ui.graphics.Color? = null, track: androidx.compose.ui.graphics.Color? = null,
) {
    val p0 = palette
    val p = p0.copy(ink = ink ?: p0.ink, muted = muted ?: p0.muted, track = track ?: p0.track)
    val accent = com.sohum.bandlog.ui.theme.Brand.Ember
    val grow = rememberMotion("nextup-bar", delayMs, PremiumMotion.GROW_X_MS)
    val frac = (progress.value(b.group).toFloat() / b.need).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Next up: ${b.name}, ${badgeRemaining(b, progress)}" }) {
        Row(Modifier.fillMaxWidth()) {
            Text("Next up: ", fontSize = 13.5.sp, color = p.ink)
            Text(b.name, fontSize = 13.5.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text(badgeRemaining(b, progress), fontSize = 13.5.sp, color = p.muted)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).background(p.track, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.02f)).height(5.dp).growFromLeft(grow).background(accent, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun BadgeTile(modifier: Modifier, b: Badges.Badge, progress: Badges.Progress, delayMs: Int, onShare: () -> Unit) {
    val p = palette
    val got = progress.earned(b)
    val tier = tierOf(b)
    val accent = com.sohum.bandlog.ui.theme.Brand.EmberLight
    Card(modifier, padding = 12.dp) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeMedal(b, progress, 64.dp, "badge-${b.name}", delayMs)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (got) tier.label else "${progress.value(b.group).coerceAtMost(b.need)} OF ${b.need}",
                    fontSize = 9.5.sp, fontWeight = FontWeight(600), letterSpacing = 1.4.sp,
                    color = if (got) tier.mid else accent,
                )
                Text(
                    b.name, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (got) p.ink else p.muted,
                    textAlign = TextAlign.Center, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp),
                )
                Text(b.requirement, fontSize = 11.sp, color = p.muted, textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
            if (got) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(48.dp).clickable(onClickLabel = "Share ${b.name}", onClick = onShare),
                    contentAlignment = Alignment.TopEnd,
                ) { Icon(ShareIcon, "Share ${b.name}", tint = p.muted, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** Compact summary medal used by the Progress "More stats" badges card. */
@Composable
fun BadgeSummaryMedal(progress: Badges.Progress, size: Dp) {
    val best = com.sohum.bandlog.util.Jewels.top(progress)
    if (best != null) Jewel(best.category, best.tier, size)
    else Jewel(com.sohum.bandlog.util.Jewels.Category.STREAK, com.sohum.bandlog.util.Jewels.Tier.BRONZE, size, locked = true)
}
