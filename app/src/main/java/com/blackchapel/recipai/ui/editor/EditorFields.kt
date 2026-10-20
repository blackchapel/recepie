package com.blackchapel.recipai.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.theme.BodyFamily
import com.blackchapel.recipai.ui.theme.DisplayFamily
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.MonoFamily
import com.blackchapel.recipai.ui.theme.RecipaiType

internal val Gutter = 18.dp

/** The prototype's --ease-out. */
internal val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Text styles for the editor's inputs (16sp so phones don't zoom or squint). */
internal object EdType {
    val input = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val inputMono = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum")
    val textarea = input.copy(lineHeight = 24.sp)
    val title = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em)
    val heading = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.06.em)
    val fieldLabel = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp)
    val panelTitle = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.06.em)
    val error = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
}

/**
 * A section of the editor: an uppercase heading with an icon and an
 * optional count, help text on the right, then [content] on a rounded
 * surface ([card] false places it bare, for the full-width photo strip).
 */
@Composable
internal fun Panel(
    icon: Ico,
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    help: String? = null,
    card: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RIcon(icon, size = 15.dp, tint = c.faint)
                Text(title.uppercase(), style = EdType.panelTitle, color = c.faint, maxLines = 1)
                if (count != null) Text("$count", style = RecipaiType.mono, color = c.faint)
            }
            if (help != null) Text(help, style = RecipaiType.bodySmall, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (card) {
            Column(
                Modifier
                    .padding(horizontal = Gutter)
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        } else {
            content()
        }
    }
}

/** A small label above a field ("Title", "Servings"). */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = EdType.fieldLabel, color = LocalColors.current.mute, modifier = modifier)
}

/** An inline validation message under a field. */
@Composable
internal fun FieldError(text: String, modifier: Modifier = Modifier) {
    Text(text, style = EdType.error, color = LocalColors.current.danger, modifier = modifier)
}

/** Secondary help text ("Add minutes to a step to give it a timer."). */
@Composable
internal fun HelpText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = RecipaiType.bodySmall, color = LocalColors.current.mute, modifier = modifier)
}

/**
 * The editor's text input: a filled, rounded box with a placeholder, a
 * saffron focus ring and a danger ring when [invalid]. [label] names it for
 * screen readers (the visible label or placeholder). Single line by
 * default; [minHeight] and [singleLine] false make a text area that grows.
 */
@Composable
internal fun EdField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = EdType.input,
    minHeight: Dp = 46.dp,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    invalid: Boolean = false,
    background: Color = LocalColors.current.surface2,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    suffix: String? = null,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(12.dp)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    if (onFocusChange != null) LaunchedEffect(focused) { onFocusChange(focused) }
    val accent = if (invalid) c.danger else c.saffron
    val border by animateColorAsState(
        when {
            invalid -> c.danger
            focused -> c.saffron.copy(alpha = 0.7f)
            else -> Color.Transparent
        },
        tween(200),
        label = "border",
    )
    val ring by animateColorAsState(if (focused || invalid) accent.copy(alpha = 0.18f) else Color.Transparent, tween(250), label = "ring")
    val multiline = !singleLine && maxLines > 1 && minHeight > 50.dp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = c.ink),
        cursorBrush = SolidColor(c.saffron),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // The 3dp glow around the field (a box-shadow ring in the prototype).
                        if (ring.alpha > 0f) {
                            val spread = 3.dp.toPx()
                            val r = 12.dp.toPx() + spread
                            drawRoundRect(
                                ring,
                                topLeft = Offset(-spread, -spread),
                                size = Size(size.width + 2 * spread, size.height + 2 * spread),
                                cornerRadius = CornerRadius(r, r),
                            )
                        }
                    }
                    .background(background, shape)
                    .border(1.dp, border, shape)
                    .heightIn(min = minHeight)
                    .padding(horizontal = 12.dp, vertical = if (multiline) 11.dp else 8.dp),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = c.faint, maxLines = if (multiline) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (suffix != null) Text(suffix, style = RecipaiType.mono, color = c.faint)
            }
        },
    )
}

/**
 * A row action: move up / move down / remove. 34 by 40, muted, or danger
 * for remove; faded and inert when [enabled] is false.
 */
@Composable
internal fun RowAction(
    icon: Ico,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    haptic: HapticType = HapticType.SELECTION,
) {
    val c = LocalColors.current
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.3f }
            .tap(enabled = enabled, pressedScale = 0.88f, haptic = haptic, role = Role.Button, label = label, onClick = onClick)
            .size(width = 34.dp, height = 40.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        RIcon(icon, size = 16.dp, tint = if (danger) c.danger else c.mute)
    }
}

/**
 * A row in the ingredient or step list. Rows added while editing slide in
 * ([appear]); a row being [removing] slides out to the right and folds
 * away, then [onGone] drops it from the draft.
 */
@Composable
internal fun AnimatedRow(appear: Boolean, removing: Boolean, onGone: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val visible = remember { MutableTransitionState(!appear) }
    visible.targetState = !removing
    val rise = with(density) { 6.dp.roundToPx() }
    val slide = with(density) { 24.dp.roundToPx() }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(400, easing = EaseOut)) + slideInVertically(tween(400, easing = EaseOut)) { -rise },
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { slide } + shrinkVertically(tween(240, delayMillis = 60, easing = EaseOut)),
    ) {
        content()
    }
    if (removing && visible.isIdle && !visible.currentState) {
        LaunchedEffect(Unit) { onGone() }
    }
}
