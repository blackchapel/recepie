package com.blackchapel.recipai.ui.library

import com.blackchapel.recipai.ui.photos.RecipeImage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Library
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.SortOrder
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.data.AppSettings
import com.blackchapel.recipai.data.LibraryLayout
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.Tab
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.EaseNav
import com.blackchapel.recipai.ui.components.EmptyLibraryState
import com.blackchapel.recipai.ui.components.EmptyState
import com.blackchapel.recipai.ui.components.FilterChip
import com.blackchapel.recipai.ui.components.GlassButton
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.IconButton
import com.blackchapel.recipai.ui.components.LargeTitle
import com.blackchapel.recipai.ui.components.NavBarHeight
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.SearchField
import com.blackchapel.recipai.ui.components.SegLabel
import com.blackchapel.recipai.ui.components.Segmented
import com.blackchapel.recipai.ui.components.TabBarHeight
import com.blackchapel.recipai.ui.components.TagDots
import com.blackchapel.recipai.ui.components.TimeBadge
import com.blackchapel.recipai.ui.components.TopBar
import com.blackchapel.recipai.ui.components.greeting
import com.blackchapel.recipai.ui.components.rememberRiseStart
import com.blackchapel.recipai.ui.components.rise
import com.blackchapel.recipai.ui.components.servingsText
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.components.tapAndHold
import com.blackchapel.recipai.ui.components.timeText
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Gutter = 18.dp

/** Recipes ([favorites] = false) and Favorites. */
@Composable
fun LibraryScreen(app: AppController, favorites: Boolean, recipes: List<Recipe>, settings: AppSettings) {
    val c = LocalColors.current
    val tab = if (favorites) Tab.FAVORITES else Tab.RECIPES
    val state = rememberLazyGridState()
    ScrollToTopOnReselect(app, tab, state)

    val list = remember(recipes, favorites, app.libraryTag, app.libraryQuery, app.sort) {
        if (favorites) Library.filter(recipes, favoritesOnly = true, sort = app.sort)
        else Library.filter(recipes, app.libraryTag, app.libraryQuery, sort = app.sort)
    }
    val tags = remember(recipes) { Library.tagCounts(recipes) }
    val layout = if (favorites) LibraryLayout.LIST else settings.layout
    val title = if (favorites) "Favorites" else "Recipes"

    val density = LocalDensity.current
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 4 } }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > with(density) { 44.dp.toPx() } } }

    // The cards/rows rise in on first appearance and after a layout switch.
    val riseStart = rememberRiseStart(layout.name)
    // At most one list row is swiped open; scrolling or a new list closes it.
    var openRow by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(list) { openRow = null }
    LaunchedEffect(state) { snapshotFlow { state.isScrollInProgress }.collect { if (it) openRow = null } }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = state,
            contentPadding = PaddingValues(top = top + NavBarHeight, bottom = bottom + TabBarHeight + 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            full("title") { LargeTitle(if (favorites) "Saved for later" else greeting(), title) }
            if (!favorites) {
                full("search") {
                    SearchField(app.libraryQuery, { app.libraryQuery = it }, "Search recipes or ingredients…", Modifier.padding(horizontal = Gutter))
                }
                full("chips") { TagChips(app, recipes.size, tags) }
            }
            full("tools") { Tools(app, list.size, favorites, settings) }

            if (list.isEmpty()) {
                full("empty") { Empty(app, favorites, recipes.isEmpty()) }
            } else if (layout == LibraryLayout.GRID) {
                // Keys differ per layout, so switching layouts brings in fresh items (they rise) instead of morphing.
                itemsIndexed(list, key = { _, r -> "g:${r.id}" }) { i, r ->
                    RecipeCard(
                        app, r,
                        Modifier
                            .animateItem(fadeInSpec = tween(250), fadeOutSpec = null)
                            .rise(riseStart, i, staggerMs = 45, durationMs = 600)
                            .padding(start = if (i % 2 == 0) Gutter else 6.dp, end = if (i % 2 == 0) 6.dp else Gutter, bottom = 12.dp),
                    )
                }
            } else {
                itemsIndexed(list, key = { _, r -> "l:${r.id}" }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, r ->
                    RecipeRow(
                        app, r, first = i == 0, last = i == list.lastIndex,
                        open = openRow == r.id,
                        onOpenChange = { open -> openRow = if (open) r.id else openRow.takeUnless { it == r.id } },
                        onInteract = { if (openRow != r.id) openRow = null },
                        modifier = Modifier
                            .animateItem(fadeInSpec = tween(250), fadeOutSpec = null)
                            .rise(riseStart, i, staggerMs = 35, durationMs = 500),
                    )
                }
            }
        }
        TopBar(title = title, scrolled = scrolled, collapsed = collapsed)
    }
}

