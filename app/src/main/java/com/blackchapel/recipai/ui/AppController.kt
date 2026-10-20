package com.blackchapel.recipai.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackchapel.recipai.core.Links
import com.blackchapel.recipai.core.ParsedRecipe
import com.blackchapel.recipai.core.PhotoImport
import com.blackchapel.recipai.core.PhotoStore
import com.blackchapel.recipai.core.Photos
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.coverPhoto
import com.blackchapel.recipai.core.photoSet
import com.blackchapel.recipai.core.withPhotos
import com.blackchapel.recipai.core.RecipeDraft
import com.blackchapel.recipai.core.SortOrder
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.core.TextRecipeParser
import com.blackchapel.recipai.core.toRecipe
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.data.SettingsStore
import com.blackchapel.recipai.core.UpdateResult
import com.blackchapel.recipai.platform.net.PageFetcher
import com.blackchapel.recipai.platform.net.UpdateFetcher
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.importer.LinkImport
import com.blackchapel.recipai.ui.timers.TimerStore
import com.blackchapel.recipai.ui.editor.EditorState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
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

    /**
     * Where the page was scrolled to (first visible item and its offset in
     * px), saved when the screen leaves the composition and restored when it
     * returns. Cook mode and the editor cover this page, which drops its
     * screen from the composition, and it must come back where it was. Plain
     * numbers, not a LazyListState, so this long-lived object doesn't hold
     * on to a dead layout (and an old activity) after a rotation.
     */
    var scrollIndex = 0
    var scrollOffset = 0

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

