package com.sohum.bandlog.util

import com.sohum.bandlog.data.ChallengeBoardRow
import com.sohum.bandlog.data.GroupPost
import com.sohum.bandlog.data.LeaderRow
import com.sohum.bandlog.data.SquadDay
import com.sohum.bandlog.data.SquadMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v2.16 premium Squad: card lines, squad streak, rank, who logged today, leaderboard metric. */
class SquadPremiumTest {
    private val today = "2026-09-29"
    private fun member(id: String, name: String, streak: Int, trained: Boolean = false, meals: Int? = null) =
        SquadMember(id, name, true, false, listOf(SquadDay(today, trained, streak, null, null, null, meals)))
    private fun post(user: String, name: String, kind: String, body: String = "") =
        GroupPost("p", "g", user, kind, body, null, null, "2026-09-29T08:00:00Z", name, null, null)

    @Test fun activityLines() {
        assertEquals("Ayaan posted a PR", SquadPremium.activityLine(post("a", "Ayaan Shah", "pr"), "me"))
        assertEquals("You trained", SquadPremium.activityLine(post("me", "Sohum", "workout"), "me"))
        assertEquals("Riya: see you at 6", SquadPremium.activityLine(post("r", "Riya", "message", "see you at 6"), "me"))
        assertNull(SquadPremium.activityLine(null, "me"))
    }

    @Test fun streakRankAndToday() {
        val board = listOf(member("me", "Sohum", 19, trained = true), member("a", "Ayaan", 12, meals = 2), member("h", "Himanshu", 6))
        assertEquals(37, SquadPremium.squadStreak(board))
        assertEquals(1, SquadPremium.myRank(board, "me"))
        assertEquals(2, SquadPremium.myRank(board, "a"))
        assertEquals(0, SquadPremium.myRank(board, "nobody"))
        val friends = SquadPremium.friendsLoggedToday(listOf(board, listOf(member("a", "Ayaan", 12, meals = 1))), today, "me")
        assertEquals(listOf("a"), friends.map { it.userId })
        assertEquals("Ayaan and Himanshu", SquadPremium.names(listOf("Ayaan Shah", "Himanshu")))
        assertEquals("A, B and 2 more", SquadPremium.names(listOf("A", "B", "C", "D")))
    }

    @Test fun leaderboardUsesStreakDaysUnlessAChallengeRuns() {
        val leaders = listOf(LeaderRow("a", "Ayaan", null, null, 12, 40), LeaderRow("s", "Sohum", null, null, 19, 10), LeaderRow("h", "Himanshu", null, null, 6, 5))
        val streak = SquadPremium.places(leaders, null)
        assertEquals(listOf("s", "a", "h"), streak.map { it.userId })
        assertEquals(1f, streak[0].fraction, 0f) // 19 days caps the ring at 7/7
        assertEquals(listOf(2, 1, 3), SquadPremium.podium(streak).map { it.first })
        val board = listOf(ChallengeBoardRow("h", "Himanshu", null, null, 5, false, null, 1), ChallengeBoardRow("s", "Sohum", null, null, 3, false, null, 2))
        val ch = SquadPremium.places(leaders, board)
        assertEquals(listOf("h", "s"), ch.map { it.userId })
        assertEquals(6, ch[0].streak)
        assertEquals(0.6f, ch[1].fraction, 1e-6f)
    }
}
