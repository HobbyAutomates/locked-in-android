package com.sohum.bandlog.ui.food

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.FoodApi
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.Recipe
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.BottomSheet
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.MealSlotPicker
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.PlusIcon
import com.sohum.bandlog.ui.components.SearchIcon
import com.sohum.bandlog.ui.components.ShareIcon
import com.sohum.bandlog.ui.components.SmallChip
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.nutrition.ComingSoonCard
import com.sohum.bandlog.ui.nutrition.NutritionIcons
import com.sohum.bandlog.ui.nutrition.NutritionPage
import com.sohum.bandlog.ui.nutrition.NutritionViewModel
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.FoodBits
import com.sohum.bandlog.util.Grocery
import com.sohum.bandlog.util.HomeRecipes
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.OrderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * v2.18 Area A pages over the tab shell (one line in MainActivity, like NutritionOverlays):
 * the "Ghar ka khana" library (A2), Eating out (A4) and Pantry & groceries (A11).
 * Web twins: components/food/HomeRecipesScreen.tsx, OrderHelperScreen.tsx, PantryScreen.tsx.
 */
sealed class FoodPage {
    data object Library : FoodPage()
    data object Order : FoodPage()
    data object Pantry : FoodPage()
}

object FoodNav {
    var page by mutableStateOf<FoodPage?>(null)
}

@Composable
fun FoodOverlays(vm: AppViewModel) {
    when (FoodNav.page) {
        null -> {}
        FoodPage.Library -> { BackHandler { FoodNav.page = null }; LibraryPage(vm) { FoodNav.page = null } }
        FoodPage.Order -> { BackHandler { FoodNav.page = null }; OrderPage(vm) { FoodNav.page = null } }
        FoodPage.Pantry -> { BackHandler { FoodNav.page = null }; PantryPage(vm) { FoodNav.page = null } }
    }
}

