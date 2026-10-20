package com.blackchapel.recipai.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Width of one row action (the prototype's 84px buttons). */
private val ActionWidth = 84.dp

/** A fling faster than this opens or closes the row whatever its position. */
private val FlingSpeed = 500.dp

/**
 * The geometry of a row swipe, in pixels; offsets are negative (to the
 * left). Pure, so it is unit-tested.
 */
internal object RowSwipe {
    /** Past fully open the row still follows the finger, at 30% (rubber band). */
    fun shown(raw: Float, openPx: Float): Float =
        if (raw < -openPx) -openPx + (raw + openPx) * 0.3f else raw.coerceAtMost(0f)

    /** The action point: half way, one action's width. */
    fun pastActionPoint(shown: Float, actionPx: Float): Boolean = shown < -actionPx

    /** Where a released row settles: open past the action point or on a left fling, closed otherwise. */
    fun settlesOpen(shown: Float, velocity: Float, actionPx: Float, flingPx: Float): Boolean = when {
        velocity <= -flingPx -> true
        velocity >= flingPx -> false
        else -> pastActionPoint(shown, actionPx)
    }
}

private val Settle = spring<Float>(dampingRatio = 0.86f, stiffness = 500f)

/**
 * A list row that swipes left to reveal Favorite and Delete (the
 * prototype's row actions). Passing the action point plays a selection
 * tick; release past it (or fling left) and the row stays open, otherwise
 * it springs back. [open] is owned by the list so only one row is open at
 * a time; [onDragStart] lets the list close the others.
 */
@Composable
internal fun SwipeActions(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val actionPx = with(density) { ActionWidth.toPx() }
    val openPx = 2 * actionPx
    val flingPx = with(density) { FlingSpeed.toPx() }
    val scope = rememberCoroutineScope()
    // The raw drag position (<= 0); drawn through RowSwipe.shown.
    val offset = remember { Animatable(0f) }
    val gesture = remember { SwipeGesture() }

    fun settle(target: Float, velocity: Float = 0f) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offset.snapTo(RowSwipe.shown(offset.value, openPx))
            offset.animateTo(target, Settle, initialVelocity = velocity)
        }
    }

    // Opened or closed from outside (another row, a scroll, a tap).
    LaunchedEffect(open) {
        val target = if (open) -openPx else 0f
        if (!gesture.dragging && offset.targetValue != target) offset.animateTo(target, Settle)
    }

    val drag = rememberDraggableState { delta ->
        val next = (offset.value + delta).coerceAtMost(0f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(next) }
        val past = RowSwipe.pastActionPoint(RowSwipe.shown(next, openPx), actionPx)
        if (past != gesture.past) {
            gesture.past = past
            haptics.perform(HapticType.SELECTION)
        }
    }

    Box(
        modifier.draggable(
            state = drag,
            orientation = Orientation.Horizontal,
            // In right-to-left layouts the actions sit on the left: swipe right.
            reverseDirection = rtl,
            onDragStarted = {
                gesture.dragging = true
                gesture.past = RowSwipe.pastActionPoint(RowSwipe.shown(offset.value, openPx), actionPx)
                onDragStart()
            },
            onDragStopped = { velocity ->
                gesture.dragging = false
                val opens = RowSwipe.settlesOpen(RowSwipe.shown(offset.value, openPx), velocity, actionPx, flingPx)
                settle(if (opens) -openPx else 0f, velocity)
                onOpenChange(opens)
            },
        ),
    ) {
        val revealed by remember { derivedStateOf { offset.value < -0.5f } }
        if (revealed) {
            Row(
                Modifier
                    .matchParentSize()
                    // The actions widen with the rubber band so no gap opens up.
                    .layout { measurable, constraints ->
                        val width = max(openPx, -RowSwipe.shown(offset.value, openPx)).roundToInt().coerceAtMost(constraints.maxWidth)
                        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(constraints.maxWidth - width, 0) }
                    },
            ) {
                RowAction(if (favorite) "Unfavorite" else "Favorite", Ico.Heart, c.sumac, Modifier.weight(1f), onFavorite)
                RowAction("Delete", Ico.Trash, c.danger, Modifier.weight(1f), onDelete)
            }
        }
        Box(Modifier.graphicsLayer { translationX = RowSwipe.shown(offset.value, openPx) * if (rtl) -1f else 1f }) {
            content()
        }
    }
}

/** Per-gesture bookkeeping that must not trigger recomposition. */
private class SwipeGesture {
    var dragging = false
    var past = false
}

@Composable
private fun RowAction(label: String, icon: Ico, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .background(color)
            // The action plays its own haptic (favorite / warning).
            .tap(pressedScale = 0.9f, haptic = null, label = label, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RIcon(icon, size = 20.dp, tint = Color.White)
        Text(label, style = RecipaiType.tag, color = Color.White, maxLines = 1)
    }
}
