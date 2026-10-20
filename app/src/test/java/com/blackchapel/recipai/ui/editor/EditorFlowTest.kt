package com.blackchapel.recipai.ui.editor

import com.blackchapel.recipai.core.DraftIngredient
import com.blackchapel.recipai.core.DraftStep
import com.blackchapel.recipai.core.ImportSamples
import com.blackchapel.recipai.core.RecipeDraft
import com.blackchapel.recipai.core.TextRecipeParser
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.data.SettingsStore
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.DetailPage
import com.blackchapel.recipai.ui.EditorPage
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

@OptIn(ExperimentalCoroutinesApi::class)
class EditorFlowTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MemorySettings : SettingsStore {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
    }

    /** An app on an empty library plus [seed] simple recipes, and the scope to cancel when done. */
    private fun TestScope.app(seed: Int = 0): Pair<AppController, CoroutineScope> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RecipeRepository(File(tmp.root, "recipes.json"), this, dispatcher, clock = { 1_000L })
        repeat(seed) { repo.upsert(com.blackchapel.recipai.core.Recipe(id = "seed$it", title = "Seed recipe $it", servings = 2)) }
        advanceUntilIdle()
        val scope = CoroutineScope(dispatcher + Job())
        return AppController(repo, MemorySettings(), scope) to scope
    }

    private fun AppController.topEditor(): EditorPage = stack.last() as EditorPage

    @Test
    fun aNewRecipeIsSavedAndOpened() = runTest {
        val (app, scope) = app()
        app.newRecipe()
        val page = app.topEditor()
        val state = app.editorFor(page)!!
        assertSame(state, app.editorFor(page))
        assertTrue(state.initial.isNew)
        assertFalse(state.dirty)

        val row = state.draft.ingredients.single().id
        val step = state.draft.steps.single().id
        state.draft = state.draft.copy(title = "Cinnamon toast")
            .updateIngredient(row) { it.copy(quantity = "2", unit = "Slices", name = "bread") }
            .updateStep(step) { it.copy(text = "Toast for 3 minutes.", minutes = "3") }
        assertTrue(state.dirty)

        app.saveEditor(page, state.effective)
        // A second tap on Save, before the page has gone, saves nothing more.
        val count = app.repo.recipes.value.size
        app.saveEditor(page, state.effective)
        assertEquals(count, app.repo.recipes.value.size)
        val saved = app.repo.recipes.value.first()
        assertEquals("Cinnamon toast", saved.title)
        assertEquals("slice", saved.ingredients.single().unit)
        assertEquals(3.0, saved.steps.single().minutes, 0.0)
        assertTrue(saved.updatedAt > 0)
        val top = app.stack.single() as DetailPage
        assertEquals(saved.id, top.recipeId)
        assertEquals("Saved “Cinnamon toast”", app.toast?.message)
        scope.cancel()
    }

    @Test
    fun editingFromTheDetailPageReturnsToIt() = runTest {
        val (app, scope) = app(seed = 2)
        val original = app.repo.recipes.value.first()
        app.openRecipe(original.id)
        val detail = app.stack.single()
        app.editRecipe(original.id)
        val page = app.topEditor()
        val state = app.editorFor(page)!!
        assertEquals(original.id, state.draft.recipeId)
        assertFalse(state.dirty)

        state.draft = state.draft.copy(title = "Renamed")
        app.saveEditor(page, state.effective)

        assertSame(detail, app.stack.single())
        val now = app.repo.get(original.id)!!
        assertEquals("Renamed", now.title)
        assertEquals(original.ingredients.map { it.quantity }, now.ingredients.map { it.quantity })
        assertTrue(now.updatedAt > original.updatedAt)
        assertEquals("Saved your changes", app.toast?.message)
        scope.cancel()
    }

    @Test
    fun photosThatFinishCopyingJoinTheLiveDraft() = runTest {
        val (app, scope) = app()
        app.newRecipe()
        val state = app.editorFor(app.topEditor())!!
        state.draft = state.draft.copy(photos = listOf("a.jpg", "b.jpg"), coverIndex = 1)

        // A photo was removed after the pick started and before the copy finished:
        // the new ones merge into what the draft holds now, not what it held then.
        state.draft = state.draft.copy(photos = listOf("b.jpg"), coverIndex = 0)
        state.addPhotos(listOf("c.jpg", "d.jpg"))

        assertEquals(listOf("b.jpg", "c.jpg", "d.jpg"), state.draft.photos)
        assertEquals(0, state.draft.coverIndex)
        assertTrue(state.dirty)
        scope.cancel()
    }

    @Test
    fun editingFromThePreviewSheetOpensTheRecipe() = runTest {
        val (app, scope) = app(seed = 2)
        val id = app.repo.recipes.value.last().id
        app.editRecipe(id)
        val page = app.topEditor()
        app.saveEditor(page, app.editorFor(page)!!.effective)
        assertEquals(id, (app.stack.single() as DetailPage).recipeId)
        scope.cancel()
    }

    @Test
    fun cancellingLeavesTheLibraryAlone() = runTest {
        val (app, scope) = app(seed = 2)
        val before = app.repo.recipes.value
        app.editRecipe(before.first().id)
        val page = app.topEditor()
        app.editorFor(page)!!.draft = app.editorFor(page)!!.draft.copy(title = "Never saved")
        app.closeEditor(page)
        assertTrue(app.stack.isEmpty())
        assertEquals(before, app.repo.recipes.value)
        // A new editor on the same recipe starts from the saved version.
        app.editRecipe(before.first().id)
        assertEquals(before.first().title, app.editorFor(app.topEditor())!!.draft.title)
        scope.cancel()
    }

    @Test
    fun aMissingRecipeHasNoEditor() = runTest {
        val (app, scope) = app()
        app.editRecipe("gone")
        assertNull(app.editorFor(app.topEditor()))
        scope.cancel()
    }

    @Test
    fun reviewingAnImportPrefillsItAndClearsThePastedText() = runTest {
        val (app, scope) = app()
        app.importText = ImportSamples.TOFU_BOWLS
        val parsed = TextRecipeParser.parse(app.importText)
        app.reviewImport(parsed)
        val page = app.topEditor()
        val state = app.editorFor(page)!!
        assertTrue(state.initial.isNew)
        assertEquals(parsed.title, state.draft.title)
        assertEquals(parsed.ingredients.size, state.draft.ingredients.size)
        assertFalse(state.dirty)

        app.saveEditor(page, state.effective)
        assertEquals(parsed.title, app.repo.recipes.value.first().title)
        assertEquals(1, app.repo.recipes.value.size)
        assertEquals("", app.importText)
        scope.cancel()
    }

    @Test
    fun rowsBeingRemovedDontCountAndUnaddedTagsDo() = runTest {
        val (app, scope) = app()
        app.newRecipe()
        val state = app.editorFor(app.topEditor())!!
        val id = state.newId()
        state.draft = state.draft.addIngredient(id)
        state.added(id, FieldKey.qty(id))
        assertEquals(FieldKey.qty(id), state.focusRequest)
        assertTrue(id in state.addedRows)
        assertTrue(state.dirty)

        state.remove(id)
        assertFalse(state.dirty)
        assertEquals(2, state.draft.ingredients.size)
        state.gone(id)
        assertEquals(1, state.draft.ingredients.size)
        assertFalse(id in state.addedRows)

        state.tagQuery = "spicy"
        assertTrue(state.dirty)
        scope.cancel()
    }

    @Test
    fun aFailedSaveGoesToTheFirstProblem() {
        val draft = RecipeDraft(
            recipeId = null,
            title = "Soup",
            ingredients = listOf(DraftIngredient("a", "2", "", "leeks"), DraftIngredient("b", "", "g", ""), DraftIngredient("c", "lots", "", "salt")),
            steps = listOf(DraftStep("s", "Simmer", "x")),
        )
        assertEquals(FieldKey.name("b"), FieldKey.firstError(draft, draft.errors()))
        assertEquals(FieldKey.TITLE, FieldKey.firstError(draft.copy(title = " "), draft.copy(title = " ").errors()))
        val onlyStep = draft.copy(ingredients = draft.ingredients.take(1))
        assertEquals(FieldKey.minutes("s"), FieldKey.firstError(onlyStep, onlyStep.errors()))
        assertNull(FieldKey.firstError(onlyStep.copy(steps = emptyList()), onlyStep.copy(steps = emptyList()).errors()))
    }

    @Test
    fun findsWhatAnEditInserted() {
        assertEquals("a\nb", inserted("", "a\nb"))
        assertEquals("\n", inserted("eggs", "eggs\n"))
        assertEquals("x\ny", inserted("abc", "ax\nyc"))
        assertEquals("1\n2", inserted("aa", "aa1\n2"))
        assertEquals("", inserted("same", "same"))
    }
}
