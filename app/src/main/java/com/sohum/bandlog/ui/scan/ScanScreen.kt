package com.sohum.bandlog.ui.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.LabelReport
import com.sohum.bandlog.data.PlateEstimate
import com.sohum.bandlog.data.PlateItem
import com.sohum.bandlog.ui.components.InfoButton
import com.sohum.bandlog.ui.components.SourceSheet
import com.sohum.bandlog.ui.components.VariantChips
import com.sohum.bandlog.util.Sources
import com.sohum.bandlog.data.ScanHistoryItem
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BarcodeIcon
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.ChevronDownIcon
import com.sohum.bandlog.ui.components.ClipboardListIcon
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.EggIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FlameIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.HistoryIcon
import com.sohum.bandlog.ui.components.MacroDonut
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.PencilIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.QuantitySheet
import com.sohum.bandlog.ui.components.QuestionIcon
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.SmallChip
import com.sohum.bandlog.ui.components.SpoonIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.TagIcon
import com.sohum.bandlog.ui.components.lucide
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.LabelParse
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.QuantityFood
import com.sohum.bandlog.util.applyFollowUpEffect
import com.sohum.bandlog.util.totalKcalRange
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

/** Shown instead of a per-100g table when the server found no real nutrition data to parse. */
private const val BACK_OF_PACK_HINT = "Flip the pack and scan the Nutrition Facts table for real numbers."

private val LENSES = listOf("protein" to "Protein", "snack" to "Snack", "cutting" to "Cutting", "bulking" to "Bulking")

// v2.12: the camera stage is always dark (like a viewfinder), in light and dark theme alike.
private val StageInk = Color(0xFFF5F5F7)
private val StageGlass = Color(0xB81C1C1E)
private val StageLine = Color(0x2EFFFFFF)
private val GlassChipBg = Color(0xDBF5F5F7)

