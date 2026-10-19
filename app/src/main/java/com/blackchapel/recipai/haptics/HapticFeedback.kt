package com.blackchapel.recipai.haptics

import androidx.compose.runtime.staticCompositionLocalOf

/** The feedback types from docs/DESIGN.md ("Haptics"). */
enum class HapticType { SELECTION, LIGHT, MEDIUM, SUCCESS, WARNING, ERROR }

/** Every interaction names a type; [Haptics] maps them to Android constants. */
fun interface HapticFeedback {
    fun perform(type: HapticType)
}

val LocalHaptics = staticCompositionLocalOf { HapticFeedback { } }
