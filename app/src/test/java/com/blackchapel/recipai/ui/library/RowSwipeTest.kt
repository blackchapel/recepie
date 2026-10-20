package com.blackchapel.recipai.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowSwipeTest {
    private val action = 84f
    private val open = 168f
    private val fling = 500f

    @Test
    fun followsTheFingerUpToFullyOpen() {
        assertEquals(0f, RowSwipe.shown(0f, open), 0f)
        assertEquals(-50f, RowSwipe.shown(-50f, open), 0f)
        assertEquals(-168f, RowSwipe.shown(-168f, open), 0f)
    }

    @Test
    fun neverMovesRight() {
        assertEquals(0f, RowSwipe.shown(40f, open), 0f)
    }

    @Test
    fun rubberBandsPastFullyOpen() {
        assertEquals(-168f - 30f, RowSwipe.shown(-268f, open), 0.001f)
    }

    @Test
    fun theActionPointIsOneActionWide() {
        assertFalse(RowSwipe.pastActionPoint(-84f, action))
        assertTrue(RowSwipe.pastActionPoint(-85f, action))
    }

    @Test
    fun settlesByPositionUnlessFlung() {
        assertTrue(RowSwipe.settlesOpen(-100f, 0f, action, fling))
        assertFalse(RowSwipe.settlesOpen(-60f, 0f, action, fling))
        // A quick flick left opens even a short swipe; a flick right closes.
        assertTrue(RowSwipe.settlesOpen(-20f, -900f, action, fling))
        assertFalse(RowSwipe.settlesOpen(-150f, 900f, action, fling))
    }
}
