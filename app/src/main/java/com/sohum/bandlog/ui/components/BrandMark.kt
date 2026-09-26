package com.sohum.bandlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.sohum.bandlog.ui.theme.Brand

/**
 * v2.14 brand mark: the Locked In padlock (web public/icon.svg, viewBox 108): an ink tile, a bone
 * shackle and body, and an ember keyhole. [tile] = false draws the lock alone (on an ink surface).
 */
@Composable
fun LockedInMark(size: Dp, modifier: Modifier = Modifier, tile: Boolean = true, ink: Color = Brand.Ink, bone: Color = Brand.Bone) {
    val shackle = remember { PathParser().parsePathString("M40 52V40a14 14 0 0 1 28 0v12").toPath() }
    val body = remember { PathParser().parsePathString("M34 50h40a5 5 0 0 1 5 5v24a5 5 0 0 1-5 5H34a5 5 0 0 1-5-5V55a5 5 0 0 1 5-5z").toPath() }
    val keyhole = remember { PathParser().parsePathString("M54 60a4.5 4.5 0 0 1 2.5 8.3L58 76h-8l1.5-7.7A4.5 4.5 0 0 1 54 60z").toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 108f
        scale(k, k, pivot = Offset.Zero) {
            if (tile) drawRoundRect(ink, Offset.Zero, androidx.compose.ui.geometry.Size(108f, 108f), CornerRadius(24f, 24f))
            drawPath(shackle, bone, style = Stroke(7f, cap = StrokeCap.Round))
            drawPath(body, bone)
            drawPath(keyhole, Brand.Ember)
        }
    }
}
