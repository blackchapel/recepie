package com.blackchapel.recipai.ui.importer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackchapel.recipai.core.Links
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.IconButton
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiType

/** Green-on-basil check and the tick inside it (prototype `.pipeline li.ok .st`). */
private val OnBasil = Color(0xFF062615)

/**
 * The "From a link" tab: the incoming-link card when one was shared in, the
 * link field with Convert, the step-by-step progress, a clear error with
 * ways forward, and how sharing to Recipai works.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LinkSource(app: AppController, onUseText: () -> Unit) {
    val c = LocalColors.current
    val link = app.linkImport
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var noLinkOnClipboard by remember { mutableStateOf(false) }
    val convert = {
        keyboard?.hide()
        focus.clearFocus()
        noLinkOnClipboard = false
        app.convertLink()
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Keep the last link while the card animates away.
        var lastShared by remember { mutableStateOf(link.shared.orEmpty()) }
        link.shared?.let { lastShared = it }
        AnimatedVisibility(
            visible = link.shared != null,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { -it / 2 },
            exit = fadeOut(tween(200)) + shrinkVertically(tween(250)),
        ) { IncomingLink(lastShared) }

        UrlBox(
            value = app.importUrl,
            onValueChange = { app.importUrl = it },
            running = link.running,
            onConvert = convert,
            onClear = {
                app.importUrl = ""
                link.reset()
            },
        )
        if (app.importUrl.isEmpty() && !link.running) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RButton(
                    "Paste link",
                    {
                        val url = clipboard.getText()?.text?.let(Links::findUrl)
                        noLinkOnClipboard = url == null
                        if (url != null) {
                            app.importUrl = url
                            convert()
                        }
                    },
                    icon = Ico.Clip,
                    style = ButtonStyle.GHOST,
                    small = true,
                )
                if (noLinkOnClipboard) {
                    Text("There’s no link on the clipboard. Copy one from your browser first.", style = RecipaiType.bodySmall, color = c.danger)
                }
            }
        }

        if (link.steps.isNotEmpty()) Pipeline(link.steps)

        var lastError by remember { mutableStateOf(link.error.orEmpty()) }
        link.error?.let { lastError = it }
        AnimatedVisibility(
            visible = link.error != null,
            enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    lastError,
                    style = RecipaiType.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                    color = c.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.danger.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                if (link.steps.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val text = link.pageText
                        if (text != null) {
                            RButton("Edit the page text", {
                                app.importText = text
                                onUseText()
                            }, icon = Ico.Pencil, small = true)
                        }
                        RButton("Paste text instead", onUseText, icon = Ico.Clip, small = true)
                        if (link.canRetry) RButton("Try again", convert, icon = Ico.Reset, style = ButtonStyle.GHOST, small = true)
                    }
                }
            }
        }

        ShareCard()
    }
}

@Composable
private fun UrlBox(value: String, onValueChange: (String) -> Unit, running: Boolean, onConvert: () -> Unit, onClear: () -> Unit) {
    val c = LocalColors.current
    val style = RecipaiType.textarea.copy(fontSize = 15.sp, lineHeight = 20.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RIcon(Ico.Link, tint = c.mute)
        Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("https://…", style = style, color = c.faint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = !running,
                textStyle = style.copy(color = if (running) c.mute else c.ink),
                cursorBrush = SolidColor(c.saffron),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (value.isNotBlank()) onConvert() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty() && !running) {
            IconButton(Ico.X, "Clear link", onClear, size = 32.dp, iconSize = 15.dp, tint = c.mute)
        }
        RButton("Convert", onConvert, icon = Ico.Spark, style = ButtonStyle.FLAME, small = true, enabled = !running && value.isNotBlank())
    }
}

/** The progress list (prototype `.pipeline`). */
@Composable
private fun Pipeline(steps: List<StepState>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinkImport.STEPS.forEachIndexed { i, label -> PipelineRow(label, steps.getOrElse(i) { StepState.WAITING }) }
    }
}

@Composable
private fun PipelineRow(label: String, state: StepState) {
    val c = LocalColors.current
    val bg by animateColorAsState(if (state == StepState.ACTIVE) c.surface2 else c.surface2.copy(alpha = 0f), tween(300), label = "pipe-bg")
    val fg by animateColorAsState(
        when (state) {
            StepState.WAITING -> c.faint
            StepState.ACTIVE -> c.ink
            StepState.DONE -> c.body
            StepState.FAILED -> c.danger
        },
        tween(300),
        label = "pipe-fg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepDot(state)
        Text(label, style = RecipaiType.body.copy(fontSize = 14.sp, lineHeight = 18.sp), color = fg)
    }
}

@Composable
private fun StepDot(state: StepState) {
    val c = LocalColors.current
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        when (state) {
            StepState.WAITING -> Box(Modifier.fillMaxSize().border(1.5.dp, c.lineStrong, CircleShape))
            StepState.ACTIVE -> {
                val spin by rememberInfiniteTransition(label = "spin").animateFloat(
                    0f,
                    360f,
                    infiniteRepeatable(tween(800, easing = LinearEasing)),
                    label = "spin-angle",
                )
                val color = c.saffron
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 1.5.dp.toPx()
                    val inset = stroke / 2
                    drawArc(
                        color = color,
                        startAngle = spin,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                }
            }
            StepState.DONE, StepState.FAILED -> {
                // The prototype's `pop`: a springy scale-in when the step settles.
                val pop = remember { Animatable(0.6f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
                val done = state == StepState.DONE
                Box(
                    Modifier
                        .fillMaxSize()
                        .scale(pop.value)
                        .background(if (done) c.basil else c.danger, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RIcon(if (done) Ico.Check else Ico.X, size = 12.dp, tint = if (done) OnBasil else Color.White)
                }
            }
        }
    }
}

/** A link that just arrived from the share sheet (prototype `.incoming`). */
@Composable
private fun IncomingLink(url: String) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface2, shape)
            .border(1.dp, c.lineStrong, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(FlameBrush, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            RIcon(Ico.Share, tint = OnFlame)
        }
        Column(Modifier.weight(1f)) {
            Text("Link shared to Recipai", style = RecipaiType.ui.copy(fontSize = 14.sp), color = c.ink, maxLines = 1)
            Text(
                url.substringAfter("://"),
                style = RecipaiType.mono,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShareCard() {
    val c = LocalColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RIcon(Ico.Share, tint = c.ink)
            Text("Share links straight to Recipai", style = RecipaiType.cardTitle, color = c.ink)
        }
        Text(
            "Tap Share in your browser or any app and pick Recipai. The link arrives here and converts on its own.",
            style = RecipaiType.bodySmall,
            color = c.mute,
        )
    }
}

/**
 * The import sheet's feedback for the link tab: a link arriving from the share
 * sheet (medium haptic, [onShared]), and how a conversion ended.
 */
@Composable
internal fun LinkHaptics(link: LinkImport, perform: (HapticType) -> Unit, onShared: () -> Unit) {
    LaunchedEffect(link.shareHapticPending) {
        if (link.shareHapticPending) {
            link.shareHapticPending = false
            onShared()
            perform(HapticType.MEDIUM)
        }
    }
    // Only outcomes that happen while the sheet is open, not one from before it opened.
    val initial = remember { link.outcome }
    LaunchedEffect(link.outcome) {
        val o = link.outcome
        if (o != null && o != initial) perform(if (o.ok) HapticType.SELECTION else HapticType.ERROR)
    }
}
