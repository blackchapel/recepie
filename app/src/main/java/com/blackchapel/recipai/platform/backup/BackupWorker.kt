package com.blackchapel.recipai.platform.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.blackchapel.recipai.core.BackupKind
import com.blackchapel.recipai.core.DriveException
import com.blackchapel.recipai.data.BackupSchedule
import com.blackchapel.recipai.ui.backup.BackupAuthRequired
import com.blackchapel.recipai.ui.backup.BackupInput
import com.blackchapel.recipai.ui.backup.toBackup
import com.blackchapel.recipai.ui.backup.toFrequency
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime

/**
 * An automatic backup (scheduled by [BackupScheduler], which each run
 * extends by queueing the next one). Reads the saved
 * recipes and settings, signs in silently and backs up through the same
 * [BackupRuntime] jobs as the app. Skips the upload when nothing changed
 * since the last backup. A sign-in that needs the user marks the status
 * "sign in again" instead of retrying.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jobs = BackupRuntime.jobs(applicationContext)
        val platform = jobs.platform
        if (!jobs.status.value.connected) {
            platform.cancelSchedule()
            return Result.success()
        }
        val settings = platform.savedSettings()
        if (settings.backupSchedule == BackupSchedule.MANUAL) return Result.success()
        queueNextRun(settings)
        val recipes = platform.savedRecipes() ?: return Result.success()
        return try {
            jobs.backUp(
                BackupInput(recipes, settings.toBackup(), settings.backupIncludePhotos),
                BackupKind.AUTOMATIC,
                jobs.silentTokens,
                skipIfUnchanged = true,
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupAuthRequired) {
            Result.failure()
        } catch (e: DriveException) {
            val transient = e.code == 0 || e.code == 429 || e.code >= 500
            if (transient && runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    /**
     * Queues the next run first, before any backup work, so one failed or
     * retried backup never ends the chain. Counted from this run's target
     * time (or now, if later), so a late run doesn't move the next one.
     */
    private fun queueNextRun(settings: com.blackchapel.recipai.data.AppSettings) {
        val now = ZonedDateTime.now()
        val target = inputData.getLong(BackupScheduler.KEY_TARGET, 0L)
        val base = if (target > now.toEpochSecond()) ZonedDateTime.ofInstant(Instant.ofEpochSecond(target), now.zone) else now
        BackupScheduler.scheduleNext(
            applicationContext, settings.backupSchedule.toFrequency(), settings.backupWeekday,
            settings.backupHour, settings.backupWifiOnly, base,
        )
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
