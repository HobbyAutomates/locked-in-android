package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.MuscleMap.Region

/*
 * v2.13 muscle map figure (spec §12): a front and a back human figure drawn with Compose Canvas
 * on a 100 × 200 grid, the 18 regions as simple anatomical shapes (left side drawn, right side
 * mirrored). Regions are filled with whatever colour the caller maps them to; unmapped ones stay
 * the neutral body colour.
 */

private const val GW = 100f
private const val GH = 200f

/** One region shape on the grid; [mirror] draws it on both sides. */
private class Shape(val region: Region, val front: Boolean, val mirror: Boolean = true, val build: (Path, Boolean) -> Unit)

/** x on the grid, or its mirror image across the body's centre line. */
private fun mx(x: Float, flip: Boolean) = if (flip) GW - x else x

private fun oval(cx: Float, cy: Float, rx: Float, ry: Float): (Path, Boolean) -> Unit = { p, f ->
    val c = mx(cx, f)
    p.addOval(androidx.compose.ui.geometry.Rect(c - rx, cy - ry, c + rx, cy + ry))
}
private fun poly(vararg xy: Float): (Path, Boolean) -> Unit = { p, f ->
    p.moveTo(mx(xy[0], f), xy[1]); var i = 2
    while (i < xy.size) { p.lineTo(mx(xy[i], f), xy[i + 1]); i += 2 }
    p.close()
}
private fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float): (Path, Boolean) -> Unit = { p, f ->
    val a = mx(l, f); val z = mx(r, f)
    p.addRoundRect(androidx.compose.ui.geometry.RoundRect(minOf(a, z), t, maxOf(a, z), b, androidx.compose.ui.geometry.CornerRadius(rad, rad)))
}

private val SHAPES: List<Shape> = listOf(
    // ---- front ----
    Shape(Region.TRAPS, true, build = poly(45f, 25f, 37f, 30.5f, 46f, 31f)),
    Shape(Region.FRONT_DELTS, true, build = oval(31f, 36.5f, 5.5f, 6.5f)),
    Shape(Region.SIDE_DELTS, true, build = oval(26.5f, 39f, 3.2f, 6.5f)),
    Shape(Region.CHEST, true, build = poly(36.5f, 32f, 49f, 31.5f, 49f, 46f, 39f, 47.5f, 34.5f, 42f)),
    Shape(Region.BICEPS, true, build = oval(25.5f, 53f, 4.3f, 8.5f)),
    Shape(Region.FOREARMS, true, build = oval(21.5f, 72f, 3.8f, 10.5f)),
    Shape(Region.ABS, true, mirror = false, build = rrect(42.5f, 49f, 57.5f, 82f, 4f)),
    Shape(Region.OBLIQUES, true, build = poly(36f, 50f, 41.5f, 50f, 41.5f, 81f, 37.5f, 77f)),
    Shape(Region.QUADS, true, build = oval(40.5f, 113f, 7.3f, 19.5f)),
    Shape(Region.ADDUCTORS, true, build = oval(46.8f, 104f, 2.4f, 10f)),
    Shape(Region.CALVES, true, build = oval(39.5f, 158f, 4.6f, 13.5f)),
    // ---- back ----
    Shape(Region.TRAPS, false, mirror = false, build = poly(50f, 23f, 38f, 31.5f, 50f, 47f, 62f, 31.5f)),
    Shape(Region.REAR_DELTS, false, build = oval(31f, 37f, 5.2f, 5.8f)),
    Shape(Region.SIDE_DELTS, false, build = oval(26.5f, 39f, 3.2f, 6.5f)),
    Shape(Region.UPPER_BACK, false, build = poly(38.5f, 36f, 47.5f, 40f, 47.5f, 53f, 40f, 50f)),
    Shape(Region.TRICEPS, false, build = oval(25.5f, 53f, 4.3f, 8.5f)),
    Shape(Region.FOREARMS, false, build = oval(21.5f, 72f, 3.8f, 10.5f)),
    Shape(Region.LATS, false, build = poly(35.5f, 42f, 40f, 50f, 47.5f, 56f, 45f, 70f, 39.5f, 72f, 35.5f, 57f)),
    Shape(Region.LOWER_BACK, false, mirror = false, build = rrect(43.5f, 60f, 56.5f, 82f, 4f)),
    Shape(Region.GLUTES, false, build = oval(42.5f, 91f, 7.8f, 8f)),
    Shape(Region.HAMSTRINGS, false, build = oval(40.5f, 118f, 6.8f, 16.5f)),
    Shape(Region.CALVES, false, build = oval(39.5f, 155f, 5.2f, 12.5f)),
)

