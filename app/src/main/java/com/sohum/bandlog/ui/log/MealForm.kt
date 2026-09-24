package com.sohum.bandlog.ui.log

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.FoodHit
import com.sohum.bandlog.data.FoodPreset
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.ParseResult
import com.sohum.bandlog.data.PlateEstimate
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.DropIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GridIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.MicIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.QuantitySheet
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.SearchIcon
import com.sohum.bandlog.ui.components.SmallChip
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.scan.PhotoReview
import com.sohum.bandlog.ui.scan.decodeScaled
import com.sohum.bandlog.ui.scan.toJpegBase64
import com.sohum.bandlog.ui.scan.toJpegBytes
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.QUnit
import com.sohum.bandlog.util.Quantity
import com.sohum.bandlog.util.QuantityFood
import com.sohum.bandlog.util.rememberDictation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CATEGORIES = listOf(
    "breakfast" to "Breakfast", "staple" to "Staples", "dal" to "Dal", "sabzi" to "Sabzi", "protein" to "Protein",
    "snack" to "Snacks", "drink" to "Drinks", "sweet" to "Sweets", "fruit" to "Fruit",
)
private val SOURCE_LABEL = mapOf("dish" to "INDB", "ifct" to "IFCT", "usda" to "USDA", "custom" to "Curated", "off" to "OFF")

/** What the Quantity sheet is open for: a food to add, or a review row to change. */
private data class SheetReq(val food: QuantityFood, val initial: Quantity? = null, val replace: Int? = null, val restaurant: Boolean = false)

/**
 * The Meal form: Dictate · Search · Presets · Photo build one review list, every quantity goes
 * through the shared Quantity sheet, and "Cooked in…" adds the fat as its own row.
 */
