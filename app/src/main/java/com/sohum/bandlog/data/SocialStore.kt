package com.sohum.bandlog.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sohum.bandlog.util.Freezes
import com.sohum.bandlog.util.I18n
import com.sohum.bandlog.util.Referrals
import com.sohum.bandlog.util.Safety
import org.json.JSONArray

/**
 * v2.18 device state for the social stream, readable from anywhere (Home's streak, the tab bar,
 * the squad feed): frozen days, the UI language, the block list fallback, the pending invite code
 * and the live-share opt-in. Everything is mirrored in SharedPreferences so it survives restarts
 * and works while schema_v44 / v45 aren't applied.
 */
object SocialStore {
    private const val FILE = "social_v218"
    private var prefs: SharedPreferences? = null

    /** Days a streak freeze covered (freeze_events 'use'); added to the day-streak inputs. */
    var frozenDays by mutableStateOf<List<String>>(emptyList()); private set
    var freezeTokens by mutableIntStateOf(0); private set
    /** en | hinglish | hi. */
    var lang by mutableStateOf("en"); private set
    var blocked by mutableStateOf<Set<String>>(emptySet()); private set
    /** D7 opt-in, OFF by default. */
    var liveShare by mutableStateOf(false); private set
    /** D11 badge skin: classic | obsidian | rose (device copy of profiles.badge_skin). */
    var badgeSkin by mutableStateOf("classic"); private set
    /** D11 unlocked pack ids (device copy of pack_unlocks). */
    var unlocked by mutableStateOf<Set<String>>(emptySet()); private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        lang = I18n.parseLang(p.getString(I18n.LANG_STORAGE_KEY, "en"))
        frozenDays = list(p.getString("frozen", null))
        freezeTokens = Freezes.clampTokens(p.getInt("tokens", 0))
        blocked = list(p.getString(Safety.BLOCKS_KEY, null)).toSet()
        liveShare = p.getBoolean(com.sohum.bandlog.util.LiveSquad.LIVE_PREF_KEY, false)
        badgeSkin = com.sohum.bandlog.util.Packs.parseSkin(p.getString(com.sohum.bandlog.util.Packs.SKIN_KEY, null))
        unlocked = list(p.getString("packs", null)).toSet()
    }

    private fun list(s: String?): List<String> = runCatching { JSONArray(s ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    fun t(key: String, vars: Map<String, Any> = emptyMap()) = I18n.t(key, lang, vars)

    // ---- freezes ----

    fun setFreezes(tokens: Int, frozen: List<String>) {
        freezeTokens = Freezes.clampTokens(tokens)
        frozenDays = frozen.distinct().sortedDescending()
        prefs?.edit()?.putInt("tokens", freezeTokens)?.putString("frozen", JSONArray(frozenDays).toString())?.apply()
    }

    /** The day freeze_sync last ran on this device (once a day is plenty). */
    var lastFreezeSync: String?
        get() = prefs?.getString(Freezes.FREEZE_SYNC_KEY, null)
        set(v) { prefs?.edit()?.putString(Freezes.FREEZE_SYNC_KEY, v)?.apply() }

    // ---- language ----

    fun setLanguage(l: String) {
        lang = I18n.parseLang(l)
        prefs?.edit()?.putString(I18n.LANG_STORAGE_KEY, lang)?.apply()
    }

    /** Bottom-bar label for an English tab name. */
    fun navLabel(english: String): String = when (english) {
        "Home" -> t("nav.home"); "Squad" -> t("nav.squad"); "Scan" -> t("nav.scan"); "Progress" -> t("nav.progress"); "Profile" -> t("nav.profile")
        else -> english
    }

    /** Speed-dial label for the English one. */
    fun dialLabel(english: String): String = when (english) {
        "Food" -> t("dial.food"); "Activity" -> t("dial.activity"); "Water +1 glass" -> t("dial.water"); "Scan" -> t("nav.scan")
        else -> english
    }

    // ---- blocks (device fallback + cache) ----

    fun updateBlocked(ids: Set<String>) {
        blocked = ids
        prefs?.edit()?.putString(Safety.BLOCKS_KEY, JSONArray(ids.toList()).toString())?.apply()
    }

    // ---- referral code from an invite link ----

    var pendingReferral: String?
        get() = prefs?.getString(Referrals.REF_STORAGE_KEY, null)
        set(v) { prefs?.edit()?.apply { if (v == null) remove(Referrals.REF_STORAGE_KEY) else putString(Referrals.REF_STORAGE_KEY, v) }?.apply() }

    // ---- live share ----

    fun setLive(on: Boolean) {
        liveShare = on
        prefs?.edit()?.putBoolean(com.sohum.bandlog.util.LiveSquad.LIVE_PREF_KEY, on)?.apply()
    }

    // ---- packs ----

    fun setSkin(skin: String) {
        badgeSkin = com.sohum.bandlog.util.Packs.parseSkin(skin)
        prefs?.edit()?.putString(com.sohum.bandlog.util.Packs.SKIN_KEY, badgeSkin)?.apply()
    }

    fun updateUnlocked(ids: Set<String>) {
        unlocked = ids
        prefs?.edit()?.putString("packs", JSONArray(ids.toList()).toString())?.apply()
    }

    // ---- misc device flags ----

    fun flag(key: String): Boolean = prefs?.getBoolean(key, false) == true
    fun setFlag(key: String, on: Boolean) { prefs?.edit()?.putBoolean(key, on)?.apply() }
    fun string(key: String): String? = prefs?.getString(key, null)
    fun setString(key: String, v: String?) { prefs?.edit()?.apply { if (v == null) remove(key) else putString(key, v) }?.apply() }
}
