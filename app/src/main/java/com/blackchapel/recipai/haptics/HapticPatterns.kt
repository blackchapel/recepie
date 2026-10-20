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
 * Tuning notes: every type is a single click (no double buzzes); the
 * types differ only in strength. See [HapticPatterns] for the history.
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
enum class PredefinedHaptic { TICK, CLICK, HEAVY_CLICK }

/** `HapticFeedbackConstants` available on every supported Android version (minSdk 26). */
enum class ViewHaptic { CLOCK_TICK, VIRTUAL_KEY, LONG_PRESS }

object HapticPatterns {
    // Every type is ONE click: user feedback on the second build was that
    // taps felt too weak and that success/warning/error buzzed twice. Scales
    // are the strength of that one click (1.0 is the strongest the
    // vibrator's click primitive goes; the original Import button was
    // stronger still, a long-press buzz, and was "too much"). The scale is
    // not linear to the finger, so the order matters more than the numbers:
    // selection < light < medium/notifications. Retune here only. Selection
    // sits close to light (0.8 vs 0.85) on purpose: sheets and chip rows are
    // full of selection-type controls, and at 0.65 they felt weaker than the
    // buttons elsewhere.
    private val selection = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 0.8f)),
        predefined = PredefinedHaptic.CLICK,
        fallback = ViewHaptic.VIRTUAL_KEY,
    )

    /** Buttons, tabs, the Import and + buttons: about 70% of the old Import button. */
    private val light = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 0.85f)),
        predefined = PredefinedHaptic.CLICK,
        fallback = ViewHaptic.VIRTUAL_KEY,
    )
    private val medium = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 1.0f)),
        predefined = PredefinedHaptic.HEAVY_CLICK,
        fallback = ViewHaptic.LONG_PRESS,
    )
    private val success = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 0.9f)),
        predefined = PredefinedHaptic.CLICK,
        fallback = ViewHaptic.VIRTUAL_KEY,
    )
    private val warning = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 1.0f)),
        predefined = PredefinedHaptic.HEAVY_CLICK,
        fallback = ViewHaptic.LONG_PRESS,
    )
    private val error = HapticPattern(
        pulses = listOf(HapticPulse(HapticPrimitive.CLICK, 1.0f)),
        predefined = PredefinedHaptic.HEAVY_CLICK,
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
