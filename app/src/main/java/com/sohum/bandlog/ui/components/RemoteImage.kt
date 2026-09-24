package com.sohum.bandlog.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.ui.theme.palette

/** Tiny in-memory cache so a list of rows doesn't refetch the same thumbnails on every recomposition. */
private val cache = object : android.util.LruCache<String, Bitmap>(40) {}

/**
 * A rounded thumbnail loaded off the main thread. Sources are tried in order until one loads:
 * [privatePath] (an object in the private [bucket], fetched with the user's token through the
 * authenticated Storage endpoint), then [url] as-is, then [storagePath] (a meal-photos path that
 * is signed first). Shows [fallback] on a grey tile until (or unless) one loads.
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
    privatePath: String? = null,
    bucket: String = "scan-photos",
) {
    val bmp = rememberRemoteBitmap(url, storagePath, privatePath, bucket)
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(fallbackBg), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        else if (fallback != null) Icon(fallback, null, tint = fallbackTint, modifier = Modifier.size(size * 0.45f))
    }
}

/** Seeds the cache (e.g. with the avatar just uploaded) so the new picture shows without a refetch. */
fun primeImageCache(url: String, bitmap: Bitmap) { cache.put(url, bitmap) }

@Composable
private fun rememberRemoteBitmap(url: String?, storagePath: String?, privatePath: String?, bucket: String): Bitmap? {
    val key = listOfNotNull(privatePath?.let { "$bucket/$it" }, url, storagePath).joinToString("|").ifBlank { null }
    val bitmap by produceState<Bitmap?>(initialValue = key?.let { cache.get(it) }, key) {
        if (key == null || value != null) return@produceState
        val bmp = privatePath?.let { Api.fetchStorageBitmap(bucket, it) }
            ?: url?.let { Api.fetchBitmap(it) }
            ?: storagePath?.let { sp -> Api.signedPhotoUrl(sp)?.let { Api.fetchBitmap(it) } }
        if (bmp != null) { cache.put(key, bmp); value = bmp }
    }
    return bitmap
}

/**
 * A round profile picture: the public avatar URL when there is one, else [initials] on a grey
 * circle (also shown while the picture loads or if it fails).
 */
@Composable
fun Avatar(url: String?, initials: String, size: Dp = 52.dp, modifier: Modifier = Modifier, bg: Color = palette.card2, ink: Color = palette.ink) {
    val bmp = rememberRemoteBitmap(url, null, null, "")
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        else androidx.compose.material3.Text(initials, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight(700), color = ink, maxLines = 1)
    }
}
