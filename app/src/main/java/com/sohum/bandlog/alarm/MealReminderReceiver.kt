package com.sohum.bandlog.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.notify.Notifications
import com.sohum.bandlog.util.BurnedCache
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Reminders
import com.sohum.bandlog.util.Wrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Posts one meal nudge (or the 9 pm daily wrap), then re-arms the same slot for tomorrow. */
class MealReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val slot = Reminders.slot(key) ?: return
        val pref = Reminders.load(app)[key]
        if (pref?.on != true) return
        // Re-arm for the next day first; AlarmManager has no dependable exact repeat.
        MealAlarms.schedule(app, key, pref.hour, pref.minute)
        if (key != Reminders.WRAP) {
            runCatching { Notifications.notifyMeal(app, slot.code, "Locked In — ${slot.prompt}") }
            return
        }
        // The wrap needs today's numbers: read them fresh (a few seconds at most), then post.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val line = withTimeoutOrNull(8_000) { runCatching { wrapLine(app) }.getOrNull() }
                runCatching { Notifications.notifyWrap(app, slot.code, line ?: "Your day, wrapped — tap to see protein, calories and tomorrow's session.") }
            } finally {
                pending.finish()
            }
        }
    }

    /** Same sums as the Home card: profile targets, today's meals, this week's sessions, burn deduped as Home does. */
    private suspend fun wrapLine(app: Context): String? {
        Session.init(app)
        if (!Session.signedIn) return null
        val t = Dates.today()
        val from = Dates.addDays(t, -60)
        val date = Wrap.wrapDate()
        return kotlinx.coroutines.coroutineScope {
            val p = async { Api.profile() }
            val w = async { Api.workouts(from, t) }
            val m = async { Api.meals(Dates.addDays(t, -1), t) }
            val x = async { runCatching { Api.exercises(Dates.addDays(t, -1), t) }.getOrDefault(emptyList()) }
            val health = Wrap.healthConnected(app)
            val exercise = x.await().filter { it.date == date && !(health && it.source == "workout") }.sumOf { it.kcal }
            val burned = exercise + (if (health) BurnedCache.get(app, date) else 0.0)
            Wrap.compute(p.await(), w.await(), m.await(), burned, date).line
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sohum.bandlog.ACTION_MEAL_REMINDER"
        const val EXTRA_KEY = "slotKey"
    }
}
