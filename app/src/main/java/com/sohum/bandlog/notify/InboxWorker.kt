package com.sohum.bandlog.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sohum.bandlog.data.InboxItem
import com.sohum.bandlog.data.PlatformApi
import com.sohum.bandlog.data.SchemaMissingException
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.PlatformPrefs
import java.util.concurrent.TimeUnit

/**
 * v2.13 (spec §2): Android has no FCM project, so the inbox (`bandlog.notifications`) is polled
 * every 15 minutes by WorkManager and once on every app open. Unread rows that haven't been
 * shown on this device yet become local notifications; the ids are remembered locally. Tapping
 * one marks it read (MainActivity → PlatformNav).
 */
class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        return try {
            Session.init(app)
            if (!Session.signedIn) return Result.success()
            check(app)
            Result.success()
        } catch (e: SchemaMissingException) {
            Result.success() // v36 not applied yet: nothing to poll.
        } catch (e: Exception) {
            android.util.Log.i("LockedIn", "Inbox check failed: ${e.message}")
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "inbox_check_periodic"
        private const val ONCE = "inbox_check_now"

        /** The rows to post: unread, not shown here yet, and not a protein / fasting notice this device already fired locally today. */
        fun toPost(rows: List<InboxItem>, shown: Set<String>, firedToday: (String) -> Boolean): List<InboxItem> =
            rows.filter { it.unread && it.id !in shown && !(it.kind in setOf("protein", "fasting") && firedToday(it.kind)) }

        suspend fun check(app: Context) {
            val rows = PlatformApi.unreadSince(3)
            val shown = PlatformPrefs.shownIds(app)
            val today = Dates.today()
            val post = toPost(rows, shown, firedToday = { k -> PlatformPrefs.firedOn(app, k) == today })
            // Everything unread gets remembered so a skipped duplicate isn't reconsidered on the next run.
            PlatformPrefs.markShown(app, rows.map { it.id })
            post.takeLast(5).forEach { PlatformNotifications.postInbox(app, it) }
            // A server-side protein notice counts as today's nudge so the local alarm stays quiet.
            post.filter { it.kind == "protein" }.forEach { _ -> PlatformPrefs.markFired(app, "protein", today) }
        }

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes (WorkManager's minimum), kept across launches. */
        fun schedule(context: Context) = runCatching {
            val req = PeriodicWorkRequestBuilder<InboxWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }.isSuccess

        /** A one-off check right now (app open). */
        fun checkSoon(context: Context) = runCatching {
            val req = OneTimeWorkRequestBuilder<InboxWorker>().setConstraints(network).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }.isSuccess

        fun cancel(context: Context) = runCatching { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC) }
    }
}
