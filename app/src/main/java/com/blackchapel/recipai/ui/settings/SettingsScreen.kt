package com.blackchapel.recipai.ui.settings

import com.blackchapel.recipai.ui.timers.timerPillClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.ThemeMode
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Tab
import com.blackchapel.recipai.ui.AvailableUpdate
import com.blackchapel.recipai.ui.backup.BackupSettingsSection
import com.blackchapel.recipai.ui.cook.CookingSettingsRows
import com.blackchapel.recipai.ui.components.Cell
import com.blackchapel.recipai.ui.components.Group
import com.blackchapel.recipai.ui.components.GroupLabel
import com.blackchapel.recipai.ui.components.Hairline
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.LargeTitle
import com.blackchapel.recipai.ui.components.NavBarHeight
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RSwitch
import com.blackchapel.recipai.ui.components.SegLabel
import com.blackchapel.recipai.ui.components.Segmented
import com.blackchapel.recipai.ui.components.TabBarHeight
import com.blackchapel.recipai.ui.components.TopBar
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

@Composable
fun SettingsScreen(app: AppController, settings: AppSettings, recipes: List<Recipe>, versionName: String) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    val initialTick = remember { app.reselectTick }
    LaunchedEffect(app.reselectTick) { if (app.reselectTick != initialTick && app.tab == Tab.SETTINGS) state.animateScrollToItem(0) }
    val density = LocalDensity.current
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 4 } }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > with(density) { 44.dp.toPx() } } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = top + NavBarHeight, bottom = bottom + TabBarHeight + 28.dp + timerPillClearance(app)),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("title") { LargeTitle("Recipai", "Settings") }
            app.availableUpdate?.let { update -> item("update") { UpdateCard(app, update) } }
            item("backup") { BackupSettingsSection(app) }

            item("appearance") {
                Section("Appearance") {
                    Group {
                        val themes = listOf(Triple(ThemeMode.AUTO, Ico.Monitor, "Auto"), Triple(ThemeMode.LIGHT, Ico.Sun, "Light"), Triple(ThemeMode.DARK, Ico.Moon, "Dark"))
                        Segmented(
                            count = 3,
                            selected = themes.indexOfFirst { it.first == settings.theme },
                            onSelect = { i -> app.updateSettings { it.copy(theme = themes[i].first) } },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        ) { i, _ ->
                            RIcon(themes[i].second, size = 15.dp)
                            SegLabel(themes[i].third)
                        }
                    }
                }
            }

            item("cooking") {
                Section("Cooking") {
                    Group {
                        Cell(
                            "Haptic feedback",
                            icon = Ico.Vibrate,
                            iconColor = c.ube,
                            subtitle = "Gentle taps on swipes, toggles and timers",
                            role = Role.Switch,
                            haptic = null,
                            checked = settings.haptics,
                            onClick = { app.updateSettings { it.copy(haptics = !it.haptics) } },
                        ) { RSwitch(settings.haptics) }
                        CookingSettingsRows(app, settings)
                    }
                }
            }

            item("library") {
                Section("Library") {
                    Group {
                        Cell("Recipes", icon = Ico.Book, iconColor = c.ube) {
                            Text("${recipes.size}", style = RecipaiType.cell, color = c.mute)
                        }
                        Hairline(start = 56.dp)
                        Cell("Photos", icon = Ico.Camera, iconColor = c.ocean) {
                            Text("${recipes.sumOf { it.photos.size }}", style = RecipaiType.cell, color = c.mute)
                        }
                    }
                }
            }

            item("about") {
                Section("About") {
                    Group {
                        Cell(
                            "Welcome tour",
                            icon = Ico.Spark,
                            iconColor = c.saffron,
                            subtitle = "See the introduction to Recipai again",
                            onClick = { app.replayWelcome() },
                        )
                        Hairline(start = 56.dp)
                        Cell("Recipai", icon = Ico.Info, iconColor = c.faint, subtitle = "Works offline. Your recipes stay on this phone, and in your own Google Drive if you back up.")
                        Hairline(start = 56.dp)
                        // Test builds have long names (0.1-branch.sha), so the version gets its own line.
                        Cell("Version", icon = Ico.Tag, iconColor = c.faint, subtitle = versionName)
                    }
                }
            }
        }
        TopBar(title = "Settings", scrolled = scrolled, collapsed = collapsed)
    }
}

/**
 * Shown only while a newer GitHub release exists (the app checks in the
 * background each time it opens). Tapping it opens that release's page in
 * the browser, where the new APK is downloaded and installed over this one;
 * the app's data is kept.
 */
@Composable
private fun UpdateCard(app: AppController, update: AvailableUpdate) {
    val c = LocalColors.current
    val uriHandler = LocalUriHandler.current
    Section("Update") {
        Group {
            Cell(
                "Update available: ${update.name}",
                icon = Ico.Spark,
                iconColor = c.saffron,
                titleColor = c.accentInk,
                subtitle = "Tap to open the release page and download the new APK",
                onClick = {
                    try {
                        uriHandler.openUri(update.url)
                    } catch (e: Exception) {
                        // No browser installed, or it refused the link.
                        app.showToast("Couldn't open the browser", Ico.Info)
                    }
                },
            )
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        GroupLabel(label)
        content()
        Spacer(Modifier.height(8.dp))
    }
}
