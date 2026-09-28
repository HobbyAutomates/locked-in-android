package com.sohum.bandlog.util

/**
 * v2.18 E2 regional food names for search: Marathi (mr), Tamil (ta) and Bengali (bn) words, in
 * Roman script as people type them, mapped onto the names search_foods already knows. Port of the
 * web's src/lib/social/regionalFoods.ts (same table). Hindi / Hinglish already works through
 * foods.names_local.
 *
 * regionalQuery("macher jhol") → "fish curry"; regionalQuery("2 poli") → "2 chapati";
 * anything without a regional word comes back unchanged.
 */
object RegionalFoods {
    data class Alias(val term: String, val lang: String, val canonical: String)

    val ALIASES = listOf(
        Alias("poli", "mr", "chapati"),
        Alias("chapati poli", "mr", "chapati"),
        Alias("bhakri", "mr", "jowar roti"),
        Alias("jwarichi bhakri", "mr", "jowar roti"),
        Alias("bajrichi bhakri", "mr", "bajra roti"),
        Alias("varan", "mr", "dal"),
        Alias("varan bhaat", "mr", "dal rice"),
        Alias("amti", "mr", "dal"),
        Alias("pithla", "mr", "besan curry"),
        Alias("zunka", "mr", "besan curry"),
        Alias("usal", "mr", "sprouts curry"),
        Alias("matki usal", "mr", "sprouts curry"),
        Alias("kanda pohe", "mr", "poha"),
        Alias("pohe", "mr", "poha"),
        Alias("koshimbir", "mr", "salad"),
        Alias("taak", "mr", "buttermilk"),
        Alias("sol kadhi", "mr", "kokum kadhi"),
        Alias("palebhaji", "mr", "palak sabzi"),
        Alias("batata bhaji", "mr", "aloo sabzi"),
        Alias("batata vada", "mr", "aloo bonda"),
        Alias("sabudana khichdi", "mr", "sabudana khichdi"),
        Alias("thalipeeth", "mr", "thalipeeth"),
        Alias("anda", "mr", "egg"),
        Alias("kombdi", "mr", "chicken curry"),
        Alias("dahi bhaat", "mr", "curd rice"),
        Alias("tup", "mr", "ghee"),
        Alias("shengdana", "mr", "peanuts"),
        Alias("sadam", "ta", "rice"),
        Alias("sadham", "ta", "rice"),
        Alias("thayir sadam", "ta", "curd rice"),
        Alias("thayir", "ta", "curd"),
        Alias("paruppu", "ta", "dal"),
        Alias("paruppu sadam", "ta", "dal rice"),
        Alias("kuzhambu", "ta", "curry"),
        Alias("kozhambu", "ta", "curry"),
        Alias("vatha kuzhambu", "ta", "tamarind curry"),
        Alias("poriyal", "ta", "vegetable stir fry"),
        Alias("kootu", "ta", "dal vegetable curry"),
        Alias("keerai", "ta", "spinach"),
        Alias("dosai", "ta", "dosa"),
        Alias("thosai", "ta", "dosa"),
        Alias("vadai", "ta", "vada"),
        Alias("medu vadai", "ta", "medu vada"),
        Alias("idiyappam", "ta", "idiyappam"),
        Alias("kozhi", "ta", "chicken curry"),
        Alias("kozhi kuzhambu", "ta", "chicken curry"),
        Alias("meen", "ta", "fish curry"),
        Alias("meen kuzhambu", "ta", "fish curry"),
        Alias("muttai", "ta", "egg"),
        Alias("muttai curry", "ta", "egg curry"),
        Alias("paal", "ta", "milk"),
        Alias("mor", "ta", "buttermilk"),
        Alias("kaapi", "ta", "filter coffee"),
        Alias("chapathi", "ta", "chapati"),
        Alias("payasam", "ta", "kheer"),
        Alias("sundal", "ta", "chana sundal"),
        Alias("kadalai", "ta", "chickpeas"),
        Alias("nei", "ta", "ghee"),
        Alias("bhaat", "bn", "rice"),
        Alias("daal bhaat", "bn", "dal rice"),
        Alias("maach", "bn", "fish curry"),
        Alias("mach", "bn", "fish curry"),
        Alias("macher jhol", "bn", "fish curry"),
        Alias("maacher jhol", "bn", "fish curry"),
        Alias("dim", "bn", "egg"),
        Alias("dimer jhol", "bn", "egg curry"),
        Alias("dim sheddho", "bn", "boiled egg"),
        Alias("murgi", "bn", "chicken curry"),
        Alias("murgir jhol", "bn", "chicken curry"),
        Alias("mangsho", "bn", "mutton curry"),
        Alias("kosha mangsho", "bn", "mutton curry"),
        Alias("luchi", "bn", "puri"),
        Alias("ruti", "bn", "roti"),
        Alias("alu posto", "bn", "aloo posto"),
        Alias("aloo posto", "bn", "aloo posto"),
        Alias("chorchori", "bn", "mixed vegetable curry"),
        Alias("shukto", "bn", "mixed vegetable curry"),
        Alias("shaak", "bn", "spinach"),
        Alias("begun bhaja", "bn", "brinjal fry"),
        Alias("doi", "bn", "curd"),
        Alias("mishti doi", "bn", "mishti doi"),
        Alias("rosogolla", "bn", "rasgulla"),
        Alias("roshogolla", "bn", "rasgulla"),
        Alias("muri", "bn", "puffed rice"),
        Alias("jhalmuri", "bn", "bhel puri"),
        Alias("chingri", "bn", "prawn curry"),
        Alias("khichuri", "bn", "khichdi"),
        Alias("cha", "bn", "tea"),
        Alias("ghugni", "bn", "chole"),
    )

    val LANG_LABEL = mapOf("mr" to "Marathi", "ta" to "Tamil", "bn" to "Bengali")

    private val NON_WORD = Regex("[^a-z0-9\\s]")
    private val SPACES = Regex("\\s+")
    private fun norm(s: String) = s.lowercase().replace(NON_WORD, " ").replace(SPACES, " ").trim()

    // Longest terms first so "macher jhol" wins over "mach" and "thayir sadam" over "thayir".
    private val SORTED = ALIASES.sortedByDescending { it.term.length }
    private val BY_TERM = ALIASES.associate { it.term to it.canonical }
    private val PATTERN = Regex("(?<=^| )(" + SORTED.joinToString("|") { it.term } + ")(?= |$)")

    /** The alias the query (its longest regional phrase) matches, or null. */
    fun regionalMatch(q: String): Alias? {
        val s = " ${norm(q)} "
        return SORTED.firstOrNull { s.contains(" ${it.term} ") }
    }

    /** Replaces regional words / phrases (whole words, longest first, one pass) with their canonical names. */
    fun regionalQuery(q: String): String {
        val s = norm(q)
        if (s.isEmpty()) return q
        var changed = false
        val out = PATTERN.replace(s) { m -> changed = true; BY_TERM[m.value] ?: m.value }
        return if (changed) out else q
    }
}
