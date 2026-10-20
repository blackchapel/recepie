package com.blackchapel.recipai.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackchapel.recipai.ui.components.Ico
import com.blackchapel.recipai.ui.components.RIcon
import com.blackchapel.recipai.ui.components.TagPill
import com.blackchapel.recipai.ui.theme.FlameBrush
import com.blackchapel.recipai.ui.theme.LocalColors
import com.blackchapel.recipai.ui.theme.OnBasil
import com.blackchapel.recipai.ui.theme.OnFlame
import com.blackchapel.recipai.ui.theme.RecipaiColors
import com.blackchapel.recipai.ui.theme.RecipaiType

/**
 * The tour's illustrations. Each is drawn at [ArtSize] square and scaled
 * to fit by the page; they are built from the app's own components and
 * theme tokens, so they match the real screens and follow light and dark.
 */
internal val ArtSize: Dp = 300.dp

/** Page 1: a recipe being scaled, amounts and times following the 2x chip. */
@Composable
internal fun ScaleArt() {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            glow(c, Offset(size.width / 2, size.height / 2), size.minDimension * 0.52f)
            sparkle(Offset(size.width * 0.9f, size.height * 0.12f), size.minDimension * 0.06f, c.saffron)
            sparkle(Offset(size.width * 0.07f, size.height * 0.3f), size.minDimension * 0.035f, c.sumac)
            sparkle(Offset(size.width * 0.93f, size.height * 0.82f), size.minDimension * 0.03f, c.ocean)
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .width(246.dp)
                .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(c.surface, RoundedCornerShape(24.dp))
                .border(1.dp, c.line, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Plate(40.dp)
                Column {
                    Text("Tomato soup", style = RecipaiType.rowTitle, color = c.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("4", style = RecipaiType.mono.copy(textDecoration = TextDecoration.LineThrough), color = c.faint)
                        Text("8 servings", style = RecipaiType.mono, color = c.accentInk)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("½×", "1×", "2×").forEachIndexed { i, label -> ScaleChip(label, selected = i == 2) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2)) {
                AmountRow("Tomatoes", "400 g", "800 g")
                Box(Modifier.fillMaxWidth().padding(start = 12.dp).height(0.5.dp).background(c.lineStrong))
                AmountRow("Olive oil", "1 tbsp", "2 tbsp")
                Box(Modifier.fillMaxWidth().padding(start = 12.dp).height(0.5.dp).background(c.lineStrong))
                AmountRow("Stock", "1 cup", "2 cups")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeChip("28 min")
                Text("20 min", style = RecipaiType.monoSmall.copy(textDecoration = TextDecoration.LineThrough), color = c.faint)
            }
        }
    }
}

/** Page 2: messy copied text goes in, a tidy recipe comes out. */
@Composable
internal fun ImportArt() {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            glow(c, Offset(size.width / 2, size.height / 2), size.minDimension * 0.52f)
            sparkle(Offset(size.width * 0.92f, size.height * 0.06f), size.minDimension * 0.05f, c.saffron)
            sparkle(Offset(size.width * 0.06f, size.height * 0.62f), size.minDimension * 0.03f, c.ocean)
        }
        // The pasted text.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 8.dp)
                .width(236.dp)
                .graphicsLayer { rotationZ = -2.5f }
                .background(c.surface2, RoundedCornerShape(16.dp))
                .border(1.dp, c.line, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RIcon(Ico.Clip, size = 14.dp, tint = c.mute)
                Text("Pasted text", style = RecipaiType.label, color = c.faint)
            }
            listOf("Tomato soup ★★★★★ 212 reviews", "2 tbsp olive oil, 1 onion", "Simmer 20 minutes until thick", "Advertisement").forEach {
                Text(it, style = RecipaiType.textarea.copy(fontSize = RecipaiType.monoSmall.fontSize), color = c.mute, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
        // The converter.
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-4).dp)
                .size(46.dp)
                .shadow(14.dp, CircleShape, ambientColor = c.paprika, spotColor = c.paprika)
                .background(FlameBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) { RIcon(Ico.Spark, size = 24.dp, tint = OnFlame) }
        // The recipe it made.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .width(246.dp)
                .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                .background(c.surface, RoundedCornerShape(22.dp))
                .border(1.dp, c.line, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Plate(32.dp)
                Column(Modifier.weight(1f)) {
                    Text("Tomato soup", style = RecipaiType.rowTitle, color = c.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        TagPill("dinner")
                        TagPill("vegetarian")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AmountPill("2 tbsp", "olive oil")
                AmountPill("1", "onion")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(22.dp).background(c.surface3, CircleShape), contentAlignment = Alignment.Center) {
                    Text("1", style = RecipaiType.monoSmall, color = c.ink)
                }
                Text("Simmer until thick", style = RecipaiType.bodySmall, color = c.body, modifier = Modifier.weight(1f), maxLines = 1)
                TimeChip("20 min")
            }
        }
    }
}

