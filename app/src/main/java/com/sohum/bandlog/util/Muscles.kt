package com.sohum.bandlog.util

import androidx.compose.ui.graphics.Color

object Muscles {
    val ALL = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms", "Core", "Glutes", "Quads", "Hamstrings", "Calves", "Other")

    private val colors = mapOf(
        "Chest" to Color(0xFFE76F51), "Back" to Color(0xFF2A9D8F), "Shoulders" to Color(0xFFE0A100),
        "Biceps" to Color(0xFF7C5CFC), "Triceps" to Color(0xFF3B82F6), "Forearms" to Color(0xFF0EA5A4),
        "Core" to Color(0xFFE0559A), "Glutes" to Color(0xFFF97316), "Quads" to Color(0xFF43A047),
        "Hamstrings" to Color(0xFF84CC16), "Calves" to Color(0xFFA16207), "Other" to Color(0xFF94A3B8),
    )
    fun color(m: String): Color = colors[m] ?: colors.getValue("Other")

    val BAND_LEVELS = listOf("Light", "Medium", "Heavy")
    fun bandColor(level: String): Color = when (level) {
        "Light" -> Color(0xFFF2C230)
        "Heavy" -> Color(0xFF64748B)
        else -> Color(0xFFE5484D)
    }
}

/**
 * v2.13 muscle map (spec §12): ~18 regions, and every library exercise ([Lifts.ALL]) mapped to
 * primary and secondary regions by standard kinesiology.
 *
 * RECONCILE AT MERGE: the spec makes web `src/lib/muscles.ts` the canonical list and Android a
 * verbatim copy. When this was written (v213/web-platform) muscles.ts didn't have the map yet, so
 * this table was written from the spec's region list + the app's exercise library. Diff it against
 * the web table and copy the web one over if they differ.
 */
object MuscleMap {
    enum class Region(val key: String, val label: String, val front: Boolean, val back: Boolean) {
        CHEST("chest", "Chest", true, false),
        FRONT_DELTS("front_delts", "Front delts", true, false),
        SIDE_DELTS("side_delts", "Side delts", true, true),
        REAR_DELTS("rear_delts", "Rear delts", false, true),
        BICEPS("biceps", "Biceps", true, false),
        TRICEPS("triceps", "Triceps", false, true),
        FOREARMS("forearms", "Forearms", true, true),
        ABS("abs", "Abs", true, false),
        OBLIQUES("obliques", "Obliques", true, false),
        TRAPS("traps", "Traps", true, true),
        LATS("lats", "Lats", false, true),
        UPPER_BACK("upper_back", "Upper back", false, true),
        LOWER_BACK("lower_back", "Lower back", false, true),
        GLUTES("glutes", "Glutes", false, true),
        QUADS("quads", "Quads", true, false),
        HAMSTRINGS("hamstrings", "Hamstrings", false, true),
        CALVES("calves", "Calves", true, true),
        ADDUCTORS("adductors", "Adductors", true, false);

        companion object {
            fun of(key: String): Region? = entries.firstOrNull { it.key == key }
        }
    }

    data class Targets(val primary: List<Region>, val secondary: List<Region>) {
        val all: List<Region> get() = primary + secondary
    }

