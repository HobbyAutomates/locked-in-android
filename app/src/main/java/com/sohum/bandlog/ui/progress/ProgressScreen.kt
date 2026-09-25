package com.sohum.bandlog.ui.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BmiCard
import com.sohum.bandlog.ui.components.SafetyNote
import com.sohum.bandlog.ui.components.TeenGoalMigration
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.ui.components.ArrowDownIcon
import com.sohum.bandlog.ui.components.ArrowFlatIcon
import com.sohum.bandlog.ui.components.ArrowUpIcon
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.ChevronDownIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Flame
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScanIcon
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import com.sohum.bandlog.util.Streaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val NARROW_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val WEEK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

// v2.3 macro colours for the stacked calorie bars (fixed, both themes).
private val PROTEIN = Color(0xFFE9636B)
private val CARBS = Color(0xFFE5A15B)
private val FATS = Color(0xFF5B8DEF)


/** Change-table periods: label to days back (null = all time). */
private val PERIODS = listOf("3 day" to 3L, "7 day" to 7L, "14 day" to 14L, "30 day" to 30L, "90 day" to 90L, "All time" to null)

/**
 * v2.8 Progress, cut to 4 cards: weight trend (chart + 7-entry moving average, current & goal),
 * this week's energy (eaten vs target vs burned), streak (day + week) and macros this week.
 * Everything else that used to sit on this screen — weight changes, daily average calories, the
 * detailed weekly energy breakdown, expenditure changes, BMI, progress photos, badges and sessions
 * per week — moves under a collapsed "More stats" section so nothing is lost.
 */
