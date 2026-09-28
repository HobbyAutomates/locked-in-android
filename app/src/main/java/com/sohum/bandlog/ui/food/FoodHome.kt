package com.sohum.bandlog.ui.food

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.sohum.bandlog.data.FoodApi
import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BowlIcon
import com.sohum.bandlog.ui.components.ChevronDownIcon
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.PeopleIcon
import com.sohum.bandlog.ui.components.SmallChip
import com.sohum.bandlog.ui.nutrition.NutritionIcons
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.FoodBits
import com.sohum.bandlog.util.FoodHonesty
import com.sohum.bandlog.util.MealTypes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * v2.18 Area A on Home (web twin: components/food/FoodHome.tsx). [FoodStore] holds the schema_v42
 * bits Home needs (leftovers, pending splits, the water-from-food switch), loaded once and after
 * each change; each part is null while its table isn't there, and its card hides.
 */
object FoodStore {
    var home by mutableStateOf<FoodApi.FoodHome?>(null)
    private var loading = false

    suspend fun refresh() {
        if (loading) return
        loading = true
        home = runCatching { FoodApi.home() }.getOrElse { FoodApi.FoodHome(null, null, null) }
        loading = false
    }

    /** A10: ml of water in these meals when the setting is on (0 otherwise, or before schema_v42). */
    fun foodWaterMl(meals: List<Meal>): Int = if (home?.waterFromFood == true) FoodBits.waterFromMeals(meals.map { it.items }) else 0

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("food_v218", Context.MODE_PRIVATE)
    fun swapHidden(ctx: Context, id: String): Boolean = prefs(ctx).getStringSet("swap_hidden", emptySet())?.contains(id) == true
    fun hideSwap(ctx: Context, id: String) {
        val cur = prefs(ctx).getStringSet("swap_hidden", emptySet()).orEmpty().toMutableList()
        cur.add(0, id)
        prefs(ctx).edit().putStringSet("swap_hidden", cur.take(40).toSet()).apply()
    }
}

/**
 * Under the macro cards, today only: the day's accuracy chip (A3, tap for the vaguest entries, each
 * one tap from its editor), splits squadmates sent (A7), leftovers (A6) and one smart swap (A9).
 */
