package com.sohum.bandlog.util

/**
 * v2.18 D9 seasonal events with limited-edition jewellery (web docs/schema_v44.sql event_badges).
 * Same ids, windows and numbers as the web's src/lib/social/seasonal.ts; the app inserts the
 * event_badges row when the goal is met inside the window. Pure.
 *
 *   Diwali protein challenge   Diwali − 10 … Diwali + 4   protein on 10 days
 *   Monsoon steps              1 Jul … 30 Sep             8,000+ steps on 30 days
 *   New Year transformation    1 Jan … 31 Jan             log 25 days and train 12 days
 */
object Seasonal {
    enum class Metric { PROTEIN_DAYS, STEP_DAYS, LOG_TRAIN }

    data class Badge(val name: String, val shape: Jewels.Shape, val metal: LongArray, val gem: LongArray)

    data class Event(
        val id: String, val slug: String, val year: Int, val title: String, val blurb: String,
        val from: String, val to: String, val metric: Metric, val target: Int,
        val trainTarget: Int? = null, val stepGoal: Int? = null, val badge: Badge,
    )

    /** Lakshmi Puja dates (Diwali) by year. Add a line a year ahead. */
    val DIWALI = mapOf(2026 to "2026-11-08", 2027 to "2027-10-29", 2028 to "2028-10-17")
    const val MONSOON_STEP_GOAL = 8000

    private val GOLD = longArrayOf(0xFFFBE7A8, 0xFFD9B872, 0xFF5E4518)
    private val PLATINUM = longArrayOf(0xFFFFFFFF, 0xFFCFD8E2, 0xFF66717E)

    fun eventsForYear(year: Int): List<Event> = buildList {
        DIWALI[year]?.let { d ->
            add(
                Event(
                    "diwali-protein-$year", "diwali-protein", year, "Diwali protein challenge", "Mithai season. Hit your protein on 10 days around Diwali.",
                    Dates.addDays(d, -10), Dates.addDays(d, 4), Metric.PROTEIN_DAYS, 10,
                    badge = Badge("Diya $year", Jewels.Shape.OCTAGON, GOLD, longArrayOf(0xFFFFE29A, 0xFFFF9F1C, 0xFFB4540A)),
                ),
            )
        }
        add(
            Event(
                "monsoon-steps-$year", "monsoon-steps", year, "Monsoon steps",
                "Rain or not: ${Money.group(MONSOON_STEP_GOAL.toLong())}+ steps on 30 days between July and September.",
                "$year-07-01", "$year-09-30", Metric.STEP_DAYS, 30, stepGoal = MONSOON_STEP_GOAL,
                badge = Badge("Monsoon $year", Jewels.Shape.ROUND, PLATINUM, longArrayOf(0xFFBFE6FF, 0xFF2A7FB8, 0xFF0B3A5C)),
            ),
        )
        add(
            Event(
                "new-year-$year", "new-year", year, "New Year transformation", "January reset: log food on 25 days and train on 12.",
                "$year-01-01", "$year-01-31", Metric.LOG_TRAIN, 25, trainTarget = 12,
                badge = Badge("Resolution $year", Jewels.Shape.DIAMOND, PLATINUM, longArrayOf(0xFFE0C8FF, 0xFF8B5CF6, 0xFF3B1A7A)),
            ),
        )
    }

    data class Around(val live: List<Event>, val soon: List<Event>)

    /** Events whose window includes today, then the next ones starting within [aheadDays]. */
    fun eventsAround(today: String, aheadDays: Int = 45): Around {
        val y = today.take(4).toInt()
        val all = eventsForYear(y) + eventsForYear(y + 1)
        val horizon = Dates.addDays(today, aheadDays.toLong())
        return Around(all.filter { it.from <= today && today <= it.to }, all.filter { it.from > today && it.from <= horizon }.sortedBy { it.from })
    }

    fun eventById(id: String): Event? {
        val m = Regex("-(\\d{4})$").find(id) ?: return null
        return eventsForYear(m.groupValues[1].toInt()).firstOrNull { it.id == id }
    }

    data class Data(val proteinDays: Collection<String>, val stepsByDay: Map<String, Long>, val logDays: Collection<String>, val trainDays: Collection<String>)
    data class Progress(val value: Int, val target: Int, val fraction: Double, val done: Boolean, val line: String)

    private fun inWindow(d: String, e: Event) = d >= e.from && d <= e.to
    private fun countIn(days: Collection<String>, e: Event) = days.filter { inWindow(it, e) }.toSet().size

    fun eventProgress(e: Event, data: Data): Progress = when (e.metric) {
        Metric.PROTEIN_DAYS -> {
            val v = countIn(data.proteinDays, e)
            Progress(v, e.target, minOf(1.0, v.toDouble() / e.target), v >= e.target, "${minOf(v, e.target)} of ${e.target} protein days")
        }
        Metric.STEP_DAYS -> {
            val goal = e.stepGoal ?: MONSOON_STEP_GOAL
            val v = data.stepsByDay.count { (d, n) -> inWindow(d, e) && n >= goal }
            Progress(v, e.target, minOf(1.0, v.toDouble() / e.target), v >= e.target, "${minOf(v, e.target)} of ${e.target} days over ${Money.group(goal.toLong())} steps")
        }
        Metric.LOG_TRAIN -> {
            val logs = countIn(data.logDays, e)
            val trains = countIn(data.trainDays, e)
            val tt = e.trainTarget ?: 12
            val fraction = minOf(1.0, minOf(logs.toDouble() / e.target, trains.toDouble() / tt))
            Progress(
                minOf(logs, e.target) + minOf(trains, tt), e.target + tt, fraction, logs >= e.target && trains >= tt,
                "${minOf(logs, e.target)}/${e.target} log days · ${minOf(trains, tt)}/$tt training days",
            )
        }
    }

    private val SHORT = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

    /** "Ends in 3 days" / "Ends today" / "Starts 29 Oct" / "Ended". */
    fun eventWhen(e: Event, today: String): String {
        if (today < e.from) return "Starts ${Dates.parse(e.from).format(SHORT)}"
        val left = Dates.daysBetween(today, e.to)
        if (left <= 0) return if (today > e.to) "Ended" else "Ends today"
        return "Ends in $left ${if (left == 1L) "day" else "days"}"
    }
}
