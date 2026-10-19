package com.blackchapel.recipai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.core.Recipe
import com.blackchapel.recipai.core.Scaling
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiType
import java.time.LocalDateTime
import java.util.Locale

/** "4 servings", "12 cookies". */
fun servingsText(r: Recipe): String = "${r.servings} ${r.servingsLabel ?: "servings"}"

/** "35 min", or [none] when no step has a time. */
fun timeText(r: Recipe, none: String = "No timings"): String = Scaling.formatMinutes(Scaling.totalMinutes(r)).ifEmpty { none }

/** "Monday evening" for the Recipes eyebrow. */
fun greeting(now: LocalDateTime = LocalDateTime.now()): String {
    val day = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val part = when (now.hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }
    return "$day $part"
}

/** The glass time badge on cards and the detail hero. */
@Composable
fun TimeBadge(text: String, modifier: Modifier = Modifier, icon: Ico = Ico.Clock) {
    val c = LocalColors.current
    Row(
        modifier
            .height(24.dp)
            .background(c.glass, CircleShape)
            .border(1.dp, c.glassLine, CircleShape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RIcon(icon, size = 13.dp, tint = Color.White)
        Text(text, style = RecipaiType.monoSmall, color = Color.White)
    }
}

/** Small colored dots for a recipe's tags (list rows). */
@Composable
fun TagDots(tags: List<String>, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.take(6).forEach { Box(Modifier.size(7.dp).background(c.tag(it), CircleShape)) }
    }
}
