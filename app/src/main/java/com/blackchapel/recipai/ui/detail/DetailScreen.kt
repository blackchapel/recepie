package com.blackchapel.recipai.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Ingredient
import com.blackchapel.recipai.core.Quantities
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.Step
import com.blackchapel.recipai.core.TagFilter
import com.blackchapel.recipai.core.TimeScale
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.DetailPage
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.cook.DetailBottomBar
import com.blackchapel.recipai.ui.cook.DetailBottomBarHeight
import com.blackchapel.recipai.ui.photos.DetailHero
import com.blackchapel.recipai.ui.photos.DetailPhotoStrip
import com.blackchapel.recipai.ui.timers.StepTimeChip
import com.blackchapel.recipai.ui.components.GlassButton
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.IconButton
import com.blackchapel.recipai.ui.components.NavBarHeight
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RecipeArt
import com.blackchapel.recipai.ui.components.SegLabel
import com.blackchapel.recipai.ui.components.Segmented
import com.blackchapel.recipai.ui.components.TagPill
import com.blackchapel.recipai.ui.components.TimeBadge
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnBasil
import com.blackchapel.recipai.ui.theme.RecipaiType

private val Gutter = 18.dp
private val HeroHeight = 340.dp

/** The scale presets under Ingredients. */
val ScalePresets = listOf(0.5 to "½×", 1.0 to "1×", 1.5 to "1½×", 2.0 to "2×", 3.0 to "3×")