private val UtensilsIcon: ImageVector by lazy { lucide("Utensils", listOf("M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2", "M7 2v20", "M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3zm0 0v7")) }
private val GalleryIcon: ImageVector by lazy { lucide("Gallery", listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 7a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M21 15l-5-5L5 21")) }
private val KeypadIcon: ImageVector by lazy {
    lucide("Keypad", listOf("M20 5H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", "M6 9h.01", "M10 9h.01", "M14 9h.01", "M18 9h.01", "M8 13h8"))
}
private val CloseIcon: ImageVector by lazy { lucide("Close", listOf("M18 6L6 18", "M6 6l12 12")) }

/** Scan modes on the camera stage: key, label, and the flow the photo is sent down (null = work it out). */
private data class ScanMode(val key: String, val label: String, val forced: String?, val hint: String)

private val MODES = listOf(
    ScanMode("food", "Scan food", null, "Fit the whole plate in the frame"),
    ScanMode("barcode", "Barcode", "barcode", "Line the bars up across the middle"),
    ScanMode("label", "Food label", "label", "Get the front of the pack and its claims in shot"),
    ScanMode("facts", "Nutrition facts", "label", "Fill the frame with the nutrition table"),
    // v2.13 §10: a restaurant menu → dishes with kcal / protein ranges and a best pick.
    ScanMode("menu", "Menu", "menu", "Fit one menu page in the frame, text sharp"),
)

private fun modeIcon(key: String): ImageVector = when (key) {
    "barcode" -> BarcodeIcon
    "label" -> TagIcon
    "facts" -> ClipboardListIcon
    "menu" -> com.sohum.bandlog.ui.nutrition.NutritionIcons.Menu
    else -> UtensilsIcon
}

/** Bottom-nav tab: the camera stage (or a result over its photo), with History as a page on top. */
@Composable
fun ScanTab(vm: AppViewModel) {
    var history by remember { mutableStateOf<List<ScanHistoryItem>>(emptyList()) }
    var historyTick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<ScanHistoryItem?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    LaunchedEffect(historyTick) { history = runCatching { Api.scanHistory() }.getOrDefault(history) }

    Box(Modifier.fillMaxSize()) {
        ScanForm(vm, onScanned = { historyTick++ }, onOpenHistory = { showHistory = true }, onLogServing = { vm.openAddFood(listOf(it)) })
        AnimatedContent(
            targetState = showHistory, label = "scan-history",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { shown ->
            if (shown) {
                BackHandler { showHistory = false }
                SubPage("Scan history", { showHistory = false }) {
                    if (history.isEmpty()) Text("Nothing scanned yet. Your scans land here.", fontSize = 14.sp, color = palette.muted)
                    HistoryList(history, { open = it }, { item ->
                        history = history.filter { it.id != item.id }
                        vm.launch { runCatching { Api.deleteScan(item.id) } }
                    })
                    Spacer(Modifier.navigationBarsPadding().height(96.dp))
                }
            }
        }
        AnimatedContent(
            targetState = open, label = "scan-detail",
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
        ) { item ->
            if (item != null) {
                BackHandler { open = null }
                ScanDetailPage(item, onLogged = { vm.refresh() }, onLogServing = { open = null; showHistory = false; vm.openAddFood(listOf(it)) }) { open = null }
            }
        }
    }
}

/** Label words in English and Hindi — with ≥120 characters read, one of these means it's a label. */
private val LABEL_WORDS = Regex("ingredients|nutrition|energy|protein|kcal|सामग्री|पोषण|ऊर्जा|प्रोटीन|कैलोरी", RegexOption.IGNORE_CASE)

internal fun looksLikeLabel(text: String): Boolean = text.trim().length >= OCR_MIN_CHARS && LABEL_WORDS.containsMatchIn(text)

private val KINDS = listOf("barcode" to "Barcode", "label" to "Label", "plate" to "Plate")

/**
 * v2.1: one Scan button. The phone works out what the photo is — ML Kit barcode first, then ML Kit
 * OCR (≥120 characters that read like a nutrition panel → label), otherwise a plate — and runs
 * that flow straight away. A chip row ("Looks like a label — change?") overrides the guess.
 * v2.12: a camera stage with mode buttons; "Scan food" keeps the automatic guess, the others run
 * the same flow the override chip would.
 */
@Composable
private fun ScanForm(
    vm: AppViewModel,
    onScanned: () -> Unit,
    onOpenHistory: () -> Unit,
    onLogServing: (com.sohum.bandlog.data.MealItem) -> Unit,
) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Profile → Preferences → "Judge scans for" decides where the lens starts.
    val lens = vm.profile.initialLens
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var kind by remember { mutableStateOf<String?>(null) }
    var ocr by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var noteOpen by remember { mutableStateOf(false) }
    var digitsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<LabelReport?>(null) }
    var plate by remember { mutableStateOf<PlateEstimate?>(null) }
    var notFound by remember { mutableStateOf(false) }
    // v2.13 §10 menu scan, and the live camera in the frame (CAMERA permission; else the camera app).
    val nvm: com.sohum.bandlog.ui.nutrition.NutritionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var menu by remember { mutableStateOf<com.sohum.bandlog.data.MenuScan?>(null) }
    var menuUnavailable by remember { mutableStateOf(false) }
    val live = remember { LiveCamera() }
    var camAllowed by remember { mutableStateOf(LiveCamera.permitted(ctx)) }
    var camFailed by remember { mutableStateOf(false) }
    var askedCam by rememberSaveable { mutableStateOf(false) }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> camAllowed = ok }
    val target = remember { mutableStateOf<Uri?>(null) }
    var mode by rememberSaveable { mutableStateOf("food") }
    // The plate as edited in the review, for the floating totals over the photo.
    var plateItems by remember { mutableStateOf<List<PlateItem>>(emptyList()) }
    LaunchedEffect(plate) { plateItems = plate?.items ?: emptyList() }

    fun clearResults() { report = null; plate = null; notFound = false; error = null; menu = null; menuUnavailable = false }

    suspend fun labelFrom(bmp: Bitmap?) {
        if (ocr.isBlank() && bmp != null) ocr = runCatching { Ocr.read(bmp) }.getOrDefault("")
        val text = ocr.trim()
        // Send the photo too unless the on-device OCR text actually contains a nutrition table —
        // front-of-pack marketing copy ("...21g Non GMO Protein...") can clear OCR_MIN_CHARS
        // without ever showing a number, and the server has no way to read the back of the pack
        // if the image never arrives (the seeds-label bug: it fabricated a table from that text).
        val hasTable = text.length >= OCR_MIN_CHARS && LabelParse.parse(text).hasTable
        val b64 = if (!hasTable && bmp != null) withContext(Dispatchers.IO) { toJpegBase64(scaleForUpload(bmp, 1600), 88) } else null
        report = Api.scanLabel(text, note, b64, lens)
    }

    /** Runs the flow for [k] on the current photo (or typed digits). */
    fun runKind(k: String) {
        val bmp = photo
        kind = k
        scope.launch {
            busy = true; clearResults()
            try {
                when (k) {
                    "barcode" -> {
                        val digits = barcode.filter(Char::isDigit)
                        if (digits.length < 8) { error = "No barcode in that photo — type the digits under the bars."; digitsOpen = true; return@launch }
                        val r = Api.scanBarcode(digits, lens, note)
                        when {
                            r != null -> report = r
                            // Not on Open Food Facts: if the same photo shows the label, read that instead.
                            bmp != null && looksLikeLabel(ocr.ifBlank { runCatching { Ocr.read(bmp) }.getOrDefault("").also { ocr = it } }) -> { kind = "label"; labelFrom(bmp) }
                            else -> notFound = true
                        }
                    }
                    "label" -> labelFrom(bmp)
                    "menu" -> {
                        bmp ?: return@launch
                        val rem = nvm.remaining(vm)
                        val remJson = org.json.JSONObject().put("kcal", rem.kcal.roundToInt()).put("protein_g", rem.protein.roundToInt())
                            .put("carbs_g", rem.carbs.roundToInt()).put("fat_g", rem.fat.roundToInt())
                        try {
                            menu = com.sohum.bandlog.data.NutritionApi.scanMenu(
                                withContext(Dispatchers.IO) { toJpegBase64(scaleForUpload(bmp, 1600), 85) }, note, remJson, nvm.dietMode(vm.profile),
                            )
                        } catch (e: com.sohum.bandlog.data.NotYetAvailable) { menuUnavailable = true }
                    }
                    else -> {
                        bmp ?: return@launch
                        plate = Api.photoMeal(withContext(Dispatchers.IO) { toJpegBase64(scaleForUpload(bmp, 1280), 85) }, note)
                    }
                }
                onScanned()
                com.sohum.bandlog.data.Analytics.track("scan_done", "kind" to (if (plate != null) "plate" else kind ?: k), "found" to (report != null || plate != null), "forced" to k)
                storeThumb(vm, report, plate, bmp, onScanned)
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    /** New photo → barcode? → label? → plate (or the stage's chosen mode), then straight into that flow. */
    fun accept(bitmap: Bitmap?) {
        photo = bitmap; ocr = ""; barcode = ""; kind = null; clearResults(); showText = false
        val bmp = bitmap ?: run { error = "Couldn't open that photo"; return }
        val forced = MODES.firstOrNull { it.key == mode }?.forced
        if (forced == "menu") { runKind("menu"); return }
        scope.launch {
            reading = true
            val code = runCatching { Barcode.read(bmp) }.getOrNull()
            if (code != null) barcode = code else ocr = runCatching { Ocr.read(bmp) }.getOrDefault("")
            reading = false
            runKind(forced ?: when { code != null -> "barcode"; looksLikeLabel(ocr) -> "label"; else -> "plate" })
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) target.value?.let { uri -> scope.launch { accept(withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 2200) }) } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { accept(withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 2200) }) }
    }
    fun openCamera() {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(dir, "capture.jpg"))
        target.value = uri
        runCatching { camera.launch(uri) }.onFailure { error = "No camera app found — pick from gallery instead." }
    }
    fun openGallery() = gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    /** Back to the camera stage (the note and mode stay). */
    fun reset() { photo = null; ocr = ""; barcode = ""; kind = null; clearResults(); showText = false; digitsOpen = false }

    val inResult = photo != null || reading || busy || report != null || plate != null || notFound || digitsOpen || menu != null || menuUnavailable
    if (!inResult) {
        LaunchedEffect(Unit) {
            nvm.loadSettings()
            // Ask once for the live preview; a "no" keeps the camera-app flow (and a link to ask again).
            if (!camAllowed && !askedCam) { askedCam = true; runCatching { camPermission.launch(android.Manifest.permission.CAMERA) } }
        }
        val liveOn = camAllowed && !camFailed
        CameraStage(
            mode = mode, onMode = { mode = it }, error = error,
            onShutter = {
                error = null
                if (liveOn && live.ready) live.takePicture(ctx, onBitmap = { accept(it) }, onError = { error = it })
                else openCamera()
            },
            onGallery = { error = null; openGallery() },
            onTypeCode = { error = null; digitsOpen = true }, onHistory = onOpenHistory,
            live = if (liveOn) live else null, onCameraFailed = { camFailed = true },
            onEnableLive = if (!camAllowed) ({ runCatching { camPermission.launch(android.Manifest.permission.CAMERA) } }) else null,
        )
        return
    }

    BackHandler(enabled = !busy && !reading) { reset() }
    val k = kind
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = photo
        val fullH = maxHeight
        val photoH = if (bmp != null) fullH * 0.46f else 132.dp
        val sheetTop = photoH - 28.dp
        val scroll = rememberScrollState()
        val sheetTopPx = with(LocalDensity.current) { sheetTop.toPx() }
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), "Your photo",
                Modifier.fillMaxWidth().height(photoH).graphicsLayer { translationY = -scroll.value * 0.35f },
                contentScale = ContentScale.Crop,
            )
        }
        if (reading || busy) ScanLine(Modifier.fillMaxWidth().height(sheetTop), color = p.green)
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(sheetTop)) {
                // Scan 2: the plate's totals as glass chips floating over the photo.
                if (plate != null && plateItems.isNotEmpty() && bmp != null) {
                    val total = totalKcalRange(plateItems)
                    val protein = fmt(round1(plateItems.sumOf { it.proteinG }))
                    val toItems: () -> Unit = { scope.launch { scroll.animateScrollTo(sheetTopPx.roundToInt()) } }
                    GlassChip(
                        "Calories", "~${total.center}", FlameIcon, Color(0xFFF5DD7F),
                        Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 20.dp).scanEnter(1).rotate(-6f), toItems,
                    )
                    GlassChip(
                        "Protein", "$protein g", EggIcon, Color(0xFFA898F5),
                        Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 40.dp).scanEnter(2).rotate(5f), toItems,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = fullH - sheetTop)
                    .shadow(20.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = p.shadow, spotColor = p.shadow)
                    .background(p.bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).background(p.muted.copy(alpha = 0.35f), CircleShape))
                if (reading || busy) {
                    Column(Modifier.scanEnter(0)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = p.green, trackColor = p.track)
                        Text(
                            when {
                                reading -> "Working out what it is…"
                                k == "plate" -> "Identifying each item, then checking the food table. 10–20 s."
                                k == "menu" -> "Reading the menu and sizing up each dish. 15–30 s."
                                k == "barcode" -> "Looking it up, then writing your report. About 10 seconds."
                                ocr.trim().length < OCR_MIN_CHARS -> "Your phone couldn't read enough, so the photo is going up too — 20–45 s."
                                else -> "Checking the brand and writing your report. About 8 seconds."
                            },
                            fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (digitsOpen) {
                    Column(Modifier.scanEnter(0)) {
                        Text("Type the digits under the bars", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).height(52.dp).background(p.card, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                BasicTextField(
                                    barcode, { barcode = it.filter(Char::isDigit).take(14) }, Modifier.fillMaxWidth().semantics { contentDescription = "Barcode digits" }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); runKind("barcode") }),
                                    textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, letterSpacing = 1.sp), cursorBrush = SolidColor(p.ink),
                                    decorationBox = { inner -> if (barcode.isEmpty()) Text("e.g. 8901058851298", fontSize = 15.sp, color = p.muted); inner() },
                                )
                            }
                            PillButton("Look up", { focus.clearFocus(); runKind("barcode") }, Modifier.width(104.dp), enabled = barcode.length >= 8 && !busy, height = 52.dp)
                        }
                    }
                }
                ErrorNote(error)
                if (notFound) Card(Modifier.scanEnter(0)) {
                    Text("Not in the database yet — photograph the label side instead.", fontSize = 14.sp, color = p.ink, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Scan again", { openCamera() }, height = 48.dp)
                }
                report?.let { ReportView(it, lens, onLogged = { vm.refresh() }, onLogServing = onLogServing) }
                if (menuUnavailable) com.sohum.bandlog.ui.nutrition.ComingSoonCard(
                    "Restaurant menu scan", "Snap a menu and see each dish's calories and protein, with the best pick for what you have left today.",
                )
                menu?.let { m ->
                    MenuResultView(m, nvm.remaining(vm), nvm.dietMode(vm.profile)) { dish ->
                        vm.saveMeal(Dates.today(), "Menu: ${dish.name}", listOf(dish.toMealItem()), mealType = MealTypes.default(), method = "menu")
                    }
                }
                plate?.let { est ->
                    PhotoReview(
                        est, photo, readOnly = false, showPhoto = false,
                        saveLabel = "Log as " + MealTypes.label(MealTypes.default()).lowercase(),
                        onItemsChanged = { plateItems = it },
                        onPickAnother = { list -> com.sohum.bandlog.data.Analytics.hintMealMethod("photo"); vm.openAddFood(list.map { it.toMealItem() }) },
                    ) { items, path ->
                        scope.launch {
                            busy = true
                            val photoPath = path ?: photo?.let { b -> runCatching { Api.uploadMealPhoto(withContext(Dispatchers.IO) { toJpegBytes(b, 85) }) }.getOrNull() }
                            val ok = vm.saveMeal(Dates.today(), est.plateNote.ifBlank { items.joinToString(", ") { it.name } }, items.map { it.toMealItem() }, photoPath, method = "photo")
                            busy = false
                            if (ok) { plate = null; photo = null; kind = null } else error = vm.error
                        }
                    }
                }

                // Not right? What the phone thinks it is, with a one-tap override, and the quieter ways in.
                if (bmp != null && k != null && k != "menu" && !reading) {
                    Column {
                        Text(
                            "Looks like " + when (k) { "barcode" -> "a barcode"; "label" -> "a label"; else -> "a plate" } + " — change?",
                            fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KINDS.forEach { (key, label) -> SmallChip(label, { if (key != k && !busy) runKind(key) }, filled = key == k) }
                        }
                    }
                }
                if (k == "label" && bmp != null && !reading) WhatIRead(ocr, false, showText, { showText = !showText }) { ocr = it }
                if (noteOpen && bmp != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(48.dp).background(p.card, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                            BasicTextField(
                                note, { note = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Note for the analysis" }, singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (note.isEmpty()) Text(if (k == "plate") "e.g. the dal has ghee, two rotis" else "What is it / what do you want to know", fontSize = 14.sp, color = p.muted, maxLines = 1); inner() },
                            )
                        }
                        PillButton("Redo", { focus.clearFocus(); k?.let { runKind(it) } }, Modifier.width(80.dp), enabled = k != null && !busy && !reading, height = 48.dp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (!busy && !reading) TextLinkSmall("Retake") { openCamera() }
                    if (!digitsOpen && k != "menu") TextLinkSmall("Type barcode digits") { digitsOpen = true }
                    if (!noteOpen && bmp != null) TextLinkSmall("Add a note") { noteOpen = true }
                }
                Spacer(Modifier.navigationBarsPadding().height(104.dp))
            }
        }
        // Top bar over the photo; the title fades as the sheet scrolls up over it.
        val fade = (1f - scroll.value / sheetTopPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(96.dp).alpha(fade).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StageButton(CloseIcon, "Close", enabled = !busy && !reading) { reset() }
            Text(
                when (k) { "barcode" -> "Barcode"; "label" -> "Label check"; "plate" -> "Your plate"; "menu" -> "Menu"; else -> if (digitsOpen) "Barcode" else "Scan" },
                Modifier.weight(1f).alpha(fade).semantics { heading() }, textAlign = TextAlign.Center,
                fontSize = 15.sp, fontWeight = FontWeight(700), color = StageInk,
            )
            Spacer(Modifier.size(48.dp))
        }
    }
}

