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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Overrides
import com.sohum.bandlog.util.PerUnit
import androidx.compose.foundation.layout.widthIn
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Counting
import com.sohum.bandlog.util.QUnit
import com.sohum.bandlog.util.Quantity
import com.sohum.bandlog.util.QuantityFood
import kotlin.math.roundToInt

/**
 * v2.5 the shared Quantity sheet, radically simpler. Two modes, decided by the food's servings:
 *  - Count foods (roti, egg, idli, scoop, katori, glass …): one big `[−] 1 roti [+]` stepper,
 *    default 1 (never the preset's "2 roti"), whole steps for pieces and ½ steps for katori / bowl /
 *    glass / cup, a live "≈ 40 g · 120 kcal" line, and one "Enter grams instead" link.
 *  - Loose foods (no serving size): a grams field and three chips (50 / 100 / 200 g) that wrap.
 * Whey and other scoop supplements get the stepper only. The restaurant portion hides under "More".
 * Built to hold at fontScale 1.3 on a 360 dp screen: nothing fixed-width beside a weighted field,
 * chips in a FlowRow, one-line labels ellipsize, touch targets ≥ 44 dp. Used by Add food (presets,
 * search results, plate rows) and the scan report's "Log 1 serving".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantitySheet(
    food: QuantityFood,
    initial: Quantity? = null,
    title: String = "How much?",
    cta: String = "Add",
    restaurantDefault: Boolean = false,
    /** v2.5: a parsed row's `default_count` from the server — where the stepper starts when there's no [initial]. */
    startCount: Double? = null,
    /**
     * v2.15 §2: shows "Correct the numbers". Called with the app's numbers and the user's (plus the
     * optional source and note) just before [onDone] gets the corrected row; the caller writes the
     * food_corrections row with its context (meal, input kind, raw words).
     */
    onCorrected: ((before: MealItem, after: MealItem, source: String?, note: String?) -> Unit)? = null,
    /** v2.17: extra content above the amount (the scan "Log it" meal-slot picker). */
    header: (@Composable () -> Unit)? = null,
    onDone: (MealItem, Quantity) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = palette
    val focus = LocalFocusManager.current
    val cu = remember(food) { Counting.unitFor(food) }
    val supplement = remember(food, cu) { Counting.isSupplement(food, cu) }
    // Where the sheet opens: the row's current amount when editing, else 1 piece (or 100 g loose).
    val startGrams = initial?.let { food.grams(it) }
    val openCount: Double? = when {
        cu == null -> null
        startGrams == null -> startCount?.takeIf { it > 0 }?.let { cu.snap(it) } ?: cu.defaultCount
        else -> (startGrams / cu.grams).let { n -> cu.snap(n).takeIf { kotlin.math.abs(it - n) <= 0.02 * n + 0.01 } }
    }
    var byGrams by remember(food) { mutableStateOf(cu == null || openCount == null) }
    var count by remember(food) { mutableStateOf(openCount ?: cu?.defaultCount ?: 1.0) }
    var gramsText by remember(food) { mutableStateOf(fmt((((startGrams ?: if (cu != null) cu.grams * count else 100.0)) * 10).roundToInt() / 10.0)) }
    val allowRestaurant = com.sohum.bandlog.util.Restaurant.allowed(food) && !supplement
    var restaurant by remember(food) { mutableStateOf(restaurantDefault && allowRestaurant) }
    var more by remember(food) { mutableStateOf(restaurantDefault && allowRestaurant) }
    val oily = com.sohum.bandlog.util.Restaurant.oily(food.name, food.category)

    // v2.15 §1: the user's per-unit number. A remembered one ("95 kcal per roti") applies when a new
    // food opens; editing "Calories per roti" / "per 100 g" re-prices the food per 100 g, and the
    // macros follow the calories unless typed too.
    val remembered = remember(food, cu) {
        if (initial != null) null
        else (cu?.let { Overrides.find(food.foodId, food.name, it.noun) }?.let { it to cu.grams })
            ?: Overrides.find(food.foodId, food.name, PerUnit.PER_100G)?.let { it to 100.0 }
    }
    val base0 = remember(food, remembered) {
        val b = PerUnit.per100Of(food)
        remembered?.let { (o, g) ->
            PerUnit.rescale(b, PerUnit.toPer100(o.kcalPerUnit, g), o.proteinPerUnit?.let { PerUnit.toPer100(it, g) }, o.carbsPerUnit?.let { PerUnit.toPer100(it, g) }, o.fatPerUnit?.let { PerUnit.toPer100(it, g) })
        } ?: b
    }
    var kcal100 by remember(food) { mutableStateOf(base0.kcal) }
    var prot100 by remember(food) { mutableStateOf(if (remembered?.first?.proteinPerUnit != null) base0.proteinG else null as Double?) }
    var carb100 by remember(food) { mutableStateOf(if (remembered?.first?.carbsPerUnit != null) base0.carbsG else null as Double?) }
    var fat100 by remember(food) { mutableStateOf(if (remembered?.first?.fatPerUnit != null) base0.fatG else null as Double?) }
    var perUnitTouched by remember(food) { mutableStateOf(false) }
    val per100 = PerUnit.rescale(PerUnit.per100Of(food), kcal100, prot100, carb100, fat100)
    val priced = PerUnit.apply(food, per100)
    val ownNumbers = perUnitTouched || remembered != null

    // Count mode prices through a one-piece serving ("1 roti" = 40 g) so the row stores unit=serving, servings=count.
    val counted = remember(priced, cu) { cu?.let { priced.copy(servings = listOf(it.serving()), defaultServing = it.serving().label) } }
    val q = if (!byGrams && counted != null) Quantity(QUnit.SERVING, count) else Quantity(QUnit.G, gramsText.toDoubleOrNull() ?: 0.0)
    val base = (if (!byGrams && counted != null) counted else priced).item(q)
    val item = if (restaurant) com.sohum.bandlog.util.Restaurant.apply(base, oily) else base
    // What the app itself would have said for this amount (before any number of the user's).
    val appItem = run {
        val f = if (!byGrams && cu != null) food.copy(servings = listOf(cu.serving()), defaultServing = cu.serving().label) else food
        f.item(q).let { if (restaurant) com.sohum.bandlog.util.Restaurant.apply(it, oily) else it }
    }

    // v2.15 §2 "Correct the numbers".
    var fixOpen by remember(food) { mutableStateOf(false) }
    var myKcal by remember(food) { mutableStateOf("") }
    var myProt by remember(food) { mutableStateOf("") }
    var myCarb by remember(food) { mutableStateOf("") }
    var myFat by remember(food) { mutableStateOf("") }
    var mySource by remember(food) { mutableStateOf("") }
    var myNote by remember(food) { mutableStateOf("") }
    val myK = myKcal.toDoubleOrNull()?.takeIf { it >= 0 }
    // The counted unit rides on the row ("1 roti" = 40 g) so logs and corrections know the noun and count.
    val unitTag: com.sohum.bandlog.data.Serving? = if (!byGrams && cu != null && cu.label == null) cu.serving() else null
    val corrected: MealItem? = if (fixOpen && onCorrected != null && myK != null) {
        PerUnit.applyCorrection(item, myK, myProt.toDoubleOrNull(), myCarb.toDoubleOrNull(), myFat.toDoubleOrNull()).copy(servingUnit = unitTag)
    } else null
    val shown = corrected ?: item

    fun switchToGrams() { gramsText = fmt((base.grams * 10).roundToInt() / 10.0); byGrams = true }
    fun switchToCount() { if (cu != null) { count = cu.snap((gramsText.toDoubleOrNull() ?: cu.grams) / cu.grams); byGrams = false; focus.clearFocus() } }

    fun finish() {
        val countMode = !byGrams && cu != null
        if (corrected != null) {
            onCorrected?.invoke(appItem.copy(servingUnit = unitTag), corrected, mySource.ifBlank { null }, myNote.ifBlank { null })
            onDone(corrected, q)
            return
        }
        if (!ownNumbers) { onDone(item.copy(servingUnit = unitTag ?: item.servingUnit), q); return }
        val unitGrams = if (countMode) cu!!.grams else 100.0
        val perUnitK = PerUnit.r1(PerUnit.fromPer100(per100.kcal, unitGrams))
        if (perUnitTouched) Overrides.remember(
            PerUnit.Override(
                PerUnit.foodKey(food.foodId, food.name), if (countMode) cu!!.noun else PerUnit.PER_100G, perUnitK,
                prot100?.let { PerUnit.r1(PerUnit.fromPer100(it, unitGrams)) }, carb100?.let { PerUnit.r1(PerUnit.fromPer100(it, unitGrams)) }, fat100?.let { PerUnit.r1(PerUnit.fromPer100(it, unitGrams)) },
            )
        )
        // Same rounding as the web (scaleToKcal): whole kcal, macros to one decimal.
        onDone(PerUnit.scaleToKcal(item, item.calories, item.proteinG, item.carbsG, item.fatG).copy(userVerified = true, perUnitKcal = if (countMode) Math.round(perUnitK).toDouble() else null, servingUnit = unitTag), q)
    }

    BottomSheet(
        title = food.name + (food.nameHi?.let { "  $it" } ?: ""),
        subtitle = title + (cu?.let { if (it.label != null) " · ${it.label} = ${fmt(it.grams)} g" else " · 1 ${it.noun} = ${fmt(it.grams)} g" } ?: ""),
        onDismiss = onDismiss,
        primary = (if (corrected != null) "Save my numbers" else cta) + " · ${shown.calories.roundToInt()} kcal",
        primaryEnabled = item.grams > 0,
        onPrimary = { finish() },
    ) {
        header?.let { it(); Spacer(Modifier.height(12.dp)) }
        if (!byGrams && cu != null) {
            // The one big stepper: [−]  2 roti  [+]
            Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(20.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", "One less", enabled = count > cu.step) { count = cu.snap(count - cu.step) }
                Row(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(count), fontSize = 36.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, maxLines = 1, softWrap = false)
                    Text(
                        " " + cu.display, fontSize = 17.sp, fontWeight = FontWeight(600), color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                StepButton("+", "One more") { count = cu.snap(count + cu.step) }
            }
            // v2.15 §1: quick picks 1 · 2 · 3.
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.0, 2.0, 3.0).forEach { n -> SmallChip(fmt(n), { count = n; focus.clearFocus() }, filled = count == n) }
            }
        } else {
            // Grams: one big field with "g" inside it.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(p.card2, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    gramsText, { gramsText = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight(800), color = p.ink), cursorBrush = SolidColor(p.ink),
                )
                Text("g", fontSize = 17.sp, fontWeight = FontWeight(600), color = p.muted, maxLines = 1)
            }
            if (cu == null) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50.0, 100.0, 200.0).forEach { g -> SmallChip("${fmt(g)} g", { gramsText = fmt(g); focus.clearFocus() }, filled = gramsText.toDoubleOrNull() == g) }
                }
            }
        }

        // v2.15 §1: "Calories per roti" (count) or "Calories per 100 g" (grams), editable.
        val countMode = !byGrams && cu != null
        val unitGrams = if (countMode) cu!!.grams else 100.0
        val unitName = if (!countMode) "100 g" else if (cu!!.label != null) "serving" else cu.noun
        Spacer(Modifier.height(10.dp))
        PerUnitRow(
            label = "Calories per $unitName", unitGrams = unitGrams,
            kcal100 = per100.kcal, prot100 = per100.proteinG, carb100 = per100.carbsG, fat100 = per100.fatG,
            onKcal = { kcal100 = it; perUnitTouched = true },
            onProt = { prot100 = it; perUnitTouched = true }, onCarb = { carb100 = it; perUnitTouched = true }, onFat = { fat100 = it; perUnitTouched = true },
            note = when {
                perUnitTouched && Overrides.available == true -> "We'll use this for your next ${if (countMode) unitName else food.name.lowercase()}."
                perUnitTouched -> null
                remembered != null -> "Your number from last time."
                else -> null
            },
        )

        // Live line: ≈ 80 g · 240 kcal, then the macros; wraps instead of colliding.
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "≈ ${fmt((shown.grams * 10).roundToInt() / 10.0)} g · ${shown.calories.roundToInt()} kcal" + (if (restaurant) " · restaurant" else ""),
                fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1,
            )
            Row(Modifier.heightIn(min = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MacroDot("${fmt(shown.proteinG)}g", p.red); MacroDot("${fmt(shown.carbsG)}g", p.orange); MacroDot("${fmt(shown.fatG)}g", p.blue)
            }
        }

        // Small text links: grams ⇄ count, and "More" (restaurant portion). Whey gets neither.
        if (!supplement && (cu != null || allowRestaurant)) {
            Spacer(Modifier.height(2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cu != null) {
                    if (byGrams) SheetLink("Count in ${if (cu.label == null) cu.noun else "servings"} instead") { switchToCount() }
                    else SheetLink("Enter grams instead") { switchToGrams() }
                }
                if (allowRestaurant) SheetLink(if (more) "Less" else "More") { more = !more }
            }
        }
        if (more && allowRestaurant) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.5.dp, p.hair, RoundedCornerShape(16.dp)).clickable { restaurant = !restaurant }.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Restaurant portion", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("×${com.sohum.bandlog.util.Restaurant.MULTIPLIER} the amount" + (if (oily) " + 1 tsp oil" else ""), fontSize = 11.sp, color = p.muted, lineHeight = 14.sp)
                }
                androidx.compose.material3.Switch(
                    restaurant, { restaurant = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = p.btn, checkedThumbColor = p.btnInk, uncheckedTrackColor = p.track, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.hair),
                )
            }
        }

        // v2.15 §2: "Correct the numbers" — the user's own calories (and optionally macros, a source, a note).
        if (onCorrected != null) {
            Spacer(Modifier.height(6.dp))
            SheetLink(if (fixOpen) "Cancel correction" else "Correct the numbers") { fixOpen = !fixOpen }
            if (fixOpen) {
                Column(
                    Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Checked another source? Put its numbers for this amount (${fmt((item.grams * 10).roundToInt() / 10.0)} g). We had ${item.calories.roundToInt()} kcal.",
                        fontSize = 12.sp, color = p.muted, lineHeight = 16.sp,
                    )
                    CorrectionField("My calories", myKcal, "kcal", decimal = true) { myKcal = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CorrectionField("Protein", myProt, "g", decimal = true, modifier = Modifier.weight(1f), placeholder = fmt(PerUnit.r1(item.proteinG))) { myProt = it }
                        CorrectionField("Carbs", myCarb, "g", decimal = true, modifier = Modifier.weight(1f), placeholder = fmt(PerUnit.r1(item.carbsG))) { myCarb = it }
                        CorrectionField("Fat", myFat, "g", decimal = true, modifier = Modifier.weight(1f), placeholder = fmt(PerUnit.r1(item.fatG))) { myFat = it }
                    }
                    CorrectionField("Source (optional)", mySource, null, placeholder = "e.g. Pintola pack, HealthifyMe") { mySource = it.take(300) }
                    CorrectionField("Note (optional)", myNote, null, placeholder = "Anything we should know") { myNote = it.take(500) }
                    if (myK == null) Text("Enter your calories to save them.", fontSize = 11.sp, color = p.muted)
                }
            }
        }
    }
}

