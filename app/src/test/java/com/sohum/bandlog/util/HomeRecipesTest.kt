package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** v2.18 A2 home recipe library + voice recipes — the same cases as the web's scripts/check-v218-food.ts. */
class HomeRecipesTest {
    /** Unit tests run from the module dir (app/); the root dir is a fallback. */
    private val lib: List<HomeRecipes.HomeRecipe> by lazy {
        val f = listOf(File("src/main/assets/home_recipes.json"), File("app/src/main/assets/home_recipes.json")).first { it.exists() }
        HomeRecipes.parse(f.readText(Charsets.UTF_8))
    }

    @Test fun library() {
        assertTrue("60+ home recipes", lib.size >= 60)
        assertEquals("unique ids", lib.size, lib.map { it.id }.toSet().size)
        for (r in lib) {
            val calc = 4 * r.proteinG + 4 * r.carbsG + 9 * r.fatG
            assertTrue("${r.id}: macros match kcal", abs(calc - r.kcal) / r.kcal <= 0.15)
            assertTrue("${r.id}: unit + ingredients", r.unitGrams > 0 && r.ingredients.isNotEmpty())
            assertTrue("${r.id}: unit", r.unit in listOf("katori", "roti", "piece", "plate", "glass", "cup", "bowl"))
        }
    }

    @Test fun searchAndItems() {
        assertEquals("roti", HomeRecipes.search("chapati", lib)[0].id)
        assertEquals("dal-tadka", HomeRecipes.search("dal tadka", lib)[0].id)
        assertTrue(HomeRecipes.search("paneer", lib).size >= 4)
        assertEquals(lib.size, HomeRecipes.search("", lib).size)
        val dal = lib.first { it.id == "dal-tadka" }
        val two = HomeRecipes.homeRecipeItem(dal, 2.0)
        assertEquals(300.0, two.grams, 0.0)
        assertEquals(340.0, two.calories, 0.0)
        assertEquals(2.0, two.servings!!, 0.0)
        assertEquals("1 katori", two.servingUnit?.label)
        assertEquals("½ katori", HomeRecipes.unitLabel(dal, 0.5))
        assertEquals("2 katoris", HomeRecipes.unitLabel(dal, 2.0))
        assertEquals(170.0, HomeRecipes.homeRecipeAsOwn(dal).perServing.kcal, 0.0)
        assertEquals(0.5, HomeRecipes.servingStep(dal), 0.0)
    }

    @Test fun voiceRecipes() {
        var v = HomeRecipes.parseVoiceRecipe("Mom's dal: 1 katori toor dal, 1 spoon ghee, serves 4")
        assertEquals(HomeRecipes.VoiceRecipe("Mom's dal", "1 katori toor dal, 1 spoon ghee", 4), v)
        v = HomeRecipes.parseVoiceRecipe("nani ka rajma 2 cup rajma 1 tbsp oil 2 onions for 6 people")
        assertEquals("Nani ka rajma", v.name)
        assertEquals(6, v.servings)
        assertTrue(v.ingredients.startsWith("2 cup rajma"))
        assertEquals("", HomeRecipes.parseVoiceRecipe("1 katori poha").name)
    }
}
