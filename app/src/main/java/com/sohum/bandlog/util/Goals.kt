package com.sohum.bandlog.util

import com.sohum.bandlog.data.Profile
import kotlin.math.roundToInt

/**
 * "✨ Auto Generate Goals" — Mifflin-St Jeor BMR, a light activity multiplier, then the user's
 * lose/maintain/gain intent at their chosen speed. Protein 1.8 g/kg, fat 25% of calories, carbs
 * take the remainder. Same maths the Cal AI onboarding uses, so the numbers look familiar.
 */
object Goals {

    data class Targets(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

    /** Human-readable list of what Personal details is still missing; empty means [generate] works. */
    fun missing(p: Profile): List<String> = buildList {
        if (p.weightKg == null || p.weightKg <= 0) add("current weight")
        if (p.heightCm == null || p.heightCm <= 0) add("height")
        if (p.age == null) add("date of birth")
        if (p.gender.isNullOrBlank()) add("gender")
    }

    fun generate(p: Profile): Targets? {
        if (missing(p).isNotEmpty()) return null
        val kg = p.weightKg!!
        val cm = p.heightCm!!
        val age = p.age!!
        // Mifflin-St Jeor, with the midpoint of the two sex constants for "other".
        val sex = when (p.gender) { "male" -> 5.0; "female" -> -161.0; else -> -78.0 }
        val bmr = 10 * kg + 6.25 * cm - 5 * age + sex
        // Light-activity baseline; hitting the bands 3+ times a week nudges it up a notch.
        val activity = if (p.weeklyWorkoutTarget >= 3) 1.45 else 1.40
        val tdee = bmr * activity

        // 1 kg of body mass ≈ 7700 kcal, spread across the week.
        val delta = p.goalSpeedKgWk.coerceIn(0.1, 1.5) * 7700.0 / 7.0
        val calories = when (p.goalType) {
            "lose" -> tdee - delta
            "gain" -> tdee + delta
            else -> tdee
        }.coerceAtLeast(1200.0)

        val protein = (1.8 * kg).roundToInt()
        val fat = (calories * 0.25 / 9.0).roundToInt()
        val carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return Targets(calories.roundToInt(), protein, carbs, fat)
    }

    /** Copy of [p] with freshly generated targets applied (unchanged when details are missing). */
    fun applyTo(p: Profile): Profile {
        val t = generate(p) ?: return p
        return p.copy(calorieTarget = t.calories, proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat)
    }

    /** Slider copy for the weekly speed, matching Cal AI's three bands. */
    fun speedLabel(kgPerWeek: Double): String = when {
        kgPerWeek < 0.5 -> "Slow and steady"
        kgPerWeek <= 0.8 -> "Recommended"
        else -> "Aggressive"
    }
}
