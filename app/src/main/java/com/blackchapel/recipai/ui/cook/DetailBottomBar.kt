package com.blackchapel.recipai.ui.cook

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.DetailPage

/** Height the detail list keeps clear for [DetailBottomBar] (above the nav bar inset). */
val DetailBottomBarHeight = 0.dp

/** STUB (cook module): the detail page's sticky bar: timers button and Start cooking. */
@Composable
fun DetailBottomBar(app: AppController, recipe: Recipe, page: DetailPage, modifier: Modifier = Modifier) {
}
