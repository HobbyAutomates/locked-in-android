package com.sohum.bandlog.ui.social

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.sohum.bandlog.R
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.Display
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Recaps
import com.sohum.bandlog.util.Wrapped
import java.io.File

/** D1 Wrapped: week / month / year as 9:16 story slides, shared one at a time or all together. */
@Composable
fun WrappedScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(Wrapped.Kind.WEEK) }
    var selected by remember { mutableIntStateOf(0) }
    val today = Dates.today()
    val period = remember(kind, today) { Wrapped.period(kind, today) }
    // The app keeps 120 days; a year needs its own read.
    val stats = rememberLoad(period.key) {
        val rp = Recaps.Period(if (kind == Wrapped.Kind.WEEK) Recaps.Kind.WEEK else Recaps.Kind.MONTH, period.from, period.to)
        val inMemory = period.from >= Dates.addDays(today, -119)
        val r = if (inMemory) Recaps.compute(rp, vm.workouts, vm.meals, vm.exercises, vm.weights, vm.profile.proteinTargetG, vm.profile.weeklyWorkoutTarget, vm.dayStreak)
        else Recaps.compute(rp, Api.workouts(period.from, period.to), Api.meals(period.from, period.to), Api.exercises(period.from, period.to), vm.weights, vm.profile.proteinTargetG, vm.profile.weeklyWorkoutTarget, vm.dayStreak)
        Wrapped.fromRecap(r, period)
    }
    val resolver = LocalFontFamilyResolver.current
    val display by resolver.resolveAsTypeface(Display, FontWeight(800))
    val slides = stats.data?.let { Wrapped.slides(it, kind, vm.profile.name) }.orEmpty()
    // Previews at a quarter size (a full slide is 8 MB); shares render full size one at a time.
    val bitmaps = remember(slides, display) { slides.map { WrappedCards.render(ctx, it, period, display, 0.25f) } }

    SubPage(tr("wrapped.title"), onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wrapped.Kind.entries.forEach { k -> ChoicePill(k.id.replaceFirstChar { it.uppercase() }, k == kind, { kind = k; selected = 0 }) }
        }
        Text(period.label, fontSize = 13.sp, color = p.muted)
        when {
            stats.loading -> Spinner()
            stats.error != null -> com.sohum.bandlog.ui.components.ErrorNote(stats.error)
            else -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    bitmaps.forEachIndexed { i, b ->
                        Image(
                            b.asImageBitmap(), slides[i].eyebrow,
                            Modifier.width(if (i == selected) 200.dp else 150.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp)).clickable { selected = i },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                slides.getOrNull(selected)?.let { s -> Text("${selected + 1} of ${slides.size} · ${s.eyebrow}", fontSize = 13.sp, color = p.muted) }
                PillButton("Share this slide", { slides.getOrNull(selected)?.let { s -> WrappedCards.share(ctx, 1) { WrappedCards.render(ctx, s, period, display) } } }, bg = Ember, fg = BoneBrand)
                PillButton("Share all ${slides.size}", { WrappedCards.share(ctx, slides.size) { i -> WrappedCards.render(ctx, slides[i], period, display) } }, height = 46.dp)
                Text("Calories never appear on a slide.", fontSize = 12.sp, color = p.muted)
            }
        }
    }
}

/** Draws Wrapped slides (1080 × 1920, brand ink / bone / ember, Bricolage for the big line, Geist for the rest). */
object WrappedCards {
    const val W = 1080
    const val H = 1920

    private fun geist(ctx: Context, weight: Int, mono: Boolean = false): Typeface {
        val base = runCatching { ResourcesCompat.getFont(ctx, if (mono) R.font.geistmono else R.font.geist) }.getOrNull() ?: Typeface.DEFAULT
        return Typeface.create(base, weight, false)
    }

