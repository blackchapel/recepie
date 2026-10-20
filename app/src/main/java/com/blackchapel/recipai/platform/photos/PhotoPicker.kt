package com.blackchapel.recipai.platform.photos

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.blackchapel.recipai.core.PhotoImport
import com.blackchapel.recipai.core.Photos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the system photo picker; see [rememberPhotoPicker]. */
@Stable
class PhotoPicker(private val open: () -> Boolean) {
    /** Shows the picker. False when no picker could be opened. */
    fun launch(): Boolean = open()
}

/**
 * The system photo picker (Android's PickVisualMedia: no storage
 * permission; older phones get the document picker), images only, up to
 * [Photos.MAX_PICK] at a time. Picked images are copied into the photo
 * folder off the main thread: [onStart] gets how many are being copied,
 * [onDone] the stored names and how many failed. Cancelling calls neither.
 *
 * The copy runs in [scope] when given (pass a scope that outlives the
 * screen, so a rotation mid-copy doesn't drop the photos), else in this
 * composition's scope.
 */
@Composable
fun rememberPhotoPicker(
    scope: CoroutineScope? = null,
    onStart: (count: Int) -> Unit = {},
    onDone: (PhotoImport) -> Unit,
): PhotoPicker {
    val context = LocalContext.current.applicationContext
    val store = rememberPhotoStore()
    val localScope = rememberCoroutineScope()
    val start by rememberUpdatedState(onStart)
    val done by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Photos.MAX_PICK),
    ) { uris: List<Uri> ->
        // Empty when the picker was cancelled. The document-picker fallback
        // doesn't enforce the limit, so cap it here.
        val picked = uris.take(Photos.MAX_PICK)
        if (picked.isNotEmpty()) {
            start(picked.size)
            (scope ?: localScope).launch {
                val result = withContext(Dispatchers.IO) { PhotoImporter.importAll(context, store, picked) }
                done(result)
            }
        }
    }
    return remember(launcher) {
        PhotoPicker {
            try {
                launcher.launch(
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build(),
                )
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: IllegalArgumentException) {
                // The system picker refused the request (e.g. its item limit).
                false
            }
        }
    }
}
