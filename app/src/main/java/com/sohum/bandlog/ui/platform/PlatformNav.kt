package com.sohum.bandlog.ui.platform

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sohum.bandlog.MainActivity
import com.sohum.bandlog.data.PhotoV2
import com.sohum.bandlog.data.PlatformApi
import com.sohum.bandlog.util.Recaps
import com.sohum.bandlog.util.Routines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** v2.13 platform pages, pushed over the tab shell by [PlatformOverlays]. */
enum class PlatformPage { PRO, INBOX, NOTIFICATIONS, MEASUREMENTS, PHOTOS, BEFORE_AFTER, ROUTINES, ROUTINE_EDIT, WORKOUT, PR_CHARTS, MUSCLE_MAP, RECAPS, RECAP }

/**
 * v2.13 platform navigation. Its own little back stack so MainActivity only needs one overlay
 * call and one intent hook (the nutrition half edits MainActivity too). Page arguments ride
 * along as plain state.
 */
object PlatformNav {
    const val OPEN_SQUAD = "squad"
    const val OPEN_INBOX = "inbox"
    const val OPEN_WORKOUT = "workout"
    const val OPEN_MEAL = MainActivity.OPEN_MEAL
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    val stack = mutableStateListOf<PlatformPage>()
    val page: PlatformPage? get() = stack.lastOrNull()

    /** Bumped to make MainShell jump to the Squad tab (a tapped nudge notification). */
    var squadTick by mutableIntStateOf(0)
    /** Bumped at a sensible moment to ask for POST_NOTIFICATIONS once (after a nudge, turning a notice on). */
    var askNotificationsTick by mutableIntStateOf(0)

    /** Recap prompts dismissed or opened this session (Home drops the card straight away). */
    val hiddenRecaps = mutableStateListOf<String>()

    // ---- page arguments ----
    var editing by mutableStateOf<Routines.Routine?>(null)
    var compare by mutableStateOf<Pair<PhotoV2, PhotoV2>?>(null)
    var recap by mutableStateOf<Recaps.Period?>(null)
    var prExercise by mutableStateOf<String?>(null)

    fun open(p: PlatformPage) {
        if (stack.lastOrNull() == p) return
        stack.remove(p)
        stack.add(p)
    }

    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun closeAll() { stack.clear() }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called from MainActivity.handleIntent before the extra is consumed. A tapped inbox
     * notification is marked read; a nudge opens Squad; the rest timer opens the workout.
     */
    fun handleIntent(context: Context, i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_NOTIFICATION_ID)?.let { id ->
            io.launch { runCatching { PlatformApi.markRead(listOf(id)) } }
            i.removeExtra(EXTRA_NOTIFICATION_ID)
        }
        when (i.getStringExtra(MainActivity.EXTRA_OPEN)) {
            OPEN_SQUAD -> { closeAll(); squadTick++ }
            OPEN_INBOX -> open(PlatformPage.INBOX)
            OPEN_WORKOUT -> open(PlatformPage.WORKOUT)
        }
    }

    /** Every app open: check the inbox now, keep the 15-minute check scheduled, re-arm the protein alarm. */
    fun onAppOpen(context: Context) {
        val app = context.applicationContext
        com.sohum.bandlog.notify.PlatformNotifications.ensureChannels(app)
        com.sohum.bandlog.notify.InboxWorker.schedule(app)
        com.sohum.bandlog.notify.InboxWorker.checkSoon(app)
        com.sohum.bandlog.alarm.ProteinNudgeAlarms.reschedule(app)
    }
}
