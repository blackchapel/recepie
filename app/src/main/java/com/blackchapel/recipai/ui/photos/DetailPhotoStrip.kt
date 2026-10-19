package com.blackchapel.recipai.ui.photos

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

private val ThumbSize = 70.dp
private val ThumbShape = RoundedCornerShape(14.dp)

/**
 * The detail page's photo strip: a thumbnail per photo (the one in the
 * hero outlined in saffron; tap another to show it there, tap the shown
 * one to open the viewer), a spinner tile per photo still being copied
 * in, and an Add photo tile. Spans the page width with the 18 dp gutter.
 */
@Composable
fun DetailPhotoStrip(app: AppController, recipe: Recipe) {
    val haptics = LocalHaptics.current
    val shown = app.heroPhoto(recipe)
    val adding = app.photosAdding?.takeIf { it.first == recipe.id }?.second ?: 0
    // Thumbnails that were already here pop in only when newly added.
    val initial = remember(recipe.id) { recipe.photos.toSet() }
    Row(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        recipe.photos.forEachIndexed { i, name ->
            key(name) {
                val selected = name == shown
                val pop = rememberPop(play = name !in initial)
                Box(
                    Modifier
                        .pop(pop)
                        .tap(
                            pressedScale = 0.94f,
                            haptic = null,
                            label = if (selected) "View photo ${i + 1} larger" else "Show photo ${i + 1}",
                        ) {
                            if (selected) {
                                haptics.perform(HapticType.LIGHT)
                                app.sheet = Sheet.PhotoViewer(recipe.id, i)
                            } else {
                                haptics.perform(HapticType.SELECTION)
                                app.showHeroPhoto(recipe.id, name)
                            }
                        }
                        .size(ThumbSize),
                ) {
                    PhotoFill(name, recipe, ThumbShape, Modifier.matchParentSize(), description = "Photo ${i + 1}")
                    Box(
                        Modifier
                            .matchParentSize()
                            .border(2.dp, if (selected) LocalColors.current.saffron else Color.Transparent, ThumbShape),
                    )
                }
            }
        }
        repeat(adding) { PendingPhotoTile(ThumbSize, ThumbShape) }
        AddPhotoThumb { app.addPhoto(recipe.id) }
    }
}

@Composable
private fun AddPhotoThumb(onClick: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier
            .tap(pressedScale = 0.94f, haptic = HapticType.LIGHT, label = "Add photo", onClick = onClick)
            .size(ThumbSize)
            .dashedBorder(1.5.dp, c.lineStrong, 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        RIcon(Ico.Camera, size = 20.dp, tint = c.mute)
        Text("Add photo", style = RecipaiType.tab, color = c.mute, maxLines = 1)
    }
}
