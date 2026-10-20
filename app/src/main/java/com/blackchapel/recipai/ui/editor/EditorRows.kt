package com.blackchapel.recipai.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.DraftErrors
import com.blackchapel.recipai.core.DraftIngredient
import com.blackchapel.recipai.core.DraftStep
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.TimeScale
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/** Units offered under a focused unit field (the prototype's datalist). */
private val CommonUnits = listOf(
    "g", "kg", "ml", "l", "tsp", "tbsp", "cup", "oz", "lb", "clove", "pinch", "can", "bunch", "sprig", "handful", "slice", "sheet",
)

/** How a step's time scales: icon, short label, and what it's for. */
internal fun TimeScale.icon(): Ico = when (this) {
    TimeScale.FIXED -> Ico.Lock
    TimeScale.PARTIAL -> Ico.Curve
    TimeScale.LINEAR -> Ico.Trend
}

internal fun TimeScale.label(): String = when (this) {
    TimeScale.FIXED -> "Fixed time"
    TimeScale.PARTIAL -> "Scales a little"
    TimeScale.LINEAR -> "Scales with batch"
}

internal fun TimeScale.explain(): String = when (this) {
    TimeScale.FIXED -> "Oven, resting, chilling: the same however much you make."
    TimeScale.PARTIAL -> "Simmering, reducing: a little longer for a bigger batch."
    TimeScale.LINEAR -> "Chopping, frying in batches: grows with the batch."
}

/** Callbacks the ingredient and step rows need from the screen. */
internal class RowActions(
    val focus: (String) -> FocusRequester,
    /** Several lines pasted into an ingredient name. True if they became rows. */
    val pasteLines: (rowId: String, pasted: String) -> Boolean,
    val openMode: (stepId: String) -> Unit,
)

