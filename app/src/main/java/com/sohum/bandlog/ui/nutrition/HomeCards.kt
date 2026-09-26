package com.sohum.bandlog.ui.nutrition

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.FoodImage
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Adaptive
import com.sohum.bandlog.util.DietModes
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.WhatToEat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Home's shortcuts row: Fasting · Micros · Recipes · What to eat (fasting only when it's allowed). */
@Composable
fun NutritionShortcuts(vm: AppViewModel, nvm: NutritionViewModel) {
    val ctx = LocalContext.current
    val fastingOk = fastingBlock(vm, ctx) == null
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Shortcut(NutritionIcons.Sparkles, "What to eat") { nvm.page = NutritionPage.WhatToEat }
        if (fastingOk) Shortcut(NutritionIcons.Timer, "Fasting") { nvm.page = NutritionPage.Fasting }
        Shortcut(NutritionIcons.Pill, "Micros") { nvm.page = NutritionPage.Micros }
        Shortcut(NutritionIcons.ChefHat, "Recipes") { nvm.page = NutritionPage.Recipes }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.heightIn(min = 44.dp).background(p.card, CircleShape).clickable(onClick = onClick).padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
    }
}

/**
 * §5 the Monday check-in card: the reason in plain English and Apply / Keep current. Never
 * applies on its own; shown until one is tapped (per week, on this device), or once applied.
 */
@Composable
fun CheckinCard(vm: AppViewModel, nvm: NutritionViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(nvm.settings, vm.loadedOnce) { nvm.ensureCheckin(vm) }
    val c = nvm.checkin ?: return
    if (c.applied || nvm.checkinHandled(ctx, c.weekStart)) return
    val p = palette
    var busy by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(p.purpleBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NutritionIcons.Trend, null, tint = p.purple, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Your weekly check-in", fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                Text("Week of ${com.sohum.bandlog.util.Dates.short(c.weekStart)}", fontSize = 12.sp, color = p.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        val next = c.newTarget
        if (next == null) {
            Text(c.reason, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp)
            nvm.checkinResult?.missing?.forEach { Text("•  $it", fontSize = 12.sp, color = p.muted, lineHeight = 17.sp) }
            Spacer(Modifier.height(10.dp))
            PillButton("Got it", { nvm.keepCheckin(ctx) }, height = 44.dp, bg = p.card2, fg = p.ink)
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${c.oldTarget ?: vm.profile.calorieTarget}", fontSize = 20.sp, fontWeight = FontWeight(700), color = p.muted)
            Text("  →  ", fontSize = 16.sp, color = p.muted)
            Text("$next kcal", fontSize = 24.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink)
        }
        Text(c.reason, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Keep current", { nvm.keepCheckin(ctx) }, Modifier.weight(1f), height = 46.dp, bg = p.card2, fg = p.ink, enabled = !busy)
            PillButton(if (busy) "Applying…" else "Apply", { scope.launch { busy = true; nvm.applyCheckin(ctx, vm); busy = false } }, Modifier.weight(1f), height = 46.dp, enabled = !busy && next != (c.oldTarget ?: -1))
        }
    }
}

/** §6 compact Home card: the top 3 picks for what's left, one tap logs; "See all" opens the sheet. */
@Composable
fun WhatToEatCard(vm: AppViewModel, nvm: NutritionViewModel) {
    val p = palette
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.loadPresets() }
    val rem = nvm.remaining(vm)
    // Only once something is logged and there's a real gap left.
    if (vm.meals.none { it.date == vm.today } || rem.kcal < 150) return
    val picks = remember(vm.presets, vm.meals, nvm.settings.dietMode, vm.profile) { nvm.picks(vm, 3) }
    if (picks.isEmpty()) return
    var logging by remember { mutableStateOf<String?>(null) }
    Card(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("What should I eat?", fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                Text("${rem.kcal.roundToInt()} kcal and ${rem.protein.roundToInt()} g protein left", fontSize = 12.sp, color = p.muted)
            }
            Box(Modifier.heightIn(min = 44.dp).clickable { nvm.page = NutritionPage.WhatToEat }.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text("See all", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
            }
        }
        picks.forEach { pk ->
            Hair()
            PickRow(pk.food, pk.why, logging == pk.food.name) {
                scope.launch { logging = pk.food.name; nvm.logPick(vm, pk.food); logging = null }
            }
        }
    }
}

