package com.sohum.bandlog.util

import com.sohum.bandlog.data.Meal
import com.sohum.bandlog.data.MealItem
import com.sohum.bandlog.data.ScanHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v2.17 Recent foods: deduped by name, newest first, up to 12, scans with numbers included. */
class RecentsTest {
    private fun item(name: String, g: Double = 100.0, kcal: Double = 200.0, id: String? = "row") =
        MealItem(id = id, foodId = null, name = name, grams = g, calories = kcal, proteinG = 10.0, carbsG = 20.0, fatG = 5.0, source = "table", confidence = 1.0)

    private fun meal(at: String, vararg items: MealItem) = Meal("m$at", at.take(10), "", at, items.toList())

    private fun scan(at: String, name: String, kcal100: Double?, serving: Double? = null, kind: String = "label") =
        ScanHistoryItem("s$at", kind, "protein", name, "ok", at, null, null, null, displayName = name, kcal100 = kcal100, protein100 = 20.0, servingG = serving)

    @Test fun dedupesNewestFirst() {
        val meals = listOf(
            meal("2026-09-29T08:00:00+00:00", item("Poha", 150.0, 250.0)),
            meal("2026-09-28T08:00:00+00:00", item("poha ", 200.0, 330.0), item("Chai")),
        )
        val r = Recents.build(meals, emptyList())
        assertEquals(listOf("Poha", "Chai"), r.map { it.name })
        // The newest poha wins, at its own quantity, with no saved row id.
        assertEquals(150.0, r[0].item.grams, 0.0)
        assertNull(r[0].item.id)
    }

    @Test fun scansAsOneServing() {
        val scans = listOf(scan("2026-09-29T10:00:00+00:00", "Protein bar", 400.0, 50.0), scan("2026-09-29T09:00:00+00:00", "Mystery", null))
        val r = Recents.build(emptyList(), scans)
        assertEquals(1, r.size)
        assertEquals(200.0, r[0].item.calories, 0.01)
        assertEquals(50.0, r[0].item.grams, 0.0)
        assertEquals("label", r[0].scanKind)
    }

    @Test fun scanLoggedLaterDedupesWithTheMealItem() {
        val meals = listOf(meal("2026-09-29T11:00:00+00:00", item("Protein bar (scan)")))
        val scans = listOf(scan("2026-09-29T10:00:00+00:00", "Protein bar", 400.0, 50.0))
        val r = Recents.build(meals, scans)
        assertEquals(1, r.size)
        assertNull(r[0].scanKind)
    }

    @Test fun capsAtTwelve() {
        val meals = (10..29).map { d -> meal("2026-09-${d}T08:00:00+00:00", item("Food $d")) }
        val r = Recents.build(meals, emptyList())
        assertEquals(12, r.size)
        assertEquals("Food 29", r.first().name)
    }
}
