package com.sohum.bandlog.util

import com.sohum.bandlog.data.ChallengeBoardRow
import com.sohum.bandlog.data.GroupPost
import com.sohum.bandlog.data.LeaderRow
import com.sohum.bandlog.data.SquadMember

/**
 * v2.16 premium Squad (boards PremiumSquad + SquadLeaderboard): the pure bits behind the squad
 * cards (latest activity line, squad streak, my rank, who logged today) and the leaderboard metric
 * (streak days this week, or challenge points while a challenge is running).
 */
object SquadPremium {

    private fun first(name: String) = name.trim().substringBefore(' ').ifBlank { "Someone" }

    /** "Ayaan logged a meal", "You posted a PR", "Riya: see you at 6". Null for no post. */
    fun activityLine(post: GroupPost?, me: String?): String? {
        post ?: return null
        val mine = post.userId == me
        val who = if (mine) "You" else first(post.authorName)
        val body = post.body.trim().replace(Regex("\\s+"), " ")
        return when (post.kind) {
            "message" -> if (body.isBlank()) "$who sent a message" else "$who: $body"
            "meal" -> "$who logged ${if (body.isNotBlank() && body.length <= 28) body.lowercase().removePrefix("logged ") else "a meal"}"
            "workout" -> "$who trained"
            "pr" -> "$who posted a PR"
            "photo" -> "$who shared a photo"
            "challenge", "battle" -> body.ifBlank { "$who started a challenge" }
            else -> body.ifBlank { "$who posted" }
        }
    }

    /** Did this member log anything today (trained, or meals / calories on today's rollup)? */
    fun loggedOn(m: SquadMember, date: String): Boolean {
        val d = m.day(date) ?: return false
        return d.trained || (d.meals ?: 0) > 0 || (d.calories ?: 0.0) > 0.0
    }

    /** Friends (not me) who logged today across [boards], one entry per person, in board order. */
    fun friendsLoggedToday(boards: Collection<List<SquadMember>>, date: String, me: String?): List<SquadMember> =
        boards.flatten().filter { it.userId != me && loggedOn(it, date) }.distinctBy { it.userId }

    /** "Ayaan", "Ayaan and Himanshu", "Ayaan, Himanshu and 2 more". */
    fun names(people: List<String>): String {
        val n = people.map { first(it) }
        return when (n.size) {
            0 -> ""
            1 -> n[0]
            2 -> "${n[0]} and ${n[1]}"
            else -> "${n[0]}, ${n[1]} and ${n.size - 2} more"
        }
    }

    /** The squad's streak: everyone's current day streaks added up (it grows while the squad keeps logging). */
    fun squadStreak(members: List<SquadMember>): Int = members.sumOf { it.weekStreak }

    /** My 1-based rank by current streak (ties share the better rank); 0 when I'm not on the board. */
    fun myRank(members: List<SquadMember>, me: String?): Int {
        val mine = members.firstOrNull { it.userId == me } ?: return 0
        return 1 + members.count { it.weekStreak > mine.weekStreak }
    }

    /** One place on the leaderboard / podium. [value] is streak days or challenge points; [fraction] fills the row's ring. */
    data class Place(val userId: String, val name: String, val username: String?, val avatarPath: String?, val value: Int, val streak: Int, val fraction: Float)

    /**
     * The leaderboard: streak days this week (from group_leaderboard's flames), or — while a
     * challenge is running and its board is loaded — challenge points (progress). Sorted high to low;
     * the ring is days / 7 for streaks and points / the leader's for a challenge.
     */
    fun places(leaders: List<LeaderRow>, challenge: List<ChallengeBoardRow>?): List<Place> {
        val streakOf = leaders.associate { it.userId to it.flames }
        if (!challenge.isNullOrEmpty()) {
            val top = challenge.maxOf { it.progress }.coerceAtLeast(1)
            return challenge.sortedWith(compareByDescending<ChallengeBoardRow> { it.progress }.thenBy { it.name.lowercase() })
                .map { Place(it.userId, it.name, it.username, it.avatarPath, it.progress, streakOf[it.userId] ?: 0, it.progress.toFloat() / top) }
        }
        return leaders.sortedWith(compareByDescending<LeaderRow> { it.flames }.thenBy { it.name.lowercase() })
            .map { Place(it.userId, it.name, it.username, it.avatarPath, it.flames, it.flames, (minOf(it.flames, 7) / 7f)) }
    }

    /** Podium order on screen: 2nd, 1st, 3rd (missing places are left out). */
    fun podium(places: List<Place>): List<Pair<Int, Place>> =
        listOf(1, 0, 2).mapNotNull { i -> places.getOrNull(i)?.let { (i + 1) to it } }
}
