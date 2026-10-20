package com.blackchapel.recipai.data

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class LibraryLayout { GRID, LIST }

/** Automatic Google Drive backups (docs/DESIGN.md, "Backup"). */
enum class BackupSchedule { DAILY, WEEKLY, MANUAL }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val haptics: Boolean = true,
    val layout: LibraryLayout = LibraryLayout.GRID,
    /** Cook mode keeps the screen on. */
    val keepScreenOn: Boolean = true,
    /** A finished timer plays a sound. */
    val timerSound: Boolean = true,
    val backupSchedule: BackupSchedule = BackupSchedule.MANUAL,
    /** ISO day of week (1 = Monday ... 7 = Sunday) for weekly backups. */
    val backupWeekday: Int = 7,
    /** Hour of day (0-23) automatic backups aim for. */
    val backupHour: Int = 2,
    val backupIncludePhotos: Boolean = true,
    val backupWifiOnly: Boolean = true,
    /**
     * The first-run welcome tour. null = not decided yet (the first launch of
     * this version decides: a fresh install shows the tour, an update does
     * not); true = decided, and the tour never shows by itself again. false
     * was written by early test builds ("show it") and is read as seen. Never
     * part of a backup.
     */
    val welcomeSeen: Boolean? = null,
)

/** Where settings live. The app uses DataStore ([DataStoreSettings]). */
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