@Composable
private fun PickRow(f: WhatToEat.Food, why: String, busy: Boolean, onLog: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) {
            contentDescription = "${f.name}, ${f.portion}, ${f.kcal.roundToInt()} kcal, ${fmt((f.protein * 10).roundToInt() / 10.0)} grams protein"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodImage(f.name, f.imageUrl, kind = "preset", size = 40.dp, foodId = f.foodId)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${f.portion} · ${f.kcal.roundToInt()} kcal · ${fmt((f.protein * 10).roundToInt() / 10.0)} g protein", fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (why.isNotBlank()) Text(why, fontSize = 11.sp, color = p.green, maxLines = 1)
        }
        Box(
            Modifier.size(44.dp).clickable(enabled = !busy, onClickLabel = "Log ${f.name}", onClick = onLog),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = p.btnInk)
                else Text("+", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.btnInk)
            }
        }
    }
}

/** §6 the full sheet: top 5 for what's left (diet-mode filtered) and "Your usual". */
@Composable
fun WhatToEatSheet(vm: AppViewModel, nvm: NutritionViewModel, onDismiss: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.loadPresets(); nvm.loadSettings() }
    val rem = nvm.remaining(vm)
    val mode = nvm.dietMode(vm.profile)
    val picks = remember(vm.presets, vm.meals, mode) { nvm.picks(vm, 5) }
    val usual = remember(vm.meals, mode) { nvm.usual(vm) }
    var logging by remember { mutableStateOf<String?>(null) }
    val type = MealTypes.label(MealTypes.default())
    BottomSheet(
        title = "What should I eat?",
        subtitle = "${rem.kcal.roundToInt()} kcal · ${rem.protein.roundToInt()} g protein · ${rem.carbs.roundToInt()} g carbs · ${rem.fat.roundToInt()} g fat left. Tap + to log to $type.",
        onDismiss = onDismiss,
    ) {
        NoticeLine(nvm)
        if (mode != DietModes.BALANCED) Text(
            "${DietModes.byKey(mode).label}" + (DietModes.excludes(mode)?.let { ": leaving out $it" } ?: ""),
            fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(bottom = 6.dp),
        )
        if (vm.presets.isEmpty()) Text(if (vm.presetsLoading) "Loading foods…" else "The food list didn't load. Try again in a moment.", fontSize = 13.sp, color = p.muted)
        if (picks.isNotEmpty()) {
            Text("Best fits right now", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.ink)
            picks.forEachIndexed { i, pk ->
                if (i > 0) Hair()
                PickRow(pk.food, pk.why, logging == pk.food.name) { scope.launch { logging = pk.food.name; nvm.logPick(vm, pk.food); logging = null } }
            }
        }
        if (usual.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Your usual", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.ink)
            usual.forEachIndexed { i, u ->
                if (i > 0) Hair()
                PickRow(u.food, "Logged ${u.times} times lately", logging == "u-" + u.food.name) { scope.launch { logging = "u-" + u.food.name; nvm.logPick(vm, u.food); logging = null } }
            }
        }
        Text(
            "Ranked by protein per calorie, how well it fits what's left, and this time of day. Suggestions only.",
            fontSize = 11.sp, color = p.muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** A small "Adaptive targets" hint for the Goals screen when the check-in can't run yet. */
fun adaptiveSummary(r: Adaptive.Result?): String? = r?.let { if (it.ready) null else it.missing.joinToString(" · ") }
