package com.sohum.bandlog.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SupplementV
import com.sohum.bandlog.data.V218Api
import com.sohum.bandlog.notify.PlatformNotifications
import com.sohum.bandlog.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * v2.18 B7 supplement reminders on the phone: one exact daily alarm per supplement with a reminder
 * time (India time), re-armed when it fires and whenever the list changes. The receiver checks the
 * server first and stays quiet when it's already ticked today. The ids / times live in prefs so a
 * reboot can re-arm them without the network.
 */
object SupplementAlarms {
    private const val PREFS = "v218_supplements"
    private const val KEY = "alarms"
    private const val BASE = 9700

    private fun code(id: String) = BASE + (id.hashCode() and 0xFF)

    fun reschedule(context: Context, items: List<SupplementV>) = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Cancel everything we set before, then arm the current list.
        prefs.getString(KEY, "").orEmpty().split("|").filter { it.isNotBlank() }.forEach { cancel(context, it.substringBefore(";")) }
        val on = items.filter { it.active && it.remindAt != null }
        prefs.edit().putString(KEY, on.joinToString("|") { "${it.id};${it.remindAt};${it.name.replace("|", " ").replace(";", " ")}" }).apply()
        on.forEach { arm(context, it.id, it.remindAt!!, it.name) }
    }.isSuccess

    /** After a reboot / update (BootReceiver doesn't know about us, so MainActivity's open re-arms too). */
    fun rearm(context: Context) = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().split("|").filter { it.isNotBlank() }.forEach {
            val p = it.split(";")
            if (p.size >= 3) arm(context, p[0], p[1], p[2])
        }
    }.isSuccess

    private fun arm(context: Context, id: String, at: String, name: String) {
        val (h, m) = at.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 8) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val t = ProteinNudgeAlarms.nextTrigger(h, m, LocalDateTime.now(Dates.ZONE))
        val pi = pending(context, id, name, at)
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    }

    private fun cancel(context: Context, id: String) = runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, id, "", "")) }

    private fun pending(context: Context, id: String, name: String, at: String): PendingIntent = PendingIntent.getBroadcast(
        context, code(id),
        Intent(context, SupplementReceiver::class.java).setAction(SupplementReceiver.ACTION).putExtra("id", id).putExtra("name", name).putExtra("at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: String, name: String) {
        if (!PlatformNotifications.permitted(context)) return
        PlatformNotifications.ensureChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, com.sohum.bandlog.ui.v218.CoachPlusNav.OPEN_SUPPLEMENTS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, code(id), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, PlatformNotifications.COACH_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("$name time")
            .setContentText("Tap to tick it off and keep your streak.")
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(code(id), n) }
    }
}

class SupplementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val id = intent.getStringExtra("id") ?: return
        val name = intent.getStringExtra("name").orEmpty().ifBlank { "Supplement" }
        SupplementAlarms.rearm(app)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) {
                    Session.init(app)
                    if (!Session.signedIn) return@withTimeoutOrNull
                    // Already ticked today (or the item is gone / paused): stay quiet.
                    val s = runCatching { V218Api.supplements() }.getOrNull()
                    val it = s?.items?.firstOrNull { x -> x.id == id }
                    if (s != null && (it == null || it.takenToday || !it.active)) return@withTimeoutOrNull
                    SupplementAlarms.post(app, id, name)
                }
            } finally { pending.finish() }
        }
    }

    companion object { const val ACTION = "com.sohum.bandlog.ACTION_SUPPLEMENT" }
}
