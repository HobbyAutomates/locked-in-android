package com.sohum.bandlog.ui.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device label reading via ML Kit Text Recognition v2 (Play Services variant, so the model is
 * downloaded once rather than shipped in the APK). The photo never leaves the phone for this step,
 * and the whole thing is best-effort: any failure — no Play Services, model not downloaded yet,
 * a recogniser crash — resolves to an empty string and the caller falls back to the server's
 * vision path.
 */
object Ocr {

    /** Recognised text, or "" when anything at all went wrong. */
    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        var done = false
        fun finish(value: String) {
            if (!done && cont.isActive) { done = true; cont.resume(value) }
        }
        runCatching {
            val input = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(input)
                .addOnSuccessListener { result -> finish(result.text.trim()) }
                .addOnFailureListener { finish("") }
                .addOnCanceledListener { finish("") }
        }.onFailure { finish("") }
    }
}
