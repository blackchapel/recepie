package com.blackchapel.recipai.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Sheet

/** STUB (photos module): full-screen photo viewer with swipe between photos. */
@Composable
fun PhotoViewer(app: AppController, sheet: Sheet.PhotoViewer, recipe: Recipe) {
    LaunchedEffect(sheet) { app.sheet = null }
}
