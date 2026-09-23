package com.sohum.bandlog.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun ProgressScreen(vm: AppViewModel, onOpenBadges: () -> Unit) {
    val p = palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = Dates.today()
    var weekBack by remember { mutableIntStateOf(0) }
    val ws = Dates.addDays(Dates.weekStart(today), -7L * weekBack)
    val days = (0..6).map { Dates.addDays(ws, it.toLong()) }
    val protein = days.map { totalsFor(vm.meals, it).protein }
    val logged = protein.filter { it > 0 }
    val avg = if (logged.isEmpty()) 0.0 else logged.average()
    val weeks = (7 downTo 0).map { Dates.addDays(Dates.weekStart(today), -7L * it) }
    val perWeek = vm.workoutDates.groupingBy { Dates.weekStart(it) }.eachCount()
    val rest = Streaks.restByMuscle(vm.workouts).filter { it.last != null }.take(3)
    val target = vm.profile.weeklyWorkoutTarget
    val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Rise(0) { ScreenTitle("Progress") } }
        item { Rise(1) { WeightCard(vm) } }
        item {
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
                        Progress((vm.thisWeek.toFloat() / target.coerceAtLeast(1)), p.ink)
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
        item { Rise(2) { BadgesCard(vm, onOpenBadges) } }
        item { Rise(2) { Segmented(listOf("This week", "Last week", "2 wks ago", "3 wks ago"), weekBack, { weekBack = it }, height = 34.dp) } }
        item { Rise(3) { WeeklyEnergyCard(vm, days, ctx) } }
        item {
            Rise(3) {
                Card {
                    Text("Protein", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${avg.toInt()}", fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink, lineHeight = 28.sp)
                        Text(" g / day avg · target ${vm.profile.proteinTargetG}", fontSize = 13.sp, color = p.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Bars(protein, vm.profile.proteinTargetG.toDouble(), days.map { Dates.parse(it).format(dayFmt) }, p.red, p.redBg, highlightLast = weekBack == 0)
                }
            }
        }
        item {
            Rise(4) {
                Card {
                    RowSpaceBetween {
                        Text("Sessions per week", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("target $target", fontSize = 12.sp, color = p.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Bars(weeks.map { (perWeek[it] ?: 0).toDouble() }, target.toDouble(), weeks.map { Dates.parse(it).dayOfMonth.toString() }, p.ink, p.track, highlightLast = true, lastColor = p.green, height = 70.dp)
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
 * Weekly Energy: calories eaten vs burned for the selected week. Burned comes from the local
 * Health Connect cache (we only ever get today's figure live), so older days read 0 until the
 * app has seen them.
 */
@Composable
private fun WeeklyEnergyCard(vm: AppViewModel, days: List<String>, ctx: android.content.Context) {
    val p = palette
    val consumed = days.map { totalsFor(vm.meals, it).calories }
    val health = remember(days) { com.sohum.bandlog.util.BurnedCache.forDates(ctx, days) }
        .toMutableList()
        .also { list ->
            // Today's number is fresher in memory than in the cache.
            val i = days.indexOf(Dates.today())
            if (i >= 0) vm.healthToday?.let { h -> if (h.activeKcal > 0) list[i] = h.activeKcal }
        }
    // Health Connect active kcal (where we have it) + logged exercise; band-workout rows are
    // skipped while Health Connect is connected since the session is already in there.
    val burned = days.mapIndexed { i, d -> health[i] + vm.exerciseKcal(d) }
    val weekExercise = vm.exercises.filter { it.date in days }.sortedWith(compareByDescending<com.sohum.bandlog.data.ExerciseEntry> { it.date }.thenByDescending { it.createdAt })
    val totalIn = consumed.sum()
    val totalOut = burned.sum()

    Card {
        Text("Weekly Energy", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EnergyStat("Consumed", totalIn, p.orange)
            EnergyStat("Burned", totalOut, p.green)
            EnergyStat("Net", totalIn - totalOut, p.ink)
        }
        Spacer(Modifier.height(16.dp))
        LineChart(consumed, burned, p.orange, p.green, Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center,
                    color = if (days.getOrNull(i) == Dates.today()) p.ink else p.muted,
                    fontWeight = if (days.getOrNull(i) == Dates.today()) FontWeight(700) else FontWeight(400),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroDot("Consumed", p.orange)
            MacroDot("Burned", p.green)
        }
        if (weekExercise.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Exercise this week", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
            Spacer(Modifier.height(4.dp))
            weekExercise.take(8).forEach { e ->
                RowSpaceBetween {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.parse(e.date).format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.width(34.dp))
                        Text(e.name.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Text("${e.minutes} min · ${e.kcal.toInt()} kcal", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
            if (weekExercise.size > 8) Text("+${weekExercise.size - 8} more", fontSize = 12.sp, color = p.muted)
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

/** Two 7-point polylines on a shared scale, with a dot at each day. */
@Composable
private fun LineChart(a: List<Double>, b: List<Double>, colorA: Color, colorB: Color, modifier: Modifier) {
    val p = palette
    val max = maxOf(a.maxOrNull() ?: 0.0, b.maxOrNull() ?: 0.0, 1.0)
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); go = true }
    val f by animateFloatAsState(if (go) 1f else 0f, Motion.spatialSlow(), label = "line")

    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Faint baseline so an all-zero week still reads as a chart.
        drawLine(p.hair, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(w, h), strokeWidth = 1.5f)

        fun points(values: List<Double>): List<androidx.compose.ui.geometry.Offset> =
            values.mapIndexed { i, v ->
                val x = if (values.size <= 1) 0f else w * i / (values.size - 1)
                val y = h - (h * (v / max).toFloat() * f).coerceIn(0f, h)
                androidx.compose.ui.geometry.Offset(x, y)
            }

        listOf(a to colorA, b to colorB).forEach { (values, color) ->
            val pts = points(values)
            val path = androidx.compose.ui.graphics.Path().apply {
                pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            drawPath(path, color, style = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            pts.forEach { o -> drawCircle(color, radius = 3.5f, center = o) }
        }
    }
}

/** Tiny line of the last few weigh-ins — no axes, just the shape of the trend. */
@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    val lo = values.min()
    val hi = values.max()
    val span = (hi - lo).takeIf { it > 0.01 } ?: 1.0
    androidx.compose.foundation.Canvas(modifier) {
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size <= 1) 0f else size.width * i / (values.size - 1)
            val y = size.height - (size.height * ((v - lo) / span).toFloat()).coerceIn(0f, size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun Progress(fraction: Float, color: Color) {
    val p = palette
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); go = true }
    val f by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, Motion.spatialSlow(), label = "progress")
    Box(Modifier.fillMaxWidth().height(6.dp).background(p.track, CircleShape)) { Box(Modifier.fillMaxWidth(f).height(6.dp).background(color, CircleShape)) }
}

@Composable
private fun Bars(values: List<Double>, target: Double, labels: List<String>, color: Color, dim: Color, highlightLast: Boolean, lastColor: Color? = null, height: androidx.compose.ui.unit.Dp = 96.dp) {
    val p = palette
    val max = maxOf(values.maxOrNull() ?: 0.0, target, 1.0)
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); go = true }
    Box(Modifier.fillMaxWidth().height(height)) {
        // Dashed-ish target line at the target's height.
        Box(Modifier.padding(top = (height.value * (1 - target / max)).toFloat().dp).fillMaxWidth().height(1.dp).background(p.hair))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val last = i == values.lastIndex
                val f by animateFloatAsState(if (go) (v / max).toFloat().coerceIn(0.03f, 1f) else 0.03f, Motion.spatialSlow(), label = "bar$i")
                val c = when { last && highlightLast && lastColor != null -> lastColor; v >= target && target > 0 -> color; else -> dim }
                Box(Modifier.weight(1f).fillMaxHeight(f).background(c, RoundedCornerShape(8.dp, 8.dp, 4.dp, 4.dp)))
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> Text(l, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center, color = if (i == labels.lastIndex && highlightLast) p.ink else p.muted, fontWeight = if (i == labels.lastIndex && highlightLast) FontWeight(700) else FontWeight(400)) }
    }
}
