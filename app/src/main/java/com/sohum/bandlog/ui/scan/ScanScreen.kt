package com.sohum.bandlog.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.LabelReport
import com.sohum.bandlog.data.PlateEstimate
import com.sohum.bandlog.data.PlateItem
import com.sohum.bandlog.data.ScanHistoryItem
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BarcodeIcon
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.ChevronDownIcon
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.HistoryIcon
import com.sohum.bandlog.ui.components.MacroDonut
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.QuantitySheet
import com.sohum.bandlog.ui.components.QuestionIcon
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScanIcon
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.Segmented
import com.sohum.bandlog.ui.components.SpoonIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.QuantityFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Under this many characters the phone's read is treated as a miss and the photo goes up too. */
private const val OCR_MIN_CHARS = 120

private val LENSES = listOf("protein" to "Protein", "snack" to "Snack", "cutting" to "Cutting", "bulking" to "Bulking")


/** Bottom-nav tab: title, the three-mode scanner, and the History list. A tapped row opens its stored report. */
@Composable
fun ScanTab(vm: AppViewModel) {
    val p = palette
    var history by remember { mutableStateOf<List<ScanHistoryItem>>(emptyList()) }
    var historyTick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<ScanHistoryItem?>(null) }
    LaunchedEffect(historyTick) { history = runCatching { Api.scanHistory() }.getOrDefault(history) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
                ScreenTitle("Scan")
                Text("Label, barcode, or a photo of your plate", fontSize = 13.sp, color = p.muted)
            }
            Box(Modifier.weight(1f).padding(bottom = 96.dp)) {
                ScanForm(vm, history, onScanned = { historyTick++ }, onOpen = { open = it }, onDelete = { item ->
                    history = history.filter { it.id != item.id }
                    vm.launch { runCatching { Api.deleteScan(item.id) } }
                })
            }
        }
        AnimatedContent(
            targetState = open, label = "scan-detail",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { item ->
            if (item != null) {
                BackHandler { open = null }
                ScanDetailPage(item, onLogged = { vm.refresh() }) { open = null }
            }
        }
    }
}

/**
 * Three ways in: Label (ML Kit OCR → text → report), Barcode (ML Kit barcode → Open Food Facts →
 * the same report) and Food photo (Sonnet vision → per-item estimate you can edit and save as a meal).
 */
