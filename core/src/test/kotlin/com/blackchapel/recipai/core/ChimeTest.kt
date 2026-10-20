package com.blackchapel.recipai.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChimeTest {
    @Test fun lengthFollowsTheRepeats() {
        assertEquals(0.99, Chime.durationSeconds(1), 1e-9)
        assertEquals(44_100 * 2.24, Chime.pcm(44_100, 2).size.toDouble(), 2.0)
    }

    @Test fun startsSilentPeaksAndFadesOut() {
        val pcm = Chime.pcm(8_000, 1)
        assertEquals(0, pcm[0].toInt())
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak in 5_000..Short.MAX_VALUE, "audible but not clipped: $peak")
        val tail = pcm.takeLast(40).maxOf { abs(it.toInt()) }
        assertTrue(tail < 300, "fades out: $tail")
    }
}
