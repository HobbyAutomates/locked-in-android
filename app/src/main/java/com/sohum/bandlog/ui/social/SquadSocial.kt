package com.sohum.bandlog.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.GroupPost
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.platform.PlatformNav
import com.sohum.bandlog.ui.platform.PlatformPage
import com.sohum.bandlog.ui.platform.PlatformViewModel
import com.sohum.bandlog.ui.platform.LiveWorkout
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.LiveSquad
import com.sohum.bandlog.util.Safety
import com.sohum.bandlog.util.Stamps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v2.18 squad hooks, called from SquadPage with one line each: the verified tick (D3), the live
 * strip + pledges row + owner's verification request (D7, D8, D3), clean / cheat stamps on meal
 * photos (D6) and report / block (E5). Each reads its own table and hides itself when it's missing.
 */
object SquadSocialState {
    val stamps = mutableStateMapOf<String, Stamps.State>()
    var stampsMissing by mutableStateOf(false)
    val verified = mutableStateMapOf<String, SocialApi.Verified>()
}

/** The gold tick and org name next to a verified squad's name. */
@Composable
fun VerifiedTick(squadId: String) {
    LaunchedEffect(squadId) {
        if (squadId !in SquadSocialState.verified) runCatching { SocialApi.squadVerified(listOf(squadId)) }.onSuccess { SquadSocialState.verified.putAll(it) }
    }
    val v = SquadSocialState.verified[squadId]?.takeIf { it.verified } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Verified${v.orgName?.let { " · $it" } ?: ""}" }) {
        Box(Modifier.size(18.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) { Text("✓", fontSize = 11.sp, fontWeight = FontWeight(800), color = InkBrand) }
        v.orgName?.let { Spacer(Modifier.width(6.dp)); Text(it, fontSize = 11.sp, fontWeight = FontWeight(600), color = Gold, maxLines = 1) }
    }
}

/** Under the squad header: who's training live (cheer / join), the pledges row, and the owner's "Request verification". */
@Composable
fun SquadSocialStrip(squadId: String, squadName: String, isOwner: Boolean) {
    val p = palette
    val scope = rememberCoroutineScope()
    val pvm: PlatformViewModel = viewModel()
    var live by remember(squadId) { mutableStateOf<List<LiveSquad.Row>>(emptyList()) }
    var liveMissing by remember(squadId) { mutableStateOf(false) }
    var cheered by remember(squadId) { mutableStateOf<Set<String>>(emptySet()) }
    var note by remember(squadId) { mutableStateOf<String?>(null) }
    LaunchedEffect(squadId) {
        while (!liveMissing) {
            try { live = SocialApi.squadLive(squadId) } catch (e: NotYetAvailable) { liveMissing = true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
            delay(60_000)
        }
    }
    val verified = SquadSocialState.verified[squadId]
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        live.forEach { r ->
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1A120C), RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(Ember, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (SocialStore.lang == "en") LiveSquad.liveLine(r.name, r.label) else tr("squad.live", mapOf("name" to r.name.trim().substringBefore(' '))), fontSize = 14.sp, fontWeight = FontWeight(700), color = BoneBrand, maxLines = 1)
                    Text(LiveSquad.liveElapsed(r.startedAt) + if (r.cheers > 0) " · ${r.cheers} cheers" else "", fontSize = 11.sp, color = Color(0xFFB5B0A8))
                }
                SmallAction(if (r.userId in cheered) "Cheered" else tr("squad.cheer"), filled = true) {
                    if (r.userId in cheered) return@SmallAction
                    scope.launch {
                        val ok = runCatching { SocialApi.liveCheer(r.userId) }.getOrDefault(false)
                        cheered = cheered + r.userId
                        note = if (ok) "Cheer sent to ${r.name.substringBefore(' ')}." else "You cheered them a moment ago."
                    }
                }
                Spacer(Modifier.width(6.dp))
                SmallAction(tr("squad.join"), filled = false) {
                    if (pvm.live == null) pvm.live = LiveWorkout(title = "Training with ${r.name.substringBefore(' ')}", startedAt = System.currentTimeMillis(), exercises = emptyList())
                    PlatformNav.open(PlatformPage.WORKOUT)
                }
            }
        }
        note?.let { Text(it, fontSize = 12.sp, color = p.muted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(tr("pledges.title"), false, { SocialNav.squad = squadId to squadName; SocialNav.open(SocialPage.PLEDGES) })
            if (isOwner && verified?.verified != true && squadId in SquadSocialState.verified) {
                ChoicePill("Request verification", false, { SocialNav.squad = squadId to squadName; SocialNav.open(SocialPage.VERIFY) })
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 34.dp).background(if (filled) Ember else Color.Transparent, CircleShape).border(1.dp, Ember, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight(700), color = if (filled) BoneBrand else Ember, maxLines = 1) }
}

