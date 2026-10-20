package com.blackchapel.recipai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The prototype's --ease-out: a quick start that settles gently. */
val EaseOutSoft = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** The prototype's --ease-nav (iOS-like navigation). */
val EaseNav = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * When a collection last started its entrance: [rememberRiseStart] returns
 * a fresh start time when [key] differs from the key it last animated for,
 * else 0 (no entrance). The last key is saved, so coming back to a tab or
 * rotating does not replay the entrance; only the first appearance (and a
 * new [key], such as another layout or sort order) does.
 */
@Composable
fun rememberRiseStart(key: String): Long {
    var risenFor by rememberSaveable { mutableStateOf<String?>(null) }
    val start = remember(key) { if (risenFor != key) System.nanoTime() else 0L }
    SideEffect { risenFor = key }
    return start
}

/** Items composed this long after the start rise; later ones (scrolled into view) just appear. */
private const val RiseWindowNanos = 450_000_000L

/** Stagger stops growing after this many items, so nothing waits long. */
private const val MaxStaggered = 12

/**
 * The prototype's `rise` entrance: fade in while moving up 16 dp and
 * scaling from 98%, staggered by [index]. Only plays for items composed
 * right after [start] (see [rememberRiseStart]). Honors the system's
 * animation scale (none with "remove animations").
 */
fun Modifier.rise(start: Long, index: Int, staggerMs: Int = 45, durationMs: Int = 600): Modifier = composed {
    val animate = remember(start) { start != 0L && System.nanoTime() - start < RiseWindowNanos }
    val progress = remember(start) { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(start) {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            val wait = (index.coerceIn(0, MaxStaggered) * staggerMs * scale).toLong()
            if (wait > 0) delay(wait)
            progress.animateTo(1f, tween(durationMs, easing = EaseOutSoft))
        }
    }
    this.graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            alpha = p
            translationY = (1f - p) * 16.dp.toPx()
            val s = 0.98f + 0.02f * p
            scaleX = s
            scaleY = s
        }
    }
}