@Composable
fun FoodHomeExtras(vm: AppViewModel, meals: List<Meal>, onOpenMeal: (Meal) -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.meals.size, vm.loadedOnce) { if (vm.signedIn) FoodStore.refresh() }
    val home = FoodStore.home
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val acc = FoodHonesty.dayAccuracy(meals.map { FoodHonesty.AccMeal(it.id, it.items) })
    val latest = meals.maxByOrNull { it.createdAt }
    val swap = latest?.let { FoodBits.pickSwap(it.items) }
    val swapId = "swap-${latest?.id}"
    var swapGone by remember(swapId) { mutableStateOf(FoodStore.swapHidden(ctx, swapId)) }

    fun act(id: String, block: suspend () -> Unit) {
        scope.launch {
            busy = id; error = null
            try { block() } catch (e: Exception) { error = e.message ?: "Couldn't do that" }
            busy = null
            FoodStore.refresh()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val score = acc.score
        if (score != null) {
            val tone = when { score >= 80 -> p.green; score >= 60 -> p.ink; else -> p.orange }
            Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = !open }
                        .semantics { contentDescription = "Logging accuracy $score out of 100, ${acc.label}. ${if (acc.vague.isEmpty()) "Nothing to fix" else "${acc.vague.size} to fix"}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).border(2.5.dp, tone, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$score", fontSize = 13.sp, fontWeight = FontWeight(800), color = tone)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Accuracy · ${acc.label}", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(
                            if (acc.vague.isEmpty()) "Every entry is well sourced today" else "${acc.vague.size} vague ${if (acc.vague.size == 1) "entry" else "entries"}. Fix ${if (acc.vague.size == 1) "it" else "them"} for sharper numbers",
                            fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(ChevronDownIcon, null, tint = p.muted, modifier = Modifier.size(16.dp))
                }
                AnimatedVisibility(open) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        acc.vague.forEach { v ->
                            val meal = meals.firstOrNull { it.id == v.mealId }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp).background(p.card2, RoundedCornerShape(12.dp)).clickable(enabled = meal != null) { meal?.let(onOpenMeal) }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(v.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${v.kcal} kcal ±${v.plusMinus}", fontSize = 13.sp, color = p.muted)
                                Spacer(Modifier.width(10.dp))
                                Text("Fix", fontSize = 13.sp, fontWeight = FontWeight(800), color = p.ember)
                            }
                        }
                        Text(
                            "Each item carries a ± range from where its numbers came from: your own numbers ±5%, a scanned label ±8%, the food table ±12%, a web-checked photo ±12–30%, a guess up to ±40%.",
                            fontSize = 11.sp, color = p.muted, lineHeight = 15.sp,
                        )
                    }
                }
            }
        }

        home?.splits.orEmpty().forEach { s ->
            HomeRow(PeopleIcon, null, "${s.fromName ?: "A squadmate"} split ${s.dish}", "Your share · ${s.kcal.roundToInt()} kcal · ${(s.share * 100).roundToInt()}%", busy == s.id,
                action = "Accept", onAction = {
                    act(s.id) {
                        val ok = vm.saveMeal(if (s.date <= Dates.today()) s.date else Dates.today(), "${s.dish} (shared${s.fromName?.let { " by $it" } ?: ""})", s.items, mealType = s.mealType ?: MealTypes.default(), method = "split")
                        if (ok) FoodApi.decideSplit(s.id, true) else error = vm.error
                    }
                },
                onClose = { act(s.id) { FoodApi.decideSplit(s.id, false) } }, closeLabel = "Decline")
        }

        home?.leftovers.orEmpty().take(2).forEach { l ->
            HomeRow(BowlIcon, "Leftovers", FoodBits.leftoverLine(l.name, l.kcal, l.fractionLeft), null, busy == l.id,
                action = "Log it", onAction = {
                    act(l.id) {
                        val ok = vm.saveMeal(Dates.today(), "${l.name} (leftovers)", l.items, mealType = MealTypes.default(), method = "leftovers")
                        if (ok) FoodApi.closeLeftover(l.id, used = true) else error = vm.error
                    }
                },
                onClose = { act(l.id) { FoodApi.closeLeftover(l.id, used = false) } }, closeLabel = "Not eating it")
        }

        if (swap != null && !swapGone) {
            HomeRow(NutritionIcons.Leaf, "Smart swap for next time", swap.line, swap.why.replaceFirstChar { it.uppercase() }, false,
                action = null, onAction = {}, onClose = { FoodStore.hideSwap(ctx, swapId); swapGone = true }, closeLabel = "Hide this swap")
        }
        error?.let { Text(it, fontSize = 12.sp, color = p.red, modifier = Modifier.padding(horizontal = 6.dp)) }
    }
}

@Composable
private fun HomeRow(icon: ImageVector, kicker: String?, title: String, sub: String?, busy: Boolean, action: String?, onAction: () -> Unit, onClose: () -> Unit, closeLabel: String) {
    val p = palette
    Row(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = p.ink, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (kicker != null) Text(kicker, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(16.dp), strokeWidth = 2.dp, color = p.ink)
        else {
            if (action != null) SmallChip(action, onAction, filled = true)
            Box(Modifier.size(44.dp).clickable(onClick = onClose).semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
                Icon(CrossIcon, null, tint = p.muted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** A10 on the Water page: "Count water in food" (off by default; hidden before schema_v42). */
@Composable
fun WaterFromFoodRow() {
    val p = palette
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (FoodStore.home == null) FoodStore.refresh() }
    val on = FoodStore.home?.waterFromFood ?: return
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count water in food", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("Dal, chaas, curd, fruit, tea and soups add their water to your day. Off by default.", fontSize = 12.sp, color = p.muted, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = on,
                onCheckedChange = { v ->
                    scope.launch {
                        error = null
                        runCatching { FoodApi.setWaterFromFood(v) }.onFailure { error = it.message ?: "Couldn't change that" }
                        FoodStore.refresh()
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = p.ember),
                modifier = Modifier.semantics { contentDescription = "Count water in food" },
            )
        }
        error?.let { Text(it, fontSize = 12.sp, color = p.red) }
    }
}

/** The "+ N mL from food" line under the water tile's total (empty string when none). */
fun foodWaterNote(ml: Int): String = if (ml > 0) " · $ml mL from food" else ""

/** Items a split sends each squadmate, scaled by share weight (index 0 = me). */
fun splitParts(items: List<MealItem>, weights: List<Double>): List<List<MealItem>> = FoodBits.splitShares(items, weights)
