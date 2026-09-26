package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.InboxItem
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.FistIcon
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette

/**
 * v2.13 Home header bell (spec §2 in-app inbox): the unread count on a dot; opens the inbox.
 * Self-contained so Home only needs one call.
 */
@Composable
fun InboxBell() {
    val p = palette
    val pvm: PlatformViewModel = viewModel()
    LaunchedEffect(Unit) { pvm.loadInbox() }
    if (pvm.inboxSupported == false) return
    val n = pvm.unread
    Box(
        Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.card, CircleShape)
            .clickable(onClickLabel = "Notifications") { PlatformNav.open(PlatformPage.INBOX) }
            .semantics { contentDescription = if (n > 0) "Notifications, $n unread" else "Notifications" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(LineIcons.Bell, null, tint = p.ink, modifier = Modifier.size(19.dp))
        if (n > 0) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp).size(if (n > 9) 16.dp else 14.dp).background(accentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(if (n > 9) "9+" else "$n", fontSize = 8.sp, fontWeight = FontWeight(800), color = androidx.compose.ui.graphics.Color.White, lineHeight = 9.sp) }
    }
}

/** The inbox list: newest first, unread in bold; tapping a row marks it read and follows it. */
@Composable
fun InboxScreen(pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    LaunchedEffect(Unit) { pvm.loadInbox(); PlatformNav.askNotificationsTick++ }
    SubPage("Notifications", onBack) {
        when {
            pvm.inboxSupported == false -> ComingSoonCard("Your inbox", "Squad nudges and reminders will collect here once the server update is live.")
            pvm.inbox.isEmpty() -> Card {
                Text("Nothing yet", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("Nudges from your squad and reminders show up here.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            }
            else -> {
                if (pvm.unread > 0) Text(
                    "Mark all as read", fontSize = 14.sp, fontWeight = FontWeight(600), color = accentColor,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp).clickable { pvm.markAllRead() }.padding(horizontal = 8.dp, vertical = 12.dp),
                )
                Card(padding = 0.dp) {
                    pvm.inbox.forEachIndexed { i, item ->
                        if (i > 0) Hair()
                        InboxRow(item) {
                            pvm.markRead(item)
                            when (com.sohum.bandlog.notify.PlatformNotifications.targetFor(item)) {
                                PlatformNav.OPEN_SQUAD -> { PlatformNav.closeAll(); PlatformNav.squadTick++ }
                                // v2.14: the coach's note opens the chat, a buddy notice the buddy page.
                                PlatformNav.OPEN_COACH -> { PlatformNav.closeAll(); com.sohum.bandlog.ui.coach.CoachNav.open(com.sohum.bandlog.ui.coach.CoachPage.CHAT) }
                                PlatformNav.OPEN_BUDDY -> { PlatformNav.closeAll(); com.sohum.bandlog.ui.coach.CoachNav.open(com.sohum.bandlog.ui.coach.CoachPage.BUDDY) }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem, onClick: () -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
            when (item.kind) {
                "nudge" -> Icon(FistIcon, null, tint = p.ink, modifier = Modifier.size(17.dp))
                "coach" -> Icon(com.sohum.bandlog.ui.onboarding.OnbIcons.Star, null, tint = p.iris, modifier = Modifier.size(17.dp))
                "buddy" -> Icon(com.sohum.bandlog.ui.onboarding.OnbIcons.Flame, null, tint = p.ember, modifier = Modifier.size(17.dp))
                else -> Icon(LineIcons.Bell, null, tint = p.ink, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight(if (item.unread) 700 else 500), color = p.ink)
            if (item.body.isNotBlank()) Text(item.body, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
            Text(ago(item.createdAt), fontSize = 12.sp, color = p.muted)
        }
        if (item.unread) Box(Modifier.padding(top = 6.dp).size(8.dp).background(accentColor, CircleShape))
    }
}

/** "5 min ago", "3 h ago", "Yesterday", "12 Sep". */
internal fun ago(iso: String): String = runCatching {
    val t = java.time.OffsetDateTime.parse(iso).toInstant()
    val mins = java.time.Duration.between(t, java.time.Instant.now()).toMinutes()
    when {
        mins < 1 -> "Just now"
        mins < 60 -> "$mins min ago"
        mins < 24 * 60 -> "${mins / 60} h ago"
        mins < 48 * 60 -> "Yesterday"
        else -> t.atZone(com.sohum.bandlog.util.Dates.ZONE).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))
    }
}.getOrDefault("")
