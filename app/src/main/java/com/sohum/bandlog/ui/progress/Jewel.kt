package com.sohum.bandlog.ui.progress

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohum.bandlog.ui.motion.drawPathTrimmed
import com.sohum.bandlog.util.Jewels
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * v2.16 jewellery badge (board RefJewellery; the mini version from SquadLeaderboard): a bevelled
 * metal frame (shape by category, metal by tier), a darker grainy face and a glowing six-facet gem
 * (colour by category). Locked: a dark frame, an unlit gem and a thin ember progress line round
 * the frame. Same 100 × 100 geometry as the web's components/Jewel.tsx, drawn in Compose Canvas.
 */

private fun r2(n: Double) = (n * 100).roundToInt() / 100.0

private fun polygon(n: Int, r: Double, rot: Double): String =
    (0 until n).joinToString(" ") { i ->
        val a = Math.toRadians(rot + 360.0 / n * i)
        "${if (i == 0) "M" else "L"}${r2(50 + r * cos(a))} ${r2(50 + r * sin(a))}"
    } + " Z"

/** The frame outline per shape, in 100 × 100 units (identical strings to the web). */
internal fun jewelFramePath(shape: Jewels.Shape): String = when (shape) {
    Jewels.Shape.SHIELD -> "M50 4 L90 16 L90 50 C90 76 70 90 50 98 C30 90 10 76 10 50 L10 16 Z"
    Jewels.Shape.HEXAGON -> polygon(6, 48.0, -90.0)
    Jewels.Shape.DIAMOND -> "M50 2 L98 50 L50 98 L2 50 Z"
    Jewels.Shape.ROUND -> "M50 4 A46 46 0 1 1 49.9 4 Z"
    Jewels.Shape.OCTAGON -> polygon(8, 48.0, -112.5)
}

private val framePaths = HashMap<Jewels.Shape, Path>()
private fun frameOf(shape: Jewels.Shape): Path = framePaths.getOrPut(shape) { PathParser().parsePathString(jewelFramePath(shape)).toPath() }

private val Ember = Color(0xFFFF5B1F)

/**
 * One jewel. [progress] (0..1) is the ember line round a locked frame; [drawFraction] lets callers
 * pen-draw that line in. [glow] (0..1) scales an extra halo behind the gem (the unlock moment).
 */
@Composable
fun Jewel(
    category: Jewels.Category, tier: Jewels.Tier, size: Dp, modifier: Modifier = Modifier,
    locked: Boolean = false, progress: Float = 0f, drawFraction: Float = 1f, glow: Float = 0f, label: String? = null,
    // v2.18: seasonal jewels bring their own shape / metal / gem; badge skins (D11) swap the frame metal.
    shape: Jewels.Shape? = null, metal: LongArray? = null, gem: LongArray? = null,
) {
    val skinMetal = metal ?: com.sohum.bandlog.util.Packs.SKIN_METAL[com.sohum.bandlog.data.SocialStore.badgeSkin]
    val small = size < 44.dp
    val grain = remember { grainDots() }
    Canvas(modifier.then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier).then(Modifier.size(size))) {
        val s = this.size.minDimension / 100f
        scale(s, s, pivot = Offset.Zero) { drawJewel(category, tier, locked, progress * drawFraction, small, glow, grain, shape, skinMetal, gem) }
    }
}

private fun grainDots(): List<Triple<Float, Float, Float>> {
    val r = java.util.Random(16)
    return List(420) { Triple(r.nextFloat() * 100f, r.nextFloat() * 100f, 0.25f + r.nextFloat() * 0.5f) }
}

