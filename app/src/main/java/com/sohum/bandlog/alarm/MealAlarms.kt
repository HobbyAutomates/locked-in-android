package com.sohum.bandlog.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sohum.bandlog.util.Reminders
import java.util.Calendar

/**
 * Arms one repeating-by-hand alarm per enabled meal reminder. Android has no reliable exact
 * repeating alarm, so each fire re-arms the next day from inside [MealReminderReceiver] — the
 * same pattern the Reminders app uses for its daily blessing.
 */
object MealAlarms {

    fun rescheduleAll(context: Context) = runCatching {
        val prefs = Reminders.load(context)
        Reminders.SLOTS.forEach { slot ->
            val p = prefs[slot.key] ?: return@forEach
            if (p.on) schedule(context, slot.key, p.hour, p.minute) else cancel(context, slot.key)
        }
    }.isSuccess

    fun schedule(context: Context, key: String, hour: Int, minute: Int) = runCatching {
        val slot = Reminders.slot(key) ?: return@runCatching
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, slot.key, slot.code)
        val at = nextTrigger(hour, minute)
        // Exact alarms can be revoked on 12+; fall back to allow-while-idle so the nudge is late, not lost.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }.isSuccess

    fun cancel(context: Context, key: String) = runCatching {
        val slot = Reminders.slot(key) ?: return@runCatching
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(context, slot.key, slot.code))
    }.isSuccess

    /** True when the system still lets us post exact alarms (the Reminders screen warns if not). */
    fun canScheduleExact(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) true
        else (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }.getOrDefault(true)

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    private fun pendingIntent(context: Context, key: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, MealReminderReceiver::class.java).apply {
                action = MealReminderReceiver.ACTION_FIRE
                putExtra(MealReminderReceiver.EXTRA_KEY, key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
