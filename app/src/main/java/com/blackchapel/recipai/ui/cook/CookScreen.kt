package com.blackchapel.recipai.ui.cook

import androidx.compose.runtime.Composable
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.CookPage
import com.blackchapel.recipai.ui.components.PlaceholderPage

/** STUB (cook module): full-screen cook mode. */
@Composable
fun CookScreen(app: AppController, page: CookPage, recipe: Recipe?) {
    PlaceholderPage(app, "Cook mode")
}
