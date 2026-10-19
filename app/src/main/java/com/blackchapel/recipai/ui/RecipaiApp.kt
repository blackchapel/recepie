package com.blackchapel.recipai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.components.ActionSheet
import com.blackchapel.recipai.ui.components.ConfirmDeleteSheet
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.NewRecipeFab
import com.blackchapel.recipai.ui.components.SheetAction
import com.blackchapel.recipai.ui.components.TabBar
import com.blackchapel.recipai.ui.components.TabBarHeight
import com.blackchapel.recipai.ui.components.ToastHost
import com.blackchapel.recipai.ui.detail.AmountSheet
import com.blackchapel.recipai.ui.backup.BackupScreen
import com.blackchapel.recipai.ui.cook.CookScreen
import com.blackchapel.recipai.ui.detail.DetailScreen
import com.blackchapel.recipai.ui.editor.EditorScreen
import com.blackchapel.recipai.ui.photos.PhotoHost
import com.blackchapel.recipai.ui.photos.PhotoViewer
import com.blackchapel.recipai.ui.timers.TimerOverlay
import com.blackchapel.recipai.ui.timers.TimersSheet
import com.blackchapel.recipai.ui.importer.ImportSheet
import com.blackchapel.recipai.ui.library.LibraryScreen
import com.blackchapel.recipai.ui.search.SearchScreen
import com.blackchapel.recipai.ui.settings.SettingsScreen
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiTheme
import com.blackchapel.recipai.ui.theme.isDarkTheme

/** iOS-like navigation easing from the prototype (--ease-nav). */
private val NavEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * The whole app: four tab roots, pages pushed over them (recipe detail),
 * the tab bar and + button, toasts, and at most one bottom sheet.
 */
@Composable
fun RecipaiApp(app: AppController, versionName: String) {
    val settings by app.settings.collectAsState()
    val recipes by app.repo.recipes.collectAsState()
    val loaded by app.repo.loaded.collectAsState()
    val loadError by app.repo.loadError.collectAsState()
    val saveError by app.repo.saveError.collectAsState()
    val dark = isDarkTheme(settings.theme)

    RecipaiTheme(dark) {
        val c = LocalColors.current
        val top = app.stack.lastOrNull()
        val pushed = top != null
        SystemBarIcons(lightIcons = dark || (top is DetailPage && app.heroUnderStatusBar) || app.sheet is Sheet.PhotoViewer)
        SystemBackHandler(enabled = pushed) { app.back() }
        LaunchedEffect(loadError) { loadError?.let { app.showToast(it, Ico.Info) } }
        LaunchedEffect(saveError) { saveError?.let { app.showToast(it, Ico.Info) } }

        Box(Modifier.fillMaxSize().background(c.canvas)) {
            if (loaded) {
                // Tab roots. The current one slides back a little under a pushed page.
                val under by animateFloatAsState(if (pushed) 1f else 0f, tween(450, easing = NavEasing), label = "under")
                val saveable = rememberSaveableStateHolder()
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -0.28f * size.width * under },
                ) {
                    Crossfade(app.tab, animationSpec = tween(250), label = "tab") { tab ->
                        saveable.SaveableStateProvider(tab.name) {
                            when (tab) {
                                Tab.RECIPES -> LibraryScreen(app, favorites = false, recipes = recipes, settings = settings)
                                Tab.FAVORITES -> LibraryScreen(app, favorites = true, recipes = recipes, settings = settings)
                                Tab.SEARCH -> SearchScreen(app, recipes)
                                Tab.SETTINGS -> SettingsScreen(app, settings, recipes, versionName)
                            }
                        }
                    }
                }
                if (under > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f * under)))

                // Pushed pages slide in from the right and back out on pop.
                AnimatedContent(
                    targetState = top,
                    transitionSpec = {
                        // Runs after the stack changed: a page that is no longer in it was popped.
                        val isPop = initialState != null && initialState !in app.stack
                        // The editor and cook mode are modal: they slide up and down; other pages slide sideways.
                        val moving = if (isPop) initialState else targetState
                        val modal = moving is EditorPage || moving is CookPage
                        val transform = if (isPop) {
                            EnterTransition.None togetherWith
                                if (modal) slideOutVertically(tween(400, easing = NavEasing)) { it }
                                else slideOutHorizontally(tween(400, easing = NavEasing)) { it }
                        } else {
                            (
                                if (modal) slideInVertically(tween(450, easing = NavEasing)) { it }
                                else slideInHorizontally(tween(450, easing = NavEasing)) { it }
                                ) togetherWith ExitTransition.KeepUntilTransitionsFinished
                        }
                        // Deeper pages draw on top, so a popped page slides out over the one below.
                        transform.apply { targetContentZIndex = app.stack.size.toFloat() }
                    },
                    label = "pages",
                ) { page ->
                    when (page) {
                        is DetailPage -> DetailScreen(app, page, recipes.firstOrNull { it.id == page.recipeId })
                        is EditorPage -> EditorScreen(app, page, recipes)
                        is CookPage -> CookScreen(app, page, recipes.firstOrNull { it.id == page.recipeId })
                        is BackupPage -> BackupScreen(app, page)
                        null -> Box(Modifier.fillMaxSize())
                    }
                }

                AnimatedVisibility(
                    visible = !pushed,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(450, easing = NavEasing)) { it },
                    exit = slideOutVertically(tween(450, easing = NavEasing)) { it },
                ) {
                    TabBar(app, onImport = { app.sheet = Sheet.Import() })
                }
                NewRecipeFab(
                    visible = !pushed && (app.tab == Tab.RECIPES || app.tab == Tab.FAVORITES),
                    onClick = { app.newRecipe() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 18.dp, bottom = TabBarHeight + 16.dp),
                )
            }
            if (loaded) TimerOverlay(app, Modifier.align(Alignment.BottomCenter))
            PhotoHost(app)
            ToastHost(app, Modifier.align(Alignment.TopCenter))
            SheetHost(app, recipes)
        }
    }
}

