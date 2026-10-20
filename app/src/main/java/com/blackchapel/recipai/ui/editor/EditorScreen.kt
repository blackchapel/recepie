package com.blackchapel.recipai.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.DraftErrors
import com.blackchapel.recipai.core.Library
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Tags
import com.blackchapel.recipai.core.TimeScale
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.EditorPage
import com.blackchapel.recipai.ui.SystemBackHandler
import com.blackchapel.recipai.ui.components.ActionSheet
import com.blackchapel.recipai.ui.components.Hairline
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.NavBarHeight
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RSheet
import com.blackchapel.recipai.ui.components.SheetAction
import com.blackchapel.recipai.ui.components.TopBar
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.photos.EditorPhotoStrip
import com.blackchapel.recipai.ui.timers.pushedPageBottomPadding
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.delay

/**
 * The recipe editor, a modal page that slides up: Cancel / Save on top,
 * then Photos, Basics, Tags, Ingredients, Method and Notes. Cancel (or
 * back) with unsaved changes asks before discarding. Save checks the
 * draft, then stores it and opens the recipe.
 */
@Composable
fun EditorScreen(app: AppController, page: EditorPage, recipes: List<Recipe>) {
    val c = LocalColors.current
    // Remembered, not looked up each time: the page keeps its state while it
    // animates out after Save or Cancel.
    val state = remember(page) { app.editorFor(page) }
    if (state == null) {
        // The recipe was deleted before the editor opened.
        LaunchedEffect(page) {
            app.closeEditor(page)
            app.showToast("That recipe no longer exists", Ico.Info)
        }
        Box(Modifier.fillMaxSize().background(c.canvas))
        return
    }

    val haptics = LocalHaptics.current
    val focusManager = LocalFocusManager.current
    val scroll = rememberScrollState()
    var confirmDiscard by remember { mutableStateOf(false) }
    var modeSheetFor by remember { mutableStateOf<String?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val errors = if (state.showErrors) state.errors else DraftErrors()
    val isTop = app.stack.lastOrNull() === page

    // Focus a field by key once it is on screen (a new row needs a frame or two).
    LaunchedEffect(state.focusRequest) {
        val key = state.focusRequest ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        requesters[key]?.let { r -> runCatching { r.requestFocus() } }
        if (state.focusRequest == key) state.focusRequest = null
    }

    val cancel: () -> Unit = {
        if (state.dirty) {
            focusManager.clearFocus()
            confirmDiscard = true
        } else {
            focusManager.clearFocus()
            app.closeEditor(page)
        }
    }
    val save: () -> Unit = {
        // A tag typed but not yet added counts.
        if (state.tagQuery.isNotBlank()) {
            state.draft = state.draft.addTag(state.tagQuery)
            state.tagQuery = ""
        }
        val draft = state.effective
        val problems = draft.errors()
        if (problems.any) {
            state.showErrors = true
            haptics.perform(HapticType.ERROR)
            state.focusRequest = FieldKey.firstError(draft, problems)
        } else {
            focusManager.clearFocus()
            haptics.perform(HapticType.SUCCESS)
            app.saveEditor(page, draft)
        }
    }
    // Back asks first, like Cancel. Only while this page is the top one (not
    // while it slides away after closing).
    SystemBackHandler(enabled = isTop && !confirmDiscard && modeSheetFor == null) { cancel() }

    val actions = remember(state) {
        RowActions(
            focus = { key -> requesters.getOrPut(key) { FocusRequester() } },
            pasteLines = { rowId, pasted ->
                val result = state.draft.pasteIngredients(rowId, pasted) { state.newId() }
                if (result == null) {
                    false
                } else {
                    val (next, count) = result
                    val before = state.draft.ingredients.mapTo(HashSet()) { it.id }
                    next.ingredients.filter { it.id !in before }.forEach { state.addedRows += it.id }
                    state.draft = next
                    haptics.perform(HapticType.LIGHT)
                    app.showToast("Added $count ingredients", Ico.Clip)
                    true
                }
            },
            openMode = { stepId ->
                focusManager.clearFocus()
                modeSheetFor = stepId
            },
        )
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                // Room to scroll Notes clear of the mini-timer pill, which floats over this page.
                .padding(top = top + NavBarHeight + 18.dp, bottom = bottom + pushedPageBottomPadding(app, 40.dp)),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PhotosPanel(app, state)
            BasicsPanel(state, titleMissing = errors.titleMissing, title = actions.focus(FieldKey.TITLE))
            TagsPanel(state, recipes)
            IngredientsPanel(state, errors, actions)
            MethodPanel(state, errors, actions)
            NotesPanel(state)
        }
        val title = when {
            page.recipeId != null -> "Edit recipe"
            page.preset != null -> "Review import"
            else -> "New recipe"
        }
        TopBar(
            title = title,
            scrolled = true,
            collapsed = true,
            left = { NavTextButton("Cancel", onClick = cancel) },
            right = { NavTextButton("Save", strong = true, haptic = null, onClick = save) },
        )
    }

    if (confirmDiscard) {
        ActionSheet(
            onDismiss = { confirmDiscard = false },
            actions = listOf(SheetAction("Discard changes", Ico.Trash, danger = true) { app.closeEditor(page) }),
            title = "Discard your changes?",
            message = if (state.initial.isNew) "This recipe hasn’t been saved yet." else "Your edits to this recipe haven’t been saved.",
        )
    }
    modeSheetFor?.let { stepId ->
        val step = state.draft.steps.firstOrNull { it.id == stepId }
        if (step == null) {
            LaunchedEffect(stepId) { modeSheetFor = null }
        } else {
            TimeScaleSheet(
                selected = step.timeScale,
                onDismiss = { modeSheetFor = null },
                onSelect = { mode -> state.draft = state.draft.updateStep(stepId) { it.copy(timeScale = mode) } },
            )
        }
    }
}

