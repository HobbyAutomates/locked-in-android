package com.sohum.bandlog.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.sohum.bandlog.ui.theme.palette

/*
 * v2.14 "Hatch = remaining" (brand v1, "Ember is earned" idea 02): rings and bars draw the done
 * part solid and what's still to go as a 45° hatch instead of a faint tint. Colour-blind safe, and
 * it keeps the body monochrome. The web draws the same thing with an SVG <pattern>.
 */

/** 45° lines (bottom-left → top-right) across the whole draw area, [spacing] px apart. */
fun DrawScope.drawHatch(color: Color, spacing: Float, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    var x = -h
    while (x < w) {
        drawLine(color, Offset(x, h), Offset(x + h, 0f), strokeWidth)
        x += spacing
    }
}

/**
 * The remaining part of a ring: the annular sector from [startDeg] over [sweepDeg] (Compose arc
 * angles, 0° = 3 o'clock, clockwise) of a stroke centred on the ellipse in [topLeft]/[arcSize],
 * filled with hatch lines. A barely-there tint under the lines keeps the ring's shape readable.
 */
fun DrawScope.drawHatchedArc(startDeg: Float, sweepDeg: Float, topLeft: Offset, arcSize: Size, strokePx: Float, color: Color, tint: Color, spacing: Float, lineWidth: Float) {
    if (sweepDeg <= 0.5f) return
    val half = strokePx / 2
    val outer = Rect(topLeft.x - half, topLeft.y - half, topLeft.x + arcSize.width + half, topLeft.y + arcSize.height + half)
    val inner = Rect(topLeft.x + half, topLeft.y + half, topLeft.x + arcSize.width - half, topLeft.y + arcSize.height - half)
    val path = Path().apply {
        arcTo(outer, startDeg, sweepDeg, forceMoveTo = true)
        arcTo(inner, startDeg + sweepDeg, -sweepDeg, forceMoveTo = false)
        close()
    }
    clipPath(path) {
        drawRect(tint)
        drawHatch(color, spacing, lineWidth)
    }
}

/** A bar / pill track whose empty part is hatched. Put the solid "done" fill inside it as before. */
@Composable
fun Modifier.hatchTrack(shape: Shape = CircleShape, color: Color? = null): Modifier {
    val p = palette
    val lines = color ?: p.hatch
    val tint = p.track.copy(alpha = 0.45f)
    return this.clip(shape).drawBehind {
        drawRect(tint)
        drawHatch(lines, 5.dp.toPx(), 1.1.dp.toPx())
    }
}
