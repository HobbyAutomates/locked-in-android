package com.sohum.bandlog.util

/**
 * v2.16 profile covers (boards CoverPicker + CoverPresets): 17 vector motifs × light / dark = 34.
 * Port of the web's src/lib/covers.ts: the ids are shared and stored in `profiles.cover_preset`
 * (schema_v40, docs/schema_v40.sql; not applied yet, so the choice also lives on the device).
 * #01 "plates-light" is today's plates cover and the default; unknown or empty ids fall back to it.
 */
object Covers {
    enum class Category(val key: String, val label: String) { IRON("iron", "Iron"), CARDIO("cardio", "Cardio"), OUTDOOR("outdoor", "Outdoor"), STUDIO("studio", "Studio"), MINIMAL("minimal", "Minimal") }

    data class Preset(val id: String, val n: Int, val name: String, val tone: String, val category: Category) {
        val label: String get() = "$name · $tone"
    }

    private data class Motif(val slug: String, val name: String, val category: Category)

    /** The 17 motifs in board order; each is #(2k+1) light and #(2k+2) dark. */
    private val MOTIFS = listOf(
        Motif("plates", "Plates", Category.IRON),
        Motif("dumbbells", "Dumbbells", Category.IRON),
        Motif("barbell", "Barbell", Category.IRON),
        Motif("kettlebells", "Kettlebells", Category.IRON),
        Motif("track", "Track", Category.CARDIO),
        Motif("chalk", "Chalk", Category.STUDIO),
        Motif("ropes", "Battle ropes", Category.CARDIO),
        Motif("summit", "Summit", Category.OUTDOOR),
        Motif("pool", "Pool lanes", Category.OUTDOOR),
        Motif("heartbeat", "Heartbeat", Category.CARDIO),
        Motif("trail", "Trail map", Category.OUTDOOR),
        Motif("grid", "Studio grid", Category.MINIMAL),
        Motif("yoga", "Yoga mat", Category.STUDIO),
        Motif("rings", "Rings", Category.STUDIO),
        Motif("ride", "Ride", Category.CARDIO),
        Motif("court", "Court", Category.OUTDOOR),
        Motif("ember", "Ember", Category.MINIMAL),
    )

    val ALL: List<Preset> = MOTIFS.flatMapIndexed { k, m ->
        listOf("light", "dark").mapIndexed { j, tone -> Preset("${m.slug}-$tone", 2 * k + j + 1, m.name, tone, m.category) }
    }

    const val DEFAULT = "plates-light"

    /** The preset for a stored id; anything unknown (or null) is the default plates cover. */
    fun of(id: String?): Preset = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun isId(id: String?): Boolean = id != null && ALL.any { it.id == id }

    fun inCategory(c: Category?): List<Preset> = if (c == null) ALL else ALL.filter { it.category == c }

    /** Which id to show: the profile's when the column exists and holds a valid id, else the device's, else the default. */
    fun resolve(profileId: String?, localId: String?): String = when {
        isId(profileId) -> profileId!!
        isId(localId) -> localId!!
        else -> DEFAULT
    }
}
