@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sohum.bandlog.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.FoodHit
import com.sohum.bandlog.data.FoodPreset
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.SavedMeal
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.DropIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FoodImage
import com.sohum.bandlog.ui.components.FoodImages
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.MicIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.QuantitySheet
import com.sohum.bandlog.ui.components.SearchIcon
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.DictationState
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.QUnit
import com.sohum.bandlog.util.Quantity
import com.sohum.bandlog.util.QuantityFood
import com.sohum.bandlog.util.Restaurant
import com.sohum.bandlog.util.rememberDictation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CATEGORIES = listOf(
    "breakfast" to "Breakfast", "staple" to "Staples", "dal" to "Dal", "sabzi" to "Sabzi", "protein" to "Protein",
    "snack" to "Snacks", "drink" to "Drinks", "sweet" to "Sweets", "fruit" to "Fruit",
)
private const val YOURS = "yours"
private const val RESTAURANT = "restaurant"
private val SOURCE_LABEL = mapOf("dish" to "INDB", "ifct" to "IFCT", "usda" to "USDA", "custom" to "Curated", "off" to "OFF")

private val UNIT_WORD = Regex(
    "\\d+(\\.\\d+)?\\s*(g|gm|gms|gram|grams|ml|kg|katori|katoris|roti|rotis|chapati|chapatis|cup|cups|bowl|bowls|tbsp|tsp|scoop|scoops|piece|pieces|pc|pcs|slice|slices|glass|glasses|plate|plates|egg|eggs)\\b",
    RegexOption.IGNORE_CASE,
)

/** Whether the bar holds a description ("2 roti, dal") rather than a food name to look up. */
internal fun looksLikeSentence(s: String): Boolean {
    val t = s.trim()
    if (t.isEmpty()) return false
    return t.split(Regex("\\s+")).size >= 3 || ',' in t || UNIT_WORD.containsMatchIn(t) || t.any { it in 'ऀ'..'ॿ' }
}

/**
 * The Quantity sheet request: a plate row whose amount is being changed ([replace] = its index), or
 * (v2.5) a preset / search result being added ([replace] = -1) — you pick the number, it isn't guessed.
 */
private data class SheetReq(
    val food: QuantityFood, val initial: Quantity?, val replace: Int,
    val raw: String = food.name, val restaurant: Boolean = false, val startCount: Double? = null,
)

/** A parse or plate photo still working; its items land on the plate when it finishes. */
private class Pending(val label: String, val job: Deferred<AppViewModel.MealBatch>)

