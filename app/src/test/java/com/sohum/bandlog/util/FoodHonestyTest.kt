package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.util.FoodHonesty.AccMeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.18 A3 honest ranges + accuracy score — the same cases as the web's scripts/check-v218-food.ts. */
class FoodHonestyTest {
    private fun meal(
        name: String, grams: Double, kcal: Double, source: String = "table", confidence: Double? = 1.0, userVerified: Boolean = false,
        sourceUrls: List<String> = emptyList(), kcalLow: Double? = null, kcalHigh: Double? = null,
    ) = MealItem(
        foodId = null, name = name, grams = grams, calories = kcal, proteinG = 5.0, carbsG = 20.0, fatG = 5.0, source = source, confidence = confidence,
        unit = "g", servings = null, userVerified = userVerified, sourceUrls = sourceUrls, kcalLow = kcalLow, kcalHigh = kcalHigh,
    )

    @Test fun relativeUncertainty() {
        assertEquals(0.05, FoodHonesty.relUncertainty(meal("x", 100.0, 200.0, userVerified = true)), 0.0)
        assertEquals(0.08, FoodHonesty.relUncertainty(meal("x", 100.0, 200.0, source = "scan")), 0.0)
        assertEquals(0.12, FoodHonesty.relUncertainty(meal("x", 100.0, 200.0, source = "estimated", confidence = 0.9, sourceUrls = listOf("https://a"))), 0.0)
        assertEquals(0.4, FoodHonesty.relUncertainty(meal("x", 100.0, 200.0, source = "estimated", confidence = 0.3)), 0.0)
    }

    @Test fun ranges() {
        assertEquals(FoodHonesty.Range(176, 224, 24, 0.12), FoodHonesty.kcalRange(meal("x", 100.0, 200.0, source = "table")))
        assertEquals(FoodHonesty.Range(150, 260, 55, 0.275), FoodHonesty.kcalRange(meal("x", 100.0, 200.0, kcalLow = 150.0, kcalHigh = 260.0)))
        // an edited amount: the stale stored range is ignored and the source rule applies (table ±12%)
        assertEquals(48, FoodHonesty.kcalRange(meal("x", 100.0, 400.0, kcalLow = 150.0, kcalHigh = 260.0, source = "table")).plusMinus)
        assertEquals("", FoodHonesty.plusMinusLabel(meal("x", 10.0, 20.0))) // ±2 isn't worth showing
        assertEquals(60, FoodHonesty.totalPlusMinus(listOf(meal("a", 100.0, 300.0), meal("b", 100.0, 400.0)))) // sqrt(36² + 48²)
        assertEquals(FoodHonesty.KcalLowHigh(160, 260), FoodHonesty.rangeFromGrams(200.0, 100.0, 80.0, 130.0))
        assertNull(FoodHonesty.rangeFromGrams(200.0, 100.0, null, null))
    }

    @Test fun dayAccuracy() {
        var acc = FoodHonesty.dayAccuracy(listOf(AccMeal("m1", listOf(meal("oats", 40.0, 150.0, source = "scan"), meal("whey", 30.0, 120.0, userVerified = true)))))
        assertTrue(acc.score!! >= 80 && acc.label == "Sharp" && acc.vague.isEmpty())
        acc = FoodHonesty.dayAccuracy(listOf(AccMeal("m1", listOf(meal("biryani", 300.0, 600.0, source = "estimated", confidence = 0.3), meal("curd", 100.0, 60.0, source = "table")))))
        assertTrue(acc.score!! < 20)
        assertEquals(listOf(Triple("m1", 0, "biryani")), acc.vague.map { Triple(it.mealId, it.index, it.name) })
        assertNull(FoodHonesty.dayAccuracy(emptyList()).score)
    }
}