// ---------------------------------------------------------------- D6 stamps

/** Loads the stamp counts for the feed's stampable posts (one RPC). */
@Composable
fun LoadStamps(posts: List<GroupPost>) {
    val ids = posts.filter { Stamps.stampable(it.kind, it.photoPath) }.map { it.id }
    LaunchedEffect(ids) {
        if (ids.isEmpty() || SquadSocialState.stampsMissing) return@LaunchedEffect
        try { SquadSocialState.stamps.putAll(SocialApi.stampCounts(ids)) } catch (e: NotYetAvailable) { SquadSocialState.stampsMissing = true }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }
}

/** The big rotated CLEAN / CHEAT stamp over a meal photo (the majority; a tie shows none). */
@Composable
fun BoxScope.StampOverlay(post: GroupPost) {
    if (!Stamps.stampable(post.kind, post.photoPath) || SquadSocialState.stampsMissing) return
    val verdict = Stamps.stampVerdict(SquadSocialState.stamps[post.id] ?: Stamps.EMPTY) ?: return
    val color = if (verdict == Stamps.CLEAN) Color(0xFF1FAE6F) else Ember
    Box(
        Modifier.align(Alignment.Center).rotate(-14f).border(4.dp, color, RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
    ) { Text(Stamps.LABEL.getValue(verdict), fontSize = 34.sp, fontWeight = FontWeight(900), letterSpacing = 4.sp, color = color) }
}

/** The two stamp buttons under a meal photo, with their counts. */
@Composable
fun StampBar(post: GroupPost) {
    if (!Stamps.stampable(post.kind, post.photoPath) || SquadSocialState.stampsMissing) return
    val p = palette
    val scope = rememberCoroutineScope()
    val state = SquadSocialState.stamps[post.id] ?: Stamps.EMPTY
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stamps.ALL.forEach { s ->
            val mine = state.mine == s
            val color = if (s == Stamps.CLEAN) p.green else Ember
            Row(
                Modifier.heightIn(min = 32.dp).background(if (mine) color.copy(alpha = 0.16f) else p.card2, CircleShape).border(1.5.dp, if (mine) color else Color.Transparent, CircleShape)
                    .clickable(onClickLabel = "Stamp ${Stamps.LABEL[s]}") {
                        val before = state
                        val ch = Stamps.toggleStamp(state, s)
                        SquadSocialState.stamps[post.id] = ch.state
                        scope.launch {
                            try { SocialApi.setStamp(post.id, ch.save) } catch (e: NotYetAvailable) { SquadSocialState.stampsMissing = true }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { SquadSocialState.stamps[post.id] = before }
                        }
                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Stamps.LABEL.getValue(s), fontSize = 11.sp, fontWeight = FontWeight(800), letterSpacing = 1.2.sp, color = color)
                val n = state.count(s)
                if (n > 0) { Spacer(Modifier.width(6.dp)); Text("$n", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink) }
            }
        }
    }
}

// ---------------------------------------------------------------- E5 report + block

