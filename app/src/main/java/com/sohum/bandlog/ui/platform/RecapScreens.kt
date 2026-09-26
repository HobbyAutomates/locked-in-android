package com.sohum.bandlog.ui.platform

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.Flame
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.countUp
import com.sohum.bandlog.ui.motion.drawPathTrimmed
import com.sohum.bandlog.ui.motion.dropIn
import com.sohum.bandlog.ui.motion.growFromBottom
import com.sohum.bandlog.ui.motion.popIn
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.motion.riseIn
import com.sohum.bandlog.ui.progress.smoothPath
import com.sohum.bandlog.ui.theme.MonoStyle
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.PlatformPrefs
import com.sohum.bandlog.util.Pro
import com.sohum.bandlog.util.Recaps

private val ACCENT = Color(0xFFFF8A3D)
private val INK = Color(0xFFF5F5F7)
private val MUTED = Color(0xFF9A9AA0)

internal fun computeRecap(vm: AppViewModel, period: Recaps.Period, rank: Int? = null, squad: String? = null) = Recaps.compute(
    period, vm.workouts, vm.meals, vm.exercises, vm.weights, vm.profile.proteinTargetG, vm.profile.weeklyWorkoutTarget, vm.dayStreak, rank, squad,
)

/** The recap the Home prompt would offer today (unseen, non-empty), or null. */
@Composable
fun recapDuePeriod(vm: AppViewModel): Recaps.Period? {
    val ctx = LocalContext.current
    val pvm: PlatformViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    if (!pvm.hasPro || !vm.loadedOnce) return null
    return Recaps.dueToday(Dates.today()).firstOrNull { it.key !in PlatformNav.hiddenRecaps && !PlatformPrefs.recapSeen(ctx, it.key) && !computeRecap(vm, it).empty }
}

/** Whether Home should show [RecapPrompt] (so an empty slot doesn't leave a gap). */
@Composable
fun recapDue(vm: AppViewModel): Boolean = recapDuePeriod(vm) != null

/** v2.13 Home prompt: last week's recap on Monday, last month's on the 1st (spec §13). Hidden once seen. */
@Composable
fun RecapPrompt(vm: AppViewModel) {
    val ctx = LocalContext.current
    val period = recapDuePeriod(vm) ?: return
    Row(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1A1A1D), Color(0xFF2A1A10))), RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Play ${period.title}") { PlatformPrefs.markRecapSeen(ctx, period.key); PlatformNav.hiddenRecaps.add(period.key); PlatformNav.recap = period; PlatformNav.open(PlatformPage.RECAP) }
            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(ACCENT, CircleShape), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${period.title}, wrapped", fontSize = 16.sp, fontWeight = FontWeight(700), color = INK)
            Text(period.label + " · tap to play", fontSize = 12.sp, color = MUTED)
        }
        Box(Modifier.size(40.dp).clickable(onClickLabel = "Dismiss") { PlatformPrefs.markRecapSeen(ctx, period.key); PlatformNav.hiddenRecaps.add(period.key) }, contentAlignment = Alignment.Center) {
            Icon(CrossIcon, null, tint = MUTED, modifier = Modifier.size(14.dp))
        }
    }
}

