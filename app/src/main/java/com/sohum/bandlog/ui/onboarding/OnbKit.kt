package com.sohum.bandlog.ui.onboarding

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.theme.AccentStyle
import com.sohum.bandlog.ui.theme.EyebrowStyle
import com.sohum.bandlog.ui.theme.HeadlineStyle
import com.sohum.bandlog.ui.theme.palette

/*
 * v2.14 onboarding kit (designs BOnb*): the top bar with back + the 6-icon section pill, the ember
 * mono eyebrow, the Bricolage headline, option cards (selected = an inverted ink card), chips, the
 * full-width pill button at the bottom, and the coach's iris bubble. Brand icons are line icons,
 * drawn here from the design's paths.
 */

/** A 24-grid line icon with the brand's thin round stroke. */
fun brandIcon(name: String, paths: List<String>, stroke: Float = 1.8f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

object OnbIcons {
    val Back by lazy { brandIcon("Back", listOf("M15 18l-6-6 6-6")) }
    val Chevron by lazy { brandIcon("Chevron", listOf("M9 18l6-6-6-6")) }
    val Utensils by lazy { brandIcon("Utensils", listOf("M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2M7 2v20M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3zm0 0v7")) }
    val Flame by lazy { brandIcon("Flame", listOf("M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z")) }
    val User by lazy { brandIcon("User", listOf("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z")) }
    val Dumbbell by lazy { brandIcon("Dumbbell", listOf("M6.5 6.5l11 11M21 21l-1-1M3 3l1 1M18 22l4-4M2 6l4-4M3 10l7-7M14 21l7-7")) }
    val Brain by lazy { brandIcon("Brain", listOf("M9 3a3 3 0 0 0-3 3 3 3 0 0 0-2 5 3 3 0 0 0 2 5 3 3 0 0 0 5 2V3zM15 3a3 3 0 0 1 3 3 3 3 0 0 1 2 5 3 3 0 0 1-2 5 3 3 0 0 1-5 2")) }
    val Users by lazy { brandIcon("Users", listOf("M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM23 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8")) }
    val Instagram by lazy { brandIcon("Instagram", listOf("M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM17.5 6.5h.01")) }
    val Youtube by lazy { brandIcon("Youtube", listOf("M22 12s0-3.5-.5-5a2.6 2.6 0 0 0-1.8-1.8C18 4.7 12 4.7 12 4.7s-6 0-7.7.5A2.6 2.6 0 0 0 2.5 7C2 8.5 2 12 2 12s0 3.5.5 5a2.6 2.6 0 0 0 1.8 1.8c1.7.5 7.7.5 7.7.5s6 0 7.7-.5a2.6 2.6 0 0 0 1.8-1.8c.5-1.5.5-5 .5-5zM10 15.5v-7l6 3.5z")) }
    val Cap by lazy { brandIcon("Cap", listOf("M22 10 12 5 2 10l10 5 10-5zM6 12v5c3 2 9 2 12 0v-5")) }
    val Search by lazy { brandIcon("Search", listOf("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-4.3-4.3")) }
    val Dots by lazy { brandIcon("Dots", listOf("M5 12h.01M12 12h.01M19 12h.01"), 3f) }
    val ArrowDown by lazy { brandIcon("ArrowDown", listOf("M12 5v14M19 12l-7 7-7-7")) }
    val Plus by lazy { brandIcon("Plus", listOf("M4 12h16M12 4v16")) }
    val Lock by lazy { brandIcon("Lock", listOf("M5 11h14v11H5zM7 11V7a5 5 0 0 1 10 0v4")) }
    val Book by lazy { brandIcon("Book", listOf("M4 19.5A2.5 2.5 0 0 1 6.5 17H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15zM20 17v5H6.5A2.5 2.5 0 0 1 4 19.5")) }
    val Home by lazy { brandIcon("Home", listOf("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")) }
    val Moon by lazy { brandIcon("Moon", listOf("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")) }
    val Clock by lazy { brandIcon("Clock", listOf("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM12 6v6l4 2")) }
    val Chart by lazy { brandIcon("Chart", listOf("M3 3v18h18M7 15l4-4 3 3 6-6")) }
    val Camera by lazy { brandIcon("Camera", listOf("M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2zM12 9a4 4 0 1 0 0 8 4 4 0 0 0 0-8z")) }
    val Send by lazy { brandIcon("Send", listOf("M22 2 11 13M22 2l-7 20-4-9-9-4z")) }
    val Mic by lazy { brandIcon("Mic", listOf("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v2a7 7 0 0 1-14 0v-2M12 19v3")) }
    val Star by lazy { brandIcon("Star", listOf("M12 3l1.9 5.8H20l-4.9 3.6 1.9 5.8L12 14.6 7 18.2l1.9-5.8L4 8.8h6.1z")) }
    val Shield by lazy { brandIcon("Shield", listOf("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z")) }
    val Bell by lazy { brandIcon("Bell", listOf("M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0")) }
    val Check by lazy { brandIcon("Check", listOf("M5 12.5l4.5 4.5L19 7.5"), 2.4f) }
    val Barcode by lazy { brandIcon("Barcode", listOf("M3 5v14M7 5v14M11 5v14M15 5v14M19 5v14M21 5v14")) }
    val Leaf by lazy { brandIcon("Leaf", listOf("M11 20A7 7 0 0 1 4 13c0-6 6-9 16-9 0 10-3 16-9 16zM4 21c3-6 6-8 10-10")) }
    val Scale by lazy { brandIcon("Scale", listOf("M3 7h18M6 7l-3 7a3 3 0 0 0 6 0zM18 7l-3 7a3 3 0 0 0 6 0zM12 3v18M8 21h8")) }
    val Bolt by lazy { brandIcon("Bolt", listOf("M13 2 3 14h9l-1 8 10-12h-9z")) }
    val X by lazy { brandIcon("X", listOf("M6 6l12 12M18 6 6 18")) }

    /** The progress pill's six sections: food, streak, you, training, mind, people. */
    val SECTIONS by lazy { listOf(Utensils, Flame, User, Dumbbell, Brain, Users) }
}

/** Back button + the 6-icon section pill ([section] 0..5 is dark, the rest dimmed). */
@Composable
fun OnbTopBar(section: Int?, onBack: (() -> Unit)?) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(40.dp).background(p.card, CircleShape).clickable(onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(OnbIcons.Back, "Back", tint = p.ink, modifier = Modifier.size(20.dp))
            }
        } else Spacer(Modifier.size(40.dp))
        Spacer(Modifier.weight(1f))
        if (section != null) {
            Row(Modifier.background(p.card, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                OnbIcons.SECTIONS.forEachIndexed { i, icon ->
                    val on = i == section
                    Box(
                        Modifier.size(28.dp).background(if (on) p.btn else Color.Transparent, CircleShape).alpha(if (on || i < section) 1f else 0.45f),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (on) p.btnInk else p.ink, modifier = Modifier.size(15.dp)) }
                }
            }
        }
    }
}

