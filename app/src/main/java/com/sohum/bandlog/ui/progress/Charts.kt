package com.sohum.bandlog.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Progress-tab charts, each drawn on a single Canvas: a left gutter with 0 / mid / max gridline
 * labels, the plot, a dashed labelled target line, and x labels underneath. Every chart grows up
 * from zero with Motion.spatialSlow() and re-runs that whenever its values change.
 */

/** Protein per day: red bars once the target is hit, soft red below it. */
@Composable
fun ProteinBars(
    values: List<Double>,
    targetG: Int,
    xLabels: List<String>,
    monthMode: Boolean,
    highlightLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val t = targetG.toDouble()
    val last = if (highlightLast) values.lastIndex else -1
    BarChart(
        values = values,
        target = t,
        xLabels = xLabels,
        barColor = { _, v -> if (t > 0 && v >= t) p.red else p.redBg },
        modifier = modifier,
        unit = " g",
        axisStep = 20.0,
        targetLabel = "target $targetG g",
        showValues = !monthMode,
        skipZeroValues = true,
        outlineIndex = last,
        boldLabelIndex = last,
        barWidthFraction = if (monthMode) 0.55f else 0.58f,
    )
}

/** Sessions per week for the last eight weeks; this week is green. */
@Composable
fun SessionBars(
    counts: List<Int>,
    target: Int,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val values = counts.map { it.toDouble() }
    BarChart(
        values = values,
        target = target.toDouble(),
        xLabels = xLabels,
        barColor = { i, v ->
            when {
                i == values.lastIndex -> p.green
                target > 0 && v >= target -> p.ink
                else -> p.track
            }
        },
        modifier = modifier,
        unit = "",
        axisStep = 2.0,
        targetLabel = "target $target",
        showValues = true,
        skipZeroValues = false,
        outlineIndex = -1,
        boldLabelIndex = counts.lastIndex,
    )
}

/**
 * Generic vertical bar chart. [barColor] picks each bar's fill; [outlineIndex] gets a 1 dp ink
 * outline; [boldLabelIndex] gets a bold ink x label. Empty strings in [xLabels] are skipped.
 */
@Composable
fun BarChart(
    values: List<Double>,
    target: Double,
    xLabels: List<String>,
    barColor: (index: Int, value: Double) -> Color,
    modifier: Modifier = Modifier,
    unit: String = "",
    axisStep: Double = 20.0,
    targetLabel: String = "target",
    showValues: Boolean = true,
    skipZeroValues: Boolean = true,
    outlineIndex: Int = -1,
    boldLabelIndex: Int = -1,
    barWidthFraction: Float = 0.58f,
) {
    val p = palette
    val measurer = rememberTextMeasurer()
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) {
        delay(120)
        grow.animateTo(1f, Motion.spatialSlow())
    }
    val style = TextStyle(fontSize = 10.sp, color = p.muted)
    val bold = TextStyle(fontSize = 10.sp, color = p.ink, fontWeight = FontWeight(700))
    val valueStyle = TextStyle(fontSize = 10.sp, color = p.muted, fontWeight = FontWeight(600))
    val valueStrong = TextStyle(fontSize = 10.sp, color = p.ink, fontWeight = FontWeight(700))
    val hair = p.hair
    val ink = p.ink
    val dash = p.muted.copy(alpha = 0.7f)
    val bg = p.card

    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val f = grow.value
        val axisMax = axisMaxFor(maxOf(values.maxOrNull() ?: 0.0, target, 1.0), axisStep)
        val plot = drawGrid(measurer, axisMax, unit, style, hair, xLabels.any { it.isNotEmpty() }, 16.dp.toPx())
        val n = values.size
        val slot = plot.width / n
        val barW = (slot * barWidthFraction).coerceAtLeast(2f)
        val stub = min(3.dp.toPx(), plot.height)
        val tops = FloatArray(n)

        values.forEachIndexed { i, v ->
            val cx = plot.left + slot * (i + 0.5f)
            val raw = plot.height * (v / axisMax).toFloat() * f
            // Spring overshoot may push past the top gridline; never past the canvas edge.
            val bh = if (v <= 0.0) stub else raw.coerceIn(stub, max(stub, plot.bottom))
            val top = plot.bottom - bh
            tops[i] = top
            val rt = min(min(8.dp.toPx(), barW / 2f), bh / 2f)
            val rb = min(min(4.dp.toPx(), barW / 2f), bh / 2f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = cx - barW / 2f, top = top, right = cx + barW / 2f, bottom = plot.bottom,
                        topLeftCornerRadius = CornerRadius(rt), topRightCornerRadius = CornerRadius(rt),
                        bottomRightCornerRadius = CornerRadius(rb), bottomLeftCornerRadius = CornerRadius(rb),
                    ),
                )
            }
            drawPath(path, barColor(i, v))
            if (i == outlineIndex) drawPath(path, ink, style = Stroke(width = 1.dp.toPx()))
        }

        drawTarget(plot, target, targetLabel, measurer, style, dash, bg)

        if (showValues) {
            values.forEachIndexed { i, v ->
                if (skipZeroValues && v <= 0.0) return@forEachIndexed
                val lay = measurer.measure(v.roundToInt().toString(), if (i == outlineIndex) valueStrong else valueStyle)
                val cx = plot.left + slot * (i + 0.5f)
                val x = (cx - lay.size.width / 2f).coerceIn(0f, (size.width - lay.size.width).coerceAtLeast(0f))
                val y = (tops[i] - lay.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                drawText(lay, topLeft = Offset(x, y))
            }
        }

        drawXLabels(plot, xLabels, n, measurer, style, bold, boldLabelIndex)
    }
}