/** Progress → Recaps: the last 8 weeks and 3 months. */
@Composable
fun RecapsScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val today = Dates.today()
    SubPage("Recaps", onBack) {
        ProGate(pvm, Pro.Feature.RECAPS) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("Weekly" to Recaps.weeks(today, 8), "Monthly" to Recaps.months(today, 3)).forEach { (label, list) ->
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(start = 4.dp))
                    Card(padding = 0.dp) {
                        list.forEachIndexed { i, per ->
                            if (i > 0) Hair()
                            val r = computeRecap(vm, per)
                            NavRow(LineIcons.Star, per.label, if (r.empty) "Nothing logged" else "${r.daysLogged} days · ${r.workouts} workouts") {
                                PlatformNav.recap = per; PlatformNav.open(PlatformPage.RECAP)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SLIDE_MS = 5200

/**
 * v2.13 recap story player (spec §13): full screen, auto-advancing slides with the premium
 * motion (blur-rise, count-ups, growing bars, pen-drawn lines). Tap right / left to go forward /
 * back, hold to pause. The last slide shares a story card.
 */
@Composable
fun RecapPlayer(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val period = PlatformNav.recap
    if (period == null) { LaunchedEffect(Unit) { onClose() }; return }
    var rank by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(period.key) {
        rank = runCatching {
            val sq = Api.mySquads().firstOrNull() ?: return@runCatching null
            val me = com.sohum.bandlog.data.Session.userId
            val rows = Api.groupLeaderboard(sq.id)
            val i = rows.indexOfFirst { it.userId == me }
            if (i < 0) null else (rows[i].rank.takeIf { it > 0 } ?: (i + 1)) to sq.name
        }.getOrNull()
    }
    val r = computeRecap(vm, period, rank?.first, rank?.second)
    val slides = buildList {
        add("intro"); add("days"); add("workouts"); add("protein")
        if (r.bestLift != null) add("lift")
        if (r.weightSeries.size >= 2 || r.weightChange != null) add("weight")
        if (r.topFoods.isNotEmpty()) add("foods")
        add("streak")
        if (r.squadRank != null) add("squad")
        add("next")
    }
    var index by remember(period.key) { mutableIntStateOf(0) }
    var progress by remember(period.key) { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    val last = index == slides.lastIndex
    LaunchedEffect(index, paused, period.key) {
        var prev = 0L
        while (progress < 1f) {
            withFrameNanos { t ->
                if (prev != 0L && !paused) progress = (progress + (t - prev) / 1_000_000f / SLIDE_MS).coerceAtMost(1f)
                prev = t
            }
            if (paused) return@LaunchedEffect
        }
        if (!last) { index++; progress = 0f }
    }
    fun go(d: Int) { val n = (index + d).coerceIn(0, slides.lastIndex); if (n != index) { index = n; progress = 0f } else if (d > 0 && last) progress = 1f else progress = 0f }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141416), Color(0xFF050505))))
            .pointerInput(slides.size) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o -> if (o.x < size.width / 3f) go(-1) else go(1) },
                )
            }
            .semantics { contentDescription = "${period.title} recap, slide ${index + 1} of ${slides.size}. Tap right for next, left for back, hold to pause." },
    ) {
        Box(Modifier.size(420.dp).align(Alignment.TopEnd).padding(0.dp).background(Brush.radialGradient(listOf(ACCENT.copy(alpha = 0.28f), Color.Transparent)), CircleShape))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Progress bars.
            Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(3.dp).background(INK.copy(alpha = 0.25f), CircleShape)) {
                        val f = when { i < index -> 1f; i == index -> progress; else -> 0f }
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(INK, CircleShape))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LOCKED IN", style = MonoStyle, color = INK.copy(alpha = 0.8f), letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).clickable(onClickLabel = "Close", onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(CrossIcon, "Close", tint = INK, modifier = Modifier.size(16.dp))
                }
            }
            AnimatedContent(slides[index], Modifier.weight(1f).fillMaxWidth(), label = "slide", transitionSpec = { fadeIn() togetherWith fadeOut() }) { slide ->
                MotionScreen {
                    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.CenterStart) { Slide(slide, r) }
                }
            }
            if (last) PillButton(
                "Share", {
                    ShareCards.share(ctx, ShareCards.recap(r), hideNumbers = vm.profile.hideNumbers == true)
                }, Modifier.padding(20.dp), bg = ACCENT, fg = Color.White, icon = LineIcons.Share,
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String) = Text(text.uppercase(), style = MonoStyle.copy(fontSize = 13.sp), color = ACCENT, letterSpacing = 2.sp)

