package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.growFromLeft
import com.sohum.bandlog.ui.motion.rememberMotion
import com.sohum.bandlog.ui.motion.PremiumMotion
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.MuscleMap
import com.sohum.bandlog.util.Pro
import kotlin.math.roundToInt

/** Workouts in the Mon–Sun week [weeksAgo] weeks back (0 = this week, up to today). */
internal fun weekWorkouts(all: List<Workout>, weeksAgo: Int): Pair<String, List<Workout>> {
    val ws = Dates.addDays(Dates.weekStart(Dates.today()), -7L * weeksAgo)
    val we = Dates.addDays(ws, 6)
    return ws to all.filter { it.date >= ws && it.date <= we }
}

@Composable
internal fun heatFills(sets: Map<MuscleMap.Region, Double>): Map<MuscleMap.Region, Color> {
    val a = accentColor
    return sets.filterValues { it > 0 }.mapValues { (_, v) -> a.copy(alpha = MuscleMap.heat(v)) }
}

/** The compact "muscles trained this week" card for Progress. */
@Composable
fun MusclesWeekCard(vm: AppViewModel) {
    val p = palette
    val pvm: PlatformViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val sets = MuscleMap.setsPerRegion(weekWorkouts(vm.workouts, 0).second)
    Card(onClick = { PlatformNav.open(PlatformPage.MUSCLE_MAP) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleWithChip("Muscles this week", pro = true)
            Spacer(Modifier.weight(1f))
            Text("${sets.count { it.value >= MuscleMap.WEEKLY_MIN }} in range", fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.height(10.dp))
        if (!pvm.hasPro) Text("See which muscles you trained, set by set. Part of Pro.", fontSize = 13.sp, color = p.muted)
        else if (sets.isEmpty()) Text("Log a gym or bodyweight session and your muscles light up here.", fontSize = 13.sp, color = p.muted)
        else MuscleFigures(heatFills(sets), Modifier.height(170.dp), description = "Muscles trained this week")
    }
}

/**
 * v2.13 muscle map (spec §12, Pro): this week's (or last week's) sets per muscle on a front/back
 * figure, with the evidence-based 10–20 sets per muscle per week guideline.
 */
@Composable
fun MuscleMapScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    var week by remember { mutableIntStateOf(0) }
    SubPage("Muscle map", onBack) {
        ProGate(pvm, Pro.Feature.MUSCLE_MAP) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Segmented(listOf("This week", "Last week"), week, { week = it })
                val (ws, list) = weekWorkouts(vm.workouts, week)
                val sets = MuscleMap.setsPerRegion(list)
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TitleWithChip("Sets per muscle", pro = true)
                        Spacer(Modifier.weight(1f))
                        Text("from ${Dates.short(ws)}", fontSize = 12.sp, color = p.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    MuscleFigures(heatFills(sets), Modifier.height(300.dp), description = "Heat map of sets per muscle")
                    Spacer(Modifier.height(10.dp))
                    Legend()
                }
                Card {
                    Text("Guideline: 10–20 hard sets per muscle a week", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(
                        "Most growth research lands here (Schoenfeld 2017 meta-analysis). A secondary muscle counts as half a set; a band session counts 3 sets per muscle.",
                        fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    MuscleMap.Region.entries.sortedByDescending { sets[it] ?: 0.0 }.forEachIndexed { i, r ->
                        val v = sets[r] ?: 0.0
                        RegionRow(r.label, v, i)
                    }
                }
                if (list.isEmpty()) Text("No sessions logged in this week yet.", fontSize = 13.sp, color = p.muted)
            }
        }
    }
}

@Composable
private fun Legend() {
    val p = palette
    val a = accentColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("0", fontSize = 11.sp, color = p.muted)
        listOf(2.0, 6.0, 10.0, 15.0, 20.0).forEach { v -> Box(Modifier.size(18.dp, 8.dp).background(a.copy(alpha = MuscleMap.heat(v)), RoundedCornerShape(3.dp))) }
        Text("20+ sets", fontSize = 11.sp, color = p.muted)
    }
}

@Composable
private fun RegionRow(label: String, sets: Double, i: Int) {
    val p = palette
    val a = accentColor
    val grow = rememberMotion("region-$label", 200 + i * 40, PremiumMotion.GROW_X_MS)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = p.ink, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f).height(8.dp).background(p.track, CircleShape)) {
            val f = (sets / MuscleMap.WEEKLY_MAX).toFloat().coerceIn(0f, 1f)
            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(8.dp).growFromLeft(grow).background(a.copy(alpha = MuscleMap.heat(sets).coerceAtLeast(0.35f)), CircleShape))
            // The 10-set line.
            Box(Modifier.fillMaxWidth(MuscleMap.WEEKLY_MIN / MuscleMap.WEEKLY_MAX.toFloat()).height(8.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(1.5.dp).height(8.dp).background(p.muted))
            }
        }
        Text(
            if (sets <= 0) "—" else "${(sets * 2).roundToInt() / 2.0}".removeSuffix(".0"),
            fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.width(40.dp).padding(start = 8.dp),
        )
        Text(MuscleMap.verdict(sets), fontSize = 11.sp, color = if (sets >= MuscleMap.WEEKLY_MIN && sets <= MuscleMap.WEEKLY_MAX) p.green else p.muted, modifier = Modifier.width(70.dp))
    }
}