/** Ember mono eyebrow, Bricolage headline (with an optional Fraunces accent phrase), grey sub. */
@Composable
fun OnbHeader(eyebrow: String?, headline: String, sub: String? = null, accent: String? = null, accentAfter: String? = null, index: Int = 0) {
    val p = palette
    Entrance(index, key = "header") {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), style = EyebrowStyle, color = p.ember)
            Text(
                buildAnnotatedString {
                    append(headline)
                    if (accent != null) withStyle(SpanStyle(fontFamily = AccentStyle.fontFamily, fontStyle = AccentStyle.fontStyle, fontWeight = FontWeight(300), color = p.ember, fontSize = 38.sp)) { append(accent) }
                    if (accentAfter != null) append(accentAfter)
                },
                style = HeadlineStyle, color = p.ink, modifier = Modifier.semantics { heading() },
            )
            if (sub != null) Text(sub, fontSize = 15.5.sp, lineHeight = 22.sp, color = p.muted)
        }
    }
}

/**
 * The screen chassis: status bar, top bar, a scrolling body and a pinned bottom (primary button,
 * optional footnote). The bottom rides above the keyboard.
 */
@Composable
fun OnbScaffold(
    section: Int?, onBack: (() -> Unit)?,
    bottom: @Composable ColumnScope.() -> Unit = {},
    scroll: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        if (section != null || onBack != null) OnbTopBar(section, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(bottom = 16.dp), content = body)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 18.dp, top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), content = bottom,
        )
    }
}

