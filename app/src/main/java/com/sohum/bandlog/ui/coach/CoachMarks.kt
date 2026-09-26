package com.sohum.bandlog.ui.coach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

/**
 * v2.14 coach voices (brand v1 "Marks", custom, not stock icons):
 *  - Calm = Tide: a horizon with two swells and a low sun.
 *  - Balanced = Axis: a gyroscope, two crossed orbits round a core.
 *  - No excuses = Edge: a blade chevron over a hard line.
 * Drawn on a 24-unit grid in one [color]; [gradient] fills the sun / blade with the voice's
 * gradient (iris for Calm, ember for No excuses) as on the style cards.
 */
@Composable
fun CoachMark(style: String, size: Dp, color: Color, modifier: Modifier = Modifier, gradient: Brush? = null) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (style) {
            "calm" -> {
                // Low sun sitting on the horizon.
                val sun = Path().apply {
                    moveTo(7f * u, 12f * u)
                    arcTo(androidx.compose.ui.geometry.Rect(7f * u, 7f * u, 17f * u, 17f * u), 180f, 180f, false)
                    close()
                }
                if (gradient != null) drawPath(sun, gradient) else drawPath(sun, color, style = stroke)
                drawLine(color, Offset(2.5f * u, 12f * u), Offset(21.5f * u, 12f * u), stroke.width, StrokeCap.Round)
                // Two swells.
                for ((y, inset) in listOf(15.8f to 4.5f, 19.4f to 7.5f)) {
                    val swell = Path().apply {
                        moveTo(inset * u, y * u)
                        var x = inset
                        var up = true
                        while (x < 24f - inset - 0.01f) {
                            val nx = (x + 3f).coerceAtMost(24f - inset)
                            quadraticBezierTo((x + nx) / 2f * u, (y + if (up) -1.4f else 1.4f) * u, nx * u, y * u)
                            x = nx; up = !up
                        }
                    }
                    drawPath(swell, color, style = stroke)
                }
            }
            "no_excuses" -> {
                // A blade chevron pointing up, over a hard line.
                val blade = Path().apply {
                    moveTo(3.5f * u, 15.5f * u)
                    lineTo(12f * u, 4f * u)
                    lineTo(20.5f * u, 15.5f * u)
                    lineTo(16.6f * u, 15.5f * u)
                    lineTo(12f * u, 9.4f * u)
                    lineTo(7.4f * u, 15.5f * u)
                    close()
                }
                if (gradient != null) drawPath(blade, gradient) else drawPath(blade, color, style = stroke)
                drawLine(color, Offset(3f * u, 20f * u), Offset(21f * u, 20f * u), stroke.width * 1.3f, StrokeCap.Square)
            }
            else -> {
                // Axis: two orbits crossed at ±28°, and a core.
                val orbit = Size(20f * u, 9f * u)
                val tl = Offset(2f * u, 7.5f * u)
                rotate(-28f) { drawOval(color, tl, orbit, style = stroke) }
                rotate(28f) { drawOval(color, tl, orbit, style = stroke) }
                if (gradient != null) drawCircle(gradient, 2.6f * u, center) else drawCircle(color, 2.2f * u, center)
            }
        }
    }
}
