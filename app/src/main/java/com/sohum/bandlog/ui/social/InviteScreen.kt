package com.sohum.bandlog.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.Mono
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Names
import com.sohum.bandlog.util.Referrals
import kotlinx.coroutines.launch

/** D2 "Invite friends": my code and link, who joined with it, and the Pro days banked. */
@Composable
fun InviteScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val code = rememberLoad(Unit) { SocialApi.myReferralCode() }
    val joined = rememberLoad(Unit) { SocialApi.myReferrals() }
    val banked = rememberLoad(Unit) { SocialApi.profileRaw().optInt("referral_pro_days", 0) }
    var typed by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    SubPage(tr("invite.title"), onBack) {
        if (code.missing) { SoonCard(tr("invite.title"), "Invite links switch on with the next server update."); return@SubPage }
        Hero(tr("invite.title"), tr("invite.sub"), "When a friend joins with your link, you both get 1 week of Pro.")
        Card {
            Text("YOUR CODE", fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.4.sp, color = p.muted)
            Spacer(Modifier.padding(3.dp))
            if (code.loading) Spinner()
            else code.data?.let { c ->
                Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(c, fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight(600), letterSpacing = 6.sp, color = p.ink)
                }
                Spacer(Modifier.padding(4.dp))
                val url = Referrals.inviteUrl(SocialApi.site, c)
                Text(url, fontSize = 12.sp, color = p.muted, maxLines = 1)
                Spacer(Modifier.padding(6.dp))
                PillButton(tr("common.share"), { com.sohum.bandlog.ui.squad.shareInvite(ctx, Referrals.inviteText(vm.profile.name, url)) }, bg = Ember, fg = BoneBrand)
            }
            ErrorNote(code.error)
        }
        Referrals.bankedText(banked.data)?.let { t ->
            Card { Row(verticalAlignment = Alignment.CenterVertically) { OutlinePill("PRO", Gold); Spacer(Modifier.width(10.dp)); Text(t, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink) }
                Text("Everyone is on the beta, so it's saved for when Pro starts.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp)) }
        }
        GroupLabel("Joined with your link")
        Card {
            when {
                joined.loading -> Spinner()
                joined.data.isNullOrEmpty() -> Text("Nobody yet. Share the link with a gym buddy.", fontSize = 14.sp, color = p.muted)
                else -> joined.data.forEachIndexed { i, r ->
                    if (i > 0) Hair()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.avatarUrl(r.avatarPath), Names.initials(r.name), 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(r.name, fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
                        r.joinedAt?.take(10)?.let { Text(Dates.relative(it), fontSize = 12.sp, color = p.muted) }
                    }
                }
            }
        }
        GroupLabel("Got a friend's code?")
        Card {
            SocialField(typed, { typed = it.uppercase().take(8) }, "6-letter code")
            Spacer(Modifier.padding(4.dp))
            ErrorNote(err)
            note?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.green) }
            PillButton("Use code", {
                val c = Referrals.normalizeCode(typed)
                if (c == null) { err = "Codes are 6 letters and numbers."; return@PillButton }
                scope.launch { err = null; note = claimReferral(c) ?: Referrals.claimMessage(false, "missing") }
            }, height = 46.dp, enabled = typed.isNotBlank())
        }
        if (SocialStore.pendingReferral != null) Text("An invite from a link will be used automatically.", fontSize = 12.sp, color = p.muted)
    }
}
