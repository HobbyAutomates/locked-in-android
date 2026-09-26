package com.sohum.bandlog.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sohum.bandlog.notify.PlatformNotifications

/**
 * v2.13 live workout: the end of a rest period as an exact alarm, so the "Rest's up" buzz still
 * comes when the app is in the background. While the workout screen is showing, the screen does
 * the buzz itself and [foreground] keeps this quiet.
 */
object RestTimerAlarm {
    private const val CODE = 9302

    /** True while the live-workout screen is visible (set by the screen). */
    @Volatile var foreground: Boolean = false

    fun schedule(context: Context, endAtMs: Long, next: String) = runCatching {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, next)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pi)
    }.isSuccess

    fun cancel(context: Context) = runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, "")) }.isSuccess

    private fun pending(context: Context, next: String): PendingIntent = PendingIntent.getBroadcast(
        context, CODE, Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_DONE).putExtra(RestTimerReceiver.EXTRA_NEXT, next),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE || RestTimerAlarm.foreground) return
        PlatformNotifications.postRestDone(context.applicationContext, intent.getStringExtra(EXTRA_NEXT).orEmpty().ifBlank { "Time for your next set" })
    }

    companion object {
        const val ACTION_DONE = "com.sohum.bandlog.ACTION_REST_DONE"
        const val EXTRA_NEXT = "next"
    }
}
