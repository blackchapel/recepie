package com.blackchapel.recipai.ui.importer

import com.blackchapel.recipai.ui.photos.RecipeImage
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.ImportSamples
import com.blackchapel.recipai.core.ParsedRecipe
import com.blackchapel.recipai.core.RecipeSource
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.core.TextRecipeParser
import com.blackchapel.recipai.core.toRecipe
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.AppController
import com.blackchapel.recipai.ui.ImportTab
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RSheet
import com.blackchapel.recipai.ui.components.SegLabel
import com.blackchapel.recipai.ui.components.Segmented
import com.blackchapel.recipai.ui.components.TagPill
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Paste a recipe and see it parsed live (rule-based: [TextRecipeParser]),
 * or convert a recipe page from a link ([LinkImport]), then review or save it.
 */
@Composable
fun ImportSheet(app: AppController, sheet: Sheet.Import) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val link = app.linkImport
    var tab by remember(sheet) { mutableStateOf(sheet.tab) }
    // A shared link also lands here when the sheet was already open on "Paste text".
    LinkHaptics(link, haptics::perform, onShared = { tab = ImportTab.LINK })
    var parsed by remember { mutableStateOf<ParsedRecipe?>(null) }
    LaunchedEffect(app.importText) {
        val text = app.importText
        if (text.isBlank()) {
            parsed = null
            return@LaunchedEffect
        }
        delay(180) // debounce typing
        parsed = withContext(Dispatchers.Default) { TextRecipeParser.parse(text) }
    }
    val p = if (tab == ImportTab.TEXT) parsed else link.result

    RSheet({ app.sheet = null }, title = "Import a recipe") { _ ->
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recipai reads it and turns it into a recipe you can scale and time.", style = RecipaiType.bodySmall, color = c.mute)
                Segmented(count = 2, selected = tab.ordinal, onSelect = { tab = ImportTab.entries[it] }, modifier = Modifier.fillMaxWidth()) { i, _ ->
                    RIcon(if (i == 0) Ico.Clip else Ico.Link, size = 15.dp)
                    SegLabel(if (i == 0) "Paste text" else "From a link")
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (tab == ImportTab.TEXT) TextSource(app) else LinkSource(app) { tab = ImportTab.TEXT }
                if (tab == ImportTab.TEXT) {
                    Preview(p, busy = app.importText.isNotBlank() && p == null, busyLabel = "Reading…")
                } else {
                    Preview(
                        p,
                        busy = link.running,
                        busyLabel = "Reading the page…",
                        note = if (p != null && link.via == RecipeSource.PAGE_TEXT) {
                            "This page has no recipe card, so Recipai read its text. Check it before saving."
                        } else {
                            null
                        },
                    )
                }
            }
            // Footer
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.lineStrong))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val source = p?.source
                val status = when {
                    p == null -> ""
                    !p.isUsable -> "Add a title line and at least one ingredient or step."
                    source != null -> "From $source"
                    else -> "Looks good? Save it to your recipes."
                }
                Text(status, style = RecipaiType.bodySmall, color = c.mute, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                RButton(
                    "Review",
                    { p?.let { app.reviewImport(it) } },
                    icon = Ico.Pencil,
                    enabled = p != null && (p.title.isNotBlank() || p.ingredients.isNotEmpty() || p.steps.isNotEmpty()),
                )
                RButton(
                    "Save",
                    {
                        val ready = p ?: return@RButton
                        if (tab == ImportTab.LINK) app.saveLinkImport(ready) else app.saveImport(ready)
                    },
                    icon = Ico.Check,
                    style = ButtonStyle.FLAME,
                    enabled = p?.isUsable == true,
                    haptic = HapticType.SUCCESS,
                )
            }
        }
    }
}

@Composable
private fun TextSource(app: AppController) {
    val c = LocalColors.current
    val clipboard = LocalClipboardManager.current
    // Inline, not a toast: toasts draw under the sheet.
    var clipboardEmpty by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (app.importText.isEmpty()) {
            RButton(
                "Paste from clipboard",
                {
                    val clip = clipboard.getText()?.text.orEmpty()
                    clipboardEmpty = clip.isBlank()
                    if (!clipboardEmpty) app.importText = clip
                },
                Modifier.fillMaxWidth(),
                icon = Ico.Clip,
                style = ButtonStyle.CTA,
            )
            if (clipboardEmpty) Text("The clipboard is empty. Copy a recipe first, or type it below.", style = RecipaiType.bodySmall, color = c.danger)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recipe text", style = RecipaiType.uiSmall.copy(fontSize = RecipaiType.bodySmall.fontSize), color = c.mute, modifier = Modifier.weight(1f))
            RButton("Try a sample", { app.importText = ImportSamples.TOFU_BOWLS }, icon = Ico.Spark, style = ButtonStyle.GHOST, small = true)
            if (app.importText.isNotEmpty()) {
                RButton("Clear", { app.importText = "" }, icon = Ico.X, style = ButtonStyle.GHOST, small = true)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp)
                .background(c.surface2, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (app.importText.isEmpty()) {
                Text(
                    "Paste a recipe here, straight from a website, a message or your notes. Headings like Ingredients and Method help, but they're optional…",
                    style = RecipaiType.textarea,
                    color = c.faint,
                )
            }
            BasicTextField(
                value = app.importText,
                onValueChange = { app.importText = it },
                textStyle = RecipaiType.textarea.copy(color = c.ink),
                cursorBrush = SolidColor(c.saffron),
                modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp, max = 320.dp),
            )
        }
    }
}

