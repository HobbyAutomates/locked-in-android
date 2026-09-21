package com.sohum.bandlog.util

/**
 * The twelve badges, in Cal AI's three tiers: consecutive training days, meals logged, and days
 * that landed inside the calorie goal. Names and thresholds are taken verbatim from the app.
 */
object Badges {

    enum class Group { STREAK, MEALS, CALORIES }

    data class Badge(val name: String, val group: Group, val need: Int) {
        /** "50 day streak" / "500 meals logged" / "30 goal days". */
        val requirement: String
            get() = when (group) {
                Group.STREAK -> if (need == 1) "1 day streak" else "$need day streak"
                Group.MEALS -> "$need meals logged"
                Group.CALORIES -> if (need == 1) "1 day on target" else "$need days on target"
            }
    }

    val ALL: List<Badge> = listOf(
        Badge("Rookie", Group.STREAK, 3),
        Badge("Getting Serious", Group.STREAK, 10),
        Badge("Locked In", Group.STREAK, 50),
        Badge("Triple Threat", Group.STREAK, 100),
        Badge("No Days Off", Group.STREAK, 365),
        Badge("Immortal", Group.STREAK, 1000),
        Badge("Forking Around", Group.MEALS, 5),
        Badge("Mission: Nutrition", Group.MEALS, 50),
        Badge("The Logfather", Group.MEALS, 500),
        Badge("One Hit Wonder", Group.CALORIES, 1),
        Badge("Loyalty III", Group.CALORIES, 7),
        Badge("Bullseye", Group.CALORIES, 30),
    )

    fun groupTitle(g: Group) = when (g) {
        Group.STREAK -> "Training streak"
        Group.MEALS -> "Meals logged"
        Group.CALORIES -> "Calorie goal"
    }

    /** How far along the user is for each tier. */
    data class Progress(val streakDays: Int, val meals: Int, val goalDays: Int) {
        fun value(g: Group) = when (g) { Group.STREAK -> streakDays; Group.MEALS -> meals; Group.CALORIES -> goalDays }
        fun earned(b: Badge) = value(b.group) >= b.need
    }

    fun earnedCount(p: Progress) = ALL.count { p.earned(it) }

    /**
     * Longest run of consecutive calendar days present in [dates] (the badge is a personal best,
     * so a broken streak never takes a badge away).
     */
    fun longestDayRun(dates: Collection<String>): Int {
        val sorted = dates.toSortedSet().toList()
        if (sorted.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (Dates.daysBetween(sorted[i - 1], sorted[i]) == 1L) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Share-sheet copy for an earned badge. */
    fun shareText(b: Badge) = "Just unlocked “${b.name}” in Locked In — ${b.requirement}. 🔥"
}
