package com.blackchapel.recipai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.RecipaiColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/**
 * "No recipes yet": an empty plate waiting for food, with a fork and knife
 * and a few sparkles. Drawn from the theme tokens, so it follows light and
 * dark. Square; the caller sets the width.
 */
@Composable
fun EmptyPlateIllustration(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Canvas(modifier.aspectRatio(1f)) { drawEmptyPlate(c) }
}

private fun DrawScope.drawEmptyPlate(c: RecipaiColors) {
    val s = size.minDimension
    val cx = size.width / 2
    val cy = size.height / 2
    val plateR = s * 0.30f

    // A soft glow behind everything, so the plate sits in a pool of warmth.
    drawCircle(
        Brush.radialGradient(
            listOf(c.saffron.copy(alpha = if (c.isDark) 0.16f else 0.22f), Color.Transparent),
            center = Offset(cx, cy), radius = s * 0.5f,
        ),
        radius = s * 0.5f, center = Offset(cx, cy),
    )

    // Plate: shadow, body, rim, and a dashed well where the food would go.
    drawCircle(Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.14f), plateR * 1.02f, Offset(cx + s * 0.012f, cy + s * 0.028f))
    drawCircle(
        Brush.radialGradient(
            listOf(c.surface, c.surface2, c.surface3),
            center = Offset(cx - plateR * 0.3f, cy - plateR * 0.35f), radius = plateR * 1.4f,
        ),
        plateR, Offset(cx, cy),
    )
    drawCircle(c.lineStrong, plateR, Offset(cx, cy), style = Stroke(width = s * 0.006f))
    drawCircle(
        c.faint.copy(alpha = 0.55f),
        plateR * 0.68f, Offset(cx, cy),
        style = Stroke(width = s * 0.007f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.02f, s * 0.026f))),
    )

    // Fork on the left, knife on the right, slightly angled like a place setting.
    val metal = c.mute.copy(alpha = 0.85f)
    val shade = Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.12f)
    fun fork(offset: Offset, color: Color) {
        val x = cx - plateR - s * 0.115f + offset.x
        val top = cy - s * 0.22f + offset.y
        val gap = s * 0.026f
        val tine = Stroke(width = s * 0.011f, cap = StrokeCap.Round)
        for (i in -1..1) drawLine(color, Offset(x + i * gap, top), Offset(x + i * gap, top + s * 0.12f), strokeWidth = tine.width, cap = StrokeCap.Round)
        val neck = Path().apply {
            moveTo(x - gap, top + s * 0.12f)
            quadraticTo(x - gap, top + s * 0.17f, x, top + s * 0.17f)
            quadraticTo(x + gap, top + s * 0.17f, x + gap, top + s * 0.12f)
        }
        drawPath(neck, color, style = Stroke(width = s * 0.011f, cap = StrokeCap.Round))
        drawLine(color, Offset(x, top + s * 0.17f), Offset(x, top + s * 0.44f), strokeWidth = s * 0.02f, cap = StrokeCap.Round)
    }
    fun knife(offset: Offset, color: Color) {
        val x = cx + plateR + s * 0.115f + offset.x
        val top = cy - s * 0.22f + offset.y
        val blade = Path().apply {
            moveTo(x - s * 0.012f, top)
            quadraticTo(x + s * 0.034f, top + s * 0.07f, x + s * 0.026f, top + s * 0.19f)
            lineTo(x - s * 0.012f, top + s * 0.19f)
            close()
        }
        drawPath(blade, color)
        drawLine(color, Offset(x, top + s * 0.19f), Offset(x, top + s * 0.44f), strokeWidth = s * 0.024f, cap = StrokeCap.Round)
    }
    val drop = Offset(s * 0.008f, s * 0.014f)
    fork(drop, shade)
    knife(drop, shade)
    fork(Offset.Zero, metal)
    knife(Offset.Zero, metal)

    // Sparkles: the plate is ready for something.
    sparkle(Offset(cx + plateR * 0.95f, cy - plateR * 1.05f), s * 0.05f, c.saffron)
    sparkle(Offset(cx - plateR * 1.0f, cy - plateR * 1.2f), s * 0.03f, c.sumac.copy(alpha = 0.9f))
    sparkle(Offset(cx + plateR * 1.15f, cy + plateR * 0.95f), s * 0.026f, c.ocean.copy(alpha = 0.9f))
}

/** A four-point star. */
private fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val k = r * 0.28f
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k, center.y - k, center.x + r, center.y)
        quadraticTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticTo(center.x - k, center.y + k, center.x - r, center.y)
        quadraticTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}

/**
 * The Recipes tab with no recipes: the illustration, a line saying so, and
 * the two ways to add one.
 */
@Composable
fun EmptyLibraryState(
    onImport: () -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyPlateIllustration(Modifier.widthIn(max = 280.dp).fillMaxWidth(0.8f))
        Spacer(Modifier.height(4.dp))
        Text("No recipes yet", style = RecipaiType.sheetTitle, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste a recipe you like, or write your own. Recipai keeps it here and scales it for you.",
            style = RecipaiType.bodySmall.copy(textAlign = TextAlign.Center),
            color = c.mute,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(18.dp))
        Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RButton("Import a recipe", onImport, Modifier.fillMaxWidth(), icon = Ico.Spark, style = ButtonStyle.FLAME, height = 50.dp)
            RButton("Write one yourself", onWrite, Modifier.fillMaxWidth(), icon = Ico.Pencil, height = 50.dp)
        }
    }
}
