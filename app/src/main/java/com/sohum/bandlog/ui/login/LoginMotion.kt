package com.sohum.bandlog.ui.login

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

/**
 * v2.12 login motion: slow, premium entrances. Each element fades in from alpha 0, rises 22 dp and
 * (API 31+) un-blurs over ~1150 ms on a cubic-bezier(.16, 1, .3, 1) ease, staggered ~170 ms. All of
 * it is skipped when the system's animator duration scale is 0 ("Remove animations").
 * Private to the login package so names never clash with the app-wide motion helpers.
 */
internal val LoginEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
internal const val LOGIN_ENTER_MS = 1150
internal const val LOGIN_STAGGER_MS = 170

/** False when the user turned animations off (Developer options / Accessibility → Remove animations). */
internal fun loginAnimationsOn(ctx: Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) != 0f

@Composable
internal fun rememberLoginMotionOn(): Boolean {
    val ctx = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember { !inspection && loginAnimationsOn(ctx) }
}

/**
 * Staggered entrance for the [index]th element; replays whenever [key] changes (a new step).
 * [rise] is the starting offset in dp (22 by default; the floating cards use more).
 */
internal fun Modifier.loginEnter(index: Int, key: Any? = Unit, rise: Float = 22f, baseDelayMs: Int = 0): Modifier = composed {
    val on = rememberLoginMotionOn()
    val a = remember(key) { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(key, on) {
        if (on) a.animateTo(1f, tween(LOGIN_ENTER_MS, delayMillis = baseDelayMs + index * LOGIN_STAGGER_MS, easing = LoginEase))
        else a.snapTo(1f)
    }
    val density = LocalDensity.current
    val risePx = with(density) { rise.dp.toPx() }
    val blurPx = with(density) { 14.dp.toPx() }
    graphicsLayer {
        val v = a.value
        alpha = v
        translationY = (1f - v) * risePx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = (1f - v) * blurPx
            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
        }
    }
}

/**
 * A gentle idle drift for the decorative preview cards once they've landed: a few dp up and down
 * on a long, slow cycle, offset by [phase] so the cards never move in lockstep. Off with animations.
 */
internal fun Modifier.loginDrift(phase: Int, amplitudeDp: Float = 4f): Modifier = composed {
    val on = rememberLoginMotionOn()
    if (!on) return@composed this
    val t = rememberInfiniteTransition(label = "drift")
    val period = 5200 + phase * 700
    val v by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift$phase",
    )
    val px = with(LocalDensity.current) { amplitudeDp.dp.toPx() }
    graphicsLayer { translationY = kotlin.math.sin(v * Math.PI / 2).toFloat() * px }
}