/**
 * v2.15 §1: "Calories per roti [ 95 ]" and, one tap away, protein / carbs / fat per roti. Values are
 * shown per unit but kept per 100 g; untouched macros follow the calories.
 */
@Composable
private fun PerUnitRow(
    label: String, unitGrams: Double,
    kcal100: Double, prot100: Double, carb100: Double, fat100: Double,
    onKcal: (Double) -> Unit, onProt: (Double) -> Unit, onCarb: (Double) -> Unit, onFat: (Double) -> Unit,
    note: String?,
) {
    val p = palette
    val focus = LocalFocusManager.current
    // Typed text while the field is being edited; null shows the live value (it follows the stepper's unit).
    var kText by remember(unitGrams) { mutableStateOf<String?>(null) }
    var macrosOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(p.card2, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        BasicTextField(
            kText ?: fmt(PerUnit.fromPer100(kcal100, unitGrams).roundToInt().toDouble()),
            { v -> val t = v.filter { c -> c.isDigit() || c == '.' }.take(6); kText = t; t.toDoubleOrNull()?.let { onKcal(PerUnit.toPer100(it, unitGrams)) } },
            Modifier.widthIn(min = 48.dp, max = 90.dp).semantics { contentDescription = label }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); kText = null }),
            textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight(800), color = p.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(p.ink),
        )
        Text(" kcal", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, maxLines = 1)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (note != null) Text(note, fontSize = 11.sp, color = p.muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
        else Spacer(Modifier.weight(1f))
        SheetLink(if (macrosOpen) "Hide macros" else "Macros per ${label.removePrefix("Calories per ")}") { macrosOpen = !macrosOpen }
    }
    if (macrosOpen) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple("Protein", prot100, onProt), Triple("Carbs", carb100, onCarb), Triple("Fat", fat100, onFat)).forEach { (name, v100, on) ->
            var t by remember(unitGrams, name) { mutableStateOf<String?>(null) }
            CorrectionField(name, t ?: fmt(PerUnit.r1(PerUnit.fromPer100(v100, unitGrams))), "g", decimal = true, modifier = Modifier.weight(1f)) { s ->
                t = s; s.toDoubleOrNull()?.let { on(PerUnit.toPer100(it, unitGrams)) }
            }
        }
    }
}

