package com.sohum.bandlog.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.LabelReport
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.ChevronDownIcon
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.QuestionIcon
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScanIcon
import com.sohum.bandlog.ui.components.SpoonIcon
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/** Under this many characters the phone's read is treated as a miss and the photo goes up too. */
private const val OCR_MIN_CHARS = 120

/** Bottom-nav tab: title + the scanner. */
@Composable
fun ScanTab() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
            com.sohum.bandlog.ui.components.ScreenTitle("Scan")
            Text("Ingredients list or nutrition panel", fontSize = 13.sp, color = palette.muted)
            Text("Scans take ~8 s now — the reading happens on your phone", fontSize = 11.sp, color = palette.muted)
        }
        Box(Modifier.weight(1f).padding(bottom = 96.dp)) { ScanForm() }
    }
}

/**
 * Photograph an ingredients / nutrition label. ML Kit reads it on the phone straight away; the
 * words (which you can correct first) go to the server for the verdict, and only a short read
 * sends the picture as well.
 */
@Composable
fun ScanForm() {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var ocr by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<LabelReport?>(null) }
    val target = remember { mutableStateOf<Uri?>(null) }

    /** New photo → clear the old read and immediately run the recogniser over it. */
    fun accept(bitmap: Bitmap?) {
        photo = bitmap; report = null; ocr = ""; error = null
        val bmp = bitmap ?: return
        scope.launch {
            reading = true
            ocr = runCatching { Ocr.read(bmp) }.getOrDefault("")
            reading = false
            showText = ocr.isNotBlank()
            if (ocr.length < OCR_MIN_CHARS) showText = true
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) target.value?.let { uri -> scope.launch { accept(withContext(Dispatchers.IO) { decodeScaled(ctx, uri) }) } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { accept(withContext(Dispatchers.IO) { decodeScaled(ctx, uri) }) }
    }

    fun openCamera() {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        val f = File(dir, "label.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        target.value = uri
        runCatching { camera.launch(uri) }.onFailure { error = "No camera app found — pick from gallery instead." }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Rise(0) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(ScanIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Scan an ingredients label", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                            Text("Safe, fake or misleading? Is the protein real? Should you eat it?", fontSize = 12.sp, color = p.muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val bmp = photo
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), "Label photo", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(if (bmp == null) "Take photo" else "Retake", { openCamera() }, Modifier.weight(1f), height = 46.dp)
                        PillButton("Gallery", { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), height = 46.dp, bg = p.card2, fg = p.ink)
                    }
                    if (bmp != null) {
                        Spacer(Modifier.height(10.dp))
                        WhatIRead(ocr, reading, showText, { showText = !showText }) { ocr = it }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            BasicTextField(
                                note, { note = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (note.isEmpty()) Text("Optional: what is it / what do you want to know", fontSize = 14.sp, color = p.muted); inner() },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val short = ocr.trim().length < OCR_MIN_CHARS
                        PillButton(if (busy) "Analysing…" else "Analyse", enabled = !busy && !reading, height = 48.dp, onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val text = ocr.trim()
                                    // Only ship the picture when the phone's own read came up short.
                                    val b64 = if (text.length < OCR_MIN_CHARS) withContext(Dispatchers.IO) { toJpegBase64(bmp) } else null
                                    report = Api.scanLabel(text, note, b64)
                                } catch (e: Exception) { error = e.message } finally { busy = false }
                            }
                        })
                        if (busy) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
                            Text(
                                if (short) "Your phone couldn't read enough, so the photo is going up too — 20–45 s."
                                else "Checking the brand and writing your report. About 8 seconds.",
                                fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            ErrorNote(error)
            report?.let { ReportView(it) }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
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
                    BasicTextField(
                        text, onChange, Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 12.sp, color = p.ink, lineHeight = 17.sp), cursorBrush = SolidColor(p.ink),
                        decorationBox = { inner -> if (text.isEmpty()) Text("Nothing was recognised. Type the label here, or just hit Analyse and we'll read the photo on the server.", fontSize = 12.sp, color = p.muted); inner() },
                    )
                }
            }
        }
    }
}

// ---- the report, as an infographic ----

