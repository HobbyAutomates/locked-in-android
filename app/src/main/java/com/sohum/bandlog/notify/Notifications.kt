package com.sohum.bandlog.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.R

/** The meal-reminder notification. Tapping it opens the app straight on Log → Meal. */
object Notifications {

    const val CHANNEL_ID = "meal_reminders"
    private const val CHANNEL_NAME = "Meal reminders"

    fun ensureChannel(context: Context) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Nudges to log breakfast, lunch, snacks and dinner"
                },
            )
        }
    }.isSuccess

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is requested in-app; SecurityException is caught below.
    fun notifyMeal(context: Context, id: Int, prompt: String) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_MEAL)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Locked In")
            .setContentText(prompt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    /** The 9 pm daily wrap. Tapping it opens Home with the Wrap card on top. */
    @SuppressLint("MissingPermission")
    fun notifyWrap(context: Context, id: Int, line: String) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_WRAP)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your day, wrapped")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    // ---- v2.6 water reminders ----

    const val WATER_CHANNEL_ID = "water_reminders"
    const val WATER_NOTIFICATION_ID = 9201

    private fun ensureWaterChannel(context: Context) = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(WATER_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(WATER_CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A nudge to drink a glass inside your reminder window, until the day's goal is hit"
                },
            )
        }
    }

    private fun openWater(context: Context): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_WATER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, WATER_NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Logs one glass without opening the app (the reminder's action and the widget's + Glass). */
    fun addGlassIntent(context: Context, source: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, com.sohum.bandlog.alarm.WaterReceiver::class.java)
            .setAction(com.sohum.bandlog.alarm.WaterReceiver.ACTION_ADD_GLASS)
            .putExtra(com.sohum.bandlog.alarm.WaterReceiver.EXTRA_SOURCE, source),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun notifyWater(context: Context, totalMl: Int, goalMl: Int, glassMl: Int) {
        ensureWaterChannel(context)
        val text = "${com.sohum.bandlog.util.WaterPrefs.litres(totalMl)} of ${com.sohum.bandlog.util.WaterPrefs.litres(goalMl)} so far today"
        val n = NotificationCompat.Builder(context, WATER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💧 Time for a glass")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openWater(context))
            .addAction(0, "+1 glass", addGlassIntent(context, "reminder", 9202))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(WATER_NOTIFICATION_ID, n) }
    }

    /** Replaces the reminder after its +1 glass with the new total (or a failure note when [totalMl] < 0). */
    @SuppressLint("MissingPermission")
    fun notifyWaterLogged(context: Context, totalMl: Int, goalMl: Int) {
        ensureWaterChannel(context)
        val title = when { totalMl < 0 -> "Couldn't log that glass"; totalMl >= goalMl -> "🎉 Water goal done for today"; else -> "💧 Glass logged" }
        val text = if (totalMl < 0) "Open Locked In and try again." else "${com.sohum.bandlog.util.WaterPrefs.litres(totalMl)} of ${com.sohum.bandlog.util.WaterPrefs.litres(goalMl)} today"
        val b = NotificationCompat.Builder(context, WATER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openWater(context))
        if (totalMl >= 0) b.setTimeoutAfter(8_000L)
        runCatching { NotificationManagerCompat.from(context).notify(WATER_NOTIFICATION_ID, b.build()) }
    }
}
