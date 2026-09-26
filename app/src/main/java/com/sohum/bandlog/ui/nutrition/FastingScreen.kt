package com.sohum.bandlog.ui.nutrition

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.FastingSession
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Fasting
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.TargetEdits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Why fasting is hidden for this person, or null when it's available: under 18, or a v2.10 safety
 * flag (very low BMI, rapid loss, repeated lowering) — the science spec's eating-disorder safeguards.
 */
fun fastingBlock(vm: AppViewModel, ctx: Context): String? {
    val p = vm.profile
    if (Goals.isTeen(Goals.ageYears(p.dob))) return "Not available under 18"
    val flags = Goals.edFlags(Goals.screenInput(p, weights = vm.weights.map { Goals.WeighIn(it.date, it.weightKg) }, edits = TargetEdits.read(ctx)))
    return if (flags.any { it in setOf("very_low_bmi", "very_low_bmi_for_age", "rapid_loss", "repeated_lowering") }) "Not available right now" else null
}

private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
private fun whenLabel(ms: Long): String = Instant.ofEpochMilli(ms).atZone(com.sohum.bandlog.util.Dates.ZONE).format(WHEN)

/** §7 the fasting timer: protocol, a ring counting up to the goal with the stages, and the last 14 fasts. */
@Composable
fun FastingScreen(vm: AppViewModel, nvm: NutritionViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val block = fastingBlock(vm, ctx)
    LaunchedEffect(Unit) { if (block == null) nvm.loadFasting(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(nvm.active) { while (nvm.active != null) { now = System.currentTimeMillis(); delay(1000) } }
    var hours by remember(nvm.settings.fastingHours) { mutableDoubleStateOf(nvm.settings.fastingHours ?: Fasting.DEFAULT_HOURS) }
    var custom by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    MotionScreen {
        SubPage("Fasting", onBack) {
            NoticeLine(nvm)
            if (block != null) {
                Entrance(0) {
                    Card {
                        Text(block, fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (block.contains("18")) "While your body is still growing, regular meals matter more than timing them. Fuel your training and school days."
                            else "Regular meals look like the kinder choice for you right now. If food or your body has been on your mind a lot, talking to someone can help.",
                            fontSize = 13.sp, color = p.muted, lineHeight = 18.sp,
                        )
                    }
                }
                return@SubPage
            }
            if (nvm.fastingUnavailable) { Entrance(0) { ComingSoonCard("Fasting timer") }; return@SubPage }

            val a = nvm.active
            Entrance(0, key = "ring") {
                Card(padding = 20.dp) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val target = a?.targetHours ?: hours
                        val elapsed = if (a != null) Fasting.elapsedHours(a.startedAtMs, now) else 0.0
                        FastRing(elapsed, target, Modifier.size(232.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (a != null) Fasting.clock(now - a.startedAtMs) else "0:00:00", fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink)
                            val stage = Fasting.stageAt(elapsed)
                            Text(if (a != null) stage.label else "Ready", fontSize = 14.sp, fontWeight = FontWeight(700), color = if (a != null) p.orange else p.muted)
                            Text("Goal ${Fasting.label(target)}", fontSize = 12.sp, color = p.muted)
                        }
                    }
                    if (a != null) {
                        val left = a.goalAtMs - now
                        Text(
                            if (left > 0) "Goal at ${whenLabel(a.goalAtMs)} · ${Fasting.duration(left)} to go" else "Goal reached. End whenever you're ready.",
                            fontSize = 13.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Text(Fasting.stageAt(Fasting.elapsedHours(a.startedAtMs, now)).blurb, fontSize = 12.sp, color = p.muted, textAlign = TextAlign.Center, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    if (a == null) {
                        PillButton(if (busy) "Starting…" else "Start a ${Fasting.label(hours)} fast", enabled = !busy, onClick = {
                            scope.launch { busy = true; nvm.startFast(ctx, hours); busy = false }
                        })
                    } else {
                        PillButton(if (busy) "Ending…" else "End fast", enabled = !busy, bg = p.card2, fg = p.ink, onClick = { confirmEnd = true })
                    }
                }
            }
            if (a == null) Entrance(1, key = "protocol") {
                Card {
                    Text("Protocol", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Hours fasting : hours eating", fontSize = 12.sp, color = p.muted)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Fasting.PROTOCOLS.forEach { pr -> Chip(pr.label, !custom && hours == pr.hours, { custom = false; hours = pr.hours }) }
                        Chip("Custom", custom || Fasting.PROTOCOLS.none { it.hours == hours }, { custom = true })
                    }
                    if (custom || Fasting.PROTOCOLS.none { it.hours == hours }) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Custom length", fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
                            Stepper("−", hours > Fasting.MIN_HOURS) { hours = Fasting.clampHours(hours - 1) }
                            Text("${Fasting.fmtH(hours)} h", Modifier.width(64.dp), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                            Stepper("+", hours < Fasting.MAX_HOURS) { hours = Fasting.clampHours(hours + 1) }
                        }
                        if (hours > 24) Text("Long fasts aren't for everyone. Check with a doctor first if you have a health condition or take regular medicines.", fontSize = 12.sp, color = p.orange, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Entrance(2, key = "stages") {
                Card {
                    Text("Stages", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Rough averages. Everyone's body is different.", fontSize = 12.sp, color = p.muted)
                    Fasting.STAGES.forEachIndexed { i, s ->
                        if (i > 0) Hair()
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 4.dp).size(10.dp).background(stageColor(s.key), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${s.label} · ${Fasting.fmtH(s.fromHours)} h+", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                                Text(s.blurb, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
            Entrance(3, key = "history") {
                Card {
                    Text("Last 14 fasts", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    val done = nvm.fasts.filter { !it.running }.take(14)
                    if (done.isEmpty()) Text(if (nvm.fastingLoaded) "Finished fasts show up here." else "Loading…", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                    done.forEachIndexed { i, f ->
                        if (i > 0) Hair()
                        HistoryRow(f) { scope.launch { nvm.deleteFast(f.id) } }
                    }
                }
            }
            Text("General wellness info, not medical advice. Skip fasting if you're pregnant, have diabetes, or have a history of disordered eating.", fontSize = 11.sp, color = p.muted, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false }, containerColor = p.card,
        title = { Text("End this fast?", fontWeight = FontWeight(800), color = p.ink) },
        text = { Text(nvm.active?.let { "You're at ${Fasting.duration(now - it.startedAtMs)} of ${Fasting.label(it.targetHours)}." } ?: "", color = p.muted) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; scope.launch { busy = true; nvm.endFast(ctx); busy = false } }) { Text("End fast", color = p.ink, fontWeight = FontWeight(700)) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going", color = p.muted) } },
    )
}

@Composable
private fun stageColor(key: String) = when (key) { "fed" -> palette.blue; "fat" -> palette.orange; else -> palette.purple }

@Composable
private fun Stepper(label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(44.dp).background(if (enabled) p.card2 else p.card2.copy(alpha = 0.4f), CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight(700), color = if (enabled) p.ink else p.muted) }
}

@Composable
private fun HistoryRow(f: FastingSession, onDelete: () -> Unit) {
    val p = palette
    val dur = (f.endedAtMs ?: f.startedAtMs) - f.startedAtMs
    val hit = f.reachedGoal()
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fasting.duration(dur) + " of " + Fasting.label(f.targetHours), fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
            Text(whenLabel(f.startedAtMs), fontSize = 12.sp, color = p.muted)
        }
        Tag(if (hit) "Goal hit" else "Ended early", if (hit) p.green else p.muted, if (hit) p.greenBg else p.card2)
        Box(Modifier.size(44.dp).clickable(onClick = onDelete).semantics { contentDescription = "Delete this fast" }, contentAlignment = Alignment.Center) {
            Icon(CrossIcon, null, tint = p.muted, modifier = Modifier.size(12.dp))
        }
    }
}

/** The ring: track, progress to the goal (sweeps in once), and ticks where the stages begin. */
@Composable
fun FastRing(elapsedHours: Double, targetHours: Double, modifier: Modifier = Modifier, stroke: Float = 14f) {
    val p = palette
    val sweep = rememberMotion("fast-ring", 100, PremiumMotion.FILL_MS)
    val track = p.track
    val color = p.orange
    val tickFat = p.orange
    val tickKeto = p.purple
    Canvas(modifier) {
        val sw = stroke.dp.toPx()
        val inset = sw / 2
        val arc = Size(size.width - sw, size.height - sw)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        val frac = (elapsedHours / targetHours.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f) * PremiumMotion.eased(sweep.value)
        if (frac > 0f) drawArc(color, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        listOf(12.0 to tickFat, 18.0 to tickKeto).forEach { (h, c) ->
            if (h < targetHours) {
                val ang = Math.toRadians(-90.0 + 360.0 * h / targetHours)
                val r = arc.width / 2
                val cx = size.width / 2; val cy = size.height / 2
                val o = Offset((cx + (r + sw * 0.9f) * kotlin.math.cos(ang)).toFloat(), (cy + (r + sw * 0.9f) * kotlin.math.sin(ang)).toFloat())
                drawCircle(c, radius = sw * 0.28f, center = o)
            }
        }
    }
}

/** §7 Home card while a fast is running: a small ring, the clock, and the goal. Tap opens the timer. */
@Composable
fun FastingHomeCard(nvm: NutritionViewModel, onOpen: () -> Unit) {
    val a = nvm.active ?: return
    val p = palette
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val elapsed = Fasting.elapsedHours(a.startedAtMs, now)
    Card(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                FastRing(elapsed, a.targetHours, Modifier.size(52.dp), stroke = 6f)
                Icon(NutritionIcons.Timer, null, tint = p.orange, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Fasting · ${Fasting.clock(now - a.startedAtMs)}", fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                val left = a.goalAtMs - now
                Text(
                    (if (left > 0) "${Fasting.duration(left)} to your ${Fasting.label(a.targetHours)} goal" else "Goal reached") + " · ${Fasting.stageAt(elapsed).label}",
                    fontSize = 12.sp, color = p.muted, maxLines = 1,
                )
            }
            com.sohum.bandlog.ui.components.Chevron()
        }
    }
}