@Composable
private fun SheetHost(app: AppController, recipes: List<Recipe>) {
    val haptics = LocalHaptics.current
    when (val sheet = app.sheet) {
        null -> Unit
        is Sheet.Import -> ImportSheet(app, sheet)
        is Sheet.Amount -> {
            val recipe = recipes.firstOrNull { it.id == sheet.page.recipeId }
            if (recipe == null) LaunchedEffect(sheet) { app.sheet = null }
            else AmountSheet(sheet, recipe) { app.sheet = null }
        }
        is Sheet.Actions -> {
            val r = recipes.firstOrNull { it.id == sheet.recipeId }
            if (r == null) {
                LaunchedEffect(sheet) { app.sheet = null }
            } else {
                val actions = buildList {
                    if (sheet.preview) {
                        add(SheetAction("Open recipe", Ico.Book) { app.openRecipe(r.id) })
                        if (r.steps.isNotEmpty()) add(SheetAction("Start cooking", Ico.Flame) { app.startCooking(r.id) })
                    }
                    add(SheetAction("Edit recipe", Ico.Pencil) { app.editRecipe(r.id) })
                    if (!sheet.preview) add(SheetAction("Add photo", Ico.Camera) { app.addPhoto(r.id) })
                    add(
                        SheetAction(if (r.favorite) "Remove from favorites" else "Add to favorites", Ico.Heart) {
                            val now = app.toggleFavorite(r.id)
                            haptics.perform(if (now == true) HapticType.SUCCESS else HapticType.LIGHT)
                        },
                    )
                    add(SheetAction("Delete recipe", Ico.Trash, danger = true) { app.sheet = Sheet.ConfirmDelete(r.id) })
                }
                ActionSheet(
                    onDismiss = { app.sheet = null },
                    actions = actions,
                    preview = r.takeIf { sheet.preview },
                    title = r.title.takeUnless { sheet.preview },
                )
            }
        }
        Sheet.Timers -> TimersSheet(app)
        is Sheet.PhotoViewer -> {
            val r = recipes.firstOrNull { it.id == sheet.recipeId }
            if (r == null) LaunchedEffect(sheet) { app.sheet = null } else PhotoViewer(app, sheet, r)
        }
        is Sheet.ConfirmDelete -> {
            val r = recipes.firstOrNull { it.id == sheet.recipeId }
            if (r == null) LaunchedEffect(sheet) { app.sheet = null }
            else ConfirmDeleteSheet(r, onDismiss = { app.sheet = null }, onDelete = { app.deleteRecipe(r.id) })
        }
    }
}