@Composable
private fun ScanForm(
    vm: AppViewModel,
    history: List<ScanHistoryItem>,
    onScanned: () -> Unit,
    onOpen: (ScanHistoryItem) -> Unit,
    onDelete: (ScanHistoryItem) -> Unit,
) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var mode by remember { mutableIntStateOf(0) } // 0 label · 1 barcode · 2 food photo
    // Profile → Preferences → "Judge scans for" decides where the lens starts.
    var lens by remember(vm.profile.initialLens) { mutableStateOf(vm.profile.initialLens) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var ocr by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<LabelReport?>(null) }
    var plate by remember { mutableStateOf<PlateEstimate?>(null) }
    var notFound by remember { mutableStateOf(false) }
    val target = remember { mutableStateOf<Uri?>(null) }

    fun clearResults() { report = null; plate = null; notFound = false; error = null }

    /** New photo → clear the old results and run the on-device reader that fits the mode. */
    fun accept(bitmap: Bitmap?) {
        photo = bitmap; ocr = ""; clearResults()
        val bmp = bitmap ?: return
        when (mode) {
            0 -> scope.launch {
                reading = true
                ocr = runCatching { Ocr.read(bmp) }.getOrDefault("")
                reading = false
                showText = ocr.isNotBlank() || ocr.length < OCR_MIN_CHARS
            }
            1 -> scope.launch {
                reading = true
                val code = runCatching { Barcode.read(bmp) }.getOrNull()
                reading = false
                if (code != null) barcode = code else error = "No barcode found — get closer, or type the digits below."
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) target.value?.let { uri -> scope.launch { accept(withContext(Dispatchers.IO) { decodeScaled(ctx, uri, if (mode == 2) 1600 else 2200) }) } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { accept(withContext(Dispatchers.IO) { decodeScaled(ctx, uri, if (mode == 2) 1600 else 2200) }) }
    }

    fun openCamera() {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        val f = File(dir, "capture.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        target.value = uri
        runCatching { camera.launch(uri) }.onFailure { error = "No camera app found — pick from gallery instead." }
    }

    fun analyse() {
        val bmp = photo
        scope.launch {
            busy = true; clearResults()
            try {
                when (mode) {
                    0 -> {
                        bmp ?: return@launch
                        val text = ocr.trim()
                        val b64 = if (text.length < OCR_MIN_CHARS) withContext(Dispatchers.IO) { toJpegBase64(bmp, 88) } else null
                        report = Api.scanLabel(text, note, b64, lens)
                    }
                    1 -> {
                        val r = Api.scanBarcode(barcode.filter(Char::isDigit), lens, note)
                        if (r == null) notFound = true else report = r
                    }
                    else -> {
                        bmp ?: return@launch
                        plate = Api.photoMeal(withContext(Dispatchers.IO) { toJpegBase64(bmp, 85) }, note)
                    }
                }
                onScanned()
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    val canAnalyse = when (mode) { 1 -> barcode.filter(Char::isDigit).length >= 8; else -> photo != null }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Rise(0) {
                Segmented(listOf("Label", "Barcode", "Food photo"), mode, { i -> mode = i; photo = null; ocr = ""; barcode = ""; clearResults() })
            }
            Rise(1) {
                Card {
                    val (icon, title, sub) = when (mode) {
                        0 -> Triple(ScanIcon, "Scan an ingredients label", "What it is, how it fits your goal, and whether to trust the pack")
                        1 -> Triple(BarcodeIcon, "Scan the barcode", "EAN under the bars — looked up on Open Food Facts")
                        else -> Triple(CameraIcon, "Photograph your plate", "Every item with grams, calories, macros and micros")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = p.btnInk, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                            Text(sub, fontSize = 12.sp, color = p.muted)
                        }
                    }
                    if (mode != 2) {
                        Spacer(Modifier.height(12.dp))
                        Text("Judge it for", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        LensSwitch(lens) { lens = it }
                    }
                    Spacer(Modifier.height(12.dp))
                    val bmp = photo
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(if (bmp == null) "Take photo" else "Retake", { openCamera() }, Modifier.weight(1f), height = 46.dp)
                        PillButton("Gallery", { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), height = 46.dp, bg = p.card2, fg = p.ink)
                    }
                    if (mode == 1) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            BasicTextField(
                                barcode, { barcode = it.filter(Char::isDigit).take(14) }, Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, letterSpacing = 1.sp), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (barcode.isEmpty()) Text(if (reading) "Reading the barcode…" else "Or type the digits, e.g. 8901058851298", fontSize = 14.sp, color = p.muted); inner() },
                            )
                        }
                    }
                    if (mode == 0 && bmp != null) {
                        Spacer(Modifier.height(10.dp))
                        WhatIRead(ocr, reading, showText, { showText = !showText }) { ocr = it }
                    }
                    if (canAnalyse) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            BasicTextField(
                                note, { note = it }, Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (note.isEmpty()) Text(if (mode == 2) "Optional: e.g. the dal has ghee, two rotis" else "Optional: what is it / what do you want to know", fontSize = 14.sp, color = p.muted); inner() },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val label = when { busy && mode == 2 -> "Looking at the plate…"; busy -> "Analysing…"; mode == 2 -> "Estimate"; mode == 1 -> "Look it up"; else -> "Analyse" }
                        PillButton(label, enabled = !busy && !reading, height = 48.dp, onClick = { analyse() })
                        if (busy) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
                            Text(
                                when (mode) {
                                    2 -> "Identifying each item, then checking the food table. 10–20 s."
                                    1 -> "Looking it up, then writing your report. About 10 seconds."
                                    else -> if (ocr.trim().length < OCR_MIN_CHARS) "Your phone couldn't read enough, so the photo is going up too — 20–45 s." else "Checking the brand and writing your report. About 8 seconds."
                                },
                                fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            ErrorNote(error)
            if (notFound) Rise(2) {
                Card {
                    Text("Not in the database yet", fontWeight = FontWeight(700), fontSize = 15.sp, color = p.ink)
                    Text("Open Food Facts doesn't know this barcode. Scan the label instead — it reads the pack itself.", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Scan the label", { mode = 0; photo = null; clearResults() }, height = 44.dp, bg = p.card2, fg = p.ink)
                }
            }
            report?.let { ReportView(it, lens, onLogged = { vm.refresh() }) }
            plate?.let { est ->
                PhotoReview(est, photo, readOnly = false) { items, path ->
                    scope.launch {
                        busy = true
                        val photoPath = path ?: photo?.let { b -> runCatching { Api.uploadMealPhoto(withContext(Dispatchers.IO) { toJpegBytes(b, 85) }) }.getOrNull() }
                        val ok = vm.saveMeal(Dates.today(), est.plateNote.ifBlank { items.joinToString(", ") { it.name } }, items.map { it.toMealItem() }, photoPath)
                        busy = false
                        if (ok) { plate = null; photo = null } else error = vm.error
                    }
                }
            }
            HistoryList(history, onOpen, onDelete)
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

/** Protein · Snack · Cutting · Bulking. */
@Composable
private fun LensSwitch(value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LENSES.forEach { (key, label) -> Chip(label, key == value, { onChange(key) }, Modifier.weight(1f)) }
    }
}

/** The collapsible "What I read" card — editable, so a misread digit can be fixed before analysing. */
@Composable
private fun WhatIRead(text: String, reading: Boolean, expanded: Boolean, onToggle: () -> Unit, onChange: (String) -> Unit) {
    val p = palette
    val rot by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatialFast(), label = "chev")
    val short = text.trim().length < OCR_MIN_CHARS
    Column(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("What I read", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(
                    when {
                        reading -> "Reading the label on your phone…"
                        text.isBlank() -> "Nothing readable — we'll send the photo instead"
                        short -> "Only ${text.trim().length} characters — the photo will go up too"
                        else -> "${text.trim().length} characters · tap to fix anything wrong"
                    },
                    fontSize = 11.sp, color = if (short && !reading) p.orange else p.muted,
                )
            }
            Icon(ChevronDownIcon, null, tint = p.muted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rot })
        }
        AnimatedVisibility(expanded && !reading) {
            Column {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 260.dp).background(p.card, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    val focus = LocalFocusManager.current
                    BasicTextField(
                        text, onChange, Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        textStyle = TextStyle(fontSize = 12.sp, color = p.ink, lineHeight = 17.sp), cursorBrush = SolidColor(p.ink),
                        decorationBox = { inner -> if (text.isEmpty()) Text("Nothing was recognised. Type the label here, or just hit Analyse and we'll read the photo on the server.", fontSize = 12.sp, color = p.muted); inner() },
                    )
                }
            }
        }
    }
}

