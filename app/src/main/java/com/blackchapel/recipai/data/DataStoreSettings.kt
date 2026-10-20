package com.blackchapel.recipai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettings(context: Context) : SettingsStore {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val layout = stringPreferencesKey("layout")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val timerSound = booleanPreferencesKey("timer_sound")
        val backupSchedule = stringPreferencesKey("backup_schedule")
        val backupWeekday = intPreferencesKey("backup_weekday")
        val backupHour = intPreferencesKey("backup_hour")
        val backupIncludePhotos = booleanPreferencesKey("backup_include_photos")
        val backupWifiOnly = booleanPreferencesKey("backup_wifi_only")
        val welcomeSeen = booleanPreferencesKey("welcome_seen")
    }

    override val settings: Flow<AppSettings> = store.data.map(::read)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.theme] = next.theme.name
            prefs[Keys.haptics] = next.haptics
            prefs[Keys.layout] = next.layout.name
            prefs[Keys.keepScreenOn] = next.keepScreenOn
            prefs[Keys.timerSound] = next.timerSound
            prefs[Keys.backupSchedule] = next.backupSchedule.name
            prefs[Keys.backupWeekday] = next.backupWeekday
            prefs[Keys.backupHour] = next.backupHour
            prefs[Keys.backupIncludePhotos] = next.backupIncludePhotos
            prefs[Keys.backupWifiOnly] = next.backupWifiOnly
            next.welcomeSeen?.let { prefs[Keys.welcomeSeen] = it } ?: prefs.remove(Keys.welcomeSeen)
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = prefs[Keys.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: defaults.theme,
            haptics = prefs[Keys.haptics] ?: defaults.haptics,
            layout = prefs[Keys.layout]?.let { v -> LibraryLayout.entries.firstOrNull { it.name == v } } ?: defaults.layout,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            timerSound = prefs[Keys.timerSound] ?: defaults.timerSound,
            backupSchedule = prefs[Keys.backupSchedule]?.let { v -> BackupSchedule.entries.firstOrNull { it.name == v } } ?: defaults.backupSchedule,
            backupWeekday = prefs[Keys.backupWeekday] ?: defaults.backupWeekday,
            backupHour = prefs[Keys.backupHour] ?: defaults.backupHour,
            backupIncludePhotos = prefs[Keys.backupIncludePhotos] ?: defaults.backupIncludePhotos,
            backupWifiOnly = prefs[Keys.backupWifiOnly] ?: defaults.backupWifiOnly,
            welcomeSeen = prefs[Keys.welcomeSeen],
        )
    }
}
