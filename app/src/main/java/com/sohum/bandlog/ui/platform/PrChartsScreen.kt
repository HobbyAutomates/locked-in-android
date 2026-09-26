package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.drawPathTrimmed
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.progress.smoothPath
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.MuscleMap
import com.sohum.bandlog.util.Pro
import com.sohum.bandlog.util.Training

/**
 * v2.13 PR charts (spec §12, Pro): every lift with history; per lift, the best set per session and
 * its estimated 1RM (Epley) as a line, PR badges where a session beat every earlier one, the
 * muscles it works, and a share card for the latest PR.
 */
@Composable
fun PrChartsScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    LaunchedEffect(Unit) { if (pvm.allWorkouts == null) pvm.loadAllWorkouts() }
    val all = pvm.allWorkouts ?: vm.workouts
    val selected = PlatformNav.prExercise
    SubPage(selected ?: "PR charts", { if (selected != null) PlatformNav.prExercise = null else onBack() }) {
        ProGate(pvm, Pro.Feature.PR_CHARTS) {
            if (selected == null) {
                val list = Training.exercisesWithHistory(all)
                if (list.isEmpty()) Card {
                    Text("No weighted lifts yet", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Log a gym session with kg and reps and your PRs show up here.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                } else Card(padding = 0.dp) {
                    list.forEachIndexed { i, (name, sessions) ->
                        if (i > 0) Hair()
                        val best = Training.bestE1rm(all, name)
                        NavRow(com.sohum.bandlog.ui.components.DumbbellIcon, name, "${num1(best)} kg e1RM · $sessions") { PlatformNav.prExercise = name }
                    }
                }
            } else PrDetail(vm, all, selected)
        }
    }
}

@Composable
private fun PrDetail(vm: AppViewModel, all: List<com.sohum.bandlog.data.Workout>, name: String) {
    val p = palette
    val ctx = LocalContext.current
    val hist = Training.history(all, name)
    val best = hist.maxByOrNull { it.e1rm }
    val lastPr = hist.lastOrNull { it.pr }
    MotionScreen {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Entrance(0, key = "pr-head") {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TitleWithChip("Estimated 1-rep max", pro = true)
                    }
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                        Text(best?.let { num1(it.e1rm) } ?: "—", fontSize = 34.sp, fontWeight = FontWeight(400), letterSpacing = (-1).sp, color = p.ink)
                        Text(" kg best", fontSize = 15.sp, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    best?.let { Text("${num1(it.kg)} kg × ${it.reps} on ${Dates.short(it.date)}", fontSize = 13.sp, color = p.muted) }
                    if (hist.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        PrChart(hist, Modifier.fillMaxWidth().height(160.dp))
                    }
                    Text("Epley: weight × (1 + reps ÷ 30). An estimate, not a test.", fontSize = 11.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            MuscleMap.of(name)?.let { t ->
                Entrance(1, key = "pr-muscles") {
                    Card {
                        Text("Muscles worked", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(
                            (t.primary.map { it.label } + t.secondary.map { it.label + " (secondary)" }).joinToString(" · "),
                            fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )
                        MuscleFigures(targetFills(t.primary, t.secondary), Modifier.height(170.dp), description = "Muscles worked by $name")
                    }
                }
            }
            if (lastPr != null) Entrance(2, key = "pr-share") {
                PillButton("Share your PR", {
                    ShareCards.share(ctx, ShareCards.pr(name, lastPr, vm.profile.name), hideNumbers = vm.profile.hideNumbers == true)
                }, icon = LineIcons.Share)
            }
            Entrance(3, key = "pr-history") {
                Card(padding = 0.dp) {
                    hist.reversed().forEachIndexed { i, pt ->
                        if (i > 0) Hair()
                        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Dates.relative(pt.date), fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                                Text("${num1(pt.kg)} kg × ${pt.reps}", fontSize = 13.sp, color = p.muted)
                            }
                            if (pt.pr) Box(Modifier.background(accentColor, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("PR", fontSize = 11.sp, fontWeight = FontWeight(800), color = androidx.compose.ui.graphics.Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${num1(pt.e1rm)} kg", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                        }
                    }
                }
            }
        }
    }
}

/** e1RM per session as a pen-drawn line; PR sessions get accent dots. */
@Composable
private fun PrChart(hist: List<Training.Point>, modifier: Modifier) {
    val p = palette
    val a = accentColor
    val draw = rememberMotion("pr-draw", 300, PremiumMotion.DRAW_MS)
    val dots = rememberMotion("pr-dots", 1600, PremiumMotion.POP_MS)
    Canvas(modifier) {
        val d0 = Dates.parse(hist.first().date).toEpochDay().toFloat()
        val span = (Dates.parse(hist.last().date).toEpochDay() - d0).coerceAtLeast(1f)
        val lo = hist.minOf { it.e1rm }; val hi = hist.maxOf { it.e1rm }
        val range = maxOf(hi - lo, 2.5)
        val pad = 8.dp.toPx()
        val pts = hist.map { pt ->
            val x = pad + (size.width - 2 * pad) * ((Dates.parse(pt.date).toEpochDay() - d0) / span)
            val y = pad + (size.height - 2 * pad) * (1f - ((pt.e1rm - (lo - (range - (hi - lo)) / 2)) / range).toFloat().coerceIn(0f, 1f))
            Offset(x, y)
        }
        drawLine(p.hair, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
        drawPathTrimmed(smoothPath(pts), PremiumMotion.eased(draw.value, PremiumMotion.EasePen), p.ink, Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val s = PremiumMotion.popScale(dots.value)
        hist.forEachIndexed { i, pt ->
            if (pt.pr) { drawCircle(p.card, 6.dp.toPx() * s, pts[i]); drawCircle(a, 4.5.dp.toPx() * s, pts[i]) }
        }
    }
}
