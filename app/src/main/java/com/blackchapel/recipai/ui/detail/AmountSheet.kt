package com.blackchapel.recipai.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Quantities
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.haptics.HapticType
import com.blackchapel.recipai.haptics.LocalHaptics
import com.blackchapel.recipai.ui.Sheet
import com.blackchapel.recipai.ui.components.ButtonStyle
import com.blackchapel.recipai.ui.components.FilterChip
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.IconButton
import com.blackchapel.recipai.ui.components.RButton
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.RSheet
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import kotlin.math.roundToLong

/**
 * Type (or step) the amount of one ingredient and the whole recipe scales to
 * match, step times included.
 */
@Composable
fun AmountSheet(sheet: Sheet.Amount, recipe: Recipe, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val page = sheet.page
    val ing = recipe.ingredients.firstOrNull { it.id == sheet.ingredientId }
    val stored = ing?.quantity
    if (ing == null || stored == null || stored <= 0) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val shown = remember(ing, page.factor) { Scaling.amount(ing, page.factor) }
    val unitKey = Scaling.singular(shown.unit)
    val step = when (unitKey) {
        "g", "ml" -> if ((shown.value ?: 0.0) >= 200) 25.0 else 5.0
        "kg", "l" -> 0.1
        else -> 0.25
    }
    var text by remember { mutableStateOf(shown.text) }
    val typed = Quantities.parse(text)?.takeIf { it > 0 }
    val factor = typed?.let { Scaling.factorFor(ing, it, shown) }

    fun nudge(direction: Int) {
        val now = (Quantities.parse(text) ?: 0.0) + direction * step
        val snapped = maxOf(step, (now / step).roundToLong() * step)
        text = Scaling.format(snapped, unitKey)
    }

    RSheet(onDismiss, title = ing.name.replaceFirstChar { it.uppercase() }) { closer ->
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStepButton(Ico.Minus, "Decrease amount") { nudge(-1) }
                Row(
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .background(c.canvas, RoundedCornerShape(20.dp))
                        .border(1.5.dp, c.saffron.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = RecipaiType.amountBig.copy(color = c.ink, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(c.saffron),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (factor != null) {
                                haptics.perform(HapticType.SELECTION)
                                page.setScale(factor, ing.id)
                                closer.close()
                            }
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    if (shown.unit.isNotEmpty()) {
                        Text(
                            shown.unit,
                            style = RecipaiType.mono.copy(fontSize = RecipaiType.secTitle.fontSize.times(17f / 19f)),
                            color = c.mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp),
                        )
                    }
                }
                BigStepButton(Ico.Plus, "Increase amount") { nudge(1) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5 to "½×", 1.0 to "Original", 2.0 to "2×", 3.0 to "3×").forEach { (x, label) ->
                    FilterChip(label, selected = false, toggle = false, onClick = {
                        page.setScale(x)
                        closer.close()
                    })
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .background(c.surface2, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (factor == null) {
                    Text("Enter an amount like 2, 1.5 or 1 ½.", style = RecipaiType.bodySmall, color = c.mute)
                } else {
                    RIcon(Ico.Spark, size = 15.dp, tint = c.mute)
                    val time = Scaling.formatMinutes(Scaling.totalMinutes(recipe, factor)).ifEmpty { "no timers" }
                    Text(
                        buildAnnotatedString {
                            val strong = SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)
                            append("Everything scales ")
                            withStyle(strong) { append("${Quantities.trimmed(factor, 2)}×") }
                            append(" · ")
                            withStyle(strong) { append(Scaling.formatServings(recipe.servings, factor)) }
                            append(" ${recipe.servingsLabel ?: "servings"} · ")
                            withStyle(strong) { append(time) }
                        },
                        style = RecipaiType.bodySmall,
                        color = c.mute,
                    )
                }
            }

            RButton(
                "Scale recipe",
                {
                    if (factor != null) {
                        page.setScale(factor, ing.id)
                        closer.close()
                    }
                },
                Modifier.fillMaxWidth(),
                icon = Ico.Spark,
                style = ButtonStyle.FLAME,
                height = 52.dp,
                enabled = factor != null,
                haptic = HapticType.SELECTION,
            )
        }
    }
}

@Composable
private fun BigStepButton(icon: Ico, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    IconButton(
        icon, label, onClick,
        size = 56.dp, iconSize = 24.dp, background = c.surface2, shape = RoundedCornerShape(18.dp), tint = c.ink, haptic = HapticType.SELECTION,
    )
}
