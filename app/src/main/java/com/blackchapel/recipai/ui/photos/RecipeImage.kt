package com.blackchapel.recipai.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.ui.components.RecipeArt

/**
 * A recipe's picture everywhere it appears small (cards, rows, search,
 * sheets): the cover photo once photos exist, else the illustration.
 * The caller sizes and clips it.
 */
@Composable
fun RecipeImage(recipe: Recipe, modifier: Modifier = Modifier) {
    RecipeArt(recipe, modifier)
}
