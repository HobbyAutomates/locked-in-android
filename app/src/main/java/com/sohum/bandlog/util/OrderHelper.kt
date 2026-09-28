package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.MenuDish
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Mirrors the web's src/lib/food/orderHelper.ts (v2.18 A4 restaurant and delivery helper; the web
 * copy wins). A pasted Swiggy / Zomato order becomes dishes with a quantity; the plan says how much
 * of each to eat for what's left today, and "Pre-log" saves exactly that plan.
 *
 *   parseOrderText   "2 x Butter Naan ₹120", "Paneer Tikka x 1", "Veg Biryani (Qty 1)" → name + qty;
 *                    fees, taxes, totals, coupons and addresses are skipped.
 *   orderPlan        the whole order when it fits (≤ 110 % of the kcal left); otherwise dishes in
 *                    protein-density order, each at the biggest quarter that still fits.
 *
 * The menu maths (mid kcal / protein, dish → meal item) reuse data/NutritionModels.kt MenuDish.
 * Same cases as scripts/check-v218-food.ts ↔ OrderHelperTest.
 */
object OrderHelper {
    data class OrderLine(val name: String, val qty: Int)
    data class OrderDish(val dish: MenuDish, val qty: Int)
    data class PlanRow(val index: Int, val eat: Double, val kcal: Int, val protein: Int, val line: String)
    data class OrderPlan(val totalKcal: Int, val totalProtein: Int, val planKcal: Int, val planProtein: Int, val fits: Boolean, val rows: List<PlanRow>, val headline: String)

    private fun jsRound(v: Double) = floor(v + 0.5)