// ---- the report, as an infographic ----

/** great = green, ok = orange, weak = grey. Never red: a weak fit is a description, not a warning. */
@Composable
private fun fitColor(verdict: String): Color = when (verdict) { "great" -> palette.green; "ok" -> palette.orange; else -> palette.muted }
private fun fitLabel(verdict: String) = when (verdict) { "great" -> "Great fit"; "ok" -> "Fine"; else -> "Weak fit" }
private fun eatLabel(verdict: String) = when (verdict) { "great" -> "Eat it"; "ok" -> "Sometimes"; else -> "Skip for this" }

@Composable
fun ReportView(r: LabelReport, initialLens: String = r.lens, onLogged: () -> Unit = {}) {
    val p = palette
    if (!r.readable) {
        Rise(1) { Card { Text("Couldn't read that as a food label", fontWeight = FontWeight(700), color = p.ink); Text(r.verdictReason, fontSize = 13.sp, color = p.muted) } }
        return
    }
    val scope = rememberCoroutineScope()
    var lens by remember(r) { mutableStateOf(if (r.fits.containsKey(initialLens)) initialLens else r.lens) }
    val g = r.infographic
    val fit = r.fits[lens]
    val score = g.score
    val scoreColor = when { score >= 7 -> p.green; score >= 4 -> p.orange; else -> p.muted }
    // "Log 1 serving": the report as a food the Quantity sheet can price.
    val food = remember(r) { QuantityFood.from(r) }
    var logFood by remember(r) { mutableStateOf<QuantityFood?>(null) }
    var logged by remember(r) { mutableStateOf<String?>(null) }
    var logError by remember(r) { mutableStateOf<String?>(null) }

    // 1. What it is: product, the neutral description, the score for the report's lens.
    Rise(1) {
        Card(padding = 18.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (r.imageUrl != null) { RemoteImage(url = r.imageUrl, size = 64.dp, radius = 12.dp, fallback = BarcodeIcon); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    // Long product names wrap (no maxLines) and never push the ring off the card.
                    Text(r.product.ifBlank { "Unknown product" }, fontSize = 19.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, lineHeight = 23.sp, softWrap = true)
                    if (g.oneLiner.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(g.oneLiner, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Ring(score / 10f, scoreColor, 72.dp, 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", fontSize = 24.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 26.sp)
                        Text("/ 10", fontSize = 10.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
            }
            if (r.whatItIs.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(r.whatItIs, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
            }
            if (food != null) {
                Spacer(Modifier.height(14.dp))
                PillButton("Log 1 serving" + (r.servingG?.takeIf { it > 0 }?.let { " · ${it.roundToInt()} g" } ?: ""), { logFood = food }, height = 44.dp)
                logged?.let { Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(CheckIcon, null, tint = p.green, modifier = Modifier.size(14.dp)); Text("  $it — on Home", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.green) } }
                logError?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 12.sp, color = p.red) }
            }
        }
    }

    // 1b. One serving as a donut: P / C / F share of its calories.
    if (food != null && (food.proteinG + food.carbsG + food.fatG) > 0) Rise(1) {
        Card {
            Text("One serving", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Text(if (r.servingG != null && r.servingG > 0) "${r.servingG.roundToInt()} g · where the calories come from" else "Per 100 g · where the calories come from", fontSize = 12.sp, color = p.muted)
            Spacer(Modifier.height(12.dp))
            val k = (r.servingG?.takeIf { it > 0 } ?: 100.0) / 100.0
            val pr = food.proteinG * k; val cb = food.carbsG * k; val ft = food.fatG * k
            val kcalSum = pr * 4 + cb * 4 + ft * 9
            Row(verticalAlignment = Alignment.CenterVertically) {
                MacroDonut(pr, cb, ft) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(food.calories * k).roundToInt()}", fontSize = 18.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, lineHeight = 20.sp)
                        Text("kcal", fontSize = 10.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Triple("Protein", pr * 4, p.red to pr), Triple("Carbs", cb * 4, p.orange to cb), Triple("Fat", ft * 9, p.blue to ft)).forEach { (label, kc, cg) ->
                        MacroDot("$label ${if (kcalSum > 0) (kc / kcalSum * 100).roundToInt() else 0}% · ${fmt((cg.second * 10).roundToInt() / 10.0)} g", cg.first)
                    }
                }
            }
        }
    }

    // 2. How it fits: the lens switcher and that lens's card, with the per-lens "eat it" pill.
    if (r.fits.isNotEmpty()) Rise(2) {
        Card {
            Text("How it fits", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(10.dp))
            LensSwitch(lens) { lens = it }
            if (fit != null) {
                val c = fitColor(fit.verdict)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().background(c.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.background(c.copy(alpha = 0.18f), CircleShape).padding(12.dp, 6.dp)) {
                            Text(fitLabel(fit.verdict), fontSize = 12.sp, fontWeight = FontWeight(800), color = c)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.background(c, CircleShape).padding(12.dp, 6.dp)) {
                            Text(eatLabel(fit.verdict), fontSize = 12.sp, fontWeight = FontWeight(800), color = if (fit.verdict == "weak") p.card else Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(fit.why, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
                }
            }
        }
    }

    // 3. Trust meter (safety and honesty only).
    Rise(3) { TrustMeter(r.verdict, r.verdictReason) }

    // 4. One serving against the day.
    if (g.caloriesPct + g.proteinPct + g.carbsPct + g.fatPct > 0) Rise(4) {
        Card {
            Text("One serving = …", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("…this much of your whole day.", fontSize = 12.sp, color = p.muted)
            Spacer(Modifier.height(12.dp))
            ShareRow("Calories", g.caloriesPct, p.ink)
            ShareRow("Protein", g.proteinPct, p.red)
            ShareRow("Carbs", g.carbsPct, p.orange)
            ShareRow("Fat", g.fatPct, p.blue)
        }
    }

    // 5. Sugar, counted in teaspoons.
    val spoons = g.sugarTsp.roundToInt()
    if (g.sugarTsp > 0.04) Rise(5) {
        Card {
            Text("Sugar", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(spoons.coerceIn(0, 12)) { Icon(SpoonIcon, null, tint = p.orange, modifier = Modifier.size(19.dp).padding(end = 2.dp)) }
                if (spoons > 12) Text("+", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.orange)
                if (spoons == 0) Text("under half a spoon", fontSize = 13.sp, color = p.muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (spoons == 0) "Less than 1 tsp of sugar per serving" else "$spoons tsp of sugar per serving", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
        }
    }

    // 6. Salt.
    if (g.sodiumPct > 0) Rise(6) {
        Card {
            val c = if (g.sodiumPct >= 40) p.red else if (g.sodiumPct >= 20) p.orange else p.green
            RowSpaceBetween {
                Text("Salt", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("${g.sodiumPct}%", fontSize = 15.sp, fontWeight = FontWeight(800), color = c)
            }
            Spacer(Modifier.height(10.dp))
            FillBar(g.sodiumPct / 100f, c)
            Spacer(Modifier.height(8.dp))
            Text("% of a day's salt", fontSize = 12.sp, color = p.muted)
        }
    }

    // 7. Protein.
    Rise(7) {
        Card {
            val pc = when (r.proteinRating) { "excellent", "good" -> p.green; "average" -> p.orange; else -> p.muted }
            val gauge = when (r.proteinRating) { "excellent" -> 1f; "good" -> 0.75f; "average" -> 0.45f; else -> 0.15f }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(gauge, pc, 64.dp, 8.dp) { Text(r.proteinPerServing?.let { "${it.roundToInt()}g" } ?: "—", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Protein · ${r.proteinRating.replaceFirstChar { it.uppercase() }}", fontSize = 15.sp, fontWeight = FontWeight(700), color = pc)
                    if (r.proteinQuality.isNotBlank()) Text(r.proteinQuality, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (r.proteinNote.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(r.proteinNote, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp) }
        }
    }

    // 8. Ingredients as chips.
    if (r.concerns.isNotEmpty()) Rise(8) {
        Card {
            Text("Ingredients", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            r.concerns.forEach { (ing, issue, sev) ->
                val c = when (sev) { "high" -> p.red; "medium" -> p.orange; else -> p.green }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.background(c.copy(alpha = 0.16f), CircleShape).padding(12.dp, 6.dp)) { Text(ing, fontSize = 13.sp, fontWeight = FontWeight(700), color = c) }
                Spacer(Modifier.height(4.dp))
                Text(issue, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
            }
        }
    }

    // 9. Claims.
    if (r.claims.isNotEmpty()) Rise(9) {
        Card {
            Text("Claims on the pack", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            r.claims.forEach { (claim, status, why) ->
                Spacer(Modifier.height(10.dp))
                val (c, icon) = when (status) { "supported" -> p.green to CheckIcon; "misleading", "false" -> p.red to CrossIcon; else -> p.orange to QuestionIcon }
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).background(c.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c, modifier = Modifier.size(13.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("“$claim”", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, lineHeight = 19.sp)
                        Text(why, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                    }
                }
            }
        }
    }

    // 10. For you / better options / the web.
    if (r.suggestions.isNotEmpty()) Rise(10) {
        Column(Modifier.fillMaxWidth().background(p.btn, RoundedCornerShape(20.dp)).padding(18.dp)) {
            Text("For you · ${LENSES.firstOrNull { it.first == r.lens }?.second ?: r.lens}", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk)
            r.suggestions.forEach {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) { Text("•  ", color = p.btnInk.copy(alpha = 0.6f), fontSize = 14.sp); Text(it, fontSize = 14.sp, color = p.btnInk, lineHeight = 20.sp) }
            }
        }
    }
    if (r.alternatives.isNotEmpty()) Rise(11) {
        Card {
            Text("Better options", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(10.dp))
            r.alternatives.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { alt -> Box(Modifier.weight(1f).background(p.card2, CircleShape).padding(12.dp, 9.dp)) { Text(alt, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 2) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (r.research.isNotEmpty()) Rise(12) { Collapsible("What the web says", r.research) }

    logFood?.let { f ->
        QuantitySheet(
            food = f, title = "Log from this scan", cta = "Log",
            onDismiss = { logFood = null },
            onDone = { item, _ ->
                logFood = null
                scope.launch {
                    runCatching { Api.saveMeal(Dates.today(), "${r.product.ifBlank { "Scanned product" }} (scan)", listOf(item)) }
                        .onSuccess { logged = "Logged ${item.quantityLabel} · ${item.calories.roundToInt()} kcal"; logError = null; onLogged() }
                        .onFailure { logError = it.message }
                }
            },
        )
    }
}

/** Safe · Caution · Unsafe · Misleading · Fake, with the one that applies filled in. Safety and honesty only. */
@Composable
private fun TrustMeter(verdict: String, reason: String) {
    val p = palette
    val segments = listOf("safe" to p.green, "caution" to p.orange, "unsafe" to p.red, "misleading" to p.purple, "fake" to p.red)
    val active = segments.indexOfFirst { it.first == verdict }
    Card {
        Text("Trust", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
        Text("Is the pack honest and safe? Not about taste or macros.", fontSize = 11.sp, color = p.muted)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            segments.forEachIndexed { i, (_, c) -> Box(Modifier.weight(1f).height(10.dp).background(if (i == active) c else p.track, CircleShape)) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            segments.forEachIndexed { i, (label, c) ->
                Text(label.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 9.sp, fontWeight = if (i == active) FontWeight(800) else FontWeight(500), color = if (i == active) c else p.muted)
            }
        }
        if (reason.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(reason, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp) }
    }
}

/** One macro's share of the day: name, bar, percentage. */
@Composable
private fun ShareRow(label: String, pct: Int, color: Color) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(66.dp), fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
        FillBar(pct / 100f, color, Modifier.weight(1f))
        Text("$pct%", Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
    }
}

/** A rounded bar that grows from zero on first draw. */
@Composable
private fun FillBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val p = palette
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, Motion.spatialSlow(), label = "bar")
    Box(modifier.fillMaxWidth().height(height).background(p.track, CircleShape)) { Box(Modifier.fillMaxWidth(a).height(height).background(color, CircleShape)) }
}

/** Collapsed-by-default bullet card (the web research). */
@Composable
private fun Collapsible(title: String, lines: List<String>) {
    val p = palette
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, Motion.spatialFast(), label = "chev")
    Card {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("${lines.size}", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted)
            Spacer(Modifier.width(8.dp))
            Icon(ChevronDownIcon, null, tint = p.muted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rot })
        }
        AnimatedVisibility(open) {
            Column { lines.forEach { Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.Top) { Text("•  ", color = p.muted, fontSize = 14.sp); Text(it, fontSize = 14.sp, color = p.muted, lineHeight = 20.sp) } } }
        }
    }
}

// ---- the plate estimate ----

private val MICRO_LABELS = listOf(
    "fiber_g" to ("Fibre" to "g"), "sugar_g" to ("Sugar" to "g"), "sodium_mg" to ("Sodium" to "mg"), "iron_mg" to ("Iron" to "mg"),
    "calcium_mg" to ("Calcium" to "mg"), "vitamin_c_mg" to ("Vit C" to "mg"), "potassium_mg" to ("Potassium" to "mg"),
    "vitamin_a_ug" to ("Vit A" to "µg"), "magnesium_mg" to ("Magnesium" to "mg"), "zinc_mg" to ("Zinc" to "mg"),
)

/**
 * The Cal AI-style review: each item with editable grams, calories, P/C/F, a Micros expander and a
 * confidence chip. [onSave] gets the edited items plus the server-stored photo path (if any).
 */
@Composable
fun PhotoReview(est: PlateEstimate, photo: Bitmap?, readOnly: Boolean, onSave: ((List<PlateItem>, String?) -> Unit)? = null) {
    val p = palette
    var items by remember(est) { mutableStateOf(est.items) }
    var open by remember(est) { mutableStateOf<Int?>(null) }
    if (est.items.isEmpty()) {
        Rise(1) { Card { Text("Couldn't find food in that photo", fontWeight = FontWeight(700), color = p.ink); Text(est.plateNote, fontSize = 13.sp, color = p.muted) } }
        return
    }
    Rise(1) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) { Image(photo.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)) }
                else if (est.photoUrl != null) { RemoteImage(url = est.photoUrl, size = 56.dp, fallback = CameraIcon); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(est.plateNote.ifBlank { "Your plate" }, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, lineHeight = 20.sp)
                    Text("This is an estimate — edit anything.", fontSize = 12.sp, color = p.muted)
                    if (est.portionHint == "restaurant") {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.background(p.orangeBg, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("Restaurant portion · ×1.4 + hidden oil", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.orange)
                        }
                    }
                }
            }
        }
    }
    Rise(2) {
        Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                items.forEachIndexed { idx, it ->
                    if (idx > 0) Hair()
                    val (cc, cl) = when (it.confidence) { "high" -> p.green to "High"; "medium" -> p.orange to "Med"; else -> p.muted to "Low" }
                    val micros = MICRO_LABELS.filter { (k, _) -> it.micros[k] != null }
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(it.name + if (it.source == "estimated") " ~" else "", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.background(cc.copy(alpha = 0.16f), CircleShape).padding(7.dp, 2.dp)) { Text(cl, fontSize = 10.sp, fontWeight = FontWeight(700), color = cc) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${it.calories.toInt()} kcal", fontSize = 12.sp, color = p.muted)
                                MacroDot("${fmt(it.proteinG)}g", p.red); MacroDot("${fmt(it.carbsG)}g", p.orange); MacroDot("${fmt(it.fatG)}g", p.blue)
                                if (micros.isNotEmpty()) Text(if (open == idx) "Hide" else "Micros", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.clickable { open = if (open == idx) null else idx })
                            }
                            AnimatedVisibility(open == idx) {
                                Text(micros.joinToString("  ·  ") { (k, lu) -> "${lu.first} ${fmt(round1(it.micros[k]!!))} ${lu.second}" }, fontSize = 12.sp, color = p.muted, lineHeight = 17.sp)
                            }
                        }
                        if (!readOnly) {
                            var g by remember(idx, est) { mutableStateOf(fmt(round1(it.grams))) }
                            NumberField(g, { v -> g = v.filter { c -> c.isDigit() || c == '.' }; g.toDoubleOrNull()?.let { d -> items = items.toMutableList().also { l -> l[idx] = it.withGrams(d) } } }, "g")
                            IconButton(onClick = { items = items.filterIndexed { i, _ -> i != idx } }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Remove", tint = p.muted) }
                        } else {
                            Text("${it.grams.roundToInt()} g", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        }
                    }
                }
            }
        }
    }
    if (est.notes.isNotEmpty()) Rise(3) { Text(est.notes.joinToString(" · "), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp)) }
    if (!readOnly && onSave != null) Rise(4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${items.sumOf { it.calories }.toInt()} kcal", fontSize = 20.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink)
                Text("${fmt(round1(items.sumOf { it.proteinG }))} g protein", fontSize = 12.sp, color = p.muted)
            }
            Spacer(Modifier.width(12.dp))
            PillButton("Save as meal", { onSave(items, est.photoPath) }, Modifier.weight(1f), enabled = items.isNotEmpty())
        }
    }
}

