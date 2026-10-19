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
    /** The "These are example recipes" banner on Recipes. */
    val showExamplesBanner: Boolean = true,
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
)

/** Where settings live. The app uses DataStore ([DataStoreSettings]). */
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
