package com.blackchapel.recipai.ui.timers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.CookTimer
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.timers.TimerSystem
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.CookPage
import com.blackchapel.recipai.ui.DetailPage
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.Tab
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.TabBarHeight
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The prototype's --ease-nav, for the pill moving with the tab bar. */
private val NavEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * The floating mini-timer pill shown over the tabs and pushed pages while
 * timers exist (not on the detail page, which has its own timers button,
 * nor in cook mode). Tap opens the Timers sheet.
 *
 * Always composed once the library has loaded, so it also runs the timers:
 * the on-screen ticker, the "time's up" haptic and toast, the Timer sound
 * setting, dropping timers of deleted recipes, and the platform side
 * (foreground service, notification permission).
 */
@Composable
fun TimerOverlay(app: AppController, modifier: Modifier = Modifier) {
    val store = app.timers
    val board by store.board.collectAsState()
    val now by store.now.collectAsState()
    val settings by app.settings.collectAsState()
    val recipes by app.repo.recipes.collectAsState()
    val haptics by rememberUpdatedState(LocalHaptics.current)

    TimerSystem(store)
    LaunchedEffect(settings.timerSound) { store.sound = settings.timerSound }
    LaunchedEffect(recipes) { store.retainRecipes(recipes.mapTo(HashSet()) { it.id }) }
    // Live countdowns: tick while anything runs. A change to the board
    // (a timer finishing, pausing, starting) restarts the loop.
    LaunchedEffect(store) {
        store.board.collectLatest { b ->
            while (b.hasRunning) {
                store.tick()
                delay(250)
            }
        }
    }
    LaunchedEffect(store) {
        store.finished.collect { t ->
            haptics.perform(HapticType.WARNING)
            app.showToast("Time’s up: ${t.label}", Ico.Timer)
        }
    }

    val top = app.stack.lastOrNull()
    val pushed = top != null
    val hidden = top is DetailPage || top is CookPage
    val fab = !pushed && (app.tab == Tab.RECIPES || app.tab == Tab.FAVORITES)
    val featured = board.featured(now)
    // Keep drawing the last timer while the pill animates out.
    val shown = remember { arrayOfNulls<CookTimer>(1) }
    if (featured != null) shown[0] = featured
    val bottom by animateDpAsState(if (pushed) PushedPillOffset else TabBarHeight + 12.dp, tween(450, easing = NavEasing), label = "pill")

    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 18.dp, end = 18.dp, bottom = bottom),
    ) {
        AnimatedVisibility(
            visible = featured != null && !hidden,
            modifier = Modifier
                .align(if (fab) Alignment.BottomStart else Alignment.BottomCenter)
                // Leave room for the + button on its right.
                .padding(end = if (fab) 72.dp else 0.dp),
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.8f) +
                slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                fadeIn(tween(200)),
            exit = scaleOut(tween(200), targetScale = 0.8f) + fadeOut(tween(200)),
        ) {
            val t = shown[0] ?: return@AnimatedVisibility
            MiniTimer(t, now, count = board.timers.size) { app.sheet = Sheet.Timers }
        }
    }
}

@Composable
private fun MiniTimer(t: CookTimer, now: Long, count: Int, onClick: () -> Unit) {
    val c = LocalColors.current
    val glow = if (t.done) doneGlow() else null
    Row(
        Modifier
            .tap(pressedScale = 0.95f, haptic = HapticType.LIGHT, label = "Show timers", onClick = onClick)
            .drawBehind {
                // A basil ring pulsing out of the pill while a timer is done.
                val p = glow?.value ?: return@drawBehind
                val spread = 9.dp.toPx() * p
                drawRoundRect(
                    color = c.basil.copy(alpha = 0.6f * (1f - p)),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + 2 * spread, size.height + 2 * spread),
                    cornerRadius = CornerRadius(size.height / 2 + spread),
                )
            }
            .shadow(18.dp, CircleShape)
            .background(c.cta, CircleShape)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimerRing(t.fractionLeft(now), t.done, 34.dp, track = c.onCta.copy(alpha = 0.18f), iconTint = c.onCta)
        Text(t.clock(now), style = clockStyle(16f), color = c.onCta, maxLines = 1)
        Text(
            t.label,
            style = RecipaiType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = c.onCta.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp).weight(1f, fill = false),
        )
        if (count > 1) TimerCountBadge(count, 22.dp)
    }
}

/** 0 -> 1 -> 0 every 1.2 s (the prototype's ring-done). */
@Composable
private fun doneGlow() = rememberInfiniteTransition(label = "done").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
    label = "glow",
)
