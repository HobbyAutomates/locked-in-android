package com.sohum.bandlog.util

import com.sohum.bandlog.data.Profile
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Auto Generate Goals", v2.10 science pass (see LockedIn-backups/science-spec.md).
 *
 * A line-for-line port of the web app's src/lib/goals.ts — same maths, same numbers, same copy.
 * The web side has the tests (scripts/check-goals.ts); change both together.
 *
 * Adults (18+): Mifflin-St Jeor BMR × a standard PAL band from workouts per week, then the
 * lose / maintain / gain intent at a pace capped to a safe share of body weight, never below
 * max(85 % of BMR, 1200 female / 1500 male). Protein by the ISSN / ICMR-NIN rule, fat 25 % of
 * calories, carbs take the remainder.
 *
 * Under 18: no deficit, ever. Energy comes from the teen EER (IOM 2005 interim coefficients) with
 * a small surplus for "gain"; protein from the ICMR-NIN 2020 adolescent table. A saved "lose" goal
 * is treated as maintain (see [migrateTeenGoal]).
 */
object Goals {

    data class Targets(val calories: Int, val protein: Int, val carbs: Int, val fat: Int)

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))
    /** One decimal, dropping ".0" (the same as ui.today.fmt and the web's fmt). */
    private fun one(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
    private fun grouped(v: Int): String = String.format(Locale.US, "%,d", v)

    /** Today as yyyy-MM-dd in the app's zone. */
    fun todayIso(): String = Dates.today()

    /** Whole years on [today], or null. */
    fun ageYears(dob: String?, today: String = todayIso()): Int? {
        val m = Bmi.ageMonths(dob, today) ?: return null
        val y = floor(m / 12.0).toInt()
        return if (y in 1..120) y else null
    }

    const val ADULT_AGE = 18
    /** Under 18: the growing-body rules apply (no deficit, teen EER, ICMR-NIN adolescent protein). */
    fun isTeen(age: Int?): Boolean = age != null && age < ADULT_AGE

    /** Human-readable list of what Personal details is still missing; empty means [generate] works. */
    fun missing(p: Profile): List<String> = buildList {
        if (p.weightKg == null || p.weightKg <= 0) add("current weight")
        if (p.heightCm == null || p.heightCm <= 0) add("height")
        if (ageYears(p.dob) == null) add("date of birth")
        if (p.gender.isNullOrBlank()) add("gender")
    }

    // ---------------------------------------------------------------- goal choices by age

    data class GoalOption(val key: String, val label: String, val sub: String)

    const val TEEN_GOAL_NOTE = "At your age your body is still growing. Talk to a doctor or dietitian before trying to lose weight."

    /** What the goal picker offers. Under 18 there is no "lose". */
    fun goalOptions(age: Int?): List<GoalOption> =
        if (isTeen(age)) listOf(
            GoalOption("maintain", "Maintain / grow stronger", "Fuel for growing, training and school"),
            GoalOption("gain", "Gain / build muscle", "A small extra on top of what you need to grow"),
        ) else listOf(
            GoalOption("lose", "Lose weight", "At a pace that keeps your muscle"),
            GoalOption("maintain", "Maintain", "Stay where you are, feel stronger"),
            GoalOption("gain", "Gain weight", "Lean gains, slow and steady"),
        )

    /** The goal the maths actually uses: a teen's saved "lose" counts as maintain. */
    fun effectiveGoal(goal: String, age: Int?): String = if (isTeen(age) && goal == "lose") "maintain" else goal

    /** True when this profile is an under-18 account still holding a "lose" goal. */
    fun needsTeenMigration(p: Profile, today: String = todayIso()): Boolean = isTeen(ageYears(p.dob, today)) && p.goalType == "lose"

    /** v2.10 one-time migration: an under-18 "lose" becomes maintain with fresh targets. Null when nothing to do. */
    fun migrateTeenGoal(p: Profile, today: String = todayIso()): Profile? {
        if (!needsTeenMigration(p, today)) return null
        return applyTo(p.copy(goalType = "maintain"), today)
    }

    const val TEEN_MIGRATION_TITLE = "We switched your goal to maintain"
    const val TEEN_MIGRATION_BODY =
        "Under 18, your body is still growing, so Locked In doesn't set weight-loss targets anymore. Your calories now fuel growth and training. Your logs, weights and history are all still here. Talk to a doctor or dietitian if weight is on your mind."

    // ---------------------------------------------------------------- energy

    data class PalBand(val key: String, val factor: Double, val label: String)

    /** Standard PAL bands (FAO/WHO/UNU 1985): 0 → 1.2, 1–2 → 1.375, 3–5 → 1.55, 6+ → 1.725. */
    fun adultPal(workoutsPerWeek: Int): PalBand {
        val w = max(0, workoutsPerWeek)
        return when {
            w == 0 -> PalBand("sedentary", 1.2, "Sedentary")
            w <= 2 -> PalBand("light", 1.375, "Lightly active")
            w <= 5 -> PalBand("moderate", 1.55, "Moderately active")
            else -> PalBand("very", 1.725, "Very active")
        }
    }

    /** Mifflin-St Jeor (1990). "other" uses the midpoint of the two sex constants — an approximation. */
    fun mifflinBmr(kg: Double, cm: Double, age: Int, sex: String?): Double {
        val s = when (sex) { "male" -> 5.0; "female" -> -161.0; else -> -78.0 }
        return 10 * kg + 6.25 * cm - 5 * age + s
    }

    /** IOM 2005 physical-activity coefficients for 9–18 y, same workout buckets as [adultPal]. */
    private fun teenPaCoefficient(workoutsPerWeek: Int, boy: Boolean): Double {
        val w = max(0, workoutsPerWeek)
        val i = if (w == 0) 0 else if (w <= 2) 1 else if (w <= 5) 2 else 3
        return (if (boy) listOf(1.0, 1.13, 1.26, 1.42) else listOf(1.0, 1.16, 1.31, 1.56))[i]
    }

    /**
     * Teen Estimated Energy Requirement, kcal/day, including the 25 kcal/day growth allowance.
     * TODO(NASEM 2023): these are the interim IOM 2005 coefficients; swap in NASEM 2023 DRI for Energy
     * Table 5-15 once transcribed (nationalacademies.org/read/26818/chapter/7). Keep goals.ts in step.
     */
    fun teenEer(kg: Double, cm: Double, age: Int, sex: String?, workoutsPerWeek: Int): Double {
        val m = cm / 100
        val boy = 88.5 - 61.9 * age + teenPaCoefficient(workoutsPerWeek, true) * (26.7 * kg + 903 * m) + 25
        val girl = 135.3 - 30.8 * age + teenPaCoefficient(workoutsPerWeek, false) * (10.0 * kg + 934 * m) + 25
        return when (sex) { "male" -> boy; "female" -> girl; else -> (boy + girl) / 2 }
    }

    /** Teen "gain": a small surplus on top of EER — 10 % of EER, never more than 300 kcal. */
    fun teenGainSurplus(eer: Double): Double = min(eer * 0.1, 300.0)

    // ---------------------------------------------------------------- safe pace and floors

    /** 1 kg of body mass ≈ 7700 kcal. */
    const val KCAL_PER_KG = 7700.0

    /** Loss: 1 % of body weight a week, at least 0.25, never more than 1.0 kg/week. */
    fun maxSafeWeeklyLossKg(kg: Double): Double = clamp(kg * 0.01, 0.25, 1.0)

    /** Lean gain: 0.5 % of body weight a week, 0.1–0.5 kg/week. */
    fun maxSafeWeeklyGainKg(kg: Double): Double = clamp(kg * 0.005, 0.1, 0.5)

    fun safeDeficitKcalPerDay(kg: Double, requestedKgPerWeek: Double): Double = min(requestedKgPerWeek, maxSafeWeeklyLossKg(kg)) * KCAL_PER_KG / 7

    fun safeSurplusKcalPerDay(kg: Double, requestedKgPerWeek: Double): Double = min(requestedKgPerWeek, maxSafeWeeklyGainKg(kg)) * KCAL_PER_KG / 7

    /** Sex backstop under the floor: 1200 female, 1500 male, the midpoint 1350 for other / unset. */
    fun sexFloor(sex: String?): Double = when (sex) { "female" -> 1200.0; "male" -> 1500.0; else -> 1350.0 }

    /** Lowest calorie target the app will generate or save: max(85 % of BMR, the sex backstop). */
    fun calorieFloor(bmr: Double, sex: String?): Double = max(bmr * 0.85, sexFloor(sex))

    /** The floor for a whole profile, rounded; just the sex backstop when details are missing. */
    fun floorFor(p: Profile, today: String = todayIso()): Int {
        val age = ageYears(p.dob, today)
        if (p.weightKg == null || p.weightKg <= 0 || p.heightCm == null || p.heightCm <= 0 || age == null) return sexFloor(p.gender).roundToInt()
        return calorieFloor(mifflinBmr(p.weightKg, p.heightCm, age, p.gender), p.gender).roundToInt()
    }

    /** The slider's top notch for a goal and body weight (0.1 kg grid, never below 0.1). */
    fun speedMax(goal: String, kg: Double?): Double {
        val cap = if (goal == "gain") (if (kg != null && kg > 0) maxSafeWeeklyGainKg(kg) else 0.5) else if (kg != null && kg > 0) maxSafeWeeklyLossKg(kg) else 1.0
        return max(0.1, floor(cap * 10 + 1e-9) / 10)
    }

    /** Round to the slider's 0.1 kg notches inside 0.1…max so the label and the saved value agree. */
    fun roundSpeed(v: Double, max: Double = 1.0): Double = (clamp(v, 0.1, max(0.1, max)) * 10).roundToInt() / 10.0

    /** Band for the slider chip, relative to this person's safe max. */
    fun speedLabel(kgPerWeek: Double, max: Double = 1.0): String {
        val f = kgPerWeek / max(0.1, max)
        return when {
            f < 0.5 -> "Slow and steady"
            f < 0.95 -> "Recommended"
            else -> "Max safe pace"
        }
    }

    /** The one-line "why" under the speed slider. */
    fun speedWhy(goal: String, kg: Double?): String {
        val max = speedMax(goal, kg)
        if (goal == "gain") return "Capped at ${one(max)} kg a week, about 0.5% of your body weight, so most of what you add is muscle."
        return "Capped at ${one(max)} kg a week, about 1% of your body weight. Faster mostly costs muscle, not fat."
    }

    // ---------------------------------------------------------------- protein and macros

    /** ICMR-NIN 2020 adolescent protein RDA, grams/day (10–12, 13–15, 16–17 bands; under 10 uses 10–12). */
    fun teenProteinG(age: Int, sex: String?): Int {
        val band = if (age <= 12) 0 else if (age <= 15) 1 else 2
        val boy = listOf(31, 44, 55)[band]
        val girl = listOf(32, 43, 46)[band]
        return when (sex) { "male" -> boy; "female" -> girl; else -> ((boy + girl) / 2.0).roundToInt() }
    }

    /** Protein, g/day: ICMR-NIN table under 18; 1.6 g/kg training 3+ a week; else max(0.83, 1.0) g/kg. */
    fun proteinTargetG(age: Int, kg: Double, sex: String?, workoutsPerWeek: Int): Int {
        if (isTeen(age)) return teenProteinG(age, sex)
        if (workoutsPerWeek >= 3) return clamp(1.6 * kg, 1.4 * kg, 2.0 * kg).roundToInt()
        return max(0.83 * kg, 1.0 * kg).roundToInt()
    }

    /** Fat 25 % of calories, carbs the remainder after protein and fat. */
    fun macrosFor(calories: Double, protein: Int): Targets {
        val fat = (calories * 0.25 / 9).roundToInt()
        val carbs = ((calories - protein * 4 - fat * 9) / 4).roundToInt().coerceAtLeast(0)
        return Targets(calories.roundToInt(), protein, carbs, fat)
    }

    data class MacroNote(val macro: String, val level: String, val text: String)

    /** AMDR checks for the macro editor — soft notes, never blocks ("warn" / "info"). */
    fun amdrNotes(t: Targets): List<MacroNote> {
        val kcal = max(1, t.calories).toDouble()
        val p = t.protein * 4 / kcal * 100
        val c = t.carbs * 4 / kcal * 100
        val f = t.fat * 9 / kcal * 100
        val out = mutableListOf<MacroNote>()
        if (p < 10) out += MacroNote("protein", "warn", "Protein is under 10% of calories. That's low for recovery and muscle.")
        else if (p > 35) out += MacroNote("protein", "info", "Protein is over 35%, above the usual range. Carbs are what fuel hard sessions.")
        if (f < 15) out += MacroNote("fat", "warn", "Fat is under 15%. Your body needs some for hormones and vitamins.")
        else if (f < 20) out += MacroNote("fat", "info", "Fat is a little under the usual 20–35% range.")
        else if (f > 35) out += MacroNote("fat", "info", "Fat is over 35%, above the usual range.")
        if (c < 20 || t.carbs < 130) out += MacroNote("carbs", "warn", "Carbs are under 130 g a day. Your brain alone runs on about that much.")
        else if (c < 45) out += MacroNote("carbs", "info", "Carbs are under 45%, below the usual 45–65% range. Fine if that's on purpose.")
        else if (c > 65) out += MacroNote("carbs", "info", "Carbs are over 65%, above the usual range.")
        return out
    }

    // ---------------------------------------------------------------- the generator

    data class Plan(
        val targets: Targets,
        val teen: Boolean,
        val age: Int,
        /** The goal the maths used (a teen's "lose" is maintain). */
        val goal: String,
        val bmr: Double,
        /** Maintenance energy: BMR × PAL for adults, EER for teens. */
        val maintenance: Double,
        val pal: PalBand,
        /** kg/week after the safe cap (0 for maintain and for teens). */
        val speed: Double,
        val speedCapped: Boolean,
        val floor: Double,
        val floorApplied: Boolean,
    )

    /** The full working behind a set of targets; null when details are missing. */
    fun plan(p: Profile, today: String = todayIso()): Plan? {
        if (missing(p).isNotEmpty()) return null
        val kg = p.weightKg!!
        val cm = p.heightCm!!
        val age = ageYears(p.dob, today)!!
        val teen = isTeen(age)
        val goal = effectiveGoal(p.goalType, age)
        val bmr = mifflinBmr(kg, cm, age, p.gender)
        val pal = adultPal(p.weeklyWorkoutTarget)
        val floor = calorieFloor(bmr, p.gender)

        val maintenance: Double
        val raw: Double
        var speed = 0.0
        var speedCapped = false
        if (teen) {
            maintenance = teenEer(kg, cm, age, p.gender, p.weeklyWorkoutTarget)
            raw = if (goal == "gain") maintenance + teenGainSurplus(maintenance) else maintenance
        } else {
            maintenance = bmr * pal.factor
            val requested = max(0.1, if (p.goalSpeedKgWk > 0) p.goalSpeedKgWk else 0.5)
            raw = when (goal) {
                "lose" -> { speed = min(requested, maxSafeWeeklyLossKg(kg)); maintenance - safeDeficitKcalPerDay(kg, requested) }
                "gain" -> { speed = min(requested, maxSafeWeeklyGainKg(kg)); maintenance + safeSurplusKcalPerDay(kg, requested) }
                else -> maintenance
            }
            speedCapped = goal != "maintain" && speed < requested - 1e-9
        }
        val calories = max(floor, raw)
        val protein = proteinTargetG(age, kg, p.gender, p.weeklyWorkoutTarget)
        return Plan(macrosFor(calories, protein), teen, age, goal, bmr, maintenance, pal, speed, speedCapped, floor, raw < floor)
    }

    fun generate(p: Profile, today: String = todayIso()): Targets? = plan(p, today)?.targets

    /** Copy of [p] with freshly generated targets applied (unchanged when details are missing). */
    fun applyTo(p: Profile, today: String = todayIso()): Profile {
        val t = generate(p, today) ?: return p
        return p.copy(calorieTarget = t.calories, proteinTargetG = t.protein, carbTargetGSet = t.carbs, fatTargetGSet = t.fat)
    }

    /** A calorie target the user typed, lifted to the floor if under it. Never blocks the save. */
    fun capToFloor(requested: Int, floor: Int): Pair<Int, Boolean> = if (requested < floor) floor to true else requested to false

    // ---------------------------------------------------------------- eating-disorder safety flags

    data class WeighIn(val date: String, val kg: Double)
    /** One edit of the calorie target ("calories") or goal weight ("goal_weight"), kept on the device. */
    data class TargetEdit(val date: String, val field: String, val from: Double, val to: Double)

    private fun dayNum(iso: String): Long = java.time.LocalDate.parse(iso.take(10)).toEpochDay()

    /** Loss in kg/week across the last 28 days' weigh-ins (first vs last); null unless they span 14+ days. */
    fun recentWeeklyLoss(weights: List<WeighIn>, today: String): Double? {
        val t = dayNum(today)
        val recent = weights.filter { it.kg > 0 && t - dayNum(it.date) <= 28 && dayNum(it.date) <= t }.sortedBy { dayNum(it.date) }
        if (recent.size < 2) return null
        val span = dayNum(recent.last().date) - dayNum(recent.first().date)
        if (span < 14) return null
        return (recent.first().kg - recent.last().kg) / (span / 7.0)
    }

    /** How many edits in the last [days] days moved a target down. */
    fun downwardEdits(edits: List<TargetEdit>, today: String, days: Int = 14): Int {
        val t = dayNum(today)
        return edits.count { it.to < it.from && t - dayNum(it.date) <= days && dayNum(it.date) <= t }
    }

    data class ScreenInput(
        val age: Int?,
        val heightCm: Double?,
        val weightKg: Double?,
        val bmiZ: Double?,
        val weights: List<WeighIn>,
        val requestedCalories: Int?,
        val floor: Int,
        val goalType: String,
        val goalWeightKg: Double?,
        val edits: List<TargetEdit>,
        val today: String,
    )

    /** Pattern signals only — never a diagnosis, never a block. */
    fun edFlags(i: ScreenInput): List<String> {
        val flags = mutableListOf<String>()
        val b = Bmi.bmi(i.weightKg, i.heightCm)
        if (i.age != null && !isTeen(i.age) && b != null && b < 17.5) flags += "very_low_bmi"
        if (isTeen(i.age) && i.bmiZ != null && i.bmiZ < -2) flags += "very_low_bmi_for_age"
        val loss = recentWeeklyLoss(i.weights, i.today)
        val kg = i.weightKg ?: i.weights.firstOrNull()?.kg
        if (loss != null && kg != null && kg > 0 && loss > maxSafeWeeklyLossKg(kg)) flags += "rapid_loss"
        if (i.requestedCalories != null && i.requestedCalories < i.floor) flags += "extreme_target"
        if (i.age != null && !isTeen(i.age) && i.goalType == "lose" && i.goalWeightKg != null && i.heightCm != null && i.heightCm > 0 && i.goalWeightKg < Bmi.healthyRange(i.heightCm).min) flags += "goal_below_range"
        if (downwardEdits(i.edits, i.today) >= 3) flags += "repeated_lowering"
        return flags
    }

    /** Fill a [ScreenInput] from a profile plus what the screen knows. */
    fun screenInput(p: Profile, weights: List<WeighIn> = emptyList(), requestedCalories: Int? = null, edits: List<TargetEdit> = emptyList(), today: String = todayIso()): ScreenInput {
        val age = ageYears(p.dob, today)
        val months = Bmi.ageMonths(p.dob, today)
        val b = Bmi.bmi(p.weightKg, p.heightCm)
        return ScreenInput(
            age = age,
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            bmiZ = if (isTeen(age) && b != null && months != null) Bmi.bmiForAgeZ(b, p.gender, months) else null,
            weights = weights,
            requestedCalories = requestedCalories,
            floor = floorFor(p, today),
            goalType = p.goalType,
            goalWeightKg = p.goalWeightKg,
            edits = edits,
            today = today,
        )
    }

    // ---------------------------------------------------------------- copy shared by both apps

    const val SAFETY_TITLE = "Let's keep this kind to your body"

    /** The safety note's body. [floor] is set when a calorie number was lifted to the floor. */
    fun safetyBody(flags: List<String>, floor: Int?): String {
        val parts = mutableListOf<String>()
        if (floor != null) parts += "We've set your target to ${grouped(floor)} kcal, a safer floor based on general nutrition guidance for your body."
        else if ("goal_below_range" in flags) parts += "That goal sits under the healthy range for your height, so we'll keep your daily target at a safer level."
        else parts += "We noticed a pattern that can sometimes mean food or weight feels heavy right now. No judgement, just checking in."
        parts += "If food or your body has been on your mind a lot, talking to someone can really help."
        return parts.joinToString(" ")
    }

    data class Helpline(val name: String, val detail: String, val phones: List<String>, val whatsapp: String? = null)

    /** Indian helplines from the science spec (Sept 2026). TODO(before launch): re-verify every number. */
    val HELPLINES = listOf(
        Helpline("iCall (TISS)", "Free counselling with trained professionals, Mon–Sat, 8 am–10 pm", listOf("9152987821", "022-25521111")),
        Helpline("Vandrevala Foundation", "Free, 24×7, calls or WhatsApp", listOf("1860-2662-345", "1800-233-3330"), "+91 9999 666 555"),
        Helpline("KIRAN", "Govt. of India mental health helpline, 24×7, toll-free", listOf("1800-599-0019")),
    )

    const val HELPLINE_NOTE = "Numbers checked September 2026. Helplines change sometimes, so if one doesn't connect, please try another."

    /** "The science" sheet: what each number is based on. */
    val SCIENCE_SOURCES = listOf(
        "Safe pace" to "Loss is capped at 1% of your body weight a week (never over 1 kg), in line with NIH and AHA/ACC/TOS obesity guidelines. Gain is capped at 0.5% a week, from sports-nutrition reviews on lean gaining (Helms, Aragon and colleagues).",
        "Your energy" to "Adults: the Mifflin-St Jeor equation (1990), the best-validated everyday formula, times a standard activity factor (FAO/WHO/UNU). Under 18: the IOM estimated energy requirement for teens, which includes energy for growing.",
        "The floor" to "Your target never goes under 85% of your resting burn, or 1200 kcal (women) / 1500 kcal (men), whichever is higher.",
        "Protein" to "1.6 g per kg if you train 3+ times a week (ISSN position stand: 1.4–2.0 g/kg). Otherwise about 1 g/kg. Under 18: ICMR-NIN 2020 recommended amounts for your age.",
        "BMI" to "Adults: Indian cut-offs (normal under 23, from the 2009 Indian consensus statement and WHO's Asia-Pacific advice), with the WHO global category shown too. Under 18: WHO 2007 growth reference for your exact age. BMI can't tell muscle from fat, so it's one signal, not a verdict.",
        "Waist-to-height" to "Keeping your waist under half your height is linked with lower health risk at most ages (Ashwell & Gibson).",
    )

    // ---------------------------------------------------------------- hide-numbers mode

    /** Words instead of kcal for the opt-in "Hide calorie numbers" mode. Never moralizing. */
    fun calorieWords(eaten: Double, target: Double): String {
        if (target <= 0) return "No target set"
        val r = eaten / target
        return when {
            r <= 0 -> "Nothing logged yet"
            r < 0.35 -> "Just getting started"
            r < 0.75 -> "Building up"
            r < 0.92 -> "Nearly there"
            r <= 1.1 -> "Right around your target"
            else -> "Past your target, and that's okay"
        }
    }
}
