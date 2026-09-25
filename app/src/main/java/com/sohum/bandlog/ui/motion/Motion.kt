package com.sohum.bandlog.ui.motion

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * v2.12 "premium" motion — slow, soft, one-time choreography for whole screens.
 *
 * A port of the CSS keyframes in the v2.12 design canvas (ProgressMotion2Dark): cards rise out of a
 * blur, line charts draw like a pen, numbers count up, dots pop on a spring, tiles drop in, bars
 * grow, and the BMI marker swings before it settles. Everything here is generic — use it on any
 * screen.
 *
 * How it fits together
 *  1. Wrap a screen in [MotionScreen] (or provide [rememberMotionSession] yourself). The session
 *     is the screen's clock: it remembers when each animated element first appeared, so an element
 *     animates once on the first composition of the screen and never again on recomposition,
 *     scrolling back into a LazyColumn, or data refreshes. Leave the screen and come back and it
 *     plays again.
 *  2. Wrap each card in [Entrance] (index = its place in the stagger). The card rises; everything
 *     inside can hang sub-animations off the card's own start time with [rememberMotion].
 *  3. [rememberMotion] returns a State<Float> that runs 0 → 1 linearly over its duration. Read it
 *     inside draw / graphicsLayer lambdas (so only the draw phase re-runs each frame) and shape it
 *     with an easing ([PremiumMotion.eased]) or one of the keyframe curves here.
 *
 * Accessibility: when the system "Remove animations" setting is on
 * (Settings.Global.ANIMATOR_DURATION_SCALE == 0) every progress is pinned at 1, i.e. the final
 * state is shown straight away. Previews (LocalInspectionMode) do the same.
 */

/** Timings and curves, lifted from the design canvas' CSS. */
object PremiumMotion {
    /** cubic-bezier(.16,1,.3,1): a long, soft ease-out ("expo out"). Rises, drops, growing bars. */
    val EaseOutSoft: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** cubic-bezier(.65,0,.35,1): ease-in-out for the pen stroke of line charts. */
    val EasePen: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    /** cubic-bezier(.34,1.56,.64,1): overshooting "back" ease, used by pops. */
    val EaseBack: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    /** cubic-bezier(.45,0,.2,1): the BMI marker's swing. */
    val EaseSwing: Easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    /** Plain CSS `ease`, for fades. */
    val Ease: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    const val ENTRANCE_MS = 1150
    const val STAGGER_MS = 170
    const val FADE_MS = 900
    const val DRAW_MS = 1900
    const val POP_MS = 800
    const val DROP_MS = 1000
    const val GROW_Y_MS = 1100
    const val GROW_X_MS = 1200
    const val SWEEP_MS = 2200
    const val COUNT_MS = 1800
    const val FILL_MS = 1800
    const val TICK_MS = 500

    /** Apply [easing] to a linear 0..1 [t]. */
    fun eased(t: Float, easing: Easing = EaseOutSoft): Float = easing.transform(t.coerceIn(0f, 1f))

    /**
     * CSS-style keyframes: [stops] are the keyframe offsets (0..1, ascending, first 0 and last 1),
     * [values] the value at each stop. Like CSS, [easing] applies to each segment separately.
     */
    fun keyframes(t: Float, stops: FloatArray, values: FloatArray, easing: Easing = EaseOutSoft): Float {
        val x = t.coerceIn(0f, 1f)
        for (i in 1 until stops.size) {
            if (x <= stops[i]) {
                val span = (stops[i] - stops[i - 1]).coerceAtLeast(1e-6f)
                val local = easing.transform(((x - stops[i - 1]) / span).coerceIn(0f, 1f))
                return values[i - 1] + (values[i] - values[i - 1]) * local
            }
        }
        return values.last()
    }

    // ---- ready-made curves (inputs are linear 0..1 progress) ----

