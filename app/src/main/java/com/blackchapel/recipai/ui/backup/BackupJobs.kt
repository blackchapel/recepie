package com.blackchapel.recipai.ui.backup

import com.blackchapel.recipai.core.BackupEngine
import com.blackchapel.recipai.core.BackupEntry
import com.blackchapel.recipai.core.BackupFingerprint
import com.blackchapel.recipai.core.BackupFormatException
import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupKind
import com.blackchapel.recipai.core.BackupProgress
import com.blackchapel.recipai.core.BackupSettings
import com.blackchapel.recipai.core.BackupStatus
import com.blackchapel.recipai.core.BackupTiming
import com.blackchapel.recipai.core.DriveAuthException
import com.blackchapel.recipai.core.DriveClient
import com.blackchapel.recipai.core.DriveException
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.RestoredBackup
import com.blackchapel.recipai.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.ZonedDateTime

/** A backup or restore in progress, for the progress bar and the Settings row. */
data class BackupRun(val label: String, val fraction: Float, val restoring: Boolean = false)

/** What to back up. */
class BackupInput(val recipes: List<Recipe>, val settings: BackupSettings, val includePhotos: Boolean)

/** Gets a Drive access token; throws [BackupAuthRequired] or [BackupAuthCancelled] when there is none. */
fun interface TokenSource {
    suspend fun token(): String
}

/**
 * Backups for the whole process: the Backup page and the background
 * worker both go through one instance (see `BackupRuntime`), so only one
 * backup or restore runs at a time and both see the same progress and
 * status. Drive work runs on the IO dispatcher and stops when cancelled.
 */
