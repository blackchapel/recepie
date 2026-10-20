package com.blackchapel.recipai.haptics

/**
 * How each [HapticType] feels, as plain data so the tuning is in one
 * readable, unit-tested table. [Haptics] turns it into Android calls,
 * picking the richest tier the phone supports:
 *
 * 1. [HapticPattern.pulses]: a `VibrationEffect` composition of haptic
 *    primitives with a per-pulse scale (API 30+, when the vibrator
 *    supports every primitive used).
 * 2. [HapticPattern.predefined]: a predefined `VibrationEffect` (API 29+).
 * 3. [HapticPattern.fallback]: `View.performHapticFeedback` (older phones,
 *    or no usable vibrator).
 *
 * Tuning notes (user feedback on the first test build): everyday feedback
 * felt too weak, so selection uses a full-strength tick instead of the
 * texture tick behind `CLOCK_TICK`/`SEGMENT_TICK`, and light is a real
 * click. The Import and + buttons felt too strong; they now use [HapticType.LIGHT]
 * instead of [HapticType.MEDIUM] (see Chrome.kt), which is a softer click
 * than medium's full one.
 */
data class HapticPattern(
    val pulses: List<HapticPulse>,
    val predefined: PredefinedHaptic,
    val fallback: ViewHaptic,
)

/**
 * One primitive in a composition. [scale] is the primitive's strength
 * (0, 1]; [delayMs] is the pause after the previous pulse.
 */
data class HapticPulse(val primitive: HapticPrimitive, val scale: Float, val delayMs: Int = 0)

/** `VibrationEffect.Composition.PRIMITIVE_*` (all API 30). */
enum class HapticPrimitive { CLICK, TICK }

/** `VibrationEffect.EFFECT_*` (all API 29). */
enum class PredefinedHaptic { TICK, CLICK, HEAVY_CLICK, DOUBLE_CLICK }

/** `HapticFeedbackConstants` available on every supported Android version (minSdk 26). */
enum class ViewHaptic { CLOCK_TICK, VIRTUAL_KEY, LONG_PRESS }

object HapticPatterns {
    private val selection = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.TICK, 1.0f)),
        predefined = PredefinedHaptic.TICK,
        fallback = ViewHaptic.CLOCK_TICK,
    )
    private val light = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 0.6f)),
        predefined = PredefinedHaptic.CLICK,
        fallback = ViewHaptic.VIRTUAL_KEY,
    )
    private val medium = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 1.0f)),
        predefined = PredefinedHaptic.HEAVY_CLICK,
        fallback = ViewHaptic.LONG_PRESS,
    )

    // The multi-pulse types follow the prototype's vibration patterns:
    // success [10, 50, 16] (soft then firm), warning [18, 70, 18] (firm,
    // a longer gap, a little softer), error [24, 60, 24, 60, 24].
    private val success = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 0.55f), HapticPulse(HapticPrimitive.CLICK, 1.0f, delayMs = 50)),
        predefined = PredefinedHaptic.DOUBLE_CLICK,
        fallback = ViewHaptic.LONG_PRESS,
    )
    private val warning = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 1.0f), HapticPulse(HapticPrimitive.CLICK, 0.8f, delayMs = 70)),
        predefined = PredefinedHaptic.HEAVY_CLICK,
        fallback = ViewHaptic.LONG_PRESS,
    )
    private val error = HapticPattern(
        pulses = listOf(
            HapticPulse(HapticPrimitive.CLICK, 1.0f),
            HapticPulse(HapticPrimitive.CLICK, 1.0f, delayMs = 60),
            HapticPulse(HapticPrimitive.CLICK, 1.0f, delayMs = 60),
        ),
        predefined = PredefinedHaptic.DOUBLE_CLICK,
        fallback = ViewHaptic.LONG_PRESS,
    )

    fun of(type: HapticType): HapticPattern = when (type) {
        HapticType.SELECTION -> selection
        HapticType.LIGHT -> light
        HapticType.MEDIUM -> medium
        HapticType.SUCCESS -> success
        HapticType.WARNING -> warning
        HapticType.ERROR -> error
    }

    /** Every primitive a type uses, for the vibrator's support check. */
    fun primitivesOf(type: HapticType): Set<HapticPrimitive> = of(type).pulses.mapTo(mutableSetOf()) { it.primitive }
}