@Composable
private fun Big(value: String, unit: String = "") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 96.sp, fontWeight = FontWeight(700), letterSpacing = (-3).sp, color = INK, lineHeight = 96.sp)
        if (unit.isNotEmpty()) Text(" $unit", fontSize = 22.sp, color = MUTED, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun Line(text: String) = Text(text, fontSize = 20.sp, fontWeight = FontWeight(500), color = INK, lineHeight = 27.sp)

@Composable
private fun Slide(slide: String, r: Recaps.Recap) {
    Entrance(0, key = "slide-$slide") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val count = rememberMotion("count-$slide", 350, PremiumMotion.COUNT_MS)
            when (slide) {
                "intro" -> {
                    Eyebrow(r.period.label)
                    Text(r.period.title + ",\nwrapped.", fontSize = 52.sp, fontWeight = FontWeight(700), letterSpacing = (-1.5).sp, color = INK, lineHeight = 56.sp)
                    Line("Here's what you put in.")
                }
                "days" -> {
                    Eyebrow("Days logged")
                    Big("${countUp(r.daysLogged, count.value)}", "of ${r.period.days}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val n = r.period.days.coerceAtMost(31)
                        repeat(n) { i ->
                            val pop = rememberMotion("day-$i", 600 + i * (if (n > 7) 40 else 120), PremiumMotion.POP_MS)
                            Box(Modifier.size(if (n > 7) 7.dp else 22.dp).popIn(pop).background(if (i < r.daysLogged) ACCENT else INK.copy(alpha = 0.18f), CircleShape))
                        }
                    }
                    Line(if (r.daysLogged >= r.period.days - 1) "Almost every single day. That's the habit." else "Every logged day is data you can use.")
                }
                "workouts" -> {
                    Eyebrow("Training")
                    Big("${countUp(r.workouts, count.value)}", if (r.workouts == 1) "workout" else "workouts")
                    Line("${countUp(r.minutes, count.value)} minutes moving")
                }
                "protein" -> {
                    Eyebrow("Protein")
                    Big("${countUp(r.proteinDaysHit, count.value)}", "days on target")
                    ProteinBars(r)
                    Line("Target ${r.proteinTarget} g a day")
                }
                "lift" -> r.bestLift?.let { (name, pt) ->
                    Eyebrow(if (pt.pr) "New PR" else "Best lift")
                    Big(num1(countUp(pt.kg, count.value)), "kg × ${pt.reps}")
                    Line(name)
                    Text("Estimated 1RM ${num1(pt.e1rm)} kg${if (r.prCount > 1) " · ${r.prCount} PRs" else ""}", fontSize = 15.sp, color = MUTED)
                }
                "weight" -> {
                    Eyebrow("Weight trend")
                    val d = r.weightChange ?: 0.0
                    Big((if (d > 0) "+" else if (d < 0) "−" else "") + num1(kotlin.math.abs(countUp(d, count.value))), "kg")
                    if (r.weightSeries.size >= 2) WeightLine(r.weightSeries.map { it.second })
                    r.weightEnd?.let { Line("Now ${num1(it)} kg") }
                }
                "foods" -> {
                    Eyebrow("Top foods")
                    r.topFoods.forEachIndexed { i, (name, n) ->
                        val drop = rememberMotion("food-$i", 300 + i * 220, PremiumMotion.DROP_MS)
                        Row(Modifier.dropIn(drop), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", fontSize = 34.sp, fontWeight = FontWeight(700), color = ACCENT, modifier = Modifier.width(44.dp))
                            Column {
                                Text(name, fontSize = 22.sp, fontWeight = FontWeight(600), color = INK)
                                Text("$n time${if (n == 1) "" else "s"}", fontSize = 14.sp, color = MUTED)
                            }
                        }
                    }
                }
                "streak" -> {
                    Eyebrow("Streak")
                    val pop = rememberMotion("flame", 200, PremiumMotion.POP_MS)
                    Box(Modifier.popIn(pop)) { Flame(ACCENT, 72.dp) }
                    Big("${countUp(r.streak, count.value)}", if (r.streak == 1) "day" else "days")
                    Line(if (r.streak > 0) "Don't break the chain." else "Start a new one today.")
                }
                "squad" -> {
                    Eyebrow("Squad")
                    Big("#${r.squadRank}", "")
                    Line("on ${r.squadName ?: "your squad"}'s board right now")
                }
                else -> {
                    Eyebrow("Next up")
                    Text(r.nextGoal, fontSize = 40.sp, fontWeight = FontWeight(700), letterSpacing = (-1).sp, color = INK, lineHeight = 46.sp)
                    Line("Share your recap with the squad.")
                }
            }
        }
    }
}

@Composable
private fun ProteinBars(r: Recaps.Recap) {
    val vals = r.proteinByDay.takeLast(31)
    val max = maxOf(vals.maxOrNull() ?: 0.0, r.proteinTarget.toDouble(), 1.0)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(if (vals.size > 7) 2.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
        vals.forEachIndexed { i, v ->
            val grow = rememberMotion("pbar-$i", 400 + i * (if (vals.size > 7) 25 else 90), PremiumMotion.GROW_Y_MS)
            val f = (v / max).toFloat().coerceIn(0.02f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight(f).growFromBottom(grow).background(if (v >= r.proteinTarget && v > 0) ACCENT else INK.copy(alpha = 0.25f), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun WeightLine(ys: List<Double>) {
    val draw = rememberMotion("wline", 400, PremiumMotion.DRAW_MS)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val lo = ys.min(); val hi = ys.max(); val span = maxOf(hi - lo, 0.6)
        val pts = ys.mapIndexed { i, y ->
            Offset(size.width * i / (ys.size - 1).coerceAtLeast(1), 8f + (size.height - 16f) * (1f - ((y - lo) / span).toFloat()))
        }
        drawPathTrimmed(smoothPath(pts), PremiumMotion.eased(draw.value, PremiumMotion.EasePen), ACCENT, Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}
