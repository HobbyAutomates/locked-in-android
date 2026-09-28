package com.sohum.bandlog.ui.tour

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.rememberMotion
import kotlin.math.roundToInt

/*
 * v2.16 guided tour (boards TourF1–TourF5): shown once over Home, after sign-up or the first time
 * v2.16 opens. A dim overlay with a rounded spotlight cut-out and a pulsing ember outline round the
 * target, and a tooltip card ("n OF 5", title, text, dots, Skip tour, an ember Next / Let's go).
 * Targets register their bounds with [tourTarget]; a stop whose target isn't on screen (e.g. the
 * coach note before the coach is live) keeps its card, centred, without a spotlight.
 */

enum class TourStop(val key: String, val title: String, val text: String) {
    CALORIES("calories", "Your day at a glance", "Calories left and your macros live here. Tap the card to flip between what’s left and what you’ve eaten."),
    COACH("coach", "Your coach", "A note every morning. Reply any time: it remembers what you tell it."),
    PLUS("plus", "Log anything with +", "Type it, say it, snap it or scan it. Hinglish is fine: “2 roti aur dal”."),
    SCAN("scan", "Scan food, labels, barcodes", "Point at your plate or a pack. We check the web for the real numbers."),
    SQUAD("squad", "Your squad", "Your friends’ meals, streaks and battles. Nudge anyone who goes quiet."),
}

/** Where each target is on screen (root coordinates), and replay requests from Preferences. */
object TourState {
    /** Live coordinates (read at draw time, so a card still rising into place is measured where it ends up). */
    val targets = mutableStateMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>()
    fun boundsOf(key: String): Rect? = targets[key]?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 1f && it.height > 1f }
    /** Bumped by Settings → Preferences → "Replay the tour". */
    var replayTick by mutableIntStateOf(0)
    fun replay() { replayTick++ }
}

/**
 * Device-side "seen", a cache of profiles.tour_seen_at (v2.17). The v2.16 "tour_v216" key in
 * profiles.milestones_seen still counts as seen when it's there, but is no longer written.
 */
object TourPrefs {
    const val PROFILE_KEY = "tour_v216"
    private const val PREFS = "tour_v216"
    fun seen(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)
    fun markSeen(ctx: Context) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply() }
}

/** v2.17: the tour is for accounts created after the v2.16 release only. */
const val TOUR_NEW_ACCOUNTS_AFTER = "2026-09-28T21:00:00Z"

/** Parses a Supabase timestamptz ("2026-09-29T10:00:00.123456+00:00", "…Z"); null when unreadable. */
fun parseInstant(s: String?): java.time.Instant? {
    val t = s?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T') ?: return null
    return runCatching { java.time.OffsetDateTime.parse(t).toInstant() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(t) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(t.replace(Regex("""([+-]\d{2})$"""), "$1:00")).toInstant() }.getOrNull()
}

/**
 * The account guard (pure, unit-tested): never seen on the profile (tour_seen_at null, no v2.16 flag)
 * AND the account was created after [TOUR_NEW_ACCOUNTS_AFTER]. An unknown created_at counts as old,
 * so existing users never see it.
 */
fun isTourEligible(createdAt: String?, tourSeenAt: String?, seenFlagV216: Boolean = false): Boolean {
    if (!tourSeenAt.isNullOrBlank() || seenFlagV216) return false
    val created = parseInstant(createdAt) ?: return false
    return created.isAfter(java.time.Instant.parse(TOUR_NEW_ACCOUNTS_AFTER))
}

/** Should the tour start now? Pure, so it's unit-tested. ("Replay the tour" bypasses this.) */
fun shouldStartTour(seenOnDevice: Boolean, eligible: Boolean, onHome: Boolean, overlayOpen: Boolean): Boolean =
    !seenOnDevice && eligible && onHome && !overlayOpen

/** Registers this element as a tour target. */
fun Modifier.tourTarget(stop: TourStop): Modifier = onGloballyPositioned { c -> TourState.targets[stop.key] = c }

private val Ember = Color(0xFFFF5B1F)
private val EmberLight = Color(0xFFFF8B5E)
private val CardBg = Color(0xFF1B1B1E)

/**
 * The overlay. [onFinish] runs on Skip, Back or the last "Let's go" (the caller marks it seen).
 */