/**
 * Consumed (orange) vs burned (green) per day on a shared kcal axis, with a soft fill under
 * each line, a dot per point and a dashed calorie-target line.
 */
@Composable
fun EnergyLineChart(
    consumed: List<Double>,
    burned: List<Double>,
    target: Double,
    xLabels: List<String>,
    highlightIndex: Int,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val measurer = rememberTextMeasurer()
    val key = consumed to burned
    val grow = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(150)
        grow.animateTo(1f, Motion.spatialSlow())
    }
    val style = TextStyle(fontSize = 10.sp, color = p.muted)
    val bold = TextStyle(fontSize = 10.sp, color = p.ink, fontWeight = FontWeight(700))
    val hair = p.hair
    val dash = p.muted.copy(alpha = 0.7f)
    val bg = p.card
    val series = listOf(consumed to p.orange, burned to p.green)

    Canvas(modifier) {
        val n = max(consumed.size, burned.size)
        if (n == 0) return@Canvas
        val f = grow.value
        val axisMax = axisMaxFor(
            maxOf(consumed.maxOrNull() ?: 0.0, burned.maxOrNull() ?: 0.0, target, 1.0),
            400.0,
        )
        val plot = drawGrid(measurer, axisMax, "", style, hair, xLabels.any { it.isNotEmpty() }, 12.dp.toPx())
        val slot = plot.width / n

        fun points(vs: List<Double>): List<Offset> = vs.mapIndexed { i, v ->
            val y = plot.bottom - plot.height * (v / axisMax).toFloat() * f
            Offset(plot.left + slot * (i + 0.5f), y.coerceIn(0f, max(0f, plot.bottom)))
        }

        val lines: List<Pair<List<Offset>, Color>> = series.map { (vs, c) -> points(vs) to c }

        // Fills first so both lines sit above both washes.
        lines.forEach { (o, c) ->
            if (o.size < 2) return@forEach
            val fill = Path().apply {
                moveTo(o.first().x, plot.bottom)
                o.forEach { lineTo(it.x, it.y) }
                lineTo(o.last().x, plot.bottom)
                close()
            }
            drawPath(fill, c.copy(alpha = 0.12f))
        }

        val r = if (n > 10) 1.8.dp.toPx() else 3.dp.toPx()
        lines.forEach { (o, c) ->
            if (o.size >= 2) {
                val line = Path().apply {
                    o.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                }
                drawPath(line, c, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            o.forEachIndexed { i, pt -> drawCircle(c, radius = if (i == highlightIndex) r * 1.4f else r, center = pt) }
        }

        drawTarget(plot, target, "target", measurer, style, dash, bg)
        drawXLabels(plot, xLabels, n, measurer, style, bold, highlightIndex)
    }
}

// ---- shared drawing helpers ----

/** Plot rectangle in px plus the value mapped to its top edge. */
private class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float, val max: Double) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
    fun y(v: Double): Float = bottom - height * (v / max).toFloat()
}

