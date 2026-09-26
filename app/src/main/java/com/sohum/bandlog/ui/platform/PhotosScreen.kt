package com.sohum.bandlog.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.PhotoV2
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.ScanIcon
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v2.13 progress photos manager (spec §11, free): add from camera or gallery with date, note,
 * weight and pose; edit those; delete the row and the Storage object (confirm, then Undo); a grid
 * grouped by month; pick two for the before/after slider (Pro); and "Share to squad" only after an
 * explicit confirmation (photos are private by default).
 */
@Composable
fun PhotosScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { pvm.loadPhotos() }
    var addSheet by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var detail by remember { mutableStateOf<PhotoV2?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<PhotoV2>>(emptyList()) }
    var undo by remember { mutableStateOf<PhotoV2?>(null) }
    var undoTick by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    val captureFile = remember { java.io.File(java.io.File(ctx.cacheDir, "scans").apply { mkdirs() }, "progress.jpg") }

    fun decode(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 1400) }.getOrNull() }
            if (bmp == null) err = "Couldn't open that photo" else pending = bmp
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { decode(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) decode(android.net.Uri.fromFile(captureFile)) }

    // Undo window for a delete: the row is hidden now and really deleted after 5 s.
    LaunchedEffect(undoTick) {
        val ph = undo ?: return@LaunchedEffect
        delay(5000)
        if (undo?.id == ph.id) { pvm.commitDeletePhoto(ph); undo = null }
    }

    // Leaving inside the Undo window still deletes (it never silently comes back); Progress re-reads its strip.
    val undoNow by androidx.compose.runtime.rememberUpdatedState(undo)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { undoNow?.let { pvm.commitDeletePhoto(it) }; vm.loadProgressPhotos() }
    }

    PageFrame("Progress photos", onBack) {
        val groups = pvm.photos.filter { it.id != undo?.id }.groupBy { it.date.take(7) }.toSortedMap(compareByDescending { it })
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Add photo", { addSheet = true }, Modifier.weight(1f), icon = CameraIcon)
                        PillButton(
                            if (comparing) "Cancel" else "Before / after", {
                                if (!pvm.hasPro) { PlatformNav.open(PlatformPage.PRO); return@PillButton }
                                comparing = !comparing; picked = emptyList()
                            },
                            Modifier.weight(1f), enabled = pvm.photos.size >= 2, bg = p.card2, fg = p.ink,
                        )
                    }
                    if (comparing) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (picked.size) { 0 -> "Pick the before photo"; 1 -> "Now pick the after photo"; else -> "" },
                            fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f),
                        )
                        ProChip()
                    }
                    Text("Private: only you can see these unless you share one.", fontSize = 12.sp, color = p.muted)
                    ErrorNote(err ?: pvm.error)
                    if (pvm.photosLoaded && pvm.photos.isEmpty()) Text(
                        "Snap one a week, same spot, same light, and watch the change.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            groups.forEach { (month, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$month") {
                    Text(
                        runCatching { Dates.month(java.time.LocalDate.parse("$month-01")) }.getOrDefault(month),
                        fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(list, key = { it.id }) { ph ->
                    val sel = picked.indexOfFirst { it.id == ph.id }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.8f).clickable {
                            if (comparing) {
                                picked = if (sel >= 0) picked.filter { it.id != ph.id } else (picked + ph).takeLast(2)
                                if (picked.size == 2) {
                                    val (a, b) = picked.sortedBy { it.date }.let { it[0] to it[1] }
                                    PlatformNav.compare = a to b; comparing = false; picked = emptyList()
                                    PlatformNav.open(PlatformPage.BEFORE_AFTER)
                                }
                            } else detail = ph
                        },
                    ) {
                        var w by remember { mutableIntStateOf(0) }
                        val wDp = with(LocalDensity.current) { w.toDp() }
                        Box(Modifier.fillMaxSize().onSizeChanged { w = it.width }) {
                            if (w > 0) RemoteImage(privatePath = ph.path, bucket = "progress-photos", size = wDp, radius = 14.dp, fallback = ScanIcon, modifier = Modifier.fillMaxSize())
                        }
                        Text(
                            Dates.parse(ph.date).dayOfMonth.toString() + (ph.pose?.let { " · " + PhotoV2.poseLabel(it) } ?: ""),
                            fontSize = 11.sp, fontWeight = FontWeight(700), color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                        if (sel >= 0) Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(accentColor, CircleShape).border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (sel == 0) "1" else "2", fontSize = 12.sp, fontWeight = FontWeight(800), color = Color.White) }
                    }
                }
            }
        }
    }

    undo?.let { ph ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            com.sohum.bandlog.ui.log.UndoSnackbar(
                "Photo from ${Dates.short(ph.date)} deleted", Modifier.navigationBarsPadding().padding(16.dp),
                onUndo = { undo = null },
            )
        }
    }

    if (addSheet) BottomSheet(title = "Progress photo", subtitle = "Private: only you can see these", onDismiss = { addSheet = false }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
            addSheet = false
            runCatching { camera.launch(androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", captureFile)) }
                .onFailure { err = "No camera app found. Choose a photo instead." }
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(CameraIcon, null, tint = p.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp))
            Text("Take photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
        }
        Hair()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
            addSheet = false
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, verticalAlignment = Alignment.CenterVertically) {
            Icon(ScanIcon, null, tint = p.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp))
            Text("Choose from gallery", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
        }
    }

    pending?.let { bmp ->
        val latest = vm.weights.firstOrNull()?.weightKg ?: vm.profile.weightKg
        PhotoDetailsSheet(
            pvm, title = "Add details", initial = PhotoV2("", Dates.today(), "", "", latest?.let { kotlin.math.round(it * 10) / 10 }, "front"),
            preview = bmp, onDismiss = { pending = null },
            onSave = { ph -> pvm.uploadPhoto(bmp, ph.date, ph.note, ph.weightKg, ph.pose).also { if (it) pending = null } },
        )
    }

    detail?.let { ph ->
        PhotoDetailsSheet(
            pvm, title = "Photo details", initial = ph, preview = null, onDismiss = { detail = null },
            onSave = { next -> pvm.updatePhoto(next).also { if (it) detail = null } },
            onDelete = { detail = null; undo = ph; undoTick++ },
        )
    }
}

