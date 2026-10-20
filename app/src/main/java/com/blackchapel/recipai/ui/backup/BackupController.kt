package com.blackchapel.recipai.ui.backup

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackchapel.recipai.core.BackupEngine
import com.blackchapel.recipai.core.BackupEntry
import com.blackchapel.recipai.core.BackupFingerprint
import com.blackchapel.recipai.core.BackupKind
import com.blackchapel.recipai.core.BackupStatus
import com.blackchapel.recipai.core.BackupText
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.BackupSchedule
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.BackupPage
import com.blackchapel.recipai.ui.components.Ico
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Google's sign-in screen, waiting to be shown by `BackupConsentHost`. */
class ConsentRequest(val id: Long, val payload: Any) {
    /** Set once launched, so a rotation doesn't open it twice. */
    var launched = false
}

/** A haptic for the UI to play (the controller has no View). */
class HapticEvent(val type: HapticType, val id: Long = System.nanoTime())

/** What a backup of the current recipes would hold, for "12 recipes · 3 photos · about 1.2 MB". */
data class BackupEstimate(val recipes: Int, val photos: Int, val bytes: Long)

/**
 * The Backup page's state and actions, owned by [AppController] (so it
 * survives rotation). The work itself runs in the process-wide
 * [BackupJobs], attached by the activity's ViewModel; until then (and in
 * the desktop compile check) backup reports itself unavailable.
 */
@Stable
class BackupController(private val app: AppController, private val scope: CoroutineScope) {
    var jobs by mutableStateOf<BackupJobs?>(null)
        private set
    var status by mutableStateOf(BackupStatus())
        private set
    var run by mutableStateOf<BackupRun?>(null)
        private set

    /** Waiting for Google sign-in (the "Waiting for Google sign-in…" state). */
    var connecting by mutableStateOf(false)
        private set
    var connectError by mutableStateOf<String?>(null)
        private set

    /** Between a tap and the run starting (getting a token), so a second tap does nothing. */
    private var starting by mutableStateOf(false)

    var listing by mutableStateOf(false)
        private set
    var listError by mutableStateOf<String?>(null)
        private set

    /** Recipes changed since the last backup. */
    var unsaved by mutableStateOf(false)
        private set
    private var fingerprint: String? = null

    var consent by mutableStateOf<ConsentRequest?>(null)
        private set
    private var consentWaiter: CompletableDeferred<AuthOutcome>? = null
    private var consentIds = 0L

    var haptic by mutableStateOf<HapticEvent?>(null)
        private set

    private var connectJob: Job? = null

    /** A backup or restore is running or about to. */
    val working: Boolean get() = run != null || starting

    fun attach(jobs: BackupJobs) {
        if (this.jobs != null) return
        this.jobs = jobs
        status = jobs.status.value
        scope.launch {
            jobs.status.collect {
                status = it
                refreshUnsaved()
            }
        }
        scope.launch { jobs.run.collect { run = it } }
        scope.launch {
            // The photos option is part of the fingerprint: turning it on is a change to back up.
            val includePhotos = app.settings.map { it.backupIncludePhotos }.distinctUntilChanged()
            combine(app.repo.recipes, includePhotos) { recipes, photos -> recipes to photos }
                .collectLatest { (recipes, photos) ->
                    delay(300)
                    fingerprint = withContext(Dispatchers.Default) { BackupFingerprint.of(recipes, photos) }
                    refreshUnsaved()
                }
        }
        // Keep the background schedule in step with the settings and the connection.
        // The stored settings are read each time, so the defaults shown before
        // DataStore loads never cancel a real schedule.
        scope.launch {
            combine(app.settings, jobs.status.map { it.connected }.distinctUntilChanged()) { _, _ -> }
                .collect {
                    try {
                        jobs.reschedule(jobs.platform.savedSettings())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Scheduling is retried on the next settings change or launch.
                    }
                }
        }
        // An automatic backup the OS held back for too long runs soon after the app opens.
        scope.launch {
            try {
                jobs.catchUpIfOverdue(jobs.platform.savedSettings())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The regular schedule still runs; the next launch checks again.
            }
        }
    }

    private fun refreshUnsaved() {
        val s = status
        unsaved = s.connected && s.lastBackupAt != null && fingerprint != null && fingerprint != s.lastFingerprint
    }

    private fun feel(type: HapticType) {
        haptic = HapticEvent(type)
    }

    /** `BackupHost` played [event]; it won't play again after a rotation. */
    fun hapticPlayed(event: HapticEvent) {
        if (haptic === event) haptic = null
    }

    private fun unavailable() {
        app.showToast("Backup isn't available in this build", Ico.Info)
    }

    private fun input(recipes: List<Recipe> = app.repo.recipes.value, includePhotos: Boolean = app.settings.value.backupIncludePhotos) =
        BackupInput(recipes, app.settings.value.toBackup(), includePhotos)

    /** Silent sign-in first; Google's screen only when it asks for the user. */
    private val uiTokens = TokenSource {
        val jobs = jobs ?: throw BackupAuthRequired("Backup isn't available in this build.")
        when (val o = jobs.platform.authorize()) {
            is AuthOutcome.Granted -> o.token
            is AuthOutcome.NeedsConsent -> awaitConsent(o.request)
            is AuthOutcome.Failed -> throw BackupAuthRequired(o.message)
            AuthOutcome.Cancelled -> throw BackupAuthCancelled()
        }
    }

    private suspend fun awaitConsent(request: Any): String {
        val waiter = CompletableDeferred<AuthOutcome>()
        consentWaiter?.cancel()
        consentWaiter = waiter
        consent = ConsentRequest(++consentIds, request)
        try {
            return when (val o = waiter.await()) {
                is AuthOutcome.Granted -> o.token
                is AuthOutcome.Failed -> throw BackupAuthRequired(o.message)
                else -> throw BackupAuthCancelled()
            }
        } finally {
            if (consentWaiter === waiter) {
                consentWaiter = null
                consent = null
            }
        }
    }

