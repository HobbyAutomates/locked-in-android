package com.sohum.bandlog.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.TargetEdits
import com.sohum.bandlog.ui.components.SafetyNote
import com.sohum.bandlog.ui.components.ScienceButton
import com.sohum.bandlog.ui.components.TeenGoalMigration
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// v2.3 goal-ring colours (fixed, both themes).
private val ProteinColor = Color(0xFFE9636B)
private val CarbsColor = Color(0xFFE5A15B)
private val FatColor = Color(0xFF5B8DEF)

/** Protein / carbs / fat as whole percentages of the calorie target, always summing to 100. */
private data class Split(val protein: Int, val carbs: Int, val fat: Int) {
    fun grams(calories: Int) = Triple(
        (calories * protein / 100.0 / 4.0).roundToInt(),
        (calories * carbs / 100.0 / 4.0).roundToInt(),
        (calories * fat / 100.0 / 9.0).roundToInt(),
    )

    /**
     * Moves [which] (0 protein, 1 carbs, 2 fat) by [delta] and rebalances so the sum stays 100:
     * carbs give or take for protein / fat, fat gives or takes when carbs is the one edited.
     * If the partner runs out, the third macro absorbs the rest.
     */
    fun step(which: Int, delta: Int): Split {
        val v = intArrayOf(protein, carbs, fat)
        val target = (v[which] + delta).coerceIn(0, 100)
        var diff = target - v[which]
        if (diff == 0) return this
        v[which] = target
        val order = if (which == 1) listOf(2, 0) else listOf(1, if (which == 0) 2 else 0)
        for (k in order) {
            if (diff == 0) break
            val take = if (diff > 0) minOf(diff, v[k]) else maxOf(diff, v[k] - 100)
            v[k] -= take; diff -= take
        }
        v[which] -= diff // anything nobody could absorb
        return Split(v[0], v[1], v[2])
    }

    companion object {
        /** Derived from gram targets; rounding leftovers land on carbs so the sum is exactly 100. */
        fun from(calories: Int, proteinG: Int, carbsG: Int, fatG: Int): Split {
            val kcal = (proteinG * 4 + carbsG * 4 + fatG * 9).toDouble().takeIf { it > 0 } ?: calories.toDouble().coerceAtLeast(1.0)
            val p = (proteinG * 4 / kcal * 100).roundToInt().coerceIn(0, 100)
            val f = (fatG * 9 / kcal * 100).roundToInt().coerceIn(0, 100 - p)
            return Split(p, 100 - p - f, f)
        }
    }
}

/**
 * v2.2 nutrition goals, one Cal AI-style screen: the calorie target, a macro split editor (three
 * percentages with −/+ 5 steppers that always sum to 100; grams follow the calories), and
 * "Auto generate", which recomputes everything from Personal details in place and shows a
 * before → after preview until Save.
 */
