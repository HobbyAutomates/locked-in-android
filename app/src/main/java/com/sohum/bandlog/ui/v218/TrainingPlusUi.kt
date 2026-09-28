package com.sohum.bandlog.ui.v218

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.nutrition.NutritionViewModel
import com.sohum.bandlog.ui.onboarding.OnbIcons
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.FastingPresets
import com.sohum.bandlog.util.Sports
import kotlinx.coroutines.launch
import java.time.LocalTime

// ---------------------------------------------------------------------------------------------
// B10 Indian fasting presets (inside the Fasting screen, when no fast is running)
// ---------------------------------------------------------------------------------------------

private const val CITY_PREF = "v218_fast_city"

@Composable
fun IndianFastsCard(nvm: NutritionViewModel) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("v218", Context.MODE_PRIVATE) }
    var cityKey by remember { mutableStateOf(prefs.getString(CITY_PREF, "delhi") ?: "delhi") }
    val city = FastingPresets.CITIES.firstOrNull { it.key == cityKey } ?: FastingPresets.CITIES[0]
    var open by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val today = Dates.today()
    val nowMin = LocalTime.now(Dates.ZONE).let { it.hour * 60 + it.minute }
    Card(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Indian fasts", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Vrat and roza timings for today, with what's usually eaten.", fontSize = 12.5.sp, color = p.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FastingPresets.CITIES.forEach { c -> Chip(c.name, c.key == city.key, { cityKey = c.key; prefs.edit().putString(CITY_PREF, c.key).apply() }) }
            }
            FastingPresets.PRESETS.forEach { pr ->
                val w = FastingPresets.window(pr, today, city)
                val on = open == pr.key
                Column(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).clickable { open = if (on) null else pr.key }.padding(14.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.name, fontSize = 14.5.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(pr.sub, fontSize = 12.sp, color = p.muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${FastingPresets.window(pr, today, city).hours.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} h", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(if (w.startMin >= 0) w.label else "your start", fontSize = 11.sp, color = p.muted)
                        }
                    }
                    if (on) {
                        if (w.sehri != null) Text("Sehri ends ~${w.sehri} · Iftar ~${w.iftar} (${city.name}, approximate: follow your local calendar)", fontSize = 13.sp, color = p.ink)
                        else if (w.startMin >= 0) Text("Today: ${w.label} (${city.name}, approximate)", fontSize = 13.sp, color = p.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Usually eaten", fontSize = 12.5.sp, fontWeight = FontWeight(700), color = p.green)
                                pr.eat.forEach { Text("· $it", fontSize = 12.5.sp, lineHeight = 17.sp, color = p.ink) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Usually avoided", fontSize = 12.5.sp, fontWeight = FontWeight(700), color = p.muted)
                                pr.avoid.forEach { Text("· $it", fontSize = 12.5.sp, lineHeight = 17.sp, color = p.muted) }
                            }
                        }
                        Text(pr.note, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.ink)
                        PillButton(if (busy) "Starting…" else "Start ${pr.name.lowercase()} timer", {
                            val back = FastingPresets.backdateMin(w, nowMin)
                            scope.launch { busy = true; nvm.startFast(ctx, w.hours, System.currentTimeMillis() - back * 60_000L); busy = false }
                        }, height = 46.dp, enabled = !busy)
                    }
                }
            }
            Text("Practice varies by family and community; adjust the hours if yours differ. Skip nirjala (no-water) fasts on hard training days.", fontSize = 11.sp, lineHeight = 15.sp, color = p.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// C3 sports presets + C2 form check links (Routines screen, coach hub)
// ---------------------------------------------------------------------------------------------

@Composable
fun TrainingPlusLinks() {
    val p = palette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(OnbIcons.Camera, "Form check", CoachPlusPage.FORM_CHECK), Triple(LineIcons.Award, "Sports & steps", CoachPlusPage.SPORTS)).forEach { (icon, label, page) ->
            Row(
                Modifier.weight(1f).pressable().background(p.card, RoundedCornerShape(16.dp)).clickable { CoachPlusNav.open(page) }.padding(14.dp, 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(icon, null, tint = p.ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(label, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val prof = vm.profile
    val weight = prof.weightKg
    val height = prof.heightCm
    val hide = prof.hideNumbers == true
    var sportKey by remember { mutableStateOf(Sports.SPORTS[0].key) }
    val sport = Sports.SPORTS.first { it.key == sportKey }
    var variantKey by remember(sportKey) { mutableStateOf(sport.variants[0].key) }
    val v = sport.variants.firstOrNull { it.key == variantKey } ?: sport.variants[0]
    var minutes by remember(sportKey, variantKey) { mutableStateOf(v.minutes.toString()) }
    var steps by remember { mutableStateOf("") }
    var pace by remember { mutableStateOf("brisk") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    fun go(what: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true; err = null; msg = null
            try { block(); msg = "Logged $what."; vm.refresh() } catch (e: Exception) { err = e.message ?: "Couldn't log it" } finally { busy = false }
        }
    }
    SubPage("Sports & steps", onBack) {
        Card(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sports.SPORTS.forEach { s -> Chip(s.name, s.key == sportKey, { sportKey = s.key }) }
                }
                sport.variants.forEach { x ->
                    val on = x.key == v.key
                    Row(
                        Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).then(if (on) Modifier.border(1.5.dp, p.ink, RoundedCornerShape(16.dp)) else Modifier).clickable { variantKey = x.key }.padding(14.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(x.label, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(x.note, fontSize = 11.5.sp, lineHeight = 15.sp, color = p.muted)
                        }
                        Text("MET ${x.met}", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                    }
                }
                NumberBox("Minutes", minutes) { minutes = it.filter(Char::isDigit).take(3) }
                val mins = minutes.toIntOrNull() ?: 0
                if (!hide) Text("≈ ${Sports.kcal(v.met, weight, mins).toInt()} kcal at ${weight?.toInt() ?: 60} kg", fontSize = 13.sp, color = p.muted)
                PillButton(if (busy) "Logging…" else "Log ${sport.name.lowercase()}", {
                    go("${sport.name}, $mins min") {
                        Api.saveExercise(Dates.today(), v.code, "${sport.name} · ${v.label}", mins.coerceIn(5, 300), if (v.met >= 7) "high" else if (v.met >= 5) "medium" else "low", Sports.kcal(v.met, weight, mins.coerceIn(5, 300)), "manual")
                    }
                }, height = 46.dp, enabled = !busy && mins >= 5)
            }
        }
        Card(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Steps", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                NumberBox("Steps", steps) { steps = it.filter(Char::isDigit).take(6) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Sports.PACES.forEach { pc -> Chip(pc.label, pc.key == pace, { pace = pc.key }) } }
                val n = steps.toIntOrNull() ?: 0
                val b = Sports.steps(n, weight, height, pace)
                if (n > 0) Text("≈ ${b.km} km · ${b.minutes} min${if (!hide) " · ${b.kcal.toInt()} kcal above resting" else ""}", fontSize = 13.sp, color = p.muted)
                PillButton(if (busy) "Logging…" else "Log steps", {
                    go("%,d steps".format(n)) {
                        Api.saveExercise(Dates.today(), "LI-17190", "Walking (steps)", b.minutes, if (pace == "fast") "high" else if (pace == "easy") "low" else "medium", b.kcal, "manual", extras = Api.ExerciseExtras(steps = n, distanceKm = b.km))
                    }
                }, height = 46.dp, enabled = !busy && n in 100..100000)
            }
        }
        ErrorNote(err)
        msg?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.green, modifier = Modifier.padding(horizontal = 4.dp)) }
        Text("Burn = MET × body weight × time (2024 Compendium of Physical Activities). Kabaddi has no Compendium entry, so it's priced like similar team drills.", fontSize = 11.sp, lineHeight = 15.sp, color = p.muted)
    }
}
