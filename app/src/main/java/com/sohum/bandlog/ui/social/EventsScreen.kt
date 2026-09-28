package com.sohum.bandlog.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.progress.Jewel
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.HealthExtras
import com.sohum.bandlog.util.Jewels
import com.sohum.bandlog.util.Seasonal

/** The data the seasonal events count, from what's on this phone (plus Health Connect steps when read). */
internal fun eventData(vm: AppViewModel): Seasonal.Data {
    val target = vm.profile.proteinTargetG
    val mealDays = vm.meals.map { it.date }.distinct()
    val steps = HashMap<String, Long>()
    vm.exercises.forEach { e -> e.steps?.let { steps[e.date] = (steps[e.date] ?: 0L) + it } }
    HealthExtras.stepsByDay.forEach { (d, n) -> steps[d] = maxOf(steps[d] ?: 0L, n) }
    vm.healthToday?.let { h -> val t = Dates.today(); steps[t] = maxOf(steps[t] ?: 0L, h.steps) }
    return Seasonal.Data(
        proteinDays = mealDays.filter { target > 0 && totalsFor(vm.meals, it).protein >= target * 0.9 },
        stepsByDay = steps,
        logDays = mealDays,
        trainDays = (vm.workouts.map { it.date } + vm.exercises.filter { it.source != "workout" && it.source != "health" }.map { it.date }).distinct(),
    )
}

/** Inserts event_badges for any live (or just-ended) event whose goal is met. Quiet on a missing table. */
internal suspend fun checkEventBadges(vm: AppViewModel) {
    val today = Dates.today()
    val y = today.take(4).toInt()
    val candidates = (Seasonal.eventsForYear(y) + Seasonal.eventsForYear(y - 1)).filter { it.from <= today && Dates.daysBetween(it.to, today) <= 14 }
    if (candidates.isEmpty()) return
    val have = SocialApi.myEventBadges()
    val data = eventData(vm)
    candidates.filter { it.id !in have && Seasonal.eventProgress(it, data).done }.forEach { SocialApi.earnEventBadge(it.id) }
}

/** D9 seasonal events: what's on now, what's next, and the limited-edition jewels earned. */
@Composable
fun EventsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val today = Dates.today()
    var tick by remember { mutableIntStateOf(0) }
    val around = remember(today) { Seasonal.eventsAround(today) }
    val earned = rememberLoad(tick) { runCatching { checkEventBadges(vm) }; SocialApi.myEventBadges() }
    val data = remember(vm.meals, vm.workouts, vm.exercises) { eventData(vm) }

    SubPage(tr("events.title"), onBack) {
        Hero(tr("events.title"), tr("events.sub"), "Each year's jewel is its own limited edition.", gold = true)
        if (earned.missing) SoonCard("Event jewels", "Your progress counts now; the jewels save with the next server update.")
        GroupLabel("On now")
        if (around.live.isEmpty()) Card { Text("Nothing live right now.", fontSize = 14.sp, color = p.muted) }
        around.live.forEach { EventCard(it, Seasonal.eventProgress(it, data), today, it.id in earned.data.orEmpty()) }
        if (around.soon.isNotEmpty()) {
            GroupLabel("Coming up")
            around.soon.forEach { EventCard(it, null, today, false) }
        }
        val mine = earned.data.orEmpty().mapNotNull { Seasonal.eventById(it) }
        if (mine.isNotEmpty()) {
            GroupLabel("Your limited editions")
            Card {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    mine.forEach { e ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Jewel(Jewels.Category.SPECIAL, Jewels.Tier.GOLD, 64.dp, shape = e.badge.shape, metal = e.badge.metal, gem = e.badge.gem, label = e.badge.name)
                            Text(e.badge.name, fontSize = 11.sp, color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(e: Seasonal.Event, prog: Seasonal.Progress?, today: String, earned: Boolean) {
    val p = palette
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Jewel(Jewels.Category.SPECIAL, Jewels.Tier.GOLD, 58.dp, locked = !earned, progress = prog?.fraction?.toFloat() ?: 0f, shape = e.badge.shape, metal = e.badge.metal, gem = e.badge.gem, label = e.badge.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(Seasonal.eventWhen(e, today), fontSize = 12.sp, fontWeight = FontWeight(600), color = if (today < e.from) p.muted else Ember)
                Text(e.blurb, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
            }
        }
        if (prog != null) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(p.track, CircleShape)) {
                Box(Modifier.fillMaxWidth(prog.fraction.toFloat().coerceIn(0f, 1f)).height(6.dp).background(if (prog.done) Gold else Ember, CircleShape))
            }
            Spacer(Modifier.height(6.dp))
            Text(if (earned) "Earned: ${e.badge.name}" else prog.line, fontSize = 12.sp, color = if (earned) Gold else p.muted)
        }
    }
}