/**
 * v2.1 Add food: one screen. A search bar (type to search the food table, the mic to say it), the
 * preset grid below it while the bar is empty ("Yours" first: saved meals + your most-used foods),
 * and the plate as a sticky panel at the bottom. v2.4: scanning lives only in the Scan tab; its
 * "Log 1 serving" opens this screen with the scanned item already on the plate.
 * Tapping a preset or a result adds it at its default serving; the amount on a plate row opens
 * the Quantity sheet. A sentence in the bar gets one "Work it out" button (parse-meal).
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MealForm(
    vm: AppViewModel,
    date: String,
    onClose: () -> Unit,
    /** v2.8: Home's per-section "+ Add" preselects the type; otherwise the hour rule picks it. */
    mealType: String? = null,
    /** v2.8 meal editor: the saved meal being edited (items, type, date; Save updates it, Delete removes it). */
    existing: com.sohum.bandlog.data.Meal? = null,
) {
    val p = palette
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MealItem>>(existing?.items.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.let { MealTypes.of(it) } ?: mealType?.takeIf { MealTypes.isType(it) } ?: MealTypes.default()) }
    var day by rememberSaveable { mutableStateOf(existing?.date ?: date) }
    var pickDate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var deleteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Leaving the editor inside the undo window still deletes (on the view model's scope), like the workout editor.
    androidx.compose.runtime.DisposableEffect(existing?.id) {
        onDispose { if (pendingDelete && deleteJob?.isActive == true) { deleteJob?.cancel(); existing?.let { vm.deleteMealLater(it.id) } } }
    }
    val rawParts = remember { mutableStateListOf<String>() }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf<List<Pending>>(emptyList()) }
    var photoPath by remember { mutableStateOf(existing?.photoPath) }
    var parsedText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastTick by remember { mutableIntStateOf(0) }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    var cookedFor by remember { mutableStateOf<Int?>(null) }
    var fixOpen by remember { mutableStateOf(false) }
    var repeatOpen by remember { mutableStateOf(false) }
    var waterNotice by remember { mutableStateOf<com.sohum.bandlog.data.ParsedWater?>(null) }
    var waterUndone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.loadSavedMeals(); vm.loadPresets()
        // A scan's "Log 1 serving" lands here with the item already on the plate.
        vm.takeAddFoodPrefill()?.takeIf { it.isNotEmpty() }?.let { pre ->
            items = items + pre; pre.forEach { rawParts += it.name }
            toast = "From your scan · ${pre.sumOf { it.calories }.roundToInt()} kcal"; toastTick++
        }
    }
    LaunchedEffect(toastTick) { if (toast != null) { delay(1800); toast = null } }

    val use = remember(vm.meals) { vm.foodUse() }
    val fats = remember(vm.presets) { vm.presets.filter { it.category == "fat" } }

    fun say(msg: String) { toast = msg; toastTick++ }

    /**
     * v2.7: "2 glasses of water" in a dictated sentence — parse-meal already pulled it out of the
     * food text (server-side, shared with the web app); this writes it to bandlog.water_log the
     * same way the Water page's + button does, and shows an Undo chip. Logged immediately (not
     * gated on Save) so a water-only utterance — which never puts anything on the plate — still
     * gets recorded; `quiet = true` because we show our own chip here instead of the app-wide notice.
     */
    fun handleWater(w: com.sohum.bandlog.data.ParsedWater) {
        waterNotice = w; waterUndone = false
        scope.launch { vm.logWater(w.ml, date, "custom", quiet = true) }
    }
    LaunchedEffect(waterNotice) { if (waterNotice != null) { delay(5000); waterNotice = null } }

    fun add(item: MealItem, raw: String, amount: String) {
        items = items + item; rawParts += raw; error = null
        say("Added · $amount · ${item.calories.roundToInt()} kcal")
    }


    fun addSaved(sm: SavedMeal) {
        items = items + sm.items; rawParts += sm.name; error = null
        say("Added · ${sm.name} · ${sm.calories.roundToInt()} kcal")
    }

    /** Watch a background job; when it lands its items join the plate. */
    fun track(label: String, job: Deferred<AppViewModel.MealBatch>, onDone: (AppViewModel.MealBatch) -> Unit = {}) {
        val pd = Pending(label, job)
        pending = pending + pd
        scope.launch {
            val r = runCatching { job.await() }
            pending = pending - pd
            r.onSuccess { b ->
                b.water?.let { handleWater(it) }
                if (b.items.isEmpty()) {
                    // A water-only utterance ("do glass paani piya") is a valid outcome, not an error.
                    if (b.water == null) error = "Couldn't find any food in “${label.take(40)}” — try naming it differently."
                } else {
                    items = items + b.items; notes = notes + b.notes
                    b.photoPath?.let { photoPath = it }
                    say("Added ${b.items.size} item${if (b.items.size == 1) "" else "s"} · ${b.items.sumOf { it.calories }.roundToInt()} kcal")
                }
                onDone(b)
            }.onFailure { error = it.message ?: "Couldn't work that out" }
        }
    }

    fun workItOut() {
        val t = text.trim()
        if (t.isEmpty()) return
        focus.clearFocus()
        rawParts += t
        parsedText = (parsedText?.let { "$it, " } ?: "") + t
        text = ""
        track(t, vm.parseAsync(t))
    }

    val (dictation, toggleMic) = rememberDictation { chunk ->
        val base = text.trimEnd()
        text = if (base.isEmpty()) chunk else base + (if (base.endsWith(",") || base.endsWith("।")) " " else ", ") + chunk
        // A spoken meal is always a description — work it out straight away.
        if (looksLikeSentence(text)) workItOut()
    }

    fun save() {
        val raw = rawParts.joinToString(", ").ifBlank { items.joinToString(", ") { it.name } }.ifBlank { "Meal" }
        if (existing != null) {
            // The editor waits for anything still being worked out (the button says so), then updates in place.
            if (pending.isNotEmpty() || items.isEmpty()) return
            scope.launch {
                saving = true
                val added = rawParts.toList()
                val newRaw = if (added.isEmpty()) null else (listOf(existing.rawText) + added).filter { it.isNotBlank() }.joinToString(", ")
                if (vm.updateMeal(existing, day, type, items, newRaw)) onClose() else { error = vm.error; saving = false }
            }
            return
        }
        // Log now, review later — automatically: Save while a parse / photo is working closes the
        // page, Home shows the pending row, and the meal saves the moment the job lands.
        if (pending.isNotEmpty()) { vm.saveMealAfter(date, raw, items, photoPath, pending.map { it.job }, type); onClose(); return }
        scope.launch {
            saving = true
            if (vm.saveMeal(date, raw, items, photoPath, type)) onClose() else { error = vm.error; saving = false }
        }
    }

    /** v2.7 undo pattern: "Deleted · Undo" for 5 s, then the delete. */
    fun startDelete() {
        val m = existing ?: return
        pendingDelete = true; error = null
        deleteJob = scope.launch {
            delay(5000)
            saving = true
            if (vm.deleteMeal(m.id)) onClose() else { error = vm.error; saving = false; pendingDelete = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 6.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // v2.8: which meal this is — the hour rule picks, one tap changes it.
                MealTypeChips(type) { type = it }
                if (existing != null) {
                    Card(padding = 0.dp) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { pickDate = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Date", fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink, modifier = Modifier.weight(1f))
                            Text(if (day == com.sohum.bandlog.util.Dates.today()) "Today, ${com.sohum.bandlog.util.Dates.short(day).drop(4)}" else com.sohum.bandlog.util.Dates.short(day), fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                            Text("  ›", fontSize = 15.sp, color = p.muted)
                        }
                    }
                }
                FoodBar(
                    text = text, onText = { text = it.take(160) }, dictation = dictation, onMic = toggleMic,
                    onSubmit = { if (looksLikeSentence(text)) workItOut() else focus.clearFocus() },
                    modifier = Modifier.fillMaxWidth(),
                )
                dictation.error?.let { Text(it, fontSize = 12.sp, color = p.orange, modifier = Modifier.padding(horizontal = 4.dp)) }
                ErrorNote(error)

                val q = text.trim()
                if (q.length >= 2 || looksLikeSentence(q)) {
                    SearchResults(q, looksLikeSentence(q), onWorkItOut = { workItOut() }) { h ->
                        focus.clearFocus()
                        sheet = SheetReq(QuantityFood.from(h), null, -1, raw = h.name)
                        text = ""
                    }
                } else {
                    PresetGrid(
                        vm = vm, use = use, selected = cat, onSelect = { cat = it },
                        onPlate = items.mapNotNull { it.foodId }.groupingBy { it }.eachCount(),
                        onPreset = { pr -> sheet = SheetReq(QuantityFood.from(pr), null, -1, raw = pr.label, restaurant = pr.category == RESTAURANT) },
                        onSaved = { addSaved(it) },
                    )
                }
                if (items.isEmpty() && pending.isEmpty()) Spacer(Modifier.navigationBarsPadding())
            }
            Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            WaterToast(
                water = waterNotice, undone = waterUndone,
                onUndo = {
                    waterUndone = true
                    scope.launch { vm.undoWater() }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (toast != null) 56.dp else 12.dp),
            )
        }
        if (items.isNotEmpty() || pending.isNotEmpty() || existing != null) {
            Plate(
                items = items, pending = pending, notes = notes, fats = fats, presets = vm.presets, saving = saving,
                editing = existing != null, deleted = pendingDelete,
                onDelete = { startDelete() }, onUndoDelete = { deleteJob?.cancel(); deleteJob = null; pendingDelete = false },
                canFix = parsedText != null && items.isNotEmpty(),
                onFix = { fixOpen = true }, onRepeat = { repeatOpen = true },
                onQuantity = { idx ->
                    val it = items[idx]
                    val byServing = it.unit == "serving" && it.servings != null && it.servings > 0
                    // Re-offer the preset's household servings when the row came from one.
                    val preset = vm.presets.firstOrNull { pr -> pr.foodId == it.foodId && pr.category != "fat" } ?: vm.presets.firstOrNull { pr -> pr.foodId == it.foodId }
                    // v2.5: a parsed row with the server's default_count counts in pieces of grams / count.
                    val dc = it.defaultCount?.takeIf { n -> n > 0 && !byServing }
                    val servings = preset?.servings.orEmpty().ifEmpty {
                        when {
                            // v2.8: a saved row counts in the unit its name suggests ("Roti" → 1 roti), else "1 serving".
                            byServing -> listOf(com.sohum.bandlog.util.Counting.savedUnitOf(it) ?: com.sohum.bandlog.data.Serving("1 serving", it.grams / it.servings!!))
                            dc != null && it.grams > 0 -> listOf(com.sohum.bandlog.data.Serving("1 serving", it.grams / dc))
                            else -> emptyList()
                        }
                    }
                    val food = QuantityFood.from(it, servings).copy(defaultServing = preset?.defaultServing, category = preset?.category)
                    sheet = SheetReq(food, Quantity(QUnit.G, it.grams), idx)
                },
                onRemove = { idx -> items = items.filterIndexed { i, _ -> i != idx } },
                onCookedIn = { idx -> cookedFor = idx },
                onSave = { save() },
            )
        }
    }

    sheet?.let { req ->
        val adding = req.replace < 0
        QuantitySheet(
            food = req.food, initial = req.initial, title = if (adding) "How much?" else "Change the amount", cta = if (adding) "Add" else "Update",
            restaurantDefault = req.restaurant, startCount = req.startCount,
            onDismiss = { sheet = null },
            onDone = { item, _ ->
                sheet = null
                if (adding) { add(item, req.raw, amountLabel(item, vm.presets)); return@QuantitySheet }
                items = items.toMutableList().also { l ->
                    if (req.replace in l.indices) { val old = l[req.replace]; l[req.replace] = item.copy(cookedIn = item.cookedIn ?: old.cookedIn, source = old.source, foodId = old.foodId) }
                }
            },
        )
    }

    cookedFor?.let { idx ->
        val dish = items.getOrNull(idx)
        if (dish == null) cookedFor = null
        else BottomSheet(title = "Cooked in…", subtitle = "Adds the fat as its own row under ${dish.name}", onDismiss = { cookedFor = null }) {
            if (fats.isEmpty()) Text("The fats list didn't load — try again in a moment.", fontSize = 13.sp, color = p.muted)
            fats.forEachIndexed { i, fat ->
                if (i > 0) Hair()
                val tsp = fat.servings.firstOrNull { it.label.contains("1 tsp", ignoreCase = true) } ?: fat.servings.firstOrNull()
                val kcal = ((tsp?.grams ?: 5.0) * fat.calories / 100).roundToInt()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                        val food = QuantityFood.from(fat, tsp?.label)
                        val fatItem = food.item(if (tsp != null) Quantity(QUnit.SERVING, 1.0) else Quantity(QUnit.G, 5.0))
                        items = items.toMutableList().also { l -> l[idx] = l[idx].copy(cookedIn = fat.id); l.add(idx + 1, fatItem) }
                        rawParts += "${fat.label} (cooked in)"
                        cookedFor = null
                        say("Added · ${tsp?.label ?: "5 g"} ${fat.label.lowercase()} · $kcal kcal")
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DropIcon, null, tint = p.orange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(fat.label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                    Text("${tsp?.label ?: "5 g"} · $kcal kcal", fontSize = 13.sp, color = p.muted)
                }
            }
        }
    }

    if (pickDate) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = com.sohum.bandlog.util.Dates.parse(day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneOffset.UTC).toString()
                        if (d <= com.sohum.bandlog.util.Dates.today()) day = d else error = "That date hasn't happened yet"
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(state) }
    }

    if (fixOpen) {
        var fix by remember { mutableStateOf("") }
        var fixing by remember { mutableStateOf(false) }
        BottomSheet(
            title = "What's off?", subtitle = "Tell me and I'll redo the plate", onDismiss = { fixOpen = false },
            primary = if (fixing) "Redoing…" else "Redo", primaryEnabled = fix.isNotBlank() && !fixing,
            onPrimary = {
                scope.launch {
                    fixing = true
                    try {
                        val r = Api.parseMeal(parsedText ?: rawParts.joinToString(", "), fix.trim(), items)
                        items = r.items; notes = r.assumptions + r.unparsed.map { "Ignored: $it" }
                        fixOpen = false; say("Redone · ${r.items.sumOf { it.calories }.roundToInt()} kcal")
                    } catch (e: Exception) { error = e.message; fixOpen = false } finally { fixing = false }
                }
            },
        ) { SheetField(fix, { fix = it }, "e.g. it was two scoops, and the rice was raw") }
    }

    if (repeatOpen) {
        var name by remember { mutableStateOf("") }
        BottomSheet(
            title = "Save as a repeat meal", subtitle = "It shows up first, under Yours", onDismiss = { repeatOpen = false },
            primary = "Save", primaryEnabled = name.isNotBlank(),
            onPrimary = {
                val n = name.trim()
                scope.launch {
                    runCatching { Api.saveSavedMeal(n, items); vm.loadSavedMeals() }
                        .onSuccess { repeatOpen = false; say("Saved “$n” under Yours") }
                        .onFailure { error = it.message; repeatOpen = false }
                }
            },
        ) { SheetField(name, { name = it.take(40) }, "Name it, e.g. Dinner usual") }
    }
}

