package com.sohum.bandlog.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.countUp
import com.sohum.bandlog.ui.motion.rememberLoop
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import kotlin.math.cos
import kotlin.math.sin

/*
 * v2.12 Profile art (design canvas18 "ProfileFinal"): the monochrome weight-plates cover, the round
 * cover buttons and the three dials. All Canvas, no photos. Dark mode gets a pale cover with black
 * plates; light mode a near-black cover, as in the mockups.
 */

internal val isDarkTheme: Boolean @Composable get() = palette.bg.luminance() < 0.5f

/** Profile's own card: a flatter surface than the app Card, with a hairline edge. */
@Composable
internal fun ProfileCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, radius: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val dark = isDarkTheme
    val shape = RoundedCornerShape(radius)
    val m = if (dark) modifier.fillMaxWidth().background(Color(0xFF141415), shape).border(1.dp, Color.White.copy(alpha = 0.05f), shape)
    else modifier.fillMaxWidth().shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
        .background(Color.White, shape).border(1.dp, Color.Black.copy(alpha = 0.03f), shape)
    Column(m.padding(padding), content = content)
}

/**
 * The sculptural cover: three black weight plates (radial sheen, inner ring, polished hub, a
 * highlight arc) and a bar across, each fading in one after the other. [height] is the drawn
 * height; the art scales with the width (designed at 390 × 300).
 */
@Composable
internal fun WeightPlatesCover(modifier: Modifier = Modifier) {
    val dark = isDarkTheme
    val fades = listOf(200, 380, 560).mapIndexed { i, d -> rememberMotion("plate$i", d, PremiumMotion.FADE_MS) }
    Canvas(modifier) {
        val s = size.width / 390f
        val bg = if (dark) listOf(Color(0xFFE9E9EB), Color(0xFFBDBDC1)) else listOf(Color(0xFF1D1D1F), Color(0xFF050505))
        drawRect(Brush.linearGradient(bg, start = Offset.Zero, end = Offset(size.width, size.height)))
        data class Plate(val cx: Float, val cy: Float, val r: Float)
        val plates = listOf(Plate(70f, 60f, 92f), Plate(330f, 210f, 110f), Plate(250f, -10f, 60f))
        plates.forEachIndexed { i, pl ->
            val a = PremiumMotion.eased(fades[i].value, PremiumMotion.Ease)
            if (a <= 0f) return@forEachIndexed
            drawPlate(Offset(pl.cx * s, pl.cy * s), pl.r * s, a)
            if (i == 1) {
                // The bar, polished like the hubs, sitting across under the third plate.
                val hub = Brush.linearGradient(listOf(Color(0xFFF4F4F6), Color(0xFF77777D)), start = Offset(120f * s, 120f * s), end = Offset(290f * s, 136f * s))
                rotate(-32f, pivot = Offset(205f * s, 128f * s)) {
                    drawRoundRect(hub, Offset(120f * s, 120f * s), Size(170f * s, 16f * s), androidx.compose.ui.geometry.CornerRadius(8f * s), alpha = 0.85f * a)
                }
            }
        }
    }
}

private fun DrawScope.drawPlate(c: Offset, r: Float, alpha: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFF3A3A3D), 0.6f to Color(0xFF151516), 1f to Color(0xFF050505),
            center = Offset(c.x - 0.3f * r, c.y - 0.4f * r), radius = r * 1.6f,
        ),
        r, c, alpha = alpha,
    )
    drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.72f, c, style = Stroke(r * 0.06f), alpha = alpha)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFF4F4F6), Color(0xFF77777D)), center = Offset(c.x - 0.2f * r * 0.16f, c.y - 0.3f * r * 0.16f), radius = r * 0.16f * 1.4f),
        r * 0.16f, c, alpha = alpha,
    )
    drawArc(
        Color.White.copy(alpha = 0.25f), 200f, 70f, false,
        topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f), size = Size(r * 1.72f, r * 1.72f),
        style = Stroke(3f, cap = StrokeCap.Round), alpha = alpha,
    )
}

/** 50 dp round button that sits on the cover's bottom edge (48 dp+ touch target). */
@Composable
internal fun CoverButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isDarkTheme
    Box(
        modifier.size(50.dp).shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.12f), spotColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.12f))
            .background(if (dark) Color(0xFF1A1A1B) else Color.White, CircleShape).clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (dark) Color(0xFFF5F5F7) else Color(0xFF111111), modifier = Modifier.size(20.dp)) }
}

/** Label under a dial: thin icon + word. */
@Composable
internal fun DialLabel(icon: ImageVector, text: String) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = p.muted, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 13.5.sp, color = p.muted)
    }
}

@Composable
private fun DialValue(value: String, unit: String, size: Int = 30) {
    val p = palette
    Text(
        buildAnnotatedString {
            append(value)
            withStyle(SpanStyle(fontSize = (size / 2).sp, color = p.muted)) { append(unit) }
        },
        fontSize = size.sp, fontWeight = FontWeight(400), letterSpacing = (-1).sp, color = p.ink,
    )
}

/**
 * Streak tick-dial: 48 ticks, the first ⌈48 · current / best⌉ lit in the accent (brightening
 * clockwise), fading in one after another, with the day count counting up in the middle.
 */
