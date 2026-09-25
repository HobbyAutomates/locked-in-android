package com.sohum.bandlog.util

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v2.10 body metrics. Pure functions (the LMS table is loaded once from res/raw) — a line-for-line
 * port of the web app's src/lib/bmi.ts, which scripts/check-goals.ts tests against the science
 * spec's vectors. Keep the two in step.
 *
 * Sources: adult BMI Indian cut-offs 23 / 25 (Misra et al. 2009; WHO Expert Consultation, Lancet
 * 2004), WHO global 25 / 30 as secondary; healthy range 18.5–22.9 × height²; teen BMI-for-age from
 * the WHO Growth Reference 2007 (5–19 y) LMS tables bundled in res/raw/who2007_bmi_lms.json (source
 * cited in the file); waist-to-height < 0.5 (Ashwell & Gibson).
 */
object Bmi {

    /** kg / m². Null when either number is missing or silly. */
    fun bmi(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || heightCm == null || weightKg <= 0 || heightCm <= 0) return null
        val m = heightCm / 100
        return weightKg / (m * m)
    }

    /** Indian / Asian consensus cut-offs (adults ≥ 18): 18.5 / 23 / 25. The primary label in the app. */
    fun categoryIndia(b: Double): String = when {
        b < 18.5 -> "Underweight"
        b < 23.0 -> "Normal"
        b < 25.0 -> "Overweight"
        else -> "Obese"
    }

    /** WHO global cut-offs (adults): 18.5 / 25 / 30. Shown second. */
    fun categoryWho(b: Double): String = when {
        b < 18.5 -> "Underweight"
        b < 25.0 -> "Normal"
        b < 30.0 -> "Overweight"
        else -> "Obese"
    }

    data class Range(val min: Double, val max: Double)

    private fun one(v: Double) = (v * 10).roundToInt() / 10.0

    /** Healthy weight range for a height: BMI 18.5–22.9 × height². One decimal, as shown on screen. */
    fun healthyRange(heightCm: Double): Range {
        val m2 = (heightCm / 100).pow(2)
        return Range(one(18.5 * m2), one(22.9 * m2))
    }

    // ---------------------------------------------------------------- teens: WHO 2007 BMI-for-age

    /** One LMS row: L, M, S. */
    data class Lms(val l: Double, val m: Double, val s: Double)

    private class Table(val firstMonth: Int, val lastMonth: Int, val boys: List<Lms>, val girls: List<Lms>)

    @Volatile private var table: Table? = null

    /** Reads res/raw/who2007_bmi_lms.json once (MainActivity calls this at start; idempotent). */
    fun load(context: Context) {
        if (table != null) return
        runCatching {
            val text = context.resources.openRawResource(com.sohum.bandlog.R.raw.who2007_bmi_lms).bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            fun rows(k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { r -> Lms(r.getDouble(0), r.getDouble(1), r.getDouble(2)) } } }
            table = Table(o.getInt("firstMonth"), o.getInt("lastMonth"), rows("boys"), rows("girls"))
        }.onFailure { android.util.Log.w("LockedIn", "WHO LMS table failed to load", it) }
    }

    /** L, M, S for a sex and age in completed months; null outside the bundled 61–216 months (or before [load]). */
    fun lmsRow(sex: String, months: Int): Lms? {
        val t = table ?: return null
        if (months < t.firstMonth || months > t.lastMonth) return null
        return (if (sex == "male") t.boys else t.girls).getOrNull(months - t.firstMonth)
    }

    /** The BMI at a given z for one LMS row (inverse Box-Cox): M·(1 + L·S·z)^(1/L). */
    fun bmiAtZ(row: Lms, z: Double): Double =
        if (row.l == 0.0) row.m * exp(row.s * z) else row.m * (1 + row.l * row.s * z).pow(1 / row.l)

    /** LMS z-score with WHO's restricted tails beyond ±3 SD (as AnthroPlus does). */
    fun zFromLms(b: Double, row: Lms): Double {
        val z = if (row.l == 0.0) ln(b / row.m) / row.s else ((b / row.m).pow(row.l) - 1) / (row.l * row.s)
        if (z > 3) {
            val sd3 = bmiAtZ(row, 3.0)
            val sd23 = sd3 - bmiAtZ(row, 2.0)
            return 3 + (b - sd3) / sd23
        }
        if (z < -3) {
            val sd3neg = bmiAtZ(row, -3.0)
            val sd23neg = bmiAtZ(row, -2.0) - sd3neg
            return -3 + (b - sd3neg) / sd23neg
        }
        return z
    }

    /** BMI-for-age z; "other" / unset averages the boys' and girls' z. Null outside 61–216 months. */
    fun bmiForAgeZ(b: Double, sex: String?, months: Int): Double? {
        if (sex == "male" || sex == "female") {
            val row = lmsRow(sex, months) ?: return null
            return zFromLms(b, row)
        }
        val boy = lmsRow("male", months) ?: return null
        val girl = lmsRow("female", months) ?: return null
        return (zFromLms(b, boy) + zFromLms(b, girl)) / 2
    }

    /** WHO 5–19 y cut-offs: < −3 severe thinness, < −2 thinness, ≤ +1 normal, ≤ +2 overweight, else obese. */
    fun bmiForAgeCategory(z: Double): String = when {
        z < -3 -> "Severe thinness"
        z < -2 -> "Thinness"
        z <= 1 -> "Normal"
        z <= 2 -> "Overweight"
        else -> "Obese"
    }

    data class Words(val title: String, val detail: String)

    /** Plain, kind words for a teen's z — no "obese" / "thin" labels on screen. */
    fun teenWords(z: Double): Words = when {
        z < -2 -> Words("Lighter than most people your age", "Worth a chat with a doctor or dietitian so you have plenty of fuel to grow.")
        z <= 1 -> Words("Right in the usual range for your age", "Your body is growing on track. Keep fuelling your training.")
        z <= 2 -> Words("A bit above the usual range for your age", "Totally common while growing. Moving often and regular meals help most.")
        else -> Words("Above the usual range for your age", "A doctor or dietitian can help you with a plan that fits a growing body.")
    }

    /** Usual weight range for a teen's height and age: z −2 to +1 × height². Null outside 61–216 months. */
    fun teenHealthyRange(heightCm: Double, sex: String?, months: Int): Range? {
        val rows = if (sex == "male" || sex == "female") listOf(lmsRow(sex, months)) else listOf(lmsRow("male", months), lmsRow("female", months))
        if (rows.any { it == null }) return null
        fun at(z: Double) = rows.sumOf { bmiAtZ(it!!, z) } / rows.size
        val m2 = (heightCm / 100).pow(2)
        return Range(one(at(-2.0) * m2), one(at(1.0) * m2))
    }

    /** Rough percentile for a z (normal CDF, Abramowitz–Stegun 7.1.26). */
    fun percentileFromZ(z: Double): Int {
        val x = abs(z) / sqrt(2.0)
        val t = 1 / (1 + 0.3275911 * x)
        val erf = 1 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
        val cdf = if (z >= 0) (1 + erf) / 2 else (1 - erf) / 2
        return (cdf * 100).roundToInt().coerceIn(1, 99)
    }

    /** Age in completed months on [today] (yyyy-MM-dd); null when the birthday is missing or later. */
    fun ageMonths(dob: String?, today: String): Int? {
        if (dob.isNullOrBlank()) return null
        val d = dob.take(10).split("-").mapNotNull { it.toIntOrNull() }
        val t = today.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (d.size != 3 || t.size != 3) return null
        var months = (t[0] - d[0]) * 12 + (t[1] - d[1])
        if (t[2] < d[2]) months--
        return if (months >= 0) months else null
    }

    // ---------------------------------------------------------------- waist-to-height

    fun waistToHeight(waistCm: Double?, heightCm: Double?): Double? {
        if (waistCm == null || heightCm == null || waistCm <= 0 || heightCm <= 0) return null
        return waistCm / heightCm
    }

    /** < 0.5 reads as fine; at or above it is "worth a check-in", never "unhealthy". */
    fun whtrWords(r: Double): Pair<Boolean, String> =
        if (r < 0.5) true to "Under 0.5, the range linked with lower health risk."
        else false to "0.5 or more. One signal, not a verdict: worth a check-in with a doctor sometime."
}
