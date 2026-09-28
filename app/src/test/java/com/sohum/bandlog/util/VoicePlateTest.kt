package com.sohum.bandlog.util

import com.sohum.bandlog.data.PlateItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.18 A1 photo + voice merge — the same cases as the web's scripts/check-v218-food.ts. */
class VoicePlateTest {
    private fun plate(name: String, grams: Double, kcal: Double, fat: Double? = null, fromVoice: Boolean = false) =
        PlateItem(name, grams, "medium", kcal, kcal / 20, kcal / 8, fat ?: (kcal / 30), emptyMap(), "estimated", null, fromVoice = fromVoice)

    private val base = listOf(plate("roti", 120.0, 330.0), plate("dal tadka", 150.0, 170.0), plate("aloo gobi", 150.0, 150.0))

    @Test fun tokensAndMatching() {
        assertEquals(listOf("roti"), VoicePlate.foodTokens("Chapatis"))
        assertEquals(listOf("dal", "tadka"), VoicePlate.foodTokens("Dal Tadka"))
        assertEquals(1, VoicePlate.matchItem(listOf(plate("jeera rice", 150.0, 230.0), plate("dal tadka", 150.0, 170.0)), "daal"))
        assertEquals(0, VoicePlate.matchItem(listOf(plate("jeera rice", 150.0, 230.0)), "chawal"))
        assertEquals(-1, VoicePlate.matchItem(listOf(plate("jeera rice", 150.0, 230.0)), "paneer"))
    }

    @Test fun parseAndApplyAmountsThenOil() {
        val p = VoicePlate.parseVoice("2 roti, less oil, extra dal")
        assertEquals(2, p.segments.size)
        assertEquals(listOf(Triple("roti", 2.0, "set"), Triple("dal", null, "extra")), p.segments.map { Triple(it.food, it.count, it.mod) })
        assertEquals(listOf(VoicePlate.OilCue("less", null)), p.oil)

        val m = VoicePlate.applyVoiceAmounts(base, p)
        assertEquals(80.0, m.items[0].grams, 0.0) // 3 roti seen (120 g) → 2 × 40 g
        assertEquals(220.0, m.items[0].calories, 0.0)
        assertEquals(225.0, m.items[1].grams, 0.0) // extra dal ×1.5
        assertEquals(150.0, m.items[2].grams, 0.0)
        assertEquals(0, m.added.size)
        assertTrue(m.changes[0], m.changes[0].startsWith("Roti → 2 roti (80 g)"))

        val (oiled, oilChanges) = VoicePlate.applyVoiceOil(m.items, p)
        // less oil: dal and aloo gobi are oily (−30 % fat), roti is not
        assertEquals(m.items[0].fatG, oiled[0].fatG, 0.0)
        assertTrue(oiled[1].fatG < m.items[1].fatG && oiled[2].fatG < m.items[2].fatG)
        assertTrue(oilChanges[0], oilChanges[0].startsWith("Less oil on 2 dishes"))
    }

    @Test fun setNotAdd() {
        // saying the count the photo already shows changes nothing
        assertEquals(0, VoicePlate.mergeVoice(listOf(plate("roti", 80.0, 220.0)), "2 roti").changes.size)
    }

    @Test fun hinglishKatoriRemovalAndMissedFoods() {
        val m = VoicePlate.mergeVoice(listOf(plate("rice", 200.0, 260.0), plate("papad", 12.0, 50.0)), "do katori chawal aur papad nahi khaya, plus a glass of chaas")
        assertEquals(2, m.items.size)
        assertEquals(300.0, m.items[0].grams, 0.0)
        assertEquals("chaas", m.items[1].name)
        assertEquals(250.0, m.items[1].grams, 0.0)
        assertEquals(true, m.items[1].fromVoice)
        assertEquals(listOf(1), m.added)
        assertTrue(m.changes.contains("Removed papad"))
        // "no papad" when there is none: nothing happens
        assertEquals(1, VoicePlate.mergeVoice(listOf(plate("rice", 200.0, 260.0)), "no papad").items.size)
    }

    @Test fun halfDoubleGramsDevanagari() {
        assertEquals(100.0, VoicePlate.mergeVoice(listOf(plate("rice", 200.0, 260.0)), "half rice").items[0].grams, 0.0)
        assertEquals(400.0, VoicePlate.mergeVoice(listOf(plate("rice", 200.0, 260.0)), "double rice").items[0].grams, 0.0)
        assertEquals(120.0, VoicePlate.mergeVoice(listOf(plate("paneer bhurji", 200.0, 500.0)), "120g paneer").items[0].grams, 0.0)
        assertEquals(120.0, VoicePlate.mergeVoice(listOf(plate("roti", 40.0, 110.0)), "तीन रोटी").items[0].grams, 0.0)
    }

