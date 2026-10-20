package com.blackchapel.recipai.ui.cook

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.DetailPage
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnBasil
import com.blackchapel.recipai.ui.timers.TimerCountBadge
import com.blackchapel.recipai.ui.timers.clockStyle

/** Height the detail list keeps clear for [DetailBottomBar] (above the nav bar inset). */
val DetailBottomBarHeight = 72.dp

/**
 * The detail page's sticky bar: the timers button (the soonest countdown,
 * Done in basil when one has finished, a count when there are several)
 * and the flame **Start cooking** button at the page's scale.
 */
@Composable
fun DetailBottomBar(app: AppController, recipe: Recipe, page: DetailPage, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val board by app.timers.board.collectAsState()
    val now by app.timers.now.collectAsState()
    val featured = board.featured(now)
    val bg by animateColorAsState(
        when {
            featured == null -> c.surface2
            featured.done -> c.basil
            else -> c.cta
        },
        tween(250),
        label = "timers",
    )
    val fg = when {
        featured == null -> c.ink
        featured.done -> OnBasil
        else -> c.onCta
    }
    val shape = RoundedCornerShape(16.dp)

    Column(modifier.fillMaxWidth().background(c.bar)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .tap(
                        pressedScale = 0.95f,
                        haptic = HapticType.LIGHT,
                        // The name has to carry the clock: a content description replaces the Text inside.
                        description = when {
                            featured == null -> "Timers"
                            board.timers.size > 1 -> "Timers, ${featured.clock(now)}, ${board.timers.size} running"
                            else -> "Timers, ${featured.clock(now)}"
                        },
                    ) {
                        if (board.isEmpty) app.showToast("No timers running. Tap a step’s time to start one.", Ico.Timer)
                        else app.sheet = Sheet.Timers
                    }
                    .animateContentSize()
                    .height(52.dp)
                    .widthIn(min = 52.dp)
                    .background(bg, shape)
                    .border(1.dp, if (featured == null) c.line else Color.Transparent, shape)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RIcon(Ico.Timer, size = 20.dp, tint = fg)
                if (featured != null) {
                    Text(featured.clock(now), style = clockStyle(15f), color = fg, maxLines = 1)
                    if (board.timers.size > 1) TimerCountBadge(board.timers.size, 20.dp)
                }
            }
            RButton(
                "Start cooking",
                {
                    if (recipe.steps.isEmpty()) app.showToast("Add some steps to use cook mode.", Ico.Info)
                    else app.startCooking(recipe.id, page.factor)
                },
                Modifier.weight(1f),
                icon = Ico.Flame,
                style = ButtonStyle.FLAME,
                height = 52.dp,
            )
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
