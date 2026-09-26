package com.sohum.bandlog.ui.platform

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
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.sohum.bandlog.R
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Recaps
import com.sohum.bandlog.util.Training
import java.io.File

/**
 * v2.13 share cards (spec §13, Pro): 1080 × 1920 story images in the v2.12 premium style (near-
 * black ground, warm accent glow, Geist type) for a PR, a streak, today's summary and a recap.
 * Shared through the system sheet with Instagram Stories offered first when it's installed.
 * "Hide calorie numbers" drops every kcal line.
 */
object ShareCards {
    const val W = 1080
    const val H = 1920
    private const val INSTAGRAM = "com.instagram.android"
    private const val IG_STORY = "com.instagram.share.ADD_TO_STORY"

    /** One detail line; [kcal] lines are left off when numbers are hidden. */
    data class Line(val label: String, val value: String, val kcal: Boolean = false)

    data class Spec(
        val eyebrow: String,
        val big: String,
        val bigUnit: String,
        val title: String,
        val lines: List<Line>,
        val date: String = Dates.today(),
        /** True when [big] itself is a kcal figure (replaced by [title] alone when hidden). */
        val bigIsKcal: Boolean = false,
    )

    fun pr(exercise: String, pt: Training.Point, name: String = ""): Spec = Spec(
        eyebrow = "NEW PR", big = num1(pt.kg), bigUnit = "kg × ${pt.reps}", title = exercise,
        lines = listOf(Line("Estimated 1RM", "${num1(pt.e1rm)} kg"), Line("Set on", Dates.short(pt.date))) +
            (if (name.isNotBlank()) listOf(Line("Lifter", name)) else emptyList()),
        date = pt.date,
    )

    fun streak(days: Int, weekSessions: Int, weekTarget: Int): Spec = Spec(
        eyebrow = "STREAK", big = "$days", bigUnit = if (days == 1) "day" else "days", title = "Locked in, every day",
        lines = listOf(Line("This week", "$weekSessions of $weekTarget sessions")),
    )

    fun today(protein: Int, proteinTarget: Int, kcal: Int, kcalTarget: Int, sessions: Int, waterMl: Int): Spec = Spec(
        eyebrow = "TODAY", big = "$protein", bigUnit = "g protein", title = if (protein >= proteinTarget) "Protein goal hit" else "of $proteinTarget g",
        lines = listOf(
            Line("Calories", "$kcal / $kcalTarget kcal", kcal = true),
            Line("Workouts", if (sessions == 0) "Rest day" else "$sessions"),
            Line("Water", String.format(java.util.Locale.US, "%.1f L", waterMl / 1000.0)),
        ),
    )

    fun recap(r: Recaps.Recap): Spec = Spec(
        eyebrow = if (r.period.kind == Recaps.Kind.WEEK) "WEEK RECAP" else "MONTH RECAP",
        big = "${r.daysLogged}", bigUnit = "of ${r.period.days} days logged", title = r.period.label,
        lines = buildList {
            add(Line("Workouts", "${r.workouts} · ${r.minutes} min"))
            add(Line("Protein days hit", "${r.proteinDaysHit}"))
            r.bestLift?.let { (n, pt) -> add(Line(if (pt.pr) "New PR" else "Best lift", "$n ${num1(pt.kg)} kg × ${pt.reps}")) }
            r.weightChange?.let { d -> add(Line("Weight", (if (d > 0) "+" else if (d < 0) "−" else "±") + num1(kotlin.math.abs(d)) + " kg")) }
            if (r.streak > 0) add(Line("Streak", "${r.streak} days"))
        },
        date = r.period.to,
    )

    private fun font(ctx: Context, weight: Int, mono: Boolean = false): Typeface {
        val base = runCatching { ResourcesCompat.getFont(ctx, if (mono) R.font.geistmono else R.font.geist) }.getOrNull() ?: Typeface.DEFAULT
        return Typeface.create(base, weight, false)
    }

