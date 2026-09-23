package com.sohum.bandlog.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Flame
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import com.sohum.bandlog.util.Streaks
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Index of the "Month" segment: the last 30 days ending today. */
private const val MONTH = 3

/** Day-of-month numbers that get an x label in month mode, so 30 bars stay readable. */
private val MONTH_TICKS = setOf(1, 6, 11, 16, 21, 26)

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val NARROW_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
private val WEEK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Sparse month-mode label: the date number on tick days, blank otherwise. */
private fun monthTick(date: String): String =
    Dates.parse(date).dayOfMonth.let { if (it in MONTH_TICKS) it.toString() else "" }

@Composable
fun ProgressScreen(vm: AppViewModel, onOpenBadges: () -> Unit) {
    val p = palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = Dates.today()
    var period by remember { mutableIntStateOf(0) }
    val monthMode = period == MONTH
    val days = if (monthMode) {
        (29 downTo 0).map { Dates.addDays(today, -it.toLong()) }
    } else {
        val ws = Dates.addDays(Dates.weekStart(today), -7L * period)
        (0..6).map { Dates.addDays(ws, it.toLong()) }
    }
    val protein = days.map { totalsFor(vm.meals, it).protein }
    val logged = protein.filter { it > 0 }
    val avg = if (logged.isEmpty()) 0.0 else logged.average()
    val weeks = (7 downTo 0).map { Dates.addDays(Dates.weekStart(today), -7L * it) }
    val perWeek = vm.workoutDates.groupingBy { Dates.weekStart(it) }.eachCount()
    val rest = Streaks.restByMuscle(vm.workouts).filter { it.last != null }.take(3)
    val target = vm.profile.weeklyWorkoutTarget
    val proteinLabels = if (monthMode) days.map { monthTick(it) } else days.map { Dates.parse(it).format(DAY_FMT) }
    val energyLabels = if (monthMode) days.map { monthTick(it) } else days.map { Dates.parse(it).format(NARROW_FMT) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "title") { Rise(0) { ScreenTitle("Progress") } }
        item(key = "weight") { Rise(1) { WeightCard(vm) } }
        item(key = "week") {
            Rise(1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.weight(1f)) {
                        Text("This week", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${vm.thisWeek}", fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 30.sp)
                            Text(" / $target", fontSize = 16.sp, fontWeight = FontWeight(600), color = p.muted)
                        }
                        Spacer(Modifier.height(8.dp))
                        Progress(vm.thisWeek.toFloat() / target.coerceAtLeast(1), p.ink)
                        Spacer(Modifier.height(6.dp))
                        val left = (target - vm.thisWeek).coerceAtLeast(0)
                        Text(if (left == 0) "Target hit — streak safe" else "$left more to keep the streak", fontSize = 12.sp, color = p.muted)
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Flame(p.flame, 40.dp)
                            Text("${vm.weekStreak}", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.flame, lineHeight = 26.sp)
                            Text("Week streak", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                                repeat(4) { i -> Box(Modifier.size(8.dp).let { m -> if (i < vm.weekStreak.coerceAtMost(4)) m.background(p.flame, CircleShape) else m.border(1.5.dp, p.flame, CircleShape) }) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "badges") { Rise(2) { BadgesCard(vm, onOpenBadges) } }
        item(key = "period") { Rise(2) { Segmented(listOf("This week", "Last week", "2 wks ago", "Month"), period, { period = it }, height = 34.dp) } }
        item(key = "energy") { Rise(3) { WeeklyEnergyCard(vm, days, energyLabels, monthMode, ctx) } }
        item(key = "protein") {
            Rise(3) {
                Card {
                    Text("Protein", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${avg.toInt()}", fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink, lineHeight = 28.sp)
                        Text(
                            " g / day avg${if (monthMode) " · 30 days" else ""} · target ${vm.profile.proteinTargetG}",
                            fontSize = 13.sp, color = p.muted,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ProteinBars(
                        values = protein,
                        targetG = vm.profile.proteinTargetG,
                        xLabels = proteinLabels,
                        monthMode = monthMode,
                        highlightLast = period == 0 || monthMode,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                    )
                }
            }
        }
        item(key = "sessions") {
            Rise(4) {
                Card {
                    RowSpaceBetween {
                        Text("Sessions per week", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("target $target", fontSize = 12.sp, color = p.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    SessionBars(
                        counts = weeks.map { perWeek[it] ?: 0 },
                        target = target,
                        xLabels = weeks.map { Dates.parse(it).format(WEEK_FMT) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rest.forEach { r -> MacroDot("${r.muscle} ${if (r.days == 0L) "today" else "${r.days} d rest"}", if (r.days >= 7) Muscles.color(r.muscle) else p.muted) }
                    }
                }
            }
        }
    }
}

/** Current vs goal weight, with a hairline sparkline of the last ten weigh-ins. */
@Composable
private fun WeightCard(vm: AppViewModel) {
    val p = palette
    val rows = vm.weights
    val current = rows.firstOrNull()?.weightKg ?: vm.profile.weightKg
    val goal = vm.profile.goalWeightKg
    // weights arrive newest-first; the sparkline reads left-to-right in time order.
    val spark = rows.take(10).map { it.weightKg }.reversed()

    Card {
        RowSpaceBetween {
            Column {
                Text("Weight", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        current?.let { com.sohum.bandlog.ui.today.fmt(it) } ?: "—",
                        fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 30.sp,
                    )
                    Text(
                        if (goal != null) " kg  ·  goal ${com.sohum.bandlog.ui.today.fmt(goal)} kg" else " kg",
                        fontSize = 13.sp, color = p.muted,
                    )
                }
            }
            if (spark.size >= 2) Sparkline(spark, p.ink, Modifier.size(96.dp, 40.dp))
        }
        if (rows.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Log a weigh-in from Profile → Weight history.", fontSize = 12.sp, color = p.muted)
        }
    }
}

/** How many medals are unlocked; taps through to the full grid. */
@Composable
private fun BadgesCard(vm: AppViewModel, onOpen: () -> Unit) {
    val p = palette
    val progress = vm.badgeProgress
    val earned = com.sohum.bandlog.util.Badges.earnedCount(progress)
    LaunchedEffect(Unit) { vm.loadBadgeTotals() }
    Card(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HexMedal(earned, earned > 0, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Badges", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(
                    "$earned of ${com.sohum.bandlog.util.Badges.ALL.size} earned",
                    fontSize = 12.sp, color = p.muted,
                )
            }
            Text("›", fontSize = 18.sp, color = p.muted)
        }
    }
}

/**
 * Energy: calories eaten vs burned for the selected week (or the last 30 days). Burned comes from
 * the local Health Connect cache (we only ever get today's figure live), so older days read 0
 * until the app has seen them.
 */
@Composable
private fun WeeklyEnergyCard(
    vm: AppViewModel,
    days: List<String>,
    xLabels: List<String>,
    monthMode: Boolean,
    ctx: android.content.Context,
) {
    val p = palette
    val today = Dates.today()
    val consumed = days.map { totalsFor(vm.meals, it).calories }
    val health = remember(days) { com.sohum.bandlog.util.BurnedCache.forDates(ctx, days) }
        .toMutableList()
        .also { list ->
            // Today's number is fresher in memory than in the cache.
            val i = days.indexOf(today)
            if (i >= 0) vm.healthToday?.let { h -> if (h.activeKcal > 0) list[i] = h.activeKcal }
        }
    // Health Connect active kcal (where we have it) + logged exercise; band-workout rows are
    // skipped while Health Connect is connected since the session is already in there.
    val burned = days.mapIndexed { i, d -> health[i] + vm.exerciseKcal(d) }
    val periodExercise = vm.exercises.filter { it.date in days }.sortedWith(compareByDescending<com.sohum.bandlog.data.ExerciseEntry> { it.date }.thenByDescending { it.createdAt })
    val totalIn = consumed.sum()
    val totalOut = burned.sum()
    val rowDateFmt = if (monthMode) WEEK_FMT else DAY_FMT

    Card {
        Text(if (monthMode) "Energy · last 30 days" else "Weekly Energy", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EnergyStat("Consumed", totalIn, p.orange)
            EnergyStat("Burned", totalOut, p.green)
            EnergyStat("Net", totalIn - totalOut, p.ink)
        }
        Spacer(Modifier.height(16.dp))
        EnergyLineChart(
            consumed = consumed,
            burned = burned,
            target = vm.profile.calorieTarget.toDouble(),
            xLabels = xLabels,
            highlightIndex = days.indexOf(today),
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroDot("Consumed", p.orange)
            MacroDot("Burned", p.green)
        }
        if (periodExercise.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(if (monthMode) "Exercise, last 30 days" else "Exercise this week", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
            Spacer(Modifier.height(4.dp))
            periodExercise.take(8).forEach { e ->
                RowSpaceBetween {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.parse(e.date).format(rowDateFmt), fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.width(if (monthMode) 46.dp else 34.dp))
                        Text(e.name.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Text("${e.minutes} min · ${e.kcal.toInt()} kcal", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (periodExercise.size > 8) Text("+${periodExercise.size - 8} more", fontSize = 12.sp, color = p.muted)
        } else {
            Spacer(Modifier.height(10.dp))
            Text("Log a run, bands or any activity from + → Exercise and it lands here.", fontSize = 12.sp, color = p.muted)
        }
    }
}

@Composable
private fun EnergyStat(label: String, value: Double, color: Color) {
    val p = palette
    Column {
        Text(label, fontSize = 12.sp, color = p.muted)
        Text(
            "${value.toInt()}",
            fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = color, lineHeight = 22.sp,
        )
        Text("kcal", fontSize = 11.sp, color = p.muted)
    }
}

/** Tiny line of the last few weigh-ins — no axes, just the shape of the trend. */
@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    val lo = values.min()
    val hi = values.max()
    val span = (hi - lo).takeIf { it > 0.01 } ?: 1.0
    Canvas(modifier) {
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size <= 1) 0f else size.width * i / (values.size - 1)
            val y = size.height - (size.height * ((v - lo) / span).toFloat()).coerceIn(0f, size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Pill progress bar. The first composition runs 0 -> value (gated by [started]); after that every
 * new [fraction] animates from wherever the bar currently is. The fill width is read in the draw
 * phase only, so a new value just redraws — nothing is cached in layout.
 */
@Composable
private fun Progress(fraction: Float, color: Color) {
    val p = palette
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); started = true }
    val f by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = Motion.spatialSlow(),
        label = "progress",
    )
    val track = p.track
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * f.coerceIn(0f, 1f)
        if (w > 0.5f) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
    }
}