@Composable
internal fun IngredientsPanel(state: EditorState, errors: DraftErrors, actions: RowActions) {
    val draft = state.draft
    Panel(Ico.Clip, "Ingredients", count = draft.ingredientCount) {
        HelpText("Paste several lines into a name field to add them all at once.")
        Column(Modifier.fillMaxWidth()) {
            val rows = draft.ingredients
            rows.forEachIndexed { i, ing ->
                key(ing.id) {
                    AnimatedRow(
                        appear = ing.id in state.addedRows,
                        removing = ing.id in state.removing,
                        onGone = { state.gone(ing.id) },
                    ) {
                        IngredientRow(
                            state = state,
                            ing = ing,
                            first = i == 0,
                            last = i == rows.lastIndex,
                            badQuantity = ing.id in errors.badQuantities,
                            missingName = ing.id in errors.missingNames,
                            actions = actions,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RButton(
                "Ingredient",
                {
                    val id = state.newId()
                    state.draft = state.draft.addIngredient(id)
                    state.added(id, FieldKey.qty(id))
                },
                Modifier.weight(1f),
                icon = Ico.Plus,
                haptic = HapticType.LIGHT,
            )
            RButton(
                "Section",
                {
                    val id = state.newId()
                    state.draft = state.draft.addIngredient(id, heading = true)
                    state.added(id, FieldKey.name(id))
                },
                Modifier.weight(1f),
                icon = Ico.Heading,
                haptic = HapticType.LIGHT,
            )
        }
    }
}

@Composable
private fun IngredientRow(
    state: EditorState,
    ing: DraftIngredient,
    first: Boolean,
    last: Boolean,
    badQuantity: Boolean,
    missingName: Boolean,
    actions: RowActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val id = ing.id
    fun update(transform: (DraftIngredient) -> DraftIngredient) {
        state.draft = state.draft.updateIngredient(id, transform)
    }
    val rowActions: @Composable () -> Unit = {
        Row {
            RowAction(Ico.Up, if (ing.isHeading) "Move section up" else "Move ingredient up", { state.draft = state.draft.moveIngredient(id, -1) }, enabled = !first)
            RowAction(Ico.Down, if (ing.isHeading) "Move section down" else "Move ingredient down", { state.draft = state.draft.moveIngredient(id, 1) }, enabled = !last)
            RowAction(Ico.Trash, if (ing.isHeading) "Remove section" else "Remove ingredient", { state.remove(id) }, danger = true, haptic = HapticType.LIGHT)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ing.isHeading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdField(
                    ing.name,
                    { v -> update { it.copy(name = v.replace('\n', ' ')) } },
                    placeholder = "Section name, e.g. For the sauce",
                    label = "Section name",
                    modifier = Modifier.weight(1f),
                    textStyle = EdType.heading,
                    background = c.surface,
                    focusRequester = actions.focus(FieldKey.name(id)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                rowActions()
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EdField(
                ing.quantity,
                { v -> update { it.copy(quantity = v.replace('\n', ' ')) } },
                placeholder = "Qty",
                label = "Quantity",
                modifier = Modifier.width(70.dp),
                textStyle = EdType.inputMono,
                background = c.surface,
                invalid = badQuantity,
                focusRequester = actions.focus(FieldKey.qty(id)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            EdField(
                ing.unit,
                { v -> update { it.copy(unit = v.replace('\n', ' ')) } },
                placeholder = "Unit",
                label = "Unit",
                modifier = Modifier.weight(1f),
                background = c.surface,
                focusRequester = actions.focus(FieldKey.unit(id)),
                onFocusChange = { f ->
                    if (f) state.unitFocus = id else if (state.unitFocus == id) state.unitFocus = null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            rowActions()
        }
        UnitSuggestions(
            visible = state.unitFocus == id,
            typed = ing.unit,
            onPick = { u ->
                update { it.copy(unit = u) }
                state.focusRequest = FieldKey.name(id)
            },
        )
        EdField(
            ing.name,
            { v ->
                if ('\n' in v || '\r' in v) {
                    val pasted = inserted(ing.name, v)
                    when {
                        actions.pasteLines(id, pasted) -> Unit
                        // Enter on a hardware keyboard: on to the note.
                        pasted.isBlank() -> state.focusRequest = FieldKey.note(id)
                        else -> update { it.copy(name = v.replace(Regex("[\\r\\n]+"), " ")) }
                    }
                } else {
                    update { it.copy(name = v) }
                }
            },
            placeholder = "Ingredient",
            label = "Ingredient name",
            modifier = Modifier.fillMaxWidth(),
            background = c.surface,
            invalid = missingName,
            // Not single line: a single-line field may drop the newlines of a
            // multi-line paste, and pasting lines here adds them all.
            singleLine = false,
            maxLines = 3,
            focusRequester = actions.focus(FieldKey.name(id)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { state.focusRequest = FieldKey.note(id) }),
        )
        EdField(
            ing.note,
            { v -> update { it.copy(note = v.replace('\n', ' ')) } },
            placeholder = "Note, e.g. finely chopped",
            label = "Note",
            modifier = Modifier.fillMaxWidth(),
            background = c.surface,
            focusRequester = actions.focus(FieldKey.note(id)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { nextIngredient(state, id) }),
        )
        if (badQuantity) FieldError("Use a number, like 2, 1.5 or 1 1/2.")
        if (missingName) FieldError("Name this ingredient, or remove the row.")
    }
}

/** From the last field of an ingredient: the next row's amount, or a new row. */
private fun nextIngredient(state: EditorState, id: String) {
    val rows = state.draft.ingredients
    val i = rows.indexOfFirst { it.id == id }
    val next = rows.getOrNull(i + 1)
    when {
        next == null -> {
            val newId = state.newId()
            state.draft = state.draft.addIngredient(newId, afterId = id)
            state.added(newId, FieldKey.qty(newId))
        }
        next.isHeading -> state.focusRequest = FieldKey.name(next.id)
        else -> state.focusRequest = FieldKey.qty(next.id)
    }
}

/** The text a change inserted: [new] minus the prefix and suffix it shares with [old]. */
internal fun inserted(old: String, new: String): String {
    var start = 0
    val max = minOf(old.length, new.length)
    while (start < max && old[start] == new[start]) start++
    var end = 0
    while (end < max - start && old[old.length - 1 - end] == new[new.length - 1 - end]) end++
    return new.substring(start, new.length - end)
}

@Composable
private fun UnitSuggestions(visible: Boolean, typed: String, onPick: (String) -> Unit) {
    val c = LocalColors.current
    val t = typed.trim().lowercase()
    val units = CommonUnits.filter { it != t && (t.isEmpty() || it.startsWith(t)) }
    AnimatedVisibility(
        visible = visible && units.isNotEmpty(),
        enter = fadeIn(tween(200)) + expandVertically(tween(250, easing = EaseOut)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(200, easing = EaseOut)),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            units.forEach { u ->
                Row(
                    Modifier
                        .tap(pressedScale = 0.94f, haptic = HapticType.SELECTION, role = Role.Button, label = "Use $u") { onPick(u) }
                        .height(30.dp)
                        .background(c.surface, CircleShape)
                        .border(1.dp, c.line, CircleShape)
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(u, style = RecipaiType.mono.copy(fontSize = RecipaiType.uiSmall.fontSize), color = c.body, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun MethodPanel(state: EditorState, errors: DraftErrors, actions: RowActions) {
    val draft = state.draft
    Panel(Ico.Timer, "Method", count = draft.steps.size) {
        HelpText("Add minutes to a step to give it a timer.")
        Column(Modifier.fillMaxWidth()) {
            val steps = draft.steps
            steps.forEachIndexed { i, step ->
                key(step.id) {
                    AnimatedRow(
                        appear = step.id in state.addedRows,
                        removing = step.id in state.removing,
                        onGone = { state.gone(step.id) },
                    ) {
                        StepRow(
                            state = state,
                            step = step,
                            number = i + 1,
                            last = i == steps.lastIndex,
                            badMinutes = step.id in errors.badMinutes,
                            actions = actions,
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp),
                        )
                    }
                }
            }
        }
        RButton(
            "Add step",
            {
                val id = state.newId()
                state.draft = state.draft.addStep(id)
                state.added(id, FieldKey.stepText(id))
            },
            Modifier.fillMaxWidth(),
            icon = Ico.Plus,
            haptic = HapticType.LIGHT,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepRow(
    state: EditorState,
    step: DraftStep,
    number: Int,
    last: Boolean,
    badMinutes: Boolean,
    actions: RowActions,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val id = step.id
    fun update(transform: (DraftStep) -> DraftStep) {
        state.draft = state.draft.updateStep(id, transform)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STEP $number", style = RecipaiType.label, color = c.mute, modifier = Modifier.weight(1f).padding(start = 4.dp))
            RowAction(Ico.Up, "Move step $number up", { state.draft = state.draft.moveStep(id, -1) }, enabled = number > 1)
            RowAction(Ico.Down, "Move step $number down", { state.draft = state.draft.moveStep(id, 1) }, enabled = !last)
            RowAction(Ico.Trash, "Remove step $number", { state.remove(id) }, danger = true, haptic = HapticType.LIGHT)
        }
        EdField(
            step.text,
            { v -> update { it.copy(text = v) } },
            placeholder = "Describe this step",
            label = "Step $number",
            modifier = Modifier.fillMaxWidth(),
            textStyle = EdType.textarea,
            minHeight = 70.dp,
            singleLine = false,
            background = c.surface,
            focusRequester = actions.focus(FieldKey.stepText(id)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EdField(
                step.minutes,
                { v -> update { it.copy(minutes = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(7)) } },
                placeholder = "0",
                label = "Minutes for step $number",
                modifier = Modifier.width(96.dp).align(Alignment.CenterVertically),
                textStyle = EdType.inputMono,
                minHeight = 40.dp,
                background = c.surface,
                invalid = badMinutes,
                focusRequester = actions.focus(FieldKey.minutes(id)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                suffix = "min",
            )
            ModePill(step.timeScale, number, Modifier.align(Alignment.CenterVertically)) { actions.openMode(id) }
            DetectChip(step.detectedMinutes, Modifier.align(Alignment.CenterVertically)) {
                state.draft = state.draft.useDetectedTime(id)
            }
        }
        if (badMinutes) FieldError("Use minutes as a number, like 12 or 1.5.")
    }
}

/** The step's scaling mode as a pill; tapping it opens the mode sheet. */
@Composable
private fun ModePill(mode: TimeScale, number: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        modifier
            .tap(pressedScale = 0.96f, haptic = HapticType.LIGHT, label = "When the recipe is scaled, step $number: ${mode.label()}", onClick = onClick)
            .height(40.dp)
            .background(c.surface, RoundedCornerShape(12.dp))
            .padding(start = 11.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RIcon(mode.icon(), size = 15.dp, tint = c.mute)
        Text(mode.label(), style = RecipaiType.uiSmall, color = c.ink, maxLines = 1)
        RIcon(Ico.Down, size = 13.dp, tint = c.mute)
    }
}

/** "Found 12 min · use it": the time read from the step's text, one tap to take it. */
@Composable
private fun DetectChip(minutes: Double?, modifier: Modifier = Modifier, onUse: () -> Unit) {
    val c = LocalColors.current
    // Keep showing the last value while the chip animates out.
    val shown = remember { doubleArrayOf(0.0) }
    if (minutes != null) shown[0] = minutes
    AnimatedVisibility(
        visible = minutes != null,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) + fadeIn(tween(150)),
        exit = scaleOut(tween(150), targetScale = 0.8f) + fadeOut(tween(150)),
    ) {
        val dash = c.saffron.copy(alpha = 0.45f)
        Row(
            Modifier
                .tap(pressedScale = 0.94f, haptic = HapticType.SELECTION, label = "Use ${Scaling.formatMinutes(shown[0])} for this step", onClick = onUse)
                .height(32.dp)
                .background(c.saffron.copy(alpha = 0.12f), CircleShape)
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        dash,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RIcon(Ico.Spark, size = 15.dp, tint = c.accentInk)
            Text("Found ${Scaling.formatMinutes(shown[0])} · use it", style = RecipaiType.tag, color = c.accentInk, maxLines = 1)
        }
    }
}
