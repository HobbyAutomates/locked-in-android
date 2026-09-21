package com.sohum.bandlog.ui.progress

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Overline
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.SectionGap
import com.sohum.bandlog.ui.components.Stat
import com.sohum.bandlog.ui.components.Tag
import com.sohum.bandlog.ui.theme.Ok
import com.sohum.bandlog.ui.theme.Warn
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Muscles
import com.sohum.bandlog.util.Streaks

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val today = Dates.today()
    val last14 = (13 downTo 0).map { Dates.addDays(today, -it.toLong()) }
    val proteinByDay = last14.map { d -> totalsFor(vm.meals, d).protein }
    val weeks = (7 downTo 0).map { Dates.addDays(Dates.weekStart(today), -7L * it) }
    val perWeek = vm.workoutDates.groupingBy { Dates.weekStart(it) }.eachCount()
    val rest = Streaks.restByMuscle(vm.workouts)
    val avgProtein7 = last14.takeLast(7).map { totalsFor(vm.meals, it).protein }.let { l -> if (l.any { it > 0 }) l.filter { it > 0 }.average() else 0.0 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp)) {
        item {
            Overline("Progress", color = cs.primary)
            Text("Last few weeks", fontSize = 26.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp)
            SectionGap()
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${vm.weekStreak}", "week streak", accent = if (vm.weekStreak > 0) Warn else null)
                    Stat("${vm.dayStreak}", "day streak")
                    Stat("${vm.workouts.size}", "sessions · 120d")
                    Stat("${avgProtein7.toInt()} g", "avg protein · 7d", accent = if (avgProtein7 >= vm.profile.proteinTargetG) Ok else null)
                }
            }
            SectionGap()
        }
        item {
            Card {
                Overline("Sessions per week")
                Spacer(Modifier.height(10.dp))
                Bars(weeks.map { (perWeek[it] ?: 0).toDouble() }, vm.profile.weeklyWorkoutTarget.toDouble(), weeks.map { Dates.parse(it).dayOfMonth.toString() }, cs.primary)
            }
            SectionGap()
        }
        item {
            Card {
                Overline("Protein · last 14 days")
                Spacer(Modifier.height(10.dp))
                Bars(proteinByDay, vm.profile.proteinTargetG.toDouble(), last14.map { Dates.parse(it).dayOfMonth.toString() }, Ok)
            }
            SectionGap()
        }
        item {
            Card {
                Overline("Rest since last trained")
                Spacer(Modifier.height(8.dp))
                rest.forEach { r ->
                    RowSpaceBetween {
                        Tag(r.muscle, Muscles.color(r.muscle))
                        Text(
                            when { r.last == null -> "never"; r.days == 0L -> "today"; r.days == 1L -> "1 day"; else -> "${r.days} days" },
                            fontSize = 13.sp, color = if (r.last != null && r.days >= 7) Warn else cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun Bars(values: List<Double>, target: Double, labels: List<String>, color: androidx.compose.ui.graphics.Color) {
    val cs = MaterialTheme.colorScheme
    val max = maxOf(values.maxOrNull() ?: 0.0, target, 1.0)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val f = (v / max).toFloat().coerceIn(0.03f, 1f)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(f * 0.85f)
                        .background(if (v >= target && target > 0) color else color.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(labels[i], fontSize = 9.sp, color = cs.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("target ${target.toInt()}", fontSize = 11.sp, color = cs.onSurfaceVariant)
    Spacer(Modifier.width(0.dp))
}
