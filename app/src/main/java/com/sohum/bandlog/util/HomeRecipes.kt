package com.sohum.bandlog.util

import android.content.Context
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Serving
import org.json.JSONArray
import kotlin.math.floor

/**
 * Mirrors the web's src/lib/food/homeRecipes.ts (v2.18 A2 "Ghar ka khana"; the web copy wins): the
 * built-in library of common Indian home recipes (assets/home_recipes.json, the same file as the
 * web's src/lib/food/home_recipes.json). Each entry is ONE serving in its household unit (1 katori =
 * 150 g of dal, 1 roti = 40 g, 1 glass = 250 ml…) with home-style oil / ghee already in the numbers.
 *
 * Also the voice recipe parser: "Mom's dal: 1 katori toor dal, 1 spoon ghee, serves 4" → a name,
 * the ingredient text and the servings. Same cases as scripts/check-v218-food.ts ↔ HomeRecipesTest.
 */
object HomeRecipes {
    /** unit: katori | roti | piece | plate | glass | cup | bowl. category: dal | sabzi | nonveg | sides | rice | bread | breakfast | snack | drink | sweet. */
    data class HomeRecipe(
        val id: String,
        val name: String,
        val local: String,
        val category: String,
        val unit: String,
        val unitGrams: Double,
        val kcal: Double,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val fiberG: Double,
        val ingredients: List<String>,
        val aliases: List<String>,
    )

    val HOME_CATEGORIES = listOf(
        "dal" to "Dal & curry", "sabzi" to "Sabzi", "nonveg" to "Non-veg", "rice" to "Rice", "bread" to "Roti & paratha",
        "breakfast" to "Breakfast", "sides" to "Curd & sides", "snack" to "Snacks", "drink" to "Drinks", "sweet" to "Sweets",
    )

    @Volatile private var cache: List<HomeRecipe>? = null

    /** The library from assets (parsed once per process). */
    fun load(context: Context): List<HomeRecipe> {
        cache?.let { return it }
        val list = runCatching {
            context.applicationContext.assets.open("home_recipes.json").bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }.getOrElse { emptyList() }
        if (list.isNotEmpty()) cache = list
        return list
    }

    /** The library JSON (an array of recipes) → recipes; malformed entries are skipped. */
    fun parse(json: String): List<HomeRecipe> {
        val arr = JSONArray(json)
        fun strings(a: JSONArray?): List<String> = a?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            HomeRecipe(
                id = id,
                name = o.optString("name"),
                local = o.optString("local"),
                category = o.optString("category"),
                unit = o.optString("unit"),
                unitGrams = o.optDouble("unit_grams", 0.0),
                kcal = o.optDouble("kcal", 0.0),
                proteinG = o.optDouble("protein_g", 0.0),
                carbsG = o.optDouble("carbs_g", 0.0),
                fatG = o.optDouble("fat_g", 0.0),
                fiberG = o.optDouble("fiber_g", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                ingredients = strings(o.optJSONArray("ingredients")),
                aliases = strings(o.optJSONArray("aliases")),
            )
        }
    }

    /** Home recipes on a spoonable unit are counted in halves ("½ katori"); pieces in whole numbers. */
    fun servingStep(r: HomeRecipe): Double = servingStep(r.unit)

    fun servingStep(unit: String): Double = if (unit == "katori" || unit == "bowl" || unit == "plate" || unit == "glass" || unit == "cup") 0.5 else 1.0

    fun unitLabel(r: HomeRecipe, n: Double = 1.0): String = unitLabel(r.unit, n)

