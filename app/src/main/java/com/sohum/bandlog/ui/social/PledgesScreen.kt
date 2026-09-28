package com.sohum.bandlog.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Pledges
import kotlinx.coroutines.launch

/** The days that count for a pledge kind, from what's logged on this phone. */
internal fun countedDays(vm: AppViewModel, kind: String): List<String> = when (kind) {
    "log_days" -> vm.meals.map { it.date }.distinct()
    "train_days" -> (vm.workouts.map { it.date } + vm.exercises.filter { it.source != "workout" && it.source != "health" }.map { it.date }).distinct()
    "protein_days" -> {
        val target = vm.profile.proteinTargetG
        vm.meals.map { it.date }.distinct().filter { target > 0 && totalsFor(vm.meals, it).protein >= target * 0.9 }
    }
    else -> emptyList()
}

/** D8 pledges: mine (or one squad's), a new pledge, and marking a custom one kept / broken. Honour system. */
@Composable
fun PledgesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val today = Dates.today()
    val squad = SocialNav.squad
    var tick by remember { mutableIntStateOf(0) }
    val list = rememberLoad(squad?.first to tick) { if (squad != null) SocialApi.squadPledges(squad.first) else SocialApi.myPledges() }
    val squads = rememberLoad(Unit) { Api.mySquads() }
    var creating by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    SubPage(squad?.let { "${tr("pledges.title")} · ${it.second}" } ?: tr("pledges.title"), onBack) {
        if (list.missing) { SoonCard(tr("pledges.title"), "Pledges switch on with the next server update."); return@SubPage }
        Hero(tr("pledges.title"), tr("pledges.sub"), "Say it in front of the squad. The stake is on your honour.")
        ErrorNote(err ?: list.error)
        if (!creating) PillButton("New pledge", { creating = true }, bg = Ember, fg = BoneBrand)
        else PledgeForm(vm, squads.data.orEmpty().map { it.id to it.name }, squad?.first, onCancel = { creating = false }) { draft, group ->
            scope.launch {
                err = null
                try { SocialApi.createPledge(draft, group); creating = false; tick++ }
                catch (e: NotYetAvailable) { err = tr("common.soon") } catch (e: Exception) { err = e.message }
            }
        }
        GroupLabel(if (squad != null) "In this squad" else "My pledges")
        when {
            list.loading -> Spinner()
            list.data.isNullOrEmpty() -> Card { Text("No pledges yet.", fontSize = 14.sp, color = p.muted) }
            else -> list.data.forEach { pl ->
                val mine = pl.userId == Session.userId
                val prog = Pledges.progress(pl, if (mine) countedDays(vm, pl.kind) else emptyList(), today)
                Card {
                    if (!mine) Text(pl.name ?: "Squadmate", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.muted)
                    Text(pl.goal, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("${Dates.short(pl.startsOn)} – ${Dates.short(pl.endsOn)}", fontSize = 12.sp, color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    Text(Pledges.stakeLine(pl.stake, pl.stakeInr), fontSize = 13.sp, color = p.ink)
                    Spacer(Modifier.height(8.dp))
                    val outcome = if (pl.status == "active") prog.outcome else pl.status
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinePill(outcome.uppercase(), when (outcome) { "kept" -> p.green; "broken" -> p.red; else -> Ember })
                        Spacer(Modifier.padding(4.dp))
                        if (pl.kind != "custom" && mine) Text("${prog.done} of ${prog.needed} days · ${prog.daysLeft} left" + if (outcome == "active" && !prog.onTrack) " · behind" else "", fontSize = 12.sp, color = p.muted)
                    }
                    if (pl.kind != "custom" && mine && prog.needed > 0) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).background(p.track, CircleShape)) {
                            Box(Modifier.fillMaxWidth((prog.done.toFloat() / prog.needed).coerceIn(0f, 1f)).height(6.dp).background(Ember, CircleShape))
                        }
                    }
                    if (mine && pl.status == "active") {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val suggested = if (pl.kind != "custom" && prog.outcome != "active") prog.outcome else null
                            ChoicePill(if (suggested == "kept") "Close as kept" else "I kept it", suggested == "kept", { scope.launch { runCatching { SocialApi.setPledgeStatus(pl.id, "kept") }.onSuccess { tick++ }.onFailure { err = it.message } } })
                            ChoicePill("I broke it", suggested == "broken", { scope.launch { runCatching { SocialApi.setPledgeStatus(pl.id, "broken") }.onSuccess { tick++ }.onFailure { err = it.message } } })
                            ChoicePill("Cancel", false, { scope.launch { runCatching { SocialApi.setPledgeStatus(pl.id, "cancelled") }.onSuccess { tick++ }.onFailure { err = it.message } } })
                        }
                    }
                    if (pl.stakeInr > 0 && pl.status == "broken") {
                        Spacer(Modifier.height(8.dp))
                        Text("Pay ${com.sohum.bandlog.util.Money.inr(pl.stakeInr)} into the squad pot · Coming soon", fontSize = 12.sp, fontWeight = FontWeight(600), color = Gold)
                    }
                }
            }
        }
        Text(Pledges.MONEY_STEP_NOTE, fontSize = 12.sp, color = p.muted, lineHeight = 17.sp)
    }
}

