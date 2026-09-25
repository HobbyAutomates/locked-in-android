package com.sohum.bandlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.log.NumberField
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Bmi
import com.sohum.bandlog.util.Goals
import com.sohum.bandlog.util.TargetEdits
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * v2.10 science UI shared by onboarding, Goal & current weight, Nutrition goals, Profile and
 * Progress — the Compose twin of the web's src/components/Science.tsx: the ⓘ "The science" sheet,
 * the kind safety note with helplines, the under-18 note and one-time goal migration card, and
 * the BMI card.
 */

// ---------------------------------------------------------------- "The science"

/** A small circled-i button that opens the sources sheet. */
@Composable
fun ScienceButton() {
    val p = palette
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(28.dp).background(p.card2, CircleShape).clickable { open = true },
        contentAlignment = Alignment.Center,
    ) { Icon(InfoIcon, "The science", tint = p.muted, modifier = Modifier.size(16.dp)) }
    if (open) ScienceSheet { open = false }
}

@Composable
fun ScienceSheet(onDismiss: () -> Unit) {
    val p = palette
    BottomSheet("The science", onDismiss, subtitle = "Where your numbers come from. General guidance, not medical advice.") {
        Goals.SCIENCE_SOURCES.forEach { (title, body) ->
            Text(title, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
            Text(body, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        }
    }
}

// ---------------------------------------------------------------- safety note

/**
 * The non-blocking note shown when a safety flag trips: what the app did, why, and people to talk
 * to. Never a popup; the save it accompanies has already gone through. The optional "hide numbers"
 * offer is a tap the person chooses (the web links to Preferences → Tracking instead).
 */
@Composable
fun SafetyNote(vm: AppViewModel, flags: List<String>, floor: Int? = null, onClose: (() -> Unit)? = null) {
    if (flags.isEmpty()) return
    val p = palette
    val ctx = LocalContext.current
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(Goals.SAFETY_TITLE, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
            if (onClose != null) Text("Close", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable(onClick = onClose))
        }
        Text(Goals.safetyBody(flags, floor), fontSize = 13.sp, color = p.ink, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Goals.HELPLINES.forEach { h ->
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(p.card2, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(h.name, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                Text(h.detail, fontSize = 11.sp, color = p.muted)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    h.phones.forEach { n ->
                        Row(
                            Modifier.clickable {
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + n.filter { it.isDigit() || it == '+' }))) }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PhoneIcon, null, tint = p.ink, modifier = Modifier.size(13.dp))
                            Text(" $n", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink)
                        }
                    }
                }
                if (h.whatsapp != null) Text("WhatsApp ${h.whatsapp}", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text(Goals.HELPLINE_NOTE, fontSize = 11.sp, color = p.muted, lineHeight = 15.sp)
        val prof = vm.profile
        if (prof.hideNumbers == false) {
            Text(
                "Prefer not to see numbers? Tap to hide calorie numbers.",
                fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink,
                modifier = Modifier.padding(top = 6.dp).clickable { vm.setHideNumbers(true) },
            )
        } else if (prof.hideNumbers == true) {
            Text("Calorie numbers are hidden. Turn them back on in Preferences → Tracking.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------------------------------------------------------- under 18

/** The gentle line shown wherever an under-18 picks a goal. */
@Composable
fun TeenNote() {
    val p = palette
    Box(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(Goals.TEEN_GOAL_NOTE, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp)
    }
}

/**
 * v2.10 migration: an under-18 account still holding "lose" is moved to maintain (fresh targets,
 * nothing deleted) the first time a goal screen computes targets, then a one-time card explains
 * why. The card stays until "Got it" on this device.
 */
@Composable
fun TeenGoalMigration(vm: AppViewModel) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(TargetEdits.teenCardPending(ctx)) }
    val prof = vm.profile
    LaunchedEffect(prof.dob, prof.goalType) {
        val next = Goals.migrateTeenGoal(prof) ?: return@LaunchedEffect
        scope.launch {
            if (vm.saveProfile(next)) { TargetEdits.setTeenCard(ctx, true); show = true }
        }
    }
    if (!show) return
    Card {
        Text(Goals.TEEN_MIGRATION_TITLE, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
        Text(Goals.TEEN_MIGRATION_BODY, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.background(p.btn, CircleShape).clickable { TargetEdits.setTeenCard(ctx, false); show = false }.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("Got it", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk) }
    }
}

// ---------------------------------------------------------------- BMI card

private fun ordinal(n: Int): String {
    val s = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$s"
}

/**
 * BMI with the healthy range as a band on a weight scale. Adults: Indian cut-offs first, WHO
 * global second. Under 18: WHO 2007 BMI-for-age in plain words and the usual range for their age.
 * [waist] adds the optional waist-to-height entry (hidden until schema_v34 is in).
 */
@Composable
fun BmiCard(vm: AppViewModel, weightKg: Double?, waist: Boolean = false) {
    val p = palette
    val prof = vm.profile
    val today = Goals.todayIso()
    val heightCm = prof.heightCm
    val b = Bmi.bmi(weightKg, heightCm)
    val age = Goals.ageYears(prof.dob, today)
    val months = Bmi.ageMonths(prof.dob, today)
    val teen = Goals.isTeen(age)

    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your BMI", fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
            if (b != null && heightCm != null && weightKg != null) {
                val z = if (teen && months != null) Bmi.bmiForAgeZ(b, prof.gender, months) else null
                val (chip, color) = if (teen) {
                    if (z == null) "For your age" to p.green else if (z < -2 || z > 1) "Worth a look" to p.orange else "On track" to p.green
                } else {
                    val india = Bmi.categoryIndia(b)
                    india to when (india) { "Normal" -> p.green; "Underweight" -> p.blue; "Overweight" -> p.orange; else -> p.red }
                }
                Box(Modifier.background(color.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(chip, fontSize = 12.sp, fontWeight = FontWeight(700), color = color)
                }
                Spacer(Modifier.width(8.dp))
            }
            ScienceButton()
        }
        if (b == null || heightCm == null || weightKg == null) {
            Text("Add your height and weight in Personal details to see it.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            return@Card
        }
        val z = if (teen && months != null) Bmi.bmiForAgeZ(b, prof.gender, months) else null
        val range = if (teen) months?.let { Bmi.teenHealthyRange(heightCm, prof.gender, it) } else Bmi.healthyRange(heightCm)
        val lines = if (teen) {
            val w = z?.let { Bmi.teenWords(it) } ?: Bmi.Words("Growth charts cover ages 5 to 17", "")
            listOf(w.title, w.detail, z?.let { "About the ${ordinal(Bmi.percentileFromZ(it))} percentile for your age, from WHO growth charts." } ?: "").filter { it.isNotBlank() }
        } else {
            listOf("${Bmi.categoryIndia(b)} by Indian/Asian cut-offs · ${Bmi.categoryWho(b)} by WHO global ones.", "BMI can't tell muscle from fat, so it's one signal, not a verdict.")
        }
        Text(String.format(Locale.US, "%.1f", b), fontSize = 32.sp, fontWeight = FontWeight(800), letterSpacing = (-1.2).sp, color = p.ink, lineHeight = 36.sp, modifier = Modifier.padding(top = 4.dp))
        if (range != null) {
            val lo = minOf(range.min, weightKg) - 8
            val hi = maxOf(range.max, weightKg) + 8
            fun at(v: Double) = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
            val track = p.track
            val band = p.green.copy(alpha = 0.55f)
            val ink = p.ink
            val card = p.card
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val barH = 10.dp.toPx()
                val top = (size.height - barH) / 2
                drawRoundRect(track, Offset(0f, top), Size(size.width, barH), cornerRadius = CornerRadius(barH / 2))
                val x0 = size.width * at(range.min)
                val x1 = size.width * at(range.max)
                drawRoundRect(band, Offset(x0, top), Size(x1 - x0, barH), cornerRadius = CornerRadius(barH / 2))
                val x = (size.width * at(weightKg)).coerceIn(size.height / 2, size.width - size.height / 2)
                drawCircle(card, size.height / 2, Offset(x, size.height / 2))
                drawCircle(ink, size.height / 2, Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
            }
            Text(
                "${if (teen) "Usual range for your age and height" else "Healthy range for your height"}: ${fmt(range.min)}–${fmt(range.max)} kg",
                fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(top = 8.dp),
            )
        }
        lines.forEach { Text(it, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (waist && prof.hideNumbers != null) WaistRow(vm)
    }
}

/** Optional waist entry and the waist-to-height ratio. */
@Composable
private fun WaistRow(vm: AppViewModel) {
    val p = palette
    val scope = rememberCoroutineScope()
    val prof = vm.profile
    var value by remember(prof.waistCm) { mutableStateOf(prof.waistCm?.let { fmt(it) } ?: "") }
    var busy by remember { mutableStateOf(false) }
    val cm = value.toDoubleOrNull()?.takeIf { it > 0 }
    val dirty = cm != prof.waistCm
    Spacer(Modifier.height(12.dp))
    Hair()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Waist (optional)", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink)
            Text("Measure around your belly button, relaxed", fontSize = 11.sp, color = p.muted)
        }
        NumberField(value, { value = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, "cm")
        if (dirty) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.background(p.btn, CircleShape).clickable(enabled = !busy) {
                    scope.launch { busy = true; vm.saveWaist(cm); busy = false }
                }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(if (busy) "…" else "Save", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.btnInk) }
        }
    }
    val r = Bmi.waistToHeight(prof.waistCm, prof.heightCm)
    if (r != null) {
        val (ok, text) = Bmi.whtrWords(r)
        Text(
            "Waist-to-height ${String.format(Locale.US, "%.2f", r)} · $text",
            fontSize = 12.sp, color = if (ok) p.green else p.orange, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
