package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LibraryTest {
    private val samples = SampleRecipes.all(now = 1_000_000_000_000L)

    @Test
    fun `json round trip keeps every field`() {
        val back = RecipeJson.decode(RecipeJson.encode(samples))
        assertEquals(samples, back)
    }

    @Test
    fun `json rejects other text and newer versions`() {
        assertFailsWith<IllegalArgumentException> { RecipeJson.decode("not json") }
        assertFailsWith<IllegalArgumentException> { RecipeJson.decode("""{"version":99,"recipes":[]}""") }
    }

    @Test
    fun `json ignores unknown keys from a later version of the same format`() {
        val back = RecipeJson.decode("""{"version":1,"recipes":[{"id":"a","title":"T","servings":2,"photos":[]}]}""")
        assertEquals("T", back.single().title)
    }

    @Test
    fun `tag counts are sorted by count then name`() {
        val counts = Library.tagCounts(samples)
        assertEquals(counts.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }), counts)
        assertTrue(counts.all { (tag, n) -> samples.count { tag in it.tags } == n })
    }

    @Test
    fun `filters by tag, quick, favorites and query`() {
        val tag = samples.first().tags.first()
        assertTrue(Library.filter(samples, TagFilter.Tag(tag)).all { tag in it.tags })
        assertTrue(Library.filter(samples, TagFilter.Quick).all { Scaling.totalMinutes(it) <= 30 })
        assertTrue(Library.filter(samples, favoritesOnly = true).all { it.favorite })
        val garlic = Library.filter(samples, query = "GARLIC")
        assertTrue(garlic.isNotEmpty())
        assertTrue(garlic.all { r -> r.ingredients.any { "garlic" in it.name.lowercase() } || "garlic" in r.title.lowercase() || "garlic" in r.description.lowercase() })
    }

    @Test
    fun `sorts`() {
        assertEquals(samples.map { it.title.lowercase() }.sorted(), Library.sorted(samples, SortOrder.A_TO_Z).map { it.title.lowercase() })
        val quick = Library.sorted(samples, SortOrder.QUICKEST).map { Scaling.totalMinutes(it) }
        assertEquals(quick.sorted(), quick)
        val recent = Library.sorted(samples, SortOrder.RECENT).map { it.updatedAt }
        assertEquals(recent.sortedDescending(), recent)
    }

    @Test
    fun `search ranks title over ingredient over tag`() {
        val hits = Library.search(samples, "feta")
        assertEquals("Shakshuka with Feta", hits.first().recipe.title)
        assertEquals(null, hits.first().why)
        val byIngredient = Library.search(samples, "cumin").first { it.why != null }
        assertTrue(byIngredient.why!!.startsWith("Uses "))
        assertEquals(emptyList(), Library.search(samples, "   "))
    }

    @Test
    fun `imported recipe gets ids, a title and a source`() {
        var n = 0
        val parsed = TextRecipeParser.parse(ImportSamples.TOFU_BOWLS)
        val r = parsed.toRecipe(now = 5, source = "example.com/tofu") { "id${n++}" }
        assertEquals("id0", r.id)
        assertEquals(parsed.title, r.title)
        assertEquals(parsed.ingredients.size, r.ingredients.size)
        assertEquals(parsed.steps.size, r.steps.size)
        assertEquals((r.ingredients.map { it.id } + r.steps.map { it.id } + r.id).toSet().size, 1 + r.ingredients.size + r.steps.size)
        assertEquals("example.com/tofu", r.source)
        assertEquals(5, r.createdAt)
        val blank = parsed.copy(title = "", servings = 0).toRecipe(0) { "x" }
        assertEquals("Imported recipe", blank.title)
        assertEquals(1, blank.servings)
    }
}