    /** Draws [spec] onto a 1080 × 1920 bitmap. */
    fun render(ctx: Context, spec: Spec, hideNumbers: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val accent = 0xFFFF8A3D.toInt()
        val ink = 0xFFF5F5F7.toInt()
        val muted = 0xFF9A9AA0.toInt()
        // Ground + glow.
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF141416.toInt(), 0xFF050505.toInt(), Shader.TileMode.CLAMP) })
        c.drawCircle(W * 0.82f, H * 0.18f, 720f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(W * 0.82f, H * 0.18f, 720f, 0x55FF8A3D, 0x00FF8A3D, Shader.TileMode.CLAMP) })
        c.drawCircle(W * 0.1f, H * 0.9f, 620f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(W * 0.1f, H * 0.9f, 620f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
        val pad = 96f
        fun text(s: String, x: Float, y: Float, size: Float, color: Int, weight: Int, mono: Boolean = false, spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT) {
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = font(ctx, weight, mono); letterSpacing = spacing; textAlign = align })
        }
        // Wordmark.
        text("LOCKED IN", pad, 190f, 40f, ink, 600, mono = true, spacing = 0.28f)
        // Eyebrow pill.
        val eb = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; typeface = font(ctx, 600, true); letterSpacing = 0.2f }
        val ebW = eb.measureText(spec.eyebrow) + 64f
        c.drawRoundRect(RectF(pad, 560f, pad + ebW, 632f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = accent })
        text(spec.eyebrow, pad + 32f, 609f, 36f, accent, 600, mono = true, spacing = 0.2f)
        // The big number (or, when it's kcal and numbers are hidden, the title in its place).
        val hideBig = hideNumbers && spec.bigIsKcal
        if (!hideBig) {
            val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = if (spec.big.length > 4) 250f else 330f; typeface = font(ctx, 700); letterSpacing = -0.04f; color = ink }
            c.drawText(spec.big, pad - 10f, 950f, bigPaint)
            text(spec.bigUnit, pad, 1040f, 58f, muted, 500)
        }
        text(spec.title.take(34), pad, if (hideBig) 950f else 1150f, 64f, ink, 600)
        // Detail lines.
        var y = 1330f
        spec.lines.filter { !(hideNumbers && it.kcal) }.take(5).forEach { l ->
            c.drawLine(pad, y - 70f, W - pad, y - 70f, Paint().apply { color = 0x33FFFFFF; strokeWidth = 2f })
            text(l.label, pad, y, 40f, muted, 500)
            text(l.value.take(30), W - pad, y, 44f, ink, 600, align = Paint.Align.RIGHT)
            y += 112f
        }
        text(Dates.long(spec.date), pad, H - 150f, 36f, muted, 500)
        text("Locked In", W - pad, H - 150f, 36f, accent, 600, align = Paint.Align.RIGHT)
        return bmp
    }

    /** Writes the card to cache/share and returns a content:// URI for it. */
    fun save(ctx: Context, bmp: Bitmap): android.net.Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val f = File(dir, "card-${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }

    fun instagramInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(INSTAGRAM, 0); true
    }.getOrDefault(false)

    /**
     * Renders and shares: the system share sheet, with an "Instagram Stories" target first
     * (`com.instagram.share.ADD_TO_STORY`) when Instagram is installed.
     */
    fun share(ctx: Context, spec: Spec, hideNumbers: Boolean) {
        runCatching {
            val uri = save(ctx, render(ctx, spec, hideNumbers))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("card", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share")
            if (instagramInstalled(ctx)) {
                val story = Intent(IG_STORY).apply {
                    setDataAndType(uri, "image/png")
                    setPackage(INSTAGRAM)
                    putExtra("source_application", ctx.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (story.resolveActivity(ctx.packageManager) != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(android.content.pm.LabeledIntent(story, INSTAGRAM, "Instagram Stories", 0)))
                }
            }
            ctx.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { android.util.Log.w("LockedIn", "Share card failed", it) }
    }
}
