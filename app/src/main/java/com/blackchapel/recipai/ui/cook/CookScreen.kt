package com.blackchapel.recipai.ui.cook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.IngredientUses
import com.blackchapel.recipai.core.Quantities
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.Step
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.timers.KeepScreenOn
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.CookPage
import com.blackchapel.recipai.ui.DetailPage
import com.blackchapel.recipai.ui.components.IconButton
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.DisplayFamily
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import com.blackchapel.recipai.ui.timers.clockStyle
import com.blackchapel.recipai.ui.timers.tapStepTimer
import kotlin.math.abs

/** The prototype's --ease-out. */
private val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val Gutter = 18.dp

private val StepStyle = RecipaiType.section.copy(
    fontFamily = DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 27.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.02).em,
)

/**
 * Full-screen cook mode at the detail page's scale: progress segments, one
 * step in large type, the scaled ingredients that step uses, a big timer
 * button when the step has a time, swipe or buttons to move, Finish on the
 * last step. Moving on marks the step done on the detail page underneath.
 * Keeps the screen on when Settings > Keep screen on is set.
 */
@Composable
fun CookScreen(app: AppController, page: CookPage, recipe: Recipe?) {
    val c = LocalColors.current
    if (recipe == null || recipe.steps.isEmpty()) {
        // Deleted meanwhile, or nothing to cook: leave the page.
        LaunchedEffect(Unit) {
            if (recipe != null) app.showToast("Add some steps to use cook mode.", Ico.Info)
            if (app.stack.lastOrNull() === page) app.back()
        }
        Box(Modifier.fillMaxSize().background(c.canvas))
        return
    }
    val haptics = LocalHaptics.current
    val settings by app.settings.collectAsState()
    KeepScreenOn(settings.keepScreenOn)

    val steps = recipe.steps
    // The detail page this was opened from, if any: its done steps.
    val detail = remember(page) { app.stack.lastOrNull { it is DetailPage && it.recipeId == page.recipeId } as DetailPage? }
    var index by rememberSaveable {
        mutableIntStateOf(detail?.let { d -> steps.indexOfFirst { it.id !in d.done } }?.coerceAtLeast(0) ?: 0)
    }
    val i = index.coerceIn(0, steps.lastIndex)
    val last = i == steps.lastIndex
    val scaled = abs(page.factor - 1) > 1e-6

    fun go(d: Int) {
        // Ignore taps and swipes while the page is already sliding away.
        if (app.stack.lastOrNull() !== page) return
        if (d > 0) detail?.let { if (steps[i].id !in it.done) it.done.add(steps[i].id) }
        if (i + d > steps.lastIndex) {
            haptics.perform(HapticType.SUCCESS)
            app.sheet = null
            app.back()
            app.showToast("All steps done. Enjoy your meal!", Ico.Check)
            return
        }
        if (i + d < 0) return
        haptics.perform(HapticType.SELECTION)
        index = i + d
    }
    val goNow by rememberUpdatedState(::go)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = Gutter, end = Gutter, top = 6.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    recipe.title,
                    style = RecipaiType.cardTitle.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Step ${i + 1} of ${steps.size}",
                    style = RecipaiType.mono.copy(fontWeight = FontWeight.Normal),
                    color = c.mute,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (scaled) ScaleBadge("${Quantities.trimmed(page.factor, 2)}×")
            IconButton(Ico.X, "Exit cook mode", { app.back() }, size = 32.dp, iconSize = 16.dp, background = c.surface2, tint = c.mute)
        }

        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            steps.indices.forEach { k -> ProgressSegment(on = k <= i, modifier = Modifier.weight(1f)) }
        }

        val density = LocalDensity.current
        val threshold = with(density) { 50.dp.toPx() }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragCancel = { dx = 0f },
                        onDragEnd = { if (abs(dx) > threshold) goNow(if (dx < 0) 1 else -1) },
                    ) { change, amount ->
                        change.consume()
                        dx += amount
                    }
                },
        ) {
            val viewport = maxHeight
            AnimatedContent(
                targetState = i,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(tween(500, easing = EaseOut)) { with(density) { 40.dp.roundToPx() } * dir } + fadeIn(tween(400))) togetherWith
                        fadeOut(tween(120))
                },
                label = "step",
            ) { k ->
                StepBody(app, recipe, steps[k], k, page.factor, Modifier.verticalScroll(rememberScrollState()).heightIn(min = viewport))
            }
        }

        Text(
            "Swipe left or right to change steps",
            style = RecipaiType.bodySmall.copy(fontSize = 12.sp, textAlign = TextAlign.Center),
            color = c.faint,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .graphicsLayer { alpha = if (i == 0) 0.4f else 1f }
                    .tap(enabled = i > 0, pressedScale = 0.94f, haptic = null, description = "Previous step") { go(-1) }
                    .size(56.dp)
                    .background(c.surface2, shape)
                    .border(1.dp, c.line, shape),
                contentAlignment = Alignment.Center,
            ) { RIcon(Ico.Left, tint = c.ink) }
            Row(
                Modifier
                    .weight(1f)
                    .tap(pressedScale = 0.97f, haptic = null, label = if (last) "Finish cooking" else "Next step") { go(1) }
                    .height(56.dp)
                    .background(c.cta, shape),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = RecipaiType.ui.copy(fontSize = 16.sp)
                if (last) {
                    RIcon(Ico.Check, tint = c.onCta)
                    Text("Finish", style = label, color = c.onCta)
                } else {
                    Text("Next step", style = label, color = c.onCta)
                    RIcon(Ico.Right, tint = c.onCta)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepBody(app: AppController, recipe: Recipe, step: Step, index: Int, factor: Double, modifier: Modifier) {
    val c = LocalColors.current
    val uses = remember(recipe, step) { IngredientUses.uses(recipe, step) }
    val minutes = Scaling.minutes(step.minutes, step.timeScale, factor)
    Column(
        modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Text("Step ${index + 1}".uppercase(), style = RecipaiType.eyebrow, color = c.accentInk)
        Text(step.text, style = StepStyle, color = c.ink)
        if (uses.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uses.forEach { ing ->
                    val a = Scaling.amount(ing, factor)
                    Row(
                        Modifier
                            .background(c.surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val qty = listOf(a.text, a.unit).filter { it.isNotEmpty() }.joinToString(" ")
                        if (a.text.isNotEmpty()) Text(qty, style = clockStyle(13f), color = c.ink, maxLines = 1)
                        Text(ing.name, style = RecipaiType.body.copy(fontSize = 14.sp, lineHeight = 18.sp), color = c.body)
                    }
                }
            }
        }
        if (minutes > 0) BigTimer(app, recipe, step, minutes)
    }
}

/** Starts, pauses, resumes or clears this step's timer; shows its countdown. */
@Composable
private fun BigTimer(app: AppController, recipe: Recipe, step: Step, minutes: Double) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val board by app.timers.board.collectAsState()
    val now by app.timers.now.collectAsState()
    val t = board.forStep(recipe.id, step.id)
    val active = t != null && !t.done
    val bg by animateColorAsState(if (active) lerp(c.surface, c.saffron, 0.18f) else c.surface, tween(250), label = "big-timer")
    val text = when {
        t == null -> "Start ${Scaling.formatMinutes(minutes)} timer"
        t.done -> "Done"
        t.paused -> "${t.clock(now)} · paused"
        else -> t.clock(now)
    }
    Row(
        Modifier
            .tap(pressedScale = 0.95f, haptic = null, role = Role.Button, label = text) { app.tapStepTimer(recipe, step, minutes, haptics) }
            .height(54.dp)
            .background(bg, CircleShape)
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RIcon(Ico.Timer, size = 24.dp, tint = c.ink)
        Text(text, style = clockStyle(19f), color = if (t?.done == true) c.basil else c.ink, maxLines = 1)
    }
}

/** One progress segment: a track that fills with the flame. */
@Composable
private fun ProgressSegment(on: Boolean, modifier: Modifier) {
    val c = LocalColors.current
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(500, easing = EaseOut), label = "segment")
    Box(modifier.height(4.dp).background(c.surface3, RoundedCornerShape(4.dp))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fill
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    clip = true
                    shape = RoundedCornerShape(4.dp)
                }
                .background(FlameBrush),
        )
    }
}

/** The saffron "1.5×" tag shown while cooking at a scale. */
@Composable
private fun ScaleBadge(text: String) {
    val c = LocalColors.current
    Box(
        Modifier
            .height(24.dp)
            .background(c.saffron.copy(alpha = 0.15f), CircleShape)
            .border(1.dp, c.saffron.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = RecipaiType.tag, color = c.tagText(c.saffron), maxLines = 1) }
}
