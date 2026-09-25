package com.sohum.bandlog.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BmiCard
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.ScienceButton
import com.sohum.bandlog.ui.components.WaistRow
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.motion.countUp
import com.sohum.bandlog.ui.motion.dropIn
import com.sohum.bandlog.ui.motion.popIn
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Badges
import com.sohum.bandlog.util.Bmi
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.MealTypes
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * v2.12 Progress cards (design canvas3 "ProgressFinal"): Weight → Streak → Energy → Macros → BMI →
 * Meal times. Each card is wrapped in a ui.motion.Entrance by ProgressScreen; the sub-animations
 * here (count-ups, pen-drawn lines, pops, drops, the BMI swing) time themselves off that card.
 */

/** The Week / Month / 3 months switch at the top of Progress. */
enum class ProgressRange(val label: String, val days: Int) {
    WEEK("Week", 7), MONTH("Month", 30), QUARTER("3 months", 90);
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val NARROW: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
private val DAY_NUM: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)

/** A rounded status pill, e.g. "−1.4 kg · 30d" or "5 of 7 on target". */
@Composable
internal fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight(700), color = color, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun CardLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 13.sp, fontWeight = FontWeight(600), color = palette.muted, modifier = modifier)

// ---------------------------------------------------------------- 1. Weight

/** 7-day trailing average (by date) for each weigh-in, ascending. */
private fun sevenDayAverage(asc: List<Pair<String, Double>>): List<Pair<String, Double>> = asc.map { (d, _) ->
    val from = Dates.addDays(d, -6)
    val window = asc.filter { it.first in from..d }.map { it.second }
    d to window.average()
}

@Composable
fun WeightCardV2(vm: AppViewModel, range: ProgressRange, onLogWeight: () -> Unit) {
    val p = palette
    val today = Dates.today()
    val rows = vm.weights // newest first
    val current = rows.firstOrNull()?.weightKg ?: vm.profile.weightKg
    val goal = vm.profile.goalWeightKg
    val windowDays = maxOf(range.days, 30)
    val ascAll = rows.sortedBy { it.date }.map { it.date to it.weightKg }
    val avgAll = remember(ascAll) { sevenDayAverage(ascAll) }
    val from = Dates.addDays(today, -windowDays.toLong())
    var series = avgAll.filter { it.first >= from }
    if (series.size < 2) series = avgAll.takeLast(2)
    val spanStart = series.firstOrNull()?.first ?: from
    val spanDays = maxOf(1L, Dates.daysBetween(spanStart, today)).toFloat()
    val xs = series.map { (Dates.daysBetween(spanStart, it.first) / spanDays).toFloat() }
    val change = if (series.size >= 2) series.last().second - series.first().second else null
    val towardGoal = change != null && goal != null && current != null && ((goal < current && change < 0) || (goal > current && change > 0))
    val trendColor = when {
        change == null -> p.muted
        goal == null -> p.ink
        towardGoal || (goal != null && current != null && abs(current - goal) < 0.3) -> p.green
        else -> p.orange
    }
    val last = rows.firstOrNull()?.date
    val nextIn = last?.let { 7 - Dates.daysBetween(it, today) }

    val count = rememberMotion("weight-count-${current}", 200, PremiumMotion.COUNT_MS)
    val pill = rememberMotion("weight-pill", 700, PremiumMotion.DROP_MS)

    Card(padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                CardLabel("Weight")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        current?.let { String.format(Locale.US, "%.1f", countUp(it, count.value)) } ?: "—",
                        fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 44.sp,
                        modifier = Modifier.semantics { contentDescription = current?.let { "Weight ${fmt(it)} kilograms" } ?: "No weight logged" },
                    )
                    Text(" kg", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (change != null) {
                    val sign = if (change < -0.05) "−" else if (change > 0.05) "+" else "±"
                    StatusPill("$sign${fmt((abs(change) * 10).roundToInt() / 10.0)} kg · ${windowDays}d", trendColor, Modifier.dropIn(pill))
                }
                // Kept from v2.8: the weigh-in reminder chip, tap to log.
                Box(
                    Modifier.heightIn(min = 32.dp).background(p.card2, CircleShape).pressable().clickable(onClickLabel = "Log a weigh-in") { onLogWeight() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when { nextIn == null -> "Log first weigh-in"; nextIn <= 0 -> "Weigh-in due"; else -> "Next weigh-in ${nextIn}d" },
                        fontSize = 12.sp, fontWeight = FontWeight(700), color = if (nextIn != null && nextIn <= 0) p.flame else p.ink, maxLines = 1,
                    )
                }
            }
        }
        if (series.size >= 2) {
            Spacer(Modifier.height(10.dp))
            WeightTrendLine(
                xs, series.map { it.second }, if (trendColor == p.muted) p.ink else trendColor, "weight-$windowDays",
                Modifier.fillMaxWidth().height(130.dp).semantics {
                    contentDescription = "Weight 7-day average ${if ((change ?: 0.0) < 0) "down" else "up"} ${fmt((abs(change ?: 0.0) * 10).roundToInt() / 10.0)} kilograms in $windowDays days"
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Dates.parse(spanStart).format(SHORT_DATE), fontSize = 12.sp, color = p.muted)
                Text(goalEta(vm, current, series, spanDays), fontSize = 12.sp, color = p.muted, maxLines = 1)
                Text("Today", fontSize = 12.sp, color = p.muted)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Log a few weigh-ins to see your trend.", fontSize = 12.sp, color = p.muted)
            if (goal == null) Text("Set a goal weight in Profile → Goal weight.", fontSize = 12.sp, color = p.muted)
        }
    }
}

