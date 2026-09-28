package com.sohum.bandlog.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.CoachView
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Names
import kotlinx.coroutines.launch

/** D4 (client side): let a trainer or dietitian read my logs; see and revoke who can. */
@Composable
fun CoachAccessScreen(onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val coaches = rememberLoad(tick) { SocialApi.myCoaches() }
    var username by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var revoke by remember { mutableStateOf<CoachView.CoachRow?>(null) }

    SubPage(tr("coach.title"), onBack, pro = true) {
        if (coaches.missing) { SoonCard(tr("coach.title"), "Coach access switches on with the next server update."); return@SubPage }
        Hero(tr("coach.title"), tr("coach.sub"), "They see your food, training, weight and notes, and can leave comments. You can remove them any time.")
        Card {
            Text("Add by username", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Spacer(Modifier.height(8.dp))
            SocialField(username, { username = it.take(24) }, "@username")
            ErrorNote(err)
            note?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.green) }
            Spacer(Modifier.height(8.dp))
            PillButton("Give access", {
                val u = CoachView.normalizeUsername(username)
                if (u == null) { err = "Usernames are 3 to 20 letters, numbers or _."; return@PillButton }
                scope.launch {
                    err = null
                    try { SocialApi.coachGrant(u); note = "@$u can now see your logs."; username = ""; tick++ }
                    catch (e: NotYetAvailable) { err = tr("common.soon") } catch (e: Exception) { err = e.message }
                }
            }, enabled = username.isNotBlank(), bg = Ember, fg = BoneBrand)
        }
        GroupLabel("Who can see my logs")
        Card {
            when {
                coaches.loading -> Spinner()
                coaches.data.isNullOrEmpty() -> Text("Nobody. Your logs are yours.", fontSize = 14.sp, color = p.muted)
                else -> coaches.data.forEachIndexed { i, c ->
                    if (i > 0) Hair()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.avatarUrl(c.avatarPath), Names.initials(c.name), 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 15.sp, color = p.ink)
                            Text(listOfNotNull(c.username?.let { "@$it" }, c.since.take(10).ifBlank { null }?.let { "since ${Dates.short(it)}" }).joinToString(" · "), fontSize = 12.sp, color = p.muted)
                        }
                        ChoicePill("Remove", false, { revoke = c })
                    }
                }
            }
        }
        Text(CoachView.COACH_TIER_NOTE, fontSize = 12.sp, color = Gold)
    }
    revoke?.let { c ->
        com.sohum.bandlog.ui.platform.ConfirmDialog(
            "Remove ${c.name}?", "They won't see your logs any more.", "Remove", danger = true,
            onConfirm = { revoke = null; scope.launch { runCatching { SocialApi.coachRevoke(c.coachId) }.onSuccess { tick++ }.onFailure { err = it.message } } },
            onDismiss = { revoke = null },
        )
    }
}

