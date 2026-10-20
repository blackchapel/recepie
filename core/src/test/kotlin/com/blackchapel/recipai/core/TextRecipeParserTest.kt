package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextRecipeParserTest {
    @Test fun parsesAMessyBlogPost() {
        val r = TextRecipeParser.parse(ImportSamples.TOFU_BOWLS)
        assertEquals("Crispy Gochujang Tofu Bowls", r.title)
        assertTrue(r.description.startsWith("These sticky, spicy tofu bowls"))
        assertEquals(3, r.servings)
        assertFalse(r.servingsGuessed)
        assertEquals(13, r.ingredients.count { !it.isHeading })
        assertEquals(listOf("For the sauce", "To serve"), r.ingredients.filter { it.isHeading }.map { it.name })
        assertEquals(6, r.steps.size)
        assertEquals(3, r.timerCount)
        assertEquals(5, r.skippedLines)
        assertEquals("Leftovers keep for 3 days in the fridge.", r.notes)
        assertTrue("korean" in r.tags && "spicy" in r.tags)
        assertTrue(r.isUsable)
    }

    @Test fun parsesNumberedStepsAndUppercaseHeadings() {
        val r = TextRecipeParser.parse(ImportSamples.LEMON_ORZO)
        assertEquals("One-Pot Lemon Orzo with Spinach", r.title)
        assertEquals(4, r.servings)
        assertFalse(r.servingsGuessed)
        assertEquals(5, r.steps.size)
        assertEquals(12.0, r.steps[2].minutes)
        assertTrue(r.steps[0].text.startsWith("Melt the butter"))
        val orzo = r.ingredients.first { it.name == "orzo" }
        assertEquals(1.5, orzo.quantity)
        assertEquals("cup", orzo.unit)
        assertTrue("one-pot" in r.tags && "italian" in r.tags && "quick" in r.tags)
        assertFalse("drinks" in r.tags, "lemon juice in a step must not make it a drink")
        assertFalse("vegetarian" in r.tags, "chicken broth is not vegetarian")
    }

    @Test fun parsesIngredientLines() {
        val tofu = TextRecipeParser.parseIngredientLine("▢ 1 (14 oz) block extra-firm tofu, pressed")!!
        assertEquals(1.0, tofu.quantity)
        assertEquals("block", tofu.unit)
        assertEquals("extra-firm tofu", tofu.name)
        assertEquals("14 oz, pressed", tofu.note)

        val garlic = TextRecipeParser.parseIngredientLine("2 garlic cloves, minced")!!
        assertEquals("", garlic.unit)
        assertEquals("garlic cloves", garlic.name)
        assertEquals("minced", garlic.note)

        val salt = TextRecipeParser.parseIngredientLine("Salt & black pepper, to taste")!!
        assertNull(salt.quantity)
        assertEquals("Salt & black pepper", salt.name)

        val range = TextRecipeParser.parseIngredientLine("2-3 tbsp Chili oil (optional)")!!
        assertEquals(2.0, range.quantity)
        assertEquals("tbsp", range.unit)
        assertEquals("chili oil", range.name)
        assertEquals("2–3, optional", range.note)

        assertNull(TextRecipeParser.parseIngredientLine("  ▢  "))
    }

    @Test fun detectsStepTimes() {
        assertEquals(12.0, TextRecipeParser.detectMinutes("simmer for 10-12 minutes until tender"))
        assertEquals(80.0, TextRecipeParser.detectMinutes("Roast for 1 hour 20 minutes"))
        assertEquals(6.5, TextRecipeParser.detectMinutes("Cook the eggs for 6½ minutes"))
        assertEquals(0.5, TextRecipeParser.detectMinutes("Blend for 30 seconds"))
        assertEquals(0.0, TextRecipeParser.detectMinutes("Season to taste"))
    }

    @Test fun guessesHowStepTimesScale() {
        assertEquals(TimeScale.FIXED, TextRecipeParser.guessTimeScale("Bake until golden"))
        assertEquals(TimeScale.LINEAR, TextRecipeParser.guessTimeScale("Chop the onions"))
        assertEquals(TimeScale.PARTIAL, TextRecipeParser.guessTimeScale("Fry the tofu until crisp"))
    }

    @Test fun worksWithoutHeadings() {
        val r = TextRecipeParser.parse(
            """
            Quick Garlic Noodles
            200 g noodles
            3 cloves garlic, sliced
            2 tbsp butter
            Boil the noodles for 4 minutes and drain them well.
            Fry the garlic in the butter, then toss everything together.
            """.trimIndent(),
        )
        assertEquals("Quick Garlic Noodles", r.title)
        assertEquals(3, r.ingredients.size)
        assertEquals(2, r.steps.size)
        assertTrue(r.servingsGuessed)
        assertEquals(4, r.servings)
    }

    @Test fun emptyTextIsNotUsable() {
        val r = TextRecipeParser.parse("")
        assertFalse(r.isUsable)
        assertTrue(r.tags.isEmpty())
    }
}