@Composable
fun MealForm(vm: AppViewModel, date: String, onClose: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var items by remember { mutableStateOf<List<MealItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var fixText by remember { mutableStateOf("") }
    var fixing by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf("") }
    var showSaveAs by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    val added = remember { mutableListOf<String>() }
    LaunchedEffect(Unit) { vm.loadSavedMeals(); vm.loadPresets() }

    val showReview = result != null || items.isNotEmpty()
    val fats = remember(vm.presets) { vm.presets.filter { it.category == "fat" } }

    fun add(item: MealItem, label: String = item.name) { items = items + item; added += label; error = null }

    /** "Cooked in…": the fat becomes its own row right after the dish, and the dish remembers it. */
    fun cookedIn(dishIdx: Int, fat: FoodPreset) {
        val tsp = fat.servings.firstOrNull { it.label.contains("1 tsp", ignoreCase = true) } ?: fat.servings.firstOrNull()
        val food = QuantityFood.from(fat, tsp?.label)
        val fatItem = food.item(if (tsp != null) Quantity(QUnit.SERVING, 1.0) else Quantity(QUnit.G, 5.0))
        items = items.toMutableList().also { l -> l[dishIdx] = l[dishIdx].copy(cookedIn = fat.id); l.add(dishIdx + 1, fatItem) }
        added += "${fat.label} (cooked in)"
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---- Dictate · Search · Presets · Photo ----
            Rise(0) {
                Row(Modifier.fillMaxWidth().background(p.card2, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(MicIcon to "Dictate", SearchIcon to "Search", GridIcon to "Presets", CameraIcon to "Photo").forEachIndexed { i, (icon, label) ->
                        val sel = tab == i
                        Row(
                            Modifier.weight(1f).height(32.dp).background(if (sel) p.card else androidx.compose.ui.graphics.Color.Transparent, CircleShape).clickable { tab = i; focus.clearFocus() },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, tint = if (sel) p.ink else p.muted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (sel) p.ink else p.muted, maxLines = 1)
                        }
                    }
                }
            }

            if (vm.savedMeals.isNotEmpty() && !showReview && tab == 0) Rise(0) {
                Column {
                    Text("Saved meals · one tap", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    vm.savedMeals.chunked(2).forEach { row ->
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { sm ->
                                Card(Modifier.weight(1f), padding = 12.dp, onClick = { text = sm.name; items = sm.items; result = ParseResult(sm.items, emptyList(), emptyList()) }) {
                                    Text(sm.name, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                                    Text("${sm.calories.toInt()} kcal · ${fmt(sm.proteinG)} g P", fontSize = 12.sp, color = p.muted)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            when (tab) {
                0 -> DictateCard(
                    text = text, onText = { text = it }, parsing = parsing,
                    showQuick = result == null && items.isEmpty(),
                    onRun = {
                        scope.launch {
                            parsing = true; error = null
                            try {
                                val r = Api.parseMeal(text)
                                result = r
                                items = items + r.items
                            } catch (e: Exception) { error = e.message } finally { parsing = false }
                        }
                    },
                    onQuick = { vm.quickLogMeal(text.trim(), date); onClose() },
                )
                1 -> SearchCard { hit -> sheet = SheetReq(QuantityFood.from(hit)) }
                2 -> PresetsCard(vm.presets, vm.presetsLoading, onRestaurant = { preset -> sheet = SheetReq(QuantityFood.from(preset), restaurant = true) }) { preset, serving ->
                    if (serving == null) sheet = SheetReq(QuantityFood.from(preset))
                    else add(QuantityFood.from(preset, serving).item(Quantity(QUnit.SERVING, 1.0)), preset.label)
                }
                else -> PhotoCard(vm, date, onClose)
            }

            ErrorNote(error)

            if (showReview) {
                Rise(1) { Text("Review · tap a quantity to change it", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(horizontal = 4.dp)) }
                Rise(2) {
                    Card(padding = 0.dp) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            if (items.isEmpty()) Text("No items left. Add some from Dictate, Search or Presets.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(vertical = 14.dp))
                            items.forEachIndexed { idx, it ->
                                if (idx > 0) Hair()
                                val isFat = fats.any { f -> f.id == it.cookedIn || f.foodId == it.foodId || f.label == it.name }
                                ReviewRow(
                                    item = it,
                                    fats = fats,
                                    showCookedIn = it.cookedIn == null && it.source != "scan" && !isFat && QuantityFood.wantsCookedIn(it.name),
                                    onCookedIn = { fat -> cookedIn(idx, fat) },
                                    onOpen = {
                                        val byServing = it.unit == "serving" && it.servings != null && it.servings > 0
                                        sheet = SheetReq(QuantityFood.from(it), if (byServing) Quantity(QUnit.SERVING, it.servings!!) else Quantity(QUnit.G, it.grams.toInt().toDouble()), replace = idx)
                                    },
                                    onRemove = { items = items.filterIndexed { i, _ -> i != idx } },
                                )
                            }
                        }
                    }
                }
                val r = result
                if (r != null && (r.assumptions.isNotEmpty() || r.unparsed.isNotEmpty())) Rise(3) {
                    Text((r.assumptions + r.unparsed.map { "Ignored: $it" }).joinToString(" · "), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp))
                }
                if (r != null) Rise(4) {
                    Card(padding = 12.dp) {
                        Text("Something wrong? Tell me and I'll redo it", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(10.dp)) {
                                BasicTextField(
                                    fixText, { fixText = it }, Modifier.fillMaxWidth(), singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                    textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                    decorationBox = { inner -> if (fixText.isEmpty()) Text("e.g. it was two scoops, and the rice was raw", fontSize = 14.sp, color = p.muted); inner() },
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            PillButton(if (fixing) "…" else "Fix", enabled = fixText.isNotBlank() && !fixing, modifier = Modifier.width(64.dp), height = 40.dp, onClick = {
                                scope.launch {
                                    fixing = true; error = null
                                    try { val fixed = Api.parseMeal(text, fixText, items); result = fixed; items = fixed.items; fixText = "" } catch (e: Exception) { error = e.message } finally { fixing = false }
                                }
                            })
                        }
                    }
                }
                Rise(5) {
                    if (showSaveAs) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            BasicTextField(
                                savedName, { savedName = it }, Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (savedName.isEmpty()) Text("Name it, e.g. Dinner usual", fontSize = 14.sp, color = p.muted); inner() },
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        PillButton("Save", enabled = savedName.isNotBlank(), modifier = Modifier.width(72.dp), height = 40.dp, onClick = {
                            scope.launch { runCatching { Api.saveSavedMeal(savedName.trim(), items); vm.loadSavedMeals(); showSaveAs = false; savedName = "" }.onFailure { error = it.message } }
                        })
                    } else TextButton(onClick = { showSaveAs = true }) { Text("Save as a repeat meal", color = p.muted, fontSize = 13.sp) }
                }
            }
        }
        if (showReview) {
            Row(Modifier.padding(16.dp, 12.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${items.sumOf { it.calories }.toInt()} kcal", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                    Text("${fmt(items.sumOf { it.proteinG })} g protein", fontSize = 12.sp, color = p.muted)
                }
                Spacer(Modifier.width(12.dp))
                PillButton(if (saving) "Saving…" else "Save meal · ${items.size} item${if (items.size == 1) "" else "s"}", enabled = items.isNotEmpty() && !saving, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        saving = true
                        val raw = text.trim().ifBlank { added.joinToString(", ").ifBlank { "Meal" } }
                        if (vm.saveMeal(date, raw, items)) onClose() else { error = vm.error; saving = false }
                    }
                })
            }
        }
    }

    sheet?.let { req ->
        QuantitySheet(
            food = req.food, initial = req.initial,
            title = if (req.replace != null) "Change the amount" else "How much?",
            cta = if (req.replace != null) "Update" else "Add",
            restaurantStart = req.restaurant,
            onDismiss = { sheet = null },
            onDone = { item, _ ->
                sheet = null
                val idx = req.replace
                if (idx != null) items = items.toMutableList().also { l -> val old = l[idx]; l[idx] = item.copy(cookedIn = item.cookedIn ?: old.cookedIn, source = old.source, foodId = old.foodId) }
                else add(item)
            },
        )
    }
}

// ---- Dictate ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DictateCard(text: String, onText: (String) -> Unit, parsing: Boolean, showQuick: Boolean, onRun: () -> Unit, onQuick: () -> Unit) {
    val p = palette
    val focus = LocalFocusManager.current
    val (dictation, toggle) = rememberDictation { chunk ->
        val base = text.trimEnd()
        onText(if (base.isEmpty()) chunk else base + (if (base.endsWith(",") || base.endsWith("।")) " " else ", ") + chunk)
    }
    Rise(1) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tap: listen / stop. Long-press: switch English ↔ Hindi.
                Box(
                    Modifier.size(40.dp).pressable().background(if (dictation.listening) p.red else p.btn, CircleShape)
                        .combinedClickable(onClick = toggle, onLongClick = { dictation.toggleLang() }),
                    contentAlignment = Alignment.Center,
                ) { Icon(MicIcon, "Dictate", tint = if (dictation.listening) androidx.compose.ui.graphics.Color.White else p.btnInk, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Describe what you ate", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                    Text(
                        when {
                            dictation.listening -> "Listening in ${if (dictation.lang == "hi-IN") "Hindi" else "English"}… tap the mic to stop"
                            dictation.available -> "Tap the mic, or type. Hold the mic for Hindi."
                            else -> "Type, or use your keyboard's mic. Hindi works too."
                        },
                        fontSize = 12.sp, color = p.muted,
                    )
                }
                if (dictation.available) SmallChip(dictation.langLabel, { dictation.toggleLang() })
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(14.dp)).padding(14.dp)) {
                BasicTextField(
                    text, onText, Modifier.fillMaxWidth().height(64.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    textStyle = TextStyle(fontSize = 15.sp, color = p.ink, lineHeight = 22.sp), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (text.isEmpty()) Text("150 g rice, 100 g dal, 2 eggs — or: दो रोटी, एक कटोरी दाल", fontSize = 15.sp, color = p.muted); inner() },
                )
            }
            dictation.error?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = p.orange) }
            Spacer(Modifier.height(12.dp))
            PillButton(if (parsing) "Working out the calories…" else "Work out the calories", enabled = text.isNotBlank() && !parsing, height = 48.dp, onClick = onRun)
            if (showQuick) {
                Spacer(Modifier.height(8.dp))
                PillButton("Log now, review later", enabled = text.isNotBlank() && !parsing, height = 44.dp, bg = p.card2, fg = p.ink, onClick = onQuick)
            }
            if (parsing) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track) }
        }
    }
}

