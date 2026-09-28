package com.sohum.bandlog.util

/**
 * v2.18 D11 premium cover packs and badge skins (web docs/schema_v44.sql pack_unlocks,
 * profiles.badge_skin, cover_preset widened to "-gold"). Payments aren't wired: during the beta
 * every pack unlocks free (`via = 'beta_free'`) and the price is still shown. Pure. Port of the
 * web's src/lib/social/packs.ts.
 *
 * Gold covers are the 17 dark motifs recoloured by [goldifySvg]: every #hex is mapped by its luma
 * onto an ink → bronze → gold → pale-gold ramp. Id "<motif>-gold"; art = the motif's DARK cover.
 */
object Packs {
    data class Pack(val id: String, val kind: String, val name: String, val blurb: String, val priceInr: Int)

    val PACKS = listOf(
        Pack("covers-gold", "covers", "Gold edition covers", "All 17 covers in dark gold.", 149),
        Pack("skin-obsidian", "badge_skin", "Obsidian badges", "Your jewellery in black glass and gunmetal.", 99),
        Pack("skin-rose", "badge_skin", "Rose gold badges", "Every frame in warm rose gold.", 99),
    )

    const val PACKS_BETA_FREE_DEFAULT = true
    const val COVERS_PACK = "covers-gold"

    fun packById(id: String): Pack? = PACKS.firstOrNull { it.id == id }

    fun priceLabel(p: Pack, betaFree: Boolean): String {
        val price = Money.inr(p.priceInr)
        return if (betaFree) "$price · free in the beta" else price
    }

    // ---------------- Gold covers ----------------

    data class GoldCover(val id: String, val name: String, val darkIndex: Int, val slug: String)

    val GOLD_COVERS: List<GoldCover> = Covers.ALL.filter { it.tone == "dark" }.map { c ->
        val slug = c.id.removeSuffix("-dark")
        GoldCover("$slug-gold", c.name, c.n - 1, slug)
    }

    fun isGoldCover(id: String?): Boolean = id != null && GOLD_COVERS.any { it.id == id }
    fun goldCover(id: String?): GoldCover? = GOLD_COVERS.firstOrNull { it.id == id }

    /** Ink → bronze → gold → pale gold, by luminance 0..1. */
    val GOLD_RAMP: List<Pair<Double, IntArray>> = listOf(
        0.0 to intArrayOf(11, 9, 6),
        0.3 to intArrayOf(94, 69, 24),
        0.62 to intArrayOf(217, 184, 114),
        1.0 to intArrayOf(251, 231, 168),
    )

    private fun hexToRgb(h: String): IntArray? {
        var s = h.removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (!Regex("^[0-9a-fA-F]{6}$").matches(s)) return null
        return intArrayOf(s.substring(0, 2).toInt(16), s.substring(2, 4).toInt(16), s.substring(4, 6).toInt(16))
    }

    /** JS Math.round (half up, towards +∞). */
    private fun jsRound(v: Double): Int = kotlin.math.floor(v + 0.5).toInt()
    private fun toHex(n: Double) = jsRound(n).coerceIn(0, 255).toString(16).padStart(2, '0')

    /** Rec. 601 luma, 0..1. */
    fun luma(rgb: IntArray): Double = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255.0

    fun goldHex(hex: String): String {
        val rgb = hexToRgb(hex) ?: return hex
        val l = luma(rgb)
        for (i in 1 until GOLD_RAMP.size) {
            val (p1, c1) = GOLD_RAMP[i]
            val (p0, c0) = GOLD_RAMP[i - 1]
            if (l <= p1) {
                val t = if (p1 == p0) 0.0 else (l - p0) / (p1 - p0)
                return "#" + (0..2).joinToString("") { k -> toHex(c0[k] + (c1[k] - c0[k]) * t) }
            }
        }
        val last = GOLD_RAMP.last().second
        return "#" + last.joinToString("") { toHex(it.toDouble()) }
    }

    private val HEX = Regex("#[0-9a-fA-F]{6}\\b|#[0-9a-fA-F]{3}\\b")

    /** Every #rgb / #rrggbb in an SVG string → its gold. Lower-case output. */
    fun goldifySvg(svg: String): String = HEX.replace(svg) { goldHex(it.value) }

    // ---------------- Badge skins ----------------

    const val SKIN_KEY = "li-badge-skin"
    val SKINS = listOf("classic", "obsidian", "rose")

    /** Frame metal per skin (overrides the tier metal); classic = the tier's own metal. */
    val SKIN_METAL = mapOf(
        "obsidian" to longArrayOf(0xFF8A8F98, 0xFF2A2C31, 0xFF060607),
        "rose" to longArrayOf(0xFFFFD9CF, 0xFFC98A7A, 0xFF5A2E25),
    )
    val SKIN_PACK = mapOf("obsidian" to "skin-obsidian", "rose" to "skin-rose")

    fun parseSkin(v: Any?): String = if (v == "obsidian" || v == "rose") v as String else "classic"

    fun skinAllowed(skin: String, unlocked: Collection<String>): Boolean {
        if (skin == "classic") return true
        return SKIN_PACK[skin]?.let { it in unlocked } == true
    }
}