/** The neutral body underneath the regions (head, torso, arms, legs). */
private fun DrawScope.body(color: Color) {
    drawCircle(color, 8.5f, Offset(50f, 13f))
    drawRect(color, Offset(46f, 20f), Size(8f, 8f))
    // Torso: shoulders → waist → hips.
    val torso = Path().apply {
        moveTo(29f, 29f); lineTo(71f, 29f); lineTo(69f, 44f); lineTo(64f, 60f); lineTo(64.5f, 84f); lineTo(67f, 96f)
        lineTo(33f, 96f); lineTo(35.5f, 84f); lineTo(36f, 60f); lineTo(31f, 44f); close()
    }
    drawPath(torso, color)
    for (side in listOf(1f, -1f)) {
        fun x(v: Float) = if (side > 0) v else GW - v
        // Arm: shoulder cap, upper arm, forearm, hand.
        drawCircle(color, 7f, Offset(x(29f), 36f))
        drawPath(Path().apply { moveTo(x(22f), 38f); lineTo(x(30f), 40f); lineTo(x(29f), 62f); lineTo(x(25f), 84f); lineTo(x(18f), 84f); lineTo(x(20.5f), 62f); close() }, color)
        drawCircle(color, 3.8f, Offset(x(21f), 88f))
        // Leg: thigh, knee, shin, foot.
        drawPath(Path().apply { moveTo(x(33f), 94f); lineTo(x(49.5f), 94f); lineTo(x(47f), 134f); lineTo(x(45f), 176f); lineTo(x(36f), 176f); lineTo(x(33.5f), 134f); close() }, color)
        drawOval(color, Offset(x(if (side > 0) 34f else 45f), 176f), Size(11f, 6f))
    }
}

/**
 * One figure ([front] or back) with each region filled from [fills]. Regions not in the map use
 * the body colour. [outline] draws thin separators so adjacent regions read apart.
 */
@Composable
fun MuscleFigure(fills: Map<Region, Color>, front: Boolean, modifier: Modifier = Modifier, description: String = "") {
    val p = palette
    val bodyColor = p.track
    val line = p.card
    Canvas(modifier.aspectRatio(GW / GH).semantics { if (description.isNotEmpty()) contentDescription = description }) {
        val s = minOf(size.width / GW, size.height / GH)
        val ox = (size.width - GW * s) / 2
        val oy = (size.height - GH * s) / 2
        withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
            body(bodyColor)
            SHAPES.filter { it.front == front }.forEach { sh ->
                val fill = fills[sh.region] ?: bodyColor
                (if (sh.mirror) listOf(false, true) else listOf(false)).forEach { flip ->
                    val path = Path().also { sh.build(it, flip) }
                    drawPath(path, fill)
                    drawPath(path, line, style = Stroke(width = 0.7f))
                }
            }
        }
    }
}

/** Front and back side by side with labels. */
@Composable
fun MuscleFigures(fills: Map<Region, Color>, modifier: Modifier = Modifier, description: String = "") {
    val p = palette
    Row(modifier.fillMaxWidth().semantics { if (description.isNotEmpty()) contentDescription = description }, horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(true, false).forEach { front ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MuscleFigure(fills, front, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                Text(if (front) "Front" else "Back", fontSize = 11.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Primary regions in solid accent, secondary lighter (an exercise or a routine day). */
@Composable
fun targetFills(primary: List<Region>, secondary: List<Region>): Map<Region, Color> {
    val a = accentColor
    return secondary.associateWith { a.copy(alpha = 0.38f) } + primary.associateWith { a }
}
