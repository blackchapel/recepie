package com.blackchapel.recipai.data

import com.blackchapel.recipai.core.Durable
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.RecipeJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * All recipes, kept in memory and saved as one JSON file ([RecipeJson]) in
 * app-private storage. The first launch (no file yet) starts with the
 * example recipes.
 */
class RecipeRepository(
    private val file: File,
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // One writer at a time, and each write saves the latest list, so the
    // file always ends up matching memory whatever order writes run in.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writer = io.limitedParallelism(1)

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Set when the saved file could not be read (a copy is kept beside it). */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** Set while the last save failed; cleared by the next successful one. */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    init {
        scope.launch {
            val saved = withContext(writer) { readFile() }
            val cleaned = saved?.let(::withoutExamples)
            _recipes.value = cleaned ?: emptyList()
            _loaded.value = true
            // Earlier builds seeded 8 example recipes; their removal is saved.
            if (saved != null && cleaned != saved) save()
        }
    }

    fun get(id: String): Recipe? = _recipes.value.firstOrNull { it.id == id }

    private val photoCleanUpClaimed = AtomicBoolean(false)

    /**
     * True for the first caller only, once per repository (so once per
     * process, as the repository is shared): the launch-time photo cleanup
     * must not run again for a second ViewModel, whose view of the open
     * editors' photos is empty.
     */
    fun claimPhotoCleanUp(): Boolean = photoCleanUpClaimed.compareAndSet(false, true)

    /** Adds [recipe] at the top, or replaces the recipe with the same id. */
    fun upsert(recipe: Recipe) = mutate { list ->
        val i = list.indexOfFirst { it.id == recipe.id }
        if (i < 0) listOf(recipe) + list else list.toMutableList().also { it[i] = recipe }
    }

    fun toggleFavorite(id: String): Boolean? {
        var now: Boolean? = null
        mutate { list ->
            list.map { r -> if (r.id == id) r.copy(favorite = !r.favorite).also { now = it.favorite } else r }
        }
        return now
    }

    /** Removes a recipe; returns it with its position so it can be undone. */
    fun delete(id: String): Pair<Int, Recipe>? {
        val list = _recipes.value
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        val gone = list[i]
        mutate { it.filterNot { r -> r.id == id } }
        return i to gone
    }

    fun restore(index: Int, recipe: Recipe) = mutate { list ->
        if (list.any { it.id == recipe.id }) list
        else list.toMutableList().also { it.add(index.coerceIn(0, it.size), recipe) }
    }

    /** Replaces every recipe at once (restoring a backup), in one save. */
    fun replaceAll(recipes: List<Recipe>) = mutate { recipes }

    /**
     * The app no longer ships example recipes. Files written by earlier
     * builds still hold them (flagged `isSample`): drop those, except any
     * the user added photos to, which become ordinary recipes. Edited
     * examples were already unflagged by the editor.
     */
    private fun withoutExamples(list: List<Recipe>): List<Recipe> =
        list.filter { !it.isSample || it.photos.isNotEmpty() }
            .map { if (it.isSample) it.copy(isSample = false) else it }

    private fun mutate(transform: (List<Recipe>) -> List<Recipe>) {
        _recipes.update(transform)
        save()
    }

    private fun save() {
        scope.launch(writer) { writeFile(_recipes.value) }
    }

    private fun readFile(): List<Recipe>? {
        if (!file.exists()) return null
        return try {
            RecipeJson.decode(file.readText())
        } catch (e: Exception) {
            // Never overwrite what we could not read: keep it for recovery.
            val kept = File(file.parentFile, "${file.name}.unreadable-${clock()}")
            file.renameTo(kept)
            _loadError.value = "Couldn't read your saved recipes. Recipai kept a copy of the file and started with an empty library."
            null
        }
    }

    private fun writeFile(list: List<Recipe>) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            // Synced before the rename: a power loss must not leave the new name on empty data.
            Durable.write(tmp, RecipeJson.encode(list).toByteArray(Charsets.UTF_8))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            _saveError.value = null
        } catch (e: IOException) {
            // Disk full or similar: keep running on what's in memory and say so.
            // The next change tries again.
            _saveError.value = "Couldn't save your recipes (${e.message ?: "storage error"}). Free up some space."
        }
    }
}
