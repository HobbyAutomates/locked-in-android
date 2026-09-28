package com.sohum.bandlog.util

import com.sohum.bandlog.util.Grocery.EatenFood
import com.sohum.bandlog.util.Grocery.PantryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.18 A11 pantry + weekly grocery list — the same cases as the web's scripts/check-v218-food.ts. */
class GroceryTest {
    private val eaten = listOf(
        EatenFood("Roti", 80.0), EatenFood("Roti", 120.0), EatenFood("Dal tadka", 150.0), EatenFood("Dal tadka", 150.0),
        EatenFood("Paneer bhurji", 120.0), EatenFood("Jeera rice", 150.0), EatenFood("Masala chai", 150.0), EatenFood("Banana", 120.0),
    )

    @Test fun weeklyList() {
        val list = Grocery.groceryList(eaten, 7, 110.0, 70.0, listOf(PantryRow("atta", true), PantryRow("Rice", false)), "veg")
        val byKey = list.associateBy { it.key }
        assertEquals(true, byKey.getValue("atta").inPantry)
        assertEquals(false, byKey.getValue("rice").inPantry)
        assertTrue(byKey.getValue("paneer").why.contains("protein gap"))
        assertNotNull("second protein fill for veg", byKey["soya"])
        assertTrue("veg: no eggs or chicken", byKey["eggs"] == null && byKey["chicken"] == null)
        assertTrue(byKey["veg"] != null && byKey["fruit"] != null)
        assertEquals(listOf("staples", "protein", "dairy", "produce"), list.map { it.category }.distinct())
        assertFalse(Grocery.groceryText(list).contains("atta"))
        assertTrue(Grocery.groceryText(list).contains("☐ Rice"))
    }

    @Test fun gapsAndPantry() {
        val ne = Grocery.groceryList(emptyList(), 14, 120.0, 60.0, emptyList(), "nonveg")
        assertTrue(ne.any { it.key == "eggs" } || ne.any { it.key == "paneer" })
        assertEquals(2, Grocery.groceryList(emptyList(), 7, 100.0, 95.0, emptyList()).size) // just produce
        assertEquals(true, Grocery.inPantry("Toor dal", listOf(PantryRow("toor dal 1kg", true))))
        assertEquals("dairy", Grocery.pantryCategory("Paneer"))
        assertEquals("produce", Grocery.pantryCategory("Onions"))
    }
}
