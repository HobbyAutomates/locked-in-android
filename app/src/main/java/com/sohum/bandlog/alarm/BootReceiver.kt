package com.sohum.bandlog.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A reboot or an app update wipes AlarmManager's schedule, so every enabled meal reminder is
 * re-armed from the local mirror here. Also covers wall-clock shifts (TIME_SET / TIMEZONE_CHANGED).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MealAlarms.rescheduleAll(context.applicationContext)
        // v2.6: the water reminder's next slot.
        WaterAlarms.reschedule(context.applicationContext)
        // v2.13 platform: the protein nudge, and the inbox check (WorkManager keeps it, this just makes sure).
        ProteinNudgeAlarms.reschedule(context.applicationContext)
        com.sohum.bandlog.notify.InboxWorker.schedule(context.applicationContext)
        // v2.13 nutrition: a running fast's "goal reached" alarm.
        runCatching { FastingAlarm.rearm(context.applicationContext) }
    }
}
