package com.blackchapel.recipai.data

import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.RecipeJson
import com.blackchapel.recipai.core.SampleRecipes
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

    @Test
    fun firstLaunchLoadsTheExamplesAndSavesThem() = runTest {
        val file = File(tmp.root, "recipes.json")
        val r = repo(file)
        assertTrue(r.loaded.value)
        assertEquals(SampleRecipes.all(1_000L).map { it.id }, r.recipes.value.map { it.id })
        assertEquals(r.recipes.value, RecipeJson.decode(file.readText()))
    }

    @Test
    fun changesSurviveARestart() = runTest {
        val file = File(tmp.root, "recipes.json")
        val first = repo(file)
        first.upsert(recipe("new"))
        val sample = first.recipes.value.last().id
        assertEquals(true, first.toggleFavorite("new"))
        first.delete(sample)
        advanceUntilIdle()

        val second = repo(file)
        val ids = second.recipes.value.map { it.id }
        assertEquals("new", ids.first())
        assertTrue(second.get("new")!!.favorite)
        assertFalse(sample in ids)
    }

    @Test
    fun deleteCanBeUndoneInPlace() = runTest {
        val r = repo(File(tmp.root, "recipes.json"))
        val before = r.recipes.value
        val (index, gone) = r.delete(before[2].id)!!
        assertEquals(before.size - 1, r.recipes.value.size)
        r.restore(index, gone)
        assertEquals(before, r.recipes.value)
        assertNull(r.delete("missing"))
    }

    @Test
    fun restoreSamplesAddsOnlyMissingOnes() = runTest {
        val r = repo(File(tmp.root, "recipes.json"))
        assertEquals(0, r.restoreSamples())
        r.delete(r.recipes.value.first().id)
        assertEquals(1, r.restoreSamples())
        assertEquals(SampleRecipes.all(0).size, r.recipes.value.size)
    }

    @Test
    fun anUnreadableFileIsKeptAndNotOverwritten() = runTest {
        val file = File(tmp.root, "recipes.json")
        file.writeText("{ this is not a recipe file")
        val r = repo(file)
        assertNotNull(r.loadError.value)
        val kept = tmp.root.listFiles()!!.single { it.name.startsWith("recipes.json.unreadable-") }
        assertEquals("{ this is not a recipe file", kept.readText())
        // The examples were loaded in its place.
        assertEquals(SampleRecipes.all(0).size, r.recipes.value.size)
    }
}