private fun DrawScope.drawJewel(
    category: Jewels.Category, tier: Jewels.Tier, locked: Boolean, progress: Float, small: Boolean, glow: Float,
    grain: List<Triple<Float, Float, Float>>,
    shapeOverride: Jewels.Shape? = null, metalOverride: LongArray? = null, gemOverride: LongArray? = null,
) {
    val shape = shapeOverride ?: Jewels.shapeOf(category)
    val frame = frameOf(shape)
    val m = (if (locked) Jewels.LOCKED_METAL else metalOverride ?: Jewels.metal(tier)).map { Color(it) }
    val g = (if (locked) Jewels.LOCKED_GEM else gemOverride ?: Jewels.gem(category)).map { Color(it) }
    val (mHi, mMid, mLo) = Triple(m[0], m[1], m[2])
    val (gHi, gMid, gLo) = Triple(g[0], g[1], g[2])
    val cy = if (shape == Jewels.Shape.SHIELD) 47f else 50f
    val gr = if (shape == Jewels.Shape.DIAMOND) 15f else 17f
    val b = frame.getBounds()

    // Soft drop shadow (native blur, like the web's drop-shadow filter).
    if (!small) drawIntoCanvas { c ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(if (locked) 70 else 120, 0, 0, 0)
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        c.nativeCanvas.save(); c.nativeCanvas.translate(0f, 4f); c.nativeCanvas.drawPath(frame.asAndroidPath(), p); c.nativeCanvas.restore()
    }
    // Unlock halo.
    if (glow > 0f) drawCircle(Brush.radialGradient(listOf(gHi.copy(alpha = 0.55f * glow), gMid.copy(alpha = 0.18f * glow), Color.Transparent), Offset(50f, cy), 70f), 70f, Offset(50f, cy))

    // Frame + bevel.
    drawPath(frame, Brush.linearGradient(0f to mHi, 0.45f to mMid, 1f to mLo, start = Offset(b.left, b.top), end = Offset(b.right, b.bottom)))
    drawPath(frame, if (locked) Color.White.copy(alpha = 0.12f) else mHi.copy(alpha = 0.7f), style = Stroke(1.5f))

    // Face: the frame scaled to 80 % about the centre.
    withTransform({ scale(0.8f, 0.8f, pivot = Offset(50f, 50f)) }) {
        val fb = b
        drawPath(
            frame,
            Brush.radialGradient(
                0f to mMid.copy(alpha = if (locked) 0.5f else 0.85f), 0.55f to mLo, 1f to Color(0xFF0C0C0D),
                center = Offset(fb.left + fb.width * 0.5f, fb.top + fb.height * 0.42f), radius = maxOf(fb.width, fb.height) * 0.7f,
            ),
        )
        if (!small) clipPath(frame) {
            grain.forEach { (x, y, a) -> drawCircle(Color.White.copy(alpha = 0.10f * a), 0.45f, Offset(x, y)) }
            grain.forEachIndexed { i, (x, y, a) -> if (i % 3 == 0) drawCircle(Color.Black.copy(alpha = 0.18f * a), 0.5f, Offset(100f - x, y)) }
        }
        drawPath(frame, mHi.copy(alpha = if (locked) 0.15f else 0.5f), style = Stroke(1.2f))
    }

    // Gem: glow, six facets (top-right lightest, bottom-left deepest), outlines, core, glint.
    if (!locked) drawCircle(Brush.radialGradient(0f to gHi.copy(alpha = 0.9f), 0.5f to gMid.copy(alpha = 0.35f), 1f to gMid.copy(alpha = 0f), center = Offset(50f, cy), radius = 36f), 36f, Offset(50f, cy))
    val pts = (0 until 6).map { i ->
        val a = Math.toRadians(-90.0 + 60 * i)
        Offset((50 + gr * cos(a)).toFloat(), (cy + gr * sin(a)).toFloat())
    }
    val shade = intArrayOf(0, 1, 2, 3, 2, 1)
    val facetFill = listOf(gHi, gMid, gLo, gLo)
    val outline = Path()
    for (i in 0 until 6) {
        val p = pts[i]; val q = pts[(i + 1) % 6]
        val wedge = Path().apply { moveTo(50f, cy); lineTo(p.x, p.y); lineTo(q.x, q.y); close() }
        drawPath(wedge, facetFill[shade[i]])
        outline.addPath(wedge)
    }
    drawPath(outline, if (locked) Color.White.copy(alpha = 0.08f) else gLo.copy(alpha = 0.55f), style = Stroke(0.8f, join = StrokeJoin.Round))
    drawCircle(
        Brush.radialGradient(0f to gHi, 0.55f to gMid, 1f to gLo, center = Offset(50f - gr * 0.42f * 0.2f, cy - gr * 0.42f * 0.3f), radius = gr * 0.42f * 1.5f),
        gr * 0.42f, Offset(50f, cy), alpha = if (locked) 0.5f else 0.95f,
    )
    if (!locked) drawLine(Color.White.copy(alpha = 0.8f), Offset(50f - gr * 0.55f, cy - gr * 0.55f), Offset(50f - gr * 0.1f, cy - gr * 0.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)

    // Locked: how far along, as an ember line round the frame.
    if (locked) {
        drawPath(frame, Color.White.copy(alpha = 0.08f), style = Stroke(2.5f))
        val f = progress.coerceIn(0f, 1f)
        if (f > 0.005f) drawPathTrimmed(frame, f, Ember, Stroke(2.5f, cap = StrokeCap.Round))
    }
}
