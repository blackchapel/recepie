package com.blackchapel.recipai.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.Step
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/**
 * A step's time on the detail page, already scaled ([minutes]). The timers
 * module makes it a tap-to-start/pause/clear timer with a live countdown.
 */
@Composable
fun StepTimeChip(app: AppController, recipe: Recipe, step: Step, minutes: Double, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(
        modifier.height(34.dp).background(c.surface2, CircleShape).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RIcon(Ico.Clock, size = 15.dp, tint = c.ink)
        Text(Scaling.formatMinutes(minutes), style = RecipaiType.mono.copy(fontSize = RecipaiType.uiSmall.fontSize.times(13f / 13.5f)), color = c.ink)
    }
}