/** Round [v] up to a multiple of [step] so the max and its midpoint are tidy numbers. */
private fun axisMaxFor(v: Double, step: Double): Double = (ceil(v / step) * step).coerceAtLeast(step)

private fun axisText(v: Double, unit: String): String = if (v <= 0.0) "0" else "${v.roundToInt()}$unit"

/** Three hairline gridlines (0 / mid / max) with right-aligned labels in a left gutter. */
private fun DrawScope.drawGrid(
    measurer: TextMeasurer,
    axisMax: Double,
    unit: String,
    style: TextStyle,
    hair: Color,
    hasXLabels: Boolean,
    topPad: Float,
): Plot {
    val grid = listOf(0.0, axisMax / 2.0, axisMax)
    val layouts = grid.map { measurer.measure(axisText(it, unit), style) }
    val gap = 6.dp.toPx()
    val gutter = layouts.maxOf { it.size.width } + gap
    val xBand = if (hasXLabels) measurer.measure("0", style).size.height + 5.dp.toPx() else 2.dp.toPx()
    val plot = Plot(gutter, topPad, size.width, size.height - xBand, axisMax)
    grid.forEachIndexed { i, g ->
        val y = plot.y(g)
        drawLine(hair, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val lay = layouts[i]
        val ty = (y - lay.size.height / 2f).coerceIn(0f, (size.height - lay.size.height).coerceAtLeast(0f))
        drawText(lay, topLeft = Offset(gutter - gap - lay.size.width, ty))
    }
    return plot
}

/** Dashed target line across the plot, labelled at its right end on a card-coloured chip. */
private fun DrawScope.drawTarget(
    plot: Plot,
    target: Double,
    label: String,
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
    bg: Color,
) {
    if (target <= 0.0) return
    val y = plot.y(target)
    drawLine(
        color, Offset(plot.left, y), Offset(plot.right, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )
    val lay = measurer.measure(label, style)
    val pad = 3.dp.toPx()
    val x = plot.right - lay.size.width - pad
    val ty = (y - lay.size.height - 1.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(
        bg,
        topLeft = Offset(x - pad, ty),
        size = Size(lay.size.width + pad * 2f, lay.size.height.toFloat()),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    drawText(lay, topLeft = Offset(x, ty))
}

/**
 * X labels centred under each slot. Walks right-to-left so the newest label always wins, and
 * drops any label that would collide with the one already drawn to its right.
 */
private fun DrawScope.drawXLabels(
    plot: Plot,
    labels: List<String>,
    n: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    bold: TextStyle,
    boldIndex: Int,
) {
    if (n <= 0) return
    val slot = plot.width / n
    var limit = size.width
    for (i in labels.indices.reversed()) {
        val l = labels[i]
        if (l.isEmpty() || i >= n) continue
        val lay = measurer.measure(l, if (i == boldIndex) bold else style)
        val cx = plot.left + slot * (i + 0.5f)
        val x = (cx - lay.size.width / 2f).coerceIn(0f, (size.width - lay.size.width).coerceAtLeast(0f))
        if (x + lay.size.width > limit) continue
        drawText(lay, topLeft = Offset(x, plot.bottom + 5.dp.toPx()))
        limit = x - 3.dp.toPx()
    }
}
