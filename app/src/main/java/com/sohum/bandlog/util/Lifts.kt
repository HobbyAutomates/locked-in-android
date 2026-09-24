package com.sohum.bandlog.util

/**
 * v2.5 the built-in exercise list behind Gym / Bodyweight "Add exercise" (Google Fit / Strong
 * style). [muscles] map onto [Muscles.ALL] so a gym session still fills the muscle column (and
 * the "last trained" streak maths); [bodyweight] ones default to reps only (no kg column).
 */
object Lifts {
    data class Exercise(val name: String, val muscles: List<String>, val bodyweight: Boolean = false)

    private fun e(name: String, vararg m: String, bw: Boolean = false) = Exercise(name, m.toList(), bw)

    val ALL: List<Exercise> = listOf(
        // Chest
        e("Bench press", "Chest", "Triceps"), e("Incline bench press", "Chest", "Shoulders"), e("Dumbbell bench press", "Chest", "Triceps"),
        e("Incline dumbbell press", "Chest", "Shoulders"), e("Chest fly", "Chest"), e("Cable crossover", "Chest"), e("Chest press machine", "Chest", "Triceps"),
        e("Push-up", "Chest", "Triceps", bw = true), e("Dip", "Chest", "Triceps", bw = true),
        // Back
        e("Deadlift", "Back", "Hamstrings", "Glutes"), e("Barbell row", "Back", "Biceps"), e("Dumbbell row", "Back", "Biceps"),
        e("Lat pulldown", "Back", "Biceps"), e("Seated cable row", "Back", "Biceps"), e("T-bar row", "Back"),
        e("Pull-up", "Back", "Biceps", bw = true), e("Chin-up", "Back", "Biceps", bw = true), e("Inverted row", "Back", bw = true),
        e("Back extension", "Back", "Glutes", bw = true), e("Face pull", "Shoulders", "Back"),
        // Shoulders
        e("Overhead press", "Shoulders", "Triceps"), e("Dumbbell shoulder press", "Shoulders", "Triceps"), e("Arnold press", "Shoulders"),
        e("Lateral raise", "Shoulders"), e("Front raise", "Shoulders"), e("Rear delt fly", "Shoulders", "Back"), e("Shrug", "Back"),
        e("Pike push-up", "Shoulders", "Triceps", bw = true),
        // Arms
        e("Barbell curl", "Biceps"), e("Dumbbell curl", "Biceps"), e("Hammer curl", "Biceps", "Forearms"), e("Preacher curl", "Biceps"),
        e("Cable curl", "Biceps"), e("Tricep pushdown", "Triceps"), e("Skull crusher", "Triceps"), e("Overhead tricep extension", "Triceps"),
        e("Close-grip bench press", "Triceps", "Chest"), e("Bench dip", "Triceps", bw = true), e("Wrist curl", "Forearms"),
        // Legs
        e("Squat", "Quads", "Glutes"), e("Front squat", "Quads", "Core"), e("Goblet squat", "Quads", "Glutes"), e("Leg press", "Quads", "Glutes"),
        e("Lunge", "Quads", "Glutes", bw = true), e("Bulgarian split squat", "Quads", "Glutes"), e("Romanian deadlift", "Hamstrings", "Glutes"),
        e("Leg extension", "Quads"), e("Leg curl", "Hamstrings"), e("Hip thrust", "Glutes", "Hamstrings"), e("Glute bridge", "Glutes", bw = true),
        e("Calf raise", "Calves", bw = true), e("Bodyweight squat", "Quads", "Glutes", bw = true), e("Step-up", "Quads", "Glutes", bw = true),
        e("Jump squat", "Quads", "Glutes", bw = true), e("Wall sit", "Quads", bw = true),
        // Core & full body
        e("Plank", "Core", bw = true), e("Side plank", "Core", bw = true), e("Crunch", "Core", bw = true), e("Sit-up", "Core", bw = true),
        e("Leg raise", "Core", bw = true), e("Hanging leg raise", "Core", bw = true), e("Russian twist", "Core", bw = true),
        e("Mountain climber", "Core", bw = true), e("Bicycle crunch", "Core", bw = true), e("Cable crunch", "Core"),
        e("Burpee", "Other", bw = true), e("Jumping jack", "Other", bw = true), e("Kettlebell swing", "Glutes", "Hamstrings"),
        e("Farmer's walk", "Forearms", "Core"), e("Clean and press", "Shoulders", "Quads"),
    )

    private val byName = ALL.associateBy { it.name.lowercase() }

    fun find(name: String): Exercise? = byName[name.trim().lowercase()]

    /** Muscles for a session: the union of each lift's, in [Muscles.ALL] order ("Other" for names we don't know). */
    fun musclesFor(names: List<String>): List<String> {
        val hit = names.flatMap { find(it)?.muscles ?: listOf("Other") }.toSet()
        return Muscles.ALL.filter { it in hit }
    }

    /** Search: prefix matches first, then anywhere in the name or its muscles; bodyweight ones first for a bodyweight session. */
    fun search(q: String, bodyweightFirst: Boolean, recent: List<String> = emptyList()): List<Exercise> {
        val t = q.trim().lowercase()
        val base = if (t.isEmpty()) {
            val rec = recent.mapNotNull { r -> find(r) ?: Exercise(r, listOf("Other")) }
            rec + ALL.filter { a -> rec.none { it.name.equals(a.name, ignoreCase = true) } }
        } else {
            ALL.filter { it.name.lowercase().startsWith(t) } +
                ALL.filter { !it.name.lowercase().startsWith(t) && (t in it.name.lowercase() || it.muscles.any { m -> m.lowercase().startsWith(t) }) }
        }
        if (!bodyweightFirst || t.isNotEmpty()) return base
        // Recent ones stay on top, then bodyweight moves, then the rest.
        val rec = base.filter { s -> recent.any { r -> r.equals(s.name, ignoreCase = true) } }
        return rec + base.filter { it.bodyweight && it !in rec } + base.filter { !it.bodyweight && it !in rec }
    }
}
