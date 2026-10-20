package com.blackchapel.recipai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackchapel.recipai.data.DataStoreSettings
import com.blackchapel.recipai.platform.install.InstallInfo
import com.blackchapel.recipai.platform.storage.RecipeRuntime
import com.blackchapel.recipai.ui.AppController
import kotlinx.coroutines.launch

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
        // The first launch decides whether this phone gets the welcome tour: only a
        // brand-new install does, never an update (see InstallInfo). Decided once;
        // later launches leave the answer as it is.
        val fresh = InstallInfo.isFreshInstall(application, RecipeRuntime.FILE_NAME)
        viewModelScope.launch { app.decideWelcome(fresh) }
        // Google Drive backup: the process-wide runner the background worker shares.
        app.backup.attach(com.blackchapel.recipai.platform.backup.BackupRuntime.jobs(application))
    }
}
