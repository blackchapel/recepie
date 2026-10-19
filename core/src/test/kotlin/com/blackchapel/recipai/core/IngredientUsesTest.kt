package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngredientUsesTest {
    private val samples = SampleRecipes.all(now = 0)
    private val shakshuka = samples.first { it.id == "shakshuka" }

    private fun uses(r: Recipe, step: Int) = IngredientUses.uses(r, r.steps[step]).map { it.name }

    @Test fun findsIngredientsByTheirKeyWord() {
        assertEquals(listOf("olive oil", "large onion", "red bell pepper", "Salt and black pepper"), uses(shakshuka, 0))
        assertEquals(listOf("garlic", "ground cumin", "smoked paprika", "chili flakes"), uses(shakshuka, 1))
        // "salt and pepper" names the bell pepper too: a word match, as in the prototype.
        assertEquals(listOf("red bell pepper", "crushed tomatoes", "Salt and black pepper"), uses(shakshuka, 2))
    }

    @Test fun pluralsAndSingularsMatch() {
        assertEquals(listOf("eggs"), uses(shakshuka, 3))
        val r = Recipe(
            id = "x", title = "x", servings = 1,
            ingredients = listOf(Ingredient("i1", "tomato", 2.0), Ingredient("i2", "bay leaves", 2.0), Ingredient("i3", "lemon", 1.0)),
            steps = listOf(Step("s1", "Add the tomatoes and a bay leaf."), Step("s2", "Squeeze over the lemons.")),
        )
        assertEquals(listOf("tomato", "bay leaves"), uses(r, 0))
        assertEquals(listOf("lemon"), uses(r, 1))
    }

    @Test fun sizeAndColorWordsAloneDoNotMatch() {
        val r = Recipe(
            id = "x", title = "x", servings = 1,
            ingredients = listOf(Ingredient("i1", "large red onion", 1.0), Ingredient("i2", "For the sauce", isHeading = true)),
            steps = listOf(Step("s1", "Use a large red pan for the sauce.")),
        )
        assertTrue(uses(r, 0).isEmpty())
    }

    @Test fun keywordsSkipShortAndStopWords() {
        assertEquals(listOf("bell", "pepper"), IngredientUses.keywords("red bell pepper"))
        assertEquals(listOf("flour"), IngredientUses.keywords("all-purpose flour"))
    }

    @Test fun everySampleStepResolves() {
        for (r in samples) for (s in r.steps) IngredientUses.uses(r, s)
    }
}
