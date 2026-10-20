package com.blackchapel.recipai.platform.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * One stored photo ([name] from `Recipe.photos`), loaded off the main
 * thread by Coil at the size it is shown. [onResult] reports whether it
 * loaded (false for a missing or unreadable file, so the caller can fall
 * back to the illustration).
 */
@Composable
fun PhotoImage(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
    onResult: (loaded: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val store = rememberPhotoStore()
    val request = remember(store, name, crossfade) {
        // A name this app never writes resolves to null data: Coil reports
        // an error and the caller shows the illustration.
        ImageRequest.Builder(context).data(store.file(name)).crossfade(crossfade).build()
    }
    val report by rememberUpdatedState(onResult)
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = { report(true) },
        onError = { report(false) },
    )
}