    /** m-pop scale: .4 → 1.12 (60%) → .96 (80%) → 1, springy. */
    fun popScale(t: Float) = keyframes(t, floatArrayOf(0f, 0.6f, 0.8f, 1f), floatArrayOf(0.4f, 1.12f, 0.96f, 1f), EaseBack)
    /** m-pop opacity: 0 → 1 over the first 60%. */
    fun popAlpha(t: Float) = keyframes(t, floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0f, 1f, 1f), EaseBack).coerceIn(0f, 1f)

    /** m-drop offset in dp: −34 → +5 (55%) → −2 (78%) → 0. */
    fun dropOffsetDp(t: Float) = keyframes(t, floatArrayOf(0f, 0.55f, 0.78f, 1f), floatArrayOf(-34f, 5f, -2f, 0f))
    /** m-drop opacity: 0 → 1 by 55%. */
    fun dropAlpha(t: Float) = keyframes(t, floatArrayOf(0f, 0.55f, 1f), floatArrayOf(0f, 1f, 1f))

    /** m-growy: scaleY 0 → 1.08 (65%) → 1. */
    fun growY(t: Float) = keyframes(t, floatArrayOf(0f, 0.65f, 1f), floatArrayOf(0f, 1.08f, 1f))
    /** m-growx: scaleX 0 → 1, soft ease-out. */
    fun growX(t: Float) = eased(t)

    /**
     * m-sweep: a horizontal offset (px) that starts at [a] (fading in over the first 12%), swings
     * to [b] at 40%, back to [c] at 68%, overshoots to −c/4 at 86% and settles at 0. Pass offsets
     * relative to the final position, e.g. a = −x (far left), b = width − x (far right).
     */
    fun sweepOffset(t: Float, a: Float, b: Float, c: Float) =
        keyframes(t, floatArrayOf(0f, 0.4f, 0.68f, 0.86f, 1f), floatArrayOf(a, b, c, -c * 0.25f, 0f), EaseSwing)
    fun sweepAlpha(t: Float) = (t / 0.12f).coerceIn(0f, 1f)
}

/** Whether the user allows animations (Settings → Accessibility → Remove animations off). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val ctx = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember(ctx) {
        !inspection && runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * A screen's motion clock. Records the first start time of every animated element (by key) so
 * each plays exactly once per screen visit. Create one per screen with [rememberMotionSession].
 */
class MotionSession internal constructor(val enabled: Boolean) {
    private val openedAt = nowMs()
    private val starts = HashMap<String, Long>()

    /**
     * When [key] starts: the first time it's asked, [openedAt] + [delayMs] (a screen-level
     * timeline), but never earlier than now (so elements composed later, e.g. scrolled into view
     * or waiting for data, still animate).
     */
    fun startOf(key: String, delayMs: Int): Long = starts.getOrPut(key) { max(openedAt + delayMs, nowMs()) }

    /** Like [startOf] but relative to another start (a card's), still never earlier than now. */
    fun startAfter(key: String, parentStart: Long, offsetMs: Int): Long = starts.getOrPut(key) { max(parentStart + offsetMs, nowMs()) }

    internal companion object {
        fun nowMs(): Long = System.nanoTime() / 1_000_000L
    }
}

/** The current screen's [MotionSession]; null outside a [MotionScreen] (each element then keeps its own clock). */
val LocalMotionSession = compositionLocalOf<MotionSession?> { null }

/** The enclosing [Entrance]'s key and start time, so children can time themselves off their card. */
data class EntranceClock(val key: String, val startMs: Long)
val LocalEntranceClock = compositionLocalOf<EntranceClock?> { null }

@Composable
fun rememberMotionSession(): MotionSession {
    val enabled = rememberAnimationsEnabled()
    return remember(enabled) { MotionSession(enabled) }
}

/** Provides a fresh [MotionSession] to [content]: everything inside animates once per visit. */
@Composable
fun MotionScreen(content: @Composable () -> Unit) {
    val session = rememberMotionSession()
    CompositionLocalProvider(LocalMotionSession provides session, content = content)
}

private val DONE = object : State<Float> { override val value: Float = 1f }

