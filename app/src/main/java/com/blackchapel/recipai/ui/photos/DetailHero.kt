package com.blackchapel.recipai.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.components.TimeBadge
import com.blackchapel.recipai.ui.theme.LocalColors

val HeroHeight = 340.dp

/**
 * The detail page's full-bleed image (cover photo, else the illustration)
 * with parallax, and the top lip of the content sheet. First item of the
 * detail page's list; [state] is that list's state.
 */
@Composable
fun DetailHero(app: AppController, recipe: Recipe, state: LazyListState) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().height(HeroHeight).clipToBounds()) {
        RecipeArt(
            recipe,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Parallax: the art moves at about half the scroll speed.
                    val y = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset else 0
                    translationY = y * 0.45f
                },
        )
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        TimeBadge("Illustration", Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 44.dp), icon = Ico.Spark)
        // The top of the content sheet, overlapping the art.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .background(c.canvas, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.padding(top = 8.dp).width(36.dp).height(5.dp).background(c.lineStrong, CircleShape))
        }
    }
}