/**
 * Date, note, weight and pose for a new or saved photo, plus (saved only) Share to squad and
 * Delete, each behind its own confirmation.
 */
@Composable
private fun PhotoDetailsSheet(
    pvm: PlatformViewModel, title: String, initial: PhotoV2, preview: android.graphics.Bitmap?,
    onDismiss: () -> Unit, onSave: suspend (PhotoV2) -> Boolean, onDelete: (() -> Unit)? = null,
) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initial.date) }
    var note by remember { mutableStateOf(initial.note) }
    var weight by remember { mutableStateOf(initial.weightKg?.let { num1(it) }.orEmpty()) }
    var pose by remember { mutableStateOf(initial.pose) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf<String?>(null) }
    BottomSheet(
        title = title, onDismiss = onDismiss, primary = if (busy) "Saving…" else "Save", primaryEnabled = !busy,
        onPrimary = {
            val kg = weight.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            if (weight.isNotBlank() && (kg == null || kg !in 20.0..350.0)) { err = "That weight looks off."; return@BottomSheet }
            busy = true; err = null
            scope.launch {
                val ok = onSave(initial.copy(date = date, note = note.trim(), weightKg = kg, pose = pose))
                busy = false
                if (!ok) err = pvm.error ?: "Couldn't save. Try again."
            }
        },
    ) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(220.dp).background(p.card2, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (preview != null) androidx.compose.foundation.Image(
                    preview.asImageBitmap(), null, Modifier.fillMaxSize().padding(0.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                ) else RemoteImage(privatePath = initial.path, bucket = "progress-photos", size = 220.dp, radius = 16.dp, fallback = ScanIcon)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { pickDate(ctx, date) { date = it } }, verticalAlignment = Alignment.CenterVertically) {
                Text("Date", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                Text(Dates.relative(date), fontSize = 15.sp, color = p.muted)
            }
            Hair()
            if (pvm.photoExtrasSupported) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Weight", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                    com.sohum.bandlog.ui.log.NumberField(weight, { v -> weight = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, "kg")
                }
                Hair()
                Text("Pose", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoV2.POSES.forEach { ps -> Chip(PhotoV2.poseLabel(ps), pose == ps, { pose = if (pose == ps) null else ps }) }
                }
                Spacer(Modifier.height(8.dp))
            } else Text("Weight and pose arrive with the next update.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(vertical = 8.dp))
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(p.card2, RoundedCornerShape(12.dp)).padding(12.dp)) {
                com.sohum.bandlog.ui.log.PlainField(note, { note = it.take(200) }, "Note (optional)")
            }
            ErrorNote(err)
            shared?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.green, modifier = Modifier.padding(vertical = 6.dp)) }
            if (onDelete != null) {
                Hair()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { confirmShare = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(LineIcons.Share, null, tint = p.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp))
                    Text("Share to squad", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                }
                Hair()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { confirmDelete = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(com.sohum.bandlog.ui.components.CrossIcon, null, tint = p.red, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp))
                    Text("Delete photo", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.red)
                }
            }
            if (busy) CircularProgressIndicator(Modifier.padding(8.dp).size(18.dp), strokeWidth = 2.dp, color = p.ink)
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Delete this photo?", "It's removed from your account and storage. You'll have a few seconds to undo.", "Delete", danger = true,
        onConfirm = { confirmDelete = false; onDelete?.invoke() }, onDismiss = { confirmDelete = false },
    )
    if (confirmShare) ConfirmDialog(
        "Share to your squads?", "Progress photos are private. This posts a copy of this photo to every squad you're in, where members can see it. You can delete the post from the squad later.",
        "Yes, share it", onConfirm = {
            confirmShare = false; busy = true
            scope.launch {
                val n = pvm.sharePhotoToSquad(initial, note.trim().ifBlank { "Progress check-in" })
                busy = false
                if (n == null) err = pvm.error ?: "Couldn't share it" else shared = if (n == 0) "You're not in a squad yet." else "Shared to $n squad${if (n == 1) "" else "s"}"
            }
        }, onDismiss = { confirmShare = false },
    )
}
