package com.blackchapel.recipai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackchapel.recipai.data.DataStoreSettings
import com.blackchapel.recipai.platform.storage.RecipeRuntime
import com.blackchapel.recipai.ui.AppController

/**
 * Owns the app state so it survives rotation and other configuration changes.
 * The recipes are not owned here: the repository is the process-wide one
 * ([RecipeRuntime]), so a second ViewModel in the same process (the activity
 * finished and opened again, or a second task) never has its own copy of
 * `recipes.json` to overwrite the first one's saves with.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = AppController(
        repo = RecipeRuntime.repo(application),
        settingsStore = DataStoreSettings(application),
        scope = viewModelScope,
    )

    init {
        // Google Drive backup: the process-wide runner the background worker shares.
        app.backup.attach(com.blackchapel.recipai.platform.backup.BackupRuntime.jobs(application))
    }
}
