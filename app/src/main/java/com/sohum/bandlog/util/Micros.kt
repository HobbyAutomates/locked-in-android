package com.sohum.bandlog.util

import kotlin.math.roundToInt

/**
 * v2.13 §9 micronutrient dashboard — pure maths, the twin of the web's src/lib/micros.ts.
 *
 * Uses the micros the app already stores on meal_items (scaled to the portion): fiber_g, sugar_g,
 * sodium_mg, iron_mg, calcium_mg, vitamin_c_mg, potassium_mg. Targets: ICMR-NIN 2020 RDA by age
 * and sex (iron, calcium, vitamin C, potassium), fibre 30 g per 2000 kcal (ICMR-NIN; the profile's
 * own fibre goal wins when set), sugar under 10 % of calories (WHO), sodium under 2000 mg (WHO).
 *
 * TODO(before launch): re-check the adolescent vitamin C and potassium figures against the
 * ICMR-NIN 2020 RDA tables (nin.res.in/rdabook) — keep src/lib/micros.ts in step.
 */
object Micros {

    data class Nutrient(
        val key: String,
        val label: String,
        val unit: String,
        /** "min" = aim to reach it; "max" = stay under it. */
        val kind: String,
    )

    val NUTRIENTS = listOf(
        Nutrient("fiber_g", "Fibre", "g", "min"),
        Nutrient("iron_mg", "Iron", "mg", "min"),
        Nutrient("calcium_mg", "Calcium", "mg", "min"),
        Nutrient("vitamin_c_mg", "Vitamin C", "mg", "min"),
        Nutrient("potassium_mg", "Potassium", "mg", "min"),
        Nutrient("sugar_g", "Sugar", "g", "max"),
        Nutrient("sodium_mg", "Sodium", "mg", "max"),
    )

    /** WHO: sodium under 2 g a day. */
    const val SODIUM_MAX_MG = 2000.0
    /** WHO: free sugars under 10 % of calories (4 kcal per g). */
    fun sugarMaxG(calorieTarget: Int): Double = (calorieTarget * 0.10 / 4).roundToInt().toDouble()
    /** ICMR-NIN: 30 g fibre per 2000 kcal. */
    fun fibreFor(calorieTarget: Int): Double = (calorieTarget / 2000.0 * 30).roundToInt().toDouble()

    /** ICMR-NIN 2020 RDA (mg/day) by age band and sex. "other" / unset uses the mean of the two. */
    fun rda(key: String, age: Int?, sex: String?): Double? {
        val a = age ?: 25
        fun pick(boy: Double, girl: Double) = when (sex) { "male" -> boy; "female" -> girl; else -> (boy + girl) / 2 }
        return when (key) {
            "iron_mg" -> when {
                a <= 12 -> pick(16.0, 28.0)
                a <= 15 -> pick(22.0, 30.0)
                a <= 17 -> pick(26.0, 32.0)
                else -> pick(19.0, 29.0)
            }
            "calcium_mg" -> when {
                a <= 12 -> 850.0
                a <= 15 -> 1000.0
                a <= 17 -> 1050.0
                else -> 1000.0
            }
            "vitamin_c_mg" -> when {
                a <= 12 -> pick(58.0, 57.0)
                a <= 15 -> pick(79.0, 66.0)
                a <= 17 -> pick(88.0, 71.0)
                else -> pick(80.0, 65.0)
            }
            "potassium_mg" -> when {
                a <= 12 -> 3050.0
                a <= 15 -> 3750.0
                else -> 3510.0
            }
            else -> null
        }
    }

    /** The day's target for one nutrient. */
    fun target(key: String, age: Int?, sex: String?, calorieTarget: Int, fibreGoal: Int?): Double? = when (key) {
        "fiber_g" -> fibreGoal?.toDouble()?.takeIf { it > 0 } ?: fibreFor(calorieTarget)
        "sugar_g" -> sugarMaxG(calorieTarget)
        "sodium_mg" -> SODIUM_MAX_MG
        else -> rda(key, age, sex)
    }

    /** One logged item's micros (already scaled to its grams) with the day it was eaten. */
    data class Entry(val date: String, val micros: Map<String, Double>, val kcal: Double)

    data class Row(
        val nutrient: Nutrient,
        val target: Double,
        val today: Double,
        /** Mean over the logged days of the 7 ending today (days with nothing logged don't count). */
        val weekAvg: Double,
        /** Share of this week's kcal whose items carried this nutrient at all (0..1). */
        val coverage: Double,
    ) {
        val todayPct: Double get() = if (target <= 0) 0.0 else today / target
        val weekPct: Double get() = if (target <= 0) 0.0 else weekAvg / target
        /** A "min" nutrient under 70 % this week, or a "max" one over its limit. */
        val flagged: Boolean get() = coverage >= 0.3 && (if (nutrient.kind == "min") weekPct < 0.7 else weekPct > 1.0)
    }

    fun rows(entries: List<Entry>, today: String, age: Int?, sex: String?, calorieTarget: Int, fibreGoal: Int?): List<Row> {
        val week = (0..6).map { Dates.addDays(today, -it.toLong()) }.toSet()
        val weekEntries = entries.filter { it.date in week }
        val loggedDays = weekEntries.map { it.date }.distinct().size.coerceAtLeast(1)
        val weekKcal = weekEntries.sumOf { it.kcal }
        return NUTRIENTS.mapNotNull { n ->
            val t = target(n.key, age, sex, calorieTarget, fibreGoal) ?: return@mapNotNull null
            val todaySum = entries.filter { it.date == today }.sumOf { it.micros[n.key] ?: 0.0 }
            val weekSum = weekEntries.sumOf { it.micros[n.key] ?: 0.0 }
            val covered = weekEntries.filter { it.micros.containsKey(n.key) }.sumOf { it.kcal }
            Row(n, t, todaySum, weekSum / loggedDays, if (weekKcal > 0) covered / weekKcal else 0.0)
        }
    }

    /** "Low this week" / "High this week" hints with Indian foods that help. */
    fun hint(key: String): String = when (key) {
        "fiber_g" -> "Add whole dal, rajma or chana, a bajra or jowar roti, guava, or a bowl of oats."
        "iron_mg" -> "Try rajma, chana, palak, bajra, poha or a little jaggery, with lemon or amla to help absorb it."
        "calcium_mg" -> "Curd, paneer, ragi, til (sesame) and a glass of milk all help."
        "vitamin_c_mg" -> "Amla, guava, oranges, mosambi, or lemon squeezed over dal and sabzi."
        "potassium_mg" -> "Banana, coconut water, rajma, dal, curd and leafy sabzi are good sources."
        "sugar_g" -> "Swap one sweet drink or mithai for fruit, lassi without sugar, or chaas."
        "sodium_mg" -> "Go easy on pickles, papad, namkeen and instant noodles; add salt at the table last."
        else -> ""
    }

    fun fmt(v: Double, unit: String): String = when {
        unit == "mg" && v >= 1000 -> String.format(java.util.Locale.US, "%,d mg", v.roundToInt())
        v >= 100 -> "${v.roundToInt()} $unit"
        v >= 10 -> "${v.roundToInt()} $unit"
        else -> "${(v * 10).roundToInt() / 10.0} $unit".replace(".0 ", " ")
    }
}
