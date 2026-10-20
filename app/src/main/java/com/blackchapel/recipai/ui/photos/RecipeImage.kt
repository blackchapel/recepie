package com.blackchapel.recipai.ui.photos

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.coverPhoto
import com.blackchapel.recipai.platform.photos.PhotoImage
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.theme.LocalColors

/**
 * A recipe's picture everywhere it appears small (cards, rows, search,
 * sheets): the cover photo once photos exist, else the illustration (also
 * when the photo file is missing, e.g. after restoring a backup without
 * photos). The caller sizes and clips it.
 */
@Composable
fun RecipeImage(recipe: Recipe, modifier: Modifier = Modifier) {
    val cover = recipe.coverPhoto
    if (cover == null) {
        RecipeArt(recipe, modifier)
        return
    }
    var failed by remember(cover) { mutableStateOf(false) }
    if (failed) {
        RecipeArt(recipe, modifier)
    } else {
        PhotoImage(
            cover,
            contentDescription = null,
            modifier = modifier.background(LocalColors.current.surface2),
            onResult = { loaded -> if (!loaded) failed = true },
        )
    }
}
