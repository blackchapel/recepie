package com.blackchapel.recipai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackchapel.recipai.data.DataStoreSettings
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.ui.AppController
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Owns the app state so it survives rotation and other configuration changes. */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = AppController(
        repo = RecipeRepository(File(application.filesDir, "recipes.json"), viewModelScope, Dispatchers.IO),
        settingsStore = DataStoreSettings(application),
        scope = viewModelScope,
    )

    init {
        // Google Drive backup: the process-wide runner the background worker shares.
        app.backup.attach(com.blackchapel.recipai.platform.backup.BackupRuntime.jobs(application))
    }
}
