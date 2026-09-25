package com.sohum.bandlog.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.10 on-device log of calorie-target and goal-weight edits, read by the "repeated lowering"
 * safety flag ([Goals.downwardEdits]). SharedPreferences only — the database keeps no edit history —
 * so it is per device, like the web app's localStorage copy (src/lib/targetEdits.ts).
 */
object TargetEdits {
    private const val PREFS = "lockedin_target_edits"
    private const val KEY = "edits"

    fun read(context: Context): List<Goals.TargetEdit> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o -> Goals.TargetEdit(o.getString("date"), o.getString("field"), o.getDouble("from"), o.getDouble("to")) }
        }
    }.getOrDefault(emptyList())

    /** Remember one change (no-op when nothing moved); keeps the newest 30. */
    fun record(context: Context, field: String, from: Double?, to: Double?): List<Goals.TargetEdit> {
        val list = read(context)
        if (from == null || to == null || from <= 0 || to <= 0 || kotlin.math.abs(from - to) < 1e-9) return list
        val next = (listOf(Goals.TargetEdit(Goals.todayIso(), field, from, to)) + list).take(30)
        val arr = JSONArray()
        next.forEach { arr.put(JSONObject().put("date", it.date).put("field", it.field).put("from", it.from).put("to", it.to)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        return next
    }

    /** v2.10 teen migration card: shown until "Got it" on this device. */
    private const val CARD = "teen_goal_card"
    fun teenCardPending(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CARD, false)
    fun setTeenCard(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CARD, on).apply()
}