@Composable
private fun PledgeForm(vm: AppViewModel, squads: List<Pair<String, String>>, preset: String?, onCancel: () -> Unit, onSave: (Pledges.Draft, String?) -> Unit) {
    val p = palette
    val today = Dates.today()
    var kind by remember { mutableStateOf("log_days") }
    var days by remember { mutableStateOf("7") }
    var target by remember { mutableStateOf("6") }
    var goal by remember { mutableStateOf("") }
    var stake by remember { mutableStateOf("") }
    var inr by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(preset ?: squads.firstOrNull()?.first) }
    var err by remember { mutableStateOf<String?>(null) }
    Card {
        Text("What are you pledging?", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pledges.KINDS.forEach { k -> ChoicePill(Pledges.KIND_LABEL[k].orEmpty().removeSuffix(" on"), kind == k, { kind = k }) }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == "custom") SocialField(goal, { goal = it.take(120) }, "e.g. No sugar after 8 pm")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialField(days, { days = it.filter(Char::isDigit).take(2) }, "Days", Modifier.weight(1f), KeyboardType.Number, label = "Days")
                if (kind != "custom") SocialField(target, { target = it.filter(Char::isDigit).take(2) }, "Target days", Modifier.weight(1f), KeyboardType.Number, label = "Target days")
            }
            if (kind != "custom") Text(Pledges.goalText(kind, target.toIntOrNull(), days.toIntOrNull() ?: 7, ""), fontSize = 13.sp, color = p.muted)
            SocialField(stake, { stake = it.take(120) }, "Stake, e.g. chai for the squad")
            SocialField(inr, { inr = it.filter(Char::isDigit).take(6) }, "₹ into the squad pot (optional)", keyboard = KeyboardType.Number, label = "Rupees")
        }
        if (squads.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Show it to", fontSize = 13.sp, color = p.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("Just me", group == null, { group = null })
                squads.forEach { (id, name) -> ChoicePill(name, group == id, { group = id }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Money step: ${tr("common.soon")}. ${Pledges.MONEY_STEP_NOTE}", fontSize = 12.sp, color = Gold, lineHeight = 16.sp)
        ErrorNote(err)
        Spacer(Modifier.height(8.dp))
        PillButton(tr("common.save"), {
            val d = Pledges.Draft(goal, kind, if (kind == "custom") null else target.toIntOrNull(), stake, inr.toIntOrNull() ?: 0, today, days.toIntOrNull() ?: 0)
            val check = Pledges.validate(if (kind == "custom") d else d.copy(goal = Pledges.goalText(kind, d.target, d.days, "")), today)
            if (check != null) err = check else onSave(d, group)
        }, bg = Ember, fg = BoneBrand)
        Text(
            tr("common.cancel"), fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onCancel).padding(vertical = 10.dp),
        )
    }
}
