package com.blackchapel.recipai.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackchapel.recipai.core.ParsedRecipe
import com.blackchapel.recipai.core.RecipeDraft
import com.blackchapel.recipai.core.SortOrder
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.core.TextRecipeParser
import com.blackchapel.recipai.core.toRecipe
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.data.SettingsStore
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.editor.EditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class Tab { RECIPES, FAVORITES, SEARCH, SETTINGS }

/** A page pushed over the tabs. Each push is a new instance. */
sealed interface Page

/** Recipe detail, with its own scale and checklist state. */
@Stable
class DetailPage(val recipeId: String) : Page {
    var factor by mutableDoubleStateOf(1.0)

    /** The ingredient whose amount was typed to set [factor], if any. */
    var anchorId by mutableStateOf<String?>(null)
    val checked = mutableStateListOf<String>()
    val done = mutableStateListOf<String>()

    fun setScale(f: Double, anchor: String? = null) {
        factor = f.coerceIn(0.05, 50.0)
        anchorId = anchor
    }
}

/**
 * The editor, a modal page that slides up (editor module). [recipeId] null
 * means a new recipe; [preset] prefills it from an import preview.
 */
@Stable
class EditorPage(val recipeId: String?, val preset: ParsedRecipe? = null) : Page

/** Full-screen cook mode at the detail page's scale (cook module). */
@Stable
class CookPage(val recipeId: String, val factor: Double) : Page

/** Google Drive backup, pushed from Settings (backup module). */
@Stable
class BackupPage : Page

enum class ImportTab { TEXT, LINK }

sealed interface Sheet {
    data class Import(val tab: ImportTab = ImportTab.TEXT) : Sheet
    data class Amount(val page: DetailPage, val ingredientId: String) : Sheet

    /** Press-and-hold preview ([preview] = true) or the detail page's more menu. */
    data class Actions(val recipeId: String, val preview: Boolean) : Sheet
    data class ConfirmDelete(val recipeId: String) : Sheet

    /** Every running timer (timers module). */
    data object Timers : Sheet

    /** Full-screen photo viewer (photos module). */
    data class PhotoViewer(val recipeId: String, val index: Int) : Sheet
}