/** A full-width item, spaced like the prototype's 16px stack gap. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.padding(bottom = 16.dp)) { content() }
    }
}

/** Tapping the current tab again scrolls it back to the top. */
@Composable
private fun ScrollToTopOnReselect(app: AppController, tab: Tab, state: LazyGridState) {
    val initial = remember { app.reselectTick }
    LaunchedEffect(app.reselectTick) {
        if (app.reselectTick != initial && app.tab == tab) state.animateScrollToItem(0)
    }
}

@Composable
private fun TagChips(app: AppController, total: Int, tags: List<Pair<String, Int>>) {
    val c = LocalColors.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = ":all") {
            FilterChip("All", app.libraryTag == TagFilter.All, { app.libraryTag = TagFilter.All }, count = total)
        }
        item(key = ":quick") {
            FilterChip(
                "Under 30 min", app.libraryTag == TagFilter.Quick,
                { app.libraryTag = if (app.libraryTag == TagFilter.Quick) TagFilter.All else TagFilter.Quick },
                color = c.ocean, icon = Ico.Bolt,
            )
        }
        items(tags, key = { it.first }) { (tag, n) ->
            val on = app.libraryTag == TagFilter.Tag(tag)
            FilterChip(tag, on, { app.libraryTag = if (on) TagFilter.All else TagFilter.Tag(tag) }, color = c.tag(tag), count = n, dot = true)
        }
    }
}

@Composable
private fun Tools(app: AppController, count: Int, favorites: Boolean, settings: AppSettings) {
    val c = LocalColors.current
    Row(
        Modifier.padding(horizontal = Gutter).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("$count ${if (count == 1) "recipe" else "recipes"}", style = RecipaiType.mono, color = c.mute, modifier = Modifier.weight(1f))
        SortPill(app)
        if (!favorites) {
            Segmented(
                count = 2,
                selected = if (settings.layout == LibraryLayout.LIST) 1 else 0,
                onSelect = { i -> app.updateSettings { it.copy(layout = if (i == 1) LibraryLayout.LIST else LibraryLayout.GRID) } },
                height = 28.dp,
                modifier = Modifier.width(84.dp),
                itemLabel = { i -> if (i == 0) "Grid layout" else "List layout" },
            ) { i, _ -> RIcon(if (i == 0) Ico.Grid else Ico.List, size = 15.dp) }
        }
    }
}

