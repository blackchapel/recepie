package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimersTest {
    private val label = TimerBoard.label(1, "Shakshuka")

    private fun started(minutes: Double = 8.0, now: Long = 0): TimerBoard =
        TimerBoard().start("t1", "r", "s1", label, minutes, now)

    @Test fun labelNamesTheStepAndRecipe() {
        assertEquals("Step 2 · Shakshuka", label)
    }

    @Test fun startingRunsForTheGivenMinutes() {
        val t = started(6.5, now = 1_000).timers.single()
        assertTrue(t.running)
        assertEquals(390_000, t.totalMs)
        assertEquals(391_000, t.endsAt)
        assertEquals("6:30", t.clock(1_000))
        assertEquals("6:29", t.clock(2_000))
        assertEquals("6:30", t.clock(1_500), "a partial second still shows as the next whole one")
        assertEquals(0.5f, t.fractionLeft(1_000 + 195_000))
    }

    @Test fun noTimeStartsNothing() {
        val b = TimerBoard()
        assertSame(b, b.start("t1", "r", "s1", label, 0.0, 0))
        assertNull(b.tapStep("t1", "r", "s1", label, 0.0, 0))
    }

    @Test fun tappingAStepCyclesStartPauseResumeAndClear() {
        var b = TimerBoard()
        val (b1, first) = b.tapStep("t1", "r", "s1", label, 1.0, now = 0)!!
        assertEquals(StepTap.STARTED, first)
        b = b1

        val (b2, second) = b.tapStep("t2", "r", "s1", label, 1.0, now = 20_000)!!
        assertEquals(StepTap.PAUSED, second)
        assertTrue(b2.timers.single().paused)
        assertEquals(40_000, b2.timers.single().remainingMs(999_999), "paused time does not move")

        val (b3, third) = b2.tapStep("t3", "r", "s1", label, 1.0, now = 100_000)!!
        assertEquals(StepTap.RESUMED, third)
        assertEquals(140_000, b3.timers.single().endsAt)

        val (b4, finished) = b3.finishDue(140_000)
        assertEquals(listOf("t1"), finished.map { it.id })
        assertTrue(b4.timers.single().done)
        assertEquals("Done", b4.timers.single().clock(150_000))

        val (b5, fourth) = b4.tapStep("t4", "r", "s1", label, 1.0, now = 150_000)!!
        assertEquals(StepTap.CLEARED, fourth)
        assertTrue(b5.isEmpty)
    }

    @Test fun finishingHappensOnce() {
        val (b, first) = started(1.0).finishDue(60_000)
        assertEquals(1, first.size)
        assertEquals(0, b.finishDue(90_000).second.size)
        assertFalse(b.hasActive)
        assertFalse(b.hasRunning)
    }

    @Test fun notYetDueKeepsRunning() {
        val b = started(1.0)
        val (same, none) = b.finishDue(59_999)
        assertSame(b, same)
        assertTrue(none.isEmpty())
    }

    @Test fun addMinuteGrowsRunningPausedAndRevivesDone() {
        val running = started(1.0).addMinute("t1", 10_000).timers.single()
        assertEquals(120_000, running.endsAt)
        assertEquals(120_000, running.totalMs)

        val paused = started(1.0).pause("t1", 30_000).addMinute("t1", 40_000).timers.single()
        assertEquals(90_000, paused.leftMs)
        assertTrue(paused.paused)

        val done = started(1.0).finishDue(60_000).first.addMinute("t1", 70_000).timers.single()
        assertTrue(done.running)
        assertEquals(130_000, done.endsAt)
        assertEquals(120_000, done.totalMs)
    }

    @Test fun toggleSwitchesBetweenPausedAndRunning() {
        val b = started(2.0).toggle("t1", 30_000)
        assertTrue(b.timers.single().paused)
        assertTrue(b.toggle("t1", 50_000).timers.single().running)
        assertEquals(140_000, b.toggle("t1", 50_000).timers.single().endsAt)
    }

    @Test fun featuredPrefersDoneThenSoonest() {
        var b = TimerBoard()
            .start("a", "r", "s1", "A", 10.0, 0)
            .start("b", "r", "s2", "B", 2.0, 0)
            .start("c", "r", "s3", "C", 5.0, 0)
        assertEquals("b", b.featured(0)?.id)
        assertEquals(120_000, b.nextDeadline())
        b = b.pause("b", 1_000)
        assertEquals("c", b.featured(1_000)?.id)
        b = b.finishDue(300_000).first
        assertEquals("c", b.featured(300_000)?.id)
        assertNull(TimerBoard().featured(0))
    }

    @Test fun oneTimerPerStep() {
        val b = started(1.0).start("t2", "r", "s1", label, 3.0, 0)
        assertEquals(listOf("t2"), b.timers.map { it.id })
        assertEquals("t2", b.forStep("r", "s1")?.id)
        assertNull(b.forStep("r", "other"))
    }

    @Test fun deletedRecipesLoseTheirTimers() {
        val b = started().start("t2", "gone", "s1", label, 3.0, 0)
        assertEquals(listOf("t1"), b.retainRecipes(setOf("r")).timers.map { it.id })
        val kept = started()
        assertSame(kept, kept.retainRecipes(setOf("r", "x")))
    }

    @Test fun removeAndUnknownIds() {
        val b = started()
        assertSame(b, b.pause("nope", 0))
        assertTrue(b.remove("t1").isEmpty)
    }
}
