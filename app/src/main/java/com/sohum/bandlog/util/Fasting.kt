package com.sohum.bandlog.util

import kotlin.math.roundToInt

/**
 * v2.13 §7 fasting timer — pure helpers (the twin of the web's src/lib/fasting.ts). The stages use
 * soft wording on purpose: these are rough, average timings, not medical claims.
 */
object Fasting {
    data class Protocol(val key: String, val label: String, val hours: Double)

    val PROTOCOLS = listOf(
        Protocol("12:12", "12:12", 12.0),
        Protocol("14:10", "14:10", 14.0),
        Protocol("16:8", "16:8", 16.0),
        Protocol("18:6", "18:6", 18.0),
        Protocol("20:4", "20:4", 20.0),
    )
    const val MIN_HOURS = 1.0
    const val MAX_HOURS = 72.0
    const val DEFAULT_HOURS = 16.0

    fun clampHours(h: Double): Double = (h.coerceIn(MIN_HOURS, MAX_HOURS) * 10).roundToInt() / 10.0

    /** "16:8" for the standard protocols, else "20 h". */
    fun label(hours: Double): String = PROTOCOLS.firstOrNull { it.hours == hours }?.label ?: "${fmtH(hours)} h"

    data class Stage(val key: String, val label: String, val fromHours: Double, val blurb: String)

    val STAGES = listOf(
        Stage("fed", "Fed", 0.0, "Digesting your last meal and using that energy."),
        Stage("fat", "Fat-burning", 12.0, "Around now your body tends to lean more on stored fat."),
        Stage("ketosis", "Ketosis", 18.0, "Some people start making more ketones around here. It varies a lot."),
    )

    fun stageAt(hours: Double): Stage = STAGES.last { hours >= it.fromHours }

    fun elapsedHours(startedAtMs: Long, nowMs: Long): Double = ((nowMs - startedAtMs).coerceAtLeast(0L)) / 3_600_000.0

    fun progress(startedAtMs: Long, nowMs: Long, targetHours: Double): Float =
        (elapsedHours(startedAtMs, nowMs) / targetHours.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)

    /** "3:07:12" (h:mm:ss) for the ring. */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) / 1000)
        return String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** "15 h 20 m" for history rows. */
    fun duration(ms: Long): String {
        val m = (ms.coerceAtLeast(0L) / 60_000)
        val h = m / 60
        return if (h > 0) "$h h ${m % 60} m" else "${m % 60} m"
    }

    fun fmtH(h: Double): String = if (h == h.toLong().toDouble()) h.toLong().toString() else String.format(java.util.Locale.US, "%.1f", h)
}
