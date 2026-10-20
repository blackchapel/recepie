package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Updating the app must never lose what is on the phone. These are the
 * recipe files earlier builds actually wrote (as literal text, not built
 * from today's classes), and they must keep loading with every field.
 * When the format changes, ADD a literal here; never edit these.
 */
class UpdateCompatibilityTest {
    /** The first build (M1): no photos, no cover index. Only non-default fields are written. */
    private val firstBuildFile = """
        {"version":1,"recipes":[
          {"id":"r1","title":"Soup","description":"Warm.","servings":4,"servingsLabel":"bowls",
           "tags":["dinner","vegetarian"],
           "ingredients":[{"id":"i1","name":"onion","quantity":2.0,"note":"diced"},{"id":"i2","name":"For the broth","isHeading":true}],
           "steps":[{"id":"s1","text":"Simmer.","minutes":20.0,"timeScale":"PARTIAL"},{"id":"s2","text":"Serve."}],
           "notes":"Better next day","favorite":true,"source":"example.com/soup","createdAt":1000,"updatedAt":2000},
          {"id":"r2","title":"Old example","servings":2,"isSample":true,"createdAt":1,"updatedAt":1}
        ]}
    """.trimIndent()

    /** After photos landed: photo file names and a cover index. */
    private val withPhotosFile = """
        {"version":1,"recipes":[{"id":"r3","title":"Cake","servings":8,"favorite":true,
          "photos":["3f2b6c1e-1111-4222-8333-444455556666.jpg","a.jpg"],"coverIndex":1,"createdAt":5,"updatedAt":6}]}
    """.trimIndent()

    @Test
    fun theFirstBuildsFileStillLoadsWithEveryField() {
        val recipes = RecipeJson.decode(firstBuildFile)
        assertEquals(2, recipes.size)
        val soup = recipes.first { it.id == "r1" }
        assertEquals("Soup", soup.title)
        assertEquals("Warm.", soup.description)
        assertEquals(4, soup.servings)
        assertEquals("bowls", soup.servingsLabel)
        assertEquals(listOf("dinner", "vegetarian"), soup.tags)
        assertTrue(soup.favorite)
        assertEquals("Better next day", soup.notes)
        assertEquals("example.com/soup", soup.source)
        assertEquals(1000L, soup.createdAt)
        assertEquals(2000L, soup.updatedAt)
        assertEquals(2.0, soup.ingredients[0].quantity)
        assertEquals("diced", soup.ingredients[0].note)
        assertTrue(soup.ingredients[1].isHeading)
        assertEquals(20.0, soup.steps[0].minutes)
        assertEquals(TimeScale.PARTIAL, soup.steps[0].timeScale)
        assertEquals(TimeScale.FIXED, soup.steps[1].timeScale)
        // Fields that did not exist yet take their defaults.
        assertEquals(emptyList(), soup.photos)
        assertEquals(0, soup.coverIndex)
        assertTrue(recipes.first { it.id == "r2" }.isSample)
    }

    @Test
    fun theFileWithPhotosStillLoads() {
        val cake = RecipeJson.decode(withPhotosFile).single()
        assertEquals(listOf("3f2b6c1e-1111-4222-8333-444455556666.jpg", "a.jpg"), cake.photos)
        assertEquals(1, cake.coverIndex)
        assertTrue(cake.favorite)
    }

    @Test
    fun savingAnOldFileBackKeepsEverything() {
        val loaded = RecipeJson.decode(firstBuildFile)
        assertEquals(loaded, RecipeJson.decode(RecipeJson.encode(loaded)))
    }

    @Test
    fun fieldsAFutureVersionAddsAreIgnoredNotFatal() {
        val withNewField = """{"version":1,"recipes":[{"id":"x","title":"T","servings":2,"rating":5,"favorite":true}]}"""
        val r = RecipeJson.decode(withNewField).single()
        assertEquals("T", r.title)
        assertTrue(r.favorite)
    }

    @Test
    fun aNewerFileFormatIsRefusedLoudlyNotMisread() {
        // The app then keeps a copy of the file and starts empty instead of overwriting it.
        assertFailsWith<IllegalArgumentException> { RecipeJson.decode("""{"version":2,"recipes":[]}""") }
        assertFalse(RecipeJson.VERSION > 1, "bump VERSION only with a migration and a new golden file above")
    }
}
