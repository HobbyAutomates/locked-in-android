package com.sohum.bandlog.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

private val CONFETTI_COLORS = listOf(
    Color(0xFF4FA3F7), Color(0xFFFFC53D), Color(0xFFFF5C8A), Color(0xFF34C759), Color(0xFFFF8A3D), Color(0xFFA78BFA),
)

private class Bit(val vx: Float, val vy: Float, val spin: Float, val w: Float, val h: Float, val color: Color, val x0: Float, val delay: Float)

/**
 * v2.6 celebration particles: ~110 paper bits burst up from the lower middle, tumble and fall
 * under gravity, and fade out over [durationMs]. Draws nothing once finished; restart with a new [key].
 */
@Composable
fun ConfettiBurst(key: Any, modifier: Modifier = Modifier, durationMs: Int = 2800, count: Int = 110) {
    val bits = remember(key) {
        val r = Random(key.hashCode())
        List(count) {
            Bit(
                vx = (r.nextFloat() - 0.5f) * 1.5f, vy = -(0.9f + r.nextFloat() * 1.1f), spin = (r.nextFloat() - 0.5f) * 900f,
                w = 6f + r.nextFloat() * 6f, h = 9f + r.nextFloat() * 9f, color = CONFETTI_COLORS[r.nextInt(CONFETTI_COLORS.size)],
                x0 = 0.35f + r.nextFloat() * 0.3f, delay = r.nextFloat() * 0.12f,
            )
        }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.snapTo(0f); t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val secsTotal = durationMs / 1000f
        val d = density
        bits.forEach { b ->
            val local = ((p - b.delay) / (1f - b.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val s = local * secsTotal
            val x = size.width * b.x0 + b.vx * size.width * 0.55f * s
            val y = size.height * 0.62f + b.vy * size.height * 0.75f * s + 0.5f * size.height * 0.9f * s * s
            val alpha = if (local > 0.7f) (1f - (local - 0.7f) / 0.3f) else 1f
            rotate(b.spin * s, Offset(x, y)) {
                drawRect(b.color.copy(alpha = alpha), topLeft = Offset(x - b.w * d / 2, y - b.h * d / 2), size = Size(b.w * d, b.h * d))
            }
        }
    }
}
