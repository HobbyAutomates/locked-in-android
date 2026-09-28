package com.sohum.bandlog.util

import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.Serving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.15 §1: "Calories per roti" / "per 100 g" — the per-unit rescale maths (mirrors the web's check). */
class PerUnitTest {
    private val eps = 0.01

    // A roti: 40 g a piece, 297 kcal / 100 g → 118.8 kcal each.
    private val roti = QuantityFood(
        name = "Roti", foodId = "f-roti", calories = 297.0, proteinG = 9.8, carbsG = 46.4, fatG = 7.5,
        servings = listOf(Serving("1 roti", 40.0)),
    )

    @Test fun perUnitConvertsThroughTheUnitsGrams() {
        assertEquals(237.5, PerUnit.toPer100(95.0, 40.0), eps)
        assertEquals(95.0, PerUnit.fromPer100(237.5, 40.0), eps)
        assertEquals(118.8, PerUnit.fromPer100(297.0, 40.0), eps)
        assertEquals(0.0, PerUnit.toPer100(95.0, 0.0), eps)
    }

    @Test fun ninetyFiveARotiTimesTwo() {
        val p = PerUnit.rescale(PerUnit.per100Of(roti), PerUnit.toPer100(95.0, 40.0))
        val two = PerUnit.apply(roti, p).copy(servings = listOf(Serving("1 roti", 40.0)), defaultServing = "1 roti").item(Quantity(QUnit.SERVING, 2.0))
        assertEquals(80.0, two.grams, eps)
        assertEquals(190.0, two.calories, 0.1) // total = count × per-unit
        assertEquals(PerUnit.total(2.0, 95.0), two.calories, 0.1)
        // Macros scale with the calories: 95 / 118.8 of the original.
        val k = 95.0 / 118.8
        assertEquals(9.8 * 0.4 * 2 * k, two.proteinG, 0.1)
        assertEquals(46.4 * 0.4 * 2 * k, two.carbsG, 0.1)
        assertEquals(7.5 * 0.4 * 2 * k, two.fatG, 0.1)
    }

    @Test fun typedMacrosAreKept() {
        val p = PerUnit.rescale(PerUnit.per100Of(roti), 250.0, proteinG = 12.0)
        assertEquals(250.0, p.kcal, eps)
        assertEquals(12.0, p.proteinG, eps) // typed: kept
        assertEquals(46.4 * 250 / 297, p.carbsG, eps) // not typed: scaled
        assertEquals(7.5 * 250 / 297, p.fatG, eps)
    }

    @Test fun per100gForLooseFoods() {
        val bhaji = QuantityFood(name = "Paneer bhaji", foodId = null, calories = 180.0, proteinG = 10.0, carbsG = 6.0, fatG = 13.0)
        val p = PerUnit.rescale(PerUnit.per100Of(bhaji), 150.0)
        val item = PerUnit.apply(bhaji, p).item(Quantity(QUnit.G, 200.0))
        assertEquals(300.0, item.calories, 0.1)
        assertEquals(10.0 * 2 * 150 / 180, item.proteinG, 0.1)
    }

    @Test fun zeroCalorieBaseKeepsMacros() {
        val p = PerUnit.rescale(PerUnit.Per100(0.0, 1.0, 2.0, 0.0), 20.0)
        assertEquals(20.0, p.kcal, eps)
        assertEquals(1.0, p.proteinG, eps)
        assertEquals(2.0, p.carbsG, eps)
    }

    private fun rotiRow(n: Double) = MealItem(
        foodId = "f-roti", name = "Roti", grams = 40.0 * n, calories = 118.8 * n, proteinG = 3.9 * n, carbsG = 18.6 * n, fatG = 3.0 * n,
        source = "table", confidence = 1.0, unit = "serving", servings = n,
    )

    @Test fun rememberedOverrideAppliesToTheNextRoti() {
        val o = PerUnit.Override(PerUnit.foodKey("f-roti", "Roti"), "roti", 95.0)
        val out = PerUnit.applyOverride(rotiRow(3.0), o)
        assertNotNull(out)
        assertEquals(285.0, out!!.calories, 0.1)
        assertEquals(95.0, out.perUnitKcal!!, eps)
        assertEquals(3.9 * 3 * 285.0 / (118.8 * 3), out.proteinG, 0.1)
        // Idempotent: applying again changes nothing.
        val again = PerUnit.applyOverride(out, o)!!
        assertEquals(out.calories, again.calories, eps)
        assertEquals(out.proteinG, again.proteinG, 0.1)
    }

    @Test fun overrideMacrosPerUnit() {
        val o = PerUnit.Override("f-roti", "roti", 95.0, proteinPerUnit = 3.0, carbsPerUnit = null, fatPerUnit = 2.0)
        val out = PerUnit.applyOverride(rotiRow(2.0), o)!!
        assertEquals(190.0, out.calories, 0.1)
        assertEquals(6.0, out.proteinG, 0.1)
        assertEquals(4.0, out.fatG, 0.1)
    }

    @Test fun overrideNeedsTheSameUnit() {
        assertNull(PerUnit.applyOverride(rotiRow(2.0), PerUnit.Override("f-roti", "katori", 95.0)))
        val byWeight = rotiRow(1.0).copy(unit = "g", servings = null)
        assertNull(PerUnit.applyOverride(byWeight, PerUnit.Override("f-roti", "roti", 95.0)))
        // A per-100 g override prices any row by its grams.
        val per100 = PerUnit.applyOverride(byWeight.copy(grams = 150.0), PerUnit.Override("f-roti", PerUnit.PER_100G, 200.0))!!
        assertEquals(300.0, per100.calories, 0.1)
        assertNull(per100.perUnitKcal)
    }

    /** The web's worked example (scripts/check-match.ts): 2 roti = 240 kcal, P 6 C 40 F 4 → 95 per roti. */
    @Test fun webWorkedExample() {
        val two = MealItem(foodId = null, name = "Roti", grams = 80.0, calories = 240.0, proteinG = 6.0, carbsG = 40.0, fatG = 4.0, source = "table", confidence = 1.0, unit = "serving", servings = 2.0)
        val out = PerUnit.rescalePerUnit(two, 2.0, 95.0)
        assertEquals(190.0, out.calories, eps)
        assertEquals(4.8, out.proteinG, eps)
        assertEquals(31.7, out.carbsG, eps)
        assertEquals(3.2, out.fatG, eps)
        assertEquals(95.0, out.perUnitKcal!!, eps)
        val fixed = PerUnit.applyCorrection(two, 199.6, proteinG = 7.0)
        assertEquals(200.0, fixed.calories, eps) // whole kcal
        assertEquals(7.0, fixed.proteinG, eps)
        assertTrue(fixed.userVerified)
        assertEquals(20.0, PerUnit.pctError(240.0, 200.0)!!, eps)
        assertNull(PerUnit.pctError(240.0, 0.0))
    }

    @Test fun foodKeysAndPieces() {
        // Same keys as the web's foodKey: quantities stripped, names shared across text / photo / search.
        assertEquals("roti", PerUnit.foodKey("f-1", "Roti"))
        assertEquals("roti", PerUnit.foodKey(null, "2 Roti"))
        assertEquals("roti", PerUnit.foodKey(null, " Roti (restaurant) "))
        assertEquals("dal tadka", PerUnit.normName("1 katori dal tadka"))
        assertEquals("paneer", PerUnit.normName("Paneer (200 g)"))
        assertEquals("banana", PerUnit.normName("banana x2"))
        assertEquals("paneer bhaji", PerUnit.normName("Paneer  Bhaji!"))
        assertEquals("id:f-9", PerUnit.foodKey("f-9", "(200 g)"))
        assertTrue(PerUnit.sameNoun("rotis", "roti"))
        assertEquals(Serving("1 roti", 40.0), Counting.pieceServing("Rotis", 120.0))
        assertEquals(Serving("1 idli", 45.0), Counting.pieceServing("Idli", 90.0))
        assertNull(Counting.pieceServing("Dal tadka", 150.0))
    }
}