data class Toast(
    val message: String,
    val icon: Ico? = null,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

/**
 * App state that outlives configuration changes (owned by the activity's
 * ViewModel): navigation, list filters, the open sheet and the toast.
 * Plain Compose snapshot state, so screens read it directly.
 */
@Stable
class AppController(
    val repo: RecipeRepository,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    var tab by mutableStateOf(Tab.RECIPES)
        private set
    val stack = mutableStateListOf<Page>()

    /** Bumped when the current tab is tapped again: scroll it to the top. */
    var reselectTick by mutableIntStateOf(0)
        private set

    var libraryTag by mutableStateOf<TagFilter>(TagFilter.All)
    var libraryQuery by mutableStateOf("")
    var sort by mutableStateOf(SortOrder.RECENT)
    var searchQuery by mutableStateOf("")

    /** Kept between openings of the import sheet, like a draft. */
    var importText by mutableStateOf("")

    /** The link in the import sheet's "From a link" tab (link-import module). */
    var importUrl by mutableStateOf("")

    var sheet by mutableStateOf<Sheet?>(null)
    var toast by mutableStateOf<Toast?>(null)

    /** The detail hero is under the status bar (wants light icons). */
    var heroUnderStatusBar by mutableStateOf(false)

    fun selectTab(t: Tab) {
        if (t == tab && stack.isEmpty()) {
            reselectTick++
            return
        }
        stack.clear()
        tab = t
    }

    fun openRecipe(id: String) {
        stack.add(DetailPage(id))
    }

    /** Back: closes the sheet, else pops a page. False when there was nothing to do. */
    fun back(): Boolean {
        if (sheet != null) {
            sheet = null
            return true
        }
        if (stack.isNotEmpty()) {
            // removeAt, not removeLast: List.removeLast is a Java 21 method
            // on newer JDKs and crashes on older Android versions.
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    /** Shows Recipes filtered by [filter] (from a tag on detail or a Search tile). */
    fun showTag(filter: TagFilter) {
        stack.clear()
        sheet = null
        tab = Tab.RECIPES
        libraryQuery = ""
        libraryTag = filter
    }

    fun showToast(message: String, icon: Ico? = null, actionLabel: String? = null, action: (() -> Unit)? = null) {
        toast = Toast(message, icon, actionLabel, action)
    }

    /** Returns the new favorite state (null if the recipe is gone). */
    fun toggleFavorite(id: String): Boolean? {
        val now = repo.toggleFavorite(id) ?: return null
        showToast(if (now) "Added to favorites" else "Removed from favorites", Ico.Heart)
        return now
    }

    fun deleteRecipe(id: String) {
        val (index, gone) = repo.delete(id) ?: return
        stack.removeAll { it is DetailPage && it.recipeId == id }
        showToast("Deleted “${gone.title}”", Ico.Trash, "Undo") {
            repo.restore(index, gone)
            showToast("Recipe restored", Ico.Check)
        }
    }

    fun saveImport(parsed: ParsedRecipe) {
        val recipe = parsed.toRecipe(now = System.currentTimeMillis()) { UUID.randomUUID().toString() }
        repo.upsert(recipe)
        importText = ""
        sheet = null
        stack.clear()
        tab = Tab.RECIPES
        openRecipe(recipe.id)
        showToast("Saved “${recipe.title}”", Ico.Check)
    }

    fun restoreSamples() {
        val n = repo.restoreSamples()
        showToast(if (n == 0) "All example recipes are already here" else "Added $n example ${if (n == 1) "recipe" else "recipes"}", Ico.Reset)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch { settingsStore.update(transform) }
    }

    /** The + button and Search's "New recipe". */
    fun newRecipe() {
        sheet = null
        stack.add(EditorPage(recipeId = null))
    }

    fun editRecipe(id: String) {
        sheet = null
        stack.add(EditorPage(recipeId = id))
    }

    /** Import sheet "Review": open the parse in the editor before saving. */
    fun reviewImport(parsed: ParsedRecipe) {
        sheet = null
        stack.add(EditorPage(recipeId = null, preset = parsed))
    }

    fun startCooking(recipeId: String, factor: Double = 1.0) {
        sheet = null
        stack.add(CookPage(recipeId, factor))
    }

    fun openBackup() {
        sheet = null
        stack.add(BackupPage())
    }

    /**
     * Text shared to Recipai from another app (share target). A link goes
     * to the "From a link" tab, anything else to "Paste text".
     */
    fun handleSharedText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        stack.clear()
        val url = Regex("""https?://\S+""").find(t)?.value
        if (url != null && t.length < url.length + 120) {
            importUrl = url
            sheet = Sheet.Import(ImportTab.LINK)
        } else {
            importText = t
            sheet = Sheet.Import(ImportTab.TEXT)
        }
    }

    // ---- Module additions go below, one section per module, so merges stay simple.

    // ---- editor ----

    /** Each open editor's working copy, kept here so it survives rotation. */
    private val editors = HashMap<EditorPage, EditorState>()

    /**
     * The editor state for [page], made on first use: the saved recipe, the
     * import being reviewed, or a blank recipe. Null when the recipe to edit
     * no longer exists.
     */
    fun editorFor(page: EditorPage): EditorState? {
        editors[page]?.let { return it }
        editors.keys.retainAll { it in stack }
        val newId = { UUID.randomUUID().toString() }
        val recipeId = page.recipeId
        val preset = page.preset
        val draft = when {
            recipeId != null -> RecipeDraft.from(repo.get(recipeId) ?: return null, newId)
            preset != null -> RecipeDraft.fromImport(preset, newId)
            else -> RecipeDraft.blank(newId)
        }
        return EditorState(draft).also { editors[page] = it }
    }

    /**
     * Saves an editor's draft (check its errors first): a new recipe or an
     * update with a fresh `updatedAt`. Closes the editor onto the recipe's
     * detail page (the one underneath, or a new one).
     */
    fun saveEditor(page: EditorPage, draft: RecipeDraft) {
        // Already saved or closed (a second tap on Save before the page left).
        if (stack.none { it === page }) return
        val original = draft.recipeId?.let { repo.get(it) }
        val recipe = draft.toRecipe(now = System.currentTimeMillis(), original = original) { UUID.randomUUID().toString() }
        repo.upsert(recipe)
        // A reviewed paste-text import is done with: clear its draft text.
        val preset = page.preset
        if (preset != null && importText.isNotBlank() && TextRecipeParser.parse(importText) == preset) importText = ""
        closeEditor(page)
        val top = stack.lastOrNull()
        if (!(top is DetailPage && top.recipeId == recipe.id)) openRecipe(recipe.id)
        showToast(if (original != null) "Changes saved" else "Saved “${recipe.title}”", Ico.Check)
    }

    /** Leaves the editor without saving (Cancel, or Discard changes). */
    fun closeEditor(page: EditorPage) {
        editors.remove(page)
        val i = stack.indexOfLast { it === page }
        if (i >= 0) stack.removeAt(i)
    }
}
