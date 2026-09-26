package com.sohum.bandlog.ui.squad

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.SquadTexture
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws squad texture [index] (see [SquadTexture.PATTERNS]) over the whole draw area in [color],
 * on a [cell] px grid. Mono by design: the caller picks ink at a low alpha.
 */
fun DrawScope.drawSquadTexture(index: Int, color: Color, cell: Float) {
    val w = size.width
    val h = size.height
    val line = (cell * 0.12f).coerceAtLeast(1f)
    when (SquadTexture.PATTERNS[index.mod(SquadTexture.PATTERNS.size)]) {
        "dots" -> {
            var y = cell / 2
            var row = 0
            while (y < h + cell) {
                var x = if (row % 2 == 0) cell / 2 else cell
                while (x < w + cell) { drawCircle(color, cell * 0.16f, Offset(x, y)); x += cell }
                y += cell * 0.87f; row++
            }
        }
        "stripes" -> {
            var y = cell / 2
            while (y < h) { drawLine(color, Offset(0f, y), Offset(w, y), line * 1.6f); y += cell * 0.7f }
        }
        "grid" -> {
            var x = 0f
            while (x < w) { drawLine(color, Offset(x, 0f), Offset(x, h), line); x += cell }
            var y = 0f
            while (y < h) { drawLine(color, Offset(0f, y), Offset(w, y), line); y += cell }
        }
        "waves" -> {
            var y = cell / 2
            while (y < h + cell) {
                val p = Path()
                var x = 0f
                p.moveTo(0f, y)
                while (x <= w) { p.lineTo(x, y + sin(x / cell * 2 * PI).toFloat() * cell * 0.22f); x += cell / 8 }
                drawPath(p, color, style = Stroke(line * 1.3f))
                y += cell * 0.8f
            }
        }
        "checks" -> {
            val s = cell / 2
            var y = 0f
            var r = 0
            while (y < h) {
                var x = if (r % 2 == 0) 0f else s
                while (x < w) { drawRect(color, Offset(x, y), Size(s, s)); x += cell }
                y += s; r++
            }
        }
        "diagonal" -> {
            // Top-left → bottom-right, so it never reads as the "remaining" hatch (which leans the other way).
            var x = -h
            while (x < w) { drawLine(color, Offset(x, 0f), Offset(x + h, h), line * 1.4f); x += cell * 0.7f }
        }
        "rings" -> {
            var y = cell / 2
            while (y < h + cell) {
                var x = cell / 2
                while (x < w + cell) { drawCircle(color, cell * 0.3f, Offset(x, y), style = Stroke(line)); x += cell }
                y += cell
            }
        }
        else -> { // zigzag
            var y = cell / 2
            while (y < h + cell) {
                val p = Path()
                var x = 0f
                var up = true
                p.moveTo(0f, y)
                while (x <= w + cell) { x += cell / 2; p.lineTo(x, if (up) y - cell * 0.22f else y + cell * 0.22f); up = !up }
                drawPath(p, color, style = Stroke(line * 1.3f))
                y += cell * 0.8f
            }
        }
    }
}

/** Paints [squadId]'s texture behind this element in ink at [alpha]. */
@Composable
fun Modifier.squadTexture(squadId: String, alpha: Float = 0.14f, cell: Dp = 9.dp): Modifier {
    val c = palette.ink.copy(alpha = alpha)
    val i = SquadTexture.index(squadId)
    return this.drawBehind { drawSquadTexture(i, c, cell.toPx()) }
}
