package com.sohum.bandlog.util

import java.text.Collator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Mirrors the web's src/lib/food/grocery.ts (v2.18 A11 pantry + weekly grocery list; the web copy wins).
 *
 * The list is built from what the person actually eats (logged foods mapped to what you buy: roti →
 * atta, dal tadka → toor dal, biryani → rice…) scaled to one week, plus protein staples to fill the
 * weekly protein gap (diet-mode aware), and produce. Pantry items in stock show as "In your pantry".
 * Same cases as scripts/check-v218-food.ts ↔ GroceryTest.
 */
object Grocery {
    /** staples | protein | dairy | produce | other */
    data class GroceryItem(val key: String, val name: String, val qty: String, val category: String, var why: String, val inPantry: Boolean)
    data class EatenFood(val name: String, val grams: Double)
    data class PantryRow(val name: String, val inStock: Boolean)

    /** [ratio]: grams of the bought thing per gram eaten. [unit]: kg | g | L | pcs | pack. */
    private class Buy(val key: String, val name: String, val category: String, val ratio: Double, val unit: String, val per: Double? = null)

    private fun jsRound(v: Double) = floor(v + 0.5)

    /** Eaten food → what to buy. First match wins. */
    private val MAP: List<Pair<Regex, Buy>> = listOf(
        "\\b(roti|chapati|phulka|paratha|thepla|puri|poori|tandoori roti)\\b" to Buy("atta", "Whole wheat atta", "staples", 0.7, "kg"),
        "\\b(rice|chawal|pulao|biryani|khichdi|curd rice|lemon rice)\\b" to Buy("rice", "Rice", "staples", 0.4, "kg"),
        "\\b(idli|dosa|uttapam|appam)\\b" to Buy("batter", "Idli / dosa batter", "staples", 1.0, "kg"),
        "\\bpoha\\b" to Buy("poha", "Poha", "staples", 0.45, "kg"),
        "\\b(upma|rava|suji)\\b" to Buy("rava", "Rava (suji)", "staples", 0.4, "kg"),
        "\\boats|daliya|porridge\\b" to Buy("oats", "Oats", "staples", 0.3, "kg"),
        "\\bbread|toast|sandwich\\b" to Buy("bread", "Bread", "staples", 1.0, "pack", 400.0),
        "\\b(moong dal|moong|pesarattu|sprouts)\\b" to Buy("moong", "Moong dal", "protein", 0.35, "kg"),
        "\\b(dal|daal|sambar|sambhar|tadka|masoor)\\b" to Buy("toor", "Toor dal", "protein", 0.35, "kg"),
        "\\brajma\\b" to Buy("rajma", "Rajma", "protein", 0.35, "kg"),
        "\\b(chole|chana|chickpea)\\b" to Buy("chana", "Kabuli / kala chana", "protein", 0.35, "kg"),
        "\\b(besan|chilla|cheela|dhokla|kadhi|pakora|pakoda)\\b" to Buy("besan", "Besan", "staples", 0.4, "kg"),
        "\\bpaneer\\b" to Buy("paneer", "Paneer", "dairy", 0.8, "g"),
        "\\b(egg|eggs|anda|omelette|bhurji)\\b" to Buy("eggs", "Eggs", "protein", 1.0, "pcs", 50.0),
        "\\b(chicken|murgh)\\b" to Buy("chicken", "Chicken", "protein", 0.8, "kg"),
        "\\b(fish|machli|prawn)\\b" to Buy("fish", "Fish", "protein", 0.8, "kg"),
        "\\b(mutton|keema|gosht)\\b" to Buy("mutton", "Mutton", "protein", 0.8, "kg"),
        "\\bsoya|nutrela\\b" to Buy("soya", "Soya chunks", "protein", 0.35, "g"),
        "\\b(whey|protein shake|protein powder)\\b" to Buy("whey", "Whey protein", "protein", 1.0, "g"),
        "\\b(curd|dahi|yogurt|raita|chaas|buttermilk|lassi)\\b" to Buy("curd", "Curd", "dairy", 0.9, "kg"),
        "\\b(milk|chai|tea|coffee|kaapi|shake)\\b" to Buy("milk", "Milk", "dairy", 0.6, "L"),
        "\\bbanana|kela\\b" to Buy("banana", "Bananas", "produce", 1.0, "pcs", 120.0),
        "\\bapple\\b" to Buy("apple", "Apples", "produce", 1.0, "pcs", 180.0),
        "\\bpeanut butter\\b" to Buy("pb", "Peanut butter", "other", 1.0, "g"),
        "\\b(peanut|mungfali)\\b" to Buy("peanuts", "Peanuts", "other", 1.0, "g"),
    ).map { (p, b) -> JsRe.re(p) to b }

    /** veg | egg | nonveg | vegan | jain */
    private class Fill(val key: String, val name: String, val per100: Double, val unit: String, val per: Double?, val diets: List<String>, val category: String)