// ---- Search ----

@Composable
private fun SearchCard(onPick: (FoodHit) -> Unit) {
    val p = palette
    val focus = LocalFocusManager.current
    var q by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<FoodHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(200)
        busy = true
        runCatching { Api.searchFoods(q) }.onSuccess { hits = it; err = null }.onFailure { err = it.message }
        busy = false
    }
    Rise(1) {
        Card(padding = 0.dp) {
            Column(Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp)) {
                Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SearchIcon, null, tint = p.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        q, { q = it.take(60) }, Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                        decorationBox = { inner -> if (q.isEmpty()) Text("Roti, dal tadka, paneer, अंडा, Maggi…", fontSize = 15.sp, color = p.muted); inner() },
                    )
                    if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = p.muted)
                }
                Text("3,000+ Indian and Western foods · English, Hinglish or हिंदी", fontSize = 11.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                err?.let { Text(it, fontSize = 13.sp, color = p.red, modifier = Modifier.padding(vertical = 12.dp)) }
                if (q.trim().length >= 2 && !busy && hits.isEmpty() && err == null) Text("Nothing matches “$q” — try Dictate, which can estimate it.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(vertical = 14.dp))
                hits.forEachIndexed { i, h ->
                    if (i > 0) Hair()
                    Row(Modifier.fillMaxWidth().clickable { onPick(h) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(h.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                if (h.nameHi != null) Text("  ${h.nameHi}", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted, maxLines = 1)
                            }
                            Text(
                                "${h.calories.toInt()} kcal · ${fmt(h.proteinG)} g P per 100 g" + (h.units.firstOrNull()?.let { " · ${it.label} ${it.grams.toInt()} g" } ?: ""),
                                fontSize = 12.sp, color = p.muted, maxLines = 1,
                            )
                        }
                        Box(Modifier.padding(start = 8.dp).background(p.card2, CircleShape).padding(7.dp, 2.dp)) {
                            Text(SOURCE_LABEL[h.source] ?: h.source, fontSize = 10.sp, fontWeight = FontWeight(700), color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

// ---- Presets ----

/** Category chips → grid of preset cards → serving chips (+ Custom…). [onPick] gets the tapped serving label, or null for Custom. */
@Composable
private fun PresetsCard(presets: List<FoodPreset>, loading: Boolean, onRestaurant: (FoodPreset) -> Unit, onPick: (FoodPreset, String?) -> Unit) {
    val p = palette
    var cat by rememberSaveable { mutableStateOf("breakfast") }
    var open by remember { mutableStateOf<String?>(null) }
    val list = remember(presets, cat) { presets.filter { it.category == cat }.sortedBy { it.sort } }
    // v2.0: common outside dishes at the top of Snacks and Protein; each opens as a restaurant portion.
    val outside = remember(presets) { presets.filter { it.category == "restaurant" }.sortedBy { it.sort } }
    Rise(1) {
        Column {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CATEGORIES.forEach { (key, label) ->
                    val sel = key == cat
                    Box(
                        Modifier.height(34.dp).pressable().background(if (sel) p.btn else p.card2, CircleShape).clickable { cat = key; open = null }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (sel) p.btnInk else p.ink) }
                }
            }
            Spacer(Modifier.height(10.dp))
            if ((cat == "snack" || cat == "protein") && outside.isNotEmpty()) {
                Text("Restaurant", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    outside.forEach { pr -> SmallChip(pr.label, { onRestaurant(pr) }, dashed = true) }
                }
            }
            if (presets.isEmpty()) {
                Card {
                    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track); Spacer(Modifier.height(8.dp)) }
                    Text(if (loading) "Loading the presets…" else "Presets didn't load — pull to refresh on Home, or use Search.", fontSize = 13.sp, color = p.muted)
                }
            }
            // An open card spans the row on its own; the rest sit two to a row.
            val rows = mutableListOf<List<FoodPreset>>()
            var i = 0
            while (i < list.size) {
                if (list[i].id == open) { rows += listOf(list[i]); i++ }
                else if (i + 1 < list.size && list[i + 1].id != open) { rows += listOf(list[i], list[i + 1]); i += 2 }
                else { rows += listOf(list[i]); i++ }
            }
            rows.forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pr ->
                        val isOpen = open == pr.id
                        val def = pr.default
                        Card(Modifier.weight(1f), padding = 12.dp, onClick = { open = if (isOpen) null else pr.id }) {
                            Text(pr.label, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                            Text(
                                (pr.labelHi?.let { "$it · " } ?: "") + (def?.let { "${it.label} · ${(pr.calories * it.grams / 100).toInt()} kcal" } ?: "per 100 g"),
                                fontSize = 12.sp, color = p.muted, maxLines = 1,
                            )
                            if (isOpen) {
                                Spacer(Modifier.height(10.dp))
                                (pr.servings.map { s -> "${s.label} · ${(pr.calories * s.grams / 100).toInt()} kcal" to s.label } + ("Custom…" to null)).chunked(3).forEach { chips ->
                                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        chips.forEach { (label, serving) ->
                                            SmallChip(label, { onPick(pr, serving); open = null }, filled = serving != null && serving == pr.defaultServing, dashed = serving == null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (row.size == 1 && row[0].id != open) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- Photo ----

/** The plate camera inside the Meal form: photo → /api/photo-meal → the same review as the Scan tab. */
@Composable
private fun PhotoCard(vm: AppViewModel, date: String, onClose: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var plate by remember { mutableStateOf<PlateEstimate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val target = remember { mutableStateOf<android.net.Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) target.value?.let { uri -> scope.launch { photo = withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 1600) }; plate = null } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 1600) }; plate = null }
    }
    fun openCamera() {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(dir, "meal.jpg"))
        target.value = uri
        runCatching { camera.launch(uri) }.onFailure { error = "No camera app found — pick from gallery instead." }
    }
    Rise(1) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(CameraIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Photograph your plate", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                    Text("Every item with grams, calories, macros and micros", fontSize = 12.sp, color = p.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            photo?.let { bmp ->
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (photo == null) "Take photo" else "Retake", { openCamera() }, Modifier.weight(1f), height = 46.dp)
                PillButton("Gallery", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), height = 46.dp, bg = p.card2, fg = p.ink)
            }
            if (photo != null && plate == null) {
                Spacer(Modifier.height(10.dp))
                PillButton(if (busy) "Looking at the plate…" else "Estimate", enabled = !busy, height = 48.dp, onClick = {
                    scope.launch {
                        busy = true; error = null
                        try { plate = Api.photoMeal(withContext(Dispatchers.IO) { toJpegBase64(photo!!, 85) }, "") } catch (e: Exception) { error = e.message } finally { busy = false }
                    }
                })
                if (busy) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track) }
            }
        }
    }
    ErrorNote(error)
    plate?.let { est ->
        PhotoReview(est, photo, readOnly = false) { items, path ->
            scope.launch {
                busy = true
                val photoPath = path ?: photo?.let { b -> runCatching { Api.uploadMealPhoto(withContext(Dispatchers.IO) { toJpegBytes(b, 85) }) }.getOrNull() }
                val ok = vm.saveMeal(date, est.plateNote.ifBlank { items.joinToString(", ") { it.name } }, items.map { it.toMealItem() }, photoPath)
                busy = false
                if (ok) onClose() else error = vm.error
            }
        }
    }
}

