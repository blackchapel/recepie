package com.blackchapel.recipai.ui

import com.blackchapel.recipai.core.ImportSamples
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.core.TextRecipeParser
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Flows that cross modules: import review + editor, delete + pushed pages. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationFlowTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MemorySettings : SettingsStore {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
    }

    private fun TestScope.app(seed: Int = 0): Pair<AppController, CoroutineScope> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RecipeRepository(File(tmp.root, "recipes.json"), this, dispatcher, clock = { 1_000L })
        repeat(seed) { repo.upsert(com.blackchapel.recipai.core.Recipe(id = "seed$it", title = "Seed recipe $it", servings = 2)) }
        advanceUntilIdle()
        val scope = CoroutineScope(dispatcher + Job())
        return AppController(repo, MemorySettings(), scope) to scope
    }

    @Test
    fun aReviewedLinkImportKeepsItsSource() = runTest {
        val (app, scope) = app()
        val parsed = TextRecipeParser.parse(ImportSamples.TOFU_BOWLS)
            .copy(source = "kitchen.example.com/shakshuka", servingsLabel = "bowls")
        app.reviewImport(parsed)
        val page = app.stack.last() as EditorPage
        val state = app.editorFor(page)!!
        app.saveEditor(page, state.effective)
        advanceUntilIdle()

        val top = app.stack.last() as DetailPage
        val saved = app.repo.get(top.recipeId)!!
        assertEquals("kitchen.example.com/shakshuka", saved.source)
        assertEquals("bowls", saved.servingsLabel)
        scope.cancel()
    }

    @Test
    fun deletingARecipeClosesEveryPageAboutIt() = runTest {
        val (app, scope) = app(seed = 2)
        val id = app.repo.recipes.value.first().id
        val other = app.repo.recipes.value.last().id
        app.openRecipe(other)
        app.openRecipe(id)
        app.startCooking(id)
        app.editRecipe(id)
        app.deleteRecipe(id)
        advanceUntilIdle()

        assertEquals(1, app.stack.size)
        assertTrue((app.stack.single() as DetailPage).recipeId == other)
        scope.cancel()
    }

    @Test
    fun sharedTextKeepsAnOpenEditor() = runTest {
        val (app, scope) = app(seed = 2)
        app.newRecipe()
        app.handleSharedText(ImportSamples.TOFU_BOWLS)
        assertTrue(app.stack.single() is EditorPage)
        assertEquals(Sheet.Import(ImportTab.TEXT), app.sheet)

        app.stack.clear()
        app.openRecipe(app.repo.recipes.value.first().id)
        app.handleSharedText(ImportSamples.TOFU_BOWLS)
        assertTrue(app.stack.isEmpty())
        scope.cancel()
    }

    @Test
    fun savingASharedImportKeepsAnOpenEditorsDraft() = runTest {
        val (app, scope) = app(seed = 1)
        val parsed = TextRecipeParser.parse(ImportSamples.TOFU_BOWLS)
        for (link in listOf(false, true)) {
            app.stack.clear()
            app.newRecipe()
            val page = app.stack.last() as EditorPage
            val state = app.editorFor(page)!!
            state.draft = state.draft.copy(title = "Half typed")

            // The share opens the import sheet over the editor; Save pushes the new recipe over it.
            if (link) app.saveLinkImport(parsed) else app.saveImport(parsed)
            assertEquals(2, app.stack.size)
            assertSame(page, app.stack.first())
            assertTrue(app.stack.last() is DetailPage)
            assertEquals("Half typed", app.editorFor(page)!!.draft.title)

            // Back from the new recipe returns to the draft, still unsaved.
            assertTrue(app.back())
            assertSame(page, app.stack.single())
            assertEquals("Half typed", app.editorFor(page)!!.draft.title)
        }
        scope.cancel()
    }

    @Test
    fun savingAnImportWithNoEditorOpenShowsItOnRecipes() = runTest {
        val (app, scope) = app(seed = 1)
        val parsed = TextRecipeParser.parse(ImportSamples.TOFU_BOWLS)
        for (link in listOf(false, true)) {
            app.selectTab(Tab.SETTINGS)
            app.openBackup()
            if (link) app.saveLinkImport(parsed) else app.saveImport(parsed)
            assertEquals(Tab.RECIPES, app.tab)
            assertTrue(app.stack.single() is DetailPage)
        }
        scope.cancel()
    }

    @Test
    fun aTagTapDoesNotLeaveAnOpenEditor() = runTest {
        val (app, scope) = app(seed = 1)
        app.newRecipe()
        val page = app.stack.last() as EditorPage
        app.editorFor(page)!!.draft = app.editorFor(page)!!.draft.copy(title = "Half typed")
        app.openRecipe(app.repo.recipes.value.first().id)

        app.showTag(TagFilter.Quick)
        assertEquals(2, app.stack.size)
        assertSame(page, app.stack.first())
        assertEquals(TagFilter.All, app.libraryTag)
        assertTrue(app.toast != null)

        // With no editor open it still goes to Recipes, filtered.
        app.stack.clear()
        app.showTag(TagFilter.Quick)
        assertEquals(TagFilter.Quick, app.libraryTag)
        assertEquals(Tab.RECIPES, app.tab)
        scope.cancel()
    }

    @Test
    fun backWalksThePagesThenHomeToRecipesThenExits() = runTest {
        val (app, scope) = app(seed = 1)
        app.selectTab(Tab.SETTINGS)
        app.openBackup()
        assertTrue(app.canGoBack)

        // Pages first, in the tab they were opened from...
        assertTrue(app.back())
        assertTrue(app.stack.isEmpty())
        assertEquals(Tab.SETTINGS, app.tab)
        // ...then Recipes, the home tab...
        assertTrue(app.canGoBack)
        assertTrue(app.back())
        assertEquals(Tab.RECIPES, app.tab)
        // ...and from Recipes there is nothing left: the system exits.
        assertFalse(app.canGoBack)
        assertFalse(app.back())
        scope.cancel()
    }

    @Test
    fun backFromEveryOtherTabGoesToRecipes() = runTest {
        val (app, scope) = app()
        for (tab in listOf(Tab.FAVORITES, Tab.SEARCH, Tab.SETTINGS)) {
            app.selectTab(tab)
            assertTrue(app.canGoBack)
            assertTrue(app.back())
            assertEquals(Tab.RECIPES, app.tab)
        }
        scope.cancel()
    }

    @Test
    fun aSheetClosesBeforeAnythingElse() = runTest {
        val (app, scope) = app()
        app.selectTab(Tab.SEARCH)
        app.sheet = Sheet.Import()
        assertTrue(app.back())
        assertNull(app.sheet)
        assertEquals(Tab.SEARCH, app.tab)
        scope.cancel()
    }
}
