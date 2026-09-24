package com.sohum.bandlog.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * v2.7 squad challenges: the pure bits both the create sheet and the cards use — default targets
 * and titles, lengths, status and the "N days left" / "starts in N days" lines. Mirrors the spec
 * (CHALLENGES_SPEC.md); dates are ISO yyyy-MM-dd, both ends inclusive.
 */
object ChallengeMath {
    const val TRAIN = "train_days"
    const val PROTEIN = "protein_days"
    const val LOG = "log_days"

    /** Most upcoming + active challenges a squad can have at once. */
    const val MAX_OPEN = 3
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 60
    const val MIN_PROTEIN = 40
    const val MAX_PROTEIN = 300
    const val DEFAULT_PROTEIN = 100
    const val MAX_TITLE = 60

    val LENGTHS = listOf(7, 14, 30)

    /** log_days: every day; train_days: 70 % of the length; protein_days: 5 of every 7. */
    fun defaultTarget(kind: String, length: Int): Int {
        val raw = when (kind) {
            LOG -> length.toDouble()
            PROTEIN -> length * 5.0 / 7.0
            else -> length * 0.7
        }
        return raw.roundToInt().coerceIn(1, length.coerceAtLeast(1))
    }

    /** The user's own protein goal when there is one, else 100 g — clamped to what the table allows. */
    fun defaultProtein(goal: Int?): Int = (goal?.takeIf { it > 0 } ?: DEFAULT_PROTEIN).coerceIn(MIN_PROTEIN, MAX_PROTEIN)

    /** "Train 10 of 14 days", "Hit 100 g protein 5 of 7 days", "Log food every day for 7 days". */
    fun defaultTitle(kind: String, target: Int, length: Int, protein: Int?): String = when (kind) {
        PROTEIN -> "Hit ${protein ?: DEFAULT_PROTEIN} g protein $target of $length days"
        LOG -> if (target >= length) "Log food every day for $length days" else "Log food $target of $length days"
        else -> if (target >= length) "Train every day for $length days" else "Train $target of $length days"
    }.take(MAX_TITLE)

    /** Inclusive end date for a challenge of [length] days starting [startsOn]. */
    fun endsOn(startsOn: String, length: Int): String = LocalDate.parse(startsOn).plusDays((length - 1).toLong()).toString()

    fun lengthDays(startsOn: String, endsOn: String): Int = runCatching {
        (ChronoUnit.DAYS.between(LocalDate.parse(startsOn), LocalDate.parse(endsOn)) + 1).toInt()
    }.getOrDefault(1)

    /** upcoming when today < starts_on, ended when today > ends_on, else active. */
    fun status(today: String, startsOn: String, endsOn: String): String = when {
        today < startsOn -> "upcoming"
        today > endsOn -> "ended"
        else -> "active"
    }

    /** Days left including today (the last day counts as 1); 0 once it's over. */
    fun daysLeft(today: String, endsOn: String): Int = runCatching {
        (ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(endsOn)) + 1).toInt().coerceAtLeast(0)
    }.getOrDefault(0)

    fun daysUntil(today: String, startsOn: String): Int = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(startsOn)).toInt().coerceAtLeast(0)
    }.getOrDefault(0)

    /** "3 days left", "last day 👀", "starts tomorrow", "starts in 4 days", "wrapped". */
    fun timeLine(today: String, startsOn: String, endsOn: String): String = when (status(today, startsOn, endsOn)) {
        "upcoming" -> daysUntil(today, startsOn).let { if (it <= 1) "starts tomorrow" else "starts in $it days" }
        "ended" -> "wrapped"
        else -> daysLeft(today, endsOn).let { if (it <= 1) "last day 👀" else "$it days left" }
    }

    /** "12/14 days 🔥" (the flame once there's at least one day in). */
    fun progressLine(progress: Int, target: Int): String = "$progress/$target days" + if (progress > 0) " 🔥" else ""

    fun fraction(progress: Int, target: Int): Float = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)

    /** Emoji + short label for a kind, as on the create sheet's template chips. */
    fun kindLabel(kind: String): String = when (kind) {
        PROTEIN -> "💪 Protein days"
        LOG -> "📝 Log every day"
        else -> "🏋️ Train days"
    }

    fun startedBody(title: String) = "🏁 started \"$title\""
    fun completedBody(title: String) = "🏆 completed \"$title\""
}