@Composable
private fun ReportView(r: LabelReport) {
    val p = palette
    if (!r.readable) {
        Rise(1) { Card { Text("Couldn't read that as a food label", fontWeight = FontWeight(700), color = p.ink); Text(r.verdictReason, fontSize = 13.sp, color = p.muted) } }
        return
    }
    val g = r.infographic
    val score = g.score
    val scoreColor = when { score >= 7 -> p.green; score >= 4 -> p.orange; else -> p.red }

    // 1. Hero: the score, the one-liner, and the eat-it call.
    Rise(1) {
        Card(padding = 18.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.product.ifBlank { "Unknown product" }, fontSize = 19.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, lineHeight = 23.sp)
                    if (g.oneLiner.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(g.oneLiner, fontSize = 14.sp, color = p.muted, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Ring(score / 10f, scoreColor, 84.dp, 9.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 30.sp)
                        Text("/ 10", fontSize = 10.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val (eatColor, eatLabel) = when (g.eatIt) {
                "yes" -> p.green to "Eat it"
                "skip" -> p.red to "Skip it"
                else -> p.orange to "Sometimes"
            }
            Box(Modifier.background(eatColor.copy(alpha = 0.16f), CircleShape).padding(14.dp, 8.dp)) {
                Text(eatLabel, fontSize = 13.sp, fontWeight = FontWeight(800), color = eatColor)
            }
        }
    }

    // 2. Verdict meter.
    Rise(2) { VerdictMeter(r.verdict, r.verdictReason) }

    // 3. One serving against the day.
    if (g.caloriesPct + g.proteinPct + g.carbsPct + g.fatPct > 0) Rise(3) {
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

    // 4. Sugar, counted in teaspoons.
    val spoons = g.sugarTsp.roundToInt()
    if (g.sugarTsp > 0.04) Rise(4) {
        Card {
            Text("Sugar", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(spoons.coerceIn(0, 12)) {
                    Icon(SpoonIcon, null, tint = p.orange, modifier = Modifier.size(19.dp).padding(end = 2.dp))
                }
                if (spoons > 12) Text("+", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.orange)
                if (spoons == 0) Text("under half a spoon", fontSize = 13.sp, color = p.muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (spoons == 0) "Less than 1 tsp of sugar per serving" else "$spoons tsp of sugar per serving",
                fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted,
            )
        }
    }

    // 5. Salt.
    if (g.sodiumPct > 0) Rise(5) {
        Card {
            RowSpaceBetween {
                Text("Salt", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("${g.sodiumPct}%", fontSize = 15.sp, fontWeight = FontWeight(800), color = if (g.sodiumPct >= 40) p.red else if (g.sodiumPct >= 20) p.orange else p.green)
            }
            Spacer(Modifier.height(10.dp))
            FillBar(g.sodiumPct / 100f, if (g.sodiumPct >= 40) p.red else if (g.sodiumPct >= 20) p.orange else p.green)
            Spacer(Modifier.height(8.dp))
            Text("% of a day's salt", fontSize = 12.sp, color = p.muted)
        }
    }

    // 6. Protein.
    Rise(6) {
        Card {
            val pc = when (r.proteinRating) { "excellent", "good" -> p.green; "average" -> p.orange; else -> p.red }
            val gauge = when (r.proteinRating) { "excellent" -> 1f; "good" -> 0.75f; "average" -> 0.45f; else -> 0.15f }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(gauge, pc, 64.dp, 8.dp) {
                    Text(r.proteinPerServing?.let { "${it.roundToInt()}g" } ?: "—", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Protein · ${r.proteinRating.replaceFirstChar { it.uppercase() }}", fontSize = 15.sp, fontWeight = FontWeight(700), color = pc)
                    if (r.proteinQuality.isNotBlank()) Text(r.proteinQuality, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (r.proteinNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(r.proteinNote, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
            }
        }
    }

    // 7. Ingredients as traffic lights.
    if (r.concerns.isNotEmpty()) Rise(7) {
        Card {
            Text("Ingredients", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            r.concerns.forEach { (ing, issue, sev) ->
                val c = when (sev) { "high" -> p.red; "medium" -> p.orange; else -> p.green }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.background(c.copy(alpha = 0.16f), CircleShape).padding(12.dp, 6.dp)) {
                    Text(ing, fontSize = 13.sp, fontWeight = FontWeight(700), color = c)
                }
                Spacer(Modifier.height(4.dp))
                Text(issue, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
            }
        }
    }

    // 8. Claims.
    if (r.claims.isNotEmpty()) Rise(8) {
        Card {
            Text("Claims on the pack", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            r.claims.forEach { (claim, status, why) ->
                Spacer(Modifier.height(10.dp))
                val (c, icon) = when (status) {
                    "supported" -> p.green to CheckIcon
                    "misleading", "false" -> p.red to CrossIcon
                    else -> p.orange to QuestionIcon
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).background(c.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = c, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("“$claim”", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, lineHeight = 19.sp)
                        Text(why, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                    }
                }
            }
        }
    }

    // 9. For you / better options / the web.
    if (r.suggestions.isNotEmpty()) Rise(9) {
        Column(Modifier.fillMaxWidth().background(p.btn, RoundedCornerShape(20.dp)).padding(18.dp)) {
            Text("For you", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk)
            r.suggestions.forEach {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("•  ", color = p.btnInk.copy(alpha = 0.6f), fontSize = 14.sp)
                    Text(it, fontSize = 14.sp, color = p.btnInk, lineHeight = 20.sp)
                }
            }
        }
    }
    if (r.alternatives.isNotEmpty()) Rise(10) {
        Card {
            Text("Better options", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(10.dp))
            r.alternatives.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { alt ->
                        Box(Modifier.weight(1f).background(p.card2, CircleShape).padding(12.dp, 9.dp)) {
                            Text(alt, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 2)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (r.research.isNotEmpty()) Rise(11) { Collapsible("What the web says", r.research) }
}

/** Safe · Caution · Unsafe · Misleading · Fake, with the one that applies filled in. */
@Composable
private fun VerdictMeter(verdict: String, reason: String) {
    val p = palette
    val segments = listOf(
        "safe" to p.green, "caution" to p.orange, "unsafe" to p.red,
        "misleading" to p.purple, "fake" to p.red,
    )
    val active = segments.indexOfFirst { it.first == verdict }
    Card {
        Text("Verdict", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            segments.forEachIndexed { i, (_, c) ->
                Box(Modifier.weight(1f).height(10.dp).background(if (i == active) c else p.track, CircleShape))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            segments.forEachIndexed { i, (label, c) ->
                Text(
                    label.replaceFirstChar { it.uppercase() },
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 9.sp, fontWeight = if (i == active) FontWeight(800) else FontWeight(500),
                    color = if (i == active) c else p.muted,
                )
            }
        }
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(reason, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
        }
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
    Box(modifier.fillMaxWidth().height(height).background(p.track, CircleShape)) {
        Box(Modifier.fillMaxWidth(a).height(height).background(color, CircleShape))
    }
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
            Column {
                lines.forEach {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•  ", color = p.muted, fontSize = 14.sp)
                        Text(it, fontSize = 14.sp, color = p.muted, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

private fun decodeScaled(ctx: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

private fun toJpegBase64(b: Bitmap): String {
    val out = ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.JPEG, 88, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
