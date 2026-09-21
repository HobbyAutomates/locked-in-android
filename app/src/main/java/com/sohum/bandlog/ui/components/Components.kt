package com.sohum.bandlog.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.theme.OverlineStyle

/** Elevated card surface used for every content block. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = m.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Small uppercase mono label above a block. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = OverlineStyle, color = color, modifier = modifier)
}

/** Selectable pill; tinted with [color] when selected. */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) color.copy(alpha = 0.18f) else cs.surfaceVariant
    val fg = if (selected) color else cs.onSurface
    val border = if (selected) color.copy(alpha = 0.6f) else cs.outlineVariant
    Box(
        modifier
            .background(bg, CircleShape)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight(600))
    }
}

/** Static tinted tag (non-interactive), e.g. a muscle on a workout card. */
@Composable
fun Tag(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight(600))
    }
}

/** Progress ring with the value in the middle. */
@Composable
fun Ring(
    value: Double,
    target: Double,
    label: String,
    unit: String,
    color: Color,
    size: Dp = 112.dp,
    stroke: Dp = 10.dp,
) {
    val cs = MaterialTheme.colorScheme
    val fraction = if (target <= 0) 0f else (value / target).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, Motion.spatialSlow(), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val track = cs.surfaceVariant
            Canvas(Modifier.size(size)) {
                val sw = stroke.toPx()
                val inset = sw / 2
                val arcSize = Size(this.size.width - sw, this.size.height - sw)
                drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                if (animated > 0f) {
                    drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${value.toInt()}", fontSize = 24.sp, fontWeight = FontWeight(800), color = cs.onSurface, letterSpacing = (-0.5).sp)
                Text("/ ${target.toInt()} $unit", fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = cs.onSurfaceVariant)
    }
}

/** Big number + caption stat block. */
@Composable
fun Stat(value: String, caption: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight(800), color = accent ?: cs.onSurface, letterSpacing = (-0.8).sp)
        Text(caption, fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
fun SectionGap() = Spacer(Modifier.height(14.dp))

/** Inline error banner. */
@Composable
fun ErrorNote(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth()
            .background(cs.error.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) { Text(text, color = cs.error, fontSize = 13.sp) }
}

@Composable
fun RowSpaceBetween(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { content() }
}
