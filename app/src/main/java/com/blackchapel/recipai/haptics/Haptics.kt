package com.blackchapel.recipai.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The one place feedback types become Android constants (docs/DESIGN.md,
 * "Haptics"). Goes through [View.performHapticFeedback], so it needs no
 * VIBRATE permission and honors the system's touch-feedback setting; the
 * in-app switch ([enabled]) turns it off entirely.
 */
class Haptics(private val view: View, private val enabled: () -> Boolean) : HapticFeedback {
    override fun perform(type: HapticType) {
        if (!enabled()) return
        view.performHapticFeedback(constantFor(type))
    }

    private fun constantFor(type: HapticType): Int = when (type) {
        HapticType.SELECTION ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
        HapticType.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticType.MEDIUM -> HapticFeedbackConstants.LONG_PRESS
        HapticType.SUCCESS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
        HapticType.WARNING, HapticType.ERROR ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
    }
}