    fun unitLabel(unit: String, n: Double = 1.0): String {
        val noun = if (unit == "piece") "piece" else unit
        val many = if (n == 1.0 || n == 0.5) noun else if (noun == "glass") "glasses" else "${noun}s"
        val count = when {
            n == 0.5 -> "½"
            n == floor(n) && n.isFinite() -> n.toLong().toString()
            else -> String.format(java.util.Locale.ROOT, "%.1f", n).removeSuffix(".0")
        }
        return "$count $many"
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** Search by name, local name or alias (every query word must appear). Empty query = everything. */
    fun search(q: String, list: List<HomeRecipe>): List<HomeRecipe> {
        val words = norm(q).split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        val joined = words.joinToString(" ")
        return list.mapNotNull { r ->
            val hay = norm((listOf(r.name, r.local) + r.aliases).joinToString(" "))
            if (!words.all { hay.contains(it) }) return@mapNotNull null
            val exact = norm(r.name) == joined || r.aliases.any { norm(it) == joined }
            r to ((if (exact) 10 else 0) + (if (norm(r.name).startsWith(words[0])) 2 else 0))
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun jsRound(v: Double) = floor(v + 0.5)
    private fun r1(v: Double) = jsRound(v * 10) / 10

    /** [servings] of a home recipe as one meal item (numbers from the library, counted in its unit). */
    fun homeRecipeItem(r: HomeRecipe, servings: Double = 1.0): MealItem {
        val n = if (servings > 0) servings else 1.0
        return MealItem(
            foodId = null,
            name = r.name,
            grams = jsRound(r.unitGrams * n),
            calories = jsRound(r.kcal * n),
            proteinG = r1(r.proteinG * n),
            carbsG = r1(r.carbsG * n),
            fatG = r1(r.fatG * n),
            source = "table",
            confidence = 0.8,
            micros = if (r.fiberG != 0.0) mapOf("fiber_g" to r1(r.fiberG * n)) else emptyMap(),
            unit = "serving",
            servings = n,
            servingUnit = Serving("1 ${r.unit}", r.unitGrams),
            cookedIn = null,
        )
    }

    data class OwnRecipe(val name: String, val servings: Double, val cookedWeightG: Double, val items: List<Recipes.Ingredient>, val perServing: Recipes.Totals, val note: String)

    /** A library entry as the person's own recipe (one serving = one unit), ready for bandlog.recipes. */
    /** A priced meal item (from /api/parse-meal) as a recipe ingredient, with per-100 g so grams edits re-price it (web ingredientFromItem). */
    fun ingredientFromItem(it: MealItem): Recipes.Ingredient {
        val g = it.grams
        fun per(v: Double) = if (g > 0) r1(v * 100 / g) else 0.0
        return Recipes.Ingredient(
            name = it.name, grams = g, kcal = jsRound(it.calories), protein = r1(it.proteinG), carbs = r1(it.carbsG), fat = r1(it.fatG),
            fiber = r1(it.micros["fiber_g"] ?: 0.0), foodId = it.foodId, micros = it.micros,
            per100 = if (g > 0) Recipes.Per100(per(it.calories), per(it.proteinG), per(it.carbsG), per(it.fatG), it.micros.mapValues { (_, v) -> per(v) }) else null,
        )
    }

    fun homeRecipeAsOwn(r: HomeRecipe): OwnRecipe {
        val micros = if (r.fiberG != 0.0) mapOf("fiber_g" to r.fiberG) else emptyMap()
        val item = Recipes.Ingredient(
            name = "${r.name} (1 ${r.unit})", grams = r.unitGrams, kcal = r.kcal, protein = r.proteinG, carbs = r.carbsG, fat = r.fatG,
            fiber = r.fiberG, foodId = null, micros = micros,
            per100 = Recipes.Per100(r.kcal * 100 / r.unitGrams, r.proteinG * 100 / r.unitGrams, r.carbsG * 100 / r.unitGrams, r.fatG * 100 / r.unitGrams),
        )
        return OwnRecipe(
            name = r.name, servings = 1.0, cookedWeightG = r.unitGrams, items = listOf(item),
            perServing = Recipes.Totals(r.kcal, r.proteinG, r.carbsG, r.fatG, r.fiberG, r.unitGrams, micros),
            note = "From the Ghar ka khana library: ${r.ingredients.joinToString(", ")}.",
        )
    }

    // ---- voice recipes ----

    private val SERVE_WORDS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "eight" to 8, "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "chhe" to 6)

    data class VoiceRecipe(val name: String, val ingredients: String, val servings: Int?)

    private val SERVE_A = JsRe.re("[,.]?\\s*\\b(?:serves|for|makes|feeds)\\s+([0-9]+|[a-z]+)(?:\\s+(?:people|persons|log|servings?|portions?|plates?))?\\b[.,]?")
    private val SERVE_B = JsRe.re("[,.]?\\s*\\b([0-9]+|[a-z]+)\\s+(?:servings?|portions?|people)\\b[.,]?")
    private val COLON = Regex("^(.{2,60}?)\\s*(?::|—|–| - )\\s*(.+)$")
    private val FIRST_AMOUNT = JsRe.re("\\b([0-9]|ek|do|teen|char|aadha|half|one|two|three|a\\s+(?:katori|spoon|cup|bowl|glass))\\b")
    private val LEAD_WORDS = JsRe.re("^(recipe|save|new recipe|my recipe)\\s*(for|:)?\\s*")
    private val SPACES = Regex("\\s+")

    /**
     * "Mom's dal: 1 katori toor dal, 1 spoon ghee, serves 4" → name "Mom's dal", ingredients
     * "1 katori toor dal, 1 spoon ghee", servings 4. The name is what comes before a colon or a dash;
     * without one, the words before the first amount. "Serves 4", "for 4 people", "4 servings",
     * "makes 6" set the servings.
     */
    fun parseVoiceRecipe(text: String?): VoiceRecipe {
        var t = text.orEmpty().replace(SPACES, " ").trim()
        var servings: Int? = null
        // Every "serves 4" / "for 4 people" / "4 servings", in order; the first with a real number wins
        // ("coriander for garnish … serves 4" → 4).
        val found = (SERVE_A.findAll(t) + SERVE_B.findAll(t)).sortedBy { it.range.first }
        for (serve in found) {
            val w = serve.groupValues[1].lowercase()
            val n = if (w.all { it in '0'..'9' }) w.toIntOrNull() else SERVE_WORDS[w]
            if (n != null && n > 0 && n <= 50) {
                servings = n
                t = (t.substring(0, serve.range.first) + t.substring(serve.range.last + 1)).replace(SPACES, " ").trim()
                break
            }
        }
        var name = ""
        var rest = t
        val colon = COLON.find(t)
        if (colon != null) {
            name = colon.groupValues[1]
            rest = colon.groupValues[2]
        } else {
            val firstAmount = FIRST_AMOUNT.find(t)?.range?.first ?: -1
            if (firstAmount > 2) {
                name = t.substring(0, firstAmount)
                rest = t.substring(firstAmount)
            }
        }
        name = name.replace(LEAD_WORDS, "").replace(Regex("[,.:\\s]+$"), "").trim()
        rest = rest.replace(Regex("^[,.:\\s]+|[,.\\s]+$"), "").trim()
        return VoiceRecipe(if (name.isNotEmpty()) name.replaceFirstChar { it.uppercase() } else "", rest, servings)
    }
}
