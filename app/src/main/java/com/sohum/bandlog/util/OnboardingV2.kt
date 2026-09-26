package com.sohum.bandlog.util

import com.sohum.bandlog.data.Profile
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v2.14 Gen Z onboarding (web docs/v214-spec.md). A port of the web's src/lib/onboardingV2.ts —
 * same keys, same maths, same copy — so the reveal shows the same date when /api/onboarding/plan
 * can't be reached (the app then uses [plan] here). Pure Kotlin: no Android types.
 */
object OnboardingV2 {

    data class Answers(
        val heardFrom: String? = null,
        /** lose | gain | recomp | habits */
        val goal: String? = null,
        val name: String? = null,
        val heightCm: Double? = null,
        val weightKg: Double? = null,
        val dob: String? = null,
        val gender: String? = null,
        val goalWeightKg: Double? = null,
        /** chill | steady | aggressive */
        val pace: String? = null,
        val trainingDays: Int? = null,
        val sports: List<String>? = null,
        val dietMode: String? = null,
        val obstacles: List<String>? = null,
        val coachStyle: String? = null,
        val firstChallenge: String? = null,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            fun p(k: String, v: Any?) { if (v != null) put(k, v) }
            p("heard_from", heardFrom); p("goal", goal); p("name", name); p("height_cm", heightCm); p("weight_kg", weightKg)
            p("dob", dob); p("gender", gender); p("goal_weight_kg", goalWeightKg); p("pace", pace); p("training_days", trainingDays)
            sports?.let { put("sports", JSONArray(it)) }; p("diet_mode", dietMode)
            obstacles?.let { put("obstacles", JSONArray(it)) }; p("coach_style", coachStyle); p("first_challenge", firstChallenge)
        }

