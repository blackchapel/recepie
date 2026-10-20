package com.blackchapel.recipai.platform.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupTiming
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Automatic backups as a chain of one-time WorkManager jobs, each aimed at
 * an exact clock time (for example 8 PM): when one starts it queues the
 * next ([scheduleNext]), so every run is anchored to the chosen time and
 * never drifts.
 *
 * This is not WorkManager's periodic job on purpose. A periodic job only
 * promises "once per period, somewhere in a window": the system may run it
 * at any point of the window, and each next run is counted from when the
 * last one actually ran, so a late run (waiting for Wi-Fi, Doze) moves
 * every later run with it. A one-time job with an initial delay starts at
 * the target time, or as soon after it as the constraints hold (a network,
 * Wi-Fi if asked, battery not low) and the system lets background work
 * run. Still best effort, but a few minutes late, not an hour.
 *
 * An overdue run can also be queued once on its own ([runSoon]).
 */
object BackupScheduler {
    /** Every queued automatic run carries this tag, so they can be cancelled together. */
    private const val TAG = "recipai-automatic-backup"

    /** The unique name of the run aimed at [target]: re-queuing the same time is a no-op. */
    private const val NAME_PREFIX = "recipai-automatic-backup-at-"
    private const val CATCH_UP = "recipai-catch-up-backup"

    /** The periodic job earlier builds registered; it is cancelled so it can't double up. */
    private const val LEGACY_PERIODIC = "recipai-automatic-backup"

    /** Input data key: the epoch second this run was aimed at. */
    const val KEY_TARGET = "target"

    private fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** The schedule changed (or was just turned on): start the chain over from the new first run. */
    fun schedule(context: Context, frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean, now: ZonedDateTime) {
        cancel(context)
        scheduleNext(context, frequency, isoWeekday, hour, wifiOnly, now)
    }

    /**
     * Queues the run after [now] (strictly later), aimed at the chosen time.
     * Called when scheduling and by each run as it starts. Safe to call
     * twice for the same time: the second call is ignored.
     */
    fun scheduleNext(context: Context, frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean, now: ZonedDateTime) {
        val next = BackupTiming.nextRun(now, frequency, isoWeekday, hour) ?: return
        val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .addTag(TAG)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TARGET to next.toEpochSecond()))
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        // KEEP: a run already queued for this exact time stays as it is. The
        // name differs per target time, so queuing the next run from inside
        // a running one can never cancel the one that is running.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NAME_PREFIX + next.toEpochSecond(), ExistingWorkPolicy.KEEP, request)
    }

    /** One automatic backup as soon as the constraints hold; one already queued is kept. */
    fun runSoon(context: Context, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(TAG)
        work.cancelUniqueWork(LEGACY_PERIODIC)
        work.cancelUniqueWork(CATCH_UP)
    }
}
