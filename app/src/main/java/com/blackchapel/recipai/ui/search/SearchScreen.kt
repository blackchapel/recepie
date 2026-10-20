package com.blackchapel.recipai.ui.search

import com.blackchapel.recipai.ui.photos.RecipeImage
import com.blackchapel.recipai.ui.timers.timerPillClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Library
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.ImportTab
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.Tab
import com.blackchapel.recipai.ui.components.EmptyState
import com.blackchapel.recipai.ui.components.GroupLabel
import com.blackchapel.recipai.ui.components.Hairline
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.LargeTitle
import com.blackchapel.recipai.ui.components.NavBarHeight
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.SearchField
import com.blackchapel.recipai.ui.components.TabBarHeight
import com.blackchapel.recipai.ui.components.TopBar
import com.blackchapel.recipai.ui.components.servingsText
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.components.timeText
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

private val Gutter = 18.dp

private data class Command(val title: String, val subtitle: String, val icon: Ico, val run: () -> Unit)

@Composable
fun SearchScreen(app: AppController, recipes: List<Recipe>) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    val initialTick = remember { app.reselectTick }
    LaunchedEffect(app.reselectTick) { if (app.reselectTick != initialTick && app.tab == Tab.SEARCH) state.animateScrollToItem(0) }

    val density = LocalDensity.current
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 4 } }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > with(density) { 44.dp.toPx() } } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val q = app.searchQuery.trim()
    val hits = remember(recipes, q) { Library.search(recipes, q) }
    val tags = remember(recipes) { Library.tagCounts(recipes) }
    val commands = listOf(
        Command("New recipe", "Start from a blank recipe", Ico.Plus) { app.newRecipe() },
        Command("Import from pasted text", "Paste a recipe from anywhere", Ico.Clip) { app.sheet = Sheet.Import(ImportTab.TEXT) },
        Command("Import from a link", "Convert a recipe page", Ico.Link) { app.sheet = Sheet.Import(ImportTab.LINK) },
        Command("Back up now", com.blackchapel.recipai.ui.backup.backUpNowSubtitle(app), Ico.CloudUp) { app.backUpNow() },
        Command("Backup settings", "Google Drive, schedule and restore", Ico.Cloud) { app.openBackup() },
    ).filter { q.isNotEmpty() && (it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)) }
    val tagHits = tags.filter { q.isNotEmpty() && it.first.contains(q.lowercase()) }.take(4)

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = top + NavBarHeight, bottom = bottom + TabBarHeight + 28.dp + timerPillClearance(app)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("title") { LargeTitle("Find anything", "Search") }
            item("field") { SearchField(app.searchQuery, { app.searchQuery = it }, "Recipes, ingredients, tags…", Modifier.padding(horizontal = Gutter)) }
            if (q.isEmpty()) {
                item("browse") { Text("Browse", style = RecipaiType.secTitle, color = c.ink, modifier = Modifier.padding(horizontal = Gutter)) }
                val tiles = buildList {
                    add(Tile("Under 30 minutes", recipes.count(Library::isQuick), Ico.Bolt, c.ocean) { app.showTag(TagFilter.Quick) })
                    add(Tile("Favorites", recipes.count { it.favorite }, Ico.Heart, c.sumac) { app.selectTab(Tab.FAVORITES) })
                    tags.forEach { (t, n) -> add(Tile(t.replaceFirstChar { it.uppercase() }, n, Ico.Tag, c.tag(t)) { app.showTag(TagFilter.Tag(t)) }) }
                }
                item("tiles") {
                    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tiles.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { BrowseTile(it, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else if (hits.isEmpty() && tagHits.isEmpty() && commands.isEmpty()) {
                item("none") { EmptyState(Ico.Search, "No results", "Nothing matches “$q”.") }
            } else {
                if (hits.isNotEmpty()) group("recipes", "Recipes") {
                    hits.forEachIndexed { i, hit ->
                        if (i > 0) Hairline(start = 62.dp)
                        ResultRow(hit.recipe.title, hit.why ?: "${timeText(hit.recipe)} · ${servingsText(hit.recipe)}", onClick = { app.openRecipe(hit.recipe.id) }) {
                            RecipeImage(hit.recipe, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                        }
                    }
                }
                if (tagHits.isNotEmpty()) group("tags", "Tags") {
                    tagHits.forEachIndexed { i, (t, n) ->
                        if (i > 0) Hairline(start = 62.dp)
                        ResultRow("#$t", "$n ${if (n == 1) "recipe" else "recipes"}", onClick = { app.showTag(TagFilter.Tag(t)) }) {
                            IconSquare(Ico.Tag, c.tag(t))
                        }
                    }
                }
                if (commands.isNotEmpty()) group("actions", "Actions") {
                    commands.forEachIndexed { i, cmd ->
                        if (i > 0) Hairline(start = 62.dp)
                        ResultRow(cmd.title, cmd.subtitle, onClick = cmd.run, chevron = false) { IconSquare(cmd.icon, c.ink) }
                    }
                }
            }
        }
        TopBar(title = "Search", scrolled = scrolled, collapsed = collapsed)
    }
}

private class Tile(val title: String, val count: Int, val icon: Ico, val color: Color, val onClick: () -> Unit)

@Composable
private fun BrowseTile(t: Tile, modifier: Modifier) {
    Box(
        modifier
            .tap(onClick = t.onClick)
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(lerp(t.color, Color.Black, 0.08f), lerp(t.color, Color.Black, 0.45f)))),
    ) {
        RIcon(
            t.icon,
            size = 64.dp,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 10.dp).graphicsLayer { rotationZ = -14f },
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(t.title, style = RecipaiType.tileTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${t.count} ${if (t.count == 1) "recipe" else "recipes"}", style = RecipaiType.mono, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

private fun LazyListScope.group(key: String, label: String, content: @Composable () -> Unit) {
    item(key) {
        Column {
            GroupLabel(label)
            Column(
                Modifier
                    .padding(horizontal = Gutter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LocalColors.current.surface),
            ) { content() }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit, chevron: Boolean = true, leading: @Composable () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = RecipaiType.ui, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = RecipaiType.bodySmall, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) RIcon(Ico.Right, tint = c.faint)
    }
}

@Composable
private fun IconSquare(icon: Ico, tint: Color) {
    Box(Modifier.size(38.dp).background(LocalColors.current.surface2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        RIcon(icon, tint = tint)
    }
}