/**
 * A 0 → 1 linear progress for one animated element.
 *
 * @param key unique within the card (or the screen, outside an [Entrance]). Include anything that
 *   should replay the animation when it changes (e.g. the selected range) in the key.
 * @param delayMs inside an [Entrance], ms after the card starts rising; outside, ms after the
 *   screen opened.
 * @param durationMs how long 0 → 1 takes.
 */
@Composable
fun rememberMotion(key: String, delayMs: Int = 0, durationMs: Int): State<Float> {
    val session = LocalMotionSession.current
    val clock = LocalEntranceClock.current
    val enabled = session?.enabled ?: rememberAnimationsEnabled()
    if (!enabled) return DONE
    val fullKey = if (clock != null) "${clock.key}/$key" else key
    val start = remember(fullKey, session) {
        when {
            session == null -> MotionSession.nowMs() + delayMs
            clock != null -> session.startAfter(fullKey, clock.startMs, delayMs)
            else -> session.startOf(fullKey, delayMs)
        }
    }
    val dur = durationMs.coerceAtLeast(1)
    val state = remember(fullKey, session) { mutableFloatStateOf(((MotionSession.nowMs() - start).toFloat() / dur).coerceIn(0f, 1f)) }
    LaunchedEffect(fullKey, session) {
        while (state.floatValue < 1f) {
            withFrameNanos { }
            state.floatValue = ((MotionSession.nowMs() - start).toFloat() / dur).coerceIn(0f, 1f)
        }
    }
    return state
}

/**
 * A never-ending phase 0 → 1 repeating every [periodMs] (e.g. a drifting wave). Frozen at 0 when
 * animations are off.
 */
@Composable
fun rememberLoop(periodMs: Int): State<Float> {
    val enabled = LocalMotionSession.current?.enabled ?: rememberAnimationsEnabled()
    val state = remember { mutableFloatStateOf(0f) }
    if (enabled) LaunchedEffect(periodMs) {
        val t0 = MotionSession.nowMs()
        while (true) {
            withFrameNanos { }
            state.floatValue = ((MotionSession.nowMs() - t0) % periodMs).toFloat() / periodMs
        }
    }
    return state
}

/**
 * A card's entrance (m-rise): opacity 0 → 1, blur 20 dp → 0 (API 31+; translate + fade only
 * below), translateY 22 dp → 0 and scale .98 → 1 over 1150 ms with a soft ease-out, staggered
 * [PremiumMotion.STAGGER_MS] per [index]. Children can time sub-animations off the card with
 * [rememberMotion].
 *
 * @param key stable identity for this card on the screen (defaults to the index).
 */
@Composable
fun Entrance(index: Int, modifier: Modifier = Modifier, key: String = "entrance-$index", content: @Composable () -> Unit) {
    val session = LocalMotionSession.current
    val enabled = session?.enabled ?: rememberAnimationsEnabled()
    val start = remember(key, session) {
        session?.startOf(key, index * PremiumMotion.STAGGER_MS) ?: (MotionSession.nowMs() + index * PremiumMotion.STAGGER_MS)
    }
    CompositionLocalProvider(LocalEntranceClock provides EntranceClock(key, start)) {
        if (!enabled) { Box(modifier) { content() }; return@CompositionLocalProvider }
        // The card's own rise uses the screen timeline directly.
        val t = riseProgress(key, start)
        Box(modifier.riseIn(t)) { content() }
    }
}

@Composable
private fun riseProgress(key: String, start: Long): State<Float> {
    val state = remember(key, start) { mutableFloatStateOf(((MotionSession.nowMs() - start).toFloat() / PremiumMotion.ENTRANCE_MS).coerceIn(0f, 1f)) }
    LaunchedEffect(key, start) {
        while (state.floatValue < 1f) {
            withFrameNanos { }
            state.floatValue = ((MotionSession.nowMs() - start).toFloat() / PremiumMotion.ENTRANCE_MS).coerceIn(0f, 1f)
        }
    }
    return state
}