/** The full-width pill at the bottom of every screen (ink, like the designs). */
@Composable
fun OnbPrimary(text: String, onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false, bg: Color? = null, fg: Color? = null) {
    val p = palette
    Box(
        Modifier.fillMaxWidth().height(56.dp).pressable().alpha(if (enabled) 1f else 0.4f)
            .background(bg ?: p.btn, CircleShape).clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = fg ?: p.btnInk)
        else Text(text, fontSize = 17.sp, fontWeight = FontWeight(700), color = fg ?: p.btnInk)
    }
}

@Composable
fun OnbFootnote(text: String, onClick: (() -> Unit)? = null) {
    val p = palette
    Text(
        text, fontSize = 14.5.sp, color = if (onClick != null) p.ink else p.muted, fontWeight = if (onClick != null) FontWeight(600) else FontWeight(400),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick).padding(4.dp) else Modifier,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** An option card; selected inverts to an ink card. [multi] shows a square check instead of a radio. */
@Composable
fun OnbOption(title: String, sub: String?, icon: ImageVector?, selected: Boolean, onClick: () -> Unit, multi: Boolean = false, compact: Boolean = false, enabled: Boolean = true) {
    val p = palette
    val bg = if (selected) p.btn else p.card
    val fg = if (selected) p.btnInk else p.ink
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().pressable().alpha(if (enabled) 1f else 0.45f).background(bg, shape).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 11.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            val d = if (compact) 36.dp else 42.dp
            Box(Modifier.size(d).background(if (selected) Color.White.copy(alpha = 0.14f) else p.card2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight(700), color = fg)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 13.5.sp, color = if (selected) fg.copy(alpha = 0.65f) else p.muted, modifier = Modifier.padding(top = 2.dp))
        }
        val mark = if (multi) RoundedCornerShape(6.dp) else CircleShape
        val ring = if (selected) fg else if (p.dark) p.muted.copy(alpha = 0.5f) else Color(0xFFE3DED4)
        Box(Modifier.size(24.dp).border(BorderStroke(2.dp, ring), mark), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).background(fg, if (multi) RoundedCornerShape(3.dp) else CircleShape))
        }
    }
}

/** A rounded chip (sports, meal type): selected = ink. */
@Composable
fun OnbChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.pressable().background(if (selected) p.btn else p.card, CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = if (selected) p.btnInk else p.ink) }
}

/** The coach talking: an iris avatar and a soft iris bubble with an optional mono label. */
@Composable
fun IrisBubble(text: AnnotatedString, label: String? = null, modifier: Modifier = Modifier, avatar: Dp = 34.dp) {
    val p = palette
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(avatar).background(p.iris, CircleShape), contentAlignment = Alignment.Center) {
            Icon(OnbIcons.Star, null, tint = Color.White, modifier = Modifier.size(avatar * 0.5f))
        }
        Column(
            Modifier.weight(1f).background(p.irisBg, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                .border(1.dp, p.iris.copy(alpha = 0.33f), RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (label != null) Text(label.uppercase(), style = EyebrowStyle.copy(fontSize = 11.sp), color = p.iris, modifier = Modifier.padding(bottom = 6.dp))
            Text(text, fontSize = 14.5.sp, lineHeight = 21.sp, color = p.ink)
        }
    }
}

@Composable
fun IrisBubble(text: String, label: String? = null, modifier: Modifier = Modifier) = IrisBubble(AnnotatedString(text), label, modifier)

/** "**bold** plain" → an AnnotatedString (the plan's reason lines). */
fun boldMarked(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(part) } else append(part) }
}

/** A tick in an ember circle (building checklist, reveal reasons). */
@Composable
fun EmberTick(done: Boolean, size: Dp = 22.dp) {
    val p = palette
    Box(
        Modifier.size(size).then(if (done) Modifier.background(p.ember, CircleShape) else Modifier.border(2.dp, p.hair.copy(alpha = if (p.dark) 0.4f else 0.25f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(OnbIcons.Check, null, tint = p.onEmber, modifier = Modifier.size(size * 0.62f)) }
}

/** A thin determinate ring (building %). */
@Composable
fun ThinRing(progress: Float, size: Dp, stroke: Dp, color: Color, track: Color) {
    Canvas(Modifier.size(size)) {
        val sw = stroke.toPx()
        val inset = sw / 2
        val arc = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
        drawArc(track, -90f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))
