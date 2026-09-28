package com.sohum.bandlog.ui.v218

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.DailyV
import com.sohum.bandlog.data.InsightsV
import com.sohum.bandlog.data.SupplementV
import com.sohum.bandlog.data.V218Api
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.onboarding.OnbIcons
import com.sohum.bandlog.ui.theme.Brand
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.DailyCheckin
import com.sohum.bandlog.util.HealthRecovery
import com.sohum.bandlog.util.Supplements
import kotlinx.coroutines.launch

/**
 * v2.18 Today: the coach's daily card. Before the check-in, the 5-second tap (B4: sleep, stress,
 * mood); after, what changed today (target buffer, training advice), the recovery score (B8, with
 * Health Connect sleep / resting HR when connected), festival mode (B9), the private cycle day
 * (B6) and supplements as tap-to-tick chips (B7). Hidden until /api/coach/daily answers.
 */
@Composable
fun CoachDailyCard() {
    val cp: CoachPlusViewModel = viewModel()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { cp.loadDaily(); com.sohum.bandlog.alarm.SupplementAlarms.rearm(ctx) }
    val d = cp.daily ?: return
    if (cp.dailyAvailable != true) return
    var editing by rememberSaveable { mutableStateOf(false) }
    var skipped by rememberSaveable(d.date) { mutableStateOf(false) }
    val supps = d.supplements?.items?.filter { it.active }.orEmpty()
    if (!d.checkinAvailable && d.festivalLine == null && supps.isEmpty()) return
    val p = palette
    Card(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            d.festivalLine?.let { FestivalBanner(d, it) }
            when {
                d.checkinAvailable && (editing || (d.checkin == null && !skipped)) -> CheckinTap(cp, d, onDone = { editing = false }, onSkip = { skipped = true; editing = false })
                d.checkin != null -> Readout(d) { editing = true }
                d.checkinAvailable -> Row(
                    Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).clickable { editing = true }.padding(14.dp, 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) { Text("How did you sleep?", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink); Text("5-second check-in", fontSize = 12.sp, color = p.muted) }
            }
            d.cycle?.let { c ->
                Row(Modifier.fillMaxWidth().background(p.purpleBg, RoundedCornerShape(14.dp)).padding(12.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(OnbIcons.Lock, null, tint = p.purple, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Text("Day ${c.day} · ${c.label}. ${c.hunger}${if (c.waterMl > 0) " Add about ${c.waterMl} ml water today." else ""}", fontSize = 12.5.sp, lineHeight = 17.sp, color = p.ink)
                }
            }
            if (supps.isNotEmpty()) SupplementChips(cp, supps, d.supplements?.allStreak ?: 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.height(38.dp).pressable().background(p.irisBg, CircleShape).clickable { CoachPlusNav.openVoice() }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Icon(OnbIcons.Mic, null, tint = p.iris, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text("Talk to coach", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.iris) }
                Row(
                    Modifier.height(38.dp).pressable().background(p.card2, CircleShape).clickable { CoachPlusNav.open(CoachPlusPage.HUB) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("Coach hub", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink); Spacer(Modifier.width(4.dp)); Icon(LineIcons.ChevronRight, null, tint = p.ink, modifier = Modifier.size(13.dp)) }
            }
        }
    }
}

@Composable
private fun FestivalBanner(d: DailyV, line: String) {
    val p = palette
    val gold = Color(0xFFB8893B)
    Column(Modifier.fillMaxWidth().background(gold.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).border(1.dp, gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(14.dp, 12.dp)) {
        Text(d.festivalActive?.let { "${it.name} mode" } ?: "${d.festivalUpcoming ?: "Festival"} is coming", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
        Text(line, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.ink)
    }
}

@Composable
private fun Readout(d: DailyV, onEdit: () -> Unit) {
    val p = palette
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        d.recovery?.let { r ->
            val tone = when (r.band) { "push" -> p.green; "deload" -> Brand.EmberDeep; else -> p.ember }
            Box(Modifier.size(56.dp).background(p.card2, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.score}", fontSize = 20.sp, fontWeight = FontWeight(800), color = tone)
                    Text(r.label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight(700), color = tone, letterSpacing = 0.6.sp)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Today's check-in", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
            Text(d.summary.ifBlank { "Logged. Train as planned." }, fontSize = 13.5.sp, lineHeight = 19.sp, color = p.ink)
            if (d.bump > 0) Text("Target today ${"%,d".format(d.kcal)} kcal (+${d.bump})", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ember)
        }
        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable(onClick = onEdit))
    }
}

@Composable
private fun ScaleRow(label: String, options: List<String>, value: Int?, onPick: (Int) -> Unit) {
    val p = palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                val on = value == i + 1
                Box(
                    Modifier.weight(1f).height(38.dp).background(if (on) p.btn else p.card2, RoundedCornerShape(12.dp)).clickable { onPick(i + 1) },
                    contentAlignment = Alignment.Center,
                ) { Text(o, fontSize = 12.sp, fontWeight = FontWeight(600), color = if (on) p.btnInk else p.ink, maxLines = 1, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun CheckinTap(cp: CoachPlusViewModel, d: DailyV, onDone: () -> Unit, onSkip: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sleep by remember { mutableStateOf(d.checkin?.sleepHours) }
    var stress by remember { mutableStateOf(d.checkin?.stress) }
    var mood by remember { mutableStateOf(d.checkin?.mood) }
    var hcAsked by remember { mutableStateOf(false) }
    val hcAvailable = remember { HealthRecovery.available(ctx) }
    var hcGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (hcAvailable) hcGranted = HealthRecovery.granted(ctx) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        hcGranted = granted.any { it in HealthRecovery.PERMISSIONS }
    }
    fun save(m: Int? = mood) = cp.checkin(ctx, sleep, stress, m, onDone)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Quick check-in", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Not today", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable(onClick = onSkip))
        }
        ScaleRow("Sleep last night", DailyCheckin.SLEEP_CHOICES.map { if (it == 5) "≤5 h" else if (it == 9) "9+ h" else "$it h" }, DailyCheckin.SLEEP_CHOICES.indexOf(sleep?.toInt() ?: -1).takeIf { it >= 0 }?.plus(1)) { sleep = DailyCheckin.SLEEP_CHOICES[it - 1].toDouble() }
        ScaleRow("Stress", DailyCheckin.STRESS_LABELS, stress) { stress = it }
        ScaleRow("Mood", DailyCheckin.MOOD_LABELS, mood) { mood = it; if (sleep != null && stress != null) save(it) }
        if (hcAvailable && !hcGranted && !hcAsked) {
            Text(
                "Use Health Connect sleep and resting heart rate for a better recovery score",
                fontSize = 12.sp, fontWeight = FontWeight(600), color = p.iris,
                modifier = Modifier.clickable { hcAsked = true; scope.launch { runCatching { hcLauncher.launch(HealthRecovery.PERMISSIONS) } } },
            )
        }
        ErrorNote(cp.error)
        PillButton(if (cp.saving) "Saving…" else "Done", { save() }, height = 44.dp, enabled = !cp.saving && (sleep != null || stress != null || mood != null || hcGranted))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplementChips(cp: CoachPlusViewModel, items: List<SupplementV>, allStreak: Int) {
    val p = palette
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Supplements", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { CoachPlusNav.open(CoachPlusPage.SUPPLEMENTS) })
            if (allStreak > 1) Text("$allStreak days, all taken", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ember)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { s ->
                Row(
                    Modifier.height(36.dp).background(if (s.takenToday) p.greenBg else p.card2, CircleShape)
                        .then(if (s.due) Modifier.border(1.dp, p.ember, CircleShape) else Modifier)
                        .clickable { cp.supplementCall(ctx) { V218Api.tick(s.id, !s.takenToday) } }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s.takenToday) { Icon(OnbIcons.Check, null, tint = p.green, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(s.name, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (s.takenToday) p.green else p.ink)
                    Supplements.doseText(s.dose, s.unit).takeIf { it.isNotBlank() }?.let { Spacer(Modifier.width(4.dp)); Text(it, fontSize = 11.sp, color = p.muted) }
                }
            }
        }
    }
}

/** v2.18 B11 consistency score for Profile and the hub. Shareable as plain text through the share sheet. */
@Composable
fun ConsistencyCard(insights: InsightsV? = null, showLink: Boolean = true) {
    val cp: CoachPlusViewModel = viewModel()
    LaunchedEffect(Unit) { if (insights == null && cp.insights == null) cp.loadInsights(false) }
    val i = insights ?: cp.insights ?: return
    val p = palette
    val ctx = LocalContext.current
    Card(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.sohum.bandlog.ui.components.Ring(i.score / 100f, p.ember, 72.dp, 7.dp) {
                    Text("${i.score}", fontSize = 20.sp, fontWeight = FontWeight(800), color = p.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text("CONSISTENCY · 14 DAYS", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.muted, letterSpacing = 0.8.sp)
                    Text(i.label, fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(i.tip, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                i.parts.forEach { part ->
                    Column(Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${part.pct}%", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(part.label, fontSize = 10.5.sp, color = p.muted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(36.dp).pressable().background(p.card2, CircleShape).clickable {
                        val text = "My Locked In consistency score: ${i.score}/100 (${i.label}) over the last 14 days. Logging, protein, training${if (i.parts.size > 3) " and sleep" else ""}."
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        ctx.startActivity(Intent.createChooser(send, "Share your score").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Icon(LineIcons.Share, null, tint = p.ink, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Share", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink) }
                if (showLink) Text("See what's behind it", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { CoachPlusNav.open(CoachPlusPage.HUB) })
            }
        }
    }
}