/** A small labelled number / text field on the card fill, for the sheet's own-numbers rows. */
@Composable
private fun CorrectionField(
    label: String, value: String, suffix: String?, decimal: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    val p = palette
    val focus = LocalFocusManager.current
    Column(modifier.background(p.card, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight(700), color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value, { v -> onChange(if (decimal) v.filter { c -> c.isDigit() || c == '.' }.take(7) else v) }, Modifier.weight(1f).heightIn(min = 28.dp).semantics { contentDescription = label },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink), cursorBrush = SolidColor(p.ink),
                decorationBox = { inner -> if (value.isEmpty() && placeholder != null) Text(placeholder, fontSize = 13.sp, color = p.muted, maxLines = 1); inner() },
            )
            if (suffix != null) Text(suffix, fontSize = 12.sp, color = p.muted, maxLines = 1)
        }
    }
}

/** v2.15 §2: the small ✓ "Your numbers" badge on an item the user corrected. */
@Composable
fun YourNumbersBadge(modifier: Modifier = Modifier) {
    val p = palette
    Box(modifier.background(p.greenBg, CircleShape).padding(horizontal = 7.dp, vertical = 1.dp).semantics { contentDescription = "Your numbers" }) {
        Text("✓ Your numbers", fontSize = 10.sp, fontWeight = FontWeight(700), color = p.green, maxLines = 1)
    }
}

@Composable
private fun SheetLink(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(700), color = palette.ink, textDecoration = TextDecoration.Underline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(56.dp).alpha(if (enabled) 1f else 0.35f).pressable().background(p.card, CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight(700), color = p.ink)
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