    /** Protein staples to cover a weekly gap, best first, with protein per 100 g. */
    private val PROTEIN_FILL = listOf(
        Fill("paneer", "Paneer", 18.0, "g", null, listOf("veg", "egg", "nonveg", "jain"), "dairy"),
        Fill("soya", "Soya chunks", 52.0, "g", null, listOf("veg", "egg", "nonveg", "vegan", "jain"), "protein"),
        Fill("eggs", "Eggs", 13.0, "pcs", 50.0, listOf("egg", "nonveg"), "protein"),
        Fill("chicken", "Chicken breast", 31.0, "kg", null, listOf("nonveg"), "protein"),
        Fill("greek", "Greek curd / hung curd", 9.0, "g", null, listOf("veg", "egg", "nonveg", "jain"), "dairy"),
        Fill("chana", "Kala chana", 20.0, "g", null, listOf("veg", "egg", "nonveg", "vegan", "jain"), "protein"),
    )

    private fun fmtQty(grams: Double, unit: String, per: Double?): String = when {
        unit == "pcs" -> "${max(1.0, jsRound(grams / (per ?: 100.0))).toLong()}"
        unit == "pack" -> {
            val n = ceil(grams / (per ?: 400.0))
            "${max(1.0, n).toLong()} pack${if (n > 1) "s" else ""}"
        }
        unit == "L" -> "~${FoodBits.num(max(0.5, jsRound(grams / 500) / 2))} L"
        unit == "kg" && grams >= 750 -> "~${FoodBits.num(jsRound(grams / 250) / 4)} kg"
        else -> "~${max(50.0, jsRound(grams / 50) * 50).toLong()} g"
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z ]+"), " ").replace(Regex("\\s+"), " ").trim()

    fun inPantry(name: String, pantry: List<PantryRow>): Boolean {
        val n = norm(name)
        return pantry.any { p ->
            if (!p.inStock) return@any false
            val q = norm(p.name)
            if (q.isEmpty()) return@any false
            n == q || n.contains(q) || q.contains(n) || n.split(" ").any { w -> w.length > 3 && q.split(" ").contains(w) }
        }
    }

    private val ORDER = listOf("staples", "protein", "dairy", "produce", "other")

    /**
     * The week's list. [eaten] is everything logged in the last [days] days (grams as eaten);
     * [proteinTarget] / [avgProtein] are per day. Sorted staples → protein → dairy → produce → other.
     */
    fun groceryList(eaten: List<EatenFood>, days: Int, proteinTarget: Double, avgProtein: Double, pantry: List<PantryRow>, diet: String? = null): List<GroceryItem> {
        val d = max(1, days)
        val need = LinkedHashMap<String, Pair<Buy, Double>>()
        for (e in eaten) {
            val g = if (e.grams.isFinite()) e.grams else 0.0
            if (!(g > 0)) continue
            val b = MAP.find { it.first.containsMatchIn(e.name) }?.second ?: continue
            val cur = need[b.key]?.second ?: 0.0
            need[b.key] = b to (cur + g * b.ratio)
        }
        val out = ArrayList<GroceryItem>()
        for ((buy, grams) in need.values) {
            val week = grams / d * 7
            if (week < 20) continue
            out.add(GroceryItem(buy.key, buy.name, fmtQty(week, buy.unit, buy.per), buy.category, "You eat this most weeks", inPantry(buy.name, pantry)))
        }
        // Protein gap for the week.
        val gap = jsRound((proteinTarget - avgProtein) * 7)
        if (proteinTarget > 0 && gap >= 70) {
            val dk = diet ?: "veg"
            val fills = PROTEIN_FILL.filter { dk in it.diets }.take(2)
            for (f in fills) {
                val grams = (gap / fills.size) * 100 / f.per100
                val existing = out.find { it.key == f.key }
                val why = "Covers ~${jsRound(gap / fills.size).toLong()} g of your weekly protein gap"
                if (existing != null) existing.why = "${existing.why} · ${why.lowercase()}"
                else out.add(GroceryItem(f.key, f.name, fmtQty(grams, f.unit, f.per), f.category, why, inPantry(f.name, pantry)))
            }
        }
        if (out.none { it.category == "produce" && it.key == "veg" }) out.add(GroceryItem("veg", "Seasonal vegetables", "~3 kg", "produce", "Fibre and micros, every week", false))
        if (out.none { it.key == "fruit" }) out.add(GroceryItem("fruit", "Fruit", "7", "produce", "One a day", false))
        val coll = Collator.getInstance(Locale.ENGLISH)
        return out.sortedWith { a, b ->
            val c = ORDER.indexOf(a.category) - ORDER.indexOf(b.category)
            if (c != 0) c else coll.compare(a.name, b.name)
        }
    }

    /** The list as plain text for sharing / copying (in-pantry items left out). */
    fun groceryText(list: List<GroceryItem>): String =
        list.filter { !it.inPantry }.joinToString("\n") { "☐ ${it.name} — ${it.qty}" }

    val PANTRY_CATEGORIES = listOf("staples" to "Staples", "protein" to "Protein", "dairy" to "Dairy", "produce" to "Fruit & veg", "other" to "Other")

    private val PRODUCE = JsRe.re("veg|onion|tomato|potato|aloo|spinach|palak|carrot|fruit|lemon|ginger|garlic|chilli")

    /** A pantry item's category from its name (for rows the person adds by name). */
    fun pantryCategory(name: String): String {
        MAP.find { it.first.containsMatchIn(name) }?.let { return it.second.category }
        if (PRODUCE.containsMatchIn(name)) return "produce"
        return "other"
    }
}
