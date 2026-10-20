package com.blackchapel.recipai.data

import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.RecipeJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.repo(file: File): RecipeRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RecipeRepository(file, this, dispatcher, clock = { 1_000L }).also { advanceUntilIdle() }
    }

    private fun recipe(id: String) = Recipe(id = id, title = "Recipe $id", servings = 2)

    private fun write(file: File, vararg recipes: Recipe) = file.writeText(RecipeJson.encode(recipes.toList()))

    @Test
    fun firstLaunchIsEmptyWithNoExamples() = runTest {
        val file = File(tmp.root, "recipes.json")
        val r = repo(file)
        assertTrue(r.loaded.value)
        assertTrue(r.recipes.value.isEmpty())
        assertNull(r.loadError.value)
    }

    @Test
    fun changesSurviveARestart() = runTest {
        val file = File(tmp.root, "recipes.json")
        val first = repo(file)
        first.upsert(recipe("old"))
        first.upsert(recipe("new"))
        assertEquals(true, first.toggleFavorite("new"))
        first.delete("old")
        advanceUntilIdle()

        val second = repo(file)
        assertEquals(listOf("new"), second.recipes.value.map { it.id })
        assertTrue(second.get("new")!!.favorite)
    }

    @Test
    fun deleteCanBeUndoneInPlace() = runTest {
        val r = repo(File(tmp.root, "recipes.json"))
        listOf("a", "b", "c").forEach { r.upsert(recipe(it)) }
        val before = r.recipes.value
        val (index, gone) = r.delete(before[1].id)!!
        assertEquals(before.size - 1, r.recipes.value.size)
        r.restore(index, gone)
        assertEquals(before, r.recipes.value)
        assertNull(r.delete("missing"))
    }

    @Test
    fun exampleRecipesFromEarlierBuildsAreRemovedAndTheRemovalIsSaved() = runTest {
        val file = File(tmp.root, "recipes.json")
        write(
            file,
            recipe("mine"),
            recipe("shakshuka").copy(isSample = true),
            // An example the user added a photo to becomes an ordinary recipe.
            recipe("kept").copy(isSample = true, photos = listOf("p1.jpg")),
        )
        val r = repo(file)
        advanceUntilIdle()
        assertEquals(listOf("mine", "kept"), r.recipes.value.map { it.id })
        assertFalse(r.get("kept")!!.isSample)
        assertEquals(r.recipes.value, RecipeJson.decode(file.readText()))
    }

    @Test
    fun photoCleanUpIsClaimedOnlyOncePerRepository() = runTest {
        // The repository is process-wide, so a second ViewModel asking too
        // (a task re-created, a config change) must not clean up again.
        val r = repo(File(tmp.root, "recipes.json"))
        assertTrue(r.claimPhotoCleanUp())
        assertFalse(r.claimPhotoCleanUp())
        // A different repository (a test, never the app) has its own claim.
        assertTrue(repo(File(tmp.root, "other.json")).claimPhotoCleanUp())
    }

    @Test
    fun anUnreadableFileIsKeptAndNotOverwritten() = runTest {
        val file = File(tmp.root, "recipes.json")
        file.writeText("{ this is not a recipe file")
        val r = repo(file)
        assertNotNull(r.loadError.value)
        val kept = tmp.root.listFiles()!!.single { it.name.startsWith("recipes.json.unreadable-") }
        assertEquals("{ this is not a recipe file", kept.readText())
        // The library starts empty in its place.
        assertTrue(r.recipes.value.isEmpty())
    }

    @Test
    fun aFileFromTheFirstBuildKeepsFavoritesTagsAndNotesAfterAnUpdate() = runTest {
        val file = File(tmp.root, "recipes.json")
        // Literal text from the first build's format: nothing here may be lost when the app updates.
        file.writeText(
            """{"version":1,"recipes":[{"id":"r1","title":"Soup","servings":4,"tags":["dinner"],"notes":"Better next day","favorite":true,"createdAt":1,"updatedAt":2}]}""",
        )
        val r = repo(file)
        advanceUntilIdle()
        assertNull(r.loadError.value)
        val soup = r.get("r1")!!
        assertTrue(soup.favorite)
        assertEquals(listOf("dinner"), soup.tags)
        assertEquals("Better next day", soup.notes)
        // Opening the app did not rewrite or damage the file.
        assertEquals(listOf("r1"), RecipeJson.decode(file.readText()).map { it.id })
    }
}