    @Test fun devanagariConjunctionSplits() {
        // Android extra: "और" / "या" split segments (the web's ASCII \b never matches around Devanagari).
        val p = VoicePlate.parseVoice("दो रोटी और आधा चावल")
        assertEquals(listOf("roti", "rice"), p.segments.map { it.food })
        assertEquals(2.0, p.segments[0].count)
        assertEquals("half", p.segments[1].mod)
    }

    @Test fun oilCues() {
        // extra ghee goes to the dish it names; no dish named → the biggest oily dish
        var g = VoicePlate.mergeVoice(listOf(plate("roti", 80.0, 220.0), plate("dal", 150.0, 170.0)), "2 roti with ghee")
        assertEquals(265.0, g.items[0].calories, 0.0)
        assertEquals(170.0, g.items[1].calories, 0.0)
        g = VoicePlate.mergeVoice(listOf(plate("salad", 100.0, 40.0), plate("rajma", 150.0, 210.0)), "extra ghee")
        assertEquals(255.0, g.items[1].calories, 0.0)
        // no oil on a named dish only
        g = VoicePlate.mergeVoice(listOf(plate("bhindi", 150.0, 160.0, fat = 11.0), plate("dal", 150.0, 170.0, fat = 6.0)), "bhindi without oil")
        assertEquals(4.4, g.items[0].fatG, 0.0)
        assertEquals(6.0, g.items[1].fatG, 0.0)
    }

    @Test fun dropUnpricedAndJunk() {
        val (items, notes) = VoicePlate.dropUnpriced(listOf(plate("rice", 150.0, 195.0), plate("chaas", 250.0, 0.0, fromVoice = true)))
        assertEquals(1, items.size)
        assertEquals(1, notes.size)
        // an empty / junk utterance changes nothing
        assertEquals(0, VoicePlate.mergeVoice(base, "").changes.size)
        assertEquals(3, VoicePlate.mergeVoice(base, "umm okay, that's it").items.size)
        assertEquals(4, VoicePlate.mergeVoice(base, "chaas bhi").items.size)
    }

    /** Review fixes (web check-v218-food "review fixes"): no phantom ghee from dish names, "not much", "half roti". */
    @Test fun reviewFixes() {
        val bc = VoicePlate.mergeVoice(listOf(plate("butter chicken", 150.0, 330.0), plate("butter naan", 90.0, 290.0)), "butter chicken and butter naan")
        assertEquals(330.0, bc.items[0].calories, 0.0)
        assertEquals(290.0, bc.items[1].calories, 0.0)
        assertEquals(emptyList<VoicePlate.OilCue>(), VoicePlate.parseVoice("fried rice").oil)
        assertEquals(emptyList<VoicePlate.OilCue>(), VoicePlate.parseVoice("dal tadka").oil)
        assertEquals(225.0, VoicePlate.mergeVoice(listOf(plate("dal tadka", 150.0, 170.0), plate("rice", 150.0, 195.0)), "extra dal tadka").items[0].grams, 0.0)
        assertEquals(listOf(VoicePlate.OilCue("less", null)), VoicePlate.parseVoice("oil kam").oil)
        assertEquals(listOf(VoicePlate.OilCue("none", null)), VoicePlate.parseVoice("without any oil").oil)
        assertEquals(140.0, VoicePlate.mergeVoice(listOf(plate("rice", 200.0, 260.0)), "not much rice").items[0].grams, 0.0)
        assertEquals(0, VoicePlate.mergeVoice(listOf(plate("rice", 200.0, 260.0)), "no rice").items.size)
        assertEquals(20.0, VoicePlate.mergeVoice(listOf(plate("roti", 120.0, 330.0)), "half roti").items[0].grams, 0.0)
        assertEquals(4, HomeRecipes.parseVoiceRecipe("Mom's dal: 1 katori toor dal, coriander for garnish, serves 4").servings)
        val m = com.sohum.bandlog.data.MealItem(foodId = null, name = "Biryani", grams = 300.0, calories = 600.0, proteinG = 10.0, carbsG = 80.0, fatG = 20.0, source = "estimated", confidence = 0.6, kcalLow = 500.0, kcalHigh = 700.0)
        val half = m.withGrams(150.0)
        assertEquals(250.0, half.kcalLow!!, 0.001)
        assertEquals(350.0, half.kcalHigh!!, 0.001)
    }
}
