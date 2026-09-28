package com.sohum.bandlog.util

/**
 * v2.16 "jewellery" badges (board RefJewellery): a bevelled metal frame, a grainy face and a glowing
 * faceted gem. Frame SHAPE by category, frame METAL by tier, gem COLOUR by category. The badge list
 * and criteria are unchanged ([Badges]); only how they look. Port of the web's src/lib/jewels.ts:
 * ids, tiers and colours are shared (docs/v216-shared.md in the web repo), keep them in step.
 */
object Jewels {

    enum class Category(val id: String) { STREAK("streak"), NUTRITION("nutrition"), TRAINING("training"), SQUAD("squad"), SPECIAL("special") }
    enum class Tier(val id: String, val label: String, val rank: Int) { BRONZE("bronze", "Bronze", 1), SILVER("silver", "Silver", 2), GOLD("gold", "Gold", 3), PLATINUM("platinum", "Platinum", 4) }
    enum class Shape { SHIELD, HEXAGON, DIAMOND, ROUND, OCTAGON }

    fun shapeOf(c: Category): Shape = when (c) {
        Category.STREAK -> Shape.SHIELD
        Category.NUTRITION -> Shape.HEXAGON
        Category.TRAINING -> Shape.DIAMOND
        Category.SQUAD -> Shape.ROUND
        Category.SPECIAL -> Shape.OCTAGON
    }

    /** [highlight, mid, shadow] (ARGB) for the frame's diagonal gradient. */
    fun metal(t: Tier): LongArray = when (t) {
        Tier.BRONZE -> longArrayOf(0xFFF0B48A, 0xFFB0643A, 0xFF5A2C12)
        Tier.SILVER -> longArrayOf(0xFFF4F5F7, 0xFFA9ADB4, 0xFF4F535A)
        Tier.GOLD -> longArrayOf(0xFFFBE7A8, 0xFFD9B872, 0xFF5E4518)
        Tier.PLATINUM -> longArrayOf(0xFFFFFFFF, 0xFFCFD8E2, 0xFF66717E)
    }

    /** [light, mid, deep] (ARGB) for the gem. */
    fun gem(c: Category): LongArray = when (c) {
        Category.STREAK -> longArrayOf(0xFFFFB08A, 0xFFFF5B1F, 0xFFC2410C)
        Category.NUTRITION -> longArrayOf(0xFFB8F5D8, 0xFF1FAE6F, 0xFF0A4D31)
        Category.TRAINING -> longArrayOf(0xFF9FD0FF, 0xFF2A6FD8, 0xFF0E2D66)
        Category.SQUAD -> longArrayOf(0xFFE0C8FF, 0xFF8B5CF6, 0xFF3B1A7A)
        Category.SPECIAL -> longArrayOf(0xFFFFF1C4, 0xFFE2B04A, 0xFF6B4A10)
    }

    val LOCKED_METAL = longArrayOf(0xFF4A4A4F, 0xFF26262A, 0xFF111113)
    val LOCKED_GEM = longArrayOf(0xFF5A5A60, 0xFF34343A, 0xFF1A1A1D)

    /** Which category each existing badge group wears (training / squad are ready for new badges). */
    fun categoryOf(g: Badges.Group): Category = when (g) {
        Badges.Group.STREAK -> Category.STREAK
        Badges.Group.MEALS -> Category.NUTRITION
        Badges.Group.CALORIES -> Category.SPECIAL
    }

    private val TIER_BY_NAME = mapOf(
        "Rookie" to Tier.BRONZE, "Getting Serious" to Tier.SILVER, "Locked In" to Tier.GOLD, "Triple Threat" to Tier.GOLD,
        "No Days Off" to Tier.PLATINUM, "Immortal" to Tier.PLATINUM,
        "Forking Around" to Tier.BRONZE, "Mission: Nutrition" to Tier.SILVER, "The Logfather" to Tier.GOLD,
        "One Hit Wonder" to Tier.BRONZE, "Loyalty III" to Tier.SILVER, "Bullseye" to Tier.GOLD,
    )

    fun tierOf(b: Badges.Badge): Tier = TIER_BY_NAME[b.name] ?: Tier.BRONZE
    fun categoryOf(b: Badges.Badge): Category = categoryOf(b.group)

    /** "Getting Serious" → "getting-serious", "Mission: Nutrition" → "mission-nutrition". */
    fun idOf(b: Badges.Badge): String = b.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    data class Item(val badge: Badges.Badge, val id: String, val category: Category, val tier: Tier, val got: Boolean, val value: Int, val fraction: Float)

    fun items(p: Badges.Progress): List<Item> = Badges.ALL.map { b ->
        val v = p.value(b.group)
        Item(b, idOf(b), categoryOf(b), tierOf(b), p.earned(b), v, (v.toFloat() / b.need).coerceIn(0f, 1f))
    }

    /** The best earned badge (highest tier, then the hardest), or null. */
    fun top(p: Badges.Progress): Item? = items(p).filter { it.got }.sortedWith(compareByDescending<Item> { it.tier.rank }.thenByDescending { it.badge.need }).firstOrNull()

    /** The locked badge closest to done ("Next up"), or null when every badge is earned. */
    fun next(p: Badges.Progress): Item? = items(p).filter { !it.got }.sortedWith(compareByDescending<Item> { it.fraction }.thenBy { it.badge.need }).firstOrNull()

    /** A squadmate's top badge from their current day streak (a lower bound of their best run); null under 3 days. */
    fun streakJewel(flames: Int): Pair<Category, Tier>? =
        Badges.ALL.filter { it.group == Badges.Group.STREAK && flames >= it.need }.lastOrNull()?.let { Category.STREAK to tierOf(it) }

    val UNLOCK_LINES = listOf("Here’s some jewellery.", "New hardware.", "That’s going on the wall.")

    /** Same badge, same line (the web's string hash, 32-bit unsigned). */
    fun unlockLine(id: String): String {
        var h = 0L
        for (ch in id) h = (h * 31 + ch.code) and 0xFFFFFFFFL
        return UNLOCK_LINES[(h % UNLOCK_LINES.size).toInt()]
    }

    fun unlockCaption(b: Badges.Badge): String {
        val tier = tierOf(b).label
        val what = when (b.group) {
            Badges.Group.STREAK -> "You logged ${b.need} days in a row"
            Badges.Group.MEALS -> "You logged ${b.need} meals"
            Badges.Group.CALORIES -> if (b.need == 1) "You landed a day on target" else "You landed ${b.need} days on target"
        }
        return "$what and earned ${b.name}, a $tier badge."
    }

    /** Newly earned items given what this device has seen. `seen` null = first run: nothing is "new". */
    fun newlyEarned(p: Badges.Progress, seen: Set<String>?): List<Item> {
        if (seen == null) return emptyList()
        return items(p).filter { it.got && it.id !in seen }
    }
}
