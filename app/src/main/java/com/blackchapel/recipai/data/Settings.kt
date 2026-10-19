package com.blackchapel.recipai.data

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class LibraryLayout { GRID, LIST }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val haptics: Boolean = true,
    val layout: LibraryLayout = LibraryLayout.GRID,
    /** The "These are example recipes" banner on Recipes. */
    val showExamplesBanner: Boolean = true,
)

/** Where settings live. The app uses DataStore ([DataStoreSettings]). */
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
