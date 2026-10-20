package com.blackchapel.recipai.haptics

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** The feedback types from docs/DESIGN.md ("Haptics"). */
enum class HapticType { SELECTION, LIGHT, MEDIUM, SUCCESS, WARNING, ERROR }

/** Every interaction names a type; [Haptics] maps them to Android constants. */
fun interface HapticFeedback {
    fun perform(type: HapticType)
}

val LocalHaptics = staticCompositionLocalOf { HapticFeedback { } }

/**
 * Replaces Compose's own LocalHapticFeedback (long press, text handles) so
 * nothing vibrates past the in-app switch. Provided at the root and again
 * inside sheets, whose separate window re-provides the system one.
 */
object NoComposeHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
