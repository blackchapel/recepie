package com.blackchapel.recipai.ui.photos

import androidx.compose.runtime.Composable
import com.blackchapel.recipai.core.Recipe

/**
 * STUB (photos module): the editor's photo strip: add photos, set the
 * cover, remove; shows the generated illustration until a photo exists.
 * [draft] is the recipe being edited (for the illustration); [onChange]
 * reports the new photo list and cover index.
 */
@Composable
fun EditorPhotoStrip(draft: Recipe, onChange: (photos: List<String>, coverIndex: Int) -> Unit) {
}
