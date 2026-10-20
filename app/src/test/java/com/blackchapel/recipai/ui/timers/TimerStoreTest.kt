package com.blackchapel.recipai.ui.timers

import com.blackchapel.recipai.core.CookTimer
import com.blackchapel.recipai.core.StepTap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerStoreTest {
    private var time = 0L
    private val store = TimerStore { time }

    private fun tap(step: String = "s1", minutes: Double = 1.0) = store.tapStep("r", "Shakshuka", step, 0, minutes)

    @Test
    fun tapsStartPauseResumeAndClear() {
        assertEquals(StepTap.STARTED, tap())
        val t = store.board.value.timers.single()
        assertEquals("Step 1 · Shakshuka", t.label)
        time = 10_000
        assertEquals(StepTap.PAUSED, tap())
        assertEquals(10_000, store.now.value)
        time = 50_000
        assertEquals(StepTap.RESUMED, tap())
        assertEquals(100_000L, store.board.value.timers.single().endsAt)
        time = 100_000
        store.tick()
        assertTrue(store.board.value.timers.single().done)
        assertEquals(StepTap.CLEARED, tap())
        assertTrue(store.board.value.isEmpty)
        assertNull(tap(minutes = 0.0))
    }

    @Test
    fun eachFinishIsReportedOnce() {
        val alerted = mutableListOf<CookTimer>()
        store.onFinish = { alerted += it }
        tap("a", 1.0)
        tap("b", 2.0)
        time = 59_999
        assertTrue(store.tick().isEmpty())
        time = 60_000
        assertEquals(listOf("Step 1 · Shakshuka"), store.tick().map { it.label })
        assertTrue(store.tick().isEmpty())
        time = 200_000
        assertEquals(1, store.tick().size)
        assertEquals(2, alerted.size)
        assertFalse(store.board.value.hasActive)
    }

    @Test
    fun addingAMinuteToAFinishedTimerRunsItAgain() {
        tap()
        time = 61_000
        store.tick()
        val id = store.board.value.timers.single().id
        store.addMinute(id)
        assertTrue(store.board.value.timers.single().running)
        time = 121_000
        assertEquals(1, store.tick().size)
    }

    @Test
    fun timersOfDeletedRecipesGo() {
        tap()
        store.retainRecipes(setOf("other"))
        assertTrue(store.board.value.isEmpty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun finishesAreBroadcast() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<CookTimer>()
        val job = launch { store.finished.toList(seen) }
        tap()
        time = 60_000
        store.tick()
        store.tick()
        assertEquals(1, seen.size)
        job.cancel()
    }

    @Test
    fun toggleAndRemoveById() {
        tap()
        val id = store.board.value.timers.single().id
        store.toggle(id)
        assertTrue(store.board.value.timers.single().paused)
        store.remove(id)
        assertTrue(store.board.value.isEmpty)
    }
}
