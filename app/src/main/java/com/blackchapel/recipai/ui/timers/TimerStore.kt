package com.blackchapel.recipai.ui.timers

import com.blackchapel.recipai.core.CookTimer
import com.blackchapel.recipai.core.StepTap
import com.blackchapel.recipai.core.TimerBoard
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * The step timers, process-wide ([shared]) so they outlive the activity:
 * the foreground service ([com.blackchapel.recipai.platform.timers]) keeps
 * the process and the countdown alive with the screen off. Plain flows,
 * not Compose state, so the service can watch them with no UI around.
 *
 * Whoever calls [tick] (the on-screen ticker or the service) finishes due
 * timers; each finish is reported exactly once, to [onFinish] (sound and
 * notification, set by the platform) and on [finished] (in-app haptic and
 * toast).
 */
class TimerStore(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val lock = Any()
    private val ids = AtomicLong()

    private val _board = MutableStateFlow(TimerBoard())
    val board: StateFlow<TimerBoard> = _board.asStateFlow()

    private val _now = MutableStateFlow(clock())

    /** The time the board was last looked at; ticks while timers run. */
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _finished = MutableSharedFlow<CookTimer>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val finished: SharedFlow<CookTimer> = _finished.asSharedFlow()

    /** Settings > Timer sound, mirrored here for the service. */
    @Volatile var sound: Boolean = true

    /** Platform alert for a finished timer (chime, notification). */
    @Volatile var onFinish: ((CookTimer) -> Unit)? = null

    fun currentTime(): Long = clock()

    /**
     * A tap on a step's time (detail chip or cook mode's big timer button).
     * [minutes] is the step's time at the current scale.
     */
    fun tapStep(recipeId: String, recipeTitle: String, stepId: String, stepIndex: Int, minutes: Double): StepTap? {
        var result: StepTap? = null
        change { b, now ->
            val r = b.tapStep("t${ids.incrementAndGet()}", recipeId, stepId, TimerBoard.label(stepIndex, recipeTitle), minutes, now)
            result = r?.second
            r?.first ?: b
        }
        return result
    }

    fun toggle(id: String) = change { b, now -> b.toggle(id, now) }

    fun addMinute(id: String) = change { b, now -> b.addMinute(id, now) }

    /** Cancel a running timer or dismiss a finished one. */
    fun remove(id: String) = change { b, _ -> b.remove(id) }

    /** Drops timers of recipes that no longer exist. */
    fun retainRecipes(recipeIds: Set<String>) = change { b, _ -> b.retainRecipes(recipeIds) }

    /** Moves the clock on and finishes due timers. Returns the ones that just finished. */
    fun tick(): List<CookTimer> {
        val done = synchronized(lock) {
            val now = clock()
            val (next, finished) = _board.value.finishDue(now)
            _board.value = next
            _now.value = now
            finished
        }
        done.forEach { t ->
            runCatching { onFinish?.invoke(t) }
            _finished.tryEmit(t)
        }
        return done
    }

    private inline fun change(f: (TimerBoard, Long) -> TimerBoard) {
        synchronized(lock) {
            val now = clock()
            _board.value = f(_board.value, now)
            _now.value = now
        }
        // A change can make a timer due (a paused one at 0:00 resumed).
        tick()
    }

    companion object {
        /** The app's timers. */
        val shared = TimerStore()
    }
}
