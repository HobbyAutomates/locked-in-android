package com.sohum.bandlog.ui.scan

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v2.12 scan motion, private to the scan package: result cards slide up on the same
 * cubic-bezier(.16, 1, .3, 1) ease as the login, plus the viewfinder's corner brackets and the
 * sweeping scan line. Everything is still when the animator duration scale is 0.
 */
internal val ScanEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

internal fun scanAnimationsOn(ctx: Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) != 0f

@Composable
internal fun rememberScanMotionOn(): Boolean {
    val ctx = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember { !inspection && scanAnimationsOn(ctx) }
}

/** Fade + slide up from [riseDp] below, staggered by [index] (~90 ms); replays when [key] changes. */
internal fun Modifier.scanEnter(index: Int = 0, key: Any? = Unit, riseDp: Float = 48f): Modifier = composed {
    val on = rememberScanMotionOn()
    val a = remember(key) { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(key, on) {
        if (on) a.animateTo(1f, tween(760, delayMillis = index * 90, easing = ScanEase)) else a.snapTo(1f)
    }
    val px = with(LocalDensity.current) { riseDp.dp.toPx() }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * px }
}

/** Four rounded corner brackets framing the scan area. */
@Composable
internal fun CornerBrackets(modifier: Modifier = Modifier, color: Color = Color.White, arm: Dp = 34.dp, stroke: Dp = 4.dp) {
    Canvas(modifier.fillMaxSize()) {
        val a = arm.toPx(); val s = stroke.toPx(); val h = s / 2
        val w = size.width; val ht = size.height
        fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), s, StrokeCap.Round)
        l(h, h, h + a, h); l(h, h, h, h + a)
        l(w - h, h, w - h - a, h); l(w - h, h, w - h, h + a)
        l(h, ht - h, h + a, ht - h); l(h, ht - h, h, ht - h - a)
        l(w - h, ht - h, w - h - a, ht - h); l(w - h, ht - h, w - h, ht - h - a)
    }
}

/** A soft glowing line sweeping up and down the area while the phone reads the photo. */
@Composable
internal fun ScanLine(modifier: Modifier = Modifier, color: Color) {
    val on = rememberScanMotionOn()
    val t = rememberInfiniteTransition(label = "scanline")
    val y by t.animateFloat(0.08f, 0.92f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "scanY")
    Canvas(modifier.fillMaxSize()) {
        val yy = size.height * (if (on) y else 0.5f)
        val glow = 28.dp.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.28f), Color.Transparent), startY = yy - glow, endY = yy + glow),
            topLeft = Offset(0f, yy - glow), size = androidx.compose.ui.geometry.Size(size.width, glow * 2),
        )
        drawLine(color, Offset(size.width * 0.06f, yy), Offset(size.width * 0.94f, yy), 2.5.dp.toPx(), StrokeCap.Round)
    }
}
