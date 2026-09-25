package com.sohum.bandlog.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
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
    // Previews / layout screenshots render one frame: show the content as it settles.
    if (androidx.compose.ui.platform.LocalInspectionMode.current) { Box(modifier) { content() }; return }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40L * index); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, Motion.spatialSlow(), label = "rise")
    Box(modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 40f }) { content() }
}

/** Black pill button (or any tint). */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 52.dp, bg: Color? = null, fg: Color? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val p = palette
    Box(
        modifier.fillMaxWidth().height(height).pressable().alpha(if (enabled) 1f else 0.5f)
            .background(bg ?: p.btn, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg ?: p.btnInk, modifier = Modifier.size(18.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp)) }
            Text(text, color = fg ?: p.btnInk, fontSize = 15.sp, fontWeight = FontWeight(700))
        }
    }
}

/**
 * Optimistic-delete placeholder: shown in place of a row right after Delete is tapped. Reads
 * "Deleted" for ~5s, then calls [onExpire] (the real delete); tapping Undo calls [onUndo]
 * instead and restores the row. If the row leaves composition first (scrolled away, screen
 * closed), the delete still goes through so it never silently comes back.
 */
@Composable
fun UndoRow(onUndo: () -> Unit, onExpire: () -> Unit) {
    val p = palette
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(5000); if (!settled) { settled = true; onExpire() } }
    DisposableEffect(Unit) { onDispose { if (!settled) { settled = true; onExpire() } } }
    Card(padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Deleted", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted)
            Text("Undo", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.btn, modifier = Modifier.clickable { settled = true; onUndo() })
        }
    }
}

/** Selectable chip: black when selected, soft grey otherwise (or a tint when [color] is given). */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null, colorBg: Color? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val p = palette
    val bg = when { selected && color != null -> colorBg ?: color.copy(alpha = 0.16f); selected -> p.btn; else -> p.card2 }
    val fg = when { selected && color != null -> color; selected -> p.btnInk; else -> p.ink }
    Box(
        modifier.height(44.dp).pressable().background(bg, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp)) }
            Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight(600) else FontWeight(500), maxLines = 1, softWrap = false)
        }
    }
}

/** iOS-style segmented control on a grey track. Never more than three options; four or more are chips. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    require(options.size <= 3) { "Segmented takes at most 3 options — use chips for more" }
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
        Text("  $value", color = color, fontSize = 12.sp, fontWeight = FontWeight(600), maxLines = 1, softWrap = false)
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
    LaunchedEffect(text) { if (!text.isNullOrBlank()) com.sohum.bandlog.data.Analytics.track("error_shown", "message" to text) }
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

/**
 * Full-screen sub-page chassis shared by every Profile detail screen: floating back pill,
 * centred title, then a scrolling body. Same shape as the Log page so pushes feel consistent.
 */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .background(p.card, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(18.dp)) }
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            Box(Modifier.size(40.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 6.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A label + trailing control row, 13 dp tall gutters, used inside grouped cards. */
@Composable
fun SettingRow(
    icon: ImageVector? = null,
    tint: Color = palette.ink,
    label: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val p = palette
    Box(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 13.dp).weight(1f)) {
                if (icon != null) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Box(Modifier.size(10.dp))
                }
                Column {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
                    if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = p.muted)
                }
            }
            trailing()
        }
    }
}

/** The "›" affordance on rows that push a new page. */
@Composable
fun Chevron() = Text("›", fontSize = 18.sp, fontWeight = FontWeight(500), color = palette.muted)

/** Small grey group heading above a card, e.g. "Account". */
@Composable
fun GroupLabel(text: String) = Text(
    text.uppercase(),
    fontSize = 11.sp,
    fontWeight = FontWeight(700),
    letterSpacing = 0.8.sp,
    color = palette.muted,
    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
)
