package com.blackchapel.recipai.ui.backup

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.tap
import com.blackchapel.recipai.ui.photos.RecipeImage
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.delay

/** The page's side gutter (the prototype's --gutter). */
internal val Gutter = 18.dp

/** The current time, ticking every 30 seconds so "2 minutes ago" stays true (as in the prototype). */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Uppercase section heading with an optional count ("BACKUPS IN YOUR DRIVE  4"). */
@Composable
internal fun SecLabel(text: String, count: Int? = null) {
    val c = LocalColors.current
    Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Text(text.uppercase(), style = RecipaiType.groupLabel, color = c.faint)
        if (count != null) Text("$count", style = RecipaiType.mono, color = c.faint)
    }
}

/** A rounded surface card (the prototype's .bk-row / .bk-toggles / .bk-hist). */
@Composable
internal fun Card(modifier: Modifier = Modifier, radius: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(LocalColors.current.surface),
        content = content,
    )
}

@Composable
internal fun RowDivider(start: Dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(LocalColors.current.lineStrong))
}

/** A line of help text with an icon (next backup, warnings, errors). */
@Composable
internal fun Note(icon: Ico, text: String, color: Color, modifier: Modifier = Modifier, bold: String? = null, after: String = "") {
    val c = LocalColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RIcon(icon, size = 15.dp, tint = color)
        Text(
            buildAnnotatedString {
                append(text)
                if (bold != null) {
                    pushStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold))
                    append(bold)
                    pop()
                }
                append(after)
            },
            style = RecipaiType.bodySmall.copy(fontSize = 13.5.sp),
            color = color,
        )
    }
}

/** The "Waiting for Google sign-in" spinner: a ring with a saffron and paprika arc. */
@Composable
internal fun Spinner(size: Dp = 52.dp) {
    val c = LocalColors.current
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn",
    )
    Canvas(Modifier.size(size).graphicsLayer { rotationZ = turn }) {
        val w = 4.dp.toPx()
        val inset = w / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
        val topLeft = Offset(inset, inset)
        drawArc(c.surface3, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
        drawArc(c.saffron, -135f, 90f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Butt))
        drawArc(c.paprika, -45f, 90f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Butt))
    }
}

/**
 * The not-connected hero: the flame cloud with a few recipe pictures
 * floating up into it (the prototype's `to-cloud` loop).
 */
@Composable
internal fun BackupHero(recipes: List<Recipe>) {
    val c = LocalColors.current
    val loop by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "float",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(c.ocean.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width / 2, size.height),
                        radius = size.width * 0.6f,
                    ),
                )
            },
    ) {
        val width = maxWidth
        recipes.take(3).forEachIndexed { i, r ->
            // Each picture runs the same 3.6 s loop, a third of a loop apart.
            val t = (loop + i / 3f) % 1f
            val k = floatKeyframes(t)
            RecipeImage(
                r,
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = width * (0.16f + i * 0.26f), y = 14.dp)
                    .graphicsLayer {
                        translationY = k.y.dp.toPx()
                        scaleX = k.scale
                        scaleY = k.scale
                        rotationZ = k.rotate
                        alpha = k.alpha
                    }
                    .size(58.dp)
                    .border(2.dp, c.surface, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(80.dp)
                .shadow(18.dp, RoundedCornerShape(25.dp), ambientColor = c.paprika, spotColor = c.paprika)
                .background(FlameBrush, RoundedCornerShape(25.dp)),
            contentAlignment = Alignment.Center,
        ) { RIcon(Ico.Cloud, size = 40.dp, tint = OnFlame) }
    }
}

private class FloatFrame(val y: Float, val scale: Float, val rotate: Float, val alpha: Float)

/**
 * The prototype's `to-cloud` keyframes at [t] (0..1): rise from below,
 * fade in by 25%, shrink toward the cloud by 70%, vanish at the top.
 * [FloatFrame.y] is in dp.
 */
private fun floatKeyframes(t: Float): FloatFrame {
    fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
    return if (t < 0.7f) {
        val f = FastOutSlowInEasing.transform(t / 0.7f)
        FloatFrame(lerp(30f, -38f, f), lerp(0.9f, 0.7f, f), lerp(-6f, 4f, f), (t / 0.25f).coerceAtMost(1f))
    } else {
        val f = FastOutSlowInEasing.transform((t - 0.7f) / 0.3f)
        FloatFrame(lerp(-38f, -70f, f), lerp(0.7f, 0.4f, f), lerp(4f, 0f, f), 1f - f)
    }
}

/** One row of a radio group: a ring that fills with saffron when chosen. */
@Composable
internal fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val ring by animateDpAsState(if (selected) 7.dp else 2.dp, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "radio")
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .tap(pressedScale = 0.99f, haptic = HapticType.SELECTION, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(ring, if (selected) c.saffron else c.lineStrong, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = RecipaiType.ui, color = c.ink)
            Text(subtitle, style = RecipaiType.bodySmall.copy(fontSize = 12.5.sp), color = c.mute)
        }
    }
}

/** The weekday picker: seven equal buttons, the chosen one inverted and a touch larger. */
@Composable
internal fun DayPicker(selectedIsoDay: Int, days: List<Int>, label: (Int) -> String, onSelect: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { day ->
            val on = day == selectedIsoDay
            val scale by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "day")
            Box(
                Modifier
                    .weight(1f)
                    .semantics { selected = on }
                    .tap(pressedScale = 0.94f, haptic = HapticType.SELECTION, role = Role.RadioButton) { onSelect(day) }
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .height(38.dp)
                    .background(if (on) c.cta else c.surface2, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(day), style = RecipaiType.uiSmall.copy(fontSize = 13.sp), color = if (on) c.onCta else c.mute, maxLines = 1)
            }
        }
    }
}

/** A full-width "select" (the prototype's .select-pill in a .bk-row) that opens a picker. */
@Composable
internal fun SelectPill(value: String, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(pressedScale = 0.98f, haptic = HapticType.LIGHT, label = label, onClick = onClick)
            .height(42.dp)
            .background(c.surface2, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = RecipaiType.uiSmall, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        RIcon(Ico.Down, size = 15.dp, tint = c.mute)
    }
}

/** The 6 dp progress bar with the flame fill. */
@Composable
internal fun ProgressBar(fraction: Float) {
    val c = LocalColors.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(200, easing = LinearEasing), label = "progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(c.surface3),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(6.dp)
                .background(FlameBrush),
        )
    }
}

/** A small colored pill with an icon ("Connected"), like the prototype's .tag. */
@Composable
internal fun StatusPill(text: String, icon: Ico, color: Color) {
    val c = LocalColors.current
    Row(
        Modifier
            .height(24.dp)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RIcon(icon, size = 13.dp, tint = c.tagText(color))
        Text(text, style = RecipaiType.tag, color = c.tagText(color), maxLines = 1)
    }
}

/** The account's initial on the flame gradient. */
@Composable
internal fun Avatar(name: String) {
    Box(Modifier.size(42.dp).background(FlameBrush, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "G",
            style = RecipaiType.secTitle.copy(fontSize = 18.sp),
            color = OnFlame,
        )
    }
}

/** A solid danger button for confirm sheets (no icon). */
@Composable
internal fun SolidDangerButton(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .tap(haptic = HapticType.WARNING, onClick = onClick)
            .height(50.dp)
            .background(c.danger, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = RecipaiType.ui.copy(fontSize = 16.sp), color = Color.White) }
}

@Composable
internal fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
internal fun HSpace(w: Dp) = Spacer(Modifier.width(w))
