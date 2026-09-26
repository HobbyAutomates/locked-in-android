package com.sohum.bandlog.util

import kotlin.math.floor

/**
 * v2.13 §7 fasting timer — a port of the web's src/lib/fasting.ts: protocols, stages and the "who
 * can use it" rule. The wording is soft on purpose: rough, commonly cited windows, not medical claims.
 */
object Fasting {
    data class Protocol(val key: String, val label: String, val fastHours: Double, val eatHours: Double)

    val PROTOCOLS = listOf(
        Protocol("12:12", "12:12", 12.0, 12.0),
        Protocol("14:10", "14:10", 14.0, 10.0),
        Protocol("16:8", "16:8", 16.0, 8.0),
        Protocol("18:6", "18:6", 18.0, 6.0),
        Protocol("20:4", "20:4", 20.0, 4.0),
    )
    const val DEFAULT_HOURS = 16.0
    const val MIN_HOURS = 1.0
    const val MAX_HOURS = 72.0

    private fun jsRound(v: Double) = floor(v + 0.5)

    /** Target hours clamped to 1–72, to the nearest half hour (NaN → 16). */
    fun clampHours(h: Double): Double {
        if (!h.isFinite()) return DEFAULT_HOURS
        return minOf(MAX_HOURS, maxOf(MIN_HOURS, jsRound(h * 2) / 2))
    }

    /** "16:8" for the standard protocols, else "20 h" / "30.5 h". */
    fun label(hours: Double): String = PROTOCOLS.firstOrNull { it.fastHours == hours }?.label ?: "${fmtH(hours)} h"

    data class Stage(val key: String, val label: String, val from: Double, val note: String)

    val STAGES = listOf(
        Stage("fed", "Fed", 0.0, "Your body is still using your last meal."),
        Stage("fat_burning", "Fat-burning", 12.0, "Around 12 hours in, many people start leaning more on stored fat."),
        Stage("ketosis", "Ketosis", 18.0, "From about 18 hours, ketone levels tend to rise. It varies a lot between people."),
    )

    fun stageAt(hours: Double): Stage = STAGES.last { hours >= it.from }

    /** Hours between two instants (ms), never negative. */
    fun hoursBetween(startMs: Long, endMs: Long): Double = maxOf(0.0, (endMs - startMs) / 3_600_000.0)

    /** "15:42:08" for an elapsed number of milliseconds. */
    fun clock(ms: Long): String {
        val s = maxOf(0L, ms / 1000)
        return String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** "16 h" / "16.5 h" / "45 min". */
    fun durationText(hours: Double): String {
        if (hours < 1) return "${jsRound(hours * 60).toLong()} min"
        val r = jsRound(hours * 10) / 10
        return "${fmtH(r)} h"
    }

    fun fmtH(h: Double): String = if (h == floor(h)) h.toLong().toString() else String.format(java.util.Locale.US, "%.1f", h)

    data class Access(val ok: Boolean, val reason: String? = null, val title: String = "", val body: String = "")

    /** Hidden under 18 and for anyone the eating-disorder safety screen flags (Goals.edFlags). */
    fun access(age: Int?, flags: List<String> = emptyList()): Access = when {
        Goals.isTeen(age) -> Access(false, "teen", "Not available under 18", "Your body is still growing, so Locked In doesn't offer fasting timers under 18. Regular meals fuel growing, training and school.")
        flags.isNotEmpty() -> Access(false, "safety", "Not available right now", "Fasting isn't a good fit when food or weight feels heavy. Regular meals are the kinder plan for now. If food has been on your mind a lot, talking to someone can really help.")
        else -> Access(true)
    }
}
