package com.sohum.bandlog.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.notify.Notifications
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.WaterPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * v2.6: fires a water reminder ("Time for a glass") inside the window and only while today's
 * goal isn't met, then arms the next slot. Also handles the notification's / widget's
 * "+1 glass", which logs one glass without opening the app.
 */
class WaterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_FIRE -> {
                val s = WaterPrefs.load(app)
                if (s.every <= 0) return
                WaterAlarms.reschedule(app)
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val today = Dates.today()
                        val fresh = withTimeoutOrNull(6_000) {
                            runCatching { Session.init(app); if (Session.signedIn) Api.waterTotal(today) else null }.getOrNull()
                        }
                        val total = fresh ?: WaterPrefs.cachedTotal(app, today) ?: 0
                        WaterPrefs.cacheTotal(app, today, total)
                        if (total < s.goalMl) runCatching { Notifications.notifyWater(app, total, s.goalMl, s.glassMl) }
                    } finally { pending.finish() }
                }
            }
            ACTION_ADD_GLASS -> {
                val s = WaterPrefs.load(app)
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: "reminder"
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        Session.init(app)
                        val today = Dates.today()
                        val ok = withTimeoutOrNull(8_000) { runCatching { Api.logWater(today, s.glassMl, source) }.isSuccess } == true
                        if (ok) {
                            val total = withTimeoutOrNull(4_000) { runCatching { Api.waterTotal(today) }.getOrNull() }
                                ?: ((WaterPrefs.cachedTotal(app, today) ?: 0) + s.glassMl)
                            WaterPrefs.cacheTotal(app, today, total)
                            runCatching { Notifications.notifyWaterLogged(app, total, s.goalMl) }
                        } else runCatching { Notifications.notifyWaterLogged(app, -1, s.goalMl) }
                    } finally { pending.finish() }
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sohum.bandlog.ACTION_WATER_REMINDER"
        const val ACTION_ADD_GLASS = "com.sohum.bandlog.ACTION_WATER_ADD_GLASS"
        const val EXTRA_SOURCE = "source"
    }
}
