package com.sohum.bandlog.ui.progress

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.rememberLoop
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.util.Jewels

/**
 * v2.16 badges seen on this device, so the unlock moment plays once per badge. The first time
 * (nothing stored) every badge already earned is recorded silently: only badges earned from then
 * on get the moment.
 */
object BadgeSeenStore {
    private const val PREFS = "badges_v216"
    private const val KEY = "seen"

    fun seen(ctx: Context): Set<String>? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, null)?.toSet()

    fun add(ctx: Context, ids: Collection<String>) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = prefs.getStringSet(KEY, null)?.toSet() ?: emptySet()
        prefs.edit().putStringSet(KEY, cur + ids).apply()
    }
}

/**
 * Watches the badge progress (once the all-time totals are in) and shows [BadgeUnlockMoment] for
 * each newly earned badge, one after another. Sits at the top of the shell.
 */
@Composable
fun BadgeUnlockHost(vm: AppViewModel, paused: Boolean = false) {
    val ctx = LocalContext.current
    val queue = remember { androidx.compose.runtime.mutableStateListOf<Jewels.Item>() }
    LaunchedEffect(vm.loadedOnce) { if (vm.loadedOnce && !vm.badgeTotalsLoaded) vm.loadBadgeTotals() }
    val progress = vm.badgeProgress
    LaunchedEffect(vm.badgeTotalsLoaded, progress) {
        if (!vm.badgeTotalsLoaded) return@LaunchedEffect
        val seen = BadgeSeenStore.seen(ctx)
        if (seen == null) {
            BadgeSeenStore.add(ctx, Jewels.items(progress).filter { it.got }.map { it.id })
            return@LaunchedEffect
        }
        val fresh = Jewels.newlyEarned(progress, seen).filter { n -> queue.none { it.id == n.id } }
        if (fresh.isNotEmpty()) {
            BadgeSeenStore.add(ctx, fresh.map { it.id })
            // Best (highest tier) first.
            queue.addAll(fresh.sortedByDescending { it.tier.rank })
        }
    }
    if (paused) return
    val current = queue.firstOrNull() ?: return
    BadgeUnlockMoment(current) { queue.removeAt(0) }
}

/**
 * The unlock moment (board RefJewellery): a dark vignette over everything, "Here's some
 * jewellery." (or one of its variants), the big badge popping in with a glow, a one-line caption
 * and a close button.
 */
@Composable
fun BadgeUnlockMoment(item: Jewels.Item, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    MotionScreen {
        val headline = rememberMotion("unlock-head-${item.id}", 0, PremiumMotion.ENTRANCE_MS)
        val pop = rememberMotion("unlock-pop-${item.id}", 250, 900)
        val caption = rememberMotion("unlock-cap-${item.id}", 900, PremiumMotion.ENTRANCE_MS)
        val breathe = rememberLoop(3200)
        Box(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(0f to Color(0xFF3A3D42), 0.55f to Color(0xFF1C1D20), 1f to Color(0xFF0E0E10), radius = 1400f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .semantics { contentDescription = "${Jewels.unlockLine(item.id)} ${Jewels.unlockCaption(item.badge)}" },
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
                    Box(
                        Modifier.size(48.dp).clickable(onClickLabel = "Close", onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(34.dp).border(2.5.dp, Color.White.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(CrossIcon, "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Text(
                    Jewels.unlockLine(item.id), fontSize = 34.sp, fontWeight = FontWeight(800), lineHeight = 38.sp, letterSpacing = (-0.5).sp, color = Color.White,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp).graphicsLayer {
                        val e = PremiumMotion.eased(headline.value); alpha = e; translationY = (1 - e) * 20.dp.toPx()
                    },
                )
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val t = pop.value
                    val glow = 0.75f + 0.25f * kotlin.math.sin(breathe.value * 2 * Math.PI).toFloat()
                    Jewel(
                        item.category, item.tier, 260.dp,
                        Modifier.graphicsLayer {
                            val s = PremiumMotion.keyframes(t, floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0.7f, 1.05f, 1f), PremiumMotion.EaseBack)
                            scaleX = s; scaleY = s; alpha = (t / 0.6f).coerceIn(0f, 1f)
                        },
                        glow = glow * t,
                    )
                }
                Text(
                    Jewels.unlockCaption(item.badge), fontSize = 20.sp, lineHeight = 29.sp, color = Color(0xFFB9BCC2), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).graphicsLayer { alpha = PremiumMotion.eased(caption.value) },
                )
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}
