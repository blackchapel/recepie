package com.blackchapel.recipai.core

import kotlin.math.roundToLong

/**
 * One step timer. Times are wall-clock milliseconds (they keep counting
 * while the phone sleeps). A timer is running ([endsAt] set), paused
 * ([leftMs] holds what is left) or [done].
 *
 * @property label what the timer is for, e.g. "Step 2 · Shakshuka"
 * @property totalMs the full length, grown by +1 min (the ring's 100%)
 */
data class CookTimer(
    val id: String,
    val recipeId: String,
    val stepId: String,
    val label: String,
    val totalMs: Long,
    val endsAt: Long?,
    val leftMs: Long,
    val done: Boolean = false,
) {
    val running: Boolean get() = !done && endsAt != null
    val paused: Boolean get() = !done && endsAt == null

    fun remainingMs(now: Long): Long = when {
        done -> 0
        endsAt != null -> maxOf(0, endsAt - now)
        else -> leftMs
    }

    /** What is left, 1 (just started) to 0 (done): the ring around the timer. */
    fun fractionLeft(now: Long): Float =
        if (totalMs <= 0) 0f else (remainingMs(now).toDouble() / totalMs).toFloat().coerceIn(0f, 1f)

    /** "9:05", or "Done". */
    fun clock(now: Long): String = if (done) "Done" else Scaling.formatClock(remainingMs(now) / 1000.0)
}

/** What a tap on a step's time did. */
enum class StepTap { STARTED, PAUSED, RESUMED, CLEARED }

/**
 * Every timer, as an immutable value: each change returns a new board.
 * Behavior follows the prototype (docs/design/recipai-prototype.html,
 * "Timers"): one timer per recipe step, tap to start / pause / resume,
 * a finished timer shows Done until cleared, +1 min revives it.
 */
data class TimerBoard(val timers: List<CookTimer> = emptyList()) {
    val isEmpty: Boolean get() = timers.isEmpty()
    val hasRunning: Boolean get() = timers.any { it.running }

    /** Running or paused: something the foreground service keeps alive. */
    val hasActive: Boolean get() = timers.any { !it.done }

    fun byId(id: String): CookTimer? = timers.firstOrNull { it.id == id }

    fun forStep(recipeId: String, stepId: String): CookTimer? =
        timers.firstOrNull { it.recipeId == recipeId && it.stepId == stepId }

    /**
     * The one to show when there is room for one: a finished timer first,
     * else the running one that ends soonest, else the first.
     */
    fun featured(now: Long): CookTimer? =
        timers.firstOrNull { it.done }
            ?: timers.filter { it.running }.minByOrNull { it.remainingMs(now) }
            ?: timers.firstOrNull()

    /** When the next running timer ends, if any is running. */
    fun nextDeadline(): Long? = timers.filter { it.running }.mapNotNull { it.endsAt }.minOrNull()

    /** Starts a timer of [minutes] for a step. No-op for no time. */
    fun start(id: String, recipeId: String, stepId: String, label: String, minutes: Double, now: Long): TimerBoard {
        val ms = (minutes * 60).roundToLong() * 1000
        if (ms <= 0) return this
        val rest = timers.filterNot { it.recipeId == recipeId && it.stepId == stepId }
        return TimerBoard(rest + CookTimer(id, recipeId, stepId, label, ms, now + ms, ms))
    }

    /**
     * A tap on a step's time: no timer starts one, a running one pauses, a
     * paused one resumes, a finished one is cleared. Returns null when
     * nothing happened (no time to start).
     */
    fun tapStep(newId: String, recipeId: String, stepId: String, label: String, minutes: Double, now: Long): Pair<TimerBoard, StepTap>? {
        val existing = forStep(recipeId, stepId)
        return when {
            existing == null -> start(newId, recipeId, stepId, label, minutes, now).takeIf { it !== this }?.let { it to StepTap.STARTED }
            existing.done -> remove(existing.id) to StepTap.CLEARED
            existing.running -> pause(existing.id, now) to StepTap.PAUSED
            else -> resume(existing.id, now) to StepTap.RESUMED
        }
    }

    fun pause(id: String, now: Long): TimerBoard = map(id) { t ->
        if (!t.running) t else t.copy(endsAt = null, leftMs = t.remainingMs(now))
    }

    fun resume(id: String, now: Long): TimerBoard = map(id) { t ->
        if (!t.paused) t else t.copy(endsAt = now + t.leftMs)
    }

    /** Pause a running timer, resume a paused one. */
    fun toggle(id: String, now: Long): TimerBoard {
        val t = byId(id) ?: return this
        return if (t.running) pause(id, now) else resume(id, now)
    }

    /** One more minute. A finished timer starts again with that minute. */
    fun addMinute(id: String, now: Long): TimerBoard = map(id) { t ->
        val total = t.totalMs + MINUTE
        when {
            t.done -> t.copy(totalMs = total, done = false, endsAt = now + MINUTE, leftMs = MINUTE)
            t.running -> t.copy(totalMs = total, endsAt = t.endsAt!! + MINUTE)
            else -> t.copy(totalMs = total, leftMs = t.leftMs + MINUTE)
        }
    }

    fun remove(id: String): TimerBoard = TimerBoard(timers.filterNot { it.id == id })

    /** Drops timers whose recipe is gone (deleted). */
    fun retainRecipes(recipeIds: Set<String>): TimerBoard =
        if (timers.all { it.recipeId in recipeIds }) this else TimerBoard(timers.filter { it.recipeId in recipeIds })

    /** Marks running timers that reached zero as done; returns them too. */
    fun finishDue(now: Long): Pair<TimerBoard, List<CookTimer>> {
        val due = timers.filter { it.running && it.endsAt!! <= now }
        if (due.isEmpty()) return this to emptyList()
        val ids = due.map { it.id }.toSet()
        val next = TimerBoard(timers.map { if (it.id in ids) it.copy(done = true, endsAt = null, leftMs = 0) else it })
        return next to next.timers.filter { it.id in ids }
    }

    private inline fun map(id: String, f: (CookTimer) -> CookTimer): TimerBoard {
        if (timers.none { it.id == id }) return this
        return TimerBoard(timers.map { if (it.id == id) f(it) else it })
    }

    companion object {
        const val MINUTE = 60_000L

        /** "Step 2 · Shakshuka". */
        fun label(stepIndex: Int, recipeTitle: String): String = "Step ${stepIndex + 1} · $recipeTitle"
    }
}