/** v2.8: 🍳 Breakfast · 🍛 Lunch · 🌙 Dinner · 🍿 Snacks — one row of four at the top of add / edit meal. */
@Composable
private fun MealTypeChips(selected: String, onSelect: (String) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MealTypes.ALL.forEach { t ->
            val sel = t.key == selected
            Column(
                Modifier.weight(1f).heightIn(min = 52.dp).pressable().background(if (sel) p.btn else p.card2, RoundedCornerShape(16.dp))
                    .selectable(selected = sel, onClick = { onSelect(t.key) }).padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(t.emoji, fontSize = 16.sp, maxLines = 1)
                Text(t.label, fontSize = 12.sp, fontWeight = if (sel) FontWeight(700) else FontWeight(600), color = if (sel) p.btnInk else p.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Added · 1 katori · 158 kcal": a black pill that rises over the plate for a moment. */
@Composable
private fun Toast(text: String?, modifier: Modifier) {
    val p = palette
    AnimatedVisibility(text != null, modifier, enter = slideInVertically { it / 2 } + fadeIn(), exit = fadeOut()) {
        Box(Modifier.shadow(10.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.btn, CircleShape).padding(16.dp, 10.dp)) {
            Text(text ?: "", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk, maxLines = 1)
        }
    }
}

/** "💧 +2 glasses to Water (500 mL) · Undo" — shown right after a dictated water phrase is logged. */
@Composable
private fun WaterToast(water: com.sohum.bandlog.data.ParsedWater?, undone: Boolean, onUndo: () -> Unit, modifier: Modifier) {
    val p = palette
    AnimatedVisibility(water != null, modifier, enter = slideInVertically { it / 2 } + fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.shadow(10.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.blueBg, CircleShape).padding(start = 14.dp, end = if (undone) 14.dp else 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val glasses = water?.glasses ?: 0.0
            val g = if (glasses % 1.0 == 0.0) glasses.toInt().toString() else String.format("%.1f", glasses)
            Text(
                if (undone) "💧 Undone" else "💧 +$g glass${if (glasses == 1.0) "" else "es"} to Water (${water?.ml ?: 0} mL)",
                fontSize = 13.sp, fontWeight = FontWeight(700), color = p.blue, maxLines = 1,
            )
            if (!undone) {
                Spacer(Modifier.width(8.dp))
                Text("Undo", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.blue, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, modifier = Modifier.clickable(onClick = onUndo).padding(8.dp))
            }
        }
    }
}

// ---- the bar ----

/** "Search or say what you ate…" with the mic inside (tap: listen, long-press: English ↔ Hindi). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodBar(text: String, onText: (String) -> Unit, dictation: DictationState, onMic: () -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    val p = palette
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.height(52.dp).shadow(10.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, shape).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SearchIcon, null, tint = p.muted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            text, onText, Modifier.weight(1f), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(
                    if (dictation.listening) "Listening in ${if (dictation.lang == "hi-IN") "Hindi" else "English"}…" else "Search or say what you ate…",
                    fontSize = 15.sp, color = p.muted, maxLines = 1,
                )
                inner()
            },
        )
        if (text.isNotEmpty()) Box(Modifier.size(44.dp).clickable { onText("") }, contentAlignment = Alignment.Center) {
            Icon(CrossIcon, "Clear", tint = p.muted, modifier = Modifier.size(13.dp))
        }
        // The language switch only shows while listening; otherwise hold the mic to switch.
        else if (dictation.listening) Box(Modifier.heightIn(min = 44.dp).clickable { dictation.toggleLang() }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(dictation.langLabel, fontSize = 11.sp, fontWeight = FontWeight(700), color = p.muted)
        }
        Box(
            Modifier.size(44.dp).background(if (dictation.listening) p.red else p.card2, CircleShape)
                .combinedClickable(onClick = onMic, onLongClick = { dictation.toggleLang() }),
            contentAlignment = Alignment.Center,
        ) { Icon(MicIcon, "Say what you ate", tint = if (dictation.listening) Color.White else p.ink, modifier = Modifier.size(18.dp)) }
    }
}

// ---- search results ----

@Composable
private fun SearchResults(q: String, sentence: Boolean, onWorkItOut: () -> Unit, onPick: (FoodHit) -> Unit) {
    val p = palette
    var hits by remember { mutableStateOf<List<FoodHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q) {
        if (q.length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(200)
        busy = true
        runCatching { Api.searchFoods(q) }.onSuccess { hits = it; err = null }.onFailure { err = it.message }
        busy = false
    }
    if (sentence) {
        PillButton("Work it out", onWorkItOut, height = 48.dp)
        Text("Counts every item you typed — or tap a match below.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp))
    }
    ErrorNote(err)
    if (!busy && hits.isEmpty() && err == null && !sentence) {
        Card {
            Text("Nothing matches “$q” in the food table.", fontSize = 14.sp, color = p.ink)
            Spacer(Modifier.height(10.dp))
            PillButton("Work it out", onWorkItOut, height = 46.dp)
        }
        return
    }
    if (busy && hits.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
    if (hits.isNotEmpty()) Card(padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            hits.forEachIndexed { i, h ->
                if (i > 0) Hair()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoodImage(h.name, h.imageUrl, kind = if (h.source == "off") "product" else "generic", size = 40.dp, foodId = h.id)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            if (h.nameHi != null) Text("  ${h.nameHi}", fontSize = 13.sp, fontWeight = FontWeight(500), color = p.muted, maxLines = 1)
                        }
                        val unit = h.units.firstOrNull()
                        Text(
                            (unit?.let { "${it.label} · ${(h.calories * it.grams / 100).roundToInt()} kcal" } ?: "100 g · ${h.calories.roundToInt()} kcal") + " · ${SOURCE_LABEL[h.source] ?: h.source}",
                            fontSize = 12.sp, color = p.muted, maxLines = 1,
                        )
                    }
                    AddDot(0)
                }
            }
        }
    }
}

/** The "+" at the end of a tappable food; a count once it's on the plate. */
@Composable
private fun AddDot(count: Int) {
    val p = palette
    Box(Modifier.padding(start = 8.dp).size(28.dp).background(if (count > 0) p.btn else p.card2, CircleShape), contentAlignment = Alignment.Center) {
        Text(if (count > 0) "×$count" else "+", fontSize = if (count > 0) 11.sp else 16.sp, fontWeight = FontWeight(800), color = if (count > 0) p.btnInk else p.ink)
    }
}

// ---- presets ----

@Composable
private fun PresetGrid(
    vm: AppViewModel,
    use: Map<String, Int>,
    selected: String?,
    onSelect: (String) -> Unit,
    onPlate: Map<String, Int>,
    onPreset: (FoodPreset) -> Unit,
    onSaved: (SavedMeal) -> Unit,
) {
    val p = palette
    val presets = vm.presets
    val top = remember(presets, use) {
        presets.filter { it.category != "fat" && (use[it.foodId] ?: 0) > 0 }.sortedByDescending { use[it.foodId] ?: 0 }.distinctBy { it.foodId }.take(8)
    }
    val hasYours = vm.savedMeals.isNotEmpty() || top.isNotEmpty()
    // Categories you eat from most come first; the rest keep their usual order.
    val cats = remember(presets, use) {
        CATEGORIES.withIndex().sortedWith(compareByDescending<IndexedValue<Pair<String, String>>> { (_, c) -> presets.filter { it.category == c.first }.sumOf { use[it.foodId] ?: 0 } }.thenBy { it.index }).map { it.value }
    }
    val chips = (if (hasYours) listOf(YOURS to "Yours") else emptyList()) + cats + (if (presets.any { it.category == RESTAURANT }) listOf(RESTAURANT to "Restaurant") else emptyList())
    val cat = selected?.takeIf { s -> chips.any { it.first == s } } ?: chips.first().first

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (key, label) -> Chip(label, key == cat, { onSelect(key) }) }
    }
    if (presets.isEmpty()) {
        Card {
            if (vm.presetsLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
                Spacer(Modifier.height(8.dp))
                Text("Loading the presets…", fontSize = 13.sp, color = p.muted)
            } else {
                Text("The presets didn't load.", fontSize = 14.sp, color = p.ink)
                Spacer(Modifier.height(10.dp))
                PillButton("Try again", { vm.loadPresets(force = true) }, height = 46.dp)
            }
        }
        return
    }
    if (cat == YOURS) {
        vm.savedMeals.chunked(2).forEach { row ->
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sm ->
                    FoodCard(Modifier.weight(1f).fillMaxHeight(), sm.name, "Saved meal · ${sm.calories.roundToInt()} kcal · ${fmt(sm.proteinG)} g P", 0, image = {
                        val big = sm.items.maxByOrNull { it.calories }
                        FoodImage(sm.pictureName, sm.imageUrl ?: big?.imageUrl, kind = "generic", size = 44.dp, foodId = big?.foodId)
                    }) { onSaved(sm) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        PresetRows(top, onPlate, onPreset)
    } else {
        val list = remember(presets, cat, use) { presets.filter { it.category == cat }.sortedWith(compareByDescending<FoodPreset> { use[it.foodId] ?: 0 }.thenBy { it.sort }) }
        PresetRows(list, onPlate, onPreset)
    }
}

@Composable
private fun PresetRows(list: List<FoodPreset>, onPlate: Map<String, Int>, onPreset: (FoodPreset) -> Unit) {
    list.chunked(2).forEach { row ->
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { pr ->
                // v2.5: the tile shows what one tap starts at — "1 roti · 119 kcal", not the preset's "2 roti".
                val cu = remember(pr) { com.sohum.bandlog.util.Counting.unitFor(pr.servings, pr.defaultServing) }
                FoodCard(
                    Modifier.weight(1f).fillMaxHeight(), pr.label,
                    (cu?.let {
                        val g = it.grams * it.defaultCount
                        (if (it.label != null) it.label else "${fmt(it.defaultCount)} ${it.noun}") + " · ${(pr.calories * g / 100).roundToInt()} kcal"
                    } ?: "100 g · ${pr.calories.roundToInt()} kcal"),
                    onPlate[pr.foodId] ?: 0,
                    image = { FoodImage(pr.label, pr.imageUrl, kind = "preset", size = 44.dp, foodId = pr.foodId, emoji = pr.icon?.takeIf { i -> i.any { c -> c.code > 0x2000 } }, fallback = presetIcon(pr.category)) },
                    hint = pr.labelHi,
                ) { onPreset(pr) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * v2.5: a stacked tile (picture + add dot on top, then the name, the Hindi name and "1 roti · 106
 * kcal" each on their own line) so two columns hold at fontScale 1.3 on a 360 dp phone instead of
 * squeezing the text between the picture and the dot.
 */
@Composable
private fun FoodCard(modifier: Modifier, title: String, sub: String, count: Int, image: (@Composable () -> Unit)? = null, hint: String? = null, onClick: () -> Unit) {
    val p = palette
    Card(modifier, padding = 12.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (image != null) image()
            Spacer(Modifier.weight(1f))
            AddDot(count)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (hint != null) Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(sub, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink.copy(alpha = 0.75f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** The tile a preset falls back to while (or if) its picture does not load. */
private fun presetIcon(category: String) = when (category) {
    "drink" -> com.sohum.bandlog.ui.components.GlassIcon
    "fat" -> DropIcon
    else -> com.sohum.bandlog.ui.components.BowlIcon
}

// ---- the plate ----

@Composable
private fun Plate(
    items: List<MealItem>,
    pending: List<Pending>,
    notes: List<String>,
    fats: List<FoodPreset>,
    presets: List<FoodPreset>,
    saving: Boolean,
    editing: Boolean,
    deleted: Boolean,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    canFix: Boolean,
    onFix: () -> Unit,
    onRepeat: () -> Unit,
    onQuantity: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onCookedIn: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val p = palette
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Column(
        Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, shape)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp).navigationBarsPadding().imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your plate", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink)
            Text(if (items.isEmpty()) "" else "  ·  ${items.size} item${if (items.size == 1) "" else "s"}", fontSize = 13.sp, color = p.muted, modifier = Modifier.weight(1f))
            if (canFix) TextLink("Fix", onFix)
            if (items.isNotEmpty()) TextLink("Save as repeat", onRepeat)
        }
        Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
            pending.forEach { PendingPlateRow(it.label) }
            items.forEachIndexed { idx, it ->
                if (idx > 0 || pending.isNotEmpty()) Hair()
                val isFat = fats.any { f -> f.id == it.cookedIn || f.foodId == it.foodId }
                PlateRow(
                    item = it,
                    amount = amountLabel(it, presets),
                    cookedInLabel = it.cookedIn?.let { id -> if (id == "restaurant") "Restaurant portion · oil included" else "Cooked in ${fats.firstOrNull { f -> f.id == id }?.label ?: id}" },
                    showCookedIn = it.cookedIn == null && it.source != "scan" && !isFat && fats.isNotEmpty() && QuantityFood.wantsCookedIn(it.name),
                    onCookedIn = { onCookedIn(idx) }, onQuantity = { onQuantity(idx) }, onRemove = { onRemove(idx) },
                )
            }
        }
        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), fontSize = 11.sp, color = p.muted, maxLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (editing && items.isEmpty() && pending.isEmpty()) Text("Nothing on the plate. Add something, or delete the meal.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(vertical = 10.dp))
        // v2.8 meal editor: Delete lives here, with the v2.7 undo.
        if (editing) {
            if (deleted) Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 44.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Deleted", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.weight(1f))
                Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onUndoDelete).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("Undo", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.btn)
                }
            } else Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.heightIn(min = 44.dp).background(p.redBg, CircleShape).clickable(enabled = !saving, onClick = onDelete).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Delete, null, tint = p.red, modifier = Modifier.size(16.dp))
                    Text(" Delete", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.red)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${items.sumOf { it.calories }.roundToInt()} kcal", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                Text("${fmt((items.sumOf { it.proteinG } * 10).roundToInt() / 10.0)} g protein", fontSize = 12.sp, color = p.muted)
            }
            Spacer(Modifier.width(12.dp))
            PillButton(
                when {
                    saving -> "Saving…"
                    editing && pending.isNotEmpty() -> "Working it out…"
                    editing -> "Save changes"
                    pending.isNotEmpty() -> "Save"
                    else -> "Save · ${items.size} item${if (items.size == 1) "" else "s"}"
                },
                onSave, Modifier.weight(1f), enabled = !saving && !deleted && (!editing || (items.isNotEmpty() && pending.isEmpty())),
            )
        }
    }
}

@Composable
private fun TextLink(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(700), color = palette.ink)
    }
}

@Composable
private fun PendingPlateRow(label: String) {
    val p = palette
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
            Text("Working it out… Save any time, it'll finish on its own.", fontSize = 11.sp, color = p.muted, maxLines = 1)
        }
    }
}

