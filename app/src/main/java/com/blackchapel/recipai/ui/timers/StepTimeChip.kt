package com.blackchapel.recipai.ui.timers

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.Step
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors

/**
 * A step's time on the detail page, already scaled ([minutes]). Tap starts
 * a timer, tap again pauses or resumes it, a finished one shows Done (tap
 * clears it). While a timer exists the chip is its live countdown.
 */
@Composable
fun StepTimeChip(app: AppController, recipe: Recipe, step: Step, minutes: Double, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val board by app.timers.board.collectAsState()
    val now by app.timers.now.collectAsState()
    val t = board.forStep(recipe.id, step.id)
    val bg by animateColorAsState(
        when {
            t == null -> c.surface2
            t.done -> lerp(c.surface, c.basil, 0.2f)
            else -> lerp(c.surface, c.saffron, 0.2f)
        },
        tween(250),
        label = "chip",
    )
    val label = when {
        t == null -> "Start a ${Scaling.formatMinutes(minutes)} timer"
        t.done -> "Clear the finished timer"
        t.running -> "Pause the timer"
        else -> "Resume the timer"
    }
    Row(
        modifier
            .tap(pressedScale = 0.94f, haptic = null, label = label) { app.tapStepTimer(recipe, step, minutes, haptics) }
            .height(34.dp)
            .background(bg, CircleShape)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RIcon(if (t != null && t.running) Ico.Pause else Ico.Timer, size = 15.dp, tint = c.ink)
        Text(
            if (t == null) Scaling.formatMinutes(minutes) else t.clock(now),
            style = clockStyle(13f),
            color = if (t == null) c.ink else c.accentInk,
            maxLines = 1,
        )
    }
}