@Composable
fun NutritionGoalsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPersonal: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val prof = vm.profile

    var calories by remember(prof) { mutableStateOf(prof.calorieTarget.toString()) }
    var split by remember(prof) { mutableStateOf(Split.from(prof.calorieTarget, prof.proteinTargetG, prof.carbTargetG, prof.fatTargetG)) }
    /** Exact generator output while untouched, so Save persists precisely what the preview shows. */
    var generated by remember(prof) { mutableStateOf<Goals.Targets?>(null) }
    var touched by remember(prof) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMicros by remember { mutableStateOf(false) }
    var fiber by remember(prof) { mutableStateOf(prof.fiberTarget.toString()) }
    var sugar by remember(prof) { mutableStateOf(prof.sugarTarget.toString()) }
    var safety by remember { mutableStateOf<Pair<List<String>, Int?>?>(null) }
    var planNote by remember(prof) { mutableStateOf<Goals.Plan?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val working = Goals.plan(prof)
    androidx.compose.runtime.LaunchedEffect(Unit) { if (vm.weights.isEmpty()) vm.loadWeights() }

    // v2.10: anything under the safe floor is lifted to it on save (never blocked).
    val cal = calories.toIntOrNull()?.coerceIn(1, 10_000) ?: prof.calorieTarget
    val (pg, cg, fg) = generated?.let { Triple(it.protein, it.carbs, it.fat) } ?: split.grams(cal)

    fun edited(): Profile {
        val g = generated
        val base = if (g != null) prof.copy(calorieTarget = g.calories, proteinTargetG = g.protein, carbTargetGSet = g.carbs, fatTargetGSet = g.fat)
        else prof.copy(calorieTarget = cal, proteinTargetG = pg.coerceIn(10, 500), carbTargetGSet = cg.coerceIn(0, 1000), fatTargetGSet = fg.coerceIn(0, 500))
        return base.copy(
            fiberTarget = fiber.toIntOrNull()?.coerceIn(1, 200) ?: prof.fiberTarget,
            sugarTarget = sugar.toIntOrNull()?.coerceIn(1, 500) ?: prof.sugarTarget,
        )
    }
    val dirty = touched && edited() != prof

    fun save() {
        scope.launch {
            busy = true; saved = false
            val want = edited()
            // Safety: only when the calorie number itself changed, so editing fibre alone never moves an old target.
            val changed = want.calorieTarget != prof.calorieTarget
            val (capped, wasCapped) = if (changed) Goals.capToFloor(want.calorieTarget, Goals.floorFor(prof)) else want.calorieTarget to false
            val out = if (!wasCapped) want else split.grams(capped).let { (gp, gc, gf) -> want.copy(calorieTarget = capped, proteinTargetG = gp.coerceIn(10, 500), carbTargetGSet = gc.coerceIn(0, 1000), fatTargetGSet = gf.coerceIn(0, 500)) }
            saved = vm.saveProfile(out)
            if (saved) {
                val edits = TargetEdits.record(ctx, "calories", prof.calorieTarget.toDouble(), want.calorieTarget.toDouble())
                val flags = Goals.edFlags(Goals.screenInput(prof, weights = vm.weights.map { Goals.WeighIn(it.date, it.weightKg) }, requestedCalories = if (changed) want.calorieTarget else null, edits = edits))
                safety = if (flags.isNotEmpty()) flags to (if (wasCapped) capped else null) else null
                generated = null; touched = false
            }
            busy = false
        }
    }

    SubPage("Nutrition goals", onBack) {
        TeenGoalMigration(vm)
        // ---- calories ----
        Rise(0) {
            Card(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ring(1f, p.ink, 28.dp, 4.dp) { androidx.compose.material3.Icon(com.sohum.bandlog.ui.components.FlameIcon, null, tint = p.ink, modifier = Modifier.size(13.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Calorie goal", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                        Text("per day", fontSize = 12.sp, color = p.muted)
                    }
                    NumberField(if (generated != null) generated!!.calories.toString() else calories, {
                        calories = it.filter(Char::isDigit).take(5); generated = null; touched = true; saved = false
                    }, "kcal")
                }
            }
        }

        // ---- macro split ----
        Rise(1) {
            Card(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Macro split", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                        Text("always 100%", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                    // The split as one bar.
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(10.dp).background(p.track, CircleShape)) {
                        listOf(split.protein to ProteinColor, split.carbs to CarbsColor, split.fat to FatColor).forEach { (pct, c) ->
                            if (pct > 0) Box(Modifier.weight(pct.toFloat()).height(10.dp).background(c, CircleShape))
                        }
                    }
                    val edit: (Int, Int) -> Unit = { which, d -> split = split.step(which, d); generated = null; touched = true; saved = false }
                    MacroRow("Protein", ProteinColor, split.protein, pg) { edit(0, it) }
                    Hair()
                    MacroRow("Carbs", CarbsColor, split.carbs, cg) { edit(1, it) }
                    Hair()
                    MacroRow("Fat", FatColor, split.fat, fg) { edit(2, it) }
                    // v2.10 AMDR: soft notes, never blocks.
                    val notes = Goals.amdrNotes(Goals.Targets(cal, pg, cg, fg))
                    if (notes.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            notes.forEach { n -> Text(n.text, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (n.level == "warn") FontWeight(600) else FontWeight(400), color = if (n.level == "warn") p.orange else p.muted) }
                        }
                    }
                    Hair()
                    // v2.3: fibre + sugar goals behind an expander.
                    Row(
                        Modifier.fillMaxWidth().clickable { showMicros = !showMicros }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("View micronutrients", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Icon(
                            com.sohum.bandlog.ui.components.ChevronDownIcon, null, tint = p.muted,
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (showMicros) 180f else 0f },
                        )
                    }
                    if (showMicros) {
                        MicroRow("Fiber", "at least", fiber) { fiber = it; touched = true; saved = false }
                        Hair()
                        MicroRow("Sugar", "at most", sugar) { sugar = it; touched = true; saved = false }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // ---- auto generate ----
        Rise(2) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto generate", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.width(6.dp))
                    ScienceButton()
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        working == null -> "From your weight, height, age and gender."
                        working.teen -> "Built for a growing body: ${if (working.goal == "gain") "what you need to grow and train, plus a small extra" else "what you need to grow and train"}. No deficits under 18."
                        else -> "From your weight, height, age and gender: ${working.pal.label.lowercase()}, " +
                            (if (working.goal == "maintain") "maintaining." else "${if (working.goal == "lose") "losing" else "gaining"} ${com.sohum.bandlog.ui.today.fmt(working.speed)} kg/week.")
                    },
                    fontSize = 12.sp, color = p.muted, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                PillButton("Auto generate", height = 46.dp, onClick = {
                    missing = Goals.missing(prof)
                    val pl = Goals.plan(prof) ?: return@PillButton
                    val t = pl.targets
                    planNote = pl
                    generated = t
                    calories = t.calories.toString()
                    split = Split.from(t.calories, t.protein, t.carbs, t.fat)
                    touched = true; saved = false
                })
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Add your ${missing.joinToString(", ")} in Personal details first.", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.orange)
                    Spacer(Modifier.height(8.dp))
                    PillButton("Open Personal details", onOpenPersonal, height = 42.dp, bg = p.card2, fg = p.ink)
                }
                val g = generated
                if (g != null) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("New goals — tap Save to keep them", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted)
                        BeforeAfter("Calories", p.ink, prof.calorieTarget, g.calories, "kcal")
                        BeforeAfter("Protein", ProteinColor, prof.proteinTargetG, g.protein, "g")
                        BeforeAfter("Carbs", CarbsColor, prof.carbTargetG, g.carbs, "g")
                        BeforeAfter("Fat", FatColor, prof.fatTargetG, g.fat, "g")
                    }
                    val pl = planNote
                    if (pl != null && pl.speedCapped) Text("We used ${com.sohum.bandlog.ui.today.fmt(pl.speed)} kg/week, the safe max for your body weight, instead of ${com.sohum.bandlog.ui.today.fmt(prof.goalSpeedKgWk)}.", fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                    if (pl != null && pl.floorApplied) Text("Calories sit at your floor (${pl.targets.calories} kcal), the lowest we'll go for your body.", fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Rise(3) { ErrorNote(vm.error) }
        Rise(3) {
            PillButton(
                if (busy) "Saving…" else if (saved && !dirty) "Saved" else "Save goals",
                enabled = !busy && dirty,
                onClick = { save() },
            )
        }
        safety?.let { (flags, floor) -> Rise(4) { SafetyNote(vm, flags, floor, onClose = { safety = null }) } }
        Spacer(Modifier.height(4.dp))
    }
}

/** One macro: coloured dot, name, grams, and a −  NN%  + stepper (±5). */
@Composable
private fun MacroRow(label: String, color: Color, pct: Int, grams: Int, onStep: (Int) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Ring(1f, color, 28.dp, 4.dp) { Box(Modifier.size(7.dp).background(color, CircleShape)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
            Text("$grams g", fontSize = 12.sp, color = p.muted)
        }
        Row(Modifier.background(p.card2, CircleShape).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = pct > 0) { onStep(-5) }
            Text("$pct%", Modifier.width(52.dp), textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink)
            StepButton("+", enabled = pct < 100) { onStep(5) }
        }
    }
}

/** A micronutrient goal: grey ring, name, and a grams field. */
@Composable
private fun MicroRow(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ring(1f, p.muted, 28.dp, 4.dp) { Box(Modifier.size(7.dp).background(p.muted, CircleShape)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
            Text("$hint, per day", fontSize = 12.sp, color = p.muted)
        }
        NumberField(value, { onChange(it.filter(Char::isDigit).take(3)) }, "g")
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(34.dp).pressable().background(if (enabled) p.card else Color.Transparent, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight(700), color = if (enabled) p.ink else p.muted) }
}

/** "Calories   2200 → 2450 kcal" with the change in the macro's colour. */
@Composable
private fun BeforeAfter(label: String, color: Color, before: Int, after: Int, unit: String) {
    val p = palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
        Text("$before", fontSize = 13.sp, color = p.muted)
        Text("  →  ", fontSize = 13.sp, color = p.muted)
        Text("$after $unit", fontSize = 13.sp, fontWeight = FontWeight(800), color = if (after == before) p.ink else color)
    }
}
