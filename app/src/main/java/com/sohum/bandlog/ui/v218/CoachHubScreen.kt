package com.sohum.bandlog.ui.v218

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.InsightsV
import com.sohum.bandlog.data.SupplementV
import com.sohum.bandlog.data.V218Api
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.Chip
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.onboarding.OnbIcons
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Cycle
import com.sohum.bandlog.util.Festival
import com.sohum.bandlog.util.Supplements
import java.time.LocalDate

/**
 * v2.18 coach hub (Android twin of web /coach/hub): today's check-in + recovery, the weekly
 * check-in (B2), plateau detective (B5), why the target changed in English / हिंदी (B3), the
 * consistency score (B11), festival & wedding mode (B9), private cycle guidance (B6), and the
 * doors to supplements, Indian fasts, home workouts, sports and form check.
 */
@Composable
fun CoachHubScreen(vm: AppViewModel, cp: CoachPlusViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { cp.loadInsights(generate = true); cp.loadModes() }
    SubPage("Coach hub", onBack) {
        CoachDailyCard()
        val i = cp.insights
        if (cp.insightsAvailable == null) Text("Reading your week…", fontSize = 13.sp, color = palette.muted, modifier = Modifier.padding(8.dp))
        if (i != null) {
            WeeklyCard(i)
            PlateauCard(i)
            if (i.whyEn.isNotEmpty()) WhyCard(i)
            ConsistencyCard(i, showLink = false)
        }
        FestivalCard(cp)
        if (vm.profile.gender != "male") CycleCard(cp)
        MoreCard()
    }
}

@Composable
private fun Head(icon: ImageVector, title: String, sub: String? = null, right: @Composable () -> Unit = {}) {
    val p = palette
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(p.card2, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = p.ink, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, lineHeight = 18.sp, color = p.muted)
        }
        right()
    }
}

@Composable
private fun WeeklyCard(i: InsightsV) {
    val w = i.weekly ?: return
    val p = palette
    Card(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Head(LineIcons.Chart, "Weekly check-in", "Week of ${runCatching { LocalDate.parse(w.weekStart).let { "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }}" } }.getOrDefault(w.weekStart)}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("${w.loggedDays}/7" to "days logged", (w.avgProtein?.let { "${it.toInt()} g" } ?: "—") to "avg protein", "${w.workouts}/${w.workoutTarget}" to "workouts", (w.avgSleep?.let { "%.1f h".format(it) } ?: "—") to "sleep").forEach { (v, l) ->
                    Column(Modifier.weight(1f).background(p.card2, RoundedCornerShape(12.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(l, fontSize = 10.sp, color = p.muted)
                    }
                }
            }
            Text(w.text, fontSize = 14.sp, lineHeight = 20.sp, color = p.ink)
            Column(Modifier.fillMaxWidth().background(p.irisBg, RoundedCornerShape(16.dp)).padding(14.dp, 12.dp)) {
                Text("Next week's one thing: ${w.planTitle}", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.iris)
                Text(w.plan, fontSize = 13.sp, lineHeight = 18.sp, color = p.ink)
            }
        }
    }
}