/**
 * Scan 1 · the camera stage, kept simple: corner brackets, one hint line, the four modes, and the
 * shutter between Gallery and typed digits. The shutter opens the phone's camera app (no CAMERA
 * permission, same as before), then the photo comes back here.
 */
@Composable
private fun CameraStage(
    mode: String, onMode: (String) -> Unit, error: String?,
    onShutter: () -> Unit, onGallery: () -> Unit, onTypeCode: () -> Unit, onHistory: () -> Unit,
    /** v2.13: the live preview (null = permission denied / no camera → the old camera-app flow). */
    live: LiveCamera? = null, onCameraFailed: (String) -> Unit = {}, onEnableLive: (() -> Unit)? = null,
) {
    val current = MODES.firstOrNull { it.key == mode } ?: MODES.first()
    Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().padding(bottom = 100.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp).scanEnter(0, riseDp = 16f), verticalAlignment = Alignment.CenterVertically) {
            Text("Scan", Modifier.weight(1f).semantics { heading() }, fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = StageInk)
            if (live != null && live.hasFlash) StageButton(
                if (live.torch) com.sohum.bandlog.ui.nutrition.NutritionIcons.Flash else com.sohum.bandlog.ui.nutrition.NutritionIcons.FlashOff,
                if (live.torch) "Flash on, tap to turn off" else "Flash off, tap to turn on",
            ) { live.toggleTorch() }
            StageButton(HistoryIcon, "Scan history", onClick = onHistory)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp, vertical = 18.dp).scanEnter(1, riseDp = 16f)) {
            if (live != null) LiveCameraPreview(live, Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(22.dp)), onFailed = onCameraFailed)
            CornerBrackets(color = StageInk.copy(alpha = 0.92f))
            if (live == null) Icon(modeIcon(current.key), null, tint = StageInk.copy(alpha = 0.16f), modifier = Modifier.align(Alignment.Center).size(64.dp))
            if (live != null && live.busy) Box(Modifier.align(Alignment.Center).size(64.dp).background(StageGlass, CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = StageInk)
            }
        }
        if (onEnableLive != null) Box(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onEnableLive).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Live preview is off · Turn on", fontSize = 12.sp, fontWeight = FontWeight(600), color = StageInk.copy(alpha = 0.75f)) }
        if (error != null) {
            Text(
                error, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), textAlign = TextAlign.Center,
                fontSize = 13.sp, fontWeight = FontWeight(600), color = Color(0xFFFF8A80),
            )
        }
        Text(
            current.hint, Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 18.dp).scanEnter(2, riseDp = 16f),
            textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight(600), color = StageInk,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup().scanEnter(3, riseDp = 16f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MODES.forEach { m ->
                val sel = m.key == mode
                Column(
                    Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) StageInk else StageGlass)
                        .border(1.dp, if (sel) Color.Transparent else StageLine, RoundedCornerShape(18.dp))
                        .selectable(selected = sel, role = Role.Tab) { onMode(m.key) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(modeIcon(m.key), null, tint = if (sel) Color.Black else StageInk, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(m.label, fontSize = 11.5.sp, lineHeight = 13.sp, fontWeight = FontWeight(600), color = if (sel) Color.Black else StageInk, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).scanEnter(4, riseDp = 16f),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            StageAction(GalleryIcon, "Gallery", onGallery)
            Box(
                Modifier.size(78.dp).clip(CircleShape).border(4.dp, StageInk, CircleShape).clickable(onClick = onShutter)
                    .semantics { contentDescription = "Take photo"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(62.dp).background(StageInk, CircleShape)) }
            StageAction(KeypadIcon, "Type code", onTypeCode)
        }
    }
}

