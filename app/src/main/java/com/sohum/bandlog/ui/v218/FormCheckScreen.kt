package com.sohum.bandlog.ui.v218

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.sohum.bandlog.data.V218Api
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.scan.LiveCamera
import com.sohum.bandlog.ui.theme.Brand
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.FormCheck
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * v2.18 C2 AI form check (Android): CameraX frames → ML Kit pose detection (on device, streaming)
 * → util/FormCheck's rep counter and tips for squat, push-up and lunge. No video is stored or sent;
 * only the set summary goes to /api/coach/form-check (kept when schema_v43 is there).
 */
@Composable
fun FormCheckScreen(onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ex by remember { mutableStateOf("squat") }
    var front by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(FormCheck.State("squat")) }
    var seen by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<FormCheck.Summary?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { state = FormCheck.State(ex); done = null; live = true } else error = "Camera permission is off for Locked In."
    }
    fun start() {
        error = null; saved = null
        if (LiveCamera.permitted(ctx)) { state = FormCheck.State(ex); done = null; live = true } else runCatching { permission.launch(Manifest.permission.CAMERA) }
    }
    fun finish() {
        live = false
        val s = FormCheck.summary(state)
        done = s
        if (s.reps > 0) scope.launch { saved = if (V218Api.saveFormCheck(ex, s.reps, s.clean, s.tips)) "Saved to your form-check history." else null }
    }
    val def = FormCheck.ex(ex)

    SubPage("Form check", { if (live) finish(); onBack() }) {
        if (!live) {
            Card(padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FormCheck.EXERCISES.forEach { e -> Chip(e.label, e.key == ex, { ex = e.key }) } }
                    Text(def.setup, fontSize = 13.sp, lineHeight = 18.sp, color = p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Front camera", front, { front = true })
                        Chip("Back camera", !front, { front = false })
                    }
                    ErrorNote(error)
                    PillButton(if (done != null) "Another set" else "Start camera", { start() }, height = 48.dp)
                    Text("Runs on your phone. Video never leaves it; only the rep count and tips are saved.", fontSize = 11.sp, lineHeight = 15.sp, color = p.muted)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(22.dp)).background(Brand.Ink)) {
                PoseCamera(front) { lm, t ->
                    seen = lm != null
                    val next = FormCheck.step(state, lm?.let { FormCheck.measure(ex, it) }, t)
                    if (next != state) state = next
                }
                Column(Modifier.padding(12.dp).background(Brand.Ink.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(12.dp, 8.dp)) {
                    Text(def.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.sp, color = Brand.Bone.copy(alpha = 0.7f))
                    Text("${state.reps}", fontSize = 40.sp, fontWeight = FontWeight(800), color = Brand.Bone)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(12.dp).background(if (seen) Color(0xFF2EA05A) else Brand.EmberDeep, CircleShape))
                FormCheck.tipText(state)?.let { tip ->
                    Text(
                        tip, fontSize = 14.sp, fontWeight = FontWeight(700), color = Brand.Ink,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                            .background(if (state.last == "good") Color(0xFF2EA05A) else Brand.EmberLight, RoundedCornerShape(16.dp)).padding(12.dp, 10.dp),
                    )
                }
            }
            if (!seen) Text("Step back until your whole body is in frame, side-on.", fontSize = 12.5.sp, color = p.muted)
            PillButton("Finish set", { finish() }, height = 48.dp, bg = p.card2, fg = p.ink)
        }
        done?.let { s ->
            Card(padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.reps} ${def.label.lowercase()}${if (s.reps == 1) "" else "s"}", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                        if (s.reps > 0) Box(Modifier.background(if (s.clean >= 70) p.greenBg else p.emberBg, CircleShape).padding(10.dp, 4.dp)) { Text("${s.clean}% clean", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (s.clean >= 70) p.green else p.ember) }
                    }
                    if (s.reps > 0) s.tips.forEach { Text("· $it", fontSize = 13.5.sp, lineHeight = 19.sp, color = p.ink) }
                    else Text("No reps counted. Make sure your whole body is in frame, side-on, and move through the full range.", fontSize = 13.sp, color = p.muted)
                    saved?.let { Text(it, fontSize = 12.sp, color = p.muted) }
                }
            }
        }
    }
}

/** CameraX preview + an ML Kit pose analyzer; [onPose] gets pixel landmarks (null = nobody in frame). */
@Composable
private fun PoseCamera(front: Boolean, onPose: (Map<Int, FormCheck.P>?, Long) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    DisposableEffect(owner, front) {
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        val exec = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val pr = future.get()
                provider = pr
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(exec) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    val t = System.currentTimeMillis()
                    detector.process(input)
                        .addOnSuccessListener(main) { pose ->
                            val lms = pose.allPoseLandmarks
                            onPose(if (lms.isEmpty()) null else lms.associate { it.landmarkType to FormCheck.P(it.position.x.toDouble(), it.position.y.toDouble(), it.inFrameLikelihood.toDouble()) }, t)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                pr.unbindAll()
                pr.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            runCatching { provider?.unbindAll() }
            runCatching { detector.close() }
            exec.shutdown()
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}
