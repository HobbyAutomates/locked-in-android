package com.sohum.bandlog.util

import android.content.Context
import org.json.JSONObject

/**
 * The five meal reminders. The source of truth is `profiles.reminders`, mirrored into
 * SharedPreferences so the BroadcastReceiver can re-arm alarms without a network call.
 */
object Reminders {

    private const val FILE = "bandlog_reminders"

    data class Slot(val key: String, val label: String, val defaultTime: String, val prompt: String, val code: Int, val defaultOn: Boolean = false)

    val SLOTS = listOf(
        Slot("breakfast", "Breakfast", "08:30", "log your breakfast?", 9101),
        Slot("lunch", "Lunch", "12:00", "log your lunch?", 9102),
        Slot("snack", "Snack", "15:00", "log your snack?", 9103),
        Slot("dinner", "Dinner", "19:00", "log your dinner?", 9104),
        Slot("endofday", "End of day", "21:00", "anything left to log today?", 9105),
        // v2.0: the 9 pm daily wrap — protein, calories, sessions, tomorrow's session. On by default.
        Slot(WRAP, "Daily wrap", "21:00", "your day, wrapped", 9106, defaultOn = true),
    )

    const val WRAP = "wrap"

    fun slot(key: String): Slot? = SLOTS.firstOrNull { it.key == key }

    data class Pref(val on: Boolean, val time: String) {
        val hour: Int get() = time.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute: Int get() = time.substringAfter(':', "0").toIntOrNull()?.coerceIn(0, 59) ?: 0
    }

    fun defaults(): Map<String, Pref> = SLOTS.associate { it.key to Pref(it.defaultOn, it.defaultTime) }

    fun parse(json: String): Map<String, Pref> {
        if (json.isBlank()) return defaults()
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return defaults()
        return SLOTS.associate { s ->
            val row = o.optJSONObject(s.key)
            s.key to Pref(if (row != null && row.has("on")) row.optBoolean("on", s.defaultOn) else s.defaultOn, row?.optString("time")?.ifBlank { null } ?: s.defaultTime)
        }
    }

    fun toJson(map: Map<String, Pref>): String {
        val o = JSONObject()
        SLOTS.forEach { s ->
            val p = map[s.key] ?: Pref(s.defaultOn, s.defaultTime)
            o.put(s.key, JSONObject().put("on", p.on).put("time", p.time))
        }
        return o.toString()
    }

    // ---- local mirror ----

    fun load(context: Context): Map<String, Pref> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return SLOTS.associate { s ->
            s.key to Pref(prefs.getBoolean("${s.key}_on", s.defaultOn), prefs.getString("${s.key}_time", s.defaultTime) ?: s.defaultTime)
        }
    }

    fun save(context: Context, map: Map<String, Pref>) {
        val e = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        SLOTS.forEach { s ->
            val p = map[s.key] ?: Pref(s.defaultOn, s.defaultTime)
            e.putBoolean("${s.key}_on", p.on).putString("${s.key}_time", p.time)
        }
        e.apply()
    }

    fun fmt(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** "8:30 AM" for the row's trailing label. */
    fun pretty(time: String): String = runCatching {
        val h = time.substringBefore(':').toInt()
        val m = time.substringAfter(':').toInt()
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        "%d:%02d %s".format(h12, m, ampm)
    }.getOrDefault(time)
}