/**
 * m-rise applied to any element, driven by a linear [progress] (see [rememberMotion]). The blur
 * needs API 31 (RenderEffect); below that it's fade + translate + scale only. The check is kept
 * although minSdk is 31 today so the helper stays safe to reuse.
 */
@android.annotation.SuppressLint("ObsoleteSdkInt")
fun Modifier.riseIn(progress: State<Float>): Modifier = graphicsLayer {
    val e = PremiumMotion.eased(progress.value)
    alpha = e
    translationY = (1f - e) * 22.dp.toPx()
    val s = 0.98f + 0.02f * e
    scaleX = s; scaleY = s
    val r = (1f - e) * 20.dp.toPx()
    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
}

/** m-fade: opacity 0 → 1 with CSS `ease`. */
fun Modifier.fadeIn(progress: State<Float>): Modifier = graphicsLayer { alpha = PremiumMotion.eased(progress.value, PremiumMotion.Ease) }

/** m-pop: scale .4 → 1.12 → .96 → 1 with a fade-in. For dots, chips, medals. */
fun Modifier.popIn(progress: State<Float>): Modifier = graphicsLayer {
    val t = progress.value
    val s = PremiumMotion.popScale(t)
    scaleX = s; scaleY = s
    alpha = PremiumMotion.popAlpha(t)
}

/** m-drop: falls in from 34 dp above with a small bounce. For tiles and pills. */
fun Modifier.dropIn(progress: State<Float>): Modifier = graphicsLayer {
    val t = progress.value
    translationY = PremiumMotion.dropOffsetDp(t) * density
    alpha = PremiumMotion.dropAlpha(t)
}

/** m-growx: scales in from the left edge. For progress bars. */
fun Modifier.growFromLeft(progress: State<Float>): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0.5f)
    scaleX = PremiumMotion.growX(progress.value)
}

/** m-growy: grows up from the bottom edge with an overshoot. For bars. */
fun Modifier.growFromBottom(progress: State<Float>): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0.5f, 1f)
    scaleY = PremiumMotion.growY(progress.value)
}

/** m-count: the value to show while counting up to [target] (soft ease-out). */
fun countUp(target: Int, progress: Float): Int = (target * PremiumMotion.eased(progress)).roundToInt()

/** m-count for decimals: [target] × eased progress. */
fun countUp(target: Double, progress: Float): Double = target * PremiumMotion.eased(progress)

/**
 * Draws the first [fraction] of [path] (by length) with [stroke], like a pen tracing it (m-draw).
 * Pass [PremiumMotion.eased] (pen easing) of a [rememberMotion] progress as [fraction].
 */
fun DrawScope.drawPathTrimmed(path: Path, fraction: Float, color: Color, stroke: Stroke) {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return
    if (f >= 1f) { drawPath(path, color, style = stroke); return }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val out = Path()
    measure.getSegment(0f, measure.length * f, out, true)
    drawPath(out, color, style = stroke)
}

/** Same as [drawPathTrimmed] with a [androidx.compose.ui.graphics.Brush]. */
fun DrawScope.drawPathTrimmed(path: Path, fraction: Float, brush: androidx.compose.ui.graphics.Brush, stroke: Stroke) {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return
    if (f >= 1f) { drawPath(path, brush, style = stroke); return }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val out = Path()
    measure.getSegment(0f, measure.length * f, out, true)
    drawPath(out, brush, style = stroke)
}

/** Where along [path] the pen is at [fraction] (for a leading dot), or null for an empty path. */
fun pathPoint(path: Path, fraction: Float): Offset? {
    val measure = PathMeasure()
    measure.setPath(path, false)
    if (measure.length <= 0f) return null
    return measure.getPosition(measure.length * fraction.coerceIn(0f, 1f))
}

/**
 * Staggered sequence helper: element [i] of a run of pops / drops / ticks starts
 * [firstMs] + i × [stepMs] after its card. Returns the delay to pass to [rememberMotion].
 */
fun stagger(i: Int, firstMs: Int, stepMs: Int): Int = firstMs + i * stepMs
