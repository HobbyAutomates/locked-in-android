package com.sohum.bandlog.util

/**
 * v2.18 D10 transformation story: the timeline maths (pure). Port of the web's
 * src/lib/social/story.ts. Progress photos oldest → newest, each with the weight logged that day
 * (or the nearest one within 7 days), the change since the first, and the day index. Android shares
 * the frames as an image sequence (ACTION_SEND_MULTIPLE).
 */
object Story {
    const val FRAME_MS = 1400
    const val FADE_MS = 350
    const val INTRO_MS = 1600
    const val OUTRO_MS = 2200
    const val MAX_FRAMES = 24
    const val STORY_OPTIN_KEY = "li-story-optin"

    data class Photo(val date: String, val url: String?, val weightKg: Double?)
    data class Weight(val date: String, val weightKg: Double)
    data class Frame(val date: String, val url: String?, val kg: Double?, val delta: Double?, val dayIndex: Int)

    private fun dayNum(d: String): Long = java.time.LocalDate.parse(d).toEpochDay()

    /** The weight for a day: that day's entry, else the nearest within [window] days, else null. */
    fun weightNear(date: String, weights: List<Weight>, window: Int = 7): Double? {
        val t = dayNum(date)
        var best: Pair<Long, Double>? = null
        for (w in weights) {
            val gap = kotlin.math.abs(dayNum(w.date) - t)
            if (gap <= window && (best == null || gap < best.first)) best = gap to w.weightKg
        }
        return best?.second
    }

    /** Frames oldest first, at most [MAX_FRAMES] (evenly thinned, keeping the first and last). */
    fun frames(photos: List<Photo>, weights: List<Weight>): List<Frame> {
        val sorted = photos.filter { !it.url.isNullOrBlank() }.sortedBy { it.date }
        val pick = if (sorted.size > MAX_FRAMES) {
            val step = (sorted.size - 1).toDouble() / (MAX_FRAMES - 1)
            (0 until MAX_FRAMES).map { i -> sorted[Math.round(i * step).toInt()] }
        } else sorted
        val first = pick.firstOrNull() ?: return emptyList()
        val firstKg = first.weightKg ?: weightNear(first.date, weights)
        val d0 = dayNum(first.date)
        return pick.map { p ->
            val kg = p.weightKg ?: weightNear(p.date, weights)
            Frame(p.date, p.url, kg, if (kg != null && firstKg != null) Math.round((kg - firstKg) * 10) / 10.0 else null, (dayNum(p.date) - d0).toInt())
        }
    }

    /** How long the reel runs, in ms. */
    fun reelLength(frames: Int): Int = if (frames > 0) INTRO_MS + frames * FRAME_MS + OUTRO_MS else 0

    /** "Day 1" / "Day 43" and "−4.2 kg" (null on the first frame or without weights). */
    fun caption(f: Frame): Pair<String, String?> {
        val d = f.delta
        val change = if (d == null || f.dayIndex == 0) null else "${if (d > 0) "+" else if (d < 0) "−" else "±"}${Wrapped.num(kotlin.math.abs(d))} kg"
        return "Day ${f.dayIndex + 1}" to change
    }
}
