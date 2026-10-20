package com.blackchapel.recipai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiType

/**
 * A tap target that shrinks a little while pressed (the prototype's springy
 * :active state) and plays [haptic]. No ripple: the scale is the feedback.
 *
 * Accessibility, for TalkBack:
 * - [label] is only the click action's label ("double-tap to <label>"). It
 *   does not name the node, so on a target with no text of its own (an icon
 *   button) it is not enough.
 * - [description] is the node's name (its content description). Pass it for
 *   icon-only targets instead of [label]; it replaces any text inside the
 *   target when read, so don't pass it where the visible text is the name.
 * - [isSelected] is for tabs, segmented items and filter chips; [isChecked]
 *   for switches and checkboxes. Null (the default) says nothing.
 */
fun Modifier.tap(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    haptic: HapticType? = HapticType.LIGHT,
    role: Role? = Role.Button,
    label: String? = null,
    description: String? = null,
    isSelected: Boolean? = null,
    isChecked: Boolean? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHaptics.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(source, indication = null, enabled = enabled, onClickLabel = label, role = role) {
            haptic?.let(haptics::perform)
            onClick()
        }
        .then(
            if (description == null && isSelected == null && isChecked == null) Modifier
            else Modifier.semantics {
                if (description != null) contentDescription = description
                if (isSelected != null) selected = isSelected
                if (isChecked != null) toggleableState = ToggleableState(isChecked)
            },
        )
}

/**
 * [tap] plus press-and-hold. Long press plays a medium haptic (the design's
 * "press and hold" feedback) before [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tapAndHold(
    onLongClick: () -> Unit,
    pressedScale: Float = 0.96f,
    label: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHaptics.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onClickLabel = label,
            role = Role.Button,
            onLongClickLabel = "More actions",
            onLongClick = {
                haptics.perform(HapticType.MEDIUM)
                onLongClick()
            },
            onClick = {
                haptics.perform(HapticType.LIGHT)
                onClick()
            },
        )
}

/** Lets a child draw past its parent's horizontal padding (for edge-to-edge chip rows). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val px = horizontal.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + 2 * px,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * px else constraints.maxWidth,
        ),
    )
    layout(placeable.width - 2 * px, placeable.height) { placeable.place(-px, 0) }
}

enum class ButtonStyle { NEUTRAL, CTA, FLAME, GHOST, DANGER }

@Composable
fun RButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Ico? = null,
    style: ButtonStyle = ButtonStyle.NEUTRAL,
    small: Boolean = false,
    height: Dp = if (small) 34.dp else 44.dp,
    enabled: Boolean = true,
    haptic: HapticType? = HapticType.LIGHT,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(if (small) 11.dp else if (height >= 50.dp) 16.dp else 14.dp)
    val content = when (style) {
        ButtonStyle.CTA -> c.onCta
        ButtonStyle.FLAME -> OnFlame
        ButtonStyle.DANGER -> c.danger
        ButtonStyle.GHOST, ButtonStyle.NEUTRAL -> c.ink
    }
    val bg: Modifier = when (style) {
        ButtonStyle.NEUTRAL, ButtonStyle.DANGER -> Modifier.background(c.surface2, shape).border(1.dp, c.line, shape)
        ButtonStyle.CTA -> Modifier.background(c.cta, shape)
        ButtonStyle.FLAME -> Modifier.shadow(10.dp, shape, ambientColor = c.paprika, spotColor = c.paprika).background(FlameBrush, shape)
        ButtonStyle.GHOST -> Modifier
    }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .tap(enabled = enabled, haptic = haptic, onClick = onClick)
            .height(height)
            .then(bg)
            .clip(shape)
            .padding(horizontal = if (small) 12.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RIcon(icon, size = if (small) 15.dp else 20.dp, tint = content)
        Text(
            text,
            style = if (small) RecipaiType.uiSmall else if (height >= 50.dp) RecipaiType.ui.copy(fontSize = RecipaiType.body.fontSize) else RecipaiType.ui,
            color = content,
            maxLines = 1,
        )
    }
}

/** A round icon-only button (top bars, steppers). */
@Composable
fun IconButton(
    icon: Ico,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color = Color.Transparent,
    shape: Shape = CircleShape,
    tint: Color = LocalContentColor.current,
    haptic: HapticType? = HapticType.LIGHT,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .tap(enabled = enabled, pressedScale = 0.88f, haptic = haptic, description = label, onClick = onClick)
            .size(size)
            .background(background, shape),
        contentAlignment = Alignment.Center,
    ) {
        RIcon(icon, size = iconSize, tint = tint)
    }
}

/**
 * The segmented control with a sliding selected pill. [selected] of -1 (a
 * custom scale) shows no pill. Items report selected to TalkBack. Pass
 * [itemLabel] when an item is only an icon, so it has a name to read.
 */
