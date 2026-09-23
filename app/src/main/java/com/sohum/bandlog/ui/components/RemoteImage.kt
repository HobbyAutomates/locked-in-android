package com.sohum.bandlog.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.ui.theme.palette

/** Tiny in-memory cache so a list of rows doesn't refetch the same thumbnails on every recomposition. */
private val cache = object : android.util.LruCache<String, Bitmap>(24) {}

/**
 * A rounded thumbnail loaded off the main thread. [url] is fetched as-is; [storagePath] is a
 * meal-photos path that is signed first. Shows [fallback] on a grey tile until (or unless) it loads.
 */
@Composable
fun RemoteImage(
    url: String? = null,
    storagePath: String? = null,
    size: Dp = 56.dp,
    radius: Dp = 14.dp,
    fallback: ImageVector? = null,
    fallbackTint: Color = palette.muted,
    fallbackBg: Color = palette.card2,
    modifier: Modifier = Modifier,
) {
    val key = url ?: storagePath
    val bitmap by produceState<Bitmap?>(initialValue = key?.let { cache.get(it) }, key) {
        if (key == null || value != null) return@produceState
        val resolved = url ?: storagePath?.let { Api.signedPhotoUrl(it) }
        val bmp = resolved?.let { Api.fetchBitmap(it) }
        if (bmp != null) { cache.put(key, bmp); value = bmp }
    }
    val bmp = bitmap
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(fallbackBg), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        else if (fallback != null) Icon(fallback, null, tint = fallbackTint, modifier = Modifier.size(size * 0.45f))
    }
}
