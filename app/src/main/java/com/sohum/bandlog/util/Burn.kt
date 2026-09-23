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

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
}