// ---- Review row ----

@Composable
private fun ReviewRow(item: MealItem, fats: List<FoodPreset>, showCookedIn: Boolean, onCookedIn: (FoodPreset) -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    val p = palette
    // Delta badge: shows "+99 kcal" for a moment after the amount changes.
    var last by remember { mutableStateOf(item.calories) }
    var delta by remember { mutableIntStateOf(0) }
    var showDelta by remember { mutableStateOf(false) }
    LaunchedEffect(item.calories) {
        val d = (item.calories - last).toInt(); last = item.calories
        if (d != 0) { delta = d; showDelta = true; delay(1400); showDelta = false }
    }
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name + if (item.source == "estimated") " ~" else "", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (item.source == "scan") Box(Modifier.padding(start = 6.dp).background(p.card2, CircleShape).padding(7.dp, 2.dp)) { Text("scan", fontSize = 10.sp, fontWeight = FontWeight(700), color = p.muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${item.calories.toInt()} kcal", fontSize = 12.sp, color = p.muted)
                MacroDot("${fmt(item.proteinG)}g", p.red); MacroDot("${fmt(item.carbsG)}g", p.orange); MacroDot("${fmt(item.fatG)}g", p.blue)
            }
            if (showCookedIn && fats.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(DropIcon, null, tint = p.muted, modifier = Modifier.size(12.dp))
                    Text("Cooked in…", fontSize = 11.sp, color = p.muted)
                    fats.take(4).forEach { f -> SmallChip(f.label, { onCookedIn(f) }) }
                }
            }
            item.cookedIn?.let { id -> Text(if (id == "restaurant") "Restaurant portion · oil included" else "Cooked in ${fats.firstOrNull { it.id == id }?.label ?: id}", fontSize = 11.sp, color = p.muted) }
        }
        AnimatedVisibility(showDelta, enter = scaleIn() + fadeIn(), exit = fadeOut() + scaleOut()) {
            Box(Modifier.padding(end = 6.dp).background(if (delta > 0) p.btn else p.card2, CircleShape).padding(8.dp, 3.dp)) {
                Text((if (delta > 0) "+" else "") + "$delta kcal", fontSize = 11.sp, fontWeight = FontWeight(700), color = if (delta > 0) p.btnInk else p.ink)
            }
        }
        // The quantity chip opens the sheet.
        Box(Modifier.height(36.dp).pressable().background(p.card2, RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(item.quantityLabel, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, textAlign = TextAlign.End)
        }
        IconButton(onClick = onRemove, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Remove", tint = p.muted) }
    }
}
