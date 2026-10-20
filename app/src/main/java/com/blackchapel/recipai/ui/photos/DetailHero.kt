package com.blackchapel.recipai.ui.photos

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.platform.photos.PhotoImage
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.components.TimeBadge
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import kotlinx.coroutines.launch

val HeroHeight = 340.dp

/** The prototype's --ease-out, cubic-bezier(.22, 1, .36, 1). */
internal val PhotoEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Both entrances grow from the top edge, like the prototype's `transform-origin: center top`. */
private val TopCenter = TransformOrigin(0.5f, 0f)

/**
 * The detail page's full-bleed image with parallax, and the top lip of the
 * content sheet. First item of the detail page's list; [state] is that
 * list's state.
 *
 * Shows the photo picked in the strip (else the cover), or the generated
 * illustration when there are no photos, with a badge saying which. On
 * the first view of a page it plays an entrance: the illustration turns
 * and settles onto the table (the prototype's `plate-in`: from -14 degrees
 * and 1.15x to rest over 1.4 s), a photo eases in from 1.1x. Tap to open
 * the photo viewer.
 */
@Composable
fun DetailHero(app: AppController, recipe: Recipe, state: LazyListState) {
    val c = LocalColors.current
    // Once per visit: not again after scrolling back up or closing the editor.
    val enter = remember { app.claimHeroEntrance(recipe.id) }
    val photos = recipe.photos
    val shown = app.heroPhoto(recipe)
    val index = shown?.let { photos.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    // Parallax: the picture moves at about half the scroll speed. Read in
    // the layer only, so scrolling never recomposes.
    val parallax: () -> Float = {
        if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset * 0.45f else 0f
    }
    Box(Modifier.fillMaxWidth().height(HeroHeight).clipToBounds().background(c.surface2)) {
        Box(
            Modifier
                .fillMaxSize()
                .tap(pressedScale = 1f, haptic = HapticType.LIGHT, label = "View larger image") {
                    app.sheet = Sheet.PhotoViewer(recipe.id, index)
                },
        ) {
            if (shown == null) HeroArt(recipe, enter, parallax) else HeroPhotos(recipe, shown, enter, parallax)
        }
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        val badge = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 44.dp)
        when {
            shown == null -> TimeBadge("Illustration", badge, icon = Ico.Spark)
            photos.size > 1 -> TimeBadge("${index + 1} of ${photos.size}", badge, icon = Ico.Image)
            else -> TimeBadge("1 photo", badge, icon = Ico.Image)
        }
        // The top of the content sheet, overlapping the picture.
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

/** The illustration, with the `plate-in` entrance when [enter]. */
@Composable
private fun HeroArt(recipe: Recipe, enter: Boolean, parallax: () -> Float) {
    val progress = remember { Animatable(if (enter) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1400, easing = PhotoEaseOut)) }
    RecipeArt(
        recipe,
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                val s = 1.15f - 0.15f * p
                transformOrigin = TopCenter
                rotationZ = -14f * (1f - p)
                scaleX = s
                scaleY = s
                translationY = parallax()
            },
    )
}

/** The shown photo; picking another thumbnail crossfades to it (the prototype's 380 ms fade). */
@Composable
private fun HeroPhotos(recipe: Recipe, shown: String, enter: Boolean, parallax: () -> Float) {
    val first = remember { shown }
    Crossfade(shown, animationSpec = tween(380), label = "hero-photo") { name ->
        HeroPhoto(recipe, name, animateIn = enter && name == first, parallax = parallax)
    }
}

/** One photo; eases in from 1.1x once loaded when [animateIn]. A missing file shows the illustration. */
@Composable
private fun HeroPhoto(recipe: Recipe, name: String, animateIn: Boolean, parallax: () -> Float) {
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        RecipeArt(recipe, Modifier.fillMaxSize().graphicsLayer { translationY = parallax() })
        return
    }
    var loaded by remember { mutableStateOf(false) }
    val scale = remember { Animatable(if (animateIn) 1.1f else 1f) }
    val fade = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(loaded) {
        if (loaded) {
            launch { fade.animateTo(1f, tween(350)) }
            scale.animateTo(1f, tween(1100, easing = PhotoEaseOut))
        }
    }
    PhotoImage(
        name,
        contentDescription = recipe.title,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TopCenter
                scaleX = scale.value
                scaleY = scale.value
                alpha = fade.value
                translationY = parallax()
            },
        crossfade = false,
        onResult = { ok -> if (ok) loaded = true else failed = true },
    )
}