class BackupJobs(
    val platform: BackupPlatform,
    private val clock: () -> Long = System::currentTimeMillis,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private val mutex = Mutex()

    private val _run = MutableStateFlow<BackupRun?>(null)
    val run: StateFlow<BackupRun?> = _run.asStateFlow()

    private val _status = MutableStateFlow(platform.status.read())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    init {
        platform.status.listen { _status.value = it }
    }

    private fun update(transform: (BackupStatus) -> BackupStatus) = platform.status.update(transform)

    private fun progress(base: Float, span: Float, restoring: Boolean, label: String? = null): (BackupProgress) -> Unit = { p ->
        val next = BackupRun(label ?: p.label, (base + span * p.fraction).coerceIn(0f, 1f), restoring)
        val prev = _run.value
        // Throttle: a new label, or at least a percent of movement.
        if (prev == null || prev.label != next.label || next.fraction - prev.fraction >= 0.01f || next.fraction >= 1f) _run.value = next
    }

    /**
     * Runs [block] with a Drive client. A refused token is cleared and
     * replaced once; refused again means the user must reconnect.
     */
    private suspend fun <T> withDrive(tokens: TokenSource, block: (BackupEngine) -> T): T {
        var token = tokens.token()
        var retried = false
        while (true) {
            val engine = BackupEngine(DriveClient(platform.http, token), platform.workDir, platform.photoDir)
            try {
                return runInterruptible(Dispatchers.IO) { block(engine) }
            } catch (e: DriveAuthException) {
                platform.clearToken(token)
                if (retried) throw BackupAuthRequired("Google Drive needs you to sign in again.")
                retried = true
                token = tokens.token()
            }
        }
    }

    /** The backups folder, found once and remembered. */
    private fun folderOf(engine: BackupEngine): String {
        val id = engine.folder(_status.value.folderId)
        if (id != _status.value.folderId) update { it.copy(folderId = id) }
        return id
    }

    /** Remembers a failure for the Backup page (and an auth failure as "reconnect"). */
    private fun recordFailure(e: Throwable) {
        if (e is CancellationException || e is BackupAuthCancelled) return
        update {
            it.copy(
                lastError = messageFor(e),
                lastErrorAt = clock(),
                needsReconnect = it.needsReconnect || e is BackupAuthRequired,
            )
        }
    }

    /** Connects with a freshly granted [token]: reads the account and finds (or makes) the folder. */
    suspend fun connect(token: String): BackupStatus = mutex.withLock {
        withDrive(TokenSource { token }) { engine ->
            engine.account() to engine.folder(_status.value.folderId)
        }.let { (user, folder) ->
            update {
                it.copy(
                    connected = true,
                    accountName = user.displayName,
                    accountEmail = user.emailAddress,
                    folderId = folder,
                    needsReconnect = false,
                    lastError = null,
                    lastErrorAt = null,
                    scheduled = null,
                )
            }
        }
    }

    /**
     * Forgets the connection. Best effort, it also revokes Recipai's
     * access at Google (when a token is at hand without asking) so the next
     * Connect asks again. Backups already in Drive stay there.
     */
    suspend fun disconnect() {
        mutex.withLock {
            val outcome = try {
                platform.authorize()
            } catch (e: Exception) {
                null
            }
            if (outcome is AuthOutcome.Granted) {
                try {
                    runInterruptible(Dispatchers.IO) { DriveClient.revoke(platform.http, outcome.token) }
                } catch (e: IOException) {
                    // Offline: access stays granted at Google; the user can remove it in their account.
                }
                platform.clearToken(outcome.token)
            }
            platform.cancelSchedule()
            // Another account may connect next: nothing about this one carries over.
            update { BackupStatus(scheduled = BackupTiming.signature(BackupFrequency.MANUAL, 7, 2, false)) }
        }
    }

    /** The backups in Drive, newest first (also kept in the status for next time). */
    suspend fun list(tokens: TokenSource): List<BackupEntry> = try {
        val list = withDrive(tokens) { engine -> engine.list(folderOf(engine)) }
        update { it.copy(history = list, needsReconnect = false) }
        list
    } catch (e: BackupAuthRequired) {
        update { it.copy(needsReconnect = true) }
        throw e
    }

    /**
     * Makes a backup. With [skipIfUnchanged] (automatic backups), nothing
     * is uploaded when the recipes are exactly as in the last backup.
     * Returns the new backup, or null when skipped.
     */
    suspend fun backUp(
        input: BackupInput,
        kind: String,
        tokens: TokenSource,
        skipIfUnchanged: Boolean = false,
    ): BackupEntry? = mutex.withLock {
        val fingerprint = BackupFingerprint.of(input.recipes, input.includePhotos)
        val status = _status.value
        if (skipIfUnchanged && status.lastBackupAt != null && status.lastFingerprint == fingerprint) return@withLock null
        _run.value = BackupRun("Preparing your recipes", 0f)
        try {
            val entry = withDrive(tokens) { engine ->
                val snapshot = BackupEngine.snapshot(input.recipes, input.settings, kind, input.includePhotos, platform.appVersion, clock(), platform.photoDir)
                engine.backUp(folderOf(engine), snapshot, onProgress = progress(0f, 1f, restoring = false))
            }
            recordBackup(entry, fingerprint)
            entry
        } catch (e: Throwable) {
            recordFailure(e)
            throw e
        } finally {
            _run.value = null
        }
    }

    private fun recordBackup(entry: BackupEntry, fingerprint: String) {
        update {
            it.copy(
                lastBackupAt = entry.createdAt,
                lastBackupBytes = entry.bytes,
                lastFingerprint = fingerprint,
                lastError = null,
                lastErrorAt = null,
                needsReconnect = false,
                history = (listOf(entry) + it.history.filter { h -> h.id != entry.id }).sortedByDescending { h -> h.createdAt }.take(BackupEngine.KEEP),
            )
        }
    }

    /**
     * Restores [entry]: downloads and checks it first, then backs up what
     * is here now ("Before restore", always with photos, so it can be
     * undone), then copies the photos out. The caller puts the returned
     * recipes and settings in place.
     *
     * [current] is asked for what is here only after the download, because
     * a big download takes minutes and the app stays usable meanwhile:
     * recipes edited during it must be in the "Before restore" backup, or
     * restoring would discard them for good. The backup being restored is
     * kept in Drive whatever the "Before restore" backup would push out, so
     * a restore that then fails does not cost the user their backup.
     */
    suspend fun restore(entry: BackupEntry, current: suspend () -> BackupInput, tokens: TokenSource): RestoredBackup = mutex.withLock {
        _run.value = BackupRun("Downloading the backup", 0f, restoring = true)
        try {
            val (archive, _) = withDrive(tokens) { engine -> engine.download(entry, progress(0f, 0.35f, restoring = true)) }
            // This phone's Include photos option: a restore leaves it as it was.
            var phonePhotos: Boolean? = null
            val restored = try {
                val input = current()
                phonePhotos = input.settings.backupIncludePhotos
                withDrive(tokens) { engine ->
                    val folder = folderOf(engine)
                    val fingerprint = BackupFingerprint.of(input.recipes, includePhotos = true)
                    val snapshot = BackupEngine.snapshot(
                        input.recipes, input.settings, BackupKind.BEFORE_RESTORE, includePhotos = true,
                        appVersion = platform.appVersion, createdAt = clock(), photoDir = platform.photoDir,
                    )
                    val before = engine.backUp(
                        folder, snapshot,
                        protect = setOf(entry.id),
                        onProgress = progress(0.35f, 0.5f, restoring = true, label = "Saving what you have now"),
                    )
                    recordBackup(before, fingerprint)
                    engine.restore(archive, progress(0.85f, 0.15f, restoring = true))
                }
            } finally {
                archive.delete()
            }
            // What is on the phone now matches a backup in Drive (the one restored).
            // The fingerprint follows this phone's option (what later backups and the
            // unsaved-changes check use), not the restored backup's.
            val photos = phonePhotos ?: restored.snapshot.includesPhotos
            update { it.copy(lastFingerprint = BackupFingerprint.of(restored.recipes, photos), lastError = null, lastErrorAt = null) }
            restored
        } catch (e: Throwable) {
            recordFailure(e)
            throw e
        } finally {
            _run.value = null
        }
    }

    /**
     * When the app opens and a scheduled backup is overdue (WorkManager held
     * it back, e.g. under Doze), queues one now, as the prototype does. Only
     * once there is a last backup to be overdue from; the background run
     * skips the upload when nothing changed. Returns true when queued.
     */
    fun catchUpIfOverdue(settings: AppSettings): Boolean {
        val status = _status.value
        if (!status.connected || status.needsReconnect) return false
        val last = status.lastBackupAt ?: return false
        val period = BackupTiming.periodHours(settings.backupSchedule.toFrequency()) ?: return false
        // A little slack, so a run that is merely due now is left to the schedule.
        val overdueAfter = (period + OVERDUE_SLACK_HOURS) * 3_600_000L
        if (clock() - last < overdueAfter) return false
        platform.runSoon(settings.backupWifiOnly)
        return true
    }

    /**
     * Registers background work for [settings] when the schedule changed
     * (or cancels it when disconnected or manual).
     */
    fun reschedule(settings: AppSettings) {
        val status = _status.value
        val frequency = if (status.connected) settings.backupSchedule.toFrequency() else BackupFrequency.MANUAL
        val signature = BackupTiming.signature(frequency, settings.backupWeekday, settings.backupHour, settings.backupWifiOnly)
        if (signature == status.scheduled) return
        if (frequency == BackupFrequency.MANUAL) {
            platform.cancelSchedule()
        } else {
            platform.schedule(frequency, settings.backupWeekday, settings.backupHour, settings.backupWifiOnly, now())
        }
        update { it.copy(scheduled = signature) }
    }

    /** Silent sign-in only (the background worker): consent needed means "reconnect". */
    val silentTokens = TokenSource {
        when (val o = platform.authorize()) {
            is AuthOutcome.Granted -> o.token
            is AuthOutcome.Failed -> throw BackupAuthRequired(o.message)
            else -> {
                update { it.copy(needsReconnect = true) }
                throw BackupAuthRequired("Google Drive needs you to sign in again.")
            }
        }
    }

    companion object {
        private const val OVERDUE_SLACK_HOURS = 2L

        /** A user-facing message for any failure. */
        fun messageFor(e: Throwable): String = when (e) {
            is DriveException -> e.message ?: "Google Drive had a problem. Try again."
            is BackupFormatException -> e.message ?: "That backup couldn't be read."
            is BackupAuthRequired -> e.message ?: "Google Drive needs you to sign in again."
            is IOException -> "Couldn't reach Google Drive. Check your connection and try again."
            else -> "Something went wrong. Try again."
        }
    }
}
