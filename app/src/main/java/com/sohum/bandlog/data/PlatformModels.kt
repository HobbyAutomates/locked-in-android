package com.sohum.bandlog.data

import org.json.JSONObject

/*
 * v2.13 platform models (schema_v36): Pro plan fields, the notifications inbox, body
 * measurements and the richer progress-photo row. Kept out of Models.kt so the nutrition half
 * can edit Profile without conflicts; every field tolerates the v36 columns being absent.
 */

private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
private fun JSONObject.d(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }

/** The v36 profile columns this half reads, straight off `profiles?select=*`. */
data class PlatformProfile(
    /** False when the row has no `plan` column (v36 not applied): everyone has Pro then. */
    val planPresent: Boolean = false,
    val plan: String? = null,
    val proUntil: String? = null,
    /** Null = column missing (local setting is used). */
    val proteinNudge: Boolean? = null,
    val proteinNudgeTime: String? = null,
    val dietMode: String? = null,
) {
    val hasPro: Boolean get() = com.sohum.bandlog.util.Pro.hasPro(plan, proUntil, planPresent)

    companion object {
        fun from(o: JSONObject) = PlatformProfile(
            planPresent = o.has("plan"),
            plan = o.s("plan"),
            proUntil = o.s("pro_until"),
            proteinNudge = if (!o.has("protein_nudge") || o.isNull("protein_nudge")) null else o.optBoolean("protein_nudge", true),
            proteinNudgeTime = o.s("protein_nudge_time")?.take(5),
            dietMode = o.s("diet_mode"),
        )
    }
}

/** One row of `bandlog.notifications` (kinds nudge | protein | fasting | checkin | system). */
data class InboxItem(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val url: String?,
    val createdAt: String,
    val readAt: String?,
) {
    val unread: Boolean get() = readAt == null

    companion object {
        fun from(o: JSONObject) = InboxItem(
            id = o.optString("id"), kind = o.s("kind") ?: "system", title = o.s("title") ?: "Locked In",
            body = o.s("body").orEmpty(), url = o.s("url"), createdAt = o.optString("created_at"), readAt = o.s("read_at"),
        )
    }
}

/** One row of `bandlog.body_measurements` (cm, and body-fat %). */
data class BodyMeasurement(
    val id: String?,
    val date: String,
    val values: Map<String, Double>,
    val note: String = "",
    val createdAt: String? = null,
) {
    fun get(key: String): Double? = values[key]

    companion object {
        /** Column, label, unit — in the order the entry sheet shows them. */
        val FIELDS: List<Triple<String, String, String>> = listOf(
            Triple("waist_cm", "Waist", "cm"), Triple("chest_cm", "Chest", "cm"), Triple("hips_cm", "Hips", "cm"),
            Triple("neck_cm", "Neck", "cm"), Triple("arm_cm", "Arm", "cm"), Triple("thigh_cm", "Thigh", "cm"),
            Triple("calf_cm", "Calf", "cm"), Triple("body_fat_pct", "Body fat", "%"),
        )

        fun from(o: JSONObject) = BodyMeasurement(
            id = o.s("id"), date = o.optString("date").take(10),
            values = FIELDS.mapNotNull { (k, _, _) -> o.d(k)?.let { k to it } }.toMap(),
            note = o.s("note").orEmpty(), createdAt = o.s("created_at"),
        )

        /** Valid range per field (the table's body_fat check is 2–70). */
        fun range(key: String): ClosedFloatingPointRange<Double> = when (key) {
            "body_fat_pct" -> 2.0..70.0
            "neck_cm" -> 15.0..80.0
            "arm_cm", "calf_cm" -> 10.0..90.0
            else -> 20.0..250.0
        }
    }
}

/** `progress_photos` with the v36 extras (weight_kg, pose, updated_at). */
data class PhotoV2(
    val id: String,
    val date: String,
    val path: String,
    val note: String,
    val weightKg: Double? = null,
    val pose: String? = null,
    val updatedAt: String? = null,
) {
    companion object {
        val POSES = listOf("front", "side", "back", "other")
        fun poseLabel(p: String?): String = when (p) { "front" -> "Front"; "side" -> "Side"; "back" -> "Back"; "other" -> "Other"; else -> "No pose" }

        fun from(o: JSONObject) = PhotoV2(
            id = o.optString("id"), date = o.optString("date").take(10), path = o.optString("path"), note = o.s("note").orEmpty(),
            weightKg = o.d("weight_kg"), pose = o.s("pose"), updatedAt = o.s("updated_at"),
        )
    }
}
