package com.blackchapel.recipai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val examplesBanner = booleanPreferencesKey("examples_banner")
    }

    override val settings: Flow<AppSettings> = store.data.map(::read)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.theme] = next.theme.name
            prefs[Keys.haptics] = next.haptics
            prefs[Keys.layout] = next.layout.name
            prefs[Keys.examplesBanner] = next.showExamplesBanner
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = prefs[Keys.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: defaults.theme,
            haptics = prefs[Keys.haptics] ?: defaults.haptics,
            layout = prefs[Keys.layout]?.let { v -> LibraryLayout.entries.firstOrNull { it.name == v } } ?: defaults.layout,
            showExamplesBanner = prefs[Keys.examplesBanner] ?: defaults.showExamplesBanner,
        )
    }
}