@Composable
fun ProgressScreen(vm: AppViewModel, onOpenBadges: () -> Unit, onLogWeight: () -> Unit) {
    val p = palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = Dates.today()
    var week by remember { mutableIntStateOf(0) }
    val ws = Dates.addDays(Dates.weekStart(today), -7L * week)
    val days = (0..6).map { Dates.addDays(ws, it.toLong()) }
    val weeks = (7 downTo 0).map { Dates.addDays(Dates.weekStart(today), -7L * it) }
    val perWeek = vm.workoutDates.groupingBy { Dates.weekStart(it) }.eachCount()
    val rest = Streaks.restByMuscle(vm.workouts).filter { it.last != null }.take(3)
    val target = vm.profile.weeklyWorkoutTarget
    var showMore by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadProgressPhotos() }
    // v2.10: low BMI / rapid loss show the kind note with helplines (never a popup; closable).
    val bodyKg = vm.weights.firstOrNull()?.weightKg ?: vm.profile.weightKg
    val flags = Goals.edFlags(Goals.screenInput(vm.profile.copy(weightKg = bodyKg), weights = vm.weights.map { Goals.WeighIn(it.date, it.weightKg) }))
    var safetyClosed by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "title") { Rise(0) { ScreenTitle("Progress") } }
        item(key = "teenGoal") { TeenGoalMigration(vm) }
        item(key = "weightTrend") { Rise(1) { WeightTrendCard(vm, onLogWeight) } }
        if (flags.isNotEmpty() && !safetyClosed) item(key = "safety") { Rise(1) { SafetyNote(vm, flags, onClose = { safetyClosed = true }) } }
        item(key = "energy") { Rise(2) { ThisWeeksEnergyCard(vm, ctx) } }
        item(key = "streak") { Rise(3) { StreakCard(vm) } }
        item(key = "macros") { Rise(4) { MacrosWeekCard(vm) } }
        item(key = "moreToggle") {
            Rise(5) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable().background(p.card2, RoundedCornerShape(16.dp))
                        .clickable { showMore = !showMore }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    RowSpaceBetween {
                        Text("More stats", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Icon(
                            ChevronDownIcon, null, tint = p.ink, modifier = Modifier.size(16.dp)
                                .graphicsLayer { rotationZ = if (showMore) 180f else 0f },
                        )
                    }
                }
            }
        }
        if (showMore) {
            item(key = "weightChanges") { Rise(0) { WeightChangesCard(vm) } }
            item(key = "weekPick") {
                Rise(1) { Segmented4(listOf("This wk", "Last wk", "2 wk ago", "3 wk ago"), week) { week = it } }
            }
            item(key = "calories") { Rise(1) { DailyCaloriesCard(vm, days) } }
            // v2.10 "Hide calorie numbers": the kcal-by-day breakdown is left out entirely.
            if (vm.profile.hideNumbers != true) item(key = "energyDetail") { Rise(2) { WeeklyEnergyCard(vm, days, days.map { Dates.parse(it).format(NARROW_FMT) }, false, ctx) } }
            item(key = "expenditure") { Rise(2) { ExpenditureChangesCard(vm, ctx) } }
            item(key = "bmi") { Rise(3) { BmiCard(vm, bodyKg, waist = true) } }
            item(key = "photos") { Rise(3) { PhotosCard(vm) } }
            item(key = "badges") { Rise(4) { BadgesCard(vm, onOpenBadges) } }
            item(key = "week") {
                Rise(4) {
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
            item(key = "sessions") {
                Rise(5) {
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
}

// ---------------------------------------------------------------- card 1: weight trend

/** A trailing moving average over [window] entries — the first ones average over what's so far. */
private fun movingAverage(values: List<Double>, window: Int = 7): List<Double> {
    val out = ArrayList<Double>(values.size)
    var sum = 0.0
    values.forEachIndexed { i, v ->
        sum += v
        if (i >= window) sum -= values[i - window]
        val n = minOf(i + 1, window)
        out.add(sum / n)
    }
    return out
}

@Composable
private fun WeightTrendCard(vm: AppViewModel, onLogWeight: () -> Unit) {
    val p = palette
    val rows = vm.weights // newest first
    val today = Dates.today()
    val current = rows.firstOrNull()?.weightKg ?: vm.profile.weightKg
    val goal = vm.profile.goalWeightKg
    val start = rows.lastOrNull()?.weightKg ?: current
    val last = rows.firstOrNull()?.date
    val nextIn = last?.let { 7 - Dates.daysBetween(it, today) }
    val asc = rows.sortedBy { it.date }.map { it.weightKg }

    Card(padding = 20.dp) {
        RowSpaceBetween {
            Text("Weight trend", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
            Box(
                Modifier.background(p.card2, CircleShape).pressable().clickable { onLogWeight() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    when { nextIn == null -> "Log your first weigh-in"; nextIn <= 0 -> "Weigh-in due today"; else -> "Next weigh-in: ${nextIn}d" },
                    fontSize = 12.sp, fontWeight = FontWeight(700), color = if (nextIn != null && nextIn <= 0) p.flame else p.ink, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(current?.let { fmt(it) } ?: "—", fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 42.sp)
            Text(" kg", fontSize = 16.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (asc.size >= 2) {
            Spacer(Modifier.height(10.dp))
            WeightTrendChart(asc, goal, Modifier.fillMaxWidth().height(90.dp))
        } else {
            Spacer(Modifier.height(6.dp))
            Text("Log a few weigh-ins to see your trend.", fontSize = 12.sp, color = p.muted)
        }
        if (goal != null && current != null && start != null) {
            val span = start - goal
            val frac = if (abs(span) < 0.05) (if (abs(current - goal) < 0.05) 1f else 0f) else ((start - current) / span).toFloat().coerceIn(0f, 1f)
            Spacer(Modifier.height(12.dp))
            Progress(frac, p.ink)
            Spacer(Modifier.height(6.dp))
            RowSpaceBetween {
                Text("Start ${fmt(start)} kg", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                Text("${(frac * 100).roundToInt()}%", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("Goal ${fmt(goal)} kg", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(if (goal == null) "Set a goal weight in Profile → Goal & weight." else "Log a weigh-in from + → Weight.", fontSize = 12.sp, color = p.muted)
        }
    }
}

/** The raw weigh-ins (faint) plus their 7-entry moving average (bold), with a dashed goal line. */
@Composable
private fun WeightTrendChart(values: List<Double>, goal: Double?, modifier: Modifier) {
    val p = palette
    val avg = remember(values) { movingAverage(values, 7) }
    val track = p.track
    val ink = p.ink
    val hair = p.hair
    Canvas(modifier) {
        val all = if (goal != null) values + goal else values
        val lo = all.min()
        val hi = all.max()
        val span = (hi - lo).takeIf { it > 0.001 } ?: 1.0
        val pad = span * 0.12
        fun y(v: Double) = size.height - (size.height * ((v - (lo - pad)) / (span + pad * 2))).toFloat()
        fun x(i: Int) = if (values.size <= 1) size.width / 2 else size.width * i / (values.size - 1)

        if (goal != null) {
            val gy = y(goal)
            drawLine(hair, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
        fun path(vs: List<Double>): Path = Path().apply {
            vs.forEachIndexed { i, v -> val px = x(i); val py = y(v); if (i == 0) moveTo(px, py) else lineTo(px, py) }
        }
        drawPath(path(values), track, style = Stroke(width = 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(path(avg), ink, style = Stroke(width = 2.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(ink, 4f, Offset(x(values.lastIndex), y(values.last())))
    }
}

// ---------------------------------------------------------------- card 2: this week's energy

@Composable
private fun ThisWeeksEnergyCard(vm: AppViewModel, ctx: android.content.Context) {
    val p = palette
    val today = Dates.today()
    val ws = Dates.weekStart(today)
    val daysSoFar = (Dates.daysBetween(ws, today) + 1).toInt()
    val days = (0 until daysSoFar).map { Dates.addDays(ws, it.toLong()) }
    val eaten = days.sumOf { totalsFor(vm.meals, it).calories }
    val burned = remember(days, vm.exercises, vm.healthToday) { burnedSeries(vm, days, ctx) }.sum()
    val target = vm.profile.calorieTarget.toDouble() * daysSoFar
    val net = eaten - burned
    val frac = if (target > 0) (net / target).toFloat() else 0f

    // v2.10 "Hide calorie numbers": the bar and words, no kcal anywhere on the card.
    if (vm.profile.hideNumbers == true) {
        Card {
            Text("This week's energy", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Since ${Dates.parse(ws).format(DAY_FMT)}, ${Dates.parse(ws).format(WEEK_FMT)}", fontSize = 12.sp, color = p.muted)
            Spacer(Modifier.height(12.dp))
            Text(Goals.calorieWords(net, target), fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(8.dp))
            Progress(frac, p.ink)
            Spacer(Modifier.height(6.dp))
            Text(if (burned > 0) "Includes the exercise you logged." else "Numbers are hidden. You can turn them back on in Tracking.", fontSize = 12.sp, color = p.muted)
        }
        return
    }

    Card {
        Text("This week's energy", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Text("Since ${Dates.parse(ws).format(DAY_FMT)}, ${Dates.parse(ws).format(WEEK_FMT)}", fontSize = 12.sp, color = p.muted)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EnergyStat("Eaten", eaten, p.ink)
            EnergyStat("Target", target, p.muted)
            EnergyStat("Burned", burned, p.green)
        }
        Spacer(Modifier.height(12.dp))
        Progress(frac, if (frac > 1.05f) androidx.compose.ui.graphics.Color(0xFFE9636B) else p.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "${net.roundToInt()} kcal net of ${target.roundToInt()} kcal target",
            fontSize = 12.sp, color = p.muted,
        )
    }
}

// ---------------------------------------------------------------- card 3: streak

@Composable
private fun StreakCard(vm: AppViewModel) {
    val p = palette
    Card {
        Text("Streak", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Flame(p.flame, 40.dp)
                Text("${vm.dayStreak}", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.flame, lineHeight = 26.sp)
                Text("Day streak", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Flame(p.flame, 40.dp)
                Text("${vm.weekStreak}", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.flame, lineHeight = 26.sp)
                Text("Week streak", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink)
            }
        }
    }
}

// ---------------------------------------------------------------- card 4: macros this week

@Composable
private fun MacrosWeekCard(vm: AppViewModel) {
    val p = palette
    val today = Dates.today()
    val ws = Dates.weekStart(today)
    val daysSoFar = (Dates.daysBetween(ws, today) + 1).toInt()
    val days = (0 until daysSoFar).map { Dates.addDays(ws, it.toLong()) }
    val totals = days.map { totalsFor(vm.meals, it) }
    val protein = totals.sumOf { it.protein }
    val carbs = totals.sumOf { it.carbs }
    val fat = totals.sumOf { it.fat }
    val pk = protein * 4; val ck = carbs * 4; val fk = fat * 9
    val sum = (pk + ck + fk).coerceAtLeast(1.0)

    Card {
        Text("Macros this week", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(12.dp).background(p.track, RoundedCornerShape(6.dp))) {
            Box(Modifier.weight((pk / sum).toFloat().coerceAtLeast(0.001f)).fillMaxSize().background(PROTEIN, RoundedCornerShape(6.dp)))
            Box(Modifier.weight((ck / sum).toFloat().coerceAtLeast(0.001f)).fillMaxSize().background(CARBS))
            Box(Modifier.weight((fk / sum).toFloat().coerceAtLeast(0.001f)).fillMaxSize().background(FATS, RoundedCornerShape(6.dp)))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroDot("Protein ${protein.roundToInt()}g", PROTEIN)
            MacroDot("Carbs ${carbs.roundToInt()}g", CARBS)
            MacroDot("Fats ${fat.roundToInt()}g", FATS)
        }
    }
}

// ---------------------------------------------------------------- (b) + (d) change tables

/** One row of a change table: the series drawn as a sparkline, and the signed change. */
private data class ChangeRow(val label: String, val series: List<Double>, val delta: Double?)

@Composable
private fun WeightChangesCard(vm: AppViewModel) {
    val today = Dates.today()
    val asc = vm.weights.sortedWith(compareBy({ it.date })).map { it.date to it.weightKg }
    val rows = PERIODS.map { (label, n) ->
        if (asc.isEmpty()) return@map ChangeRow(label, emptyList(), null)
        val from = n?.let { Dates.addDays(today, -it) }
        val inside = if (from == null) asc else asc.filter { it.first >= from }
        val before = if (from == null) null else asc.lastOrNull { it.first < from }
        val series = listOfNotNull(before?.second) + inside.map { it.second }
        val delta = if (series.size >= 2) series.last() - series.first() else null
        ChangeRow(label, series, delta)
    }
    ChangeTable("Weight changes", rows, unit = "kg", threshold = 0.05, decimals = true, empty = if (asc.isEmpty()) "Log a weigh-in to see changes." else null)
}

@Composable
private fun ExpenditureChangesCard(vm: AppViewModel, ctx: android.content.Context) {
    val today = Dates.today()
    val all = (119 downTo 0).map { Dates.addDays(today, -it.toLong()) }
    val burned = burnedSeries(vm, all, ctx)
    val firstActive = burned.indexOfFirst { it > 0 }
    val rows = PERIODS.map { (label, n) ->
        if (firstActive < 0) return@map ChangeRow(label, emptyList(), null)
        if (n == null) {
            val span = burned.drop(firstActive)
            val half = span.size / 2
            val delta = if (half >= 1) span.drop(half).average() - span.take(half).average() else null
            ChangeRow(label, weeklyAverages(span), delta)
        } else {
            val k = n.toInt()
            val recent = burned.takeLast(k)
            // The period before, but never earlier than the first day anything was burned.
            val prevEnd = burned.size - k
            val prev = if (prevEnd <= firstActive) emptyList() else burned.subList(maxOf(firstActive, prevEnd - k), prevEnd)
            val delta = if (prev.isNotEmpty()) recent.average() - prev.average() else null
            ChangeRow(label, if (k > 30) weeklyAverages(recent) else recent, delta)
        }
    }
    ChangeTable(
        "Expenditure changes", rows, unit = "kcal/day", threshold = 5.0, decimals = false,
        subtitle = "Average burned vs the period before",
        empty = if (firstActive < 0) "Log exercise or connect Health Connect to see changes." else null,
    )
}

/** 7-day means, so a long series still reads as a trend. */
private fun weeklyAverages(v: List<Double>): List<Double> = v.chunked(7).map { it.average() }

/** Burned per day: Health Connect cache + exercise_log rows, deduplicated like Weekly Energy. */
private fun burnedSeries(vm: AppViewModel, days: List<String>, ctx: android.content.Context): List<Double> {
    val today = Dates.today()
    val health = com.sohum.bandlog.util.BurnedCache.forDates(ctx, days).toMutableList()
    val i = days.indexOf(today)
    if (i >= 0) vm.healthToday?.let { h -> if (h.activeKcal > 0) health[i] = h.activeKcal }
    val byDate = vm.exercises.groupBy { it.date }
    return days.mapIndexed { idx, d ->
        health[idx] + (byDate[d] ?: emptyList()).filter { !(vm.healthConnected && health[idx] > 0 && it.source == "workout") }.sumOf { it.kcal }
    }
}

@Composable
private fun ChangeTable(title: String, rows: List<ChangeRow>, unit: String, threshold: Double, decimals: Boolean, subtitle: String? = null, empty: String? = null) {
    val p = palette
    Card(padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(top = 14.dp))
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = p.muted)
            if (empty != null) {
                Text(empty, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                return@Column
            }
            Spacer(Modifier.height(6.dp))
            rows.forEachIndexed { i, r ->
                if (i > 0) Hair()
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.width(66.dp), maxLines = 1)
                    val d = r.delta
                    val flat = d == null || abs(d) < threshold
                    val tint = if (flat) p.muted else p.ink
                    Sparkline(r.series, tint, Modifier.weight(1f).height(22.dp).padding(horizontal = 8.dp))
                    Row(Modifier.width(122.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (flat) ArrowFlatIcon else if (d!! < 0) ArrowDownIcon else ArrowUpIcon, null, tint = tint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        val amount = d?.let { abs(it) }?.let { if (decimals) fmt((it * 10).roundToInt() / 10.0) else "${it.roundToInt()}" }
                        Text(
                            when { d == null -> "Not enough data"; flat -> "No change"; else -> "${if (d < 0) "Down" else "Up"} $amount ${unit.substringBefore('/')}" },
                            fontSize = 13.sp, fontWeight = FontWeight(if (flat) 500 else 700), color = tint, maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ---------------------------------------------------------------- (c) daily average calories

@Composable
private fun DailyCaloriesCard(vm: AppViewModel, days: List<String>) {
    val p = palette
    val today = Dates.today()
    val totals = days.map { totalsFor(vm.meals, it) }
    val logged = totals.filter { it.calories > 0 }
    val avg = if (logged.isEmpty()) 0.0 else logged.map { it.calories }.average()
    val avgP = if (logged.isEmpty()) 0.0 else logged.map { it.protein }.average()
    val avgC = if (logged.isEmpty()) 0.0 else logged.map { it.carbs }.average()
    val avgF = if (logged.isEmpty()) 0.0 else logged.map { it.fat }.average()
    val target = vm.profile.calorieTarget.toDouble()
    val track = p.track
    val hair = p.hair
    val ink = p.ink

    Card {
        if (vm.profile.hideNumbers == true) {
            Text("Daily average", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
            Text(if (logged.isEmpty()) "Nothing logged yet" else Goals.calorieWords(avg, target), fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        } else {
            Text("Daily average calories", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%,d", avg.roundToInt()), fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 32.sp)
                Text(" kcal · target ${String.format(Locale.US, "%,d", target.roundToInt())}", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        // Stacked bars: protein at the bottom, then carbs, then fats; the dashed line is the target.
        val maxKcal = (maxOf(target, totals.maxOfOrNull { it.protein * 4 + it.carbs * 4 + it.fat * 9 } ?: 0.0) * 1.12).coerceAtLeast(1.0)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = days.size
            val slot = size.width / n
            val barW = slot * 0.46f
            val r = CornerRadius(barW / 2.4f)
            fun y(v: Double) = size.height - (size.height * (v / maxKcal)).toFloat()
            days.indices.forEach { i ->
                val t = totals[i]
                val x = slot * i + (slot - barW) / 2
                val segs = listOf(t.protein * 4 to PROTEIN, t.carbs * 4 to CARBS, t.fat * 9 to FATS)
                val total = segs.sumOf { it.first }
                if (total <= 0) {
                    drawRoundRect(track, Offset(x, size.height - 6f), Size(barW, 6f), cornerRadius = CornerRadius(3f))
                    return@forEach
                }
                val top = y(total)
                val clip = Path().apply { addRoundRect(RoundRect(x, top, x + barW, size.height, r)) }
                clipPath(clip) {
                    var bottom = size.height
                    segs.forEach { (kcal, c) ->
                        val h = size.height * (kcal / maxKcal).toFloat()
                        if (h > 0f) drawRect(c, Offset(x, bottom - h), Size(barW, h))
                        bottom -= h
                    }
                }
                if (days[i] == today) drawCircle(ink, 3f, Offset(x + barW / 2, top - 8f))
            }
            if (target > 0) {
                val ty = y(target)
                drawLine(hair, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEach { d ->
                Text(
                    Dates.parse(d).format(DAY_FMT), Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 11.sp, fontWeight = FontWeight(if (d == today) 800 else 600), color = if (d == today) p.ink else p.muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroDot("Protein ${avgP.roundToInt()}g", PROTEIN)
            MacroDot("Carbs ${avgC.roundToInt()}g", CARBS)
            MacroDot("Fats ${avgF.roundToInt()}g", FATS)
        }
        if (logged.isEmpty()) Text("No meals logged this week.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---------------------------------------------------------------- (f) progress photos

@Composable
private fun PhotosCard(vm: AppViewModel) {
    val p = palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val captureFile = remember { java.io.File(java.io.File(ctx.cacheDir, "scans").apply { mkdirs() }, "progress.jpg") }

    fun upload(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            busy = true
            val bmp = withContext(Dispatchers.IO) { runCatching { com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 1024) }.getOrNull() }
            if (bmp == null) vm.progressPhotosError = "Couldn't open that photo" else vm.uploadProgressPhoto(bmp)
            busy = false
        }
    }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { upload(it) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) upload(android.net.Uri.fromFile(captureFile))
    }

    Card {
        RowSpaceBetween {
            Text("Progress photos", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            if (vm.progressPhotos.isNotEmpty()) Text("${vm.progressPhotos.size}", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.size(96.dp, 120.dp).pressable().background(p.card2, RoundedCornerShape(16.dp)).clickable(enabled = !busy) { sheet = true }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.ink)
                else Icon(CameraIcon, null, tint = p.ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                Text(if (busy) "Uploading…" else "Upload a photo", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = TextAlign.Center)
            }
            vm.progressPhotos.forEach { ph ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RemoteImage(privatePath = ph.path, bucket = "progress-photos", size = 96.dp, radius = 16.dp, fallback = ScanIcon)
                    Text(runCatching { Dates.parse(ph.date).format(WEEK_FMT) }.getOrDefault(ph.date), fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (vm.progressPhotos.isEmpty() && vm.progressPhotosError == null) Text("Snap one a week — same spot, same light — and watch the change.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 10.dp))
        ErrorNote(vm.progressPhotosError, Modifier.padding(top = 10.dp))
    }

    if (sheet) BottomSheet(title = "Progress photo", subtitle = "Private — only you can see these", onDismiss = { sheet = false }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
            sheet = false
            runCatching {
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", captureFile)
                camera.launch(uri)
            }.onFailure { vm.progressPhotosError = "No camera app found — choose a photo instead." }
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(CameraIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Take photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
        }
        Hair()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
            sheet = false
            gallery.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(ScanIcon, null, tint = p.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text("Choose from gallery", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** Four-option segmented control (the shared one caps at three). */
@Composable
private fun Segmented4(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().background(p.card2, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(36.dp)
                    .shadow(if (sel) 6.dp else 0.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .background(if (sel) p.card else Color.Transparent, CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted, maxLines = 1, softWrap = false) }
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
                Text("$earned of ${com.sohum.bandlog.util.Badges.ALL.size} earned", fontSize = 12.sp, color = p.muted)
            }
            Text("›", fontSize = 18.sp, color = p.muted)
        }
    }
}

/**
 * Energy: calories eaten vs burned for the selected week. Burned comes from the local Health
 * Connect cache (we only ever get today's figure live), so older days read 0 until the app has
 * seen them.
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
    val burned = remember(days, vm.exercises, vm.healthToday) { burnedSeries(vm, days, ctx) }
    val periodExercise = vm.exercises.filter { it.date in days }.sortedWith(compareByDescending<com.sohum.bandlog.data.ExerciseEntry> { it.date }.thenByDescending { it.createdAt })
    val totalIn = consumed.sum()
    val totalOut = burned.sum()
    val rowDateFmt = if (monthMode) WEEK_FMT else DAY_FMT

    Card {
        Text("Weekly Energy", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
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
            Text("Exercise this week", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
            Spacer(Modifier.height(4.dp))
            periodExercise.take(8).forEach { e ->
                RowSpaceBetween {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.parse(e.date).format(rowDateFmt), fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.width(34.dp))
                        Icon(com.sohum.bandlog.ui.components.activityIcon(e.name, e.activityCode), null, tint = p.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(e.name.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Text(
                        "${e.minutes} min${e.distanceKm?.let { " · ${fmt(it)} km" } ?: ""} · ${e.kcal.toInt()} kcal",
                        fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 8.dp), maxLines = 1,
                    )
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
        Text("${value.toInt()}", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = color, lineHeight = 22.sp)
        Text("kcal", fontSize = 11.sp, color = p.muted)
    }
}

/** Tiny line — no axes, just the shape of the trend. Fewer than two points draw a flat hairline. */
@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    val hair = palette.hair
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(hair, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f)
            return@Canvas
        }
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0.01 } ?: 1.0
        val flat = hi - lo <= 0.01
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = if (flat) size.height / 2 else size.height - (size.height * ((v - lo) / span).toFloat()).coerceIn(0f, size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Pill progress bar. The first composition runs 0 -> value (gated by [started]); after that every
 * new [fraction] animates from wherever the bar currently is.
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
