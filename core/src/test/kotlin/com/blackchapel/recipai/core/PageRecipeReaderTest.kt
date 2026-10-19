package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageRecipeReaderTest {
    @Test fun prefersTheRecipeCardAndKeepsTheSource() {
        val read = PageRecipeReader.read(page("wprm-graph.html"), "https://www.kitchen.example.com/recipes/crispy-gochujang-tofu-bowls/?utm_source=share#recipe")
        assertEquals(RecipeSource.RECIPE_CARD, read.via)
        val r = assertNotNull(read.recipe)
        assertEquals("kitchen.example.com/recipes/crispy-gochujang-tofu-bowls", r.source)
        assertEquals(12, r.ingredients.count { !it.isHeading })

        val saved = r.toRecipe(now = 5) { "id" }
        assertEquals("kitchen.example.com/recipes/crispy-gochujang-tofu-bowls", saved.source)
    }

    @Test fun fallsBackToThePageTextWithoutACard() {
        val read = PageRecipeReader.read(page("blog-no-card.html"), "https://crumb.example.net/banana-bread")
        assertEquals(RecipeSource.PAGE_TEXT, read.via)
        val r = assertNotNull(read.recipe)
        assertEquals("Grandma’s Banana Bread", r.title, "from the h1, not the menu above it")
        assertEquals("This is the loaf I grew up with: moist, not too sweet, and perfect for using up spotty bananas.", r.description)
        assertEquals(10, r.servings)
        assertEquals(8, r.ingredients.size)
        assertEquals(1.0 / 3, assertNotNull(r.ingredients[1].quantity), 0.001)
        assertEquals("melted butter", r.ingredients[1].name)
        assertEquals(5, r.steps.size)
        assertEquals(60.0, r.steps[3].minutes)
        assertTrue(r.steps.none { "Made this twice" in it.text }, "comments are skipped")
        assertTrue(r.ingredients.none { "joy muffins" in it.name }, "the sidebar is dropped")
        assertEquals("crumb.example.net/banana-bread", r.source)
        assertFalse("Subscribe" in read.pageText)
    }

    @Test fun reportsNoRecipeOnAnArticle() {
        val read = PageRecipeReader.read(page("article.html"), "https://news.example.com/prices")
        assertEquals(RecipeSource.NONE, read.via)
        assertNull(read.recipe)
        assertTrue(read.pageText.startsWith("Food prices rise again"), read.pageText)
    }

    @Test fun anIncompleteCardFallsBackToTheTextButKeepsItsName() {
        val html = """
            <html><head><script type="application/ld+json">{"@type":"Recipe","name":"Lemon Drizzle Cake"}</script></head>
            <body><nav>Home Cakes</nav><article><h1>Best ever lemon drizzle!</h1>
            <h3>Ingredients</h3><p>225g butter<br>225g caster sugar<br>4 eggs<br>2 lemons, zested</p>
            <h3>Method</h3><p>Heat the oven to 180C and line a loaf tin.</p><p>Beat everything together and bake for 45 minutes.</p>
            </article></body></html>
        """.trimIndent()
        val read = PageRecipeReader.read(html)
        assertEquals(RecipeSource.PAGE_TEXT, read.via)
        val r = assertNotNull(read.recipe)
        assertEquals("Lemon Drizzle Cake", r.title)
        assertEquals(4, r.ingredients.size)
        assertEquals("g", r.ingredients[0].unit)
        assertEquals(2, r.steps.size)
        assertNull(r.source)
    }

    @Test fun describesFailures() {
        assertEquals(
            "kitchen.example.com didn’t let Recipai read this page (error 403). Open it in your browser, copy the recipe and paste the text instead.",
            LinkMessages.fetchFailed(FetchFailure.BLOCKED, "kitchen.example.com", 403),
        )
        FetchFailure.entries.forEach { assertTrue(LinkMessages.fetchFailed(it, "x.com", 500).isNotBlank()) }
        assertTrue("paste the text instead" in LinkMessages.noRecipe("x.com"))
    }
}
