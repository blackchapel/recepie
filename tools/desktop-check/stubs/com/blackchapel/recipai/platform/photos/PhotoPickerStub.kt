package com.blackchapel.recipai.platform.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.blackchapel.recipai.core.PhotoImport
import kotlinx.coroutines.CoroutineScope

@Stable
class PhotoPicker(private val open: () -> Boolean) {
    fun launch(): Boolean = open()
}

@Composable
fun rememberPhotoPicker(
    scope: CoroutineScope? = null,
    onStart: (count: Int) -> Unit = {},
    onDone: (PhotoImport) -> Unit,
): PhotoPicker = remember { PhotoPicker { false } }
