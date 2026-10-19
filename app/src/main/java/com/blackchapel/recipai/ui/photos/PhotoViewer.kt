package com.blackchapel.recipai.ui.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.photos.PhotoImage
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.SystemBackHandler
import com.blackchapel.recipai.ui.components.GlassButton
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The full-screen photo viewer (the prototype's lightbox): black, the
 * photo fitted to the screen, swipe left/right between photos, tap
 * anywhere or the close button (or back) to close. A recipe without
 * photos shows its illustration. Fades in, the picture growing from
 * 0.94x; fades out on close.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer(app: AppController, sheet: Sheet.PhotoViewer, recipe: Recipe) {
    val haptics = LocalHaptics.current
    val photos = recipe.photos
    val count = if (photos.isEmpty()) 1 else photos.size
    val pager = rememberPagerState(initialPage = sheet.index.coerceIn(0, count - 1)) { count }

    val fade = remember { Animatable(0f) }
    val grow = remember { Animatable(0.94f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { grow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
        fade.animateTo(1f, tween(300))
    }
    LaunchedEffect(closing) {
        if (closing) {
            fade.animateTo(0f, tween(250))
            if (app.sheet == sheet) app.sheet = null
        }
    }
    val close = {
        if (!closing) {
            haptics.perform(HapticType.LIGHT)
            closing = true
        }
    }
    SystemBackHandler(enabled = !closing) { close() }

    // A swipe to another photo ticks, like cook mode's step swipes.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { haptics.perform(HapticType.SELECTION) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { close() } }
            .semantics { contentDescription = "Photo viewer" },
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { photos.getOrNull(it) ?: "illustration" },
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { close() } },
                contentAlignment = Alignment.Center,
            ) {
                val picture = Modifier.graphicsLayer {
                    scaleX = grow.value
                    scaleY = grow.value
                }
                if (photos.isEmpty()) {
                    RecipeArt(recipe, picture.fillMaxWidth().aspectRatio(4f / 3f))
                } else {
                    ViewerPhoto(photos[page], recipe, page, photos.size, picture)
                }
            }
        }
        GlassButton(
            Ico.X,
            "Close",
            { close() },
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp, end = 14.dp),
            haptic = null,
        )
        Text(
            if (count > 1) "${pager.currentPage + 1} of $count · swipe for more" else "Tap anywhere to close",
            style = RecipaiType.mono.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp),
        )
    }
}

/** One photo, fitted within 80% of the height; the illustration if the file is gone. */
@Composable
private fun ViewerPhoto(name: String, recipe: Recipe, index: Int, count: Int, modifier: Modifier) {
    var failed by remember(name) { mutableStateOf(false) }
    if (failed) {
        RecipeArt(recipe, modifier.fillMaxWidth().aspectRatio(4f / 3f))
    } else {
        PhotoImage(
            name,
            contentDescription = if (count > 1) "${recipe.title}, photo ${index + 1} of $count" else recipe.title,
            modifier = modifier.fillMaxWidth().fillMaxHeight(0.8f),
            contentScale = ContentScale.Fit,
            onResult = { ok -> if (!ok) failed = true },
        )
    }
}
