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
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * An automatic backup (scheduled by [BackupScheduler]). Reads the saved
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

    private companion object {
        const val MAX_RETRIES = 3
    }
}
