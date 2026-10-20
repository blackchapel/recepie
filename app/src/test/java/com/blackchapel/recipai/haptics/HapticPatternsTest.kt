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
    fun everyTypeIsASingleClick() {
        // User feedback: remove double vibrations, make them single.
        for (type in HapticType.entries) {
            val p = HapticPatterns.of(type)
            assertEquals("$type is one pulse", 1, p.pulses.size)
            assertEquals("$type is a click", HapticPrimitive.CLICK, p.pulses.single().primitive)
        }
    }

    @Test
    fun strengthRisesFromSelectionToMedium() {
        assertTrue(peak(HapticType.SELECTION) < peak(HapticType.LIGHT))
        assertTrue(peak(HapticType.LIGHT) < peak(HapticType.MEDIUM))
        // Everyday taps were too weak (0.6): light is clearly firmer, but
        // not the full strength of the old Import button.
        assertTrue(peak(HapticType.LIGHT) in 0.75f..0.9f)
        // Close to light: selection controls (chips, segmented, steppers) must not feel weaker.
        assertTrue(peak(HapticType.SELECTION) >= 0.75f)
        assertTrue(peak(HapticType.LIGHT) - peak(HapticType.SELECTION) <= 0.1f)
        assertEquals(1.0f, peak(HapticType.MEDIUM), 0f)
    }

    @Test
    fun fallbacksStayClicks() {
        assertEquals(PredefinedHaptic.CLICK, HapticPatterns.of(HapticType.LIGHT).predefined)
        assertEquals(ViewHaptic.VIRTUAL_KEY, HapticPatterns.of(HapticType.LIGHT).fallback)
        assertEquals(ViewHaptic.LONG_PRESS, HapticPatterns.of(HapticType.MEDIUM).fallback)
    }
}
