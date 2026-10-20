package com.blackchapel.recipai.platform.photos

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.blackchapel.recipai.core.PhotoStore

/** The app's photo folder, `filesDir/photos` (also for the backup module). */
fun photoStore(context: Context): PhotoStore = PhotoStore.inFilesDir(context.applicationContext.filesDir)

/** [photoStore] for composables. */
@Composable
fun rememberPhotoStore(): PhotoStore {
    val context = LocalContext.current
    return remember(context.applicationContext) { photoStore(context) }
}
