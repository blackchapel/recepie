package com.blackchapel.recipai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.platform.nav.PageBackHandler
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
import com.blackchapel.recipai.ui.welcome.WelcomeScreen
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiTheme
import com.blackchapel.recipai.ui.theme.isDarkTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** iOS-like navigation easing from the prototype (--ease-nav). */
private val NavEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * The whole app: four tab roots, pages pushed over them (recipe detail,
 * editor, cook mode, Backup), the tab bar and + button, the mini timer,
 * toasts, and at most one bottom sheet.
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
        // How far the current tab is shifted left under a pushed page (0..1).
        // Only sideways pages push it: the editor and cook mode slide up
        // from the bottom, and the page behind them stays where it is.
        val sideways = app.stack.any { it !is EditorPage && it !is CookPage }
        val under = remember { Animatable(if (sideways) 1f else 0f) }
        LaunchedEffect(sideways) { under.animateTo(if (sideways) 1f else 0f, tween(450, easing = NavEasing)) }
        val swipe = remember { PageBackSwipe() }
        PageBack(app, swipe, under)
        LaunchedEffect(loadError) { loadError?.let { app.showToast(it, Ico.Info) } }
        LaunchedEffect(saveError) { saveError?.let { app.showToast(it, Ico.Info) } }

        Box(Modifier.fillMaxSize().background(c.canvas)) {
            if (loaded) {
                // Tab roots. The current one slides back a little under a pushed page.
                val saveable = rememberSaveableStateHolder()
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -0.28f * size.width * under.value },
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
                if (under.value > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f * under.value)))

                // Pushed pages slide in from the right and back out on pop.
                AnimatedContent(
                    targetState = top,
                    transitionSpec = {
                        // Runs after the stack changed: a page that is no longer in it was popped.
                        val isPop = initialState != null && initialState !in app.stack
                        // The editor and cook mode are modal: they slide up and down; other pages slide sideways.
                        val moving = if (isPop) initialState else targetState
                        val modal = moving is EditorPage || moving is CookPage
                        val transform = if (isPop && swipe.swipedAway(initialState)) {
                            // A page swiped away is already off screen: no second slide.
                            EnterTransition.None togetherWith ExitTransition.None
                        } else if (isPop) {
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
                    // Follows the finger during a back swipe.
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = swipe.offset(page) * size.width }) {
                        when (page) {
                            is DetailPage -> DetailScreen(app, page, recipes.firstOrNull { it.id == page.recipeId })
                            is EditorPage -> EditorScreen(app, page, recipes)
                            is CookPage -> CookScreen(app, page, recipes.firstOrNull { it.id == page.recipeId })
                            is BackupPage -> BackupScreen(app, page)
                            null -> Box(Modifier.fillMaxSize())
                        }
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
            // The first-run tour covers the whole app until it is finished. While the answer is
            // still unknown (the first frames of a launch) an empty canvas stands in for it, so a
            // new install never flashes the library before the tour.
            val welcome = when {
                settings.welcomeSeen == null -> WelcomeStage.UNDECIDED
                app.showWelcome -> WelcomeStage.TOUR
                else -> WelcomeStage.DONE
            }
            Crossfade(welcome, animationSpec = tween(350), label = "welcome") { stage ->
                when (stage) {
                    WelcomeStage.UNDECIDED -> Box(Modifier.fillMaxSize().background(c.canvas))
                    WelcomeStage.TOUR -> WelcomeScreen(onFinish = app::finishWelcome)
                    WelcomeStage.DONE -> Unit
                }
            }
            ToastHost(app, Modifier.align(Alignment.TopCenter))
            // Not over the tour: a link shared into a fresh install waits for it to end.
            if (welcome == WelcomeStage.DONE) SheetHost(app, recipes)
            com.blackchapel.recipai.ui.backup.BackupHost(app)
        }
    }
}

/** What covers the app at launch: nothing decided yet, the first-run tour, or nothing. */
private enum class WelcomeStage { UNDECIDED, TOUR, DONE }

/**
 * System back for pushed pages. A predictive back swipe (Android 14+) drags
 * the page to the right with the finger and the tab underneath back into
 * place ([PageBackSwipe]); releasing finishes the slide and pops it (no
 * haptic: going back is silent), abandoning springs it back. The back button and older
 * Android versions pop with the normal animation.
 */
@Composable
private fun PageBack(app: AppController, swipe: PageBackSwipe, under: Animatable<Float, AnimationVector1D>) {
    val scope = rememberCoroutineScope()
    PageBackHandler(
        enabled = app.canGoBack,
        onProgress = { fraction ->
            val top = app.stack.lastOrNull()
            if (top != null && PageBackSwipe.canFollow(top, app.stack.size)) {
                swipe.follow(top, fraction)
                under.snapTo(1f - fraction)
            }
        },
        onCancel = {
            scope.launch {
                val spec = tween<Float>(300, easing = NavEasing)
                coroutineScope {
                    launch { swipe.progress.animateTo(0f, spec) }
                    if (app.stack.isNotEmpty()) under.animateTo(1f, spec)
                }
            }
        },
        onBack = { swiped ->
            val top = app.stack.lastOrNull()
            // The finish-the-slide path only for a real swipe: a later back
            // press on a page whose swipe was abandoned pops normally.
            if (swiped && top != null && swipe.isFollowing(top)) {
                swipe.commit()
                scope.launch {
                    try {
                        // Finish the slide from where the finger let go.
                        val spec = tween<Float>((320 * (1f - swipe.progress.value)).toInt().coerceAtLeast(80), easing = NavEasing)
                        coroutineScope {
                            launch { swipe.progress.animateTo(1f, spec) }
                            under.animateTo(0f, spec)
                        }
                    } finally {
                        // Pop exactly the page that was swiped (not a sheet
                        // or another page, should anything have changed).
                        if (app.stack.lastOrNull() === top) app.stack.removeAt(app.stack.lastIndex)
                    }
                }
            } else {
                app.back()
            }
        },
    )
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
                    add(SheetAction("Add photo", Ico.Camera) { app.addPhoto(r.id) })
                    add(
                        SheetAction(if (r.favorite) "Remove from favorites" else "Add to favorites", Ico.Heart, haptic = null) {
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