private fun round1(d: Double) = (d * 10).roundToInt() / 10.0

// ---- history ----

@Composable
private fun HistoryList(items: List<ScanHistoryItem>, onOpen: (ScanHistoryItem) -> Unit, onDelete: (ScanHistoryItem) -> Unit) {
    val p = palette
    if (items.isEmpty()) return
    Rise(3) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HistoryIcon, null, tint = p.muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("History", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
            }
            items.forEach { it -> HistoryRow(it, { onOpen(it) }, { onDelete(it) }) }
        }
    }
}

@Composable
private fun HistoryRow(it: ScanHistoryItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val p = palette
    val kindIcon = when (it.kind) { "barcode" -> BarcodeIcon; "photo" -> CameraIcon; else -> ScanIcon }
    val kindLabel = when (it.kind) { "barcode" -> "Barcode"; "photo" -> "Photo"; else -> "Label" }
    val trust = when (it.verdict) { "safe" -> p.green to "Safe"; "caution" -> p.orange to "Caution"; "unsafe" -> p.red to "Unsafe"; "misleading" -> p.purple to "Misleading"; "fake" -> p.red to "Likely fake"; else -> null }
    Card(padding = 12.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                it.imageUrl != null -> RemoteImage(url = it.imageUrl, size = 46.dp, radius = 12.dp, fallback = kindIcon)
                it.imagePath != null -> RemoteImage(storagePath = it.imagePath, size = 46.dp, radius = 12.dp, fallback = kindIcon)
                else -> Box(Modifier.size(46.dp).background(p.card2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(kindIcon, null, tint = p.muted, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(it.product.ifBlank { kindLabel }, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$kindLabel · ${dayOf(it.createdAt)}" + (it.score?.let { s -> " · $s/10" } ?: ""), fontSize = 11.sp, color = p.muted)
                    if (it.kind != "photo") Box(Modifier.background(p.card2, CircleShape).padding(7.dp, 2.dp)) { Text(it.lens, fontSize = 10.sp, fontWeight = FontWeight(700), color = p.ink) }
                    if (trust != null) Text(trust.second, fontSize = 11.sp, fontWeight = FontWeight(700), color = trust.first)
                }
            }
            IconButton(onClick = onDelete, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = p.muted) }
        }
    }
}

