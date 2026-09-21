package com.sohum.bandlog.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sohum.bandlog.notify.Notifications
import com.sohum.bandlog.util.Reminders

/** Posts one meal nudge, then re-arms the same slot for tomorrow. */
class MealReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val slot = Reminders.slot(key) ?: return
        val pref = Reminders.load(app)[key]
        if (pref?.on == true) {
            Notifications.notifyMeal(app, slot.code, "Locked In — ${slot.prompt}")
            // Re-arm for the next day; AlarmManager has no dependable exact repeat.
            MealAlarms.schedule(app, key, pref.hour, pref.minute)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sohum.bandlog.ACTION_MEAL_REMINDER"
        const val EXTRA_KEY = "slotKey"
    }
}