/** A newer release than the installed one: [name] is its number, [url] its page on GitHub. */
data class AvailableUpdate(val name: String, val url: String)

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

    /**
     * True while back has something to do in the app: a page to pop, or
     * (on Favorites, Search or Settings) the Recipes tab to return to. On
     * Recipes with nothing pushed it is false, so the system back exits.
     */
    val canGoBack: Boolean get() = stack.isNotEmpty() || tab != Tab.RECIPES

    /**
     * Back: closes the sheet, else pops a page, else (from another tab)
     * returns to Recipes, the home tab. False when there was nothing to
     * do, i.e. on Recipes: the system then exits the app.
     */
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
        if (tab != Tab.RECIPES) {
            tab = Tab.RECIPES
            return true
        }
        return false
    }

    /**
     * Shows Recipes filtered by [filter] (from a tag on detail or a Search
     * tile). With an editor open (a shared import saved over it) it only
     * says so: leaving would drop the editor's unsaved draft.
     */
    fun showTag(filter: TagFilter) {
        if (stack.any { it is EditorPage }) {
            showToast("Save or discard your draft first.", Ico.Info)
            return
        }
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
        // Every page about the recipe goes: its detail, an editor or cook mode for it.
        stack.removeAll {
            (it is DetailPage && it.recipeId == id) ||
                (it is EditorPage && it.recipeId == id) ||
                (it is CookPage && it.recipeId == id)
        }
        showToast("Deleted “${gone.title}”", Ico.Trash, "Undo") {
            repo.restore(index, gone)
            showToast("Restored “${gone.title}”", Ico.Check)
        }
    }

    fun saveImport(parsed: ParsedRecipe) {
        val recipe = parsed.toRecipe(now = System.currentTimeMillis()) { UUID.randomUUID().toString() }
        repo.upsert(recipe)
        importText = ""
        sheet = null
        showSavedImport(recipe)
    }

    /**
     * Opens a just-saved import: Recipes with its detail page. An open
     * editor is kept, with the detail page pushed over it, so its unsaved
     * draft isn't lost and Back returns to it.
     */
    private fun showSavedImport(recipe: Recipe) {
        if (stack.none { it is EditorPage }) {
            stack.clear()
            tab = Tab.RECIPES
        }
        openRecipe(recipe.id)
        showToast("Saved “${recipe.title}”", Ico.Check)
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
        // Back to the tabs, unless an editor is open: its unsaved draft would
        // be lost without asking. The import sheet then opens over it.
        if (stack.none { it is EditorPage }) stack.clear()
        val url = Links.findUrl(t)
        if (url != null && t.length < url.length + 120) {
            shareLink(url)
        } else {
            importText = t
            sheet = Sheet.Import(ImportTab.TEXT)
        }
    }

    // ---- Module additions go below, one section per module, so merges stay simple.

    // ---- link-import ----

    /** The import sheet's "From a link" conversion; lives here so it survives rotation. */
    val linkImport = LinkImport(scope, fetch = { PageFetcher.fetch(it) })

    /** Converts the link in [importUrl]; the field then shows the cleaned-up link. */
    fun convertLink() {
        linkImport.convert(importUrl)?.let { importUrl = it }
    }

    /** A link shared from another app: open "From a link" and convert it straight away. */
    fun shareLink(url: String) {
        importUrl = url
        sheet = Sheet.Import(ImportTab.LINK)
        linkImport.convert(url, fromShare = true)?.let { importUrl = it }
        linkImport.shareHapticPending = true
    }

    /**
     * Saves a converted link (its source comes along in [ParsedRecipe.source])
     * and clears the link tab. Unlike [saveImport] it leaves a pasted-text
     * draft alone.
     */
    fun saveLinkImport(parsed: ParsedRecipe) {
        val recipe = parsed.toRecipe(now = System.currentTimeMillis()) { UUID.randomUUID().toString() }
        repo.upsert(recipe)
        linkImport.reset()
        importUrl = ""
        sheet = null
        showSavedImport(recipe)
    }

    // ---- timers-cook ----

    /**
     * Step timers. Process-wide, not per controller: the timers foreground
     * service keeps them counting after the activity (and this ViewModel)
     * is gone, and a new activity picks the same timers up.
     */
    val timers: TimerStore get() = TimerStore.shared

    // ---- photos ----

    /** A recipe waiting for the photo picker (More menu or the Add photo tile); `PhotoHost` opens it. */
    var photoPickFor by mutableStateOf<String?>(null)
        private set

    /** Photos being copied in: the recipe id and how many (placeholder thumbnails). */
    var photosAdding by mutableStateOf<Pair<String, Int>?>(null)

    /** The photo each open detail page's hero shows when it isn't the cover. */
    private val heroPhotos = mutableStateMapOf<DetailPage, String>()

    /** Detail pages whose hero already played its entrance (once per visit). */
    private val heroEntered = mutableSetOf<DetailPage>()

    /** Scope for copying picked photos, so a rotation mid-copy doesn't drop them. */
    val photoScope: CoroutineScope get() = scope

    private fun openDetailPage(recipeId: String): DetailPage? =
        stack.lastOrNull { it is DetailPage && it.recipeId == recipeId } as DetailPage?

    /** Add photos to a saved recipe (detail page's Add photo tile and More menu). */
    fun addPhoto(recipeId: String) {
        sheet = null
        photoPickFor = recipeId
    }

    /** `PhotoHost` took the request in [photoPickFor]. */
    fun photoPickTaken() {
        photoPickFor = null
    }

    /** Picked photos are stored: attach them to the recipe and show the last one. */
    fun photosPicked(recipeId: String, result: PhotoImport) {
        if (photosAdding?.first == recipeId) photosAdding = null
        val r = repo.get(recipeId)
        if (r == null) {
            // Deleted while the photos were copied; the files are cleaned up on a later start.
            if (result.added > 0) showToast("Recipai didn't add the photos because the recipe no longer exists.", Ico.Info)
            return
        }
        if (result.added > 0) {
            repo.upsert(r.withPhotos(Photos.add(r.photoSet, result.names)).copy(updatedAt = System.currentTimeMillis()))
            showHeroPhoto(recipeId, result.names.last())
        }
        val added = result.added
        val failed = result.failed
        val noun = if (added == 1) "photo" else "photos"
        when {
            added > 0 && failed == 0 -> showToast("Added $added $noun", Ico.Camera)
            added > 0 -> showToast("Added $added $noun; $failed couldn't be read", Ico.Camera)
            failed == 1 -> showToast("Couldn't read that photo. Try a JPG or PNG.", Ico.Info)
            failed > 1 -> showToast("Couldn't read those $failed photos. Try JPGs or PNGs.", Ico.Info)
        }
    }

    /** The photo the detail hero shows: the one picked from the strip, else the cover. */
    fun heroPhoto(recipe: Recipe): String? {
        val picked = openDetailPage(recipe.id)?.let { heroPhotos[it] }
        return picked?.takeIf { it in recipe.photos } ?: recipe.coverPhoto
    }

    /** Shows [name] in the open detail page's hero (a thumbnail tap). */
    fun showHeroPhoto(recipeId: String, name: String) {
        val page = openDetailPage(recipeId) ?: return
        heroPhotos.keys.filter { it !in stack }.forEach { heroPhotos.remove(it) }
        heroPhotos[page] = name
    }

    /**
     * True the first time the open detail page for [recipeId] shows its
     * hero, so the entrance animation plays once per visit, not again after
     * scrolling back up or returning from the editor.
     */
    fun claimHeroEntrance(recipeId: String): Boolean {
        val page = openDetailPage(recipeId) ?: return false
        heroEntered.retainAll { it in stack }
        return heroEntered.add(page)
    }

    /**
     * Once per process (not per controller: a second ViewModel must not
     * clean up behind the first one's open editors), after the recipes
     * loaded: delete photo files no recipe uses (removed in the editor,
     * deleted recipes, cancelled edits). Skipped when the recipe file
     * couldn't be read, so its photos are never lost.
     */
    fun cleanUpPhotos(store: PhotoStore) {
        if (!repo.loaded.value || repo.loadError.value != null || !repo.claimPhotoCleanUp()) return
        val referenced = repo.recipes.value.flatMapTo(HashSet()) { it.photos }
        scope.launch(Dispatchers.IO) {
            try {
                store.cleanUp(referenced)
            } catch (e: Exception) {
                // Best effort: whatever is left is tried again next launch.
            }
        }
    }

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
        // A reviewed link import is done with too: clear the link tab, as saveLinkImport does.
        if (preset?.source != null && linkImport.result == preset) {
            linkImport.reset()
            importUrl = ""
        }
        closeEditor(page)
        val top = stack.lastOrNull()
        if (!(top is DetailPage && top.recipeId == recipe.id)) openRecipe(recipe.id)
        showToast(if (original != null) "Saved your changes" else "Saved “${recipe.title}”", Ico.Check)
    }

    /** Leaves the editor without saving (Cancel, or Discard changes). */
    fun closeEditor(page: EditorPage) {
        editors.remove(page)
        val i = stack.indexOfLast { it === page }
        if (i >= 0) stack.removeAt(i)
    }

    // ---- backup ----

    /** Google Drive backup (ui/backup): the Backup page's state and actions. */
    val backup = com.blackchapel.recipai.ui.backup.BackupController(this, scope)

    /** Settings and Search "Back up now": backs up, or opens the Backup page to connect first. */
    fun backUpNow() = backup.backUpNow()

    // ---- welcome ----

    /** True while the welcome tour is on screen (kept here, so rotation keeps it). */
    var showWelcome by mutableStateOf(false)
        private set

    /**
     * Decides, once, whether this phone gets the welcome tour: only a
     * brand-new install does ([freshInstall], see InstallInfo), never an
     * update. The answer is stored as "seen" at once, whether or not the tour
     * is shown: it appears the one time and never comes back by itself, even
     * if the app is closed or updated halfway through it. (A phone whose
     * stored answer is the earlier "show it" is marked seen too, without
     * showing it.) Replay is Settings > About > Welcome tour.
     */
    suspend fun decideWelcome(freshInstall: Boolean) {
        settingsStore.update {
            if (it.welcomeSeen == null) showWelcome = freshInstall
            if (it.welcomeSeen == true) it else it.copy(welcomeSeen = true)
        }
    }

    /** The tour was finished or skipped. */
    fun finishWelcome() {
        showWelcome = false
    }

    /** Settings > About > Welcome tour. */
    fun replayWelcome() {
        showWelcome = true
    }

    // ---- updates ----

    /** Set when the latest GitHub release is newer than this build; Settings shows a card for it. */
    var availableUpdate by mutableStateOf<AvailableUpdate?>(null)
        private set

    private var updateJob: Job? = null
    private var lastUpdateCheck = 0L

    /**
     * The background update check, run each time the app is opened. Silent:
     * a failure, no release, or an up-to-date build changes nothing on
     * screen. At most one at a time, and not again within [UPDATE_CHECK_GAP_MS]
     * (GitHub allows 60 anonymous requests an hour).
     */
    fun checkForUpdate(installedVersionName: String, now: Long = System.currentTimeMillis()) {
        if (updateJob?.isActive == true || now - lastUpdateCheck < UPDATE_CHECK_GAP_MS) return
        lastUpdateCheck = now
        updateJob = scope.launch {
            val result = try {
                UpdateFetcher.check(installedVersionName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            when (result) {
                is UpdateResult.Available -> availableUpdate = AvailableUpdate(result.name, result.url)
                UpdateResult.UpToDate -> availableUpdate = null
                is UpdateResult.Failed -> Unit // keep what is shown
            }
        }
    }

    private companion object {
        const val UPDATE_CHECK_GAP_MS = 10 * 60 * 1000L
    }
}
