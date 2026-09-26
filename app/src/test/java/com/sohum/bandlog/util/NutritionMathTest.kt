package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.13 §4 / §6 / §8 / §9 vectors (web: scripts/check-nutrition.ts). */
class NutritionMathTest {

    // ---- §4 diet modes ----

    @Test fun proteinByMode() {
        // 25 y, 70 kg, 4 sessions a week.
        assertEquals(112, DietModes.proteinG("balanced", 25, 70.0, "male", 4))
        assertEquals(140, DietModes.proteinG("high_protein", 25, 70.0, "male", 4))
        assertEquals(112, DietModes.proteinG("vegetarian", 25, 70.0, "male", 4))
        assertEquals(126, DietModes.proteinG("vegan", 25, 70.0, "male", 4))
        assertEquals(126, DietModes.proteinG("low_carb", 25, 70.0, "male", 4))
        // Mediterranean: at least 1.2 g/kg (84) — the balanced 112 is higher.
        assertEquals(112, DietModes.proteinG("mediterranean", 25, 70.0, "male", 4))
        assertEquals(84, DietModes.proteinG("mediterranean", 25, 70.0, "male", 0))
    }

    @Test fun teenRules() {
        assertFalse(DietModes.allowed("keto", 16))
        assertFalse(DietModes.allowed("low_carb", 16))
        assertFalse(DietModes.allowed("mediterranean", 17))
        assertTrue(DietModes.allowed("vegan", 16))
        assertTrue(DietModes.allowed("keto", 18))
        assertEquals("balanced", DietModes.effective("keto", 16))
        // 16 y boy, 60 kg: high protein capped at 1.6 g/kg = 96; ICMR table alone is 55.
        assertEquals(96, DietModes.proteinG("high_protein", 16, 60.0, "male", 3))
        // Vegan teen: 1.8 → capped at 1.6.
        assertEquals(96, DietModes.proteinG("vegan", 16, 60.0, "male", 3))
        assertEquals(55, DietModes.proteinG("balanced", 16, 60.0, "male", 3))
    }

    @Test fun macrosByMode() {
        // 2000 kcal, 100 g protein.
        assertEquals(Goals.Targets(2000, 100, 274, 56), DietModes.macros("balanced", 2000.0, 100))
        assertEquals(Goals.Targets(2000, 100, 50, 156), DietModes.macros("keto", 2000.0, 100))
        assertEquals(Goals.Targets(2000, 100, 130, 120), DietModes.macros("low_carb", 2000.0, 100))
        assertEquals(Goals.Targets(2000, 100, 225, 78), DietModes.macros("mediterranean", 2000.0, 100))
        // Low carb at 1600 kcal: 26 % = 104 g (under the 130 g cap).
        assertEquals(104, DietModes.macros("low_carb", 1600.0, 100).carbs)
        // Calories never change.
        assertEquals(2000, DietModes.targets("keto", 2000, 30, 70.0, "female", 2)!!.calories)
    }

    @Test fun foodFilters() {
        assertFalse(DietModes.allows("vegetarian", "Chicken curry"))
        assertFalse(DietModes.allows("vegetarian", "Boiled eggs"))
        assertTrue(DietModes.allows("vegetarian", "Paneer bhurji"))
        assertTrue(DietModes.allows("eggetarian", "Egg bhurji"))
        assertFalse(DietModes.allows("eggetarian", "Fish fry"))
        assertFalse(DietModes.allows("vegan", "Paneer tikka"))
        assertFalse(DietModes.allows("vegan", "Ghee roti"))
        assertTrue(DietModes.allows("vegan", "Soy milk"))
        assertTrue(DietModes.allows("vegan", "Rajma"))
        assertTrue(DietModes.allows("vegetarian", "Baingan (eggplant) bharta"))
        assertFalse(DietModes.allows("jain", "Aloo paratha"))
        assertFalse(DietModes.allows("jain", "Onion pakoda"))
        assertTrue(DietModes.allows("jain", "Moong dal"))
        assertTrue(DietModes.allows("keto", "Chicken curry"))
    }

    // ---- §6 what to eat ----

    private fun food(name: String, cat: String, kcal: Double, protein: Double) =
        WhatToEat.Food(name, cat, "1 serving", 100.0, kcal, protein, 10.0, 5.0)