@Composable
fun DetailScreen(app: AppController, page: DetailPage, recipe: Recipe?) {
    val c = LocalColors.current
    if (recipe == null) {
        // Deleted while open (e.g. from the more menu): leave the page.
        LaunchedEffect(Unit) { if (app.stack.lastOrNull() === page) app.back() }
        Box(Modifier.fillMaxSize().background(c.canvas))
        return
    }
    val r = recipe
    // Cook mode or the editor covering this page drops the screen from the
    // composition, so the position is kept on the page and restored here.
    val state = rememberLazyListState(page.scrollIndex, page.scrollOffset)
    DisposableEffect(state) {
        onDispose {
            page.scrollIndex = state.firstVisibleItemIndex
            page.scrollOffset = state.firstVisibleItemScrollOffset
        }
    }
    val density = LocalDensity.current
    val collapsePx = with(density) { 250.dp.toPx() }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > collapsePx } }

    LaunchedEffect(collapsed) { app.heroUnderStatusBar = !collapsed }
    DisposableEffect(Unit) { onDispose { app.heroUnderStatusBar = false } }

    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scaled = kotlin.math.abs(page.factor - 1) > 1e-6

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = bottom + DetailBottomBarHeight + 40.dp), modifier = Modifier.fillMaxSize()) {
            item("hero") { DetailHero(app, r, state) }
            item("head") { Head(app, r) }
            item("stats") { Stats(page, r, scaled) }
            item("photos") { DetailPhotoStrip(app, r) }
            item("ingredients") { Ingredients(app, page, r, scaled) }
            item("method-head") {
                SectionHead(
                    "Method",
                    when {
                        r.steps.any { it.minutes > 0 } -> "Tap a time to start a timer"
                        r.steps.isNotEmpty() -> "Tap a number when it's done"
                        else -> null
                    },
                    Modifier.padding(start = Gutter, end = Gutter, top = 24.dp, bottom = 10.dp),
                )
            }
            if (r.steps.isEmpty()) {
                item("no-steps") { Text("No steps yet.", style = RecipaiType.body, color = c.mute, modifier = Modifier.padding(horizontal = Gutter)) }
            }
            itemsIndexed(r.steps, key = { _, s -> s.id }) { i, s -> StepCard(app, r, page, s, i, scaled) }
            if (r.notes.isNotBlank()) {
                item("notes") { Notes(r.notes) }
            }
        }

        DetailTopBar(app, r, collapsed)
        DetailBottomBar(app, r, page, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DetailTopBar(app: AppController, r: Recipe, collapsed: Boolean) {
    val c = LocalColors.current
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(250), label = "bar")
    val haptics = LocalHaptics.current
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.matchParentSize().graphicsLayer { alpha = barAlpha }.background(c.bar), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(NavBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tint = if (collapsed) c.ink else Color.White
            GlassButton(Ico.Left, "Back", { app.back() }, haptic = null, glass = !collapsed, tint = if (collapsed) c.accentInk else Color.White, iconSize = 24.dp)
            Text(
                r.title,
                style = RecipaiType.navTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = barAlpha },
            )
            GlassButton(
                if (r.favorite) Ico.HeartFill else Ico.Heart,
                if (r.favorite) "Remove from favorites" else "Add to favorites",
                {
                    val now = app.toggleFavorite(r.id)
                    haptics.perform(if (now == true) HapticType.SUCCESS else HapticType.LIGHT)
                },
                glass = !collapsed,
                tint = if (r.favorite) c.sumac else tint,
                haptic = null,
            )
            GlassButton(Ico.Dots, "More actions", { app.sheet = Sheet.Actions(r.id, preview = false) }, glass = !collapsed, tint = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Head(app: AppController, r: Recipe) {
    val c = LocalColors.current
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (r.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.tags.forEach { t -> TagPill(t) { app.showTag(TagFilter.Tag(t)) } }
            }
        }
        Text(r.title, style = RecipaiType.detailTitle, color = c.ink)
        if (r.description.isNotBlank()) Text(r.description, style = RecipaiType.body, color = c.body)
        val source = r.source
        if (!source.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RIcon(Ico.Link, size = 15.dp, tint = c.mute)
                Text(source, style = RecipaiType.mono, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Stats(page: DetailPage, r: Recipe, scaled: Boolean) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val total = Scaling.totalMinutes(r, page.factor)
    val original = Scaling.totalMinutes(r)
    Row(
        Modifier
            .padding(start = Gutter, end = Gutter, top = 20.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface),
    ) {
        Stat("Time", Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(Scaling.formatMinutes(total).ifEmpty { "—" }, style = RecipaiType.statValue, color = c.ink)
                if (scaled && Scaling.formatMinutes(total) != Scaling.formatMinutes(original)) {
                    Text(Scaling.formatMinutes(original), style = RecipaiType.monoSmall.copy(textDecoration = TextDecoration.LineThrough), color = c.faint, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        StatDivider()
        val label = r.servingsLabel?.replaceFirstChar { it.uppercase() } ?: "Servings"
        Stat(label, Modifier.weight(1.25f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(Ico.Minus, "Fewer servings", {
                    haptics.perform(HapticType.SELECTION)
                    page.setScale(Scaling.stepServings(r.servings, page.factor, -1))
                }, size = 30.dp, iconSize = 15.dp, background = c.surface2, tint = c.ink, haptic = null)
                Text(
                    Scaling.formatServings(r.servings, page.factor),
                    style = RecipaiType.statValue,
                    color = c.ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(36.dp),
                    maxLines = 1,
                )
                IconButton(Ico.Plus, "More servings", {
                    haptics.perform(HapticType.SELECTION)
                    page.setScale(Scaling.stepServings(r.servings, page.factor, 1))
                }, size = 30.dp, iconSize = 15.dp, background = c.surface2, tint = c.ink, haptic = null)
            }
        }
        StatDivider()
        Stat("Items", Modifier.weight(1f)) { Text("${r.itemCount}", style = RecipaiType.statValue, color = c.ink) }
    }
}

@Composable
private fun Stat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = RecipaiType.label, color = LocalColors.current.faint, maxLines = 1)
        value()
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(0.5.dp).fillMaxHeight().background(LocalColors.current.lineStrong))
}

@Composable
private fun SectionHead(title: String, hint: String?, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = RecipaiType.section, color = c.ink)
        if (hint != null) Text(hint, style = RecipaiType.bodySmall, color = c.mute)
    }
}

@Composable
private fun Ingredients(app: AppController, page: DetailPage, r: Recipe, scaled: Boolean) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 24.dp)) {
        SectionHead("Ingredients", "Tap an amount to scale", Modifier.padding(bottom = 10.dp))
        val idx = ScalePresets.indexOfFirst { kotlin.math.abs(it.first - page.factor) < 1e-6 }
        Segmented(
            count = ScalePresets.size,
            selected = idx,
            onSelect = { i -> page.setScale(ScalePresets[i].first) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) { i, _ -> SegLabel(ScalePresets[i].second, RecipaiType.mono.copy(fontSize = RecipaiType.uiSmall.fontSize)) }

        AnimatedVisibility(scaled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val anchor = r.ingredients.firstOrNull { it.id == page.anchorId }
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .background(c.saffron.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .border(1.dp, c.saffron.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RIcon(Ico.Spark, size = 15.dp, tint = c.accentInk)
                Text(
                    "Scaled ${Quantities.trimmed(page.factor, 2)}×" + (anchor?.let { " to match ${it.name}" } ?: "") + ". Times adjusted.",
                    style = RecipaiType.bodySmall,
                    color = c.accentInk,
                    modifier = Modifier.weight(1f),
                )
                RButton("Reset", { page.setScale(1.0) }, style = ButtonStyle.GHOST, small = true, haptic = HapticType.SELECTION)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)) {
            r.ingredients.forEachIndexed { i, ing ->
                if (ing.isHeading) {
                    Text(
                        ing.name.uppercase(),
                        style = RecipaiType.label,
                        color = c.faint,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                    )
                } else {
                    if (i > 0 && !r.ingredients[i - 1].isHeading) {
                        Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(0.5.dp).background(c.lineStrong))
                    }
                    IngredientRow(
                        ing,
                        page,
                        anchored = scaled && page.anchorId == ing.id,
                        onToggle = {
                            val on = ing.id in page.checked
                            haptics.perform(if (on) HapticType.LIGHT else HapticType.SELECTION)
                            if (on) page.checked.remove(ing.id) else page.checked.add(ing.id)
                        },
                        onAmount = { app.sheet = Sheet.Amount(page, ing.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ing: Ingredient, page: DetailPage, anchored: Boolean, onToggle: () -> Unit, onAmount: () -> Unit) {
    val c = LocalColors.current
    val checked = ing.id in page.checked
    val amount = Scaling.amount(ing, page.factor)
    Row(
        Modifier
            .fillMaxWidth()
            .tap(pressedScale = 0.99f, haptic = null, role = Role.Checkbox, isChecked = checked, onClick = onToggle)
            .heightIn(min = 54.dp)
            .padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(if (checked) c.basil else Color.Transparent, CircleShape)
                .border(1.8.dp, if (checked) c.basil else c.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (checked) RIcon(Ico.Check, size = 14.dp, tint = OnBasil) }
        Column(Modifier.weight(1f)) {
            Text(
                ing.name,
                style = RecipaiType.body.copy(textDecoration = if (checked) TextDecoration.LineThrough else null),
                color = if (checked) c.faint else c.ink,
            )
            if (ing.note.isNotBlank()) Text(ing.note, style = RecipaiType.bodySmall, color = c.mute)
        }
        if (amount.text.isNotEmpty()) {
            Row(
                Modifier
                    .graphicsLayer { alpha = if (checked) 0.45f else 1f }
                    .tap(pressedScale = 0.94f, haptic = HapticType.LIGHT, label = "Change amount of ${ing.name}", onClick = onAmount)
                    .heightIn(min = 32.dp)
                    .background(if (anchored) c.saffron.copy(alpha = 0.2f) else c.surface2, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedContent(
                    targetState = amount.text,
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "amount",
                ) { text -> Text(text, style = RecipaiType.amount, color = if (anchored) c.accentInk else c.ink) }
                if (amount.unit.isNotEmpty()) Text(amount.unit, style = RecipaiType.amount.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.mute)
            }
        } else if (amount.unit.isNotEmpty()) {
            Text(amount.unit, style = RecipaiType.amount, color = c.mute)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(app: AppController, r: Recipe, page: DetailPage, s: Step, index: Int, scaled: Boolean) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val done = s.id in page.done
    val minutes = Scaling.minutes(s.minutes, s.timeScale, page.factor)
    Row(
        Modifier
            .padding(start = Gutter, end = Gutter, bottom = 10.dp)
            .fillMaxWidth()
            .graphicsLayer { alpha = if (done) 0.5f else 1f }
            .background(c.surface, RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                // Named "Step N" (the number is its only text) and checked when done.
                .tap(pressedScale = 0.88f, haptic = null, role = Role.Checkbox, description = "Step ${index + 1}", isChecked = done) {
                    haptics.perform(if (done) HapticType.LIGHT else HapticType.SUCCESS)
                    if (done) page.done.remove(s.id) else page.done.add(s.id)
                }
                .size(32.dp)
                .background(if (done) c.basil else c.surface3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) RIcon(Ico.Check, size = 15.dp, tint = OnBasil)
            else Text("${index + 1}", style = RecipaiType.mono.copy(fontSize = RecipaiType.monoSmall.fontSize.times(13f / 11.5f)), color = c.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(
                s.text,
                style = RecipaiType.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = c.body,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (minutes > 0) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StepTimeChip(app, r, s, minutes, Modifier.align(Alignment.CenterVertically))
                    if (scaled && minutes != s.minutes) {
                        Text(
                            Scaling.formatMinutes(s.minutes),
                            style = RecipaiType.monoSmall.copy(textDecoration = TextDecoration.LineThrough),
                            color = c.faint,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    val (icon, label) = when (s.timeScale) {
                        TimeScale.FIXED -> Ico.Lock to "Fixed time"
                        TimeScale.LINEAR -> Ico.Trend to "Scales with batch"
                        TimeScale.PARTIAL -> Ico.Curve to "Scales a little"
                    }
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RIcon(icon, size = 15.dp, tint = c.mute)
                        Text(label, style = RecipaiType.bodySmall.copy(fontSize = RecipaiType.tag.fontSize), color = c.mute, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Notes(notes: String) {
    val c = LocalColors.current
    Column(
        Modifier
            .padding(start = Gutter, end = Gutter, top = 10.dp)
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("NOTES", style = RecipaiType.label, color = c.faint)
        Text(notes, style = RecipaiType.body.copy(fontSize = RecipaiType.ui.fontSize.times(14.5f / 15f)), color = c.body)
    }
}
