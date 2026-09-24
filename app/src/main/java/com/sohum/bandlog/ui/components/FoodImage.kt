package com.sohum.bandlog.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * v2.4 food pictures. Resolution order for a food: the row's own `image_url` → a known URL for the
 * same food id / name (registered when presets load) → `GET /api/food-image?q=&kind=` (asked once
 * per name per process, at most 3 requests at a time; hits persist in cacheDir) → the icon/emoji
 * tile. Bitmaps are downsampled and kept in a small LRU plus a disk copy, so lists never refetch.
 */
object FoodImages {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)
    /** food id / normalised name → a URL that came from the database. */
    private val known = ConcurrentHashMap<String, String>()
    /** "name|kind" → URL, or "" when the service had nothing (this process only). */
    private val lookedUp = ConcurrentHashMap<String, String>()
    private val lookups = ConcurrentHashMap<String, Deferred<String?>>()
    private val loads = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val bitmaps = object : android.util.LruCache<String, Bitmap>(80) {}
    @Volatile private var dir: File? = null

    fun norm(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Remember a database URL for a food so rows that only carry the id or name can show it. */
    fun register(foodId: String?, name: String?, url: String?) {
        if (url.isNullOrBlank()) return
        foodId?.takeIf { it.isNotBlank() }?.let { known["id:$it"] = url }
        name?.takeIf { it.isNotBlank() }?.let { known["n:${norm(it)}"] = url }
    }

    fun knownUrl(foodId: String?, name: String?): String? =
        foodId?.let { known["id:$it"] } ?: name?.let { known["n:${norm(it)}"] }

    fun cachedBitmap(url: String): Bitmap? = bitmaps.get(url)

    private fun dir(ctx: Context): File = dir ?: File(ctx.cacheDir, "food-images").apply { mkdirs() }.also { dir = it }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The picture URL for [name] from the food-image service; null when it has none. */
    suspend fun lookup(ctx: Context, name: String, kind: String): String? {
        val n = norm(name)
        if (n.length < 2) return null
        val key = "$n|$kind"
        lookedUp[key]?.let { return it.ifBlank { null } }
        val job = lookups.getOrPut(key) {
            scope.async {
                val f = File(dir(ctx), "q-${hash(key)}")
                val disk = runCatching { if (f.exists()) f.readText().trim() else null }.getOrNull()
                if (!disk.isNullOrBlank()) return@async disk
                val url = gate.withPermit { runCatching { Api.foodImage(name.trim(), kind) }.getOrNull() }
                if (url != null) runCatching { f.writeText(url) }
                url
            }
        }
        val r = runCatching { job.await() }.getOrNull()
        lookedUp[key] = r.orEmpty()
        lookups.remove(key)
        return r
    }

    /** Downloads (or reads from disk) and downsamples [url] to about [px] on its short edge. */
    suspend fun bitmap(ctx: Context, url: String, px: Int): Bitmap? {
        bitmaps.get(url)?.let { return it }
        val job = loads.getOrPut(url) {
            scope.async {
                val f = File(dir(ctx), "img-${hash(url)}")
                val cached = runCatching { if (f.exists() && f.length() > 0) f.readBytes() else null }.getOrNull()
                val bytes = cached ?: gate.withPermit { Api.fetchBytes(url) }?.also { b -> runCatching { f.writeBytes(b) } }
                bytes?.let { decode(it, px) }?.also { bitmaps.put(url, it) }
            }
        }
        val b = runCatching { job.await() }.getOrNull()
        loads.remove(url)
        return b
    }

    private fun decode(bytes: ByteArray, px: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        val short = minOf(o.outWidth, o.outHeight)
        while (short / (sample * 2) >= px) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** The food-image `kind` for a meal item / search hit source. */
    fun kindFor(source: String?): String = when (source) { "scan", "off" -> "product"; else -> "generic" }
}

/**
 * A food's picture: [imageUrl] when the row has one, else whatever [FoodImages] can find for
 * [name] (swapped in when it lands), else [emoji] or [fallback] on the grey tile.
 * Rounded 12 dp, centre-cropped, with a 1 dp hairline border.
 */
@Composable
fun FoodImage(
    name: String,
    imageUrl: String? = null,
    kind: String = "generic",
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    foodId: String? = null,
    emoji: String? = null,
    fallback: ImageVector = BowlIcon,
    fallbackTint: Color = palette.orange,
    fallbackBg: Color = palette.card2,
) {
    val p = palette
    val ctx = LocalContext.current.applicationContext
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(64, 360)
    val direct = imageUrl?.takeIf { it.isNotBlank() } ?: FoodImages.knownUrl(foodId, name)
    val bmp by produceState(initialValue = direct?.let { FoodImages.cachedBitmap(it) }, name, direct, kind) {
        if (value != null) return@produceState
        val url = direct ?: if (name.isBlank()) null else FoodImages.lookup(ctx, name, kind)
        if (url != null) FoodImages.bitmap(ctx, url, px)?.let { value = it }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.size(size).clip(shape).background(fallbackBg).border(1.dp, p.hair, shape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        when {
            b != null -> Image(b.asImageBitmap(), name, Modifier.size(size), contentScale = ContentScale.Crop)
            !emoji.isNullOrBlank() -> Text(emoji, fontSize = (size.value * 0.46f).sp)
            else -> Icon(fallback, null, tint = fallbackTint, modifier = Modifier.size(size * 0.45f))
        }
    }
}
