package com.sohum.bandlog.util

import kotlin.math.floor
import kotlin.math.max

/**
 * v2.13 §9 micronutrient dashboard — a port of the web's src/lib/micros.ts. Uses the micros every
 * meal item already carries (scaled to its grams): fibre, sugar, sodium, iron, calcium, vitamin C,
 * potassium.
 *
 * Targets: ICMR-NIN 2020 RDA by age and sex for iron, calcium, vitamin C and potassium; fibre at
 * 30 g per 2,000 kcal (ICMR-NIN) unless the user set their own; sugar under 10 % of calories (WHO)
 * unless set in Nutrition goals; sodium under 2,000 mg (WHO).
 * TODO(before launch): re-check the adolescent vitamin C and potassium bands against the ICMR-NIN
 * 2020 tables. Keep src/lib/micros.ts in step.
 */
object Micros {

    data class Target(val key: String, val label: String, val unit: String, /** "min" goal or "max" limit. */ val kind: String, val target: Double, val source: String)

    val KEYS = listOf("fiber_g", "iron_mg", "calcium_mg", "vitamin_c_mg", "potassium_mg", "sugar_g", "sodium_mg")

    private fun jsRound(v: Double) = floor(v + 0.5)

    /** ICMR-NIN 2020 iron RDA (mg/day). */
    fun ironRda(age: Int?, sex: String?): Double {
        fun pick(boy: Double, girl: Double) = when (sex) { "male" -> boy; "female" -> girl; else -> jsRound((boy + girl) / 2) }
        return when {
            age == null || age >= 18 -> pick(19.0, 29.0)
            age <= 12 -> pick(16.0, 28.0)
            age <= 15 -> pick(22.0, 30.0)
            else -> pick(26.0, 32.0)
        }
    }

    /** ICMR-NIN 2020 calcium RDA (mg/day). */
    fun calciumRda(age: Int?): Double = when {
        age == null || age >= 18 -> 1000.0
        age <= 12 -> 850.0
        age <= 15 -> 1000.0
        else -> 1050.0
    }

    /** ICMR-NIN 2020 vitamin C RDA (mg/day). */
    fun vitaminCRda(sex: String?): Double = when (sex) { "male" -> 80.0; "female" -> 65.0; else -> 72.0 }

    /** ICMR-NIN 2020 potassium (mg/day). */
    fun potassiumRda(age: Int?): Double = if (age != null && age < 16) 3000.0 else 3510.0

    const val SODIUM_LIMIT_MG = 2000.0

    /** [fiberTarget] / [sugarTarget]: the user's own goals (null = the formula). */
    fun targets(age: Int?, sex: String?, calorieTarget: Int, fiberTarget: Int?, sugarTarget: Int?): List<Target> {
        val kcal = max(1000, if (calorieTarget > 0) calorieTarget else 2000)
        return listOf(
            Target("fiber_g", "Fibre", "g", "min", fiberTarget?.toDouble() ?: jsRound(30.0 * kcal / 2000), "ICMR-NIN 2020: 30 g per 2,000 kcal"),
            Target("iron_mg", "Iron", "mg", "min", ironRda(age, sex), "ICMR-NIN 2020 RDA for your age and sex"),
            Target("calcium_mg", "Calcium", "mg", "min", calciumRda(age), "ICMR-NIN 2020 RDA for your age"),
            Target("vitamin_c_mg", "Vitamin C", "mg", "min", vitaminCRda(sex), "ICMR-NIN 2020 RDA"),
            Target("potassium_mg", "Potassium", "mg", "min", potassiumRda(age), "ICMR-NIN 2020"),
            Target("sugar_g", "Sugar", "g", "max", sugarTarget?.toDouble() ?: jsRound(kcal * 0.1 / 4), "WHO: under 10 % of your calories"),
            Target("sodium_mg", "Sodium", "mg", "max", SODIUM_LIMIT_MG, "WHO: under 2,000 mg (about 5 g salt)"),
        )
    }

    /** One logged item: the day, its micros (already scaled to its grams). */
    data class Item(val date: String, val micros: Map<String, Double>)

    data class Day(val values: Map<String, Double>, val items: Int, val itemsWithData: Int)

