package com.blackchapel.recipai.ui.cook

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.Cell
import com.blackchapel.recipai.ui.components.Hairline
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RSwitch
import com.blackchapel.recipai.ui.theme.LocalColors

/**
 * Rows appended to Settings > Cooking after Haptic feedback: Keep screen
 * on in cook mode, Timer sound. Each row draws its own leading hairline.
 */
@Composable
fun CookingSettingsRows(app: AppController, settings: AppSettings) {
    val c = LocalColors.current
    Hairline(start = 56.dp)
    Cell(
        "Keep screen on in cook mode",
        icon = Ico.Sun,
        iconColor = c.saffron,
        role = Role.Switch,
        haptic = HapticType.SELECTION,
        checked = settings.keepScreenOn,
        onClick = { app.updateSettings { it.copy(keepScreenOn = !it.keepScreenOn) } },
    ) { RSwitch(settings.keepScreenOn) }
    Hairline(start = 56.dp)
    Cell(
        "Timer sound",
        icon = Ico.Volume,
        iconColor = c.sumac,
        subtitle = "Plays a chime when a timer ends",
        role = Role.Switch,
        haptic = HapticType.SELECTION,
        checked = settings.timerSound,
        onClick = { app.updateSettings { it.copy(timerSound = !it.timerSound) } },
    ) { RSwitch(settings.timerSound) }
}
