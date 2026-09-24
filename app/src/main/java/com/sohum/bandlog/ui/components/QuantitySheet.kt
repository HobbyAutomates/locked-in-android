package com.sohum.bandlog.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.QUnit
import com.sohum.bandlog.util.Quantity
import com.sohum.bandlog.util.QuantityFood
import kotlin.math.roundToInt

/**
 * The shared Quantity sheet (on the one [BottomSheet] chassis): g · ml · kg · serving, a number, a 0.5-step servings stepper when a
 * serving size is known, quick chips, and a live kcal / P / C / F preview. Used by Presets
 * ("Custom…"), Search results, the scan report's "Log 1 serving" and the review rows.
 */
@Composable
fun QuantitySheet(
    food: QuantityFood,
    initial: Quantity? = null,
    title: String = "How much?",
    cta: String = "Add",
    onDone: (MealItem, Quantity) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    val focus = LocalFocusManager.current
    val sg = food.servingGrams
    val start = initial ?: if (sg != null) Quantity(QUnit.SERVING, 1.0) else Quantity(QUnit.G, 100.0)
    var unit by remember(food) { mutableStateOf(start.unit) }
    var text by remember(food) { mutableStateOf(fmt(start.value)) }
    val value = text.toDoubleOrNull() ?: 0.0
    val q = Quantity(unit, value)
    val grams = food.grams(q)
    // v2.0 "Restaurant portion": x1.4 the amount and, for dal / sabzi / protein dishes, a hidden tsp of oil.
    val allowRestaurant = com.sohum.bandlog.util.Restaurant.allowed(food)
    var restaurant by remember(food) { mutableStateOf(false) }
    val oily = com.sohum.bandlog.util.Restaurant.oily(food.name, food.category)
    val item = food.item(q).let { if (restaurant) com.sohum.bandlog.util.Restaurant.apply(it, oily) else it }
    val servingLabel = food.serving?.label

    fun pick(next: Quantity) { unit = next.unit; text = fmt(next.value) }
    fun step(d: Double) {
        val cur = if (unit == QUnit.SERVING) value else if (sg != null) grams / sg else 1.0
        pick(Quantity(QUnit.SERVING, ((cur + d) * 2).roundToInt() / 2.0).let { if (it.value < 0.5) it.copy(value = 0.5) else it })
    }

    BottomSheet(
        title = food.name + (food.nameHi?.let { "  $it" } ?: ""),
        subtitle = title + (if (sg != null) " · ${servingLabel?.let { if (it.first().isDigit()) it else "1 $it" }} = ${fmt(sg)} g" else ""),
        onDismiss = onDismiss,
        primary = "$cta · ${item.calories.roundToInt()} kcal",
        primaryEnabled = grams > 0,
        onPrimary = { onDone(item, q) },
    ) {
        // Unit: g · ml · kg · serving (serving disabled when the food has no serving size).
        Row(Modifier.fillMaxWidth().background(p.card2, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QUnit.entries.forEach { u ->
                val sel = u == unit
                val enabled = u != QUnit.SERVING || sg != null
                Box(
                    Modifier.weight(1f).height(36.dp).alpha(if (enabled) 1f else 0.4f)
                        .background(if (sel) p.card else Color.Transparent, CircleShape)
                        .clickable(enabled = enabled) {
                            pick(
                                when (u) {
                                    QUnit.SERVING -> Quantity(u, (((if (sg != null) grams / sg else 1.0).takeIf { it > 0 } ?: 1.0) * 2).roundToInt() / 2.0)
                                    QUnit.KG -> Quantity(u, (grams / 10).roundToInt() / 100.0)
                                    else -> Quantity(u, grams.roundToInt().toDouble())
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (u == QUnit.SERVING) (servingLabel?.removePrefix("1 ") ?: "serving") else u.label, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Number + servings stepper.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unit == QUnit.SERVING && sg != null) StepButton("−") { step(-0.5) }
            Box(Modifier.weight(1f).height(48.dp).background(p.card2, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
                BasicTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight(800), color = p.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(p.ink),
                )
            }
            Text(if (unit == QUnit.SERVING) (servingLabel?.removePrefix("1 ") ?: if (value == 1.0) "serving" else "servings") else unit.label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.width(56.dp))
            if (unit == QUnit.SERVING && sg != null) StepButton("+") { step(0.5) }
        }
        Spacer(Modifier.height(6.dp))

        // Quick chips.
        val chips = remember(food) { food.quickChips() }
        chips.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, cq) ->
                    val sel = cq.unit == unit && kotlin.math.abs(cq.value - value) < 1e-6
                    SmallChip(label, { pick(cq) }, filled = sel)
                }
            }
        }
        if (allowRestaurant) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().border(1.5.dp, p.hair, RoundedCornerShape(16.dp)).clickable { restaurant = !restaurant }.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Restaurant portion", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                    Text("×${com.sohum.bandlog.util.Restaurant.MULTIPLIER} the amount" + (if (oily) " + 1 tsp hidden oil" else "") + " — outside kitchens serve bigger", fontSize = 11.sp, color = p.muted, lineHeight = 14.sp)
                }
                androidx.compose.material3.Switch(
                    restaurant, { restaurant = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Live preview.
        Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${item.calories.roundToInt()} kcal", fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, lineHeight = 24.sp)
                Text("${fmt((item.grams * 10).roundToInt() / 10.0)} g total" + (if (restaurant) " · restaurant" else ""), fontSize = 12.sp, color = p.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroDot("${fmt(item.proteinG)}g", p.red); MacroDot("${fmt(item.carbsG)}g", p.orange); MacroDot("${fmt(item.fatG)}g", p.blue)
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val p = palette
    Box(Modifier.size(48.dp).pressable().background(p.card2, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink)
    }
}

/**
 * A macro donut: protein / carbs / fat share of the calories in one serving (4 / 4 / 9 kcal per g),
 * drawn from zero with a spring. [center] sits inside the ring.
 */
@Composable
fun MacroDonut(proteinG: Double, carbsG: Double, fatG: Double, size: Dp = 84.dp, stroke: Dp = 11.dp, center: (@Composable () -> Unit)? = null) {
    val p = palette
    val kcal = proteinG * 4 + carbsG * 4 + fatG * 9
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) 1f else 0f, Motion.spatialSlow(), label = "donut")
    val parts = if (kcal > 0) listOf(proteinG * 4 / kcal to p.red, carbsG * 4 / kcal to p.orange, fatG * 9 / kcal to p.blue) else emptyList()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val track = p.track
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            var startAngle = -90f
            parts.forEach { (share, color) ->
                val sweep = (360f * share * a).toFloat()
                if (sweep > 0.5f) drawArc(color, startAngle, sweep, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Butt))
                startAngle += sweep
            }
        }
        center?.invoke()
    }
}

/**
 * Small chip for quick picks ("Cooked in…" fats, serving chips, lens chips). The pill is 32 dp tall
 * but the touch target is 44 dp, so it stays easy to hit without looking chunky.
 */
@Composable
fun SmallChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, dashed: Boolean = false) {
    val p = palette
    Box(modifier.heightIn(min = 44.dp).pressable().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        var m = Modifier.height(32.dp)
        m = if (dashed) m.border(1.5.dp, p.hair, CircleShape) else m.background(if (filled) p.btn else p.card2, CircleShape)
        Box(m.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (filled) p.btnInk else p.ink, maxLines = 1)
        }
    }
}
