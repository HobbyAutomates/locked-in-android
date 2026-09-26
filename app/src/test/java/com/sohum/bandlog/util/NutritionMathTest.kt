package com.sohum.bandlog.util

import com.sohum.bandlog.data.FoodPreset
import com.sohum.bandlog.data.MenuDish
import com.sohum.bandlog.data.Profile
import com.sohum.bandlog.data.Serving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.13 §4 / §6 / §7 / §8 / §9 / §10 / §14: the same vectors as the web's scripts/check-nutrition.ts,
 * run against the Kotlin ports. Both apps must give the same numbers.
 */
class NutritionMathTest {

    private val TODAY = "2026-09-26"
    private val adult = Profile(dob = "1998-01-01", gender = "male", heightCm = 175.0, weightKg = 80.0, goalType = "maintain", weeklyWorkoutTarget = 3, proteinTargetG = 120, calorieTarget = 2000)
    private val teen = adult.copy(dob = "2010-06-01", weightKg = 60.0)

    private fun t(c: Int, p: Int, cb: Int, f: Int) = Goals.Targets(c, p, cb, f)

    // ---------------------------------------------------------------- diet modes: targets

    @Test fun dietTargets() {
        assertEquals(9, DietModes.ALL.size)
        assertEquals(t(2000, 128, 246, 56), DietModes.targets(adult, 2000.0, "balanced", TODAY))
        assertEquals(t(2000, 160, 214, 56), DietModes.targets(adult, 2000.0, "high_protein", TODAY))
        assertEquals(128, DietModes.targets(adult, 2000.0, "vegetarian", TODAY).protein)
        assertEquals(144, DietModes.targets(adult, 2000.0, "vegan", TODAY).protein)
        assertEquals(t(2000, 128, 50, 143), DietModes.targets(adult, 2000.0, "keto", TODAY))
        assertEquals(t(2000, 144, 130, 100), DietModes.targets(adult, 2000.0, "low_carb", TODAY))
        assertEquals(104, DietModes.targets(adult, 1600.0, "low_carb", TODAY).carbs)
        assertEquals(t(2000, 128, 197, 78), DietModes.targets(adult, 2000.0, "mediterranean", TODAY))
        assertEquals(96, DietModes.targets(adult.copy(weeklyWorkoutTarget = 0), 2000.0, "mediterranean", TODAY).protein)
        assertEquals(DietModes.ALL.map { 2345 }, DietModes.ALL.map { DietModes.targets(adult, 2345.0, it.key, TODAY).calories })
        assertEquals(120, DietModes.targets(adult.copy(weightKg = null), 2000.0, "high_protein", TODAY).protein)
        assertEquals(96, DietModes.targets(teen, 2400.0, "high_protein", TODAY).protein)
        assertEquals(DietModes.targets(teen, 2400.0, "balanced", TODAY), DietModes.targets(teen, 2400.0, "keto", TODAY))
        assertEquals(55, DietModes.targets(teen, 2400.0, "balanced", TODAY).protein)
        assertEquals(listOf(false, false, false), listOf("keto", "low_carb", "mediterranean").map { DietModes.allowed(it, 16) })
        assertTrue(listOf("balanced", "high_protein", "vegetarian", "eggetarian", "vegan", "jain").all { DietModes.allowed(it, 16) })
        assertTrue(DietModes.ALL.all { DietModes.allowed(it.key, 25) })
        assertEquals("balanced", DietModes.effective("paleo", 25))
        assertEquals(100, DietModes.targets(adult, 400.0, "high_protein", TODAY).protein)
    }

    // ---------------------------------------------------------------- diet modes: food filters

    private fun allowed(m: String, names: List<String>) = names.map { DietModes.allows(m, it) }