    fun render(ctx: Context, s: Wrapped.Slide, period: Wrapped.Period, display: Typeface?, scale: Float = 1f): Bitmap {
        val bmp = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val ink = 0xFF0B0B0C.toInt(); val bone = 0xFFF4F1EA.toInt(); val ember = 0xFFFF5B1F.toInt()
        val (bg, fg, accent, muted) = when (s.tone) {
            "ember" -> listOf(ember, bone, bone, 0xCCF4F1EA.toInt())
            "bone" -> listOf(bone, ink, ember, 0xFF6B665E.toInt())
            else -> listOf(ink, bone, ember, 0xFF8F8A82.toInt())
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { color = bg })
        if (s.tone == "ember") c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFFFF7A3D.toInt(), 0xFFC2410C.toInt(), Shader.TileMode.CLAMP) })
        if (s.tone == "ink") c.drawCircle(W * 0.85f, H * 0.2f, 700f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(W * 0.85f, H * 0.2f, 700f, 0x40FF5B1F, 0x00FF5B1F, Shader.TileMode.CLAMP) })
        val pad = 96f
        fun text(t: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface, spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT) {
            c.drawText(t, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf; letterSpacing = spacing; textAlign = align })
        }
        text("LOCKED IN", pad, 190f, 40f, fg, geist(ctx, 600, true), 0.28f)
        text(period.label.uppercase(), W - pad, 190f, 32f, muted, geist(ctx, 500, true), 0.12f, Paint.Align.RIGHT)
        // Eyebrow pill.
        val ebPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = geist(ctx, 600, true); letterSpacing = 0.2f }
        val eyebrow = s.eyebrow.uppercase().take(28)
        val ebW = ebPaint.measureText(eyebrow) + 64f
        c.drawRoundRect(RectF(pad, 620f, pad + ebW, 692f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = accent })
        text(eyebrow, pad + 32f, 669f, 36f, accent, geist(ctx, 600, true), 0.2f)
        // The big line: shrink to fit the width.
        val bigTf = display ?: geist(ctx, 800)
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bigTf; color = fg; letterSpacing = -0.03f; textSize = 300f }
        while (big.measureText(s.big) > W - 2 * pad && big.textSize > 90f) big.textSize -= 10f
        c.drawText(s.big, pad - 6f, 1010f, big)
        // The line under it, wrapped to two lines.
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 60f; typeface = geist(ctx, 500); color = fg }
        var y = 1140f
        wrap(s.line, lp, W - 2 * pad).take(3).forEach { l -> c.drawText(l, pad, y, lp); y += 78f }
        text("Locked In", pad, H - 150f, 38f, accent, geist(ctx, 700))
        text("lockedin · wrapped", W - pad, H - 150f, 32f, muted, geist(ctx, 500, true), 0.08f, Paint.Align.RIGHT)
        return bmp
    }

    private fun wrap(t: String, p: Paint, width: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (w in t.split(' ')) {
            val next = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(next) > width && cur.isNotEmpty()) { out.add(cur); cur = w } else cur = next
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    fun save(ctx: Context, bmp: Bitmap, name: String = "wrapped-${System.nanoTime()}.png"): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }

    /** One image → ACTION_SEND; several → ACTION_SEND_MULTIPLE (a story sequence). */
    fun share(ctx: Context, count: Int, title: String = "Share", render: (Int) -> Bitmap) {
        if (count <= 0) return
        runCatching {
            shareUris(ctx, (0 until count).map { i -> val b = render(i); save(ctx, b).also { b.recycle() } }, title)
        }.onFailure { android.util.Log.w("LockedIn", "Share failed", it) }
    }

    /** One image → ACTION_SEND; several → ACTION_SEND_MULTIPLE. */
    fun shareUris(ctx: Context, list: List<Uri>, title: String = "Share") {
        if (list.isEmpty()) return
        runCatching {
            val uris = ArrayList(list)
            val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
            else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
            send.type = "image/png"
            send.clipData = ClipData.newRawUri("image", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { android.util.Log.w("LockedIn", "Share failed", it) }
    }
}