    private fun t(primary: String, secondary: String = ""): Targets {
        fun parse(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { Region.of(it) ?: error("Unknown region $it") }
        return Targets(parse(primary), parse(secondary))
    }

    /** Exercise name (lower case) to regions. Covers every entry in [Lifts.ALL]. */
    val TABLE: Map<String, Targets> = linkedMapOf(
        // Chest
        "bench press" to t("chest", "front_delts,triceps"),
        "incline bench press" to t("chest,front_delts", "triceps"),
        "dumbbell bench press" to t("chest", "front_delts,triceps"),
        "incline dumbbell press" to t("chest,front_delts", "triceps"),
        "chest fly" to t("chest", "front_delts"),
        "cable crossover" to t("chest", "front_delts"),
        "chest press machine" to t("chest", "front_delts,triceps"),
        "push-up" to t("chest", "front_delts,triceps,abs"),
        "dip" to t("chest,triceps", "front_delts"),
        // Back
        "deadlift" to t("hamstrings,glutes,lower_back", "quads,traps,forearms,lats,upper_back"),
        "barbell row" to t("lats,upper_back", "rear_delts,biceps,lower_back,forearms"),
        "dumbbell row" to t("lats,upper_back", "rear_delts,biceps"),
        "lat pulldown" to t("lats", "biceps,upper_back,rear_delts"),
        "seated cable row" to t("upper_back,lats", "rear_delts,biceps"),
        "t-bar row" to t("upper_back,lats", "rear_delts,biceps,lower_back"),
        "pull-up" to t("lats", "biceps,upper_back,rear_delts,forearms"),
        "chin-up" to t("lats,biceps", "upper_back,forearms"),
        "inverted row" to t("upper_back,lats", "rear_delts,biceps"),
        "back extension" to t("lower_back", "glutes,hamstrings"),
        "face pull" to t("rear_delts", "upper_back,traps"),
        // Shoulders
        "overhead press" to t("front_delts", "side_delts,triceps,traps"),
        "dumbbell shoulder press" to t("front_delts", "side_delts,triceps"),
        "arnold press" to t("front_delts,side_delts", "triceps"),
        "lateral raise" to t("side_delts", "traps"),
        "front raise" to t("front_delts"),
        "rear delt fly" to t("rear_delts", "upper_back"),
        "shrug" to t("traps", "forearms"),
        "pike push-up" to t("front_delts", "triceps,side_delts"),
        // Arms
        "barbell curl" to t("biceps", "forearms"),
        "dumbbell curl" to t("biceps", "forearms"),
        "hammer curl" to t("biceps,forearms"),
        "preacher curl" to t("biceps"),
        "cable curl" to t("biceps", "forearms"),
        "tricep pushdown" to t("triceps"),
        "skull crusher" to t("triceps"),
        "overhead tricep extension" to t("triceps"),
        "close-grip bench press" to t("triceps,chest", "front_delts"),
        "bench dip" to t("triceps", "chest,front_delts"),
        "wrist curl" to t("forearms"),
        // Legs
        "squat" to t("quads,glutes", "adductors,hamstrings,lower_back"),
        "front squat" to t("quads", "glutes,abs,upper_back"),
        "goblet squat" to t("quads,glutes", "adductors,abs"),
        "leg press" to t("quads,glutes", "adductors,hamstrings"),
        "lunge" to t("quads,glutes", "hamstrings,adductors"),
        "bulgarian split squat" to t("quads,glutes", "adductors,hamstrings"),
        "romanian deadlift" to t("hamstrings,glutes", "lower_back,forearms"),
        "leg extension" to t("quads"),
        "leg curl" to t("hamstrings", "calves"),
        "hip thrust" to t("glutes", "hamstrings"),
        "glute bridge" to t("glutes", "hamstrings"),
        "calf raise" to t("calves"),
        "bodyweight squat" to t("quads,glutes", "adductors"),
        "step-up" to t("quads,glutes", "hamstrings"),
        "jump squat" to t("quads,glutes", "calves"),
        "wall sit" to t("quads", "glutes"),
        // Core & full body
        "plank" to t("abs", "obliques"),
        "side plank" to t("obliques", "abs"),
        "crunch" to t("abs"),
        "sit-up" to t("abs", "obliques"),
        "leg raise" to t("abs"),
        "hanging leg raise" to t("abs", "obliques,forearms"),
        "russian twist" to t("obliques", "abs"),
        "mountain climber" to t("abs", "front_delts,quads"),
        "bicycle crunch" to t("abs,obliques"),
        "cable crunch" to t("abs"),
        "burpee" to t("quads,chest", "front_delts,triceps,abs"),
        "jumping jack" to t("calves", "side_delts"),
        "kettlebell swing" to t("glutes,hamstrings", "lower_back,front_delts,abs"),
        "farmer’s walk" to t("forearms,traps", "abs,obliques"),
        "clean and press" to t("front_delts,glutes,quads", "traps,triceps,hamstrings,upper_back"),
        // Web library names (v2.13 merge: canonical table is web src/lib/muscles.ts)
        "dips" to t("chest,triceps", "front_delts"),
        "diamond push-up" to t("triceps", "chest,front_delts"),
        "ab wheel rollout" to t("abs", "lats,obliques"),
        "jumping jacks" to t("calves", "glutes,side_delts"),
    )

    /** The older coarse groups ([Muscles.ALL], the workouts.muscles column) to regions, for band sessions. */
    val COARSE: Map<String, List<Region>> = mapOf(
        "Chest" to listOf(Region.CHEST),
        "Back" to listOf(Region.LATS, Region.UPPER_BACK),
        "Shoulders" to listOf(Region.FRONT_DELTS, Region.SIDE_DELTS),
        "Biceps" to listOf(Region.BICEPS),
        "Triceps" to listOf(Region.TRICEPS),
        "Forearms" to listOf(Region.FOREARMS),
        "Core" to listOf(Region.ABS, Region.OBLIQUES),
        "Glutes" to listOf(Region.GLUTES),
        "Quads" to listOf(Region.QUADS),
        "Hamstrings" to listOf(Region.HAMSTRINGS),
        "Calves" to listOf(Region.CALVES),
    )

    /** Straight and curly apostrophes both match ("Farmer's walk"). */
    private fun norm(s: String) = s.trim().lowercase().replace('\'', '’')

    fun of(exercise: String): Targets? = TABLE[norm(exercise)]

    /** A secondary muscle counts as half a set (the usual "fractional sets" convention). */
    const val SECONDARY_WEIGHT = 0.5

    /** A band session has no set log; each muscle it lists counts as this many sets. */
    const val BAND_SETS_PER_MUSCLE = 3.0

    /** Evidence-based weekly volume guideline (sets per muscle per week). */
    const val WEEKLY_MIN = 10
    const val WEEKLY_MAX = 20

    /**
     * Sets per region across [workouts]: each logged set is 1 for a primary muscle and
     * [SECONDARY_WEIGHT] for a secondary one; a lift with no sets logged counts as 1 set. Band
     * sessions add [BAND_SETS_PER_MUSCLE] per listed muscle. Exercises not in the table are skipped.
     */
    fun setsPerRegion(workouts: List<com.sohum.bandlog.data.Workout>): Map<Region, Double> {
        val out = LinkedHashMap<Region, Double>()
        fun add(r: Region, v: Double) { out[r] = (out[r] ?: 0.0) + v }
        workouts.forEach { w ->
            if (w.isBands) {
                w.muscles.forEach { m -> COARSE[m]?.forEach { add(it, BAND_SETS_PER_MUSCLE) } }
                return@forEach
            }
            w.lifts.forEach { l ->
                val tg = of(l.name) ?: return@forEach
                val sets = l.sets.size.coerceAtLeast(1).toDouble()
                tg.primary.forEach { add(it, sets) }
                tg.secondary.forEach { add(it, sets * SECONDARY_WEIGHT) }
            }
        }
        return out
    }

    /** Regions for a list of exercise names: primary wins over secondary. */
    fun targetsFor(names: List<String>): Targets {
        val ts = names.mapNotNull { of(it) }
        val prim = ts.flatMap { it.primary }.distinct()
        val sec = ts.flatMap { it.secondary }.distinct().filter { it !in prim }
        return Targets(prim, sec)
    }

    /** 0..1 fill for the heat map: 0 sets is 0, the guideline's lower bound 0.75, its upper bound or more 1. */
    fun heat(sets: Double): Float = when {
        sets <= 0.0 -> 0f
        sets >= WEEKLY_MAX -> 1f
        sets >= WEEKLY_MIN -> (0.75 + 0.25 * (sets - WEEKLY_MIN) / (WEEKLY_MAX - WEEKLY_MIN)).toFloat()
        else -> (0.12 + 0.63 * sets / WEEKLY_MIN).toFloat()
    }

    /** Against the 10-20 sets/week guideline. */
    fun verdict(sets: Double): String = when {
        sets <= 0.0 -> "Not trained"
        sets < WEEKLY_MIN -> "Under $WEEKLY_MIN"
        sets <= WEEKLY_MAX -> "In range"
        else -> "Over $WEEKLY_MAX"
    }
}