// ---------------------------------------------------------------- A2 Ghar ka khana

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryPage(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val all = remember { HomeRecipes.load(ctx) }
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("all") }
    var open by remember { mutableStateOf<HomeRecipes.HomeRecipe?>(null) }
    val list = HomeRecipes.search(q, all).filter { cat == "all" || it.category == cat }
    val hide = vm.profile.hideNumbers == true
    MotionScreen {
        SubPage("Ghar ka khana", onBack) {
            Text("Home-style numbers (with the usual oil and ghee) for one serving in the unit you'd eat it in. Tap one to log it.", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth().height(48.dp).background(p.card, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SearchIcon, null, tint = p.muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    q, { q = it }, Modifier.weight(1f).semantics { contentDescription = "Search home recipes" }, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (q.isEmpty()) Text("Search: dal, roti, poha, rajma…", fontSize = 15.sp, color = p.muted); inner() },
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("all" to "All") + HomeRecipes.HOME_CATEGORIES).forEach { (k, l) -> SmallChip(l, { cat = k }, filled = cat == k) }
            }
            if (all.isEmpty()) ErrorNote("Couldn't open the recipe library.")
            Card(padding = 6.dp) {
                if (list.isEmpty() && all.isNotEmpty()) Text("Nothing by that name. Try another word, or save your own recipe.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(12.dp))
                list.forEachIndexed { i, r ->
                    if (i > 0) Hair()
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { open = r }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(
                                (if (r.local != r.name) "${r.local} · " else "") + "1 ${r.unit} (${r.unitGrams.roundToInt()} g)" + (if (hide) "" else " · ${r.kcal.roundToInt()} kcal") + " · ${fmt(r.proteinG)} g protein",
                                fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(PlusIcon, null, tint = p.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    open?.let { r -> LibrarySheet(vm, r, hide) { open = null } }
}

@Composable
private fun LibrarySheet(vm: AppViewModel, r: HomeRecipes.HomeRecipe, hide: Boolean, onDismiss: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val nvm: NutritionViewModel = viewModel()
    val step = HomeRecipes.servingStep(r)
    var n by remember(r.id) { mutableDoubleStateOf(1.0) }
    var slot by remember(r.id) { mutableStateOf(MealTypes.default()) }
    var busy by remember { mutableStateOf(false) }
    var done by remember(r.id) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val item = HomeRecipes.homeRecipeItem(r, n)
    BottomSheet(
        r.name, onDismiss, subtitle = "${r.local} · ${r.ingredients.joinToString(", ")}",
        primary = when { busy -> "Saving…"; done != null -> "Logged"; else -> "Log ${HomeRecipes.unitLabel(r, n)} to ${MealTypes.label(slot)}" },
        primaryEnabled = !busy && done == null,
        onPrimary = {
            scope.launch {
                busy = true; error = null
                val ok = vm.saveMeal(Dates.today(), "${r.name} (ghar ka khana)", listOf(item), mealType = slot, method = "recipe")
                busy = false
                if (ok) done = "Logged ${HomeRecipes.unitLabel(r, n)} to ${MealTypes.label(slot)}" else error = vm.error
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallChip("−", { n = (n - step).coerceAtLeast(step) })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(HomeRecipes.unitLabel(r, n), fontSize = 22.sp, fontWeight = FontWeight(800), color = p.ink)
                    Text("${item.grams.roundToInt()} g" + (if (hide) "" else " · ${item.calories.roundToInt()} kcal") + " · ${fmt(item.proteinG)} g P · ${fmt(item.carbsG)} g C · ${fmt(item.fatG)} g F", fontSize = 12.sp, color = p.muted, textAlign = TextAlign.Center)
                }
                SmallChip("+", { n = (n + step).coerceAtMost(10.0) })
            }
            MealSlotPicker(slot, { slot = it })
            done?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.green, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        val own = HomeRecipes.homeRecipeAsOwn(r)
                        val saved = nvm.saveRecipe(Recipe(null, own.name, own.servings, own.cookedWeightG, own.items, own.note))
                        busy = false
                        if (saved != null) { onDismiss(); FoodNav.page = null; nvm.page = NutritionPage.RecipeEdit(saved) } else error = nvm.message
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Text("Make it mine (copy to your recipes to tweak)", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted) }
            ErrorNote(error)
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------- A4 Eating out

private val STEPS = listOf(0.0, 0.25, 0.5, 0.75, 1.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderPage(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nvm: NutritionViewModel = viewModel()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var res by remember { mutableStateOf<FoodApi.OrderResult?>(null) }
    val eat = remember { mutableStateListOf<Double>() }
    var slot by remember { mutableStateOf(MealTypes.default()) }
    var saved by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val hide = vm.profile.hideNumbers == true

    fun read(t: String?, img: String?) {
        scope.launch {
            busy = true; error = null; res = null; saved = null
            try {
                val rem = nvm.remaining(vm)
                val remJson = org.json.JSONObject().put("kcal", rem.kcal.roundToInt()).put("protein", rem.protein.roundToInt()).put("carbs", rem.carbs.roundToInt()).put("fat", rem.fat.roundToInt())
                val r = FoodApi.orderHelper(t, img, remJson)
                res = r; eat.clear(); eat.addAll(r.plan.rows.map { it.eat })
            } catch (e: NotYetAvailable) { unavailable = true } catch (e: Exception) { error = e.message ?: "Couldn't read that order" } finally { busy = false }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b64 = withContext(Dispatchers.IO) {
                com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 2200)?.let { com.sohum.bandlog.ui.scan.toJpegBase64(com.sohum.bandlog.ui.scan.scaleForUpload(it, 1600), 86) }
            }
            if (b64 == null) error = "Couldn't open that image" else read(text.ifBlank { null }, b64)
        }
    }

    MotionScreen {
        SubPage("Eating out", onBack) {
            if (unavailable) { ComingSoonCard("Eating out", "Paste a Swiggy or Zomato order and get a plan for what's left today."); return@SubPage }
            Card {
                Text("Paste your Swiggy or Zomato order", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    BasicTextField(
                        text, { text = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Order text" },
                        textStyle = TextStyle(fontSize = 14.sp, color = p.ink, lineHeight = 20.sp), cursorBrush = SolidColor(p.ink),
                        decorationBox = { inner -> if (text.isEmpty()) Text("2 x Butter Naan\nPaneer Tikka x 1\nDal Makhani", fontSize = 14.sp, color = p.muted, lineHeight = 20.sp); inner() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Screenshot", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), enabled = !busy, height = 48.dp, bg = p.card2, fg = p.ink)
                    PillButton(if (busy) "Reading…" else "Plan it", { read(text, null) }, Modifier.weight(1f), enabled = !busy && text.isNotBlank(), height = 48.dp)
                }
                Text("At the restaurant? Scan the menu instead (Scan → Menu).", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (busy) Column {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
                Text("Pricing each dish and planning for what's left today… 10–30 s", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
            }
            ErrorNote(error)
            val r = res
            if (r != null) {
                val rows = r.dishes.mapIndexed { i, d -> i to (eat.getOrNull(i) ?: 0.0) }
                val planKcal = rows.sumOf { (i, e) -> r.dishes[i].dish.midKcal * r.dishes[i].qty * e }.roundToInt()
                val planProt = rows.sumOf { (i, e) -> r.dishes[i].dish.midProtein * r.dishes[i].qty * e }.roundToInt()
                Card {
                    Text(r.plan.headline, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, lineHeight = 20.sp)
                    if (!hide) Text("This plan · $planKcal kcal · $planProt g protein · ${r.remainingKcal.roundToInt()} kcal left today", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                    if (r.note.isNotBlank()) Text(r.note, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                }
                Card(padding = 8.dp) {
                    r.dishes.forEachIndexed { i, od ->
                        if (i > 0) Hair()
                        val d = od.dish
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text((if (od.qty > 1) "${od.qty} × " else "") + d.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                            Text(
                                (if (hide) "" else "${d.kcalLow.roundToInt()}–${d.kcalHigh.roundToInt()} kcal · ") + "${fmt(d.proteinLow)}–${fmt(d.proteinHigh)} g protein each · ${d.confidence} confidence" + if (!d.fitsDiet) " · not ${r.dietMode}" else "",
                                fontSize = 12.sp, color = p.muted,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                STEPS.forEach { f -> SmallChip(if (f == 0.0) "Skip" else FoodBits.fractionLabel(f), { if (i < eat.size) eat[i] = f }, filled = eat.getOrNull(i) == f) }
                            }
                            val sug = r.plan.rows.getOrNull(i)?.eat
                            if (sug != null && sug != 1.0) Text("Suggested: ${if (sug == 0.0) "skip / share" else FoodBits.fractionLabel(sug)}", fontSize = 11.sp, color = p.muted)
                        }
                    }
                }
                MealSlotPicker(slot, { slot = it })
                PillButton(
                    when { busy -> "Saving…"; saved != null -> "Pre-logged"; else -> "Pre-log this plan to ${MealTypes.label(slot)}" },
                    {
                        scope.launch {
                            busy = true; error = null
                            val plan = r.plan.copy(rows = rows.map { (i, e) -> OrderHelper.PlanRow(i, e, 0, 0, "") })
                            val items = OrderHelper.planItems(r.dishes, plan)
                            val ok = items.isNotEmpty() && vm.saveMeal(Dates.today(), "${r.restaurant?.let { "$it order" } ?: "Delivery order"} (pre-logged)", items, mealType = slot, method = "order")
                            if (ok) {
                                // What isn't eaten waits as leftovers (quietly skipped before schema_v42).
                                val rest = rows.filter { it.second < 1.0 }.mapNotNull { (i, e) ->
                                    OrderHelper.planItems(listOf(r.dishes[i]), plan.copy(rows = listOf(OrderHelper.PlanRow(0, 1.0, 0, 0, "")))).firstOrNull()?.let { FoodBits.scaleMealItem(it, 1 - e) }
                                }
                                val total = r.plan.totalKcal.coerceAtLeast(1)
                                PlateSave.leftovers("${r.restaurant?.let { "$it " } ?: ""}order leftovers", rest, (rest.sumOf { it.calories } / total).coerceIn(0.05, 0.95))
                                saved = "Pre-logged $planKcal kcal to ${MealTypes.label(slot)}${if (rest.isNotEmpty()) ". The rest waits as leftovers" else ""}."
                                FoodStore.refresh()
                            } else if (items.isNotEmpty()) error = vm.error
                            busy = false
                        }
                    },
                    enabled = !busy && saved == null && planKcal > 0,
                )
                saved?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.green, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
    }
}

// ---------------------------------------------------------------- A11 pantry + groceries

@Composable
fun PantryPage(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("list") }
    var pantry by remember { mutableStateOf<List<FoodApi.PantryItem>?>(null) }
    var pantryOff by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val prefs = remember { ctx.getSharedPreferences("food_v218", android.content.Context.MODE_PRIVATE) }
    var ticked by remember { mutableStateOf(prefs.getStringSet("grocery_ticked", emptySet()).orEmpty()) }

    suspend fun reload() {
        try { pantry = FoodApi.pantry(); pantryOff = false } catch (e: NotYetAvailable) { pantryOff = true; pantry = emptyList() } catch (e: Exception) { error = e.message; pantry = pantry ?: emptyList() }
    }
    LaunchedEffect(Unit) { reload() }

    // The list: the last 14 days of logs, scaled to a week, plus the protein gap; pantry in stock drops off.
    val to = Dates.today()
    val from = Dates.addDays(to, -13L)
    val meals = vm.meals.filter { it.date in from..to }
    val days = meals.map { it.date }.toSet().size.coerceAtLeast(1)
    val eaten = meals.flatMap { m -> m.items.map { Grocery.EatenFood(it.name, it.grams) } }
    val avgProtein = meals.sumOf { m -> m.items.sumOf { it.proteinG } } / days
    val target = vm.profile.proteinTargetG.toDouble()
    val nvm: NutritionViewModel = viewModel()
    val mode = nvm.dietMode(vm.profile)
    val hasMeat = meals.any { m -> m.items.any { Regex("chicken|mutton|fish|keema|prawn|murgh", RegexOption.IGNORE_CASE).containsMatchIn(it.name) } }
    val hasEgg = meals.any { m -> m.items.any { Regex("\\begg|anda|omelette", RegexOption.IGNORE_CASE).containsMatchIn(it.name) } }
    val diet = when (mode) { "vegan" -> "vegan"; "jain" -> "jain"; "vegetarian" -> "veg"; "eggetarian" -> "egg"; else -> if (hasMeat) "nonveg" else if (hasEgg) "egg" else "veg" }
    val list = Grocery.groceryList(eaten, days, target, avgProtein, pantry.orEmpty().map { Grocery.PantryRow(it.name, it.inStock) }, diet)
    fun tick(key: String) {
        ticked = if (key in ticked) ticked - key else ticked + key
        prefs.edit().putStringSet("grocery_ticked", ticked).apply()
    }

    MotionScreen {
        SubPage("Pantry & groceries", onBack) {
            Row(Modifier.fillMaxWidth().background(p.card2, RoundedCornerShape(16.dp)).padding(4.dp)) {
                listOf("list" to "This week's list", "pantry" to "My pantry").forEach { (k, l) ->
                    Box(
                        Modifier.weight(1f).height(40.dp).background(if (tab == k) p.card else p.card2, RoundedCornerShape(12.dp)).clickable { tab = k }
                            .semantics { role = Role.Tab; selected = tab == k },
                        contentAlignment = Alignment.Center,
                    ) { Text(l, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink) }
                }
            }
            ErrorNote(error)
            if (tab == "list") {
                Card {
                    Text("From $days day${if (days == 1) "" else "s"} of your logs, scaled to a week." + if (target > 0) " You average ${avgProtein.roundToInt()} g protein a day against ${target.roundToInt()} g." else "", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Share the list", {
                        val body = "Groceries for the week\n" + Grocery.groceryText(list)
                        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Share the list")) }
                    }, height = 46.dp, icon = ShareIcon)
                }
                Grocery.PANTRY_CATEGORIES.forEach { (k, label) ->
                    val rows = list.filter { it.category == k && !it.inPantry }
                    if (rows.isNotEmpty()) Card(padding = 8.dp) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        rows.forEach { g ->
                            val on = g.key in ticked
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { tick(g.key) }.padding(horizontal = 8.dp)
                                    .semantics { role = Role.Checkbox; selected = on },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(22.dp).background(if (on) p.ember else p.card2, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                    if (on) Icon(CheckIcon, null, tint = p.onEmber, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = if (on) p.muted else p.ink, textDecoration = if (on) TextDecoration.LineThrough else null)
                                    Text(g.why, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(g.qty, fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                            }
                        }
                    }
                }
                val stocked = list.filter { it.inPantry }
                if (stocked.isNotEmpty()) Text("In your pantry, so not on the list: ${stocked.joinToString(", ") { it.name }}", fontSize = 12.sp, color = p.muted)
            } else if (pantryOff) {
                ComingSoonCard("Pantry", "Keep what's at home here, and it drops off your grocery list.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(48.dp).background(p.card, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            name, { name = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Pantry item" }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { val n = name; name = ""; scope.launch { runCatching { FoodApi.addPantry(n, pantry.orEmpty()) }.onFailure { error = it.message }; reload() } }),
                            textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                            decorationBox = { inner -> if (name.isEmpty()) Text("Add: atta, toor dal, paneer…", fontSize = 15.sp, color = p.muted); inner() },
                        )
                    }
                    PillButton("Add", { val n = name; name = ""; scope.launch { runCatching { FoodApi.addPantry(n, pantry.orEmpty()) }.onFailure { error = it.message }; reload() } }, Modifier.width(80.dp), enabled = name.isNotBlank(), height = 48.dp)
                }
                Card(padding = 8.dp) {
                    if (pantry == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.ink)
                    else if (pantry!!.isEmpty()) Text("Add what you keep at home. Items in stock drop off your grocery list.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(8.dp))
                    pantry.orEmpty().forEachIndexed { i, it ->
                        if (i > 0) Hair()
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it.name, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight(700), color = if (it.inStock) p.ink else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            SmallChip(if (it.inStock) "In stock" else "Ran out", { scope.launch { runCatching { FoodApi.setPantryStock(it.id, !it.inStock) }; reload() } }, filled = it.inStock)
                            Box(Modifier.size(44.dp).clickable { scope.launch { runCatching { FoodApi.deletePantry(it.id) }; reload() } }.semantics { contentDescription = "Remove ${it.name}" }, contentAlignment = Alignment.Center) {
                                Icon(CrossIcon, null, tint = p.muted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- A2 on the Recipes page

/** Ghar ka khana + "Say a recipe", under "New recipe". */
@Composable
fun RecipeTopLinks(vm: AppViewModel, nvm: NutritionViewModel) {
    val p = palette
    var voiceOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinkTile(Modifier.weight(1f), "Ghar ka khana", "Home recipes, by the katori", NutritionIcons.ChefHat) { FoodNav.page = FoodPage.Library }
        LinkTile(Modifier.weight(1f), "Say a recipe", "Mom's dal, by voice", NutritionIcons.Sparkles) { voiceOpen = true }
    }
    if (voiceOpen) VoiceRecipeSheet(nvm) { voiceOpen = false }
    SquadRecipes(vm, nvm)
}

@Composable
private fun LinkTile(modifier: Modifier, title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = palette
    Row(modifier.heightIn(min = 64.dp).background(p.card, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = p.ink, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink)
            Text(sub, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Mom's dal: 1 katori toor dal, 1 spoon ghee, serves 4" → priced by /api/parse-meal → a recipe to review. */
@Composable
fun VoiceRecipeSheet(nvm: NutritionViewModel, onDismiss: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val voice = rememberVoiceNote()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(voice.text) { if (voice.text.isNotBlank()) typed = voice.text }
    val parsed = HomeRecipes.parseVoiceRecipe(typed)
    BottomSheet(
        "Say a recipe", onDismiss, subtitle = "Name, then the ingredients with amounts, then how many it serves.",
        primary = if (busy) "Working it out…" else "Work it out and save", primaryEnabled = !busy && typed.isNotBlank(),
        onPrimary = {
            val r = HomeRecipes.parseVoiceRecipe(typed)
            if (r.ingredients.isBlank()) error = "Say the ingredients too: “1 katori toor dal, 1 spoon ghee”."
            else scope.launch {
                busy = true; error = null
                try {
                    val res = Api.parseMeal(r.ingredients)
                    val items = res.items.filter { it.grams > 0 }.map { HomeRecipes.ingredientFromItem(it) }
                    if (items.isEmpty()) throw Exception("Couldn't work out those ingredients. Try naming amounts: “2 katori rice, 1 spoon ghee”.")
                    val saved = nvm.saveRecipe(Recipe(null, r.name.ifBlank { "My recipe" }, (r.servings ?: 2).toDouble(), null, items, "Said: ${typed.take(200)}"))
                    if (saved == null) throw Exception(nvm.message ?: "Couldn't save the recipe")
                    onDismiss()
                    nvm.page = NutritionPage.RecipeEdit(saved)
                } catch (e: Exception) { error = e.message ?: "Couldn't save that" } finally { busy = false }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (voice.dictation.available) { HoldMic(voice, dark = false, size = 44.dp, label = "Hold to say the recipe"); LangToggle(voice, dark = false) }
                Text(if (voice.dictation.listening) "Listening…" else if (voice.dictation.available) "Hold the mic, or type below" else "Type it below", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(start = 8.dp))
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 90.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(12.dp)) {
                BasicTextField(
                    typed, { typed = it; voice.text = it }, Modifier.fillMaxWidth().semantics { contentDescription = "The recipe" },
                    textStyle = TextStyle(fontSize = 14.sp, color = p.ink, lineHeight = 20.sp), cursorBrush = SolidColor(p.ink),
                    decorationBox = { inner -> if (typed.isEmpty()) Text("Mom's dal: 1 katori toor dal, 1 spoon ghee, 1 tomato, serves 4", fontSize = 14.sp, color = p.muted, lineHeight = 20.sp); inner() },
                )
            }
            if (parsed.name.isNotBlank() || parsed.servings != null) Text(
                (parsed.name.ifBlank { "No name yet" }) + (parsed.servings?.let { " · serves $it" } ?: " · serves 2 (change it after)"),
                fontSize = 12.sp, color = p.muted,
            )
            if (busy) Text("Pricing each ingredient (the web checks anything the food table doesn't know)…", fontSize = 12.sp, color = p.muted)
            ErrorNote(error)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Recipes squadmates shared (hidden before schema_v42 or when there are none). */
@Composable
private fun SquadRecipes(vm: AppViewModel, nvm: NutritionViewModel) {
    val p = palette
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<FoodApi.SharedRecipe>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { list = runCatching { FoodApi.sharedRecipes(Api.mySquads()) }.getOrDefault(emptyList()).filter { !it.mine } }
    if (list.isEmpty()) return
    Card(padding = 8.dp) {
        Text("From your squads", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        list.forEachIndexed { i, r ->
            if (i > 0) Hair()
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("${r.authorName ?: "A squadmate"} · ${r.squad}" + (if (vm.profile.hideNumbers == true) "" else " · ${r.kcal} kcal") + " · ${fmt(r.protein)} g protein", fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (busy == r.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.ink)
                else SmallChip("Save a copy", {
                    scope.launch {
                        busy = r.id
                        val saved = nvm.saveRecipe(FoodApi.sharedAsRecipe(r))
                        busy = null
                        if (saved != null) nvm.page = NutritionPage.RecipeEdit(saved)
                    }
                }, filled = true)
            }
        }
    }
}

/** "Share to squad" on a saved recipe (hidden when there are no squads). */
@Composable
fun ShareRecipeButton(vm: AppViewModel, recipe: Recipe) {
    val p = palette
    val scope = rememberCoroutineScope()
    var squads by remember { mutableStateOf<List<com.sohum.bandlog.data.Squad>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { squads = runCatching { Api.mySquads() }.getOrDefault(emptyList()) }
    if (squads.isEmpty() || recipe.id == null) return
    PillButton(done ?: "Share to squad", { open = true }, height = 46.dp, bg = p.card2, fg = p.ink, icon = ShareIcon)
    if (open) BottomSheet("Share to a squad", { open = false }, subtitle = "Squadmates see it under Recipes and can save a copy.") {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            squads.forEach { s ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).background(p.card2, RoundedCornerShape(12.dp)).clickable {
                        scope.launch {
                            try { FoodApi.shareRecipe(recipe, s.id, vm.profile.name.ifBlank { null }); done = "Shared with ${s.name}"; open = false }
                            catch (e: NotYetAvailable) { error = "Sharing recipes is coming with the next update." }
                            catch (e: Exception) { error = e.message ?: "Couldn't share it" }
                        }
                    }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(s.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink) }
            }
            ErrorNote(error)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A MealItem list's total kcal, rounded (for sheet subtitles). */
fun kcalOf(items: List<MealItem>): Int = items.sumOf { it.calories }.roundToInt()
