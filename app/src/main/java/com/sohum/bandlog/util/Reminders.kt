package com.sohum.bandlog.util

import android.content.Context
import org.json.JSONObject

/**
 * The five meal reminders. The source of truth is `profiles.reminders`, mirrored into
 * SharedPreferences so the BroadcastReceiver can re-arm alarms without a network call.
 */
object Reminders {

    private const val FILE = "bandlog_reminders"

    data class Slot(val key: String, val label: String, val defaultTime: String, val prompt: String, val code: Int)

    val SLOTS = listOf(
        Slot("breakfast", "Breakfast", "08:30", "log your breakfast?", 9101),
        Slot("lunch", "Lunch", "12:00", "log your lunch?", 9102),
        Slot("snack", "Snack", "15:00", "log your snack?", 9103),
        Slot("dinner", "Dinner", "19:00", "log your dinner?", 9104),
        Slot("endofday", "End of day", "21:00", "anything left to log today?", 9105),
    )

    fun slot(key: String): Slot? = SLOTS.firstOrNull { it.key == key }

    data class Pref(val on: Boolean, val time: String) {
        val hour: Int get() = time.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute: Int get() = time.substringAfter(':', "0").toIntOrNull()?.coerceIn(0, 59) ?: 0
    }

    fun defaults(): Map<String, Pref> = SLOTS.associate { it.key to Pref(false, it.defaultTime) }

    fun parse(json: String): Map<String, Pref> {
        if (json.isBlank()) return defaults()
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return defaults()
        return SLOTS.associate { s ->
            val row = o.optJSONObject(s.key)
            s.key to Pref(row?.optBoolean("on", false) ?: false, row?.optString("time")?.ifBlank { null } ?: s.defaultTime)
        }
    }

    fun toJson(map: Map<String, Pref>): String {
        val o = JSONObject()
        SLOTS.forEach { s ->
            val p = map[s.key] ?: Pref(false, s.defaultTime)
            o.put(s.key, JSONObject().put("on", p.on).put("time", p.time))
        }
        return o.toString()
    }

    // ---- local mirror ----

    fun load(context: Context): Map<String, Pref> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return SLOTS.associate { s ->
            s.key to Pref(prefs.getBoolean("${s.key}_on", false), prefs.getString("${s.key}_time", s.defaultTime) ?: s.defaultTime)
        }
    }

    fun save(context: Context, map: Map<String, Pref>) {
        val e = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        SLOTS.forEach { s ->
            val p = map[s.key] ?: Pref(false, s.defaultTime)
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
