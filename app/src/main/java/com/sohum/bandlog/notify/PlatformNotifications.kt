package com.sohum.bandlog.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.R
import com.sohum.bandlog.data.InboxItem
import com.sohum.bandlog.ui.platform.PlatformNav

/**
 * v2.13 platform notifications: the inbox (squad nudges and other server notices), the local
 * protein nudge, and the live-workout rest timer (an ongoing countdown, then a "rest's up" ping).
 */
object PlatformNotifications {
    const val INBOX_CHANNEL = "squad_inbox"
    const val PROTEIN_CHANNEL = "protein_nudge"
    const val REST_CHANNEL = "rest_timer"
    const val REST_DONE_CHANNEL = "rest_timer_done"

    const val PROTEIN_ID = 9301
    const val REST_ID = 9302

    fun ensureChannels(context: Context) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun make(id: String, name: String, importance: Int, desc: String, silent: Boolean = false) {
            if (nm.getNotificationChannel(id) != null) return
            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = desc
                    if (silent) { setSound(null, null); enableVibration(false) }
                },
            )
        }
        make(INBOX_CHANNEL, "Squad & inbox", NotificationManager.IMPORTANCE_DEFAULT, "Nudges from your squad and other notices")
        make(PROTEIN_CHANNEL, "Protein nudge", NotificationManager.IMPORTANCE_DEFAULT, "An afternoon heads-up when you're well short on protein")
        make(REST_CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_LOW, "The countdown between sets during a live workout", silent = true)
        make(REST_DONE_CHANNEL, "Rest timer done", NotificationManager.IMPORTANCE_HIGH, "Buzzes when your rest between sets is over")
    }.isSuccess

    /** API 33+ needs the runtime permission; below that notifications are on unless blocked in Settings. */
    fun permitted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openIntent(context: Context, code: Int, open: String, notificationId: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, open)
            if (notificationId != null) putExtra(PlatformNav.EXTRA_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Where tapping an inbox row goes: nudges (and anything pointing at /squad) open Squad, the rest the inbox. */
    fun targetFor(item: InboxItem): String = when {
        item.kind == "nudge" || item.url?.startsWith("/squad") == true -> PlatformNav.OPEN_SQUAD
        item.kind == "protein" -> PlatformNav.OPEN_MEAL
        else -> PlatformNav.OPEN_INBOX
    }

    @SuppressLint("MissingPermission")
    fun postInbox(context: Context, item: InboxItem) {
        ensureChannels(context)
        val code = 9400 + (item.id.hashCode() and 0x3FF)
        val n = NotificationCompat.Builder(context, INBOX_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(item.body.ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setCategory(if (item.kind == "nudge") NotificationCompat.CATEGORY_SOCIAL else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, code, targetFor(item), item.id))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(code, n) }
    }

    @SuppressLint("MissingPermission")
    fun postProtein(context: Context, title: String, body: String) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, PROTEIN_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, PROTEIN_ID, PlatformNav.OPEN_MEAL))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(PROTEIN_ID, n) }
    }

    /** The ongoing rest countdown (a system chronometer counting down to [endAtMs]). */
    @SuppressLint("MissingPermission")
    fun postRest(context: Context, exercise: String, next: String, endAtMs: Long) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, REST_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest · $exercise")
            .setContentText(next)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(endAtMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setTimeoutAfter((endAtMs - System.currentTimeMillis()).coerceAtLeast(1_000L) + 60_000L)
            .setContentIntent(openIntent(context, REST_ID, PlatformNav.OPEN_WORKOUT))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REST_ID, n) }
    }

    /** Rest's over: replaces the countdown with a buzzing heads-up that clears itself. */
    @SuppressLint("MissingPermission")
    fun postRestDone(context: Context, next: String) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, REST_DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest's up")
            .setContentText(next)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .setAutoCancel(true)
            .setTimeoutAfter(30_000L)
            .setContentIntent(openIntent(context, REST_ID, PlatformNav.OPEN_WORKOUT))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REST_ID, n) }
    }

    fun cancelRest(context: Context) { runCatching { NotificationManagerCompat.from(context).cancel(REST_ID) } }
}