/** "Goal 68 kg · about Nov 20" from the recent trend (or the planned pace when the trend is flat / wrong way). */
internal fun goalEta(vm: AppViewModel, current: Double?, series: List<Pair<String, Double>>, spanDays: Float): String {
    val goal = vm.profile.goalWeightKg ?: return "No goal set"
    val g = "Goal ${fmt(goal)} kg"
    if (current == null) return g
    val remaining = goal - current
    if (abs(remaining) < 0.2) return "$g · reached"
    val perDayTrend = if (series.size >= 2) (series.last().second - series.first().second) / spanDays else 0.0
    val perDay = if (perDayTrend * remaining > 0 && abs(perDayTrend) > 0.005) abs(perDayTrend) else vm.profile.goalSpeedKgWk.coerceAtLeast(0.1) / 7.0
    val days = (abs(remaining) / perDay).roundToInt()
    if (days > 730) return g
    return "$g · about ${LocalDate.now(Dates.ZONE).plusDays(days.toLong()).format(SHORT_DATE)}"
}

// ---------------------------------------------------------------- 2. Streak

@Composable
fun StreakCardV2(vm: AppViewModel) {
    val p = palette
    val today = Dates.today()
    val logged = remember(vm.workouts, vm.exercises, vm.meals) {
        (vm.workouts.map { it.date } + vm.exercises.map { it.date } + vm.meals.map { it.date }).toSet()
    }
    val current = vm.dayStreak
    val best = maxOf(Badges.longestDayRun(logged), current)
    val ws = Dates.weekStart(today)
    val week = (0..6).map { Dates.addDays(ws, it.toLong()) }
    val count = rememberMotion("streak-count", 200, PremiumMotion.COUNT_MS)
    val flame = p.orange

    Card(padding = 20.dp) {
        CardLabel("Streak")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.Flame, null, tint = flame, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${countUp(current, count.value)}", fontSize = 44.sp, fontWeight = FontWeight(800), letterSpacing = (-1.5).sp, color = p.ink, lineHeight = 46.sp,
                modifier = Modifier.semantics { contentDescription = "$current day streak" },
            )
            Text(if (current == 1) " day" else " days", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    buildAnnotatedString { append("Best "); withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight(700))) { append("$best") } },
                    fontSize = 13.sp, color = p.muted,
                )
                Text(
                    when { current == 0 -> "Log today to start"; current >= best -> "Your best yet"; else -> "${best - current} to beat it" },
                    fontSize = 13.sp, color = p.muted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val desc = week.filter { it <= today }.joinToString { d -> "${Dates.parse(d).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${if (d in logged) "logged" else "not logged"}" }
        Row(Modifier.fillMaxWidth().semantics { contentDescription = "This week: $desc" }) {
            week.forEachIndexed { i, d ->
                val on = d in logged
                val pop = rememberMotion("streak-day$i", 520 + i * 120, PremiumMotion.POP_MS)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(30.dp).popIn(pop).background(if (on) flame else p.card2, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(LineIcons.Flame, null, tint = if (p.bg.luminance() < 0.5f) Color.Black else Color.White, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        Dates.parse(d).format(NARROW), fontSize = 11.sp,
                        fontWeight = FontWeight(if (d == today) 800 else 600), color = if (d == today) p.ink else p.muted,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 3. Energy

@Composable
fun EnergyCardV2(vm: AppViewModel, range: ProgressRange) {
    val p = palette
    val today = Dates.today()
    val target = vm.profile.calorieTarget.toDouble()
    // Slots: this week's days (Week), the last 30 days (Month) or the last 13 weeks (3 months, weekly averages).
    data class Slot(val label: String, val kcal: Double?, val future: Boolean = false)
    val slots: List<Slot> = remember(vm.meals, range, today) {
        when (range) {
            ProgressRange.WEEK -> {
                val ws = Dates.weekStart(today)
                (0..6).map { i ->
                    val d = Dates.addDays(ws, i.toLong())
                    val k = totalsFor(vm.meals, d).calories
                    Slot(Dates.parse(d).format(NARROW), if (d <= today && k > 0) k else null, d > today)
                }
            }
            ProgressRange.MONTH -> (29 downTo 0).map { back ->
                val d = Dates.addDays(today, -back.toLong())
                val k = totalsFor(vm.meals, d).calories
                Slot(if (back % 7 == 0) Dates.parse(d).format(DAY_NUM) else "", if (k > 0) k else null)
            }
            ProgressRange.QUARTER -> (12 downTo 0).map { back ->
                val ws = Dates.addDays(Dates.weekStart(today), -7L * back)
                val days = (0..6).map { Dates.addDays(ws, it.toLong()) }.filter { it <= today }
                val ks = days.map { totalsFor(vm.meals, it).calories }.filter { it > 0 }
                Slot(if (back % 4 == 0) Dates.parse(ws).format(SHORT_DATE) else "", if (ks.isEmpty()) null else ks.average())
            }
        }
    }
    val logged = slots.mapNotNull { it.kcal }
    val avg = if (logged.isEmpty()) 0.0 else logged.average()
    val onTarget = logged.count { target > 0 && abs(it - target) <= target * 0.05 }
    val unit = if (range == ProgressRange.QUARTER) "weeks" else "days"
    val title = when (range) { ProgressRange.WEEK -> "Energy · this week"; ProgressRange.MONTH -> "Energy · 30 days"; ProgressRange.QUARTER -> "Energy · 3 months" }
    val hide = vm.profile.hideNumbers == true
    val count = rememberMotion("energy-count-${range.name}", 200, PremiumMotion.COUNT_MS)
    val pill = rememberMotion("energy-pill-${range.name}", 600, PremiumMotion.DROP_MS)
    val slotsTotal = slots.count { !it.future }

    Card(padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                CardLabel(title)
                if (logged.isEmpty()) {
                    Text("Nothing logged yet", fontSize = 20.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(top = 2.dp))
                } else if (hide) {
                    // v2.10 "Hide calorie numbers": words, never kcal.
                    Text(Goals.calorieWords(avg, target), fontSize = 18.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(top = 2.dp))
                } else {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                        Text(String.format(Locale.US, "%,d", countUp(avg.roundToInt(), count.value)), fontSize = 30.sp, fontWeight = FontWeight(600), letterSpacing = (-1).sp, color = p.ink, lineHeight = 34.sp)
                        Text(" avg a day", fontSize = 14.sp, fontWeight = FontWeight(500), color = p.muted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            if (logged.isNotEmpty() && target > 0) {
                StatusPill("$onTarget of $slotsTotal on target", if (onTarget * 2 >= logged.size) p.green else p.orange, Modifier.dropIn(pill))
            }
        }
        Spacer(Modifier.height(14.dp))
        EnergyTargetLine(
            slots.map { it.kcal }, target, slots.map { it.label }, "energy-${range.name}",
            Modifier.fillMaxWidth().height(120.dp).semantics {
                contentDescription = "Calories eaten each ${if (range == ProgressRange.QUARTER) "week" else "day"} against a target of ${target.roundToInt()}. $onTarget of $slotsTotal $unit on target."
            },
        )
    }
}

// ---------------------------------------------------------------- 4. Macros

private data class Pick(val name: String, val protein: Int)

/** Protein-rich foods you actually log (most-logged first), topped up with Indian staples. */
private fun quickPicks(vm: AppViewModel, remaining: Double): List<Pick> {
    val mine = vm.meals.asSequence().flatMap { it.items.asSequence() }
        .filter { it.proteinG >= 5 && it.name.isNotBlank() && it.name.length <= 26 }
        .groupBy { it.name.trim().lowercase() }
        .map { (_, items) -> Triple(items.first().name.trim().replaceFirstChar { it.uppercase() }, items.size, items.map { it.proteinG }.average()) }
        .filter { it.third <= remaining + 12 }
        .sortedWith(compareByDescending<Triple<String, Int, Double>> { it.second }.thenByDescending { it.third })
        .map { Pick(it.first, it.third.roundToInt()) }
        .toList()
    val staples = listOf(Pick("Bowl of dal", 9), Pick("Cup of curd", 8), Pick("2 egg whites", 7), Pick("50 g paneer", 9), Pick("Roasted chana", 7), Pick("Scoop of whey", 24))
        .filter { it.protein <= remaining + 12 }
    return (mine + staples).distinctBy { it.name.lowercase() }.take(3)
}

@Composable
fun MacrosCardV2(vm: AppViewModel, range: ProgressRange) {
    val p = palette
    val today = Dates.today()
    val prof = vm.profile
    val days = (0 until range.days).map { Dates.addDays(today, -it.toLong()) }
    val totals = days.map { totalsFor(vm.meals, it) }.filter { it.calories > 0 }
    val n = totals.size.coerceAtLeast(1)
    val avgP = totals.sumOf { it.protein } / n
    val avgC = totals.sumOf { it.carbs } / n
    val avgF = totals.sumOf { it.fat } / n
    data class M(val name: String, val avg: Double, val target: Int, val color: Color)
    val macros = listOf(
        M("Protein", avgP, prof.proteinTargetG, p.blue),
        M("Carbs", avgC, prof.carbTargetG, p.orange),
        M("Fat", avgF, prof.fatTargetG, p.purple),
    )
    val todayProtein = totalsFor(vm.meals, today).protein
    val toGo = (prof.proteinTargetG - todayProtein).coerceAtLeast(0.0)
    val picks = remember(vm.meals, toGo.roundToInt()) { quickPicks(vm, toGo) }

    Card(padding = 20.dp) {
        CardLabel("Macros · daily average")
        Spacer(Modifier.height(14.dp))
        if (totals.isEmpty()) {
            Text("Log a meal to see your macros.", fontSize = 13.sp, color = p.muted)
            return@Card
        }
        Row(Modifier.fillMaxWidth()) {
            macros.forEachIndexed { i, m ->
                val pct = if (m.target > 0) (m.avg / m.target * 100).roundToInt() else 0
                val diff = m.avg - m.target
                val (status, sc) = when {
                    m.target <= 0 -> "no target" to p.muted
                    pct in 95..105 -> "on track" to p.green
                    diff < 0 -> "${abs(diff).roundToInt()} g to go" to p.orange
                    else -> "${diff.roundToInt()} g over" to p.muted
                }
                val sweep = rememberMotion("macro-ring$i-${range.name}", 460 + i * 260, PremiumMotion.DRAW_MS - 500)
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${m.name} $pct percent of target, $status" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                        val track = p.track
                        Canvas(Modifier.size(78.dp)) {
                            val sw = 8.dp.toPx()
                            val r = (size.minDimension - sw) / 2f
                            val tl = Offset(center.x - r, center.y - r)
                            drawCircle(track, r, center, style = Stroke(sw))
                            val f = (pct / 100f).coerceIn(0f, 1f) * PremiumMotion.eased(sweep.value, PremiumMotion.EasePen)
                            if (f > 0.001f) drawArc(m.color, -90f, 360f * f, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Text("${countUp(pct, sweep.value)}%", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink)
                    }
                    Text(m.name, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(status, fontSize = 12.sp, fontWeight = FontWeight(600), color = sc, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
        Spacer(Modifier.height(10.dp))
        if (toGo < 1) {
            Text("Protein target hit today.", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.green)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${toGo.roundToInt()} g", fontSize = 20.sp, fontWeight = FontWeight(800), color = p.blue)
                Text(" protein to go today.${if (picks.isNotEmpty()) " Quick picks:" else ""}", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (picks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    picks.forEachIndexed { i, pk ->
                        val drop = rememberMotion("macro-pick$i", 1440 + i * 140, PremiumMotion.DROP_MS)
                        Column(Modifier.weight(1f).dropIn(drop).background(p.card2, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) {
                            Text(pk.name, fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                            Text("+${pk.protein} g protein", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 5. BMI (Indian ranges)

/** Piecewise scale for the band bar: 15–18.5 / 18.5–23 / 23–25 / 25–35 over fixed shares of the width. */
private fun bmiAt(b: Double): Float {
    val cuts = doubleArrayOf(15.0, 18.5, 23.0, 25.0, 35.0)
    val pos = floatArrayOf(0f, 0.205f, 0.465f, 0.59f, 1f)
    val v = b.coerceIn(cuts.first(), cuts.last())
    for (i in 1 until cuts.size) if (v <= cuts[i]) return pos[i - 1] + (pos[i] - pos[i - 1]) * ((v - cuts[i - 1]) / (cuts[i] - cuts[i - 1])).toFloat()
    return 1f
}

@Composable
fun BmiCardV2(vm: AppViewModel, weightKg: Double?) {
    val prof = vm.profile
    val teen = Goals.isTeen(Goals.ageYears(prof.dob, Goals.todayIso()))
    // Under-18s are judged on WHO growth charts, not adult cut-offs: the v2.10 card handles that.
    if (teen) { BmiCard(vm, weightKg, waist = true); return }
    val p = palette
    val b = Bmi.bmi(weightKg, prof.heightCm)
    Card(padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardLabel("BMI · Indian ranges", Modifier.weight(1f))
            Text("The science", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(end = 6.dp))
            ScienceButton()
        }
        if (b == null || prof.heightCm == null || weightKg == null) {
            Text("Add your height and weight in Personal details to see it.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
            return@Card
        }
        val cat = Bmi.categoryIndia(b)
        val (words, wc) = when (cat) {
            "Normal" -> "Healthy" to p.green
            "Underweight" -> "Below healthy" to p.blue
            "Overweight" -> "A little above healthy" to p.orange
            else -> "Above healthy" to p.red
        }
        val count = rememberMotion("bmi-count", 200, PremiumMotion.COUNT_MS)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(String.format(Locale.US, "%.1f", countUp(b, count.value)), fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 38.sp)
            Spacer(Modifier.width(10.dp))
            Text(words, fontSize = 14.sp, fontWeight = FontWeight(600), color = wc, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        val bands = listOf(Triple("Under", 15.0 to 18.5, p.blue), Triple("Healthy", 18.5 to 23.0, p.green), Triple("Over", 23.0 to 25.0, p.orange), Triple("Obese", 25.0 to 35.0, p.red))
        val grows = bands.indices.map { rememberMotion("bmi-band$it", 300 + it * 150, PremiumMotion.GROW_X_MS) }
        val sweep = rememberMotion("bmi-sweep", 900, PremiumMotion.SWEEP_MS)
        val ink = p.ink
        val muted = p.muted
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val label = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight(600), color = muted)
        val small = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = FontWeight(500), color = muted)
        Canvas(Modifier.fillMaxWidth().height(66.dp).semantics { contentDescription = "BMI ${String.format(Locale.US, "%.1f", b)} on the Indian scale: $cat" }) {
            val gap = 2.dp.toPx()
            val barTop = 18.dp.toPx()
            val barH = 12.dp.toPx()
            bands.forEachIndexed { i, (name, r, c) ->
                val x0 = size.width * bmiAt(r.first) + if (i > 0) gap / 2 else 0f
                val x1 = size.width * bmiAt(r.second) - if (i < bands.lastIndex) gap / 2 else 0f
                val w = (x1 - x0) * PremiumMotion.growX(grows[i].value)
                if (w > 0.5f) drawRoundRect(c, Offset(x0, barTop), Size(w, barH), CornerRadius(barH / 2))
                val lay = measurer.measure(name, label)
                drawText(lay, topLeft = Offset((x0 + x1) / 2 - lay.size.width / 2, barTop + barH + 6.dp.toPx()))
            }
            listOf(18.5, 23.0, 25.0).forEach { v ->
                val lay = measurer.measure(fmt(v), small)
                drawText(lay, topLeft = Offset(size.width * bmiAt(v) - lay.size.width / 2, size.height - lay.size.height))
            }
            // Marker: swings far left → far right → back past the value → settles.
            val mx = size.width * bmiAt(b)
            val t = sweep.value
            val off = PremiumMotion.sweepOffset(t, -mx, (size.width - mx) * 0.95f, -mx * 0.45f)
            val x = (mx + off).coerceIn(0f, size.width)
            val a = PremiumMotion.sweepAlpha(t)
            val tri = Path().apply { moveTo(x - 7.dp.toPx(), 4.dp.toPx()); lineTo(x + 7.dp.toPx(), 4.dp.toPx()); lineTo(x, 14.dp.toPx()); close() }
            drawPath(tri, ink, alpha = a)
            drawRoundRect(ink, Offset(x - 1.5.dp.toPx(), 14.dp.toPx()), Size(3.dp.toPx(), 20.dp.toPx()), CornerRadius(1.5.dp.toPx()), alpha = a)
        }
        val range = Bmi.healthyRange(prof.heightCm)
        val whtr = Bmi.waistToHeight(prof.waistCm, prof.heightCm)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BmiTile("Healthy weight", "${range.min.roundToInt()} – ${range.max.roundToInt()} kg", null, Modifier.weight(1f))
            if (whtr != null) {
                val ok = whtr < 0.5
                BmiTile("Waist ÷ height", String.format(Locale.US, "%.2f", whtr), (if (ok) "under 0.5" else "0.5 or more") to (if (ok) p.green else p.orange), Modifier.weight(1f))
            }
        }
        Text(
            "$cat by Indian/Asian cut-offs · ${Bmi.categoryWho(b)} by WHO global ones. BMI can't tell muscle from fat, so it's one signal, not a verdict.",
            fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp),
        )
        // Kept from v2.10: optional waist entry (feeds the waist ÷ height tile).
        if (prof.hideNumbers != null) WaistRow(vm)
    }
}

@Composable
private fun BmiTile(label: String, value: String, note: Pair<String, Color>?, modifier: Modifier) {
    val p = palette
    Column(modifier.background(p.card2, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            note?.let { (t, c) -> Text(" $t", fontSize = 12.sp, color = c, modifier = Modifier.padding(bottom = 2.dp)) }
        }
    }
}

// ---------------------------------------------------------------- 6. Meal times

/** India-time minute of day for a Postgres timestamptz, or null. */
private fun minuteOfDay(createdAt: String?): Int? {
    if (createdAt.isNullOrBlank()) return null
    val raw = createdAt.trim().replace(" ", "T")
    val fixed = when {
        raw.endsWith("Z", ignoreCase = true) -> raw
        Regex("[+-]\\d\\d:\\d\\d$").containsMatchIn(raw) -> raw
        Regex("[+-]\\d\\d\\d\\d$").containsMatchIn(raw) -> raw.dropLast(2) + ":" + raw.takeLast(2)
        Regex("[+-]\\d\\d$").containsMatchIn(raw) -> "$raw:00"
        else -> raw + "Z"
    }
    return runCatching { OffsetDateTime.parse(fixed).atZoneSameInstant(Dates.ZONE).let { it.hour * 60 + it.minute } }.getOrNull()
}

private fun clock(min: Int): Pair<String, String> {
    val h = (min / 60) % 24
    val m = min % 60
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return String.format(Locale.US, "%d:%02d", h12, m) to (if (h < 12) "am" else "pm")
}

private fun span(mins: Int): String = when {
    mins < 60 -> "$mins min"
    mins % 60 == 0 || mins >= 180 -> "${(mins + 30) / 60} h"
    else -> "${mins / 60} h ${mins % 60} min"
}

@Composable
fun MealTimesCard(vm: AppViewModel) {
    val p = palette
    val today = Dates.today()
    val now = ZonedDateTime.now(Dates.ZONE).let { it.hour * 60 + it.minute }
    val order = listOf(MealTypes.BREAKFAST to p.orange, MealTypes.LUNCH to p.green, MealTypes.SNACK to p.purple, MealTypes.DINNER to p.blue)
    data class Row4(val key: String, val color: Color, val usual: Int?, val todayAt: Int?)
    val rows = remember(vm.meals, today) {
        val from = Dates.addDays(today, -30)
        val recent = vm.meals.filter { it.date in from..today }
        order.map { (key, c) ->
            val mine = recent.filter { MealTypes.of(it) == key }
            val firstPerDay = mine.filter { it.date != today }.groupBy { it.date }.mapNotNull { (_, ms) -> ms.mapNotNull { minuteOfDay(it.createdAt) }.minOrNull() }.sorted()
            val usual = if (firstPerDay.isEmpty()) null else firstPerDay[firstPerDay.size / 2]
            val todayAt = mine.filter { it.date == today }.mapNotNull { minuteOfDay(it.createdAt) }.minOrNull()
            Row4(key, c, usual, todayAt)
        }
    }
    val nextKey = rows.filter { it.todayAt == null && it.usual != null && it.usual > now }.minByOrNull { it.usual!! }?.key

    Card(padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            CardLabel("Your usual meal times", Modifier.weight(1f))
            Text("and today", fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.height(14.dp))
        rows.chunked(2).forEachIndexed { ri, pair ->
            if (ri > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { ci, r ->
                    val i = ri * 2 + ci
                    val drop = rememberMotion("mealtime$i", 350 + i * 150, PremiumMotion.DROP_MS)
                    val (status, sc) = when {
                        r.todayAt != null && r.usual != null -> {
                            val d = r.todayAt - r.usual
                            val (t, ap) = clock(r.todayAt)
                            "Today $t $ap · " to when {
                                abs(d) <= 5 -> "on time" to p.green
                                d < 0 -> "${span(-d)} early" to p.green
                                else -> "${span(d)} late" to p.orange
                            }
                        }
                        r.todayAt != null -> clock(r.todayAt).let { (t, ap) -> "Today $t $ap" to ("" to p.muted) }
                        r.usual == null -> "Not enough logs yet" to ("" to p.muted)
                        r.key == nextKey -> "Next · " to ("in about ${span(r.usual - now)}" to p.blue)
                        now > r.usual + 90 -> "Today — · " to ("skipped" to p.muted)
                        now >= r.usual -> "Today · " to ("due now" to p.orange)
                        else -> "Later today" to ("" to p.muted)
                    }
                    Column(
                        Modifier.weight(1f).dropIn(drop).background(p.card2, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(r.color, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(MealTypes.label(r.key), fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (r.usual != null) {
                                val (t, ap) = clock(r.usual)
                                Text(t, fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 30.sp)
                                Text(" $ap", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(bottom = 3.dp))
                            } else {
                                Text("—", fontSize = 28.sp, fontWeight = FontWeight(800), color = p.muted, lineHeight = 30.sp)
                            }
                        }
                        Text(
                            buildAnnotatedString {
                                append(status)
                                if (sc.first.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight(700), color = sc.second)) { append(sc.first) }
                            },
                            fontSize = 12.sp, color = p.muted, maxLines = 2, lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
