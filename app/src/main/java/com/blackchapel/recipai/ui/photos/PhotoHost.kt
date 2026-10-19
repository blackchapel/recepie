package com.blackchapel.recipai.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.photos.rememberPhotoPicker
import com.blackchapel.recipai.platform.photos.rememberPhotoStore
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Ico

/**
 * Always composed at the app root (draws nothing): runs the photo picker
 * for "Add photo" on a saved recipe ([AppController.addPhoto], from the
 * detail strip or the More menu) and cleans up unused photo files once
 * the recipes have loaded. Lives at the root so the picker result arrives
 * even when the detail page's strip has scrolled out of view or the
 * activity was recreated while the picker was open.
 */
@Composable
fun PhotoHost(app: AppController) {
    val haptics = LocalHaptics.current
    val store = rememberPhotoStore()
    val loaded by app.repo.loaded.collectAsState()
    LaunchedEffect(loaded) { if (loaded) app.cleanUpPhotos(store) }

    // The recipe the open picker adds to; saved so it survives the activity
    // being recreated behind the picker.
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberPhotoPicker(
        scope = app.photoScope,
        onStart = { n -> target?.let { app.photosAdding = it to n } },
        onDone = { result ->
            val id = target
            if (id != null) {
                haptics.perform(if (result.added > 0) HapticType.SUCCESS else HapticType.ERROR)
                app.photosPicked(id, result)
            }
        },
    )
    val request = app.photoPickFor
    LaunchedEffect(request) {
        if (request != null) {
            app.photoPickTaken()
            target = request
            if (!picker.launch()) app.showToast("Couldn't open the photo picker on this phone", Ico.Info)
        }
    }
}
