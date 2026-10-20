package com.blackchapel.recipai.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi

/**
 * The one place feedback types become Android vibrations (docs/DESIGN.md,
 * "Haptics"; the tuning table is [HapticPatterns]).
 *
 * Plays a tuned `VibrationEffect` composition (API 30+, when the vibrator
 * supports the primitives) or a predefined effect (API 29+) through the
 * [Vibrator] with touch usage, so the system's touch-feedback intensity
 * applies; older phones fall back to [View.performHapticFeedback]. Nothing
 * plays when the in-app switch ([enabled]) or the system "Touch feedback"
 * setting is off. Needs the VIBRATE permission (in the manifest).
 */
class Haptics(private val view: View, private val enabled: () -> Boolean) : HapticFeedback {
    private val vibrator: Vibrator? = findVibrator(view.context)

    /** Built effects per type; the vibrator's capabilities never change. */
    private val effects = arrayOfNulls<VibrationEffect>(HapticType.entries.size)

    override fun perform(type: HapticType) {
        if (!enabled() || !view.isHapticFeedbackEnabled || !systemTouchFeedbackOn()) return
        val v = vibrator
        if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (vibrate(v, effectFor(type, v))) return
        }
        view.performHapticFeedback(viewConstant(HapticPatterns.of(type).fallback))
    }

    /** The system "Touch feedback" switch (on unless the phone says otherwise). */
    private fun systemTouchFeedbackOn(): Boolean = try {
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (e: RuntimeException) {
        true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun effectFor(type: HapticType, v: Vibrator): VibrationEffect {
        effects[type.ordinal]?.let { return it }
        val pattern = HapticPatterns.of(type)
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsPrimitives(v, type)) {
            composition(pattern)
        } else {
            VibrationEffect.createPredefined(predefinedId(pattern.predefined))
        }
        effects[type.ordinal] = effect
        return effect
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun supportsPrimitives(v: Vibrator, type: HapticType): Boolean {
        val ids = HapticPatterns.primitivesOf(type).map(::primitiveId).toIntArray()
        return try {
            v.areAllPrimitivesSupported(*ids)
        } catch (e: RuntimeException) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composition(pattern: HapticPattern): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        for (p in pattern.pulses) {
            composition.addPrimitive(primitiveId(p.primitive), p.scale.coerceIn(0f, 1f), p.delayMs.coerceAtLeast(0))
        }
        return composition.compose()
    }

    /** False when the vibrator refused, so the caller can fall back. */
    private fun vibrate(v: Vibrator, effect: VibrationEffect): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            // Sonification audio attributes are what the system classifies
            // as touch feedback before VibrationAttributes existed.
            @Suppress("DEPRECATION")
            v.vibrate(effect, touchAudioAttributes)
        }
        true
    } catch (e: RuntimeException) {
        false
    }

    private companion object {
        val touchAudioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun findVibrator(context: Context): Vibrator? {
            val v = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (e: RuntimeException) {
                null
            }
            return v?.takeIf { it.hasVibrator() }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun primitiveId(p: HapticPrimitive): Int = when (p) {
            HapticPrimitive.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
            HapticPrimitive.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun predefinedId(p: PredefinedHaptic): Int = when (p) {
            PredefinedHaptic.TICK -> VibrationEffect.EFFECT_TICK
            PredefinedHaptic.CLICK -> VibrationEffect.EFFECT_CLICK
            PredefinedHaptic.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
        }

        fun viewConstant(h: ViewHaptic): Int = when (h) {
            ViewHaptic.CLOCK_TICK -> HapticFeedbackConstants.CLOCK_TICK
            ViewHaptic.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY
            ViewHaptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        }
    }
}
