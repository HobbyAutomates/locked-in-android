package com.sohum.bandlog.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.OfflineQueue
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Freezes
import com.sohum.bandlog.util.Leagues
import kotlinx.coroutines.launch

/** Profile → "Social and rewards": every v2.18 screen in one list. */
@Composable
fun SocialHubScreen(vm: AppViewModel, onBack: () -> Unit) {
    SubPage(tr("social.title"), onBack) {
        Hero(tr("social.title"), "${vm.dayStreak}-day streak", Freezes.freezeCountText(SocialStore.freezeTokens) + " banked")
        GroupLabel("Motivation")
        Card(padding = 0.dp) {
            HubRow(LineIcons.Shield, tr("streak.title"), tr("streak.sub"), Freezes.freezeCountText(SocialStore.freezeTokens)) { SocialNav.open(SocialPage.FREEZES) }
            Hair()
            HubRow(LineIcons.Star, tr("wrapped.title"), tr("wrapped.sub")) { SocialNav.open(SocialPage.WRAPPED) }
            Hair()
            HubRow(LineIcons.Flag, tr("pledges.title"), tr("pledges.sub")) { SocialNav.squad = null; SocialNav.open(SocialPage.PLEDGES) }
            Hair()
            HubRow(LineIcons.Award, tr("events.title"), tr("events.sub")) { SocialNav.open(SocialPage.EVENTS) }
            Hair()
            HubRow(LineIcons.Camera, tr("story.title"), tr("story.sub")) { SocialNav.open(SocialPage.STORY) }
            if (Leagues.leaguesOn(null)) { Hair(); HubRow(LineIcons.Trophy, tr("leagues.title"), "") { SocialNav.open(SocialPage.LEAGUES) } }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show my squads when I'm training", fontSize = 15.sp, fontWeight = FontWeight(600), color = palette.ink)
                    Text("During a live workout: “${com.sohum.bandlog.util.LiveSquad.liveLine(vm.profile.name.ifBlank { "You" })}”. Off by default.", fontSize = 12.sp, color = palette.muted, lineHeight = 16.sp)
                }
                androidx.compose.material3.Switch(SocialStore.liveShare, { on ->
                    SocialStore.setLive(on)
                    SocialNav.io.launch { com.sohum.bandlog.data.SocialApi.patchProfile(org.json.JSONObject().put("live_share", on)) }
                }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Ember))
            }
        }
        GroupLabel("Friends and coaches")
        Card(padding = 0.dp) {
            HubRow(LineIcons.Share, tr("invite.title"), tr("invite.sub")) { SocialNav.open(SocialPage.INVITE) }
            Hair()
            HubRow(LineIcons.User, tr("coach.title"), tr("coach.sub")) { SocialNav.open(SocialPage.COACH) }
            Hair()
            HubRow(LineIcons.Chart, tr("clients.title"), "For trainers and dietitians") { SocialNav.open(SocialPage.CLIENTS) }
        }
        GroupLabel("Premium")
        Card(padding = 0.dp) {
            HubRow(LineIcons.Crown, tr("packs.title"), tr("packs.sub"), tint = Gold) { SocialNav.open(SocialPage.PACKS) }
        }
        GroupLabel("App")
        Card(padding = 0.dp) {
            HubRow(LineIcons.Refresh, "Offline logging", if (OfflineQueue.count > 0) com.sohum.bandlog.util.I18n.syncLabel(OfflineQueue.count, SocialStore.lang).orEmpty() else tr("sync.done"), chevron = false) { OfflineQueue.replaySoon() }
            Hair()
            HubRow(LineIcons.Drop, "Scales and watches", "Weight, steps and heart rate from Health Connect") { SocialNav.open(SocialPage.HEALTH) }
            Hair()
            HubRow(LineIcons.Message, tr("lang.title"), com.sohum.bandlog.util.I18n.LANGS.first { it.key == SocialStore.lang }.native) { SocialNav.open(SocialPage.LANGUAGE) }
            Hair()
            HubRow(LineIcons.Sliders, tr("export.title"), tr("export.sub")) { SocialNav.open(SocialPage.EXPORT) }
            Hair()
            HubRow(LineIcons.Shield, "Blocked people", "${SocialStore.blocked.size}") { SocialNav.open(SocialPage.BLOCKED) }
        }
    }
}

@Composable
internal fun HubRow(icon: ImageVector, title: String, sub: String, value: String = "", tint: Color? = null, chevron: Boolean = true, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClickLabel = title, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: p.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
        }
        if (value.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Text(value, fontSize = 12.sp, color = p.muted, maxLines = 1) }
        if (chevron) { Spacer(Modifier.width(6.dp)); Icon(LineIcons.ChevronRight, null, tint = p.muted, modifier = Modifier.size(16.dp)) }
    }
}