@Composable
internal fun StreakDial(current: Int, best: Int, size: Dp = 112.dp) {
    val accent = accentColor
    val track = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE7E7EA)
    val lit = if (best <= 0) 0 else ((48f * current / best) + 0.5f).toInt().coerceIn(if (current > 0) 1 else 0, 48)
    val ticks: List<State<Float>> = (0 until 48).map { rememberMotion("tick$it", 390 + it * 22, PremiumMotion.TICK_MS) }
    val count = rememberMotion("streak-count", 390, PremiumMotion.COUNT_MS)
    Box(Modifier.size(size).semantics { contentDescription = "Streak $current days, best $best" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension / 112f
            for (i in 0 until 48) {
                val a = Math.toRadians(i * 7.5 - 90.0)
                val long = i % 4 == 0
                val r0 = (if (long) 44f else 44.5f) * s
                val r1 = (if (long) 52f else 49.5f) * s
                val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                val on = i < lit
                val base = if (on) accent.copy(alpha = 0.35f + 0.63f * i / 47f) else track
                val o = 0.12f + 0.88f * PremiumMotion.eased(ticks[i].value, PremiumMotion.Ease)
                drawLine(base, Offset(center.x + ca * r0, center.y + sa * r0), Offset(center.x + ca * r1, center.y + sa * r1), strokeWidth = 2f * s, cap = StrokeCap.Round, alpha = o)
            }
        }
        DialValue("${countUp(current, count.value)}", "d")
    }
}

/**
 * Protein wave-fill: a circle that fills with a softly drifting liquid to today's protein / target,
 * rising from the bottom on first view.
 */
@Composable
internal fun ProteinWaveDial(grams: Int, target: Int, size: Dp = 112.dp) {
    val dark = isDarkTheme
    val accent = accentColor
    val light = if (dark) Color(0xFFFFB27A) else Color(0xFFF4A061)
    val disc = if (dark) Color(0xFF1C1C1E) else Color.White
    val edge = if (dark) Color(0xFF2C2C2E) else Color(0xFFE7E7EA)
    val frac = if (target > 0) (grams.toFloat() / target).coerceIn(0f, 1f) else 0f
    val rise = rememberMotion("wave-fill", 490, PremiumMotion.FILL_MS)
    val drift = rememberLoop(5000)
    Box(Modifier.size(size).semantics { contentDescription = "Protein today $grams of $target grams" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension / 112f
            val r = 50f * s
            drawCircle(disc, r, center)
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
            clipPath(circle) {
                val level = center.y + r - 2 * r * frac // top of the liquid
                val lift = (1f - PremiumMotion.eased(rise.value)) * (center.y + r - level + 10f * s)
                val y0 = level + lift
                val wl = 60f * s
                val amp = 3.5f * s
                val shift = -drift.value * wl
                val wave = Path().apply {
                    var x = -wl + shift
                    moveTo(x, y0)
                    var up = true
                    while (x < this@Canvas.size.width + wl) {
                        quadraticBezierTo(x + wl / 4f, y0 + if (up) -amp * 2 else amp * 2, x + wl / 2f, y0)
                        x += wl / 2f; up = !up
                    }
                    lineTo(x, this@Canvas.size.height + 10f); lineTo(-wl + shift, this@Canvas.size.height + 10f); close()
                }
                if (frac > 0f) drawPath(wave, Brush.verticalGradient(listOf(light, accent.copy(alpha = 0.25f)), startY = y0 - amp * 2, endY = center.y + r))
            }
            drawCircle(edge, r, center, style = Stroke(1.5f * s))
        }
        DialValue("$grams", "g")
    }
}

/**
 * Weight 270° arc: a grey track from bottom-left round to bottom-right, the start → goal progress
 * drawn in with a muted-to-accent gradient, and a dot that pops at its tip.
 */
@Composable
internal fun WeightArcDial(weightText: String, progress: Float, size: Dp = 112.dp) {
    val dark = isDarkTheme
    val accent = accentColor
    val track = if (dark) Color(0xFF2C2C2E) else Color(0xFFE7E7EA)
    val muted = palette.muted
    val draw = rememberMotion("arc-draw", 590, PremiumMotion.DRAW_MS)
    val dot = rememberMotion("arc-dot", 2090, PremiumMotion.POP_MS)
    val f = progress.coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension / 112f
            val r = 50f * s
            val tl = Offset(center.x - r, center.y - r)
            val arcSize = Size(2 * r, 2 * r)
            drawArc(track, 135f, 270f, false, tl, arcSize, style = Stroke(3f * s, cap = StrokeCap.Round))
            val sweep = 270f * f * PremiumMotion.eased(draw.value, PremiumMotion.EasePen)
            if (sweep > 0.5f) {
                val brush = Brush.linearGradient(listOf(muted.copy(alpha = 0.3f), accent), start = Offset(0f, this.size.height), end = Offset(this.size.width, 0f))
                drawArc(brush, 135f, sweep, false, tl, arcSize, style = Stroke(3f * s, cap = StrokeCap.Round))
            }
            val d = dot.value
            if (f > 0f && d > 0f) {
                val a = Math.toRadians((135.0 + 270.0 * f))
                val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                drawCircle(accent, 4.5f * s * PremiumMotion.popScale(d), p, alpha = PremiumMotion.popAlpha(d))
            }
        }
        DialValue(weightText, if (weightText == "—") "" else "kg", 28)
    }
}
