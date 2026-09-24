package com.sohum.bandlog.util

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Small bitmap helpers for avatars and scan thumbnails. */
object Images {
    /** Scales so the longest edge is at most [maxEdge] (never upscales). */
    fun fitWithin(b: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(b.width, b.height)
        if (longest <= maxEdge) return b
        val k = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(1), (b.height * k).roundToInt().coerceAtLeast(1), true)
    }

    /** Center-crops to a square, then scales it down to at most [maxEdge] px. */
    fun squareCrop(b: Bitmap, maxEdge: Int): Bitmap {
        val side = minOf(b.width, b.height)
        val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        return if (side <= maxEdge) sq else Bitmap.createScaledBitmap(sq, maxEdge, maxEdge, true)
    }

    fun jpeg(b: Bitmap, quality: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // JPEG has no alpha: flatten onto white so transparent PNGs don't turn black.
        val src = if (b.hasAlpha()) Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888).also { c ->
            android.graphics.Canvas(c).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        } else b
        src.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
