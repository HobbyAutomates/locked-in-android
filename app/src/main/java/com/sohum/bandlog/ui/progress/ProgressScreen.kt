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
fun ProgressScreen(vm: AppViewModel) {
    val p = palette
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
        item { Rise(2) { Segmented(listOf("This week", "Last week", "2 wks ago", "3 wks ago"), weekBack, { weekBack = it }, height = 34.dp) } }
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
