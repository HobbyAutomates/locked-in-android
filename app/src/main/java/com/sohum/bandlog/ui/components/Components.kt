package com.sohum.bandlog.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.delay

/** White/charcoal card with the soft Cal AI shadow. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(20.dp)
    var m = modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, shape)
    if (onClick != null) m = m.pressable().clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/** Scale-down-on-press, like a native button. */
@Composable
fun Modifier.pressable(): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.spatialFast(), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = source, indication = null) {}
}

/** Fade + slide-up on first composition, staggered by [index]. */
@Composable
fun Rise(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40L * index); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, Motion.spatialSlow(), label = "rise")
    Box(modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 40f }) { content() }
}

/** Black pill button (or any tint). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 52.dp, bg: Color? = null, fg: Color? = null) {
    val p = palette
    Box(
        modifier.fillMaxWidth().height(height).pressable().alpha(if (enabled) 1f else 0.5f)
            .background(bg ?: p.btn, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg ?: p.btnInk, fontSize = 15.sp, fontWeight = FontWeight(700)) }
}

/** Selectable chip: black when selected, soft grey otherwise (or a tint when [color] is given). */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null, colorBg: Color? = null) {
    val p = palette
    val bg = when { selected && color != null -> colorBg ?: color.copy(alpha = 0.16f); selected -> p.btn; else -> p.card2 }
    val fg = when { selected && color != null -> color; selected -> p.btnInk; else -> p.ink }
    Box(
        modifier.height(38.dp).pressable().background(bg, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight(600) else FontWeight(500)) }
}

/** iOS-style segmented control on a grey track. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 38.dp) {
    val p = palette
    Row(modifier.fillMaxWidth().background(p.card2, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(height - 8.dp)
                    .shadow(if (sel) 6.dp else 0.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .background(if (sel) p.card else Color.Transparent, CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted) }
        }
    }
}

/** Progress ring; draws from zero with a spring. */
@Composable
fun Ring(fraction: Float, color: Color, size: Dp, stroke: Dp, modifier: Modifier = Modifier, center: (@Composable () -> Unit)? = null) {
    val p = palette
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120); go = true }
    val animated by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, Motion.spatialSlow(), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val track = p.track
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        center?.invoke()
    }
}

/** Streak flame with a gentle breathing motion. */
@Composable
fun Flame(color: Color, size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "flame")
    val s by t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "flameScale")
    Icon(FlameIcon, null, tint = color, modifier = modifier.size(size).graphicsLayer { scaleX = s; scaleY = s })
}

/** Macro dot + value, e.g. "● 38g" in the protein colour. */
@Composable
fun MacroDot(value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text("  $value", color = color, fontSize = 12.sp, fontWeight = FontWeight(600))
    }
}

/** Large title used at the top of each tab. */
@Composable
fun ScreenTitle(text: String) = Text(text, fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = palette.ink)

/** Grey square icon tile used on list rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, bg: Color) {
    Box(Modifier.size(56.dp).background(bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp)) }
}

@Composable
fun ErrorNote(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    val p = palette
    Box(modifier.fillMaxWidth().background(p.redBg, RoundedCornerShape(12.dp)).padding(12.dp)) { Text(text, color = p.red, fontSize = 13.sp) }
}

@Composable
fun RowSpaceBetween(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun Hair() = Box(Modifier.fillMaxWidth().height(1.dp).background(palette.hair))
