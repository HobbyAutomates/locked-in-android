package com.sohum.bandlog.util

import kotlin.math.roundToInt

/**
 * Calories burned from the 2024 Compendium of Physical Activities MET table (bandlog.activities):
 * kcal = MET × body weight (kg) × hours. Intensity nudges the MET by ±15%. Same maths as the web
 * app's burn.ts so a session logged on either surface lands on the same number.
 */
object Burn {
    /** Used when the profile has no weight yet. */
    const val DEFAULT_WEIGHT_KG = 60.0

    /** Compendium 12150 "running, general". */
    const val RUN_CODE = "12150"
    const val RUN_MET = 8.3

    fun multiplier(intensity: String): Double = when (intensity) { "low" -> 0.85; "high" -> 1.15; else -> 1.0 }

    fun kcal(met: Double, weightKg: Double?, minutes: Int, intensity: String = "medium"): Double =
        round1(met * multiplier(intensity) * (weightKg ?: DEFAULT_WEIGHT_KG) * minutes / 60.0)

    // ---- band workouts: one row per level, intensity already baked into the MET ----

    fun bandCode(level: String): String = when (level) { "Light" -> "LI-BAND-L"; "Heavy" -> "LI-BAND-H"; else -> "LI-BAND-M" }
    fun bandMet(level: String): Double = when (level) { "Light" -> 3.5; "Heavy" -> 6.0; else -> 5.0 }
    fun bandIntensity(level: String): String = when (level) { "Light" -> "low"; "Heavy" -> "high"; else -> "medium" }
    fun bandLevel(intensity: String): String = when (intensity) { "low" -> "Light"; "high" -> "Heavy"; else -> "Medium" }
    fun bandKcal(level: String, weightKg: Double?, minutes: Int): Double = round1(bandMet(level) * (weightKg ?: DEFAULT_WEIGHT_KG) * minutes / 60.0)

    fun intensityLabel(intensity: String): String = when (intensity) { "low" -> "Low"; "high" -> "High"; else -> "Medium" }

    // ---- v2.3: the Low → High intensity slider (0–100) ----

    /** Where the slider sits by default: exactly ×1.0, i.e. the plain MET-table number. */
    const val DEFAULT_PCT = 40

    /** 0 → ×0.8, 40 → ×1.0, 100 → ×1.3 (linear). */
    fun pctMultiplier(pct: Int): Double = 0.8 + 0.5 * pct.coerceIn(0, 100) / 100.0

    fun kcalPct(met: Double, weightKg: Double?, minutes: Int, pct: Int): Double =
        round1(met * pctMultiplier(pct) * (weightKg ?: DEFAULT_WEIGHT_KG) * minutes / 60.0)

    /** The legacy low / medium / high column, derived from the slider. */
    fun intensityFromPct(pct: Int): String = when { pct < 25 -> "low"; pct <= 75 -> "medium"; else -> "high" }

    /** Plain-English band for the slider. */
    fun pctLabel(pct: Int): String = when {
        pct < 25 -> "Easy: could sing"
        pct < 50 -> "Moderate: can talk in sentences"
        pct <= 75 -> "Hard: heavy breathing, short sentences"
        else -> "All out: can't talk"
    }

    fun pctShort(pct: Int): String = when { pct < 25 -> "Easy"; pct < 50 -> "Moderate"; pct <= 75 -> "Hard"; else -> "All out" }

    /** Band level for the slider when the activity is a band workout. */
    fun bandLevelFromPct(pct: Int): String = when { pct < 25 -> "Light"; pct <= 75 -> "Medium"; else -> "Heavy" }

    /** Backs the MET out of a logged row (for the Recent row): kcal / (kg × h) / intensity multiplier. */
    fun metOf(kcal: Double, weightKg: Double?, minutes: Int, intensity: String, pct: Int?): Double {
        if (minutes <= 0) return 0.0
        val mult = pct?.let { pctMultiplier(it) } ?: multiplier(intensity)
        return kcal / ((weightKg ?: DEFAULT_WEIGHT_KG) * minutes / 60.0) / mult
    }

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
}
