package com.sohum.bandlog.util

import com.sohum.bandlog.data.MenuDish
import com.sohum.bandlog.util.OrderHelper.OrderDish
import com.sohum.bandlog.util.OrderHelper.OrderLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.18 A4 order helper — the same cases as the web's scripts/check-v218-food.ts. */
class OrderHelperTest {
    private fun dish(name: String, kl: Double, kh: Double, pl: Double, ph: Double, qty: Int = 1) = OrderDish(
        MenuDish(name = name, portion = "1", grams = 200.0, kcalLow = kl, kcalHigh = kh, proteinLow = pl, proteinHigh = ph, carbsG = 30.0, fatG = 10.0, confidence = "medium", veg = true),
        qty,
    )

    private val ds = listOf(dish("Butter Naan", 260.0, 320.0, 7.0, 9.0, 2), dish("Paneer Tikka", 300.0, 360.0, 20.0, 24.0), dish("Gulab Jamun", 280.0, 320.0, 3.0, 5.0))

    @Test fun parsesOrderText() {
        val order = OrderHelper.parseOrderText(
            listOf(
                "Order #88213", "2 x Butter Naan ₹120", "Paneer Tikka x 1   ₹280", "Dal Makhani (Qty 1)", "Item total ₹560", "Delivery fee ₹35",
                "GST and restaurant charges ₹42", "1 Gulab Jamun", "To pay ₹637",
            ).joinToString("\n"),
        )
        assertEquals(listOf(OrderLine("Butter Naan", 2), OrderLine("Paneer Tikka", 1), OrderLine("Dal Makhani", 1), OrderLine("Gulab Jamun", 1)), order)
        assertEquals(listOf(OrderLine("Veg Biryani", 3)), OrderHelper.parseOrderText("Veg Biryani\nveg biryani x 2"))
    }

    @Test fun plans() {
        var plan = OrderHelper.orderPlan(ds, 2000.0, 80.0)
        assertEquals(true, plan.fits)
        assertTrue(plan.rows.all { it.eat == 1.0 })
        plan = OrderHelper.orderPlan(ds, 700.0, 60.0)
        assertEquals(false, plan.fits)
        assertEquals(1.0, plan.rows[1].eat, 0.0) // paneer tikka (most protein per kcal) stays whole
        assertTrue(plan.planKcal <= 700)
        assertTrue(plan.rows[2].eat < 1) // dessert shrinks
        val pre = OrderHelper.planItems(ds, plan)
        assertTrue(pre.all { it.calories > 0 && it.kcalLow!! <= it.calories && it.kcalHigh!! >= it.calories })
        plan = OrderHelper.orderPlan(ds, 0.0, 0.0)
        assertEquals(0.5, plan.rows[1].eat, 0.0) // nothing fits → half the most protein-dense dish
        assertEquals(0, OrderHelper.orderPlan(emptyList(), 500.0, 0.0).rows.size)
    }
}
