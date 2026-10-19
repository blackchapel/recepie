package com.blackchapel.recipai.ui.timers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.blackchapel.recipai.ui.AppController

/** STUB (timers module): the sheet listing every timer with pause, +1 min, cancel. */
@Composable
fun TimersSheet(app: AppController) {
    LaunchedEffect(Unit) { app.sheet = null }
}
