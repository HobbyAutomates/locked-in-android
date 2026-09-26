package com.sohum.bandlog.util

import android.content.Context

/**
 * v2.13 platform: device-local state. The notification ids already posted (so the inbox check
 * never posts one twice), the protein-nudge settings mirror (and the only copy while schema_v36
 * isn't applied), the last day a local nudge fired, and which recaps were already offered.
 */
object PlatformPrefs {
    private const val FILE = "platform_v213"
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- inbox: ids already shown as a system notification ----

    fun shownIds(c: Context): Set<String> = prefs(c).getStringSet("shown_ids", emptySet()).orEmpty()

    /** Keeps the last 200 ids (older rows fall out of the 3-day window anyway). */
    fun markShown(c: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = (shownIds(c).toList() + ids).distinct().takeLast(200).toSet()
        prefs(c).edit().putStringSet("shown_ids", next).apply()
    }

    // ---- protein nudge ----

    data class Nudge(val on: Boolean, val time: String)

    fun proteinNudge(c: Context): Nudge = prefs(c).let { Nudge(it.getBoolean("pn_on", true), it.getString("pn_time", ProteinNudge.DEFAULT_TIME) ?: ProteinNudge.DEFAULT_TIME) }

    fun setProteinNudge(c: Context, n: Nudge) { prefs(c).edit().putBoolean("pn_on", n.on).putString("pn_time", n.time).apply() }

    /** The India date a local notice of [kind] (protein | fasting) last fired, to keep it to one a day. */
    fun firedOn(c: Context, kind: String): String? = prefs(c).getString("fired_$kind", null)
    fun markFired(c: Context, kind: String, date: String) { prefs(c).edit().putString("fired_$kind", date).apply() }

    // ---- extra facts the background check needs without a network round trip ----

    fun age(c: Context): Int? = prefs(c).getInt("age", -1).takeIf { it >= 0 }
    fun setProfileFacts(c: Context, age: Int?, goalType: String, dietMode: String?) {
        prefs(c).edit().putInt("age", age ?: -1).putString("goal", goalType).putString("diet", dietMode).apply()
    }
    fun goalType(c: Context): String = prefs(c).getString("goal", "maintain") ?: "maintain"
    fun dietMode(c: Context): String? = prefs(c).getString("diet", null)

    // ---- recaps + the notification permission ask ----

    fun recapSeen(c: Context, key: String): Boolean = prefs(c).getBoolean("recap_$key", false)
    fun markRecapSeen(c: Context, key: String) { prefs(c).edit().putBoolean("recap_$key", true).apply() }

    fun askedNotifications(c: Context): Boolean = prefs(c).getBoolean("asked_post_notifications", false)
    fun markAskedNotifications(c: Context) { prefs(c).edit().putBoolean("asked_post_notifications", true).apply() }
}
