package com.sohum.bandlog.util

/**
 * v2.18 E2 UI language: English, Hinglish (Roman) and Hindi (Devanagari). Port of the web's
 * src/lib/social/i18n.ts (same keys, same strings). Stored on the device (SharedPreferences
 * [LANG_STORAGE_KEY]) and mirrored to profiles.ui_lang (schema_v45) when the column exists.
 *
 * Coverage in v2.18: the bottom bar, the offline chip, Preferences → Language and every v2.18
 * screen. Older screens stay English until they're moved onto [t].
 */
object I18n {
    data class Lang(val key: String, val label: String, val native: String)

    val LANGS = listOf(Lang("en", "English", "English"), Lang("hinglish", "Hinglish", "Hinglish"), Lang("hi", "Hindi", "हिन्दी"))
    const val LANG_STORAGE_KEY = "li-lang"

    fun parseLang(v: Any?): String = if (v == "hinglish" || v == "hi") v as String else "en"

    val STRINGS: Map<String, List<String>> = linkedMapOf(
        "nav.home" to listOf("Home", "Home", "होम"),
        "nav.squad" to listOf("Squad", "Squad", "स्क्वॉड"),
        "nav.scan" to listOf("Scan", "Scan", "स्कैन"),
        "nav.progress" to listOf("Progress", "Progress", "प्रगति"),
        "nav.profile" to listOf("Profile", "Profile", "प्रोफ़ाइल"),
        "dial.food" to listOf("Food", "Khana", "खाना"),
        "dial.food.sub" to listOf("type or talk", "likho ya bolo", "लिखें या बोलें"),
        "dial.activity" to listOf("Activity", "Activity", "एक्टिविटी"),
        "dial.water" to listOf("Water +1 glass", "Paani +1 glass", "पानी +1 गिलास"),
        "sync.waiting" to listOf("{n} waiting to sync", "{n} sync hone baaki", "{n} सिंक होना बाकी"),
        "sync.waiting.one" to listOf("1 waiting to sync", "1 sync hona baaki", "1 सिंक होना बाकी"),
        "sync.offline" to listOf("You're offline. Logs are saved on this phone.", "Offline ho. Logs phone pe save hain.", "आप ऑफ़लाइन हैं। लॉग इसी फ़ोन में सेव हैं।"),
        "sync.syncing" to listOf("Syncing…", "Sync ho raha hai…", "सिंक हो रहा है…"),
        "sync.done" to listOf("All synced", "Sab sync ho gaya", "सब सिंक हो गया"),
        "sync.queued" to listOf("Saved offline. It'll sync when you're back online.", "Offline save hua. Net aate hi sync hoga.", "ऑफ़लाइन सेव हुआ। इंटरनेट आते ही सिंक होगा।"),
        "social.title" to listOf("Social and rewards", "Social aur rewards", "सोशल और इनाम"),
        "social.sub" to listOf("Freezes, invites, wrapped, pledges and more", "Freeze, invite, wrapped, pledge aur bahut kuch", "फ़्रीज़, इनवाइट, रैप्ड, संकल्प और भी"),
        "streak.title" to listOf("Streak freezes", "Streak freeze", "स्ट्रीक फ़्रीज़"),
        "streak.sub" to listOf("Miss a day without losing the streak", "Ek din miss, streak safe", "एक दिन छूटे, स्ट्रीक बची रहे"),
        "invite.title" to listOf("Invite friends", "Dost ko bulao", "दोस्तों को बुलाएँ"),
        "invite.sub" to listOf("You both get a week of Pro", "Dono ko ek hafte ka Pro", "दोनों को एक हफ़्ते का Pro"),
        "wrapped.title" to listOf("Wrapped", "Wrapped", "रैप्ड"),
        "wrapped.sub" to listOf("Your week, month and year as story cards", "Hafta, mahina, saal: story cards mein", "आपका हफ़्ता, महीना और साल, स्टोरी कार्ड में"),
        "pledges.title" to listOf("Pledges", "Pledge", "संकल्प"),
        "pledges.sub" to listOf("Put something on the line", "Kuch daav pe lagao", "कुछ दांव पर लगाएँ"),
        "events.title" to listOf("Seasonal events", "Seasonal events", "मौसमी इवेंट"),
        "events.sub" to listOf("Limited-edition jewellery", "Limited edition jewellery", "सीमित संस्करण के बैज"),
        "coach.title" to listOf("Coach access", "Coach access", "कोच एक्सेस"),
        "coach.sub" to listOf("Let a trainer or dietitian see your logs", "Trainer ya dietitian ko logs dikhao", "ट्रेनर या डाइटिशियन को लॉग दिखाएँ"),
        "clients.title" to listOf("My clients", "Mere clients", "मेरे क्लाइंट"),
        "story.title" to listOf("Transformation story", "Transformation story", "बदलाव की कहानी"),
        "story.sub" to listOf("Before and after, as a short reel", "Pehle aur baad, ek chhoti reel", "पहले और बाद, एक छोटी रील"),
        "packs.title" to listOf("Covers and badge packs", "Covers aur badge packs", "कवर और बैज पैक"),
        "packs.sub" to listOf("Free during the beta", "Beta mein free", "बीटा में मुफ़्त"),
        "leagues.title" to listOf("Squad leagues", "Squad leagues", "स्क्वॉड लीग"),
        "export.title" to listOf("Export my data", "Mera data export karo", "मेरा डेटा एक्सपोर्ट करें"),
        "export.sub" to listOf("CSV or a printable PDF", "CSV ya print hone wala PDF", "CSV या प्रिंट होने वाला PDF"),
        "delete.title" to listOf("Delete account", "Account delete karo", "अकाउंट डिलीट करें"),
        "delete.sub" to listOf("Wipes your data for good", "Aapka data hamesha ke liye mit jayega", "आपका डेटा हमेशा के लिए मिट जाएगा"),
        "lang.title" to listOf("Language", "Bhasha", "भाषा"),
        "lang.sub" to listOf("App text. Food search also knows Marathi, Tamil and Bengali names.", "App ka text. Food search Marathi, Tamil aur Bangla naam bhi samajhta hai.", "ऐप का टेक्स्ट। खाने की खोज मराठी, तमिल और बांग्ला नाम भी समझती है।"),
        "common.soon" to listOf("Coming with the next update", "Agle update mein aa raha hai", "अगले अपडेट में आ रहा है"),
        "common.share" to listOf("Share", "Share karo", "शेयर करें"),
        "common.save" to listOf("Save", "Save karo", "सेव करें"),
        "common.cancel" to listOf("Cancel", "Cancel", "रद्द करें"),
        "squad.report" to listOf("Report", "Report karo", "रिपोर्ट करें"),
        "squad.block" to listOf("Block", "Block karo", "ब्लॉक करें"),
        "squad.unblock" to listOf("Unblock", "Unblock karo", "अनब्लॉक करें"),
        "squad.live" to listOf("{name} is training now 🔥", "{name} abhi train kar raha hai 🔥", "{name} अभी ट्रेनिंग कर रहे हैं 🔥"),
        "squad.cheer" to listOf("Cheer", "Cheer karo", "हौसला दें"),
        "squad.join" to listOf("Join", "Join karo", "जुड़ें"),
    )

    private fun idx(lang: String) = when (parseLang(lang)) { "hinglish" -> 1; "hi" -> 2; else -> 0 }

    /** t("sync.waiting", "hi", mapOf("n" to 3)) → "3 सिंक होना बाकी". Unknown keys return the key. */
    fun t(key: String, lang: String = "en", vars: Map<String, Any> = emptyMap()): String {
        val e = STRINGS[key]
        var s = if (e != null) e[idx(lang)].ifEmpty { e[0] } else key
        for ((k, v) in vars) s = s.replace("{$k}", v.toString())
        return s
    }

    /** The sync chip text for n queued logs (null for none). */
    fun syncLabel(n: Int, lang: String = "en"): String? {
        if (n <= 0) return null
        return if (n == 1) t("sync.waiting.one", lang) else t("sync.waiting", lang, mapOf("n" to n))
    }
}