/** A text button in the top bar (Cancel, Save). */
@Composable
private fun NavTextButton(text: String, strong: Boolean = false, haptic: HapticType? = HapticType.LIGHT, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .tap(pressedScale = 0.9f, haptic = haptic, label = text, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = RecipaiType.ui.copy(fontSize = 16.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium),
            color = c.accentInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun PhotosPanel(app: AppController, state: EditorState) {
    val draft = state.draft
    // The illustration follows the title and ingredients, but not on every keystroke.
    var art by remember { mutableStateOf(draft) }
    LaunchedEffect(draft.title, draft.ingredients) {
        delay(500)
        art = state.draft
    }
    val preview = remember(art, draft.photos, draft.coverIndex) {
        art.preview().copy(photos = draft.photos, coverIndex = draft.coverIndex)
    }
    Panel(Ico.Camera, "Photos", count = draft.photos.size, help = "First photo is the cover", card = false) {
        EditorPhotoStrip(
            preview,
            onChange = { photos, cover -> state.draft = state.draft.copy(photos = photos, coverIndex = cover) },
            adding = state.photoAdding,
            onAdded = state::addPhotos,
            // The app's scope, so a rotation mid-copy doesn't drop the photos.
            scope = app.photoScope,
        )
    }
}

@Composable
private fun BasicsPanel(state: EditorState, titleMissing: Boolean, title: FocusRequester) {
    val draft = state.draft
    Panel(Ico.Book, "Basics") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Title")
            EdField(
                draft.title,
                { v -> state.draft = state.draft.copy(title = v) },
                placeholder = "e.g. Sunday roast chicken",
                label = "Title",
                modifier = Modifier.fillMaxWidth(),
                textStyle = EdType.title,
                minHeight = 54.dp,
                invalid = titleMissing,
                focusRequester = title,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            if (titleMissing) FieldError("Give the recipe a title so you can find it later.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel("Description")
            EdField(
                draft.description,
                { v -> state.draft = state.draft.copy(description = v) },
                placeholder = "What makes this one worth cooking?",
                label = "Description",
                modifier = Modifier.fillMaxWidth(),
                textStyle = EdType.textarea,
                minHeight = 84.dp,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Servings")
                EdField(
                    draft.servings,
                    { v -> state.draft = state.draft.copy(servings = v.filter { it.isDigit() }.take(3)) },
                    placeholder = "4",
                    label = "Servings",
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = EdType.inputMono,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Counted as")
                EdField(
                    draft.servingsLabel,
                    { v -> state.draft = state.draft.copy(servingsLabel = v.replace('\n', ' ').take(24)) },
                    placeholder = "servings",
                    label = "Counted as",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsPanel(state: EditorState, recipes: List<Recipe>) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val draft = state.draft
    val libraryTags = remember(recipes) { Library.tagCounts(recipes).map { it.first } }
    val query = state.tagQuery
    val suggestions = remember(draft.title, draft.ingredients, draft.steps, draft.tags, libraryTags, query) {
        draft.tagSuggestions(libraryTags, query)
    }
    fun add(raw: String) {
        val before = state.draft.tags
        state.draft = state.draft.addTag(raw)
        if (state.draft.tags != before) haptics.perform(HapticType.SELECTION)
    }
    Panel(Ico.Tag, "Tags") {
        val shape = RoundedCornerShape(12.dp)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(c.surface2, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            draft.tags.forEach { t ->
                key(t) {
                    RemovableTag(t, Modifier.align(Alignment.CenterVertically)) {
                        state.draft = state.draft.removeTag(t)
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .widthIn(min = 120.dp)
                    .weight(1f)
                    .height(32.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        if (draft.tags.isEmpty()) "Type a tag and press Enter" else "Add another",
                        style = EdType.input,
                        color = c.faint,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { v ->
                        // A comma or a new line ends a tag, so "spicy, quick" adds two.
                        val parts = v.split(',', '\n', '\r')
                        if (parts.size > 1) {
                            parts.dropLast(1).forEach { if (it.isNotBlank()) add(it) }
                            state.tagQuery = parts.last().trimStart()
                        } else {
                            state.tagQuery = v
                        }
                    },
                    singleLine = true,
                    textStyle = EdType.input.copy(color = c.ink),
                    cursorBrush = SolidColor(c.saffron),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (state.tagQuery.isNotBlank()) {
                            add(state.tagQuery)
                            state.tagQuery = ""
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Add a tag" },
                )
            }
        }
        val typed = Tags.normalize(query)
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("SUGGESTED", style = RecipaiType.label, color = c.faint, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
                suggestions.forEach { t ->
                    key(t) {
                        SuggestedTag(t, Modifier.align(Alignment.CenterVertically)) {
                            add(t)
                            state.tagQuery = ""
                        }
                    }
                }
            }
        } else if (typed.isNotEmpty() && typed !in draft.tags) {
            HelpText("Press Enter to add “$typed”.")
        }
    }
}

/** A tag in the tag field, with an x to remove it. */
@Composable
private fun RemovableTag(tag: String, modifier: Modifier = Modifier, onRemove: () -> Unit) {
    val c = LocalColors.current
    val tint = c.tag(tag)
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) + fadeIn(tween(150)),
    ) {
        Row(
            Modifier
                .height(28.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, tint.copy(alpha = 0.3f), CircleShape)
                .padding(start = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(tag, style = RecipaiType.tag, color = c.tagText(tint), maxLines = 1)
            Box(
                Modifier
                    .clip(CircleShape)
                    .tap(pressedScale = 0.85f, haptic = HapticType.LIGHT, role = Role.Button, label = "Remove tag $tag", onClick = onRemove)
                    .size(22.dp)
                    .semantics { contentDescription = "Remove tag $tag" },
                contentAlignment = Alignment.Center,
            ) { RIcon(Ico.X, size = 13.dp, tint = c.tagText(tint)) }
        }
    }
}

/** A suggested tag: tap to add it. */
@Composable
private fun SuggestedTag(tag: String, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val c = LocalColors.current
    val tint = c.tag(tag)
    Row(
        modifier
            .tap(pressedScale = 0.95f, haptic = null, label = "Add tag $tag", onClick = onAdd)
            .height(30.dp)
            .background(tint.copy(alpha = 0.15f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.3f), CircleShape)
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RIcon(Ico.Plus, size = 13.dp, tint = c.tagText(tint))
        Text(tag, style = RecipaiType.tag, color = c.tagText(tint), maxLines = 1)
    }
}

@Composable
private fun NotesPanel(state: EditorState) {
    Panel(Ico.Pencil, "Notes") {
        EdField(
            state.draft.notes,
            { v -> state.draft = state.draft.copy(notes = v) },
            placeholder = "Make-ahead tips, swaps, where it came from",
            label = "Notes",
            modifier = Modifier.fillMaxWidth(),
            textStyle = EdType.textarea,
            minHeight = 84.dp,
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** "When the recipe is scaled": pick how a step's time scales. */
@Composable
private fun TimeScaleSheet(selected: TimeScale, onDismiss: () -> Unit, onSelect: (TimeScale) -> Unit) {
    val c = LocalColors.current
    RSheet(onDismiss, title = "When the recipe is scaled") { closer ->
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface2)) {
                TimeScale.entries.forEachIndexed { i, mode ->
                    if (i > 0) Hairline(start = 52.dp)
                    val on = mode == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap(pressedScale = 0.99f, haptic = HapticType.SELECTION, role = Role.RadioButton, label = mode.label()) {
                                onSelect(mode)
                                closer.close()
                            }
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        RIcon(mode.icon(), tint = if (on) c.accentInk else c.mute)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mode.label(), style = RecipaiType.cell, color = c.ink)
                            Text(mode.explain(), style = RecipaiType.bodySmall, color = c.mute)
                        }
                        if (on) RIcon(Ico.Check, tint = c.accentInk)
                    }
                }
            }
        }
    }
}
