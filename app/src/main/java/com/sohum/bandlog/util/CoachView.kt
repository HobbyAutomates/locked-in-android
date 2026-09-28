package com.sohum.bandlog.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.18 D4 trainer / dietitian view (web docs/schema_v44.sql coach_links, coach_comments, RPCs
 * coach_grant, coach_revoke, my_coaches, my_clients, client_overview). Pure parsing + helpers.
 * Port of the web's src/lib/social/coachView.ts. Coach tools are "part of a paid plan later".
 */
object CoachView {
    const val COACH_TIER_NOTE = "Coach tools will be part of a paid plan later. Free during the beta."

    /** "@Ayan_K " → "ayan_k"; null when it can't be a username (3-20 of a-z, 0-9 and _). */
    fun normalizeUsername(v: Any?): String? {
        if (v !is String) return null
        val u = v.trim().trimStart('@').lowercase()
        return if (Regex("^[a-z0-9_]{3,20}$").matches(u)) u else null
    }

    data class ClientRow(val clientId: String, val name: String, val username: String?, val avatarPath: String?, val since: String, val lastActive: String?, val streak: Int, val weightKg: Double?)
    data class CoachRow(val coachId: String, val name: String, val username: String?, val avatarPath: String?, val since: String)

    private fun JSONObject.s(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
    private fun JSONObject.num(k: String): Double? {
        if (!has(k) || isNull(k)) return null
        val v = opt(k)
        return when (v) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull(); else -> null }?.takeIf { it.isFinite() }
    }

    fun parseClient(o: JSONObject) = ClientRow(
        o.s("client_id").orEmpty(), o.s("name") ?: "Client", o.s("username"), o.s("avatar_path"), o.s("since").orEmpty(), o.s("last_active"),
        (o.num("streak") ?: 0.0).toInt().coerceAtLeast(0), o.num("weight_kg"),
    )

    fun parseCoach(o: JSONObject) = CoachRow(o.s("coach_id").orEmpty(), o.s("name") ?: "Coach", o.s("username"), o.s("avatar_path"), o.s("since").orEmpty())

    data class Day(val date: String, val calories: Double, val proteinG: Double, val meals: Int, val trained: Boolean, val burned: Double)
    data class MealLine(val date: String, val text: String, val calories: Double, val proteinG: Double)
    data class WeightPoint(val date: String, val kg: Double)
    data class WorkoutLine(val date: String, val kind: String, val minutes: Int?, val muscles: List<String>)
    data class Overview(
        val name: String, val calorieTarget: Double?, val proteinTargetG: Double?, val goalType: String?, val weightKg: Double?, val goalWeightKg: Double?,
        val days: List<Day>, val meals: List<MealLine>, val weights: List<WeightPoint>, val workouts: List<WorkoutLine>,
    )

    fun parseOverview(v: Any?): Overview {
        val o = v as? JSONObject ?: JSONObject()
        val p = o.optJSONObject("profile") ?: JSONObject()
        fun arr(k: String): List<JSONObject> = (o.opt(k) as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
        return Overview(
            name = p.s("name") ?: "Client",
            calorieTarget = p.num("calorie_target"), proteinTargetG = p.num("protein_target_g"), goalType = p.s("goal_type"),
            weightKg = p.num("weight_kg"), goalWeightKg = p.num("goal_weight_kg"),
            days = arr("days").map { d -> Day(d.optString("date"), d.num("calories") ?: 0.0, d.num("protein_g") ?: 0.0, (d.num("meals") ?: 0.0).toInt(), d.opt("trained") == true, d.num("burned") ?: 0.0) },
            meals = arr("meals").map { m -> MealLine(m.optString("date"), m.s("text").orEmpty(), m.num("calories") ?: 0.0, m.num("protein_g") ?: 0.0) },
            weights = arr("weights").map { w -> WeightPoint(w.optString("date"), w.num("kg") ?: 0.0) },
            workouts = arr("workouts").map { w ->
                val mus = w.optJSONArray("muscles")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                WorkoutLine(w.optString("date"), w.s("kind") ?: "gym", w.num("minutes")?.toInt(), mus)
            },
        )
    }

    /** "Logged 6 of 7 days · protein hit 4 · trained 3". */
    fun adherenceLine(o: Overview, today: String, days: Int = 7): String {
        val from = Dates.addDays(today, -(days - 1).toLong())
        val recent = o.days.filter { it.date >= from && it.date <= today }
        val logged = recent.count { it.meals > 0 }
        val trained = recent.count { it.trained }
        val target = o.proteinTargetG ?: 0.0
        val protein = if (target > 0) recent.count { it.proteinG >= target * 0.9 } else null
        return listOfNotNull("Logged $logged of $days days", protein?.let { "protein hit $it" }, "trained $trained").joinToString(" · ")
    }
}
