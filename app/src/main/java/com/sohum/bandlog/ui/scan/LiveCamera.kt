package com.sohum.bandlog.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * v2.13: the live camera in the scan frame (CameraX preview + still capture). Needs the CAMERA
 * permission; without it the stage keeps the old flow (the phone's camera app via TakePicture).
 * The captured frame goes through exactly the same accept() → barcode / OCR / plate / label / menu
 * logic as a photo from the camera app.
 */
class LiveCamera {
    var camera by mutableStateOf<Camera?>(null); internal set
    internal var capture: ImageCapture? = null
    var torch by mutableStateOf(false); internal set
    var busy by mutableStateOf(false); private set

    val ready: Boolean get() = camera != null && capture != null
    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    /** The flash as a torch: it lights the preview and the shot alike (labels and menus read better). */
    fun toggleTorch() {
        val c = camera ?: return
        torch = !torch
        runCatching { c.cameraControl.enableTorch(torch) }.onFailure { torch = false }
    }

    /** Takes one frame, upright and at most [maxEdge] px, on the main thread's callbacks. */
    fun takePicture(context: Context, maxEdge: Int = 2200, onBitmap: (Bitmap) -> Unit, onError: (String) -> Unit) {
        val ic = capture ?: return onError("The camera isn't ready yet.")
        if (busy) return
        busy = true
        ic.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = runCatching { upright(image, maxEdge) }.getOrNull()
                image.close()
                busy = false
                if (bmp != null) onBitmap(bmp) else onError("Couldn't read that photo. Try again.")
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                onError(exception.message ?: "The camera couldn't take that photo.")
            }
        })
    }

    private fun upright(image: ImageProxy, maxEdge: Int): Bitmap {
        val raw = image.toBitmap()
        val deg = image.imageInfo.rotationDegrees
        val longest = maxOf(raw.width, raw.height)
        val k = if (longest > maxEdge) maxEdge.toFloat() / longest else 1f
        if (deg == 0 && k == 1f) return raw
        val m = Matrix().apply { postScale(k, k); if (deg != 0) postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }

    companion object {
        fun permitted(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}

/** The PreviewView, bound to the back camera for as long as it's on screen. */
@Composable
fun LiveCameraPreview(cam: LiveCamera, modifier: Modifier = Modifier, onFailed: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()
                p.unbindAll()
                cam.camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                cam.capture = capture
            }.onFailure { onFailed(it.message ?: "Couldn't open the camera") }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { cam.camera?.cameraControl?.enableTorch(false) }
            cam.torch = false
            cam.camera = null; cam.capture = null
            runCatching { provider?.unbindAll() }
        }
    }
    AndroidView({ view }, modifier)
}
