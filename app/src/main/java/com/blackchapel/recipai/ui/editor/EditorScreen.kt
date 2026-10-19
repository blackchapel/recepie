package com.blackchapel.recipai.ui.editor

import androidx.compose.runtime.Composable
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.EditorPage
import com.blackchapel.recipai.ui.components.PlaceholderPage

/** STUB (editor module): the recipe editor, a modal page. */
@Composable
fun EditorScreen(app: AppController, page: EditorPage, recipes: List<Recipe>) {
    PlaceholderPage(app, if (page.recipeId == null) "New recipe" else "Edit recipe")
}