        companion object {
            private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
            private fun JSONObject.d(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
            private fun JSONObject.list(k: String): List<String>? = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }

            fun from(o: JSONObject) = Answers(
                heardFrom = o.s("heard_from"), goal = o.s("goal"), name = o.s("name"), heightCm = o.d("height_cm"), weightKg = o.d("weight_kg"),
                dob = o.s("dob"), gender = o.s("gender"), goalWeightKg = o.d("goal_weight_kg"), pace = o.s("pace"),
                trainingDays = if (!o.has("training_days") || o.isNull("training_days")) null else o.optInt("training_days"),
                sports = o.list("sports"), dietMode = o.s("diet_mode"), obstacles = o.list("obstacles"), coachStyle = o.s("coach_style"),
                firstChallenge = o.s("first_challenge"),
            )
        }
    }

    data class Option(val key: String, val label: String, val sub: String = "", val adultsOnly: Boolean = false)

    val SOURCES = listOf(
        Option("instagram", "Instagram"), Option("youtube", "YouTube"), Option("friend", "A friend sent it"),
        Option("college_gym", "College or gym"), Option("search", "Google / Play Store"), Option("other", "Somewhere else"),
    )

    val GOALS = listOf(
        Option("lose", "Lose fat", "Most people start here"),
        Option("gain", "Build muscle", "Get stronger, add size"),
        Option("recomp", "Both. Recomp.", "Lose fat, keep the gains"),
        Option("habits", "Just stay locked in", "Habits, energy, consistency"),
    )

    val OBSTACLES = listOf(
        Option("exam_stress", "Exam stress", "Food as a coping tool"),
        Option("mess_food", "Hostel or mess food", "No control over what's served"),
        Option("late_night", "Late-night Maggi", "The 1 am kitchen run"),
        Option("no_time", "No time to cook", "Convenience wins"),
        Option("eating_out", "Eating out with friends", "Hard to track, harder to say no"),
        Option("lost_motivation", "Lost motivation before", "Started strong, then drifted"),
    )

    /** Sports chips. Stored as the labels themselves, like the web (its SPORTS list), so both apps write the same values. */
    val SPORTS = listOf("Gym", "Home workout", "Running", "Cricket", "Football", "Yoga", "Badminton", "Walking").map { Option(it, it) }

    val EATER_TYPES = listOf(
        Option(DietModes.BALANCED, "Balanced", "Eat everything, in moderation"),
        Option(DietModes.HIGH_PROTEIN, "High protein", "Gym focus, stay full longer"),
        Option(DietModes.VEGETARIAN, "Vegetarian", "No meat, fish or egg"),
        Option(DietModes.EGGETARIAN, "Eggetarian", "Veg + eggs"),
        Option(DietModes.JAIN, "Jain", "No roots, no onion-garlic"),
        Option(DietModes.VEGAN, "Vegan", "Fully plant-based"),
        Option(DietModes.KETO, "Keto", "Very low carb", adultsOnly = true),
        Option(DietModes.LOW_CARB, "Low carb", "Fewer carbs, more fat", adultsOnly = true),
    )

    data class Challenge(val key: String, val title: String, val sub: String, val level: String, val days: Int)

    val CHALLENGES = listOf(
        Challenge("protein_7", "7-day protein streak", "Hit your protein 7 days in a row", "WINNABLE", 7),
        Challenge("perfect_week", "Perfect week", "Log 3 meals a day for 7 days", "MEDIUM", 7),
        Challenge("no_maggi_30", "No-Maggi month", "30 days, zero instant noodles", "HARD", 30),
    )

    data class Style(val key: String, val label: String, val short: String, val sample: String)

    val STYLES = listOf(
        Style("calm", "Calm", "Gentle, encouraging, zero pressure", "Rough day? One good meal tonight is enough."),
        Style("balanced", "Balanced", "Honest, supportive, a nudge when needed", "You're 20 g short on protein. Curd before bed?"),
        Style("no_excuses", "No excuses", "Direct. Calls out skipped days. Pushes you.", "3 skipped workouts. Gym at 6. No excuses."),
    )

    fun styleLabel(key: String?): String = STYLES.firstOrNull { it.key == key }?.label ?: "Balanced"

    /** kg/week per pace band; loss and gain have their own scales (the safe cap still applies). */
    val LOSS_PACES = mapOf("chill" to 0.3, "steady" to 0.5, "aggressive" to 0.75)
    val GAIN_PACES = mapOf("chill" to 0.1, "steady" to 0.25, "aggressive" to 0.4)

    fun isCoachStyle(v: String?): Boolean = v == "calm" || v == "balanced" || v == "no_excuses"

    /** Under 18 the coach is capped at Balanced (the database trigger does the same). */
    fun effectiveCoachStyle(style: String?, age: Int?): String {
        val s = if (isCoachStyle(style)) style!! else "balanced"
        return if (Goals.isTeen(age) && s == "no_excuses") "balanced" else s
    }

    /** The goal_type the maths uses. Recomp is a slow cut; a teen never gets a deficit. */
    fun goalTypeOf(goal: String?, age: Int?): String = when {
        goal == "gain" -> "gain"
        (goal == "lose" || goal == "recomp") && !Goals.isTeen(age) -> "lose"
        else -> "maintain"
    }

    fun paceKg(goalType: String, pace: String?): Double {
        if (goalType == "maintain") return 0.0
        val table = if (goalType == "gain") GAIN_PACES else LOSS_PACES
        return table[pace ?: "steady"] ?: table.getValue("steady")
    }

    /** The profile columns the answers fill (the classic ones, i.e. without schema_v37). */
    fun toProfile(a: Answers, base: Profile = Profile(), today: String = Dates.today()): Profile {
        val age = Goals.ageYears(a.dob ?: base.dob, today)
        val goalType = if (a.goal != null) goalTypeOf(a.goal, age) else base.goalType
        val speed = paceKg(goalType, a.pace)
        return base.copy(
            name = a.name ?: base.name,
            heightCm = a.heightCm ?: base.heightCm,
            weightKg = a.weightKg ?: base.weightKg,
            dob = a.dob ?: base.dob,
            gender = a.gender ?: base.gender,
            goalType = goalType,
            goalWeightKg = if (goalType == "maintain") null else (a.goalWeightKg ?: base.goalWeightKg),
            goalSpeedKgWk = if (speed > 0) speed else base.goalSpeedKgWk,
            weeklyWorkoutTarget = a.trainingDays ?: base.weeklyWorkoutTarget,
        )
    }

    data class Targets(val calories: Int, val protein: Int, val carbs: Int, val fat: Int, val fiber: Int)

    data class Plan(
        val targets: Targets,
        val goalDate: String?,
        val weeks: Int?,
        val paceKgWk: Double,
        val teen: Boolean,
        val goalType: String,
        val reasons: List<String>,
        val honest: String,
    ) {
        companion object {
            /** The /api/onboarding/plan answer. */
            fun from(o: JSONObject): Plan? {
                val t = o.optJSONObject("targets") ?: return null
                return Plan(
                    Targets(t.optInt("calories"), t.optInt("protein"), t.optInt("carbs"), t.optInt("fat"), t.optInt("fiber", fiberFor(t.optInt("calories")))),
                    goalDate = if (o.isNull("goal_date")) null else o.optString("goal_date").ifBlank { null },
                    weeks = if (o.isNull("weeks") || !o.has("weeks")) null else o.optInt("weeks"),
                    paceKgWk = o.optDouble("pace_kg_wk", 0.0),
                    teen = o.optBoolean("teen", false),
                    goalType = o.optString("goal_type", "maintain"),
                    reasons = o.optJSONArray("reasons")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                    honest = o.optString("honest"),
                )
            }
        }
    }

    /** Fibre: 14 g per 1,000 kcal (IOM), 25–40 g. */
    fun fiberFor(kcal: Int): Int = max(25, min(40, (kcal / 1000.0 * 14).roundToInt()))

    /** Everything the reveal screen shows; null while the body details are missing. */
    fun plan(a: Answers, today: String = Dates.today()): Plan? {
        val p = toProfile(a, Profile(gender = a.gender), today)
        val pl = Goals.plan(p, today) ?: return null
        val mode = DietModes.effective(a.dietMode ?: DietModes.BALANCED, pl.age)
        val g = if (mode == DietModes.BALANCED) pl.targets else DietModes.targets(p, pl.targets.calories.toDouble(), mode, today)
        val kg = p.weightKg!!
        val speed = when (pl.goal) {
            "lose" -> min(p.goalSpeedKgWk, Goals.maxSafeWeeklyLossKg(kg))
            "gain" -> min(p.goalSpeedKgWk, Goals.maxSafeWeeklyGainKg(kg))
            else -> 0.0
        }
        val target = p.goalWeightKg
        var weeks: Int? = null
        var goalDate: String? = null
        if (speed > 0 && target != null && ((pl.goal == "lose" && target < kg) || (pl.goal == "gain" && target > kg))) {
            weeks = max(1, ceil(abs(kg - target) / speed).toInt())
            goalDate = Dates.addDays(today, ceil(abs(kg - target) / speed * 7).toLong())
        }
        val t = Targets(g.calories, g.protein, g.carbs, g.fat, fiberFor(g.calories))
        return Plan(t, goalDate, weeks, (speed * 100).roundToInt() / 100.0, pl.teen, pl.goal, reasonsFor(a, t, pl.teen), honestLine(pl.goal))
    }

    private val COACH_REASON = mapOf(
        "calm" to "**Calm** coach: one kind note a morning, zero guilt",
        "balanced" to "**Balanced** coach: honest, with one concrete ask a day",
        "no_excuses" to "**No-excuses** coach, a nudge at 6 pm on training days",
    )

    private val OBSTACLE_REASON = mapOf(
        "exam_stress" to "Protein front-loaded at breakfast for **exam-stress** snacking",
        "mess_food" to "Mess-food swaps: **dal + curd + roti** math done for you",
        "late_night" to "A **late-night** protein snack planned in, so Maggi isn't the only option",
        "no_time" to "**No-cook** picks first in every suggestion",
        "eating_out" to "**Eating out** logs in one line, and menus scan too",
        "lost_motivation" to "A **daily streak** and a buddy, so week 3 isn't a solo fight",
    )

    /** Three "why this works for you" lines. `**x**` marks the bold words. */
    fun reasonsFor(a: Answers, t: Targets, teen: Boolean): List<String> {
        val out = mutableListOf<String>()
        for (o in a.obstacles.orEmpty()) { val r = OBSTACLE_REASON[o]; if (r != null && out.size < 2) out += r }
        val days = a.trainingDays ?: 0
        if (out.size < 2 && days > 0) out += "**${t.protein} g protein** to back $days training day${if (days == 1) "" else "s"} a week"
        if (out.size < 2) out += "**${t.protein} g protein** a day keeps you full and protects muscle"
        out += COACH_REASON.getValue(effectiveCoachStyle(a.coachStyle, if (teen) 15 else 30))
        return out.take(3)
    }

    fun honestLine(goal: String): String = when (goal) {
        "lose" -> "First 2 weeks: mostly water weight. Week 3 slows. That's normal."
        "gain" -> "The scale jumps early from water and food. Then it's slow, and that's the point."
        else -> "Some days up, some days down. The weekly average is what counts."
    }

    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** "2027-02-14" → "14 February" (or "14 Feb" when [short]). */
    fun prettyDate(iso: String, short: Boolean = false): String {
        val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
        val m = parts.getOrNull(1) ?: 1
        val d = parts.getOrNull(2) ?: 1
        val name = MONTHS[(m - 1).coerceIn(0, 11)]
        return "$d ${if (short) name.take(3) else name}"
    }

    /** The coach note under the first parsed meal (screen 4). Deterministic, no AI call. */
    fun firstLogNote(calories: Double, protein: Double, names: List<String>): String {
        val p = protein.roundToInt()
        val joined = names.take(2).joinToString(" + ")
        return when {
            p >= 25 -> "Solid plate. ${if (joined.isNotBlank()) "$joined = " else ""}$p g protein already. Keep that up at dinner and you're golden."
            p >= 12 -> "Good start: $p g protein. Add paneer, eggs or curd at your next meal and you're on track."
            else -> "Logged. Only $p g protein here, so make the next meal the protein one: dal, paneer, eggs or chicken."
        }
    }

    /** The building screen's checklist, from the answers. */
    fun buildingLines(a: Answers, hasFirstLog: Boolean): List<String> = buildList {
        if (hasFirstLog) add("Reading your first meal")
        add("Setting calories + protein")
        a.obstacles.orEmpty().take(2).forEach { o ->
            add(
                when (o) {
                    "exam_stress" -> "Planning around exam stress"
                    "mess_food" -> "Hostel-food swaps"
                    "late_night" -> "A plan for the 1 am kitchen run"
                    "no_time" -> "No-cook picks first"
                    "eating_out" -> "Eating-out shortcuts"
                    else -> "A streak that survives week 3"
                },
            )
        }
        add("Loading your ${styleLabel(a.coachStyle)} coach")
        if (a.goal == "lose" || a.goal == "recomp" || a.goal == "gain") add("Pacing to your finish date") else add("Setting your daily rhythm")
    }
}