    @Test fun rankingWeights() {
        val paneer = food("Paneer bhurji", "protein", 250.0, 18.0)
        // 0.5·min(1, 7.2/10) + 0.3·1 + 0.2·1 = 0.86
        assertEquals(0.86, WhatToEat.score(paneer, WhatToEat.Remaining(600.0, 50.0, 100.0, 30.0), "dinner"), 1e-9)
        // Over by half of what's left: fit 0.5.
        assertEquals(0.5, WhatToEat.fitScore(300.0, 200.0), 1e-9)
    }

    @Test fun rankFiltersAndOrders() {
        val foods = listOf(
            food("Chicken breast", "protein", 165.0, 31.0),
            food("Paneer bhurji", "protein", 250.0, 18.0),
            food("Gulab jamun", "sweet", 300.0, 4.0),
            food("Moong dal", "dal", 120.0, 8.0),
            food("Ghee", "fat", 90.0, 0.0),
        )
        val r = WhatToEat.Remaining(500.0, 40.0, 80.0, 20.0)
        val all = WhatToEat.rank(foods, r, "dinner", "balanced")
        assertEquals(listOf("Chicken breast", "Paneer bhurji", "Moong dal", "Gulab jamun"), all.map { it.food.name })
        val veg = WhatToEat.rank(foods, r, "dinner", "vegetarian")
        assertEquals("Paneer bhurji", veg.first().food.name)
        assertTrue(veg.none { it.food.name == "Chicken breast" })
        assertEquals("Try a, b or c", WhatToEat.tryLine(listOf("a", "b", "c", "d")))
    }

    // ---- §8 recipes ----

    @Test fun recipePerServing() {
        val items = listOf(
            Recipes.Ingredient("Oats", 80.0, 300.0, 10.0, 54.0, 6.0, 8.0),
            Recipes.Ingredient("Milk", 300.0, 180.0, 9.6, 14.4, 9.6),
        )
        val t = Recipes.totals(items)
        assertEquals(480.0, t.kcal, 1e-9)
        val s = Recipes.perServing(items, 2.0, cookedWeightG = 500.0)
        assertEquals(240.0, s.kcal, 1e-9)
        assertEquals(9.8, s.protein, 1e-9)
        assertEquals(250.0, s.grams, 1e-9)
        assertEquals(96.0, Recipes.per100Cooked(items, 500.0)!!, 1e-9)
    }

    // ---- §9 micros ----

    @Test fun microTargets() {
        assertEquals(50.0, Micros.sugarMaxG(2000), 1e-9)
        assertEquals(30.0, Micros.fibreFor(2000), 1e-9)
        assertEquals(19.0, Micros.rda("iron_mg", 25, "male")!!, 1e-9)
        assertEquals(29.0, Micros.rda("iron_mg", 25, "female")!!, 1e-9)
        assertEquals(1000.0, Micros.rda("calcium_mg", 30, "female")!!, 1e-9)
        val rows = Micros.rows(
            listOf(Micros.Entry("2026-09-26", mapOf("iron_mg" to 5.0), 500.0), Micros.Entry("2026-09-25", mapOf("iron_mg" to 7.0), 500.0)),
            "2026-09-26", 25, "male", 2000, null,
        )
        val iron = rows.first { it.nutrient.key == "iron_mg" }
        assertEquals(5.0, iron.today, 1e-9)
        assertEquals(6.0, iron.weekAvg, 1e-9)
        assertTrue(iron.flagged)
    }

    // ---- §14 item facts ----

    @Test fun itemFacts() {
        val f = ItemInfo.facts(150.0, 0.3, "estimated", null, null)
        assertEquals("AI estimate", f.kind)
        assertEquals("low", f.level)
        assertEquals("100–205 g", ItemInfo.rangeLabel(f))
        val db = ItemInfo.facts(100.0, 1.0, "table", "x", "ifct")
        assertEquals("Database match", db.kind)
        assertEquals("90–110 g", ItemInfo.rangeLabel(db))
    }

    // ---- §7 fasting ----

    @Test fun fastingStages() {
        assertEquals("fed", Fasting.stageAt(3.0).key)
        assertEquals("fat", Fasting.stageAt(12.0).key)
        assertEquals("ketosis", Fasting.stageAt(19.5).key)
        assertEquals("16:8", Fasting.label(16.0))
        assertEquals("30 h", Fasting.label(30.0))
        assertEquals(72.0, Fasting.clampHours(100.0), 1e-9)
        assertEquals("1:02:03", Fasting.clock(3_723_000))
    }
}
