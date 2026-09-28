package com.sohum.bandlog.util

/**
 * v2.18 D12 squad vs squad leagues. UNCONFIRMED by the owner, so it's behind two switches that are
 * both OFF: [LEAGUES_ENABLED] here and app_config.leagues.enabled (web docs/schema_v44.sql). Tiers
 * 1 (Diamond) … 5 (Bronze); each week the top 20 % of a tier go up and the bottom 20 % go down (at
 * least one each way once a tier has 5+ squads). Pure. Port of the web's src/lib/social/leagues.ts.
 */
object Leagues {
    const val LEAGUES_ENABLED = false
    val TIERS = listOf("Diamond", "Platinum", "Gold", "Silver", "Bronze")
    const val TOP_TIER = 1
    const val BOTTOM_TIER = 5
    const val MOVE_SHARE = 0.2

    fun tierName(tier: Int): String = TIERS[(if (tier == 0) BOTTOM_TIER else tier).coerceIn(TOP_TIER, BOTTOM_TIER) - 1]

    data class Standing(val groupId: String, val points: Int, val name: String? = null)
    data class Result(val groupId: String, val rank: Int, val movement: String, val nextTier: Int)

    fun movers(n: Int): Int = if (n < 5) 0 else maxOf(1, kotlin.math.floor(n * MOVE_SHARE).toInt())

    /** Closes one tier's week. Ties keep the given order. The top tier can't go up, the bottom can't go down. */
    fun closeWeek(standings: List<Standing>, tier: Int): List<Result> {
        val ranked = standings.withIndex().sortedWith(compareByDescending<IndexedValue<Standing>> { it.value.points }.thenBy { it.index }).map { it.value }
        val m = movers(ranked.size)
        return ranked.mapIndexed { idx, s ->
            val movement = when {
                idx < m && tier > TOP_TIER -> "up"
                idx >= ranked.size - m && tier < BOTTOM_TIER -> "down"
                else -> "stay"
            }
            Result(s.groupId, idx + 1, movement, when (movement) { "up" -> tier - 1; "down" -> tier + 1; else -> tier })
        }
    }

    /** Whether the leagues screen shows at all. */
    fun leaguesOn(serverEnabled: Boolean?): Boolean = LEAGUES_ENABLED && serverEnabled == true
}
