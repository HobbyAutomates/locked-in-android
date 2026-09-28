package com.sohum.bandlog.ui.social

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.OfflineQueue
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Motion
import com.sohum.bandlog.ui.platform.PlatformViewModel
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.LiveSquad
import com.sohum.bandlog.util.Referrals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v2.18 social + platform: the pages over the tab shell, the "3 waiting to sync" chip and the
 * background jobs (offline replay, the once-a-day freeze sync, a pending invite claim, the block
 * list, the live-session heartbeat, seasonal jewels). MainActivity calls this once.
 */
@Composable
fun SocialOverlays(vm: AppViewModel) {
    val ctx = LocalContext.current
    val pvm: PlatformViewModel = viewModel()
    LaunchedEffect(Unit) { SocialStore.init(ctx); OfflineQueue.init(ctx) }

    // Signed in and loaded: replay offline logs, sync freezes once a day, claim an invite, load blocks.
    LaunchedEffect(vm.signedIn, vm.loadedOnce) {
        if (!vm.signedIn || !vm.loadedOnce) return@LaunchedEffect
        OfflineQueue.replay()
        val today = Dates.today()
        if (SocialStore.lastFreezeSync != today) {
            try {
                val r = SocialApi.freezeSync()
                SocialStore.setFreezes(r.tokens, r.usedDays)
                SocialStore.lastFreezeSync = today
            } catch (e: NotYetAvailable) { SocialStore.lastFreezeSync = today }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("LockedIn", "Freeze sync failed", e) }
        }
        SocialStore.pendingReferral?.let { code -> claimReferral(code)?.let { referralNote = it } }
        runCatching { SocialApi.myBlocks() }.onSuccess { SocialStore.updateBlocked(SocialStore.blocked + it) }
        runCatching { SocialApi.profileRaw() }.onSuccess { o ->
            if (o.has("live_share") && !o.isNull("live_share")) SocialStore.setLive(o.optBoolean("live_share", false))
        }
        runCatching { checkEventBadges(vm) }
    }
    // Logs that just synced: re-read the lists (Home, streaks, squads).
    LaunchedEffect(OfflineQueue.syncedTick) { if (OfflineQueue.syncedTick > 0) vm.refresh() }

    // D7 live session heartbeat: while a live workout runs and the opt-in is on (default off).
    val liveOn = pvm.live != null && SocialStore.liveShare && Session.signedIn
    LaunchedEffect(liveOn) {
        if (!liveOn) return@LaunchedEffect
        val started = pvm.live?.startedAt ?: System.currentTimeMillis()
        try {
            while (true) {
                val label = pvm.live?.title?.substringAfter(" · ")?.ifBlank { null } ?: "Training"
                val ok = runCatching { SocialApi.upsertLive(label, java.time.Instant.ofEpochMilli(started).toString()) }
                if (ok.exceptionOrNull() is NotYetAvailable) break
                delay(LiveSquad.LIVE_HEARTBEAT_MS)
            }
        } finally {
            // Finished, discarded or opted out: end the row (outlives this effect).
            SocialNav.io.launch { runCatching { SocialApi.deleteLive() } }
        }
    }

    // The offline chip: "3 waiting to sync" (tap = sync now).
    Box(Modifier.fillMaxSize()) {
        val label = com.sohum.bandlog.util.I18n.syncLabel(OfflineQueue.count, SocialStore.lang)
        AnimatedVisibility(label != null && SocialNav.page == null, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(Motion.effects()), exit = fadeOut(Motion.effectsFast())) {
            OfflineChip(label.orEmpty())
        }
    }

    referralNote?.let { note ->
        com.sohum.bandlog.ui.platform.ConfirmDialog("Invite", note, "OK", onConfirm = { referralNote = null }, onDismiss = { referralNote = null })
    }

    OfflineQueue.droppedNote?.let { note ->
        com.sohum.bandlog.ui.platform.ConfirmDialog("Sync", note, "OK", onConfirm = { OfflineQueue.droppedNote = null }, onDismiss = { OfflineQueue.droppedNote = null })
    }

    AnimatedContent(
        targetState = SocialNav.page, label = "social",
        transitionSpec = { (slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())).togetherWith(slideOutVertically(Motion.spatialFast()) { it / 3 } + fadeOut(Motion.effectsFast())) },
    ) { page ->
        if (page == null) return@AnimatedContent
        val back: () -> Unit = { SocialNav.back() }
        androidx.activity.compose.BackHandler { back() }
        when (page) {
            SocialPage.HUB -> SocialHubScreen(vm, back)
            SocialPage.FREEZES -> FreezesScreen(vm, back)
            SocialPage.INVITE -> InviteScreen(vm, back)
            SocialPage.WRAPPED -> WrappedScreen(vm, back)
            SocialPage.PLEDGES -> PledgesScreen(vm, back)
            SocialPage.EVENTS -> EventsScreen(vm, back)
            SocialPage.COACH -> CoachAccessScreen(back)
            SocialPage.CLIENTS -> ClientsScreen(back)
            SocialPage.CLIENT -> ClientScreen(back)
            SocialPage.STORY -> StoryScreen(vm, back)
            SocialPage.PACKS -> PacksScreen(vm, back)
            SocialPage.LEAGUES -> LeaguesScreen(back)
            SocialPage.LANGUAGE -> LanguageScreen(vm, back)
            SocialPage.EXPORT -> ExportScreen(vm, back)
            SocialPage.DELETE -> DeleteAccountScreen(vm, back)
            SocialPage.HEALTH -> HealthSyncScreen(vm, back)
            SocialPage.VERIFY -> VerifySquadScreen(back)
            SocialPage.BLOCKED -> BlockedScreen(back)
        }
    }
}

/** The result of claiming a stored invite code, shown once. */
private var referralNote by mutableStateOf<String?>(null)

/** Claims the invite code stored from a /r/<CODE> link, once. Null = nothing to say. */
internal suspend fun claimReferral(code: String): String? {
    val c = Referrals.normalizeCode(code) ?: run { SocialStore.pendingReferral = null; return null }
    return try {
        val r = SocialApi.referralClaim(c)
        // "missing" (v44 not applied): keep the code and try again on a later open.
        if (r.reason != "missing") SocialStore.pendingReferral = null
        if (r.reason == "missing") null else Referrals.claimMessage(r.ok, r.reason, r.referrerName)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { null }
}

/** The small pill under the status bar while logs wait for the network. Tap = try now. */
@Composable
private fun OfflineChip(text: String) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(
        Modifier.statusBarsPadding().padding(top = 6.dp).shadow(8.dp, CircleShape).background(InkBrand, CircleShape)
            .clickable(onClickLabel = "Sync now") { scope.launch { OfflineQueue.replay() } }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (OfflineQueue.syncing) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = BoneBrand)
        else Box(Modifier.size(8.dp).background(if (OfflineQueue.online) Ember else Color(0xFF8F8A82), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight(600), color = BoneBrand)
    }
}