    private val SKIP = JsRe.re(
        "\\b(item total|sub ?total|total|grand total|to pay|paid|bill|delivery|packing|packaging|platform|gst|tax|taxes|charges?|fee|fees|discount|coupon|offer|saved|savings|tip|donation|order\\s*#?|order id|ordered on|delivered|address|phone|payment|upi|card|cash|rating|rate|help|support|swiggy one|zomato gold|restaurant|km|mins?)\\b",
    )
    private val HAS_X_QTY = JsRe.re("\\b[0-9]+\\s*[x×]\\b|\\b[x×]\\s*[0-9]+\\b")
    private val LEAD_X = JsRe.re("^\\s*([0-9]{1,2})\\s*[x×]\\s*(.+)$")
    private val LEAD_N = JsRe.re("^\\s*([0-9]{1,2})\\s+(?!g\\b|ml\\b|kg\\b|pcs?\\b|pieces?\\b)(.+)$")
    private val TRAIL_X = JsRe.re("^(.+?)\\s*[x×]\\s*([0-9]{1,2})\\b")
    private val PAREN_QTY = JsRe.re("^(.+?)\\s*\\(\\s*(?:qty[:\\s]*)?([0-9]{1,2})\\s*\\)")
    private val WORD_QTY = JsRe.re("^(one|two|three|four|five|six)\\s+(.+)$")
    private val QTY_WORDS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6)
    private val HAS_WORD = JsRe.re("[a-z]{3,}")

    private val PRICE = JsRe.re("₹\\s?[0-9,.]+|rs\\.?\\s?[0-9,.]+|inr\\s?[0-9,.]+")
    private val VEG_ICON = JsRe.re("\\b(veg|non[- ]?veg)\\s+icon\\b")
    private val BULLETS = Regex("[•·●▪■◼◾|*]+")
    private val SPACES = Regex("\\s+")
    private val EDGES = Regex("^[\\s,.:-]+|[\\s,.:-]+$")

    private fun cleanName(s: String): String =
        s.replace(PRICE, " ").replace(VEG_ICON, " ").replace(BULLETS, " ").replace(SPACES, " ").replace(EDGES, "").trim()

    /** Order text → dish lines with quantities (max 20). Lines that are prices, fees or totals are skipped. */
    fun parseOrderText(text: String?): List<OrderLine> {
        val names = ArrayList<String>()
        val qtys = ArrayList<Int>()
        for (rawLine in text.orEmpty().split(Regex("\\r?\\n|;"))) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.length > 120) continue
            if (SKIP.containsMatchIn(line) && !HAS_X_QTY.containsMatchIn(line)) continue
            var qty = 1
            var name = line
            val lead = LEAD_X.find(line) ?: LEAD_N.find(line)
            if (lead != null) {
                qty = lead.groupValues[1].toInt()
                name = lead.groupValues[2]
            } else {
                val trail = TRAIL_X.find(line)
                val paren = if (trail == null) PAREN_QTY.find(line) else null
                val word = if (trail == null && paren == null) WORD_QTY.find(line) else null
                when {
                    trail != null -> { qty = trail.groupValues[2].toInt(); name = trail.groupValues[1] }
                    paren != null -> { qty = paren.groupValues[2].toInt(); name = paren.groupValues[1] }
                    word != null -> { qty = QTY_WORDS.getValue(word.groupValues[1].lowercase()); name = word.groupValues[2] }
                }
            }
            name = cleanName(name)
            // A line that is only a price / number / symbol isn't a dish.
            if (!HAS_WORD.containsMatchIn(name) || SKIP.containsMatchIn(name)) continue
            qty = max(1, min(20, if (qty == 0) 1 else qty))
            val same = names.indexOfFirst { it.lowercase() == name.lowercase() }
            if (same >= 0) qtys[same] = qtys[same] + qty
            else { names.add(name.take(80)); qtys.add(qty) }
            if (names.size >= 20) break
        }
        return names.indices.map { OrderLine(names[it], qtys[it]) }
    }

    private val STEPS = listOf(1.0, 0.75, 0.5, 0.25, 0.0)

    private fun eatLine(eat: Double): String {
        if (eat >= 1) return "Have all of it"
        if (eat == 0.0) return "Skip, share or save it"
        val part = if (eat == 0.75) "¾" else if (eat == 0.5) "Half" else "A quarter"
        return "$part, then share or save the rest"
    }

    /** [remainingKcal] / [remainingProtein]: what's left today (the web's Remaining kcal / protein). */
    fun orderPlan(dishes: List<OrderDish>, remainingKcal: Double, @Suppress("UNUSED_PARAMETER") remainingProtein: Double = 0.0): OrderPlan {
        fun kcalOf(d: OrderDish) = d.dish.midKcal * d.qty
        fun protOf(d: OrderDish) = d.dish.midProtein * d.qty
        val totalKcal = jsRound(dishes.sumOf { kcalOf(it) }).toInt()
        val totalProtein = jsRound(dishes.sumOf { protOf(it) }).toInt()
        val budget = max(0.0, remainingKcal)
        if (dishes.isEmpty()) return OrderPlan(0, 0, 0, 0, true, emptyList(), "No dishes found in that order.")
        if (totalKcal <= budget * 1.1) {
            val rows = dishes.mapIndexed { index, d -> PlanRow(index, 1.0, jsRound(kcalOf(d)).toInt(), jsRound(protOf(d)).toInt(), eatLine(1.0)) }
            return OrderPlan(totalKcal, totalProtein, totalKcal, totalProtein, true, rows, "The whole order fits: $totalKcal of the ${jsRound(budget).toLong()} kcal you have left.")
        }
        fun density(d: OrderDish) = if (d.dish.midKcal > 0) d.dish.midProtein / d.dish.midKcal else 0.0
        val order = dishes.indices.sortedWith { a, b ->
            val c = density(dishes[b]).compareTo(density(dishes[a]))
            if (c != 0) c else a - b
        }
        val eat = DoubleArray(dishes.size)
        var used = 0.0
        for (i in order) {
            val k = kcalOf(dishes[i])
            val step = STEPS.firstOrNull { s -> used + k * s <= budget } ?: 0.0
            eat[i] = step
            used += k * step
        }
        // Nothing fits at all (a big order late in the day): still suggest half the most protein-dense dish.
        if (used == 0.0 && order.isNotEmpty()) eat[order[0]] = 0.5
        val rows = dishes.mapIndexed { index, d -> PlanRow(index, eat[index], jsRound(kcalOf(d) * eat[index]).toInt(), jsRound(protOf(d) * eat[index]).toInt(), eatLine(eat[index])) }
        val planKcal = rows.sumOf { it.kcal }
        val planProtein = rows.sumOf { it.protein }
        return OrderPlan(
            totalKcal, totalProtein, planKcal, planProtein, false, rows,
            "The order is ~$totalKcal kcal and you have ${jsRound(budget).toLong()} left. This plan keeps the protein: $planKcal kcal, $planProtein g protein.",
        )
    }

    /** The plan as meal items to pre-log (quantity × what to eat; skipped dishes left out). */
    fun planItems(dishes: List<OrderDish>, plan: OrderPlan): List<MealItem> {
        val out = ArrayList<MealItem>()
        for (r in plan.rows) {
            val d = dishes.getOrNull(r.index) ?: continue
            if (!(r.eat > 0)) continue
            val one = d.dish.toMealItem()
            val k = d.qty * r.eat
            out.add(
                one.copy(
                    name = if (d.qty > 1) "${d.dish.name} ×${d.qty}" else d.dish.name,
                    grams = jsRound(one.grams * k),
                    calories = jsRound(one.calories * k),
                    proteinG = jsRound(one.proteinG * k * 10) / 10,
                    carbsG = jsRound(one.carbsG * k * 10) / 10,
                    fatG = jsRound(one.fatG * k * 10) / 10,
                    kcalLow = jsRound(d.dish.kcalLow * k),
                    kcalHigh = jsRound(d.dish.kcalHigh * k),
                ),
            )
        }
        return out
    }
}
