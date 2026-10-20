package com.blackchapel.recipai.ui.timers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.Step
import com.blackchapel.recipai.core.StepTap
import com.blackchapel.recipai.haptics.HapticFeedback
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiType

/**
 * A tap on a step's time, from the detail chip or cook mode's big timer
 * button: start (medium haptic and a toast), pause / resume (selection),
 * or clear a finished one (light). [minutes] is the time at the shown scale.
 */
fun AppController.tapStepTimer(recipe: Recipe, step: Step, minutes: Double, haptics: HapticFeedback) {
    val index = recipe.steps.indexOfFirst { it.id == step.id }.coerceAtLeast(0)
    when (timers.tapStep(recipe.id, recipe.title, step.id, index, minutes)) {
        StepTap.STARTED -> {
            haptics.perform(HapticType.MEDIUM)
            showToast("Started a ${Scaling.formatMinutes(minutes)} timer", Ico.Timer)
        }
        StepTap.PAUSED, StepTap.RESUMED -> haptics.perform(HapticType.SELECTION)
        StepTap.CLEARED -> haptics.perform(HapticType.LIGHT)
        null -> Unit
    }
}

/** Mono digits for live countdowns. */
internal fun clockStyle(size: Float) = RecipaiType.mono.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp, fontWeight = FontWeight.SemiBold)

/**
 * The countdown ring from the prototype: a track and a flame arc for the
 * time left, starting at 12 o'clock, with a timer (or check) icon inside.
 */
@Composable
fun TimerRing(
    fractionLeft: Float,
    done: Boolean,
    size: Dp,
    track: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(if (done) 0f else fractionLeft, tween(300, easing = LinearEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            // The prototype's 46-unit viewBox: radius 19, stroke 4.
            val unit = this.size.minDimension / 46f
            val stroke = 4f * unit
            val radius = 19f * unit
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(
                    FlameBrush, -90f, 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        RIcon(if (done) Ico.Check else Ico.Timer, size = 15.dp, tint = iconTint)
    }
}

/** The flame count badge ("3") when more than one timer exists. */
@Composable
fun TimerCountBadge(count: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = size)
            .height(size)
            .background(FlameBrush, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", style = clockStyle(11f), color = OnFlame, maxLines = 1)
    }
}

/**
 * Extra bottom padding for a scrolling screen while the mini-timer pill
 * shows, so its last row isn't hidden under the pill (the prototype's
 * `.has-timer`).
 */
@Composable
fun timerPillClearance(app: AppController): Dp {
    val board by app.timers.board.collectAsState()
    return if (board.isEmpty) 0.dp else 64.dp
}

/** How far above the navigation bar the mini-timer pill floats on a pushed page. */
internal val PushedPillOffset = 84.dp

/** The mini-timer pill's height (6 dp padding around the 34 dp ring). */
private val PillHeight = 46.dp

/**
 * Bottom padding for a scrolling pushed page that the mini-timer pill
 * floats over (the editor, Backup; detail and cook mode hide the pill).
 * [base] is the page's own padding below its last row; while a timer shows,
 * the padding grows so the last row can scroll clear above the pill, which
 * sits [PushedPillOffset] above the nav bar rather than above a tab bar.
 */
@Composable
fun pushedPageBottomPadding(app: AppController, base: Dp): Dp {
    val board by app.timers.board.collectAsState()
    return if (board.isEmpty) base else maxOf(base, PushedPillOffset + PillHeight + 12.dp)
}
