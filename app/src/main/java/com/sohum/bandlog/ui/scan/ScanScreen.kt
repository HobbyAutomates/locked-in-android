package com.sohum.bandlog.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.LabelReport
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.RowSpaceBetween
import com.sohum.bandlog.ui.components.ScanIcon
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Bottom-nav tab: title + the scanner. */
@Composable
fun ScanTab() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
            com.sohum.bandlog.ui.components.ScreenTitle("Scan")
            Text("Ingredients list or nutrition panel", fontSize = 13.sp, color = palette.muted)
        }
        Box(Modifier.weight(1f).padding(bottom = 96.dp)) { ScanForm() }
    }
}

/** Take a photo of an ingredients / nutrition label → Haiku reads it, researches the product, reports. */
@Composable
fun ScanForm() {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<LabelReport?>(null) }
    val target = remember { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) target.value?.let { uri -> scope.launch { photo = withContext(Dispatchers.IO) { decodeScaled(ctx, uri) }; report = null } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { decodeScaled(ctx, uri) }; report = null }
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
                        Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            BasicTextField(
                                note, { note = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 14.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                                decorationBox = { inner -> if (note.isEmpty()) Text("Optional: what is it / what do you want to know", fontSize = 14.sp, color = p.muted); inner() },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        PillButton(if (busy) "Reading label and researching…" else "Analyse", enabled = !busy, height = 48.dp, onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val b64 = withContext(Dispatchers.IO) { toJpegBase64(bmp) }
                                    report = Api.scanLabel(b64, note)
                                } catch (e: Exception) { error = e.message } finally { busy = false }
                            }
                        })
                        if (busy) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track); Text("Usually 15–40 s — it may search the web for recalls and lab tests.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
            }
            ErrorNote(error)
            report?.let { ReportView(it) }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

@Composable
private fun ReportView(r: LabelReport) {
    val p = palette
    val (vColor, vBg, vLabel) = when (r.verdict) {
        "safe" -> Triple(p.green, p.greenBg, "Safe")
        "caution" -> Triple(p.orange, p.orangeBg, "Caution")
        "unsafe" -> Triple(p.red, p.redBg, "Unsafe")
        "misleading" -> Triple(p.purple, p.purpleBg, "Misleading")
        "fake" -> Triple(p.red, p.redBg, "Likely fake")
        else -> Triple(p.muted, p.card2, r.verdict)
    }
    if (!r.readable) {
        Rise(1) { Card { Text("Couldn't read that as a food label", fontWeight = FontWeight(700), color = p.ink); Text(r.verdictReason, fontSize = 13.sp, color = p.muted) } }
        return
    }
    Rise(1) {
        Card {
            RowSpaceBetween {
                Text(r.product.ifBlank { "Unknown product" }, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
                Box(Modifier.background(vBg, CircleShape).padding(10.dp, 5.dp)) { Text(vLabel, fontSize = 12.sp, fontWeight = FontWeight(700), color = vColor) }
            }
            Spacer(Modifier.height(6.dp))
            Text(r.verdictReason, fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
            if (r.per100.isNotEmpty()) {
                Spacer(Modifier.height(10.dp)); Hair(); Spacer(Modifier.height(10.dp))
                Text("Per 100 g", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("calories" to "kcal", "protein_g" to "g P", "carbs_g" to "g C", "sugar_g" to "g sugar", "fat_g" to "g F", "sodium_mg" to "mg Na").forEach { (k, u) ->
                        r.per100[k]?.let { Column { Text("${it.toInt()}", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink); Text(u, fontSize = 10.sp, color = p.muted) } }
                    }
                }
            }
        }
    }
    Rise(2) {
        Card {
            val pc = when (r.proteinRating) { "excellent", "good" -> p.green; "average" -> p.orange; else -> p.red }
            RowSpaceBetween {
                Text("Protein", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Box(Modifier.background(pc.copy(alpha = 0.16f), CircleShape).padding(10.dp, 5.dp)) { Text(r.proteinRating.replaceFirstChar { it.uppercase() } + (r.proteinPerServing?.let { " · ${it.toInt()} g / serving" } ?: ""), fontSize = 12.sp, fontWeight = FontWeight(700), color = pc) }
            }
            Spacer(Modifier.height(6.dp))
            Text(r.proteinQuality, fontSize = 14.sp, color = p.ink)
            if (r.proteinNote.isNotBlank()) Text(r.proteinNote, fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (r.concerns.isNotEmpty()) Rise(3) {
        Card {
            Text("Ingredients to know about", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            r.concerns.forEach { (ing, issue, sev) ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).background(when (sev) { "high" -> p.red; "medium" -> p.orange; else -> p.muted }, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column { Text(ing, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink); Text(issue, fontSize = 13.sp, color = p.muted) }
                }
            }
        }
    }
    if (r.claims.isNotEmpty()) Rise(4) {
        Card {
            Text("Claims on the pack", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            r.claims.forEach { (claim, status, why) ->
                Spacer(Modifier.height(8.dp))
                val c = when (status) { "supported" -> p.green; "misleading", "false" -> p.red; else -> p.orange }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("“$claim”", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                    Text(status, fontSize = 12.sp, fontWeight = FontWeight(700), color = c)
                }
                Text(why, fontSize = 13.sp, color = p.muted)
            }
        }
    }
    if (r.research.isNotEmpty()) Rise(5) { Bullets("What the web says", r.research) }
    if (r.suggestions.isNotEmpty()) Rise(6) { Bullets("For you", r.suggestions, strong = true) }
    if (r.alternatives.isNotEmpty()) Rise(7) { Bullets("Better options", r.alternatives) }
}

@Composable
private fun Bullets(title: String, lines: List<String>, strong: Boolean = false) {
    val p = palette
    Card(padding = 16.dp) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
        lines.forEach {
            Spacer(Modifier.height(6.dp))
            Row { Text("•  ", color = p.muted); Text(it, fontSize = 14.sp, color = if (strong) p.ink else p.muted, lineHeight = 20.sp) }
        }
    }
}

private fun decodeScaled(ctx: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

private fun toJpegBase64(b: Bitmap): String {
    val out = ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@Suppress("unused") private val keepColor: Color = Color.Unspecified
