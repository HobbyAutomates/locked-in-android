package com.sohum.bandlog.ui.nutrition

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
 * "low this week" hints with Indian foods. Built from the micros already on meal_items.
 */
@Composable
fun MicrosScreen(vm: AppViewModel, nvm: NutritionViewModel, onBack: () -> Unit) {
    val p = palette
    val prof = vm.profile
    val today = vm.today
    val rows = remember(vm.meals, prof) {
        val entries = vm.meals.filter { com.sohum.bandlog.util.Dates.daysBetween(it.date, today) in 0..6 }
            .flatMap { m -> m.items.map { Micros.Entry(m.date, it.micros, it.calories) } }
        Micros.rows(entries, today, Goals.ageYears(prof.dob), prof.gender, prof.calorieTarget, prof.fiberTarget)
    }
    val coverage = rows.maxOfOrNull { it.coverage } ?: 0.0
    MotionScreen {
        SubPage("Micronutrients", onBack) {
            Entrance(0, key = "intro") {
                Card {
                    Text("Today vs your 7-day average", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(
                        "Targets from ICMR-NIN 2020 for your age and sex; sugar under 10% of calories and sodium under 2,000 mg (WHO). " +
                            if (coverage < 0.6) "Only some of what you logged carries these numbers, so treat this as a rough guide." else "One signal, not a verdict.",
                        fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(p.ink, "Today"); Legend(p.muted.copy(alpha = 0.5f), "7-day avg"); Legend(p.ink.copy(alpha = 0.55f), "Target", bar = true)
                    }
                }
            }
            val flagged = rows.filter { it.flagged }
            if (flagged.isNotEmpty()) Entrance(1, key = "hints") {
                Card {
                    Text("This week", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    flagged.forEachIndexed { i, r ->
                        if (i > 0) Hair()
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.nutrient.label, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                                if (r.nutrient.kind == "min") Tag("Low this week", p.orange, p.orangeBg) else Tag("High this week", p.red, p.redBg)
                            }
                            Text(Micros.hint(r.nutrient.key), fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            Entrance(2, key = "rows") {
                Card(padding = 0.dp) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        rows.forEachIndexed { i, r ->
                            if (i > 0) Hair()
                            MicroRow(r)
                        }
                    }
                }
            }
            if (rows.all { it.coverage == 0.0 }) Text(
                "Nothing logged this week carries micronutrient numbers yet. Foods from the table and plate scans usually do.",
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
private fun MicroRow(r: Micros.Row) {
    val p = palette
    val max = r.nutrient.kind == "max"
    // Bars run to 150 % of the target so going over is visible; the marker sits at the target.
    val scale = 1.5
    val over = if (max) r.today > r.target else false
    val color = when {
        max && over -> p.red
        max -> p.green
        r.todayPct >= 1 -> p.green
        else -> p.ink
    }
    Column(
        Modifier.padding(vertical = 12.dp).semantics {
            contentDescription = "${r.nutrient.label}: today ${Micros.fmt(r.today, r.nutrient.unit)}, week average ${Micros.fmt(r.weekAvg, r.nutrient.unit)}, ${if (max) "limit" else "target"} ${Micros.fmt(r.target, r.nutrient.unit)}"
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(r.nutrient.label, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
            Text("${Micros.fmt(r.today, r.nutrient.unit)} ", fontSize = 14.sp, fontWeight = FontWeight(800), color = p.ink)
            Text("/ ${if (max) "under " else ""}${Micros.fmt(r.target, r.nutrient.unit)}", fontSize = 12.sp, color = p.muted)
        }
        Spacer(Modifier.height(6.dp))
        GrowBar((r.today / (r.target * scale)).toFloat(), color, "t-${r.nutrient.key}", marker = (1 / scale).toFloat())
        Spacer(Modifier.height(4.dp))
        GrowBar((r.weekAvg / (r.target * scale)).toFloat(), p.muted.copy(alpha = 0.5f), "w-${r.nutrient.key}", height = 5, marker = (1 / scale).toFloat())
        Text(
            "7-day avg ${Micros.fmt(r.weekAvg, r.nutrient.unit)} · ${(r.weekPct * 100).roundToInt()}% of ${if (max) "the limit" else "target"}",
            fontSize = 11.sp, color = p.muted, modifier = Modifier.padding(top = 3.dp),
        )
    }
}