    /** One day's totals. [Day.itemsWithData] counts items that carried any micro at all. */
    fun day(items: List<Item>, date: String): Day {
        val values = KEYS.associateWith { 0.0 }.toMutableMap()
        var n = 0
        var withData = 0
        for (it in items) {
            if (it.date != date) continue
            n++
            var any = false
            for (k in KEYS) {
                val v = it.micros[k] ?: continue
                if (v.isFinite() && v > 0) { values[k] = values.getValue(k) + v; any = true }
            }
            if (any) withData++
        }
        for (k in KEYS) values[k] = jsRound(values.getValue(k) * 10) / 10
        return Day(values, n, withData)
    }

    data class Week(val values: Map<String, Double>, val loggedDays: Int, val coverage: Double)

    /** The average over the days with anything logged among the [days] days ending [today]. */
    fun weekAverage(items: List<Item>, today: String, days: Int = 7): Week {
        val sum = KEYS.associateWith { 0.0 }.toMutableMap()
        var logged = 0
        var n = 0
        var withData = 0
        for (i in 0 until days) {
            val d = day(items, Dates.addDays(today, -i.toLong()))
            if (d.items == 0) continue
            logged++; n += d.items; withData += d.itemsWithData
            for (k in KEYS) sum[k] = sum.getValue(k) + d.values.getValue(k)
        }
        val values = KEYS.associateWith { k -> if (logged > 0) jsRound(sum.getValue(k) / logged * 10) / 10 else 0.0 }
        return Week(values, logged, if (n > 0) withData.toDouble() / n else 0.0)
    }

    /** Indian foods that are good sources, per nutrient (filtered by diet mode before showing). */
    val FOOD_HINTS: Map<String, List<String>> = mapOf(
        "fiber_g" to listOf("Rajma", "Whole moong or chana", "Oats", "Guava", "Bajra or jowar roti", "Apple with the skin", "Cucumber and carrot salad"),
        "iron_mg" to listOf("Rajma", "Chana", "Palak", "Bajra roti", "Ragi dosa", "Poha with lemon", "Eggs", "Mutton"),
        "calcium_mg" to listOf("Ragi", "Curd (dahi)", "Paneer", "Til (sesame) chikki", "Milk", "Tofu"),
        "vitamin_c_mg" to listOf("Amla", "Guava", "Orange or mosambi", "Lemon on your dal", "Capsicum", "Papaya"),
        "potassium_mg" to listOf("Banana", "Coconut water", "Rajma", "Curd", "Palak", "Sweet potato"),
    )

    data class Hint(val key: String, val label: String, val kind: String, val pct: Double, val text: String, val foods: List<String>)

    private fun grouped(v: Double): String = String.format(java.util.Locale.US, "%,d", jsRound(v).toLong())
    private fun num(v: Double): String = if (v == floor(v)) grouped(v) else v.toString()

    /** "Low this week": goals under 70 % of target on average, limits over 100 %. Needs 3+ logged days. */
    fun weekHints(targets: List<Target>, avg: Map<String, Double>, loggedDays: Int, mode: String): List<Hint> {
        if (loggedDays < 3) return emptyList()
        val out = mutableListOf<Hint>()
        for (t in targets) {
            val a = avg[t.key] ?: 0.0
            val pct = if (t.target > 0) a / t.target else 0.0
            if (t.kind == "min" && pct < 0.7) {
                val foods = (FOOD_HINTS[t.key] ?: emptyList()).filter { DietModes.allows(mode, it) }.take(4)
                out += Hint(t.key, t.label, "low", pct, "${t.label} was low this week (about ${jsRound(pct * 100).toLong()}% of your ${num(t.target)} ${t.unit}).", foods)
            } else if (t.kind == "max" && pct > 1) {
                out += Hint(t.key, t.label, "high", pct, "${t.label} averaged over your limit this week (${grouped(a)} of ${num(t.target)} ${t.unit}).", emptyList())
            }
        }
        return out
    }

    fun fmt(v: Double, unit: String): String = when {
        v >= 100 -> "${grouped(v)} $unit"
        v >= 10 -> "${jsRound(v).toLong()} $unit"
        else -> "${(jsRound(v * 10) / 10).let { if (it == floor(it)) it.toLong().toString() else it.toString() }} $unit"
    }
}