    @Test fun foodFilters() {
        assertEquals(listOf(true, true, true, true), allowed("balanced", listOf("Chicken curry", "Egg bhurji", "Fish fry", "Honey")))
        assertEquals(
            listOf(false, false, true, false, true, false, true, true, false),
            allowed("vegetarian", listOf("Chicken curry", "Egg bhurji", "Paneer tikka", "Fish fry", "Veg biryani", "Chicken biryani", "Hara bhara kebab", "Eggless cake", "Omelette")),
        )
        assertEquals(listOf(true, true, false, false, true), allowed("eggetarian", listOf("Egg bhurji", "Boiled eggs", "Fish fry", "Mutton curry", "Anda curry")))
        assertEquals(
            listOf(false, true, true, false, false, true, false, false, true),
            allowed("vegan", listOf("Paneer butter masala", "Peanut butter toast", "Dal tadka", "Ghee roti", "Curd rice", "Soy milk", "Masala chai", "Honey", "Tofu stir fry")),
        )
        assertEquals(
            listOf(false, true, false, false, true, false, false, false, true),
            allowed("jain", listOf("Aloo paratha", "Jeera rice", "Onion pakoda", "Honey lemon water", "Moong dal", "Samosa", "Chicken tikka", "Gajar halwa", "Paneer bhurji")),
        )
        assertEquals(listOf("egg", "dairy"), DietModes.conflicts("vegan", "Egg and cheese sandwich"))
        assertEquals(listOf(true, true), allowed("vegetarian", listOf("Eggplant bharta", "Hamburger bun")))
    }

    // ---------------------------------------------------------------- what to eat

    private fun preset(id: String, label: String, category: String, per100: DoubleArray, servings: List<Serving>, def: String?) = FoodPreset(
        id = id, foodId = "f-$id", label = label, labelHi = null, category = category, servings = servings, defaultServing = def, sort = 1, icon = null,
        foodName = label, calories = per100[0], proteinG = per100[1], carbsG = per100[2], fatG = per100[3], micros = mapOf("fiber_g" to 1.0),
    )

    private val presets = listOf(
        preset("egg", "Boiled egg", "protein", doubleArrayOf(155.0, 13.0, 1.1, 11.0), listOf(Serving("1 egg", 50.0)), "1 egg"),
        preset("chicken", "Chicken breast", "protein", doubleArrayOf(165.0, 31.0, 0.0, 3.6), listOf(Serving("1 katori", 150.0)), "1 katori"),
        preset("paneer", "Paneer bhurji", "protein", doubleArrayOf(260.0, 17.0, 6.0, 19.0), listOf(Serving("1 katori", 150.0)), "1 katori"),
        preset("dal", "Moong dal", "dal", doubleArrayOf(105.0, 7.0, 15.0, 2.0), listOf(Serving("1 katori", 150.0)), "1 katori"),
        preset("rice", "Jeera rice", "staple", doubleArrayOf(150.0, 3.0, 30.0, 3.0), listOf(Serving("1 katori", 150.0)), "1 katori"),
        preset("jalebi", "Jalebi", "sweet", doubleArrayOf(400.0, 3.0, 60.0, 18.0), listOf(Serving("1 piece", 30.0)), "1 piece"),
        preset("ghee", "Ghee", "fat", doubleArrayOf(900.0, 0.0, 0.0, 100.0), listOf(Serving("1 tsp", 5.0)), "1 tsp"),
        preset("poha", "Poha", "breakfast", doubleArrayOf(130.0, 2.5, 25.0, 3.0), listOf(Serving("1 plate", 200.0)), "1 plate"),
        preset("curd", "Curd", "protein", doubleArrayOf(60.0, 3.5, 4.5, 3.5), listOf(Serving("1 katori", 150.0)), "1 katori"),
    )
    private val rem = WhatToEat.Remaining(600.0, 50.0, 60.0, 20.0)

