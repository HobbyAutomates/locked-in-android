package com.sohum.bandlog.ui.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device barcode reading via ML Kit (Play Services variant, so the model is downloaded once
 * rather than shipped in the APK). Only retail formats are requested — EAN-13, EAN-8, UPC-A,
 * UPC-E — and the first value found wins. Best-effort like [Ocr]: any failure resolves to null.
 */
object Barcode {

    /** The digits of the first retail barcode in [bitmap], or null when nothing was found. */
    suspend fun read(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        var done = false
        fun finish(value: String?) {
            if (!done && cont.isActive) { done = true; cont.resume(value) }
        }
        runCatching {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { codes ->
                    val value = codes.asSequence().mapNotNull { it.rawValue?.filter(Char::isDigit) }.firstOrNull { it.length in 8..14 }
                    finish(value)
                }
                .addOnFailureListener { finish(null) }
                .addOnCanceledListener { finish(null) }
        }.onFailure { finish(null) }
    }
}