@Composable
private fun Preview(p: ParsedRecipe?, busy: Boolean, busyLabel: String, note: String? = null) {
    val c = LocalColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.canvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (p == null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(52.dp).background(c.surface2, CircleShape), contentAlignment = Alignment.Center) {
                    if (busy) {
                        // The prototype's slow spin while reading.
                        val spin by rememberInfiniteTransition(label = "reading").animateFloat(
                            0f,
                            360f,
                            infiniteRepeatable(tween(1400, easing = LinearEasing)),
                            label = "reading-spin",
                        )
                        RIcon(Ico.Spark, Modifier.graphicsLayer { rotationZ = spin }, size = 24.dp, tint = c.accentInk)
                    } else {
                        RIcon(Ico.Chef, size = 24.dp, tint = c.faint)
                    }
                }
                Text(if (busy) busyLabel else "Your recipe appears here", style = RecipaiType.ui, color = c.ink)
                if (!busy) {
                    Text(
                        "Recipai finds the title, servings, each ingredient with its amount and unit, and the steps with their timings.",
                        style = RecipaiType.bodySmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        color = c.mute,
                    )
                }
            }
        } else {
            if (note != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.saffron.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .border(1.dp, c.saffron.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RIcon(Ico.Info, size = 16.dp, tint = c.accentInk)
                    Text(note, style = RecipaiType.bodySmall, color = c.body)
                }
            }
            PreviewBody(p)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewBody(p: ParsedRecipe) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val recipe = remember(p) { p.toRecipe(now = 0) { "preview" } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecipeImage(recipe, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { "Untitled recipe" }, style = RecipaiType.secTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.description.isNotBlank()) Text(p.description, style = RecipaiType.bodySmall, color = c.mute, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatPill("${recipe.itemCount} ingredients")
            StatPill("${p.steps.size} steps")
            StatPill("${p.timerCount} timers")
            StatPill("${p.servings} ${p.servingsLabel ?: "servings"}" + if (p.servingsGuessed) " (guessed)" else "")
            if (p.skippedLines > 0) StatPill("Skipped ${p.skippedLines} lines of clutter", warn = true)
        }
        if (p.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.tags.forEach { TagPill(it) }
            }
        }
        if (recipe.ingredients.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("INGREDIENTS", style = RecipaiType.label, color = c.faint)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(vertical = 4.dp)) {
                    recipe.ingredients.forEach { ing ->
                        if (ing.isHeading) {
                            Text(ing.name.uppercase(), style = RecipaiType.label, color = c.faint, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp))
                        } else {
                            val a = Scaling.amount(ing, 1.0)
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (a.text.isEmpty()) "—" else listOf(a.text, a.unit).filter { it.isNotEmpty() }.joinToString(" "),
                                    style = RecipaiType.mono,
                                    color = if (a.text.isEmpty()) c.faint else c.ink,
                                    modifier = Modifier.width(76.dp),
                                )
                                Text(
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        append(ing.name)
                                        if (ing.note.isNotBlank()) {
                                            append(" ")
                                            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.mute, fontStyle = FontStyle.Italic))
                                            append(ing.note)
                                            pop()
                                        }
                                    },
                                    style = RecipaiType.bodySmall,
                                    color = c.body,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (recipe.steps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("METHOD", style = RecipaiType.label, color = c.faint)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(vertical = 4.dp)) {
                    recipe.steps.forEachIndexed { i, s ->
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}", style = RecipaiType.mono, color = c.faint, modifier = Modifier.width(16.dp))
                            Text(s.text, style = RecipaiType.bodySmall, color = c.body, modifier = Modifier.weight(1f))
                            if (s.minutes > 0) Text(Scaling.formatMinutes(s.minutes), style = RecipaiType.mono, color = c.accentInk)
                        }
                    }
                }
            }
        }
        if (p.notes.isNotBlank()) {
            Column(
                Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("NOTES", style = RecipaiType.label, color = c.faint)
                Text(p.notes, style = RecipaiType.bodySmall, color = c.body)
            }
        }
    }
}

@Composable
private fun StatPill(text: String, warn: Boolean = false) {
    val c = LocalColors.current
    val color = if (warn) c.accentInk else c.body
    Box(
        Modifier
            .height(26.dp)
            .background(if (warn) c.saffron.copy(alpha = 0.12f) else c.surface, CircleShape)
            .border(1.dp, if (warn) c.saffron.copy(alpha = 0.3f) else c.line, CircleShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = RecipaiType.monoSmall, color = color, maxLines = 1) }
}