/** Report a post (a reason, an optional note) and / or block its author. */
@Composable
fun ReportBlockSheet(post: GroupPost, onDismiss: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }
    var done by remember { mutableStateOf<String?>(null) }
    val blocked = post.userId in SocialStore.blocked
    BottomSheet(title = "${tr("squad.report")} · ${post.authorName}", subtitle = "Reports go to the Locked In team. They're private.", onDismiss = onDismiss) {
        if (done != null) { Text(done.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(vertical = 12.dp)); return@BottomSheet }
        Safety.REPORT_REASONS.forEachIndexed { i, r ->
            if (i > 0) Hair()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { reason = r.key }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).border(2.dp, if (reason == r.key) Ember else p.muted, CircleShape).padding(4.dp)) {
                    if (reason == r.key) Box(Modifier.size(10.dp).background(Ember, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Text(r.label, fontSize = 15.sp, color = p.ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        SocialField(noteText, { noteText = it.take(500) }, "Anything we should know? (optional)", singleLine = false)
        Spacer(Modifier.height(10.dp))
        com.sohum.bandlog.ui.components.PillButton(tr("squad.report"), {
            val r = reason ?: return@PillButton
            scope.launch {
                done = try {
                    SocialApi.report(r, noteText.trim(), post.userId, post.groupId, post.id, Safety.reportSnapshot(post.kind, post.body, post.authorName, post.createdAt))
                    "Thanks. We'll take a look."
                } catch (e: NotYetAvailable) { "Reporting is ${tr("common.soon").lowercase()}. You can still block them below." }
                catch (e: Exception) { e.message ?: "Couldn't send the report" }
            }
        }, enabled = reason != null, bg = Ember, fg = BoneBrand)
        Spacer(Modifier.height(8.dp))
        com.sohum.bandlog.ui.components.PillButton(if (blocked) tr("squad.unblock") else "${tr("squad.block")} ${post.authorName.substringBefore(' ')}", {
            if (blocked) {
                SocialStore.updateBlocked(SocialStore.blocked - post.userId)
                scope.launch { runCatching { SocialApi.unblock(post.userId) } }
                onDismiss()
            } else {
                SocialStore.setString("blocked-name-${post.userId}", post.authorName)
                SocialStore.updateBlocked(SocialStore.blocked + post.userId) // the device list works before schema_v45
                scope.launch { runCatching { SocialApi.block(post.userId) } }
                done = "Blocked. Their posts and messages are hidden. Undo in Profile → Social and rewards → Blocked people."
            }
        }, height = 46.dp)
    }
}

/** D3: the owner's request for a verified tick (an admin approves it on the web). */
@Composable
fun VerifySquadScreen(onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val (id, name) = SocialNav.squad ?: ("" to "Squad")
    val status = rememberLoad(id) { SocialApi.verificationStatus(id) }
    var org by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("gym") }
    var proof by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    com.sohum.bandlog.ui.components.SubPage("Verify $name", onBack) {
        if (status.missing) { SoonCard("Verified squads", "Verification requests open with the next server update."); return@SubPage }
        Hero("Verified squads", "Gym, college or office?", "A verified squad gets a gold tick and its organisation's name. The Locked In team checks every request.", gold = true)
        val st = status.data
        if (sent || st == "pending") { com.sohum.bandlog.ui.components.Card { Text("Request sent. We'll check it and add the tick.", fontSize = 15.sp, color = p.ink) }; return@SubPage }
        if (st == "approved") { com.sohum.bandlog.ui.components.Card { Text("This squad is verified.", fontSize = 15.sp, color = Gold) }; return@SubPage }
        if (st == "rejected") Text("The last request wasn't approved. You can send a new one.", fontSize = 13.sp, color = p.muted)
        com.sohum.bandlog.ui.components.Card {
            Text("Organisation", fontSize = 13.sp, color = p.muted)
            Spacer(Modifier.height(6.dp))
            SocialField(org, { org = it.take(80) }, "e.g. Gold's Gym Bandra, IIT Bombay")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gym" to "Gym", "college" to "College", "office" to "Office", "club" to "Club").forEach { (k, l) -> ChoicePill(l, kind == k, { kind = k }) }
            }
            Spacer(Modifier.height(10.dp))
            SocialField(proof, { proof = it.take(500) }, "How can we check? A website, an Instagram handle, an email domain", singleLine = false)
            com.sohum.bandlog.ui.components.ErrorNote(err)
            Spacer(Modifier.height(10.dp))
            com.sohum.bandlog.ui.components.PillButton("Send request", {
                if (org.trim().length < 2) { err = "Add the organisation's name"; return@PillButton }
                scope.launch {
                    err = null
                    try { SocialApi.requestVerification(id, org.trim(), kind, proof.trim()); sent = true }
                    catch (e: NotYetAvailable) { err = tr("common.soon") } catch (e: Exception) { err = e.message }
                }
            }, bg = Gold, fg = InkBrand)
        }
    }
}