/** Page 3: recipes flowing up to a cloud, with a safe tick. */
@Composable
internal fun BackupArt() {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            glow(c, Offset(size.width / 2, size.height * 0.42f), size.minDimension * 0.55f)
            val w = size.width
            val h = size.height
            // The cloud: overlapping circles and a base, one shape, one gradient.
            val cloud = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.22f, h * 0.2f), Size(w * 0.3f, w * 0.3f)))
                addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.36f, h * 0.1f), Size(w * 0.36f, w * 0.36f)))
                addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.54f, h * 0.2f), Size(w * 0.26f, w * 0.26f)))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.14f, h * 0.3f, w * 0.86f, h * 0.5f, CornerRadius(w * 0.1f)))
            }
            drawPath(cloud, Brush.linearGradient(listOf(c.ocean.copy(alpha = 0.95f), c.ube.copy(alpha = 0.9f)), Offset(w * 0.15f, h * 0.1f), Offset(w * 0.85f, h * 0.5f)))
            // Dashed lines from the recipes up to the cloud.
            val dash = Stroke(width = 2.2f * density, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 8f * density)))
            for (x in listOf(0.27f, 0.5f, 0.73f)) {
                drawLine(c.faint.copy(alpha = 0.7f), Offset(w * x, h * 0.74f), Offset(w * x, h * 0.56f), strokeWidth = dash.width, cap = StrokeCap.Round, pathEffect = dash.pathEffect)
            }
            sparkle(Offset(w * 0.9f, h * 0.1f), w * 0.05f, c.saffron)
            sparkle(Offset(w * 0.08f, h * 0.18f), w * 0.03f, c.sumac)
            sparkle(Offset(w * 0.93f, h * 0.62f), w * 0.028f, c.basil)
        }
        // The tick on the cloud.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 62.dp)
                .size(44.dp)
                .shadow(8.dp, CircleShape)
                .background(c.basil, CircleShape),
            contentAlignment = Alignment.Center,
        ) { RIcon(Ico.Check, size = 24.dp, tint = OnBasil) }
        // Recipes waiting to go up.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf("Soup", "Pasta", "Cake").forEach { MiniRecipe(it) }
        }
        // The privacy note.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .background(c.surface, CircleShape)
                .border(1.dp, c.line, CircleShape)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RIcon(Ico.Lock, size = 13.dp, tint = c.mute)
            Text("Your Drive, your files", style = RecipaiType.monoSmall, color = c.mute)
        }
    }
}

@Composable
private fun MiniRecipe(title: String) {
    val c = LocalColors.current
    Column(
        Modifier
            .width(76.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Plate(34.dp)
        Text(title, style = RecipaiType.tag, color = c.ink, maxLines = 1)
    }
}

/** A small round plate, the app's stand-in for a recipe picture. */
@Composable
private fun Plate(size: Dp) {
    val c = LocalColors.current
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(c.surface, c.surface3), Offset(r * 0.7f, r * 0.6f), r * 1.4f), r)
        drawCircle(c.lineStrong, r, style = Stroke(width = r * 0.06f))
        drawCircle(Brush.radialGradient(listOf(c.saffron, c.paprika), Offset(r * 0.8f, r * 0.8f), r), r * 0.58f, Offset(r, r))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.1f, Offset(r * 0.8f, r * 0.8f))
    }
}

@Composable
private fun RowScope.ScaleChip(label: String, selected: Boolean) {
    val c = LocalColors.current
    Box(
        Modifier
            .weight(1f)
            .height(30.dp)
            .background(if (selected) c.saffron.copy(alpha = 0.22f) else c.surface2, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) c.saffron.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = RecipaiType.mono, color = if (selected) c.ink else c.mute) }
}

@Composable
private fun AmountRow(name: String, from: String, to: String) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = RecipaiType.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
        Text(from, style = RecipaiType.monoSmall.copy(textDecoration = TextDecoration.LineThrough), color = c.faint)
        Spacer(Modifier.width(6.dp))
        Text(to, style = RecipaiType.amount.copy(fontSize = RecipaiType.mono.fontSize), color = c.accentInk)
    }
}

@Composable
private fun AmountPill(amount: String, name: String) {
    val c = LocalColors.current
    Row(
        Modifier.height(24.dp).background(c.surface2, CircleShape).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(amount, style = RecipaiType.monoSmall, color = c.ink)
        Text(name, style = RecipaiType.tag, color = c.mute)
    }
}

@Composable
private fun TimeChip(text: String) {
    val c = LocalColors.current
    Row(
        Modifier.height(28.dp).background(c.surface2, CircleShape).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RIcon(Ico.Clock, size = 14.dp, tint = c.ink)
        Text(text, style = RecipaiType.mono, color = c.ink)
    }
}

/** A soft pool of warmth behind an illustration. */
private fun DrawScope.glow(c: RecipaiColors, center: Offset, radius: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(c.saffron.copy(alpha = if (c.isDark) 0.16f else 0.22f), Color.Transparent),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
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
