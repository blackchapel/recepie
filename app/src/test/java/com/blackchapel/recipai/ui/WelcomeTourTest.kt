package com.blackchapel.recipai.ui

import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.RecipeRepository
import com.blackchapel.recipai.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The first-run tour: shown on a fresh install only, never on an update. */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeTourTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MemorySettings(initial: AppSettings = AppSettings()) : SettingsStore {
        override val settings = MutableStateFlow(initial)
        override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
    }

    private fun TestScope.app(store: MemorySettings): Pair<AppController, CoroutineScope> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = RecipeRepository(File(tmp.root, "recipes.json"), this, dispatcher, clock = { 1_000L })
        advanceUntilIdle()
        val scope = CoroutineScope(dispatcher + Job())
        return AppController(repo, store, scope) to scope
    }

    @Test
    fun nothingIsDecidedUntilTheInstallIsChecked() = runTest {
        val store = MemorySettings()
        val (_, scope) = app(store)
        assertNull(store.settings.value.welcomeSeen)
        scope.cancel()
    }

    @Test
    fun aFreshInstallShowsTheTour() = runTest {
        val store = MemorySettings()
        val (app, scope) = app(store)
        app.decideWelcome(freshInstall = true)
        assertEquals(false, store.settings.value.welcomeSeen)
        scope.cancel()
    }

    @Test
    fun anUpdateNeverShowsTheTour() = runTest {
        val store = MemorySettings()
        val (app, scope) = app(store)
        app.decideWelcome(freshInstall = false)
        assertEquals(true, store.settings.value.welcomeSeen)
        scope.cancel()
    }

    @Test
    fun theDecisionIsMadeOnlyOnce() = runTest {
        val store = MemorySettings()
        val (app, scope) = app(store)
        app.decideWelcome(freshInstall = true)
        app.decideWelcome(freshInstall = false) // a later launch looks like an update
        assertEquals(false, store.settings.value.welcomeSeen)
        app.finishWelcome()
        advanceUntilIdle()
        app.decideWelcome(freshInstall = true)
        assertEquals(true, store.settings.value.welcomeSeen)
        scope.cancel()
    }

    @Test
    fun finishingAndReplayingTheTour() = runTest {
        val store = MemorySettings(AppSettings(welcomeSeen = false))
        val (app, scope) = app(store)
        app.finishWelcome()
        advanceUntilIdle()
        assertEquals(true, store.settings.value.welcomeSeen)
        app.replayWelcome()
        advanceUntilIdle()
        assertEquals(false, store.settings.value.welcomeSeen)
        scope.cancel()
    }
}
