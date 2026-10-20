package com.blackchapel.recipai.platform.storage

import android.content.Context
import com.blackchapel.recipai.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The one [RecipeRepository] of the process, like `BackupRuntime.jobs`.
 *
 * The repository keeps the whole recipe list in memory and rewrites
 * `recipes.json` from it, so two repositories over the same file would
 * overwrite each other's saves with whichever list is older. A ViewModel
 * is not a safe owner: a second activity (a share into a new task, a task
 * re-created after the first was finished) gets a second ViewModel while
 * the process lives on. Every ViewModel therefore asks here, and its
 * controller is only a view over the shared repository.
 *
 * The repository's scope lives as long as the process, not a ViewModel, so
 * a save still queued when the activity goes away is written. Tests don't
 * use this holder: they construct [RecipeRepository] directly with a file
 * and a test scope.
 */
object RecipeRuntime {
    /** The recipe file's name in `filesDir` (the backup worker reads it too). */
    const val FILE_NAME = "recipes.json"

    @Volatile
    private var instance: RecipeRepository? = null

    fun repo(context: Context): RecipeRepository =
        instance ?: synchronized(this) {
            instance ?: RecipeRepository(
                file = File(context.applicationContext.filesDir, FILE_NAME),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                io = Dispatchers.IO,
            ).also { instance = it }
        }
}
