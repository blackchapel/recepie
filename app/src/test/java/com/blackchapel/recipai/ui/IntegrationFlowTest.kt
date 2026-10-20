package com.blackchapel.recipai.ui

import com.blackchapel.recipai.core.ImportSamples
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

    private fun TestScope.app(): Pair<AppController, CoroutineScope> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RecipeRepository(File(tmp.root, "recipes.json"), this, dispatcher, clock = { 1_000L })
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
        val (app, scope) = app()
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
        val (app, scope) = app()
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
}
