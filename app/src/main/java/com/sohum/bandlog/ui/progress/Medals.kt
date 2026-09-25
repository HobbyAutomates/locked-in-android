package com.sohum.bandlog.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.popIn
import com.sohum.bandlog.util.Badges
import kotlin.math.cos
import kotlin.math.sin

/*
 * v2.12 Medals v2: struck-metal medals for the badges. Earned = a knurled gold / silver / bronze
 * rim, a dark enamel centre and an engraved thin-line icon with the tier label under it. Locked =
 * a dark disc with an accent progress ring. Pure Canvas, so it scales to any size.
 */

enum class MedalTier(val label: String, val light: Color, val mid: Color, val dark: Color, val enamel: Color) {
    GOLD("GOLD", Color(0xFFFFF1C4), Color(0xFFE2B04A), Color(0xFF8A5A12), Color(0xFF3B2606)),
    SILVER("SILVER", Color(0xFFFFFFFF), Color(0xFFC9C9D1), Color(0xFF6C6C75), Color(0xFF26262A)),
    BRONZE("BRONZE", Color(0xFFFFD9B8), Color(0xFFC97A42), Color(0xFF7A3D17), Color(0xFF2E1407)),
}

/** The warm accent used sparingly on Profile (dark #FF8A3D, light #E8701F). */
val accentColor: Color @Composable get() = if (palette.bg.luminance() < 0.5f) Color(0xFFFF8A3D) else Color(0xFFE8701F)

/**
 * Tier for a badge: its place within its group, split in thirds — the easiest third bronze, the
 * middle silver, the hardest gold (streak: Rookie/Getting Serious bronze … No Days Off/Immortal
 * gold; meals and calorie goals: one of each).
 */
fun tierOf(b: Badges.Badge): MedalTier {
    val group = Badges.ALL.filter { it.group == b.group }.sortedBy { it.need }
    val i = group.indexOf(b).coerceAtLeast(0)
    return when ((i * 3) / group.size.coerceAtLeast(1)) { 0 -> MedalTier.BRONZE; 1 -> MedalTier.SILVER; else -> MedalTier.GOLD }
}

/** The engraving for a badge's group. */
fun medalIcon(g: Badges.Group): ImageVector = when (g) {
    Badges.Group.STREAK -> LineIcons.Flame
    Badges.Group.MEALS -> LineIcons.Bowl
    Badges.Group.CALORIES -> LineIcons.Leaf
}

/** An earned medal: knurled metal rim, enamel centre, engraved [icon]. */
@Composable
fun MetalMedal(tier: MedalTier, icon: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    val painter = rememberVectorPainter(icon)
    Box(modifier.size(size).shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.55f), spotColor = Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension / 76f // design units
            val c = center
            val rim = Brush.linearGradient(listOf(tier.light, tier.mid, tier.dark), start = Offset.Zero, end = Offset(this.size.width, this.size.height))
            val rimReversed = Brush.linearGradient(listOf(tier.light, tier.mid, tier.dark), start = Offset(this.size.width, this.size.height), end = Offset.Zero)
            drawCircle(rim, 37f * s, c)
            // Knurling: 72 short radial grooves around the edge.
            val groove = tier.dark.copy(alpha = 0.55f)
            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0)
                val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                drawLine(groove, Offset(c.x + ca * 33f * s, c.y + sa * 33f * s), Offset(c.x + ca * 36.5f * s, c.y + sa * 36.5f * s), strokeWidth = 1.2f * s)
            }
            drawCircle(rimReversed, 29f * s, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2A2D), tier.enamel), center = Offset(c.x - 0.15f * 52f * s, c.y - 0.2f * 52f * s), radius = 26f * s * 1.8f), 26f * s, c)
            drawCircle(rim, 23f * s, c, style = Stroke(0.7f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * s, 2.5f * s))), alpha = 0.9f)
            // Engraved icon, 22 design units square, tinted with the rim's mid tone.
            val iconSize = 22f * s
            translate(c.x - iconSize / 2f, c.y - iconSize / 2f) {
                with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(tier.mid)) }
            }
            // Specular highlight on the enamel.
            drawArc(
                Color.White.copy(alpha = 0.5f), 200f, 60f, false,
                topLeft = Offset(c.x - 22f * s, c.y - 22f * s), size = Size(44f * s, 44f * s),
                style = Stroke(1.6f * s, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * A locked medal: dark disc with the badge's icon greyed and an accent ring for [progress] (0..1).
 * [drawFraction] (0..1) lets callers draw the ring in with a pen animation.
 */
@Composable
fun LockedMedal(icon: ImageVector, progress: Float, size: Dp, modifier: Modifier = Modifier, drawFraction: Float = 1f) {
    val p = palette
    val dark = p.bg.luminance() < 0.5f
    val accent = accentColor
    val track = if (dark) Color(0xFF2C2C2E) else Color(0xFFE7E7EA)
    val disc = if (dark) Color(0xFF141415) else Color(0xFFF1F0ED)
    val painter = rememberVectorPainter(icon)
    val muted = p.muted
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 76f
        val c = center
        val r = 33f * s
        val tl = Offset(c.x - r, c.y - r)
        drawCircle(track, r, c, style = Stroke(3f * s))
        val sweep = 360f * progress.coerceIn(0f, 1f) * drawFraction.coerceIn(0f, 1f)
        if (sweep > 0.5f) drawArc(accent, -90f, sweep, false, tl, Size(r * 2, r * 2), style = Stroke(3f * s, cap = StrokeCap.Round))
        drawCircle(disc, 27f * s, c)
        val iconSize = 20f * s
        translate(c.x - iconSize / 2f, c.y - iconSize / 2f) {
            with(painter) { draw(Size(iconSize, iconSize), alpha = 0.7f, colorFilter = ColorFilter.tint(muted)) }
        }
    }
}

/** Eases a pen-draw progress for ring sweeps. */
internal fun penEased(t: Float) = PremiumMotion.eased(t, PremiumMotion.EasePen)

/**
 * One badge as a Medals v2 medal: metal when earned (pops in), a locked disc with a progress ring
 * otherwise (the ring draws in). Times itself off the enclosing Entrance via [motionKey] / [delayMs].
 */
@Composable
fun BadgeMedal(b: Badges.Badge, progress: Badges.Progress, size: Dp, motionKey: String, delayMs: Int, modifier: Modifier = Modifier) {
    val pop = com.sohum.bandlog.ui.motion.rememberMotion("$motionKey-pop", delayMs, PremiumMotion.POP_MS)
    if (progress.earned(b)) {
        MetalMedal(tierOf(b), medalIcon(b.group), size, modifier.then(Modifier.popIn(pop)))
    } else {
        val ring = com.sohum.bandlog.ui.motion.rememberMotion("$motionKey-ring", delayMs + 300, PremiumMotion.DRAW_MS - 600)
        val frac = (progress.value(b.group).toFloat() / b.need).coerceIn(0f, 1f)
        LockedMedal(LineIcons.Trophy, frac, size, modifier.then(Modifier.popIn(pop)), drawFraction = penEased(ring.value))
    }
}