    /** `BackupConsentHost` reports what the user did on Google's screen. */
    fun consentFinished(outcome: AuthOutcome) {
        val waiter = consentWaiter
        consent = null
        if (waiter != null) {
            waiter.complete(outcome)
        } else if (outcome is AuthOutcome.Granted && !status.connected) {
            // The app restarted while Google's screen was open: finish connecting.
            connectWith { outcome.token }
        }
    }

    fun connect() {
        if (jobs == null) return unavailable()
        if (connecting) return
        connectWith { uiTokens.token() }
    }

    private fun connectWith(token: suspend () -> String) {
        val jobs = jobs ?: return
        connectError = null
        connecting = true
        connectJob = scope.launch {
            try {
                jobs.connect(token())
                feel(HapticType.SUCCESS)
                app.showToast("Google Drive connected", Ico.Check)
                refresh()
            } catch (e: BackupAuthCancelled) {
                // Closed Google's screen: back to the explainer, no message.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connectError = BackupJobs.messageFor(e)
                feel(HapticType.ERROR)
            } finally {
                connecting = false
            }
        }
    }

    /** "Cancel" while waiting for Google sign-in. */
    fun cancelConnect() {
        connectJob?.cancel()
        consentWaiter?.cancel()
        consentWaiter = null
        consent = null
        connecting = false
    }

    fun disconnect() {
        val jobs = jobs ?: return unavailable()
        scope.launch {
            try {
                jobs.disconnect()
                listError = null
                app.showToast("Google Drive disconnected. Your backups are still in Drive.", Ico.Cloud)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.showToast(BackupJobs.messageFor(e), Ico.Info)
            }
        }
    }

    /** Settings and Search "Back up now": backs up, or opens the Backup page to connect first. */
    fun backUpNow() {
        val jobs = jobs ?: return unavailable()
        if (!status.connected) {
            // Already on the Backup page: its Connect button is right there.
            if (app.stack.lastOrNull() !is BackupPage) app.openBackup()
            return
        }
        if (working) return
        starting = true
        scope.launch {
            try {
                val entry = jobs.backUp(input(), BackupKind.MANUAL, uiTokens)
                feel(HapticType.SUCCESS)
                val n = entry?.recipes ?: app.repo.recipes.value.size
                app.showToast("Backed up ${BackupText.plural(n, "recipe")} to Google Drive", Ico.Cloud)
            } catch (e: BackupAuthCancelled) {
                // No message.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feel(HapticType.ERROR)
                app.showToast(BackupJobs.messageFor(e), Ico.Info)
            } finally {
                starting = false
            }
        }
    }

    /** Re-lists the backups in Drive (silently: a sign-in problem shows as "Sign in again"). */
    fun refresh() {
        val jobs = jobs ?: return
        if (!status.connected || listing) return
        listing = true
        listError = null
        scope.launch {
            try {
                jobs.list(jobs.silentTokens)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupAuthRequired) {
                // Shown as the "Sign in again" banner.
            } catch (e: Exception) {
                listError = BackupJobs.messageFor(e)
            } finally {
                listing = false
            }
        }
    }

    /**
     * Restores [entry] after a "Before restore" backup of what is here now,
     * then puts its recipes and settings in place.
     */
    fun restore(entry: BackupEntry) {
        val jobs = jobs ?: return unavailable()
        if (working) return
        starting = true
        scope.launch {
            try {
                // Read what is here once the download is done, not now: the user
                // may edit recipes while it runs (see BackupJobs.restore).
                val restored = jobs.restore(entry, { input(includePhotos = true) }, uiTokens)
                app.repo.replaceAll(restored.recipes)
                app.updateSettings { it.restoredFrom(restored.snapshot.settings) }
                app.libraryTag = TagFilter.All
                app.libraryQuery = ""
                feel(HapticType.SUCCESS)
                val missing = restored.missingPhotos
                app.showToast(
                    if (missing == 0) "Backup restored" else "Backup restored without ${BackupText.plural(missing, "photo")}",
                    Ico.Check,
                )
                refresh()
            } catch (e: BackupAuthCancelled) {
                // No message.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feel(HapticType.ERROR)
                app.showToast(BackupJobs.messageFor(e), Ico.Info)
            } finally {
                starting = false
            }
        }
    }

    /** Size and counts of a backup of [recipes] now (reads photo sizes, so off the main thread). */
    suspend fun estimate(recipes: List<Recipe>, settings: AppSettings): BackupEstimate {
        val photoDir = jobs?.platform?.photoDir ?: return BackupEstimate(recipes.size, 0, 0)
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = BackupEngine.snapshot(recipes, settings.toBackup(), BackupKind.MANUAL, settings.backupIncludePhotos, "", 0, photoDir)
                BackupEstimate(recipes.size, snapshot.photos.size, BackupEngine.estimateBytes(snapshot))
            } catch (e: Exception) {
                BackupEstimate(recipes.size, 0, 0)
            }
        }
    }

    fun setSchedule(schedule: BackupSchedule) = app.updateSettings { it.copy(backupSchedule = schedule) }

    fun setWeekday(isoDay: Int) = app.updateSettings { it.copy(backupWeekday = isoDay) }

    fun setHour(hour: Int) = app.updateSettings { it.copy(backupHour = hour) }

    fun toggleIncludePhotos() = app.updateSettings { it.copy(backupIncludePhotos = !it.backupIncludePhotos) }

    fun toggleWifiOnly() = app.updateSettings { it.copy(backupWifiOnly = !it.backupWifiOnly) }
}
