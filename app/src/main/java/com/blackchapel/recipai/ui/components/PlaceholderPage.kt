package com.blackchapel.recipai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/** Stand-in for a page a module has not built yet. Delete once unused. */
@Composable
fun PlaceholderPage(app: AppController, title: String) {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize().background(c.canvas), contentAlignment = Alignment.Center) {
        Text("$title is not built yet", style = RecipaiType.ui, color = c.mute)
        TopBar(title = title, scrolled = true, collapsed = true, left = {
            IconButton(Ico.Left, "Back", { app.back() }, tint = c.accentInk, iconSize = 24.dp)
        })
    }
}


