package com.blackchapel.recipai.platform.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupTiming
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Automatic backups as one unique periodic WorkManager job: first run at
 * the next chosen time, then every day or week. Best effort: WorkManager
 * runs it when the OS allows (Doze, battery saver) and the constraints
 * hold (a network, Wi-Fi if asked, battery not low). An overdue run can
 * also be queued once on its own ([runSoon]).
 */
object BackupScheduler {
    private const val WORK = "recipai-automatic-backup"
    private const val CATCH_UP = "recipai-catch-up-backup"

    private fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun schedule(context: Context, frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean, now: ZonedDateTime) {
        val next = BackupTiming.nextRun(now, frequency, isoWeekday, hour)
        val periodHours = BackupTiming.periodHours(frequency)
        if (next == null || periodHours == null) {
            cancel(context)
            return
        }
        val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<BackupWorker>(periodHours, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        // Re-registering means the schedule changed: start over from the new first run.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
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
        work.cancelUniqueWork(WORK)
        work.cancelUniqueWork(CATCH_UP)
    }
}