/** A stored scan, opened from History: the same views, read-only. */
@Composable
private fun ScanDetailPage(item: ScanHistoryItem, onLogged: () -> Unit, onBack: () -> Unit) {
    val p = palette
    var json by remember(item.id) { mutableStateOf<org.json.JSONObject?>(null) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) { runCatching { Api.scanReport(item.id) }.onSuccess { json = it }.onFailure { error = it.message } }
    SubPage(item.product.ifBlank { "Saved scan" }, onBack) {
        val o = json
        when {
            error != null -> ErrorNote(error)
            o == null -> { LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track); Text("Opening…", fontSize = 12.sp, color = p.muted) }
            item.kind == "photo" -> PhotoReview(PlateEstimate.from(o), null, readOnly = true)
            else -> ReportView(LabelReport.from(o), onLogged = onLogged)
        }
    }
}

private fun dayOf(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" })
        .atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
}.getOrDefault("")

internal fun decodeScaled(ctx: Context, uri: Uri, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    // inSampleSize only halves; finish with an exact scale so the upload never exceeds maxEdge.
    val longest = maxOf(bmp.width, bmp.height)
    if (longest <= maxEdge) return bmp
    val k = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt(), (bmp.height * k).roundToInt(), true)
}

internal fun toJpegBytes(b: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

internal fun toJpegBase64(b: Bitmap, quality: Int): String = Base64.encodeToString(toJpegBytes(b, quality), Base64.NO_WRAP)