@Composable
fun TourOverlay(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val stops = TourStop.entries
    val stop = stops[step]
    BackHandler(onBack = onFinish)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val pulse by rememberInfiniteTransition(label = "tourPulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tourPulseA")
    // Re-read the target a few times a second (cards may still be settling, the list may relayout).
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(150); tick++ } }
    val target = remember(tick, step, origin) { TourState.boundsOf(stop.key)?.translate(-origin.x, -origin.y) }
    val pad = with(density) { 6.dp.toPx() }
    val hole = target?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
    // Small targets (the + button, a tab) get a round spotlight; cards a 24 dp corner.
    val minSide = hole?.let { minOf(it.width, it.height) } ?: 0f
    val radius = with(density) { if (minSide < 90.dp.toPx()) minSide / 2 else 24.dp.toPx() }

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .semantics { contentDescription = "Tour, step ${step + 1} of ${stops.size}" },
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            if (hole != null) {
                drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(radius), blendMode = BlendMode.Clear)
                val o = with(density) { 3.dp.toPx() }
                drawRoundRect(
                    Ember.copy(alpha = pulse), Offset(hole.left - o, hole.top - o), Size(hole.width + 2 * o, hole.height + 2 * o), CornerRadius(radius + o),
                    style = Stroke(with(density) { 2.dp.toPx() }),
                )
            }
        }
        // Tooltip: below the target when it sits in the top half, above it otherwise; centred when there's none.
        val cardW = maxWidth - 40.dp
        val below = hole == null || hole.center.y < hPx / 2
        var cardH by remember { mutableIntStateOf(0) }
        val gap = with(density) { 16.dp.toPx() }
        val y = when {
            hole == null -> (hPx - cardH) / 2
            below -> hole.bottom + gap
            else -> hole.top - gap - cardH
        }.coerceIn(with(density) { 24.dp.toPx() }, (hPx - cardH - with(density) { 24.dp.toPx() }).coerceAtLeast(0f))
        val arrowX = ((hole?.center?.x ?: (wPx / 2)) - with(density) { 20.dp.toPx() }).coerceIn(with(density) { 24.dp.toPx() }, with(density) { (cardW - 24.dp).toPx() })
        val rise = rememberMotion("tour-card-$step", 0, 700)
        Box(
            Modifier.offset { IntOffset(with(density) { 20.dp.roundToPx() }, y.roundToInt()) }.width(cardW)
                .onGloballyPositioned { cardH = it.size.height }
                .graphicsLayer { val e = PremiumMotion.eased(rise.value); alpha = e; translationY = (1 - e) * 16.dp.toPx() },
        ) {
            if (hole != null) Box(
                Modifier.offset { IntOffset(arrowX.roundToInt(), if (below) -7.dp.roundToPx() else cardH - 7.dp.roundToPx()) }
                    .size(14.dp).graphicsLayer { rotationZ = 45f }.background(CardBg),
            )
            Column(
                Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .background(CardBg, RoundedCornerShape(22.dp)).padding(18.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${step + 1} OF ${stops.size}", fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.4.sp, color = EmberLight)
                    Text(
                        "Skip tour", fontSize = 13.sp, fontWeight = FontWeight(500), color = Color(0xFF9A9AA1),
                        modifier = Modifier.heightIn(min = 40.dp).clickable(onClickLabel = "Skip the tour", onClick = onFinish).padding(horizontal = 4.dp, vertical = 11.dp),
                    )
                }
                Text(stop.title, fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = Color(0xFFF5F5F7), modifier = Modifier.padding(top = 2.dp))
                Text(stop.text, fontSize = 14.5.sp, lineHeight = 21.sp, color = Color(0xFFC9C9CE), modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                        stops.indices.forEach { i ->
                            Box(Modifier.size(width = if (i == step) 18.dp else 6.dp, height = 6.dp).background(if (i == step) Ember else Color.White.copy(alpha = 0.25f), CircleShape))
                        }
                    }
                    val last = step == stops.lastIndex
                    Box(
                        Modifier.height(40.dp).background(Ember, CircleShape).clickable(onClickLabel = if (last) "Finish the tour" else "Next tip") {
                            if (last) onFinish() else step++
                        }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (last) "Let’s go" else "Next", fontSize = 14.sp, fontWeight = FontWeight(700), color = Color.White) }
                }
            }
        }
    }
}