@Composable
private fun SortPill(app: AppController) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val labels = mapOf(SortOrder.RECENT to "Recent", SortOrder.A_TO_Z to "A to Z", SortOrder.QUICKEST to "Quickest")
    val haptics = LocalHaptics.current
    Box {
        Row(
            Modifier
                .tap(pressedScale = 0.95f, haptic = HapticType.SELECTION, label = "Sort") { open = true }
                .height(34.dp)
                .background(c.surface, CircleShape)
                .border(1.dp, c.line, CircleShape)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(labels.getValue(app.sort), style = RecipaiType.uiSmall, color = c.ink)
            RIcon(Ico.Down, size = 15.dp, tint = c.mute)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEach { (order, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = RecipaiType.ui, color = if (order == app.sort) c.ink else c.body) },
                    trailingIcon = { if (order == app.sort) RIcon(Ico.Check, size = 18.dp, tint = c.accentInk) },
                    onClick = {
                        haptics.perform(HapticType.SELECTION)
                        app.sort = order
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Empty(app: AppController, favorites: Boolean, noRecipes: Boolean) {
    when {
        favorites -> EmptyState(Ico.Heart, "No favorites yet", "Tap the heart on any recipe to keep it here.")
        noRecipes -> EmptyLibraryState(onImport = { app.sheet = Sheet.Import() }, onWrite = { app.newRecipe() })
        else -> EmptyState(Ico.Chef, "Nothing matches", "Try another tag or search word.") {
            RButton("Clear filters", {
                app.libraryTag = TagFilter.All
                app.libraryQuery = ""
            })
        }
    }
}

@Composable
private fun RecipeCard(app: AppController, r: Recipe, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val time = timeText(r, none = "")
    Column(
        modifier
            .tapAndHold(onLongClick = { app.sheet = Sheet.Actions(r.id, preview = true) }, label = "Open ${r.title}") { app.openRecipe(r.id) }
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(15.dp))
                .background(c.surface2),
        ) {
            RecipeImage(r, Modifier.fillMaxSize())
            if (time.isNotEmpty()) TimeBadge(time, Modifier.align(Alignment.BottomStart).padding(8.dp))
            FavoriteGlassButton(app, r, Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 9.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.title, style = RecipaiType.cardTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${servingsText(r)} · ${r.itemCount} ingr.", style = RecipaiType.monoSmall, color = c.mute, maxLines = 1)
        }
    }
}

@Composable
fun FavoriteGlassButton(app: AppController, r: Recipe, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    GlassButton(
        icon = if (r.favorite) Ico.HeartFill else Ico.Heart,
        label = if (r.favorite) "Remove from favorites" else "Add to favorites",
        onClick = {
            val now = app.toggleFavorite(r.id)
            haptics.perform(if (now == true) HapticType.SUCCESS else HapticType.LIGHT)
        },
        modifier = modifier,
        size = 32.dp,
        iconSize = 17.dp,
        tint = if (r.favorite) c.sumac else androidx.compose.ui.graphics.Color.White,
        haptic = null,
    )
}

/**
 * A list row: tap to open, press and hold for the preview sheet, swipe
 * left for Favorite and Delete (Delete collapses the row, then offers
 * Undo). TalkBack gets the swipe actions as custom actions.
 */
@Composable
private fun RecipeRow(
    app: AppController,
    r: Recipe,
    first: Boolean,
    last: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(
        topStart = if (first) 18.dp else 0.dp, topEnd = if (first) 18.dp else 0.dp,
        bottomStart = if (last) 18.dp else 0.dp, bottomEnd = if (last) 18.dp else 0.dp,
    )
    // 1 = full height; animates to 0 before a swipe delete.
    val collapse = remember { Animatable(1f) }
    var deleting by remember { mutableStateOf(false) }

    val favorite = {
        onOpenChange(false)
        val now = app.toggleFavorite(r.id)
        haptics.perform(if (now == true) HapticType.SUCCESS else HapticType.LIGHT)
    }
    val delete = {
        if (!deleting) {
            deleting = true
            haptics.perform(HapticType.WARNING)
            scope.launch {
                try {
                    collapse.animateTo(0f, tween(260, easing = EaseNav))
                } finally {
                    // Even if the list went away mid-animation, the delete still happens.
                    app.deleteRecipe(r.id)
                    onOpenChange(false)
                }
            }
        }
    }

    Box(
        modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * collapse.value).roundToInt()) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = collapse.value }
            .padding(horizontal = Gutter)
            .clip(shape),
    ) {
        SwipeActions(
            open = open,
            onOpenChange = onOpenChange,
            onDragStart = onInteract,
            favorite = r.favorite,
            onFavorite = favorite,
            onDelete = delete,
        ) {
            Column(Modifier.background(c.surface)) {
                if (!first) Box(Modifier.padding(start = 92.dp).fillMaxWidth().height(0.5.dp).background(c.lineStrong))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tapAndHold(onLongClick = { app.sheet = Sheet.Actions(r.id, preview = true) }, pressedScale = 0.985f, label = "Open ${r.title}") {
                            // Tapping a swiped-open row just closes it.
                            if (open) onOpenChange(false) else {
                                onInteract()
                                app.openRecipe(r.id)
                            }
                        }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(if (r.favorite) "Remove from favorites" else "Add to favorites") { favorite(); true },
                                CustomAccessibilityAction("Delete recipe") { delete(); true },
                            )
                        }
                        .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box {
                        RecipeImage(r, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                        if (r.favorite) {
                            Box(
                                Modifier
                                    .offset(x = 48.dp, y = (-2).dp)
                                    .size(18.dp)
                                    .background(c.surface, CircleShape)
                                    .padding(2.dp)
                                    .background(c.sumac, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { RIcon(Ico.HeartFill, size = 9.dp, tint = androidx.compose.ui.graphics.Color.White) }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(r.title, style = RecipaiType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${timeText(r)} · ${servingsText(r)}", style = RecipaiType.mono, color = c.mute, maxLines = 1)
                        TagDots(r.tags)
                    }
                    RIcon(Ico.Right, size = 20.dp, tint = c.faint)
                }
            }
        }
    }
}
