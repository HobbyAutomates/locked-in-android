package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.util.FoodBits.Per100
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** v2.18 A5–A10 small food rules — the same cases as the web's scripts/check-v218-food.ts. */
class FoodBitsTest {
    private fun meal(name: String, grams: Double, kcal: Double, unit: String = "g", servings: Double? = null, kcalLow: Double? = null, kcalHigh: Double? = null) =
        MealItem(
            foodId = null, name = name, grams = grams, calories = kcal, proteinG = 5.0, carbsG = 20.0, fatG = 5.0, source = "table", confidence = 1.0,
            unit = unit, servings = servings, kcalLow = kcalLow, kcalHigh = kcalHigh,
        )

    @Test fun portionReference() {
        assertEquals("katori", FoodBits.cleanScaleRef("steel katori"))
        assertEquals("hand", FoodBits.cleanScaleRef("Hand / palm"))
        assertNull(FoodBits.cleanScaleRef("none"))
        assertNull(FoodBits.cleanScaleRef("a banana"))
        assertEquals("Sized using: spoon", FoodBits.sizedUsingLabel("tablespoon"))
        assertNull(FoodBits.sizedUsingLabel(null))
    }

    @Test fun leftovers() {
        val biryani = meal("Paneer biryani", 400.0, 640.0, unit = "serving", servings = 2.0, kcalLow = 560.0, kcalHigh = 720.0)
        val half = FoodBits.splitEaten(listOf(biryani), 0.5)
        assertEquals(320.0, half.eaten[0].calories, 0.0)
        assertEquals(320.0, half.left[0].calories, 0.0)
        assertEquals(1.0, half.left[0].servings!!, 0.0)
        assertEquals(280.0, half.left[0].kcalLow!!, 0.0)
        assertEquals(0.5, half.leftFraction, 0.0)
        assertEquals(0, FoodBits.splitEaten(listOf(biryani), 1.0).left.size)
        assertEquals("¾", FoodBits.fractionLabel(0.75))
        assertEquals("½ of Paneer biryani · 320 kcal", FoodBits.leftoverLine("Paneer biryani", 320.0, 0.5))
        val now = Instant.parse("2026-09-29T12:00:00Z").toEpochMilli()
        assertEquals(true, FoodBits.leftoverActive("2026-09-28T20:00:00Z", now = now))
        assertEquals(false, FoodBits.leftoverActive("2026-09-25T20:00:00Z", now = now)) // > 3 days
        assertEquals(false, FoodBits.leftoverActive("2026-09-29T08:00:00Z", usedAt = "2026-09-29T09:00:00Z", now = now))
        assertEquals(100.0, FoodBits.scaleMealItem(biryani, 0.25).grams, 0.0)
    }

    @Test fun split() {
        assertEquals(listOf(0.25, 0.25, 0.5), FoodBits.normalizeShares(listOf(1.0, 1.0, 2.0)))
        assertEquals(listOf(0.5, 0.5), FoodBits.normalizeShares(listOf(0.0, 0.0)))
        assertEquals(listOf(0.5, 0.0, 0.5), FoodBits.normalizeShares(listOf(2.0, -1.0, 2.0)))
        val parts = FoodBits.splitShares(listOf(meal("Rajma", 600.0, 840.0)), listOf(1.0, 1.0, 1.0))
        assertEquals(3, parts.size)
        assertEquals(280.0, parts[0][0].calories, 0.0)
        assertEquals(200.0, parts[0][0].grams, 0.0)
    }

    @Test fun labelVsReality() {
        val gaps = FoodBits.labelRealityGaps(Per100(380.0, 30.0, 40.0, 10.0), Per100(400.0, 20.0, 42.0, 11.0))
        assertEquals(listOf("protein_g"), gaps.map { it.field })
        assertEquals(50, gaps[0].pct)
        assertEquals("Protein: label says 30 g per 100 g, the web says 20 g (50% apart)", FoodBits.realityLine(gaps[0]))
        // tiny absolute gaps
        assertEquals(0, FoodBits.labelRealityGaps(Per100(calories = 50.0, proteinG = 1.0), Per100(calories = 60.0, proteinG = 1.5)).size)
        assertEquals("calories", FoodBits.labelRealityGaps(Per100(calories = 250.0), Per100(calories = 480.0))[0].field)
        assertEquals(0, FoodBits.labelRealityGaps(Per100(), Per100(calories = 400.0)).size)
    }

    @Test fun swaps() {
        var s = FoodBits.pickSwap(listOf(meal("Butter naan", 90.0, 290.0), meal("Dal makhani", 150.0, 280.0)))
        assertEquals("Dal tadka", s?.to)
        assertEquals(-111, s?.delta)
        assertEquals("Dal tadka instead of dal makhani: −111 kcal", s?.line)
        s = FoodBits.pickSwap(listOf(meal("Maida roti", 60.0, 200.0)))
        assertEquals("Wheat roti instead of maida roti: −40 kcal", s?.line)
        assertNull(FoodBits.pickSwap(listOf(meal("Roti", 40.0, 105.0))))
        assertNull(FoodBits.pickSwap(listOf(meal("Pav bhaji", 300.0, 550.0)))) // not a pakora
        assertNull(FoodBits.pickSwap(listOf(meal("Diet coke", 330.0, 1.0))))
        assertNull(FoodBits.pickSwap(listOf(meal("Chai without sugar", 150.0, 50.0))))
    }

    @Test fun waterFromFood() {
        assertEquals(120, FoodBits.waterMl("Dal tadka", 150.0))
        assertEquals(225, FoodBits.waterMl("Chaas", 250.0))
        assertEquals(186, FoodBits.waterMl("Watermelon", 200.0))
        assertEquals(0, FoodBits.waterMl("Roti", 80.0))
        assertEquals(0, FoodBits.waterMl("Whey protein powder with milk", 30.0))
        assertEquals(120 + 85 + 151, FoodBits.waterFromFoods(listOf(listOf("Dal" to 150.0, "Curd" to 100.0), listOf("Apple" to 180.0))))
        assertEquals(120 + 85 + 151, FoodBits.waterFromMeals(listOf(listOf(meal("Dal", 150.0, 170.0), meal("Curd", 100.0, 60.0)), listOf(meal("Apple", 180.0, 95.0)))))
    }
}
