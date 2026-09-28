package com.sohum.bandlog.ui.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** v2.18 social + platform pages, pushed over the tab shell by [SocialOverlays]. */
enum class SocialPage {
    HUB, FREEZES, INVITE, WRAPPED, PLEDGES, EVENTS, COACH, CLIENTS, CLIENT, STORY, PACKS, LEAGUES,
    LANGUAGE, EXPORT, DELETE, HEALTH, VERIFY, BLOCKED,
}

/**
 * v2.18 navigation: its own back stack (like PlatformNav / CoachNav) so MainActivity needs one
 * overlay call. Page arguments ride along as plain state.
 */
object SocialNav {
    val stack = mutableStateListOf<SocialPage>()
    val page: SocialPage? get() = stack.lastOrNull()

    // ---- page arguments ----
    /** CLIENT: the coach's client (id, name). */
    var client by mutableStateOf<Pair<String, String>?>(null)
    /** VERIFY / squad PLEDGES: the squad (id, name). */
    var squad by mutableStateOf<Pair<String, String>?>(null)

    /** Background work that must outlive a screen (ending a live session, a sync). */
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun open(p: SocialPage) {
        if (stack.lastOrNull() == p) return
        stack.remove(p)
        stack.add(p)
    }

    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun closeAll() { stack.clear() }
}

/**
 * v2.18 D2: an invite link (https://…/r/<CODE>) opened the app. The code is kept on the device and
 * claimed once, after sign-in (SocialOverlays). Called from MainActivity.handleIntent.
 */
fun handleReferralLink(i: android.content.Intent?) {
    val uri = i?.data ?: return
    if (i.action != android.content.Intent.ACTION_VIEW) return
    com.sohum.bandlog.util.Referrals.codeFromPath(uri.pathSegments)?.let { com.sohum.bandlog.data.SocialStore.pendingReferral = it }
}