    @Test fun whatToEat() {
        assertEquals(50.0, WhatToEat.portionOf(presets[0]).grams, 0.0)
        assertEquals(1.0, WhatToEat.proteinScore(31.0, 165.0), 0.0)
        assertEquals(0.167, Math.round(WhatToEat.proteinScore(3.0, 150.0) * 1000) / 1000.0, 0.0)
        assertEquals(1.0, WhatToEat.fitScore(300.0, 600.0), 0.0)
        assertEquals(0.5, WhatToEat.fitScore(900.0, 600.0), 0.0)
        assertEquals(0.0, WhatToEat.fitScore(100.0, 0.0), 0.0)
        assertEquals(1.0, WhatToEat.scorePreset(presets[1], rem, "lunch"), 0.0)
        val top = WhatToEat.suggest(rem, "balanced", "lunch", presets)
        assertEquals(listOf("chicken", "egg", "dal", "paneer", "curd"), top.map { it.preset.id })
        assertFalse(top.any { it.preset.category == "fat" })
        assertEquals(listOf("dal", "paneer", "curd"), WhatToEat.suggest(rem, "vegetarian", "lunch", presets).map { it.preset.id }.take(3))
        assertEquals(listOf("dal", "poha", "rice", "jalebi"), WhatToEat.suggest(rem, "vegan", "breakfast", presets).map { it.preset.id })
        val it = top[0].item
        assertEquals(listOf<Any?>("f-chicken", "Chicken breast", 150.0, 248.0, 46.5, 0.0, 5.4, "table", 1.0, mapOf("fiber_g" to 1.5), "serving", 1.0, null),
            listOf<Any?>(it.foodId, it.name, it.grams, it.calories, it.proteinG, it.carbsG, it.fatG, it.source, it.confidence, it.micros, it.unit, it.servings, it.cookedIn))
        assertEquals(listOf("rice", "dal"), WhatToEat.usual(rem, "balanced", "lunch", presets, mapOf("f-rice" to 9, "f-dal" to 4, "f-chicken" to 20, "f-ghee" to 50), listOf("chicken")).map { s -> s.preset.id })
        assertEquals(WhatToEat.Remaining(0.0, 60.0, 0.0, 50.0), WhatToEat.remainingFrom(2000.0, 100.0, 200.0, 60.0, 2100.0, 40.0, 250.0, 10.0))
    }

    // ---------------------------------------------------------------- fasting

    @Test fun fasting() {
        assertEquals(listOf("fed", "fed", "fat_burning", "fat_burning", "ketosis", "ketosis"), listOf(0.0, 11.9, 12.0, 17.9, 18.0, 40.0).map { Fasting.stageAt(it).key })
        assertEquals(listOf(1.0, 1.0, 16.5, 72.0, 16.0), listOf(0.0, 0.2, 16.3, 100.0, Double.NaN).map { Fasting.clampHours(it) })
        assertEquals("15:42:08", Fasting.clock(15 * 3_600_000L + 42 * 60_000L + 8_000L))
        assertEquals(listOf("30 min", "16 h", "16.5 h"), listOf(0.5, 16.0, 16.54).map { Fasting.durationText(it) })
        assertFalse(Fasting.access(16).ok)
        assertFalse(Fasting.access(25, listOf("very_low_bmi")).ok)
        assertTrue(Fasting.access(25).ok)
        assertEquals("16:8", Fasting.label(16.0))
    }

    // ---------------------------------------------------------------- recipes

    private val oats = Recipes.Ingredient("Oats", 80.0, 311.0, 13.5, 53.0, 5.5, 8.5, micros = mapOf("fiber_g" to 8.5, "iron_mg" to 3.4), per100 = Recipes.Per100(389.0, 16.9, 66.3, 6.9, mapOf("fiber_g" to 10.6, "iron_mg" to 4.3)))
    private val milk = Recipes.Ingredient("Milk", 300.0, 186.0, 9.6, 14.4, 9.9, micros = mapOf("calcium_mg" to 360.0))

    @Test fun recipes() {
        assertEquals(oats.copy(grams = 100.0, kcal = 389.0, protein = 16.9, carbs = 66.3, fat = 6.9, fiber = 10.6, micros = mapOf("fiber_g" to 10.6, "iron_mg" to 4.3)), Recipes.price(oats, 100.0))
        assertEquals(93.0, Recipes.price(milk, 150.0).kcal, 0.0)
        assertEquals(Recipes.Totals(497.0, 23.1, 67.4, 15.4, 8.5, 380.0, mapOf("fiber_g" to 8.5, "iron_mg" to 3.4, "calcium_mg" to 360.0)), Recipes.totals(listOf(oats, milk)))
        assertEquals(Recipes.Totals(249.0, 11.6, 33.7, 7.7, 4.3, 190.0, mapOf("fiber_g" to 4.3, "iron_mg" to 1.7, "calcium_mg" to 180.0)), Recipes.perServing(listOf(oats, milk), 2.0))
        assertEquals(250.0, Recipes.perServing(listOf(oats, milk), 2.0, 500.0).grams, 0.0)
        assertEquals(311.0, Recipes.perServing(listOf(oats), 0.0).kcal, 0.0)
        val logged = Recipes.mealItem("Overnight oats", Recipes.perServing(listOf(oats, milk), 2.0), 1.5)
        assertEquals(listOf<Any?>("Overnight oats", 374.0, 17.4, 285.0, "recipe", 1.5, "table", null), listOf<Any?>(logged.name, logged.calories, logged.proteinG, logged.grams, logged.unit, logged.servings, logged.source, logged.foodId))
    }

