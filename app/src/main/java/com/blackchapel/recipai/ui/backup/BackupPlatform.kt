package com.blackchapel.recipai.ui.backup

import com.blackchapel.recipai.core.BackupFrequency
import com.blackchapel.recipai.core.BackupSettings
import com.blackchapel.recipai.core.BackupStatusStore
import com.blackchapel.recipai.core.HttpTransport
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.BackupSchedule
import com.blackchapel.recipai.data.LibraryLayout
import com.blackchapel.recipai.data.ThemeMode
import java.io.File
import java.time.ZonedDateTime

/** What asking Google for a Drive token came to. */
sealed interface AuthOutcome {
    data class Granted(val token: String) : AuthOutcome

    /**
     * Google wants the user to pick an account or allow access first.
     * [request] is the platform's handle for that screen (a PendingIntent
     * on Android), launched by `BackupConsentHost`.
     */
    class NeedsConsent(val request: Any) : AuthOutcome

    /** The user closed Google's screen. */
    data object Cancelled : AuthOutcome

    /** [message] is user-facing. */
    data class Failed(val message: String) : AuthOutcome
}

/** No usable token without the user (signed out, access revoked, consent needed). */
class BackupAuthRequired(message: String) : Exception(message)

/** The user cancelled Google's sign-in screen: not an error worth a message. */
class BackupAuthCancelled : Exception("Cancelled")

/**
 * The Android side of backups (`platform/backup/AndroidBackupPlatform`):
 * Google authorization, files, background scheduling. Everything else
 * (what to back up, when, progress, status) is shared code.
 */
interface BackupPlatform {
    val status: BackupStatusStore
    val http: HttpTransport

    /** Recipe photos (`filesDir/photos`). */
    val photoDir: File

    /** Scratch space for archives (in the cache directory). */
    val workDir: File
    val appVersion: String

    /** Asks Google for a Drive token without showing anything. */
    suspend fun authorize(): AuthOutcome

    /** Makes Google Play services forget a token Drive refused, so the next [authorize] gets a fresh one. */
    suspend fun clearToken(token: String)

    /** Registers background work for automatic backups (replacing any before). */
    fun schedule(frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean, now: ZonedDateTime)

    /** Cancels automatic backups, including a queued catch-up ([runSoon]). */
    fun cancelSchedule()

    /**
     * Queues one automatic backup to run as soon as the OS allows (a network,
     * Wi-Fi with [wifiOnly]): an overdue schedule caught up when the app opens.
     * A catch-up already queued is kept.
     */
    fun runSoon(wifiOnly: Boolean)

    /** The recipes as last saved (the background worker reads them without the app's repository). */
    suspend fun savedRecipes(): List<Recipe>?

    /** The settings as stored. */
    suspend fun savedSettings(): AppSettings
}

fun BackupSchedule.toFrequency(): BackupFrequency = when (this) {
    BackupSchedule.DAILY -> BackupFrequency.DAILY
    BackupSchedule.WEEKLY -> BackupFrequency.WEEKLY
    BackupSchedule.MANUAL -> BackupFrequency.MANUAL
}

/** The settings a backup carries. */
fun AppSettings.toBackup(): BackupSettings = BackupSettings(
    theme = theme.name,
    haptics = haptics,
    layout = layout.name,
    keepScreenOn = keepScreenOn,
    timerSound = timerSound,
    backupSchedule = backupSchedule.name,
    backupWeekday = backupWeekday,
    backupHour = backupHour,
    backupIncludePhotos = backupIncludePhotos,
    backupWifiOnly = backupWifiOnly,
)

/**
 * These settings with a backup's applied: appearance and cooking come
 * back; the backup options stay as they are on this phone (they belong to
 * this phone's connection, and the user is on the Backup page setting them).
 * Values this version doesn't know are skipped.
 */
fun AppSettings.restoredFrom(b: BackupSettings): AppSettings = copy(
    theme = b.theme?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: theme,
    haptics = b.haptics ?: haptics,
    layout = b.layout?.let { v -> LibraryLayout.entries.firstOrNull { it.name == v } } ?: layout,
    keepScreenOn = b.keepScreenOn ?: keepScreenOn,
    timerSound = b.timerSound ?: timerSound,
)
