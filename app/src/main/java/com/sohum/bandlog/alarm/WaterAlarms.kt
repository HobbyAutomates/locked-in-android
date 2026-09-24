package com.sohum.bandlog.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sohum.bandlog.util.WaterPrefs
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v2.6 water reminders: one exact alarm at a time, the next slot inside the from-to window
 * (from, from + every, ... up to to; a window past midnight runs into the next day). Each fire
 * re-arms the next slot from [WaterReceiver], the same by-hand repeat as the meal reminders.
 */
object WaterAlarms {
    private const val CODE = 9201

    fun reschedule(context: Context) = runCatching {
        val s = WaterPrefs.load(context)
        cancel(context)
        if (s.every <= 0) return@runCatching
        val at = nextTrigger(s, LocalDateTime.now())
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }.isSuccess

    fun cancel(context: Context) = runCatching {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
    }.isSuccess

    /** Epoch millis of the first slot strictly after [now]. */
    fun nextTrigger(s: WaterPrefs.Settings, now: LocalDateTime): Long {
        val zone = ZoneId.systemDefault()
        val (fh, fm) = WaterPrefs.hm(s.from)
        val (th, tm) = WaterPrefs.hm(s.to)
        val every = s.every.coerceAtLeast(15).toLong()
        for (offset in -1L..2L) {
            val start = now.toLocalDate().plusDays(offset).atTime(fh, fm)
            var end = start.toLocalDate().atTime(th, tm)
            if (!end.isAfter(start)) end = end.plusDays(1)
            var t = start
            while (!t.isAfter(end)) {
                if (t.isAfter(now)) return t.atZone(zone).toInstant().toEpochMilli()
                t = t.plusMinutes(every)
            }
        }
        return now.toLocalDate().plusDays(1).atTime(fh, fm).atZone(zone).toInstant().toEpochMilli()
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, CODE,
        Intent(context, WaterReceiver::class.java).setAction(WaterReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