/** D4 (coach side): the people who gave me access. Paid plan later, free in the beta. */
@Composable
fun ClientsScreen(onBack: () -> Unit) {
    val p = palette
    val clients = rememberLoad(Unit) { SocialApi.myClients() }
    SubPage(tr("clients.title"), onBack, pro = true) {
        if (clients.missing) { SoonCard(tr("clients.title"), "The coach view switches on with the next server update."); return@SubPage }
        Hero(tr("clients.title"), "${clients.data?.size ?: 0} ${if (clients.data?.size == 1) "client" else "clients"}", CoachView.COACH_TIER_NOTE, gold = true)
        Card(padding = 0.dp) {
            when {
                clients.loading -> Spinner()
                clients.data.isNullOrEmpty() -> Text("Nobody yet. Ask a client to add your username under Coach access.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(16.dp))
                else -> clients.data.forEachIndexed { i, c ->
                    if (i > 0) Hair()
                    Row(
                        Modifier.fillMaxWidth().clickable { SocialNav.client = c.clientId to c.name; SocialNav.open(SocialPage.CLIENT) }.padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(Api.avatarUrl(c.avatarPath), Names.initials(c.name), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                            Text(
                                listOfNotNull("${c.streak}-day streak", c.weightKg?.let { "${com.sohum.bandlog.util.Wrapped.num(Math.round(it * 10) / 10.0)} kg" }, c.lastActive?.let { "active ${Dates.relative(it)}" }).joinToString(" · "),
                                fontSize = 12.sp, color = p.muted,
                            )
                        }
                        Text("›", fontSize = 18.sp, color = p.muted)
                    }
                }
            }
        }
    }
}

/** One client: the last 14 days (logs, weight, training), and my comments. */
@Composable
fun ClientScreen(onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val (id, name) = SocialNav.client ?: ("" to "Client")
    var tick by remember { mutableIntStateOf(0) }
    val ov = rememberLoad(id) { SocialApi.clientOverview(id, 14) }
    val comments = rememberLoad(id to tick) { SocialApi.coachComments(id) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val today = Dates.today()
    SubPage(name, onBack, pro = true) {
        if (ov.missing) { SoonCard(name); return@SubPage }
        val o = ov.data
        if (o == null) { if (ov.loading) Spinner() else ErrorNote(ov.error); return@SubPage }
        Hero("Last 7 days", CoachView.adherenceLine(o, today), listOfNotNull(o.goalType?.replaceFirstChar { it.uppercase() }, o.weightKg?.let { "${kgText(it)} kg" }, o.goalWeightKg?.let { "goal ${kgText(it)} kg" }).joinToString(" · "))
        GroupLabel("Days")
        Card {
            o.days.sortedByDescending { it.date }.take(14).forEachIndexed { i, d ->
                if (i > 0) Hair()
                LineRow(Dates.short(d.date), listOfNotNull("${d.meals} meals", "${d.calories.toInt()} kcal", "${d.proteinG.toInt()} g protein", if (d.trained) "trained" else null).joinToString(" · "))
            }
            if (o.days.isEmpty()) Text("Nothing logged in the last 14 days.", fontSize = 14.sp, color = p.muted)
        }
        if (o.meals.isNotEmpty()) {
            GroupLabel("Meals")
            Card { o.meals.take(20).forEachIndexed { i, m -> if (i > 0) Hair(); LineRow("${Dates.short(m.date)} · ${m.text.take(40)}", "${m.calories.toInt()} kcal") } }
        }
        if (o.weights.isNotEmpty()) {
            GroupLabel("Weight")
            Card { o.weights.sortedByDescending { it.date }.take(10).forEachIndexed { i, w -> if (i > 0) Hair(); LineRow(Dates.short(w.date), "${kgText(w.kg)} kg") } }
        }
        if (o.workouts.isNotEmpty()) {
            GroupLabel("Training")
            Card { o.workouts.take(12).forEachIndexed { i, w -> if (i > 0) Hair(); LineRow("${Dates.short(w.date)} · ${w.kind}", listOfNotNull(w.minutes?.let { "$it min" }, w.muscles.take(3).joinToString(", ").ifBlank { null }).joinToString(" · ")) } }
        }
        GroupLabel("Comments")
        Card {
            SocialField(text, { text = it.take(1000) }, "A note for $name", singleLine = false)
            ErrorNote(err)
            Spacer(Modifier.height(8.dp))
            PillButton("Send", {
                scope.launch {
                    err = null
                    try { SocialApi.addCoachComment(id, text.trim(), today); text = ""; tick++ }
                    catch (e: NotYetAvailable) { err = tr("common.soon") } catch (e: Exception) { err = e.message }
                }
            }, enabled = text.isNotBlank(), height = 44.dp)
            comments.data.orEmpty().forEach { c ->
                Hair()
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(c.body, fontSize = 14.sp, color = p.ink, lineHeight = 19.sp)
                    Text(c.createdAt.take(10).let { Dates.relative(it) }, fontSize = 11.sp, color = p.muted)
                }
            }
        }
        Text(CoachView.COACH_TIER_NOTE, fontSize = 12.sp, color = Gold)
    }
}

private fun kgText(kg: Double) = com.sohum.bandlog.util.Wrapped.num(Math.round(kg * 10) / 10.0)
