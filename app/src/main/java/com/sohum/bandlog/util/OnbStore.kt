package com.sohum.bandlog.util

import android.content.Context
import android.content.SharedPreferences
import com.sohum.bandlog.data.MealItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * v2.14 onboarding, kept on the device until the account exists (the web keeps the same thing in
 * localStorage `li_onb_v2`): the answers, the pre-account first log, where the flow was, what to
 * do once signed in (invite a buddy, join a squad), and whether a finished flow still has to be
 * sent to /api/onboarding/finish ([pending]). Plain SharedPreferences like the rest of the app.
 */
object OnbStore {
    private const val FILE = "li_onb_v2"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private val ready: Boolean get() = ::prefs.isInitialized

    /** A random id for the signed-out parse preview's per-device rate limit (X-Device-Id). */
    fun deviceId(): String {
        if (!ready) return "none"
        prefs.getString("device_id", null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    var answers: OnboardingV2.Answers
        get() = if (!ready) OnboardingV2.Answers() else prefs.getString("answers", null)?.let { runCatching { OnboardingV2.Answers.from(JSONObject(it)) }.getOrNull() } ?: OnboardingV2.Answers()
        set(v) { if (ready) prefs.edit().putString("answers", v.toJson().toString()).apply() }

    /** The first meal logged before the account existed. */
    data class FirstLog(val text: String, val mealType: String, val date: String, val items: List<MealItem>) {
        fun toJson(): JSONObject = JSONObject().put("text", text).put("meal_type", mealType).put("date", date)
            .put("items", JSONArray().apply { items.forEach { put(itemJson(it)) } })

        companion object {
            fun itemJson(i: MealItem): JSONObject = i.toJson("", "").apply { remove("meal_id"); remove("user_id") }
            fun from(o: JSONObject): FirstLog? {
                val arr = o.optJSONArray("items") ?: return null
                val items = (0 until arr.length()).map { MealItem.from(arr.getJSONObject(it)) }.filter { it.name.isNotBlank() }
                if (items.isEmpty()) return null
                return FirstLog(o.optString("text"), o.optString("meal_type", MealTypes.default()), o.optString("date", Dates.today()), items)
            }
        }
    }

    var firstLog: FirstLog?
        get() = if (!ready) null else prefs.getString("first_log", null)?.let { runCatching { FirstLog.from(JSONObject(it)) }.getOrNull() }
        set(v) { if (ready) prefs.edit().apply { if (v == null) remove("first_log") else putString("first_log", v.toJson().toString()) }.apply() }

    /** Where the flow was (so a killed app resumes on the same screen). */
    var step: Int
        get() = if (!ready) 0 else prefs.getInt("step", 0)
        set(v) { if (ready) prefs.edit().putInt("step", v).apply() }

    /** "Invite a buddy" was tapped before the account existed: share the link once signed in. */
    var buddyIntent: Boolean
        get() = ready && prefs.getBoolean("buddy_intent", false)
        set(v) { if (ready) prefs.edit().putBoolean("buddy_intent", v).apply() }

    /** A squad code typed on the buddy screen, joined once signed in. */
    var squadCode: String?
        get() = if (!ready) null else prefs.getString("squad_code", null)
        set(v) { if (ready) prefs.edit().apply { if (v == null) remove("squad_code") else putString("squad_code", v) }.apply() }

    /** The flow reached the end (pledge) and still has to be saved to the account. */
    var pending: Boolean
        get() = ready && prefs.getBoolean("pending", false)
        set(v) { if (ready) prefs.edit().putBoolean("pending", v).apply() }

    /** The email a sign-up is waiting on ("confirm your email"), shown on the sign-in form. */
    var pendingEmail: String?
        get() = if (!ready) null else prefs.getString("pending_email", null)
        set(v) { if (ready) prefs.edit().apply { if (v == null) remove("pending_email") else putString("pending_email", v) }.apply() }

    /** Local "the new onboarding is done" (covers a database without profiles.onboarded_v2). */
    var doneLocally: Boolean
        get() = ready && prefs.getBoolean("done", false)
        set(v) { if (ready) prefs.edit().putBoolean("done", v).apply() }

    /** "Tune your plan" was dismissed or finished on this device. */
    var tuneDismissed: Boolean
        get() = ready && prefs.getBoolean("tune_dismissed", false)
        set(v) { if (ready) prefs.edit().putBoolean("tune_dismissed", v).apply() }

    /** Clears the flow once it has been saved (keeps the device id and the local flags). */
    fun clearFlow() {
        if (!ready) return
        prefs.edit().remove("answers").remove("first_log").remove("step").remove("pending").remove("pending_email").apply()
    }
}