/** A round glass icon button for the dark stage (48 dp target). */
@Composable
private fun StageButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).background(StageGlass, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = StageInk, modifier = Modifier.size(20.dp))
        }
    }
}

/** Glass circle with a caption under it (Gallery / Type code). */
@Composable
private fun StageAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).background(StageGlass, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = StageInk, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = StageInk)
    }
}

/** Scan 2 · a floating glass chip over the photo: icon tile, label, value, and an edit mark. */
@Composable
private fun GlassChip(label: String, value: String, icon: ImageVector, tile: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.shadow(14.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(GlassChipBg)
            .clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "$label $value. Edit items"; role = Role.Button }
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(tile, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF111111), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = Color(0xFF555555))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = Color(0xFF111111), lineHeight = 24.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(PencilIcon, null, tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TextLinkSmall(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).semantics { role = Role.Button }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight(700), color = palette.muted)
    }
}

/** Protein · Snack · Cutting · Bulking, as small chips. */
@Composable
private fun LensSwitch(value: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LENSES.forEach { (key, label) -> SmallChip(label, { onChange(key) }, filled = key == value) }
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
private fun eatLabel(verdict: String) = when (verdict) { "great" -> "Eat it"; "ok" -> "Sometimes"; else -> "Skip for this" }

/** The dial's words for the 0–10 score, on the same bands as its colour. */
private fun scoreWords(score: Int) = when { score >= 7 -> "Good pick"; score >= 4 -> "Okay sometimes"; else -> "Not a great pick" }

/**
 * v2.12 report. Barcode (Scan 3): a product card — picture, name, where the numbers came from,
 * kcal / protein / carbs / fat, "Log 1 serving". Label (Scan 4): the score dial, then "Check this"
 * warnings, traffic lights and the claims check. Both then show the fit for the chosen lens and
 * fold everything else into one "Details" expander.
 */
@Composable
fun ReportView(r: LabelReport, initialLens: String = r.lens, onLogged: () -> Unit = {}, onLogServing: ((com.sohum.bandlog.data.MealItem) -> Unit)? = null) {
    val p = palette
    if (!r.readable) {
        Card(Modifier.scanEnter(0)) { Text("Couldn't read that as a food label", fontWeight = FontWeight(700), color = p.ink); Text(r.verdictReason, fontSize = 13.sp, color = p.muted) }
        return
    }
    val scope = rememberCoroutineScope()
    var lens by remember(r) { mutableStateOf(if (r.fits.containsKey(initialLens)) initialLens else r.lens) }
    var details by remember(r) { mutableStateOf(false) }
    val g = r.infographic
    val fit = r.fits[lens]
    val score = g.score
    val scoreColor = when { score >= 7 -> p.green; score >= 4 -> p.orange; else -> p.muted }
    val isBarcode = r.kind == "barcode"
    // "Log 1 serving": the report as a food the Quantity sheet can price.
    val food = remember(r) { QuantityFood.from(r) }
    var logFood by remember(r) { mutableStateOf<QuantityFood?>(null) }
    var logged by remember(r) { mutableStateOf<String?>(null) }
    var logError by remember(r) { mutableStateOf<String?>(null) }
    val source = r.sourceInfo ?: Sources.forReport(r.nutritionSource, r.barcode)
    val checked = r.nutritionSource == "openfoodfacts" || r.nutritionSource == "label"
    val sourceLine = (if (r.nutritionSource == "openfoodfacts") "Found on ${source.label}" else source.label) + if (checked) " · checked" else ""

    fun logOne() {
        val go = onLogServing
        val f = food ?: return
        com.sohum.bandlog.data.Analytics.hintMealMethod(if (r.kind == "barcode") "barcode" else "label")
        if (go == null) logFood = f
        // v2.9: the plate's ⓘ shows where these numbers came from (the label, or Open Food Facts).
        else go(f.item(if (f.servingGrams != null) com.sohum.bandlog.util.Quantity(com.sohum.bandlog.util.QUnit.SERVING, 1.0) else com.sohum.bandlog.util.Quantity(com.sohum.bandlog.util.QUnit.G, 100.0))
            .copy(sourceInfo = r.sourceInfo ?: Sources.forReport(r.nutritionSource, r.barcode)))
    }

    // ---- hero: the product card (barcode) or the score dial (label) ----
    Card(Modifier.scanEnter(0, r, riseDp = 96f), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (r.imageUrl != null) { RemoteImage(url = r.imageUrl, size = 48.dp, radius = 12.dp, fallback = BarcodeIcon); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                // Long product names wrap (no maxLines) and never push the score off the card.
                Text(
                    (if (ScanHistoryItem.isJunkName(r.product)) "Unnamed label" else r.product) + (r.servingG?.takeIf { it > 0 }?.let { " · ${it.roundToInt()} g" } ?: ""),
                    fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, lineHeight = 21.sp, softWrap = true, modifier = Modifier.semantics { heading() },
                )
                Text(sourceLine, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
            }
            if (isBarcode && score > 0) {
                Spacer(Modifier.width(10.dp))
                Ring(score / 10f, scoreColor, 52.dp, 6.dp, Modifier.semantics { contentDescription = "Score $score out of 10" }) {
                    Text("$score", fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
                }
            }
        }
        if (isBarcode) {
            Spacer(Modifier.height(12.dp))
            if (r.needsBackOfPack) Text(BACK_OF_PACK_HINT, fontSize = 13.sp, color = p.orange, lineHeight = 18.sp)
            else MacroCells(r)
            if (g.oneLiner.isNotBlank()) Text(g.oneLiner, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
        } else {
            Spacer(Modifier.height(4.dp))
            ScoreDial(score, scoreColor, scoreWords(score), Modifier.align(Alignment.CenterHorizontally))
            if (g.oneLiner.isNotBlank()) Text(g.oneLiner, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (food != null) {
            Spacer(Modifier.height(12.dp))
            // v2.4: opens Add food with one serving already on the plate (the amount stays editable there).
            PillButton("Log 1 serving" + (r.servingG?.takeIf { it > 0 }?.let { " · ${it.roundToInt()} g" } ?: ""), { logOne() }, height = 48.dp)
            logged?.let { Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(CheckIcon, null, tint = p.green, modifier = Modifier.size(14.dp)); Text("  $it — on Home", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.green) } }
            logError?.let { Text(it, fontSize = 12.sp, color = p.red, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
        }
    }

    // v2.8: deterministic sanity-check flags (kJ read as kcal, decimal slips, ...) — non-blocking.
    if (r.validation.isNotEmpty()) {
        Column(
            Modifier.scanEnter(1, r).fillMaxWidth().background(p.orangeBg, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            r.validation.forEachIndexed { i, f ->
                Text(
                    buildAnnotatedString {
                        if (i == 0) withStyle(SpanStyle(fontWeight = FontWeight(700))) { append("Check this: ") }
                        append(f.issue)
                    },
                    fontSize = 13.sp, color = p.ink, lineHeight = 19.sp,
                )
            }
        }
    }

    // ---- label: the numbers, traffic lights, claims ----
    if (!isBarcode) {
        if (r.needsBackOfPack) {
            Card(Modifier.scanEnter(2, r), padding = 14.dp) { Text(BACK_OF_PACK_HINT, fontSize = 13.sp, lineHeight = 18.sp, color = p.orange) }
        } else if (listOf("protein_g", "calories", "carbs_g", "fat_g").any { r.per100[it] != null }) {
            Card(Modifier.scanEnter(2, r), padding = 14.dp) { MacroCells(r) }
        }
        TrafficLights(r, Modifier.scanEnter(3, r))
    }
    if (r.claims.isNotEmpty()) ClaimsCheck(r.claims, Modifier.scanEnter(4, r))

    // ---- how it fits the chosen way of eating ----
    if (fit != null || r.fits.size > 1) {
        Card(Modifier.scanEnter(5, r)) {
            if (fit != null) {
                val c = fitColor(fit.verdict)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(c, CircleShape).padding(14.dp, 7.dp)) {
                        Text(eatLabel(fit.verdict), fontSize = 13.sp, fontWeight = FontWeight(800), color = if (fit.verdict == "weak") p.card else Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("for ${LENSES.firstOrNull { it.first == lens }?.second?.lowercase() ?: lens}", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                }
                if (fit.why.isNotBlank()) Text(fit.why, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (r.fits.size > 1) {
                Spacer(Modifier.height(4.dp))
                LensSwitch(lens) { lens = it }
            }
        }
    }
    if (r.fits.isNotEmpty()) Box(Modifier.scanEnter(5, r)) { FitPills(r.fits) }

    // ---- details ----
    Box(Modifier.scanEnter(6, r)) {
        val rot by animateFloatAsState(if (details) 180f else 0f, Motion.spatialFast(), label = "details")
        Card(padding = 16.dp, onClick = { details = !details }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Details", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Trust, sugar, salt, protein, ingredients", fontSize = 12.sp, color = p.muted, maxLines = 1)
                }
                Icon(ChevronDownIcon, if (details) "Hide details" else "Show details", tint = p.muted, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rot })
            }
        }
    }
    AnimatedVisibility(details) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (r.whatItIs.isNotBlank()) Card { Text("What it is", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink); Spacer(Modifier.height(6.dp)); Text(r.whatItIs, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp) }

            TrustMeter(r.verdict, r.verdictReason)

            if (r.needsBackOfPack) {
                Card {
                    Text("One serving", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(BACK_OF_PACK_HINT, fontSize = 13.sp, color = p.orange, lineHeight = 18.sp)
                }
            } else
            // One serving as a donut, and against the whole day.
            if (food != null && (food.proteinG + food.carbsG + food.fatG) > 0) Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("One serving", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    if (r.nutritionSource != null && r.nutritionSource != "label") EstimateBadge()
                }
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
                if (g.caloriesPct + g.proteinPct + g.carbsPct + g.fatPct > 0) {
                    Spacer(Modifier.height(14.dp))
                    Text("…is this much of your whole day", fontSize = 12.sp, color = p.muted)
                    Spacer(Modifier.height(8.dp))
                    ShareRow("Calories", g.caloriesPct, p.ink)
                    ShareRow("Protein", g.proteinPct, p.red)
                    ShareRow("Carbs", g.carbsPct, p.orange)
                    ShareRow("Fat", g.fatPct, p.blue)
                }
            }

            // Sugar in teaspoons and salt, side by side in one card.
            val spoons = g.sugarTsp.roundToInt()
            if (g.sugarTsp > 0.04 || g.sodiumPct > 0) Card {
                if (g.sugarTsp > 0.04) {
                    Text("Sugar", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                        repeat(spoons.coerceIn(0, 12)) { Icon(SpoonIcon, null, tint = p.orange, modifier = Modifier.size(19.dp).padding(end = 2.dp)) }
                        if (spoons > 12) Text("+", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.orange)
                    }
                    Text(if (spoons == 0) "Less than 1 tsp of sugar per serving" else "$spoons tsp of sugar per serving", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 6.dp))
                }
                if (g.sodiumPct > 0) {
                    if (g.sugarTsp > 0.04) { Spacer(Modifier.height(12.dp)); Hair(); Spacer(Modifier.height(12.dp)) }
                    val c = if (g.sodiumPct >= 40) p.red else if (g.sodiumPct >= 20) p.orange else p.green
                    RowSpaceBetween {
                        Text("Salt", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("${g.sodiumPct}% of a day", fontSize = 13.sp, fontWeight = FontWeight(800), color = c)
                    }
                    Spacer(Modifier.height(8.dp))
                    FillBar(g.sodiumPct / 100f, c)
                }
            }

            Card {
                val pc = when (r.proteinRating) { "excellent", "good" -> p.green; "average" -> p.orange; else -> p.muted }
                val gauge = when (r.proteinRating) { "excellent" -> 1f; "good" -> 0.75f; "average" -> 0.45f; else -> 0.15f }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(gauge, pc, 60.dp, 8.dp) { Text(r.proteinPerServing?.let { "${it.roundToInt()}g" } ?: "—", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Protein · ${r.proteinRating.replaceFirstChar { it.uppercase() }}", fontSize = 15.sp, fontWeight = FontWeight(700), color = pc)
                        if (r.proteinQuality.isNotBlank()) Text(r.proteinQuality, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (r.proteinNote.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(r.proteinNote, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp) }
            }

            if (r.concerns.isNotEmpty()) Card {
                Text("Ingredients", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                r.concerns.forEach { (ing, issue, sev) ->
                    val (c, icon) = when (sev) { "high" -> p.red to CrossIcon; "medium" -> p.orange to QuestionIcon; else -> p.green to CheckIcon }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(20.dp).background(c.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c, modifier = Modifier.size(12.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ing, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, lineHeight = 19.sp)
                            if (issue.isNotBlank()) Text(issue, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                        }
                    }
                }
            }

            if (r.suggestions.isNotEmpty()) Column(Modifier.fillMaxWidth().background(p.btn, RoundedCornerShape(20.dp)).padding(18.dp)) {
                Text("For you · ${LENSES.firstOrNull { it.first == r.lens }?.second ?: r.lens}", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk)
                r.suggestions.forEach {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) { Text("•  ", color = p.btnInk.copy(alpha = 0.6f), fontSize = 14.sp); Text(it, fontSize = 14.sp, color = p.btnInk, lineHeight = 20.sp) }
                }
            }
            if (r.alternatives.isNotEmpty()) Card {
                Text("Better options", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Spacer(Modifier.height(10.dp))
                r.alternatives.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { alt -> Box(Modifier.weight(1f).background(p.card2, CircleShape).padding(12.dp, 9.dp)) { Text(alt, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 2) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (r.research.isNotEmpty()) Collapsible("What the web says", r.research)
        }
    }

    logFood?.let { f ->
        QuantitySheet(
            food = f, title = "Log from this scan", cta = "Log",
            onDismiss = { logFood = null },
            onDone = { item, _ ->
                logFood = null
                scope.launch {
                    runCatching { Api.saveMeal(Dates.today(), "${r.product.ifBlank { "Scanned product" }} (scan)", listOf(item)) }
                        .onSuccess { com.sohum.bandlog.data.Analytics.track("meal_logged", "method" to (if (r.kind == "barcode") "barcode" else "label"), "items" to 1, "from" to "scan") }
                        .onSuccess { logged = "Logged ${item.quantityLabel} · ${item.calories.roundToInt()} kcal"; logError = null; onLogged() }
                        .onFailure { logError = it.message }
                }
            },
        )
    }
}

@Composable
private fun EstimateBadge() {
    val p = palette
    Text(
        "ESTIMATE", fontSize = 10.sp, fontWeight = FontWeight(700), color = p.orange,
        modifier = Modifier.background(p.orangeBg, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * kcal · protein · carbs · fat as four cells: per serving (per-100 g × serving size) when the
 * label gives a serving, else per 100 g (and it says so).
 */
@Composable
private fun MacroCells(r: LabelReport) {
    val p = palette
    val serving = r.servingG?.takeIf { it > 0 }
    val k = (serving ?: 100.0) / 100.0
    val isEstimate = r.nutritionSource != null && r.nutritionSource != "label"
    fun v(key: String): String = r.per100[key]?.let { x ->
        val y = x * k
        if (key == "calories") "${y.roundToInt()}" else fmt(round1(y)) + " g"
    } ?: "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (serving != null) "Per serving · ${serving.roundToInt()} g" else "Per 100 g", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
        if (isEstimate) EstimateBadge()
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(v("calories") to "kcal", v("protein_g") to "protein", v("carbs_g") to "carbs", v("fat_g") to "fat").forEach { (value, label) ->
            Column(
                Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 4.dp)
                    .semantics(mergeDescendants = true) { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink, maxLines = 1)
                Text(label, fontSize = 11.sp, color = p.muted, maxLines = 1)
            }
        }
    }
}

/**
 * Scan 4 · traffic lights per 100 g, from the parsed numbers. Sugar and sodium use the UK FSA
 * front-of-pack bands (sugar ≤5 / >22.5 g; sodium ≤120 / >600 mg, i.e. salt ≤0.3 / >1.5 g). Fibre
 * uses the EU claim thresholds (≥3 g "source", ≥6 g "high"). Protein follows the report's own
 * protein rating. Good things are never red: low fibre or protein is grey.
 */
@Composable
private fun TrafficLights(r: LabelReport, modifier: Modifier = Modifier) {
    val p = palette
    data class Light(val name: String, val value: String, val color: Color, val word: String)
    val rows = buildList {
        r.per100["sugar_g"]?.let { s ->
            val (c, w) = when { s <= 5 -> p.green to "low"; s <= 22.5 -> p.orange to "medium"; else -> p.red to "high" }
            add(Light("Sugar", "${fmt(round1(s))} g", c, w))
        }
        r.per100["sodium_mg"]?.let { s ->
            val (c, w) = when { s <= 120 -> p.green to "low"; s <= 600 -> p.orange to "medium"; else -> p.red to "high" }
            add(Light("Sodium", "${s.roundToInt()} mg", c, w))
        }
        r.per100["fiber_g"]?.let { f ->
            val (c, w) = when { f >= 6 -> p.green to "high"; f >= 3 -> p.orange to "some"; else -> p.muted to "low" }
            add(Light("Fibre", "${fmt(round1(f))} g", c, w))
        }
        r.per100["protein_g"]?.let { pr ->
            val (c, w) = when (r.proteinRating) { "excellent", "good" -> p.green to r.proteinRating; "average" -> p.orange to "average"; else -> p.muted to "low" }
            add(Light("Protein", "${fmt(round1(pr))} g", c, w))
        }
    }
    if (rows.isEmpty()) return
    Card(modifier) {
        Text("Per 100 g", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
        rows.forEach { l ->
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).semantics(mergeDescendants = true) { contentDescription = "${l.name} ${l.value} per 100 grams, ${l.word}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(l.color, CircleShape))
                Text(l.name, fontSize = 13.sp, color = p.ink, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text(l.value, fontSize = 13.sp, color = p.muted)
                Text(" · ${l.word}", fontSize = 12.sp, color = l.color.takeIf { it != p.muted } ?: p.muted, fontWeight = FontWeight(600))
            }
        }
    }
}

/** Claims on the pack, each checked: supported (green tick), misleading/false (red cross), unclear (amber). */
@Composable
private fun ClaimsCheck(claims: List<Triple<String, String, String>>, modifier: Modifier = Modifier) {
    val p = palette
    Card(modifier) {
        Text("Claims check", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
        claims.forEach { (claim, status, why) ->
            Spacer(Modifier.height(10.dp))
            val (c, icon, word) = when (status) { "supported" -> Triple(p.green, CheckIcon, "holds up"); "misleading", "false" -> Triple(p.red, CrossIcon, status); else -> Triple(p.orange, QuestionIcon, "unclear") }
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Claim “$claim”: $word. $why" }, verticalAlignment = Alignment.Top) {
                Box(Modifier.size(20.dp).background(c.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c, modifier = Modifier.size(12.dp)) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("“$claim”", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, lineHeight = 19.sp)
                    if (why.isNotBlank()) Text(why, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                }
            }
        }
    }
}

/** Scan 4 · the half-circle score dial (0–10), sweeping to its value on the scan ease. */
@Composable
private fun ScoreDial(score: Int, color: Color, caption: String, modifier: Modifier = Modifier) {
    val p = palette
    val on = rememberScanMotionOn()
    val target = (score / 10f).coerceIn(0f, 1f)
    val a = remember(score) { Animatable(if (on) 0f else target) }
    LaunchedEffect(score, on) { if (on) a.animateTo(target, tween(1100, delayMillis = 200, easing = ScanEase)) else a.snapTo(target) }
    val track = p.track
    Box(modifier.size(220.dp, 124.dp).semantics { contentDescription = "Score $score out of 10, ${caption.lowercase()}" }, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 16.dp.toPx()
            val r = size.width / 2 - sw
            val topLeft = Offset(size.width / 2 - r, size.height - 14.dp.toPx() - r)
            val arc = Size(r * 2, r * 2)
            drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (a.value > 0f) drawArc(color, 180f, 180f * a.value, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                buildAnnotatedString {
                    append("$score")
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted)) { append(" /10") }
                },
                fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 42.sp,
            )
            Text(caption, fontSize = 12.sp, color = p.muted)
        }
    }
}

/** How it fits each way of eating: small pills with a coloured dot (great green, ok amber, weak grey). */
@Composable
private fun FitPills(fits: Map<String, com.sohum.bandlog.data.Fit>) {
    val p = palette
    val ordered = LENSES.mapNotNull { (key, label) -> fits[key]?.let { label to it } }
    if (ordered.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { (label, f) ->
            Row(
                Modifier.weight(1f).background(p.card, CircleShape).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(8.dp).background(fitColor(f.verdict), CircleShape))
                Text(" $label", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
    }
}

/**
 * Best-effort, after the report is already on screen: a ≤320 px JPEG of the captured photo to
 * scan-photos/<uid>/<id>.jpg (label and plate scans, and barcode scans without an OFF picture),
 * recorded as thumb_path; a barcode's OFF picture recorded as image_url. Never throws.
 */
private fun storeThumb(vm: AppViewModel, report: LabelReport?, plate: PlateEstimate?, bmp: Bitmap?, onDone: () -> Unit) {
    val id = report?.id ?: plate?.id ?: return
    val offImage = report?.takeIf { it.kind == "barcode" }?.imageUrl
    vm.launch {
        var changed = false
        if (offImage != null) runCatching { Api.setScanImageUrl(id, offImage); changed = true }
        if (bmp != null && offImage == null) {
            runCatching {
                val bytes = withContext(Dispatchers.Default) { com.sohum.bandlog.util.Images.jpeg(com.sohum.bandlog.util.Images.fitWithin(bmp, 320), 80) }
                Api.uploadScanThumb(id, bytes); changed = true
            }.onFailure { android.util.Log.w("LockedIn", "Scan thumbnail failed", it) }
        }
        if (changed) runCatching { onDone() }
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

/** Low confidence sorts first — the rows that most need a second look. */
private val CONF_RANK = mapOf("low" to 0, "medium" to 1, "high" to 2)

/**
 * The Cal AI-style review: each item with editable grams, calories, P/C/F, a Micros expander and a
 * confidence dot. [onSave] gets the edited items plus the server-stored photo path (if any).
 * v2.12 (Scan 2): the total ± range leads, then the one follow-up question, then the items as
 * cards (gram range, confidence, ⓘ sources, "Which one?"), then [saveLabel]. [onItemsChanged]
 * reports every edit (the scan screen's floating totals follow it). [showPhoto] off when the
 * photo is already on screen behind the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoReview(
    est: PlateEstimate, photo: Bitmap?, readOnly: Boolean,
    /** v2.9 "Not right? Pick another" with no variants: open the plate in Add food to change it. */
    onPickAnother: ((List<PlateItem>) -> Unit)? = null,
    showPhoto: Boolean = true,
    saveLabel: String = "Save as meal",
    onItemsChanged: ((List<PlateItem>) -> Unit)? = null,
    onSave: ((List<PlateItem>, String?) -> Unit)? = null,
) {
    val p = palette
    var items by remember(est) { mutableStateOf(est.items) }
    // v2.9: the row whose "Where's this from?" sheet is open, rows whose variant was picked, reported rows.
    var infoIdx by remember(est) { mutableStateOf<Int?>(null) }
    var confirmed by remember(est) { mutableStateOf(setOf<Int>()) }
    var reported by remember(est) { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()
    // v2.8: the un-scaled estimate for each row (including any follow-up effect already applied), so
    // a gram edit always rescales from the latest baseline, never from the server's raw estimate.
    var originals by remember(est) { mutableStateOf(est.items) }
    var open by remember(est) { mutableStateOf<Int?>(null) }
    var pickedEffect by remember(est) { mutableStateOf<String?>(null) }
    LaunchedEffect(items) { onItemsChanged?.invoke(items) }
    if (est.items.isEmpty()) {
        Card(Modifier.scanEnter(0)) { Text("Couldn't find food in that photo", fontWeight = FontWeight(700), color = p.ink); Text(est.plateNote, fontSize = 13.sp, color = p.muted) }
        return
    }
    val kcalTotal = totalKcalRange(items)

    // ---- the total ± range ----
    Column(Modifier.scanEnter(0, est).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showPhoto && photo != null) { Image(photo.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)) }
            else if (showPhoto && est.photoUrl != null) { RemoteImage(url = est.photoUrl, size = 52.dp, fallback = CameraIcon); Spacer(Modifier.width(12.dp)) }
            Text(
                buildAnnotatedString {
                    append(if (kcalTotal.plusMinus > 0) "~${kcalTotal.center}" else "${kcalTotal.center}")
                    withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted, letterSpacing = 0.sp)) {
                        append(if (kcalTotal.plusMinus > 0) " kcal ±${kcalTotal.plusMinus}" else " kcal")
                    }
                },
                Modifier.weight(1f).semantics { heading() },
                fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 34.sp,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight(800), color = p.red)) { append("${fmt(round1(items.sumOf { it.proteinG }))} g") }
                    append(" protein")
                },
                fontSize = 13.sp, color = p.ink,
            )
        }
        Text(
            (est.plateNote.ifBlank { "Your plate" }) + " · an estimate, edit anything.",
            fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp),
        )
        if (est.portionHint == "restaurant") {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.background(p.orangeBg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Restaurant portion · ×1.4 + hidden oil", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.orange)
            }
        }
    }

    // ---- the one follow-up question, e.g. "Homemade or restaurant?" ----
    est.followUp?.takeIf { it.options.isNotEmpty() }?.let { fu ->
        Column(
            Modifier.scanEnter(1, est).fillMaxWidth().background(p.purpleBg, RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(fu.question, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fu.options.forEach { o ->
                    val picked = pickedEffect == o.effect
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(CircleShape)
                            .clickable {
                                pickedEffect = o.effect
                                items = applyFollowUpEffect(items, o.effect, fu.question)
                                originals = applyFollowUpEffect(originals, o.effect, fu.question)
                            }
                            .semantics { role = Role.RadioButton; selected = picked },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            o.label, fontSize = 12.sp, fontWeight = FontWeight(700), color = if (picked) p.card else p.ink,
                            modifier = Modifier
                                .background(if (picked) p.ink else Color.Transparent, CircleShape)
                                .border(1.dp, if (picked) p.ink else p.muted.copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    // ---- the items, low confidence first ----
    Column(Modifier.scanEnter(2, est), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val order = items.indices.sortedBy { CONF_RANK[items[it].confidence] ?: 1 }
        order.forEach { idx ->
            val it = items[idx]
            val low = it.confidence == "low"
            val (cc, cl) = when (it.confidence) { "high" -> p.green to "high"; "medium" -> p.orange to "medium"; else -> p.orange to "low" }
            val micros = MICRO_LABELS.filter { (k, _) -> it.micros[k] != null }
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().background(p.card, shape).then(if (low) Modifier.border(BorderStroke(1.dp, p.orange), shape) else Modifier)
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.name + if (it.source == "estimated") " ~" else "", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Confidence $cl" }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(cc, CircleShape))
                        Text(" $cl", fontSize = 11.sp, color = p.muted)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        buildAnnotatedString {
                            append("${it.calories.toInt()}")
                            withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight(600), color = p.muted)) { append(" kcal") }
                        },
                        fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink,
                    )
                    InfoButton(it.name, idx !in confirmed && Sources.needsCheck(it)) { infoIdx = idx }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (it.gramsLow != null && it.gramsHigh != null) Text(it.gramsRangeLabel(), fontSize = 12.sp, color = p.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MacroDot("${fmt(it.proteinG)}g", p.red); MacroDot("${fmt(it.carbsG)}g", p.orange); MacroDot("${fmt(it.fatG)}g", p.blue)
                            if (micros.isNotEmpty()) Box(
                                Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp)).clickable { open = if (open == idx) null else idx }.semantics { role = Role.Button }.padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (open == idx) "Hide" else "Micros", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink) }
                        }
                    }
                    if (!readOnly) {
                        var g by remember(idx, est) { mutableStateOf(fmt(round1(it.grams))) }
                        NumberField(g, { v ->
                            g = v.filter { c -> c.isDigit() || c == '.' }
                            // Always scale from the ORIGINAL estimate, never the already-scaled item — and
                            // ignore empty/unparsable/≤0 input so the previous value sticks instead of zeroing.
                            val d = g.toDoubleOrNull()
                            if (d != null && d > 0) items = items.toMutableList().also { l -> l[idx] = originals[idx].withGrams(d) }
                        }, "g")
                        IconButton(onClick = {
                            items = items.filterIndexed { i, _ -> i != idx }
                            originals = originals.filterIndexed { i, _ -> i != idx }
                            confirmed = confirmed.filter { i -> i != idx }.map { i -> if (i > idx) i - 1 else i }.toSet()
                            reported = reported.filter { i -> i != idx }.map { i -> if (i > idx) i - 1 else i }.toSet()
                        }, Modifier.size(48.dp)) { Icon(Icons.Outlined.Delete, "Remove ${it.name}", tint = p.muted, modifier = Modifier.size(20.dp)) }
                    } else {
                        Text("${it.grams.roundToInt()} g", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(end = 10.dp))
                    }
                }
                if (it.uncertainties.isNotEmpty()) Text(it.uncertainties.joinToString(" · "), fontSize = 11.sp, color = p.orange, modifier = Modifier.padding(end = 8.dp))
                // v2.9 "Which one?" — one tap swaps the row (and its unscaled original) at the same grams.
                if (!readOnly && it.variants.size > 1) VariantChips(it.variants, it.foodId, { v ->
                    items = items.toMutableList().also { l -> l[idx] = Sources.swap(l[idx], v) }
                    originals = originals.toMutableList().also { l -> if (idx in l.indices) l[idx] = Sources.swap(l[idx], v) }
                    confirmed = confirmed + idx
                })
                AnimatedVisibility(open == idx) {
                    Text(micros.joinToString("  ·  ") { (k, lu) -> "${lu.first} ${fmt(round1(it.micros[k]!!))} ${lu.second}" }, fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
    if (est.notes.isNotEmpty()) Text(est.notes.joinToString(" · "), fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp).scanEnter(3, est))
    infoIdx?.let { idx ->
        val row = items.getOrNull(idx)
        if (row == null) { infoIdx = null; return@let }
        SourceSheet(
            name = row.name,
            info = row.sourceInfo ?: Sources.fallback(row.toMealItem()) ?: Sources.forRow(null, row.name),
            confidence = Sources.confidenceLabel(row.confidence),
            per100 = Sources.per100Note(row.grams, row.calories, row.proteinG, row.carbsG, row.fatG),
            variants = row.variants, currentId = row.foodId,
            reported = idx in reported,
            onDismiss = { infoIdx = null },
            onPickVariant = if (readOnly) null else { v ->
                items = items.toMutableList().also { l -> l[idx] = Sources.swap(l[idx], v) }
                originals = originals.toMutableList().also { l -> if (idx in l.indices) l[idx] = Sources.swap(l[idx], v) }
                confirmed = confirmed + idx
                infoIdx = null
            },
            onPickAnother = if (readOnly || onPickAnother == null) null else ({ infoIdx = null; onPickAnother(items) }),
            onReport = {
                reported = reported + idx
                val note = "[source report] ${row.name} · food_id=${row.foodId ?: "none"} · ${row.sourceInfo?.label ?: row.source} · ${Sources.per100Note(row.grams, row.calories, row.proteinG, row.carbsG, row.fatG)}"
                scope.launch { runCatching { Api.feedback("down", row.name, null, note) } }
            },
        )
    }
    if (!readOnly && onSave != null) {
        Box(Modifier.scanEnter(4, est)) { PillButton(saveLabel, { onSave(items, est.photoPath) }, enabled = items.isNotEmpty()) }
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

/** Short, single-line trust labels for the History chip. Null (no chip) when the verdict is blank. */
@Composable
private fun trustChip(verdict: String): Pair<Color, String>? {
    val p = palette
    return when (verdict.trim().lowercase()) {
        "safe" -> p.green to "Trust"
        "caution" -> p.orange to "Caution"
        "unsafe" -> p.red to "Avoid"
        "misleading" -> p.purple to "Misleading"
        "fake" -> p.red to "Fake"
        else -> null
    }
}

@Composable
private fun HistoryRow(it: ScanHistoryItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val p = palette
    val kindIcon = when { it.kind == "barcode" -> BarcodeIcon; it.kind == "menu" -> com.sohum.bandlog.ui.nutrition.NutritionIcons.Menu; it.isPlate -> CameraIcon; else -> TagIcon }
    val kindLabel = when { it.kind == "barcode" -> "Barcode"; it.kind == "menu" -> "Menu"; it.isPlate -> "Plate"; else -> "Label" }
    val trust = trustChip(it.verdict)
    Card(padding = 12.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Own thumbnail (private bucket) → Open Food Facts picture → the plate's meal photo; with
            // none of those, a food picture for the product name (v2.4) → kind icon.
            val plateless = it.thumbPath == null && it.imageUrl == null && !(it.isPlate && it.imagePath != null)
            if (plateless && !ScanHistoryItem.isJunkName(it.product)) com.sohum.bandlog.ui.components.FoodImage(
                it.displayName, kind = if (it.isPlate) "generic" else "product", size = 48.dp,
                fallback = kindIcon, fallbackTint = p.muted,
            ) else RemoteImage(
                privatePath = it.thumbPath, url = it.imageUrl, storagePath = if (it.isPlate) it.imagePath else null,
                size = 48.dp, radius = 12.dp, fallback = kindIcon,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(it.displayName, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (it.whatItIs.isNotBlank()) Text(it.whatItIs, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                Text(
                    "$kindLabel · ${dayOf(it.createdAt)}" + (it.score?.let { s -> " · $s/10" } ?: ""),
                    fontSize = 11.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (trust != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.widthIn(min = 64.dp).background(trust.first.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(trust.second, fontSize = 11.sp, fontWeight = FontWeight(700), color = trust.first, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
            }
            IconButton(onClick = onDelete, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = p.muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** A stored scan, opened from History: the same views, read-only. */
@Composable
private fun ScanDetailPage(item: ScanHistoryItem, onLogged: () -> Unit, onLogServing: (com.sohum.bandlog.data.MealItem) -> Unit, onBack: () -> Unit) {
    val p = palette
    var json by remember(item.id) { mutableStateOf<org.json.JSONObject?>(null) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) { runCatching { Api.scanReport(item.id) }.onSuccess { json = it }.onFailure { error = it.message } }
    SubPage(item.displayName, onBack) {
        val o = json
        when {
            error != null -> ErrorNote(error)
            o == null -> { LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track); Text("Opening…", fontSize = 12.sp, color = p.muted) }
            item.isPlate -> PhotoReview(PlateEstimate.from(o), null, readOnly = true)
            // v2.13: a saved restaurant-menu scan; + opens Add food with that dish on the plate.
            item.kind == "menu" -> MenuResultView(com.sohum.bandlog.data.MenuScan.from(o), com.sohum.bandlog.util.WhatToEat.Remaining(0.0, 0.0, 0.0, 0.0), "balanced", localPick = false) { d -> onLogServing(d.toMealItem()); true }
            else -> ReportView(LabelReport.from(o), onLogged = onLogged, onLogServing = onLogServing)
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

/**
 * v2.7: downscale for the network upload only — on-device barcode/OCR reading (Barcode.read,
 * Ocr.read) already ran on the full-resolution [decodeScaled] bitmap before this is called, so
 * accuracy there is unaffected. A meal photo needs less detail than a label's nutrition table, so
 * the two flows shrink to different sizes before they leave the phone.
 */
internal fun scaleForUpload(b: Bitmap, maxEdge: Int): Bitmap {
    val longest = maxOf(b.width, b.height)
    if (longest <= maxEdge) return b
    val k = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true)
}
