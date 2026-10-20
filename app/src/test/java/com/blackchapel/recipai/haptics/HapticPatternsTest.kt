package com.blackchapel.recipai.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {
    private fun peak(type: HapticType) = HapticPatterns.of(type).pulses.maxOf { it.scale }

    @Test
    fun everyTypeHasAPlayablePattern() {
        for (type in HapticType.entries) {
            val p = HapticPatterns.of(type)
            assertTrue("$type has pulses", p.pulses.isNotEmpty())
            for (pulse in p.pulses) {
                assertTrue("$type scale in (0, 1]", pulse.scale > 0f && pulse.scale <= 1f)
                assertTrue("$type delay is short and not negative", pulse.delayMs in 0..200)
            }
            assertEquals("$type starts right away", 0, p.pulses.first().delayMs)
            assertEquals(p.pulses.map { it.primitive }.toSet(), HapticPatterns.primitivesOf(type))
        }
    }

    @Test
    fun selectionIsAFullTickNotATextureTick() {
        // Feedback on the first build: everyday haptics felt too weak.
        val p = HapticPatterns.of(HapticType.SELECTION)
        assertEquals(listOf(HapticPulse(HapticPrimitive.TICK, 1.0f)), p.pulses)
        assertEquals(PredefinedHaptic.TICK, p.predefined)
    }

    @Test
    fun lightIsARealClickButSofterThanMedium() {
        val light = HapticPatterns.of(HapticType.LIGHT)
        assertEquals(HapticPrimitive.CLICK, light.pulses.single().primitive)
        assertEquals(PredefinedHaptic.CLICK, light.predefined)
        assertEquals(ViewHaptic.VIRTUAL_KEY, light.fallback)
        // The Import and + buttons moved from medium to light: a little softer.
        assertTrue(peak(HapticType.LIGHT) < peak(HapticType.MEDIUM))
        assertTrue(peak(HapticType.LIGHT) >= 0.5f)
    }

    @Test
    fun notificationTypesAreSeveralPulses() {
        assertEquals(2, HapticPatterns.of(HapticType.SUCCESS).pulses.size)
        assertEquals(2, HapticPatterns.of(HapticType.WARNING).pulses.size)
        assertEquals(3, HapticPatterns.of(HapticType.ERROR).pulses.size)
        // Success rises (soft then firm); warning falls (firm then softer).
        val s = HapticPatterns.of(HapticType.SUCCESS).pulses
        assertTrue(s[0].scale < s[1].scale)
        val w = HapticPatterns.of(HapticType.WARNING).pulses
        assertTrue(w[0].scale > w[1].scale)
    }
}