@Composable
private fun PlateRow(item: MealItem, amount: String, cookedInLabel: String?, showCookedIn: Boolean, onCookedIn: () -> Unit, onQuantity: () -> Unit, onRemove: () -> Unit) {
    val p = palette
    // Delta badge: "+99 kcal" for a moment after the amount changes.
    var last by remember { mutableStateOf(item.calories) }
    var delta by remember { mutableIntStateOf(0) }
    var showDelta by remember { mutableStateOf(false) }
    LaunchedEffect(item.calories) {
        val d = (item.calories - last).toInt(); last = item.calories
        if (d != 0) { delta = d; showDelta = true; delay(1400); showDelta = false }
    }
    // v2.5: two lines so nothing collides at fontScale 1.3 — [pic] name … [2 roti] [×], then kcal + macros (wrapping as whole chips).
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoodImage(item.name, item.imageUrl, kind = FoodImages.kindFor(item.source), size = 38.dp, foodId = item.foodId)
            Spacer(Modifier.width(10.dp))
            Text(
                item.name + if (item.source == "estimated") " ~" else "", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            // The amount opens the Quantity sheet.
            Box(Modifier.heightIn(min = 44.dp).padding(start = 6.dp).clickable(onClick = onQuantity), contentAlignment = Alignment.Center) {
                Box(Modifier.heightIn(min = 34.dp).widthIn(max = 120.dp).background(p.card2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(amount, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(44.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(CrossIcon, "Remove ${item.name}", tint = p.muted, modifier = Modifier.size(13.dp))
            }
        }
        Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Text("${item.calories.roundToInt()} kcal", fontSize = 12.sp, color = p.muted, maxLines = 1)
                MacroDot("${fmt(item.proteinG)}g", p.red); MacroDot("${fmt(item.carbsG)}g", p.orange); MacroDot("${fmt(item.fatG)}g", p.blue)
                AnimatedVisibility(showDelta, enter = scaleIn() + fadeIn(), exit = fadeOut() + scaleOut()) {
                    Box(Modifier.background(if (delta > 0) p.btn else p.card2, CircleShape).padding(8.dp, 1.dp)) {
                        Text((if (delta > 0) "+" else "") + "$delta kcal", fontSize = 11.sp, fontWeight = FontWeight(700), color = if (delta > 0) p.btnInk else p.ink, maxLines = 1)
                    }
                }
            }
            if (cookedInLabel != null) Text(cookedInLabel, fontSize = 11.sp, color = p.muted)
            else if (showCookedIn) Row(
                Modifier.heightIn(min = 44.dp).clickable(onClick = onCookedIn),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DropIcon, null, tint = p.orange, modifier = Modifier.size(12.dp))
                Text(" Cooked in…", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
            }
        }
    }
}

/**
 * "1 katori", "2 roti", "1.5 cup" when the row is counted in a preset's household serving; else the
 * grams. v2.5: counts in single pieces ("2 roti", not "1 2 roti").
 */
private fun amountLabel(item: MealItem, presets: List<FoodPreset>): String {
    val n = item.servings
    if (item.unit != "serving" || n == null || n <= 0) return item.quantityLabel
    val pr = presets.firstOrNull { it.foodId == item.foodId && it.category != "fat" } ?: presets.firstOrNull { it.foodId == item.foodId } ?: return item.quantityLabel
    val cu = com.sohum.bandlog.util.Counting.unitFor(pr.servings, pr.defaultServing) ?: return item.quantityLabel
    val count = (item.grams / cu.grams * 2).roundToInt() / 2.0
    return if (cu.label != null) "${fmt(count)} × ${cu.label}" else "${fmt(count)} ${cu.noun}"
}

/** A one-line text field on the grey fill, for the plate's sheets. */
@Composable
internal fun SheetField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val p = palette
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(14.dp, 12.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = p.muted, maxLines = 1); inner() },
        )
    }
}
