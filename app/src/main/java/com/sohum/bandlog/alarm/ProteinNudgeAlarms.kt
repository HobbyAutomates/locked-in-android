package com.sohum.bandlog.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.PlatformApi
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.notify.PlatformNotifications
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.PlatformPrefs
import com.sohum.bandlog.util.ProteinNudge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v2.13 protein nudge (spec §3), scheduled locally so it's on time (the web cron covers web
 * push): one exact alarm a day at the chosen time (India time, like every "today" in the app),
 * re-armed from the receiver — the MealAlarms pattern.
 */
object ProteinNudgeAlarms {
    private const val CODE = 9301

    fun reschedule(context: Context) = runCatching {
        val s = PlatformPrefs.proteinNudge(context)
        cancel(context)
        if (!s.on) return@runCatching
        val (h, m) = ProteinNudge.parseTime(s.time)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTrigger(h, m, LocalDateTime.now(Dates.ZONE))
        val pi = pending(context)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }.isSuccess

    fun cancel(context: Context) = runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context)) }.isSuccess

    /** Epoch millis of the next h:m India time strictly after [now] (an India wall-clock time). */
    fun nextTrigger(h: Int, m: Int, now: LocalDateTime, zone: ZoneId = Dates.ZONE): Long {
        var t = now.toLocalDate().atTime(h, m)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(zone).toInstant().toEpochMilli()
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, CODE, Intent(context, ProteinNudgeReceiver::class.java).setAction(ProteinNudgeReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Checks today's protein when the alarm fires and posts the nudge if §3's condition holds. */
class ProteinNudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val app = context.applicationContext
        val s = PlatformPrefs.proteinNudge(app)
        if (!s.on) return
        ProteinNudgeAlarms.reschedule(app)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) { runCatching { fire(app) } }
            } finally { pending.finish() }
        }
    }

    private suspend fun fire(app: Context) {
        Session.init(app)
        if (!Session.signedIn) return
        val today = Dates.today()
        val profile = Api.profile()
        val meals = Api.meals(today, today)
        val protein = totalsFor(meals, today).protein
        val extras = runCatching { PlatformApi.profileExtras() }.getOrNull()
        // The server's copy of the switch wins when it exists.
        val enabled = extras?.proteinNudge ?: PlatformPrefs.proteinNudge(app).on
        val go = ProteinNudge.shouldNudge(
            enabled = enabled, proteinToday = protein, target = profile.proteinTargetG, loggedToday = meals.any { it.date == today },
            age = profile.age, goalType = profile.goalType, alreadySentToday = PlatformPrefs.firedOn(app, "protein") == today,
        )
        if (!go) return
        val short = ProteinNudge.shortBy(protein, profile.proteinTargetG)
        val diet = extras?.dietMode ?: PlatformPrefs.dietMode(app)
        val picks = ProteinNudge.picksProvider?.invoke(short, diet) ?: ProteinNudge.picks(short, diet)
        val title = ProteinNudge.title(short)
        val body = ProteinNudge.body(picks)
        PlatformPrefs.markFired(app, "protein", today)
        PlatformNotifications.postProtein(app, title, body)
        // Mirror it into the inbox (already "pushed", so the web dispatcher won't push it again).
        runCatching { PlatformApi.insertNotice("protein", title, body, "/log") }
    }

    companion object {
        const val ACTION_FIRE = "com.sohum.bandlog.ACTION_PROTEIN_NUDGE"
    }
}
