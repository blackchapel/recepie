package com.blackchapel.recipai.ui.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.platform.photos.PhotoImage
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.theme.LocalColors

// Pieces shared by the detail and editor photo strips.

/** A dashed rounded outline (the "Add photo" tiles). */
internal fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = drawBehind {
    val w = width.toPx()
    val r = radius.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r - w / 2, r - w / 2),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
}

/**
 * A stored photo filling its tile; the illustration if the file is
 * missing. [shape] clips it.
 */
@Composable
internal fun PhotoFill(name: String, recipe: Recipe, shape: Shape, modifier: Modifier = Modifier, description: String? = null) {
    val c = LocalColors.current
    var failed by remember(name) { mutableStateOf(false) }
    Box(modifier.clip(shape).background(c.surface2)) {
        if (failed) {
            RecipeArt(recipe, Modifier.matchParentSize())
        } else {
            PhotoImage(
                name,
                contentDescription = description,
                modifier = Modifier.matchParentSize(),
                onResult = { ok -> if (!ok) failed = true },
            )
        }
    }
}

/** A tile for a photo that is still being copied in. */
@Composable
internal fun PendingPhotoTile(size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(modifier.size(size).clip(shape).background(c.surface2), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), color = c.saffron, strokeWidth = 2.dp, trackColor = Color.Transparent)
    }
}

/**
 * The prototype's `pop` for a newly added tile (0.6x, overshoot, rest).
 * Starts at rest unless [play]. Apply it with [pop].
 */
@Composable
internal fun rememberPop(play: Boolean): Animatable<Float, AnimationVector1D> {
    val scale = remember { Animatable(if (play) 0.6f else 1f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    return scale
}

internal fun Modifier.pop(scale: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    val s = scale.value
    scaleX = s
    scaleY = s
    alpha = (0.4f + (s - 0.6f) * 1.5f).coerceIn(0f, 1f)
}