@Composable
private fun PlateauCard(i: InsightsV) {
    val pl = i.plateau ?: return
    val p = palette
    Card(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Head(LineIcons.Balance, "Plateau detective", "Your trend has been flat for ${pl.days}+ days (${if (pl.trend > 0) "+" else ""}${pl.trend} kg/week).")
            pl.causes.take(3).forEachIndexed { idx, c ->
                Column(Modifier.fillMaxWidth().background(if (idx == 0) p.emberBg else p.card2, RoundedCornerShape(16.dp)).padding(14.dp, 12.dp)) {
                    Text((if (idx == 0) "Most likely: " else "") + c.title, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text(c.detail, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.muted)
                    if (idx == 0) Text("Fix: ${c.fix}", fontSize = 13.sp, lineHeight = 18.sp, color = p.ink, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun WhyCard(i: InsightsV) {
    var hi by remember { mutableStateOf(false) }
    val p = palette
    Card(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Head(LineIcons.Target, if (i.whyOld == i.whyNew) "Your target is on track" else "Why your target changed", if (i.whyOld != null && i.whyNew != null) "${"%,d".format(i.whyOld)} → ${"%,d".format(i.whyNew)} kcal" else null) {
                Box(Modifier.height(32.dp).background(p.card2, CircleShape).clickable { hi = !hi }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(if (hi) "English" else "हिंदी", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink)
                }
            }
            (if (hi) i.whyHi else i.whyEn).forEachIndexed { n, l ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${n + 1}.", fontSize = 13.5.sp, fontWeight = FontWeight(600), color = p.muted)
                    Text(l, fontSize = 13.5.sp, lineHeight = 19.sp, color = p.ink)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FestivalCard(cp: CoachPlusViewModel) {
    val m = cp.modes ?: return
    val p = palette
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableStateOf(m.date) }
    var end by remember { mutableStateOf(m.date) }
    var err by remember { mutableStateOf<String?>(null) }
    fun pick(date: String, onPick: (String) -> Unit) {
        val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        DatePickerDialog(ctx, { _, y, mo, da -> onPick(LocalDate.of(y, mo + 1, da).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
    Card(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Head(OnbIcons.Star, "Festival & wedding mode", if (m.festivalAvailable) "Targets go to maintenance, your streak is protected, and the coach warns you a few days ahead." else "Coming with the next update")
            if (!m.festivalAvailable) return@Column
            m.modes.filter { it.endDate >= m.date }.forEach { f ->
                Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text("${f.startDate} → ${f.endDate}${if (f.startDate <= m.date) " · on now" else ""}", fontSize = 12.sp, color = p.muted)
                    }
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { cp.modesCall { V218Api.removeFestival(f.id) } })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (Festival.PRESETS.map { it.kind to it.name } + ("other" to "Other")).forEach { (k, n) ->
                    Chip(n, kind == k, {
                        if (kind == k) kind = null else {
                            kind = k
                            start = m.date
                            end = LocalDate.parse(m.date).plusDays(((Festival.PRESETS.firstOrNull { it.kind == k }?.days ?: 3) - 1).toLong()).toString()
                        }
                    })
                }
            }
            kind?.let { k ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).background(p.card2, RoundedCornerShape(14.dp)).clickable { pick(start) { start = it } }.padding(12.dp, 8.dp)) {
                        Text("From", fontSize = 11.sp, color = p.muted); Text(start, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                    }
                    Column(Modifier.weight(1f).background(p.card2, RoundedCornerShape(14.dp)).clickable { pick(end) { end = it } }.padding(12.dp, 8.dp)) {
                        Text("To", fontSize = 11.sp, color = p.muted); Text(end, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
                    }
                }
                Festival.PRESETS.firstOrNull { it.kind == k }?.let { Text(it.tip, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.muted) }
                ErrorNote(err ?: cp.error)
                PillButton("Turn on", {
                    err = Festival.validate(start, end, m.date)
                    if (err == null) {
                        val name = Festival.PRESETS.firstOrNull { it.kind == k }?.name ?: "Festival"
                        cp.modesCall { V218Api.addFestival(k, name, start, end) }
                        kind = null
                    }
                }, height = 46.dp)
            }
        }
    }
}

@Composable
private fun CycleCard(cp: CoachPlusViewModel) {
    val m = cp.modes ?: return
    if (!m.cycleAvailable) return
    val p = palette
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(m.cycle.lastPeriodStart ?: m.date) }
    var len by remember { mutableStateOf(m.cycle.cycleLength.toString()) }
    var per by remember { mutableStateOf(m.cycle.periodLength.toString()) }
    Card(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Head(OnbIcons.Lock, "Cycle-aware guidance", "Optional and private: only you and your own coach see it. It adjusts hunger expectations, water and training advice, never your targets.")
            when {
                editing -> {
                    Column(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(14.dp)).clickable {
                        val d = runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now())
                        DatePickerDialog(ctx, { _, y, mo, da -> start = LocalDate.of(y, mo + 1, da).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }.padding(12.dp, 8.dp)) { Text("Last period started", fontSize = 11.sp, color = p.muted); Text(start, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberBox("Cycle (days)", len, Modifier.weight(1f)) { len = it.filter(Char::isDigit).take(2) }
                        NumberBox("Period (days)", per, Modifier.weight(1f)) { per = it.filter(Char::isDigit).take(1) }
                    }
                    PillButton("Save", { cp.modesCall { V218Api.saveCycle(Cycle.Settings(true, start, len.toIntOrNull()?.coerceIn(21, 40) ?: 28, per.toIntOrNull()?.coerceIn(2, 9) ?: 5)) }; editing = false }, height = 46.dp)
                }
                m.cycle.enabled -> {
                    m.cycleToday?.let { c ->
                        Column(Modifier.fillMaxWidth().background(p.purpleBg, RoundedCornerShape(16.dp)).padding(14.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Day ${c.day} · ${c.label} · next period in ~${c.nextPeriodIn} days", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(c.hunger, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.ink)
                            Text(c.training, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.ink)
                            Text(c.scale, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.muted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Period started", { start = m.date; editing = true }, Modifier.weight(1f), height = 44.dp, bg = p.card2, fg = p.ink)
                        PillButton("Turn off", { cp.modesCall { V218Api.saveCycle(Cycle.Settings(false, null)) } }, Modifier.weight(1f), height = 44.dp, bg = p.card2, fg = p.ink)
                    }
                }
                else -> PillButton("Turn on", { editing = true }, height = 44.dp, bg = p.card2, fg = p.ink)
            }
        }
    }
}

@Composable
internal fun NumberBox(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val p = palette
    Row(modifier.heightIn(min = 46.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
        BasicTextField(value, onChange, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink), modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun MoreCard() {
    val p = palette
    Card(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(LineIcons.Drop, "Supplements" to "Doses, reminders, streak") { CoachPlusNav.open(CoachPlusPage.SUPPLEMENTS) },
                Triple(OnbIcons.Flame, "Indian fasts" to "Navratri, Ramadan, Ekadashi, Jain: in Fasting") { CoachPlusNav.closeAll(); com.sohum.bandlog.ui.nutrition.NutritionNav.openFastingTick++; Unit },
                Triple(OnbIcons.Home, "Home workouts" to "No-equipment and band plans: in Routines") { CoachPlusNav.closeAll(); com.sohum.bandlog.ui.platform.PlatformNav.open(com.sohum.bandlog.ui.platform.PlatformPage.ROUTINES) },
                Triple(LineIcons.Award, "Sports & steps" to "Cricket, football, badminton, kabaddi") { CoachPlusNav.open(CoachPlusPage.SPORTS) },
                Triple(OnbIcons.Camera, "Form check" to "Rep count + tips, on device") { CoachPlusNav.open(CoachPlusPage.FORM_CHECK) },
                Triple(OnbIcons.Mic, "Voice coach" to "Ask out loud, hear it back") { CoachPlusNav.openVoice() },
            ).forEach { (icon, text, go) ->
                Row(Modifier.fillMaxWidth().pressable().background(p.card2, RoundedCornerShape(16.dp)).clickable(onClick = go).padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, null, tint = p.ink, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text.first, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(text.second, fontSize = 11.5.sp, color = p.muted)
                    }
                    Icon(LineIcons.ChevronRight, null, tint = p.muted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B7 supplements
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplementsScreen(cp: CoachPlusViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { cp.loadSupplements() }
    var adding by remember { mutableStateOf<String?>(null) }
    SubPage("Supplements", onBack) {
        val s = cp.supplements
        when {
            cp.supplementsAvailable == false -> Card(padding = 18.dp) { Head(LineIcons.Info, "Coming with the next update", "The supplement tracker needs a quick server update. Everything else works as usual.") }
            s == null -> Text("Loading…", fontSize = 13.sp, color = p.muted)
            else -> {
                if (s.items.isEmpty()) Card(padding = 18.dp) { Head(LineIcons.Drop, "Track your supplements", "Tick them off each day, get a reminder, and keep the streak going.") }
                else Card(padding = 16.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                        if (s.allStreak > 0) Text("${s.allStreak}-day streak", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ember)
                    }
                    s.items.forEach { SupplementRow(cp, it) }
                }
                ErrorNote(cp.error)
                Card(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add one", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (Supplements.PRESETS.map { it.kind to it.name } + ("custom" to "Custom")).forEach { (k, n) -> Chip(n, adding == k, { adding = if (adding == k) null else k }) }
                        }
                        adding?.let { k -> AddSupplement(k, onCancel = { adding = null }) { name, dose, unit, time -> cp.supplementCall(ctx) { V218Api.addSupplement(k, name, dose, unit, time) }; adding = null } }
                    }
                }
                Text("Locked In doesn't recommend doses. Use what's on your label or what your doctor advised, and check with a doctor before starting iron or high-dose vitamins.", fontSize = 11.sp, lineHeight = 15.sp, color = p.muted)
            }
        }
    }
}

@Composable
private fun SupplementRow(cp: CoachPlusViewModel, s: SupplementV) {
    val p = palette
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).background(if (s.takenToday) p.green else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .then(if (s.takenToday) Modifier else Modifier.border(1.5.dp, p.hair, CircleShape))
                    .clickable(enabled = s.active) { cp.supplementCall(ctx) { V218Api.tick(s.id, !s.takenToday) } },
                contentAlignment = Alignment.Center,
            ) { if (s.takenToday) Icon(OnbIcons.Check, "Taken", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(listOfNotNull(Supplements.doseText(s.dose, s.unit).ifBlank { null }, s.remindAt?.let { "reminder $it" }, if (!s.active) "paused" else null).joinToString(" · ").ifBlank { "no dose set" }, fontSize = 12.sp, color = p.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${s.current}", fontSize = 15.sp, fontWeight = FontWeight(800), color = if (s.current > 0) p.ember else p.muted)
                Text("best ${s.best}", fontSize = 10.sp, color = p.muted)
            }
            Icon(LineIcons.Sliders, "More", tint = p.muted, modifier = Modifier.size(18.dp).clickable { menu = !menu })
        }
        if (menu) Row(Modifier.padding(start = 56.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (s.active) "Pause" else "Resume", false, { cp.supplementCall(ctx) { V218Api.setActive(s, !s.active) } })
            Chip("Remove", false, { cp.supplementCall(ctx) { V218Api.removeSupplement(s.id) } })
        }
    }
}

@Composable
private fun AddSupplement(kind: String, onCancel: () -> Unit, onSave: (String, Double?, String, String?) -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val preset = Supplements.PRESETS.firstOrNull { it.kind == kind }
    var name by remember(kind) { mutableStateOf(preset?.name ?: "") }
    var dose by remember(kind) { mutableStateOf("") }
    var unit by remember(kind) { mutableStateOf(preset?.unit ?: "serving") }
    var time by remember(kind) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        preset?.let { Text(it.hint, fontSize = 12.5.sp, lineHeight = 17.sp, color = p.muted) }
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Name", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
            BasicTextField(name, { name = it.take(40) }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = p.ink), modifier = Modifier.width(170.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberBox("Dose", dose, Modifier.weight(1f)) { dose = it.filter { c -> c.isDigit() || c == '.' }.take(7) }
            Row(Modifier.weight(1f).heightIn(min = 46.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Unit", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                BasicTextField(unit, { unit = it.take(12) }, singleLine = true, textStyle = TextStyle(fontSize = 15.sp, color = p.ink), modifier = Modifier.width(64.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).background(p.card2, RoundedCornerShape(14.dp)).clickable {
            TimePickerDialog(ctx, { _, h, mi -> time = "%02d:%02d".format(h, mi) }, 8, 0, true).show()
        }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily reminder", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
            Text(time ?: "Off", fontSize = 14.sp, fontWeight = FontWeight(600), color = if (time == null) p.muted else p.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancel", onCancel, Modifier.weight(1f), height = 44.dp, bg = p.card2, fg = p.ink)
            PillButton("Add", { onSave(name.trim(), dose.toDoubleOrNull(), unit.trim().ifBlank { "serving" }, time) }, Modifier.weight(1f), height = 44.dp, enabled = name.trim().length >= 2)
        }
    }
}
