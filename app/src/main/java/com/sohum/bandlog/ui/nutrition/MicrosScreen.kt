package com.sohum.bandlog.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.Micros
import kotlin.math.roundToInt

/**
 * §9 micronutrient dashboard: today and the 7-day average against ICMR-NIN / WHO targets, and
 * "low this week" hints with Indian foods (filtered by the diet mode). Built from the micros
 * already on meal_items (util/Micros, the web's micros.ts).
 */
@Composable
fun MicrosScreen(vm: AppViewModel, nvm: NutritionViewModel, onBack: () -> Unit) {
    val p = palette
    val prof = vm.profile
    val today = vm.today
    val mode = nvm.dietMode(prof)
    val items = remember(vm.meals) {
        vm.meals.filter { com.sohum.bandlog.util.Dates.daysBetween(it.date, today) in 0..6 }
            .flatMap { m -> m.items.map { Micros.Item(m.date, it.micros) } }
    }
    val targets = remember(prof) { Micros.targets(Goals.ageYears(prof.dob), prof.gender, prof.calorieTarget, prof.fiberTarget, prof.sugarTarget) }
    val day = remember(items) { Micros.day(items, today) }
    val week = remember(items) { Micros.weekAverage(items, today) }
    val hints = remember(targets, week, mode) { Micros.weekHints(targets, week.values, week.loggedDays, mode) }
    MotionScreen {
        SubPage("Micronutrients", onBack) {
            Entrance(0, key = "intro") {
                Card {
                    Text("Today vs your 7-day average", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(
                        "Targets from ICMR-NIN 2020 for your age and sex; sugar under 10% of calories and sodium under 2,000 mg (WHO). " +
                            (if (week.loggedDays > 0 && week.coverage < 0.6) "About ${(week.coverage * 100).roundToInt()}% of what you logged carries these numbers, so treat this as a rough guide." else "One signal, not a verdict."),
                        fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(p.ink, "Today"); Legend(p.muted.copy(alpha = 0.5f), "7-day avg"); Legend(p.ink.copy(alpha = 0.55f), "Target", bar = true)
                    }
                }
            }
            if (hints.isNotEmpty()) Entrance(1, key = "hints") {
                Card {
                    Text("This week", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    hints.forEachIndexed { i, h ->
                        if (i > 0) Hair()
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(h.label, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                                if (h.kind == "low") Tag("Low this week", p.orange, p.orangeBg) else Tag("High this week", p.red, p.redBg)
                            }
                            Text(h.text, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
                            if (h.foods.isNotEmpty()) Text("Try: " + h.foods.joinToString(", "), fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            Entrance(2, key = "rows") {
                Card(padding = 0.dp) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        targets.forEachIndexed { i, t ->
                            if (i > 0) Hair()
                            MicroRow(t, day.values[t.key] ?: 0.0, week.values[t.key] ?: 0.0)
                        }
                    }
                }
            }
            if (day.items == 0 && week.loggedDays == 0) Text(
                "Nothing logged this week yet. Foods from the table and plate scans carry these numbers.",
                fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String, bar: Boolean = false) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (bar) 2.dp else 10.dp, 10.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = p.muted)
    }
}

@Composable
private fun MicroRow(t: Micros.Target, today: Double, weekAvg: Double) {
    val p = palette
    val max = t.kind == "max"
    // Bars run to 150 % of the target so going over is visible; the marker sits at the target.
    val scale = 1.5
    val color = when {
        max && today > t.target -> p.red
        max -> p.green
        today >= t.target -> p.green
        else -> p.ink
    }
    val weekPct = if (t.target > 0) weekAvg / t.target else 0.0
    Column(
        Modifier.padding(vertical = 12.dp).semantics {
            contentDescription = "${t.label}: today ${Micros.fmt(today, t.unit)}, week average ${Micros.fmt(weekAvg, t.unit)}, ${if (max) "limit" else "target"} ${Micros.fmt(t.target, t.unit)}"
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(t.label, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
            Text("${Micros.fmt(today, t.unit)} ", fontSize = 14.sp, fontWeight = FontWeight(800), color = p.ink)
            Text("/ ${if (max) "under " else ""}${Micros.fmt(t.target, t.unit)}", fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.height(6.dp))
        GrowBar((today / (t.target * scale)).toFloat(), color, "t-${t.key}", marker = (1 / scale).toFloat())
        Spacer(Modifier.height(4.dp))
        GrowBar((weekAvg / (t.target * scale)).toFloat(), p.muted.copy(alpha = 0.5f), "w-${t.key}", height = 5, marker = (1 / scale).toFloat())
        Text(
            "7-day avg ${Micros.fmt(weekAvg, t.unit)} · ${(weekPct * 100).roundToInt()}% of ${if (max) "the limit" else "target"} · ${t.source}",
            fontSize = 11.sp, color = p.muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp),
        )
    }
}