    // ---------------------------------------------------------------- micros

    @Test fun micros() {
        assertEquals(listOf(19.0, 29.0, 22.0, 32.0), listOf(Micros.ironRda(25, "male"), Micros.ironRda(25, "female"), Micros.ironRda(14, "male"), Micros.ironRda(16, "female")))
        assertEquals(listOf(1000.0, 850.0, 1000.0, 1050.0), listOf(Micros.calciumRda(30), Micros.calciumRda(11), Micros.calciumRda(14), Micros.calciumRda(17)))
        val mt = Micros.targets(28, "male", 2400, null, null)
        assertEquals(listOf(36.0, 60.0, 2000.0), listOf("fiber_g", "sugar_g", "sodium_mg").map { k -> mt.first { it.key == k }.target })
        val own = Micros.targets(28, "male", 2000, 25, 40)
        assertEquals(listOf(25.0, 40.0), listOf(own[0].target, own[5].target))
        val items = listOf(
            Micros.Item("2026-09-26", mapOf("iron_mg" to 5.0, "fiber_g" to 10.0)), Micros.Item("2026-09-26", emptyMap()),
            Micros.Item("2026-09-25", mapOf("iron_mg" to 7.0)), Micros.Item("2026-09-24", mapOf("iron_mg" to 3.0, "sodium_mg" to 7000.0)),
            Micros.Item("2026-09-10", mapOf("iron_mg" to 100.0)),
        )
        val d = Micros.day(items, "2026-09-26")
        assertEquals(listOf(5.0, 1.0, 2.0), listOf(d.values.getValue("iron_mg"), d.itemsWithData.toDouble(), d.items.toDouble()))
        val wk = Micros.weekAverage(items, TODAY)
        assertEquals(listOf(5.0, 3.0, 75.0), listOf(wk.values.getValue("iron_mg"), wk.loggedDays.toDouble(), Math.round(wk.coverage * 100).toDouble()))
        val hints = Micros.weekHints(mt, wk.values, wk.loggedDays, "vegetarian")
        val iron = hints.first { it.key == "iron_mg" }
        assertFalse(iron.foods.contains("Mutton")); assertTrue(iron.foods.contains("Rajma"))
        assertEquals("high", hints.first { it.key == "sodium_mg" }.kind)
        assertEquals(emptyList<Micros.Hint>(), Micros.weekHints(mt, wk.values, 2, "balanced"))
    }

    // ---------------------------------------------------------------- menu scan (a tapped dish)

    @Test fun menuDishItem() {
        val d = MenuDish(name = "Tandoori chicken (half)", kcalLow = 350.0, kcalHigh = 450.0, proteinLow = 45.0, proteinHigh = 55.0, carbsG = 5.0, fatG = 18.0, confidence = "high")
        val it = d.toMealItem()
        assertEquals(listOf<Any?>(400.0, 50.0, "estimated", "restaurant", null), listOf<Any?>(it.calories, it.proteinG, it.source, it.cookedIn, it.foodId))
        assertTrue(Math.abs(it.proteinG * 4 + it.carbsG * 4 + it.fatG * 9 - it.calories) < 3)
    }

    // ---------------------------------------------------------------- item info

    @Test fun itemInfo() {
        val table = ItemInfo.Row(80.0, "roti", "table", 1.0)
        val ai = ItemInfo.Row(200.0, null, "estimated", 0.45)
        assertEquals(listOf("database", "ai", "scan", "recipe"), listOf(ItemInfo.origin(table), ItemInfo.origin(ai), ItemInfo.origin(table.copy(source = "scan")), ItemInfo.origin(table.copy(unit = "recipe", foodId = null))))
        assertEquals(listOf("High", "Low", "Medium"), listOf(ItemInfo.level(table), ItemInfo.level(ai), ItemInfo.level(ai.copy(confidence = 0.6))))
        assertEquals(listOf("70–90 g", "120–280 g"), listOf(ItemInfo.gramRangeText(table), ItemInfo.gramRangeText(ai)))
        assertEquals(ItemInfo.Range(150, 260, true), ItemInfo.gramRange(ai.copy(gramsLow = 150.0, gramsHigh = 260.0)))
        assertTrue(ItemInfo.why(table).length > 10); assertTrue(ItemInfo.why(ai).contains("check"))
    }
}
