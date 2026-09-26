@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sohum.bandlog.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.FoodHit
import com.sohum.bandlog.data.Recipe
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CrossIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.MacroDot
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SearchIcon
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.motion.Entrance
import com.sohum.bandlog.ui.motion.MotionScreen
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.ui.today.fmt
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.Recipes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** §8 Log → Recipes: your recipes with per-serving numbers, "Log a serving", edit and a new one. */
@Composable
fun RecipesScreen(vm: AppViewModel, nvm: NutritionViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { nvm.loadRecipes() }
    var logging by remember { mutableStateOf<String?>(null) }
    MotionScreen {
        SubPage("Recipes", onBack, pro = true) {
            NoticeLine(nvm)
            if (nvm.recipesUnavailable) { Entrance(0) { ComingSoonCard("Recipe builder") }; return@SubPage }
            Entrance(0, key = "new") {
                PillButton("New recipe", { nvm.page = NutritionPage.RecipeEdit(null) }, icon = com.sohum.bandlog.ui.components.PlusIcon)
            }
            if (!nvm.recipesLoaded) LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
            else if (nvm.recipes.isEmpty()) Entrance(1, key = "empty") {
                Card {
                    Text("Build it once, log it in one tap", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Add the ingredients of a dish you make often (with grams), say how many servings it makes, and we'll work out each serving.", fontSize = 13.sp, color = p.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            nvm.recipes.forEachIndexed { i, r ->
                Entrance(1 + i.coerceAtMost(6), key = "r-${r.id ?: r.name}") {
                    val ps = r.perServing
                    Card(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).background(p.orangeBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(NutritionIcons.ChefHat, null, tint = p.orange, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${r.items.size} ingredient${if (r.items.size == 1) "" else "s"} · ${fmt(r.servings)} serving${if (r.servings == 1.0) "" else "s"}", fontSize = 12.sp, color = p.muted)
                            }
                            Box(Modifier.heightIn(min = 44.dp).clickable { nvm.page = NutritionPage.RecipeEdit(r) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Edit", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${ps.kcal.roundToInt()} kcal / serving", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
                            MacroDot("${fmt(ps.protein)}g P", p.red); MacroDot("${fmt(ps.carbs)}g C", p.orange); MacroDot("${fmt(ps.fat)}g F", p.blue)
                        }
                        Spacer(Modifier.height(10.dp))
                        PillButton(
                            if (logging == r.id) "Logging…" else "Log a serving to ${MealTypes.label(MealTypes.default())}", height = 44.dp, enabled = logging == null,
                            onClick = { scope.launch { logging = r.id; nvm.logRecipe(vm, r); logging = null } },
                        )
                    }
                }
            }
        }
    }
}

/** One ingredient row being edited: grams as text so the field can be empty while typing. */
private data class Draft(val base: Recipes.Ingredient, val gramsText: String) {
    /** Re-priced at the typed grams (per-100 g when known, else linear), like the web's priceIngredient. */
    fun priced(): Recipes.Ingredient = Recipes.price(base, gramsText.toDoubleOrNull()?.coerceIn(0.0, 5000.0) ?: 0.0)
}

/** §8 the builder: name, ingredients from the food search with grams, servings and an optional cooked weight. */
@Composable
fun RecipeEditor(vm: AppViewModel, nvm: NutritionViewModel, recipe: Recipe?, onDone: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(recipe?.name ?: "") }
    var servings by remember { mutableStateOf(recipe?.servings?.let { fmt(it) } ?: "2") }
    var cooked by remember { mutableStateOf(recipe?.cookedWeightG?.let { fmt(it) } ?: "") }
    var drafts by remember { mutableStateOf(recipe?.items.orEmpty().map { Draft(it, fmt(it.grams)) }) }
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<FoodHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(250); searching = true
        runCatching { Api.searchFoods(q.trim(), 8) }.onSuccess { hits = it; error = null }.onFailure { error = it.message }
        searching = false
    }
    val items = drafts.map { it.priced() }.filter { it.grams > 0 }
    val s = servings.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    val cw = cooked.toDoubleOrNull()?.takeIf { it > 0 }
    val ps = Recipes.perServing(items, s, cw)
    val total = Recipes.totals(items)

    SubPage(if (recipe == null) "New recipe" else "Edit recipe", onDone) {
        ErrorNote(error ?: nvm.message)
        Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Field("Name", name, "e.g. Moong dal chilla", KeyboardType.Text) { name = it.take(60) }
                Hair()
                Field("Servings it makes", servings, "2", KeyboardType.Decimal) { servings = it.filter { c -> c.isDigit() || c == '.' }.take(5) }
                Hair()
                Field("Cooked weight (optional)", cooked, "g", KeyboardType.Decimal) { cooked = it.filter { c -> c.isDigit() || c == '.' }.take(6) }
            }
        }
        Text("Ingredients", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink, modifier = Modifier.padding(start = 4.dp))
        // The same food search as logging.
        Row(
            Modifier.fillMaxWidth().height(50.dp).background(p.card, RoundedCornerShape(25.dp)).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SearchIcon, null, tint = p.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                q, { q = it.take(80) }, Modifier.weight(1f).semantics { contentDescription = "Search foods to add" }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
                decorationBox = { inner -> if (q.isEmpty()) Text("Add an ingredient…", fontSize = 15.sp, color = p.muted); inner() },
            )
            if (q.isNotEmpty()) Box(Modifier.size(44.dp).clickable { q = "" }, contentAlignment = Alignment.Center) { Icon(CrossIcon, "Clear", tint = p.muted, modifier = Modifier.size(12.dp)) }
        }
        if (searching && hits.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.ink, trackColor = p.track)
        if (hits.isNotEmpty()) Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                hits.forEachIndexed { i, h ->
                    if (i > 0) Hair()
                    val g = h.units.firstOrNull()?.grams ?: 100.0
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                            drafts = drafts + Draft(Recipes.fromPer100(h.name, g, h.calories, h.proteinG, h.carbsG, h.fatG, h.micros, h.id), fmt(g))
                            q = ""; hits = emptyList()
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${h.calories.roundToInt()} kcal · ${fmt(h.proteinG)} g protein per 100 g", fontSize = 12.sp, color = p.muted)
                        }
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (drafts.isNotEmpty()) Card(padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                drafts.forEachIndexed { i, d ->
                    if (i > 0) Hair()
                    val it = d.priced()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${it.kcal.roundToInt()} kcal · ${fmt((it.protein * 10).roundToInt() / 10.0)} g P", fontSize = 12.sp, color = p.muted)
                        }
                        Box(Modifier.width(76.dp).height(40.dp).background(p.card2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
                            BasicTextField(
                                d.gramsText, { v -> drafts = drafts.toMutableList().also { l -> l[i] = d.copy(gramsText = v.filter { c -> c.isDigit() || c == '.' }.take(6)) } },
                                Modifier.fillMaxWidth().semantics { contentDescription = "Grams of ${it.name}" }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.End), cursorBrush = SolidColor(p.ink),
                            )
                        }
                        Text(" g", fontSize = 13.sp, color = p.muted)
                        Box(Modifier.size(44.dp).clickable { drafts = drafts.filterIndexed { j, _ -> j != i } }, contentAlignment = Alignment.Center) {
                            Icon(CrossIcon, "Remove ${it.name}", tint = p.muted, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
        // Totals and one serving.
        Card {
            Text("Per serving", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted)
            Text("${ps.kcal.roundToInt()} kcal", fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroDot("${fmt(ps.protein)}g protein", p.red); MacroDot("${fmt(ps.carbs)}g carbs", p.orange); MacroDot("${fmt(ps.fat)}g fat", p.blue)
                if (ps.fiber > 0) MacroDot("${fmt(ps.fiber)}g fibre", p.green)
            }
            Text(
                "Whole recipe: ${total.kcal.roundToInt()} kcal, ${fmt(total.grams)} g raw" + (Recipes.per100Cooked(items, cw)?.let { " · ${it.roundToInt()} kcal per 100 g cooked" } ?: "") +
                    " · one serving ≈ ${ps.grams.roundToInt()} g",
                fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
        PillButton(
            if (saving) "Saving…" else "Save recipe", enabled = !saving && name.isNotBlank() && items.isNotEmpty(),
            onClick = {
                scope.launch {
                    saving = true
                    val saved = nvm.saveRecipe(Recipe(recipe?.id, name.trim(), s, cw, items, recipe?.note.orEmpty()))
                    saving = false
                    if (saved != null) onDone()
                }
            },
        )
        if (recipe?.id != null) Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
            Text("Delete recipe", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.red)
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = p.card,
        title = { Text("Delete “${recipe?.name}”?", fontWeight = FontWeight(800), color = p.ink) },
        text = { Text("Meals you already logged from it stay in your history.", color = p.muted) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { if (recipe?.id != null && nvm.deleteRecipe(recipe.id)) onDone() } }) { Text("Delete", color = p.red, fontWeight = FontWeight(700)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it", color = p.ink) } },
    )
}

@Composable
private fun Field(label: String, value: String, placeholder: String, type: KeyboardType, onChange: (String) -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
        BasicTextField(
            value, onChange, Modifier.width(170.dp).semantics { contentDescription = label }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.End), cursorBrush = SolidColor(p.ink),
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = p.muted); inner() } },
        )
    }
}
