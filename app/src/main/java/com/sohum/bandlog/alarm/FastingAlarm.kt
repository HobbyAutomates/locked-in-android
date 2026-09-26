package com.sohum.bandlog.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.R
import com.sohum.bandlog.util.Fasting

/**
 * v2.13 §7: the running fast mirrored on the phone (so Home's card and the alarm work offline) and
 * a one-shot AlarmManager "goal reached" notification — the same exact-alarm pattern as MealAlarms.
 */
object FastingAlarm {
    private const val PREFS = "fasting_local"
    private const val REQ = 9301
    const val NOTIFICATION_ID = 9302
    const val CHANNEL_ID = "fasting"
    const val OPEN_FASTING = "fasting"

    data class Local(val id: String, val startedAtMs: Long, val targetHours: Double) {
        val goalAtMs: Long get() = startedAtMs + (targetHours * 3_600_000).toLong()
    }

    fun load(context: Context): Local? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = p.getLong("started", 0L).takeIf { it > 0 } ?: return null
        return Local(p.getString("id", "").orEmpty(), start, p.getFloat("hours", Fasting.DEFAULT_HOURS.toFloat()).toDouble())
    }

    fun save(context: Context, l: Local?) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (l == null) e.clear() else e.putString("id", l.id).putLong("started", l.startedAtMs).putFloat("hours", l.targetHours.toFloat())
        e.apply()
    }

    /** Arms (or re-arms) the goal notification for [l]; clears it when [l] is null or already past. */
    fun schedule(context: Context, l: Local?) = runCatching {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        am.cancel(pi)
        if (l == null || l.goalAtMs <= System.currentTimeMillis()) return@runCatching
        val exact = am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, l.goalAtMs, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, l.goalAtMs, pi)
    }.isSuccess

    /** After a reboot (BootReceiver) and on app open. */
    fun rearm(context: Context) { schedule(context, load(context)) }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ, Intent(context, FastingReceiver::class.java).setAction(FastingReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel(context: Context) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Fasting timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when your fast reaches its goal"
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyReached(context: Context, l: Local) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, OPEN_FASTING)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = "You reached your ${Fasting.label(l.targetHours)} goal. End the fast whenever you're ready, and eat something nourishing."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fast goal reached")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
        // v2.13 merge: tell the inbox check this phone already showed today's fasting notice (no duplicate).
        runCatching { com.sohum.bandlog.util.PlatformPrefs.markFired(context, "fasting", com.sohum.bandlog.util.Dates.today()) }
    }
}

/** Fires once when the running fast hits its target. */
class FastingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val l = FastingAlarm.load(context.applicationContext) ?: return
        // A stale alarm (the fast was ended or restarted) stays quiet.
        if (System.currentTimeMillis() + 60_000 < l.goalAtMs) return
        FastingAlarm.notifyReached(context.applicationContext, l)
    }

    companion object { const val ACTION = "com.sohum.bandlog.ACTION_FASTING_GOAL" }
}