@Composable
fun Segmented(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    itemLabel: ((index: Int) -> String)? = null,
    item: @Composable RowScope.(index: Int, isSelected: Boolean) -> Unit,
) {
    val c = LocalColors.current
    BoxWithConstraints(
        modifier
            .background(c.surface2, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        val w = maxWidth / count
        val x by animateDpAsState(w * selected.coerceAtLeast(0), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        if (selected >= 0) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(w)
                    .height(height)
                    .shadow(1.dp, RoundedCornerShape(9.dp))
                    .background(c.surface, RoundedCornerShape(9.dp))
                    .border(0.5.dp, c.lineStrong, RoundedCornerShape(9.dp)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(count) { i ->
                val on = i == selected
                Row(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .clip(RoundedCornerShape(9.dp))
                        .tap(
                            pressedScale = 0.97f,
                            haptic = HapticType.SELECTION,
                            role = Role.Tab,
                            description = itemLabel?.invoke(i),
                            isSelected = on,
                        ) { onSelect(i) },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides if (on) c.ink else c.mute) {
                        item(i, on)
                    }
                }
            }
        }
    }
}

/** Text for a [Segmented] item, colored by LocalContentColor. */
@Composable
fun SegLabel(text: String, style: TextStyle = RecipaiType.uiSmall) {
    Text(text, style = style, color = LocalContentColor.current, maxLines = 1)
}

/**
 * A filter chip (library tags, quick scale amounts). A [toggle] chip (the
 * default) reports its [selected] state to TalkBack; pass false for a chip
 * that is just a button (the quick scale amounts), which has no state.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.saffron,
    count: Int? = null,
    icon: Ico? = null,
    dot: Boolean = false,
    toggle: Boolean = true,
) {
    val c = LocalColors.current
    val shape = CircleShape
    Row(
        modifier
            .tap(
                pressedScale = 0.94f,
                haptic = HapticType.SELECTION,
                role = if (toggle) Role.Checkbox else Role.Button,
                isSelected = if (toggle) selected else null,
                onClick = onClick,
            )
            .height(34.dp)
            .background(if (selected) androidx.compose.ui.graphics.lerp(c.surface, color, 0.2f) else c.surface, shape)
            .border(1.dp, if (selected) color.copy(alpha = 0.55f) else c.line, shape)
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) RIcon(icon, size = 15.dp, tint = color)
        if (dot) Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = RecipaiType.uiSmall, color = if (selected) c.ink else c.body, maxLines = 1)
        if (count != null) Text("$count", style = RecipaiType.monoSmall, color = c.faint)
    }
}

/** A colored tag: 15% tint fill, 30% tint border, text mixed toward ink. */
@Composable
fun TagPill(tag: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalColors.current
    val tint = c.tag(tag)
    val shape = CircleShape
    Box(
        modifier
            .then(if (onClick != null) Modifier.tap(pressedScale = 0.95f, haptic = HapticType.SELECTION, onClick = onClick) else Modifier)
            .height(24.dp)
            .background(tint.copy(alpha = 0.15f), shape)
            .border(1.dp, tint.copy(alpha = 0.3f), shape)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tag, style = RecipaiType.tag, color = c.tagText(tint), maxLines = 1)
    }
}

/** The rounded search field used on Recipes and Search. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(c.surface2, RoundedCornerShape(13.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RIcon(Ico.Search, size = 15.dp, tint = c.mute)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = RecipaiType.body.copy(fontSize = RecipaiType.ui.fontSize), color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = RecipaiType.body.copy(color = c.ink, fontSize = RecipaiType.ui.fontSize),
                cursorBrush = SolidColor(c.saffron),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(Ico.X, "Clear search", { onValueChange("") }, size = 34.dp, iconSize = 15.dp, tint = c.mute)
        }
    }
}

/** Uppercase label above a settings/results group. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = RecipaiType.groupLabel,
        color = LocalColors.current.faint,
        modifier = modifier.padding(start = 32.dp, end = 18.dp, bottom = 6.dp),
    )
}

/** A rounded group of [Cell]s with hairlines between them. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(
        modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface),
    ) { content() }
}

/** Hairline between rows, inset to line up with the text. */
@Composable
fun Hairline(start: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(0.5.dp)
            .background(LocalColors.current.lineStrong),
    )
}

/**
 * A settings/list cell: colored icon square, title, subtitle and a trailing
 * slot. A switch row passes its state as [checked] (with `role = Role.Switch`)
 * so TalkBack can say whether it is on.
 */
@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    icon: Ico? = null,
    iconColor: Color = LocalColors.current.mute,
    subtitle: String? = null,
    titleColor: Color = LocalColors.current.ink,
    onClick: (() -> Unit)? = null,
    role: Role? = Role.Button,
    haptic: HapticType? = HapticType.LIGHT,
    checked: Boolean? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.tap(pressedScale = 0.99f, haptic = haptic, role = role, isChecked = checked, onClick = onClick)
                else Modifier,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(30.dp).background(iconColor, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                RIcon(icon, size = 17.dp, tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RecipaiType.cell, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = RecipaiType.bodySmall, color = LocalColors.current.mute, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** The design's switch: basil when on, white knob, springy travel. */
@Composable
fun RSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "switch")
    Box(
        modifier
            .width(51.dp)
            .height(31.dp)
            .background(if (checked) c.basil else c.surface3, CircleShape)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** The dashed "nothing here" card with a plate icon. */
@Composable
fun EmptyState(icon: Ico, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalColors.current
    Column(
        modifier
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .fillMaxWidth()
            .border(1.5.dp, c.lineStrong, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(76.dp)
                .background(c.surface3, CircleShape)
                .padding(9.dp)
                .background(c.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) { RIcon(icon, size = 24.dp, tint = c.faint) }
        Text(title, style = RecipaiType.secTitle, color = c.ink)
        Text(body, style = RecipaiType.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = c.mute)
        if (action != null) {
            Spacer(Modifier.height(2.dp))
            action()
        }
    }
}

/** Glass circle button drawn over photos (card heart, detail top bar). */
@Composable
fun GlassButton(
    icon: Ico,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    tint: Color = Color.White,
    glass: Boolean = true,
    haptic: HapticType? = HapticType.LIGHT,
) {
    val c = LocalColors.current
    Box(
        modifier
            .tap(pressedScale = 0.88f, haptic = haptic, description = label, onClick = onClick)
            .size(size)
            .then(if (glass) Modifier.background(c.glass, CircleShape).border(1.dp, c.glassLine, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { RIcon(icon, size = iconSize, tint = tint) }
}
