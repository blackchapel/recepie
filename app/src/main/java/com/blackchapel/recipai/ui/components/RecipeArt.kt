package com.blackchapel.recipai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.blackchapel.recipai.core.Recipe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A generated top-down plate, seeded from the recipe so it never changes
 * between launches. A simpler take on the prototype's canvas paintings
 * (docs/DESIGN.md calls these a nice-to-have); shown until photos exist.
 */
@Composable
fun RecipeArt(recipe: Recipe, modifier: Modifier = Modifier) {
    val plan = remember(recipe.id, recipe.ingredients) { ArtPlan.of(recipe) }
    Canvas(modifier) { plan.draw(this) }
}

/** Food item kinds, picked from ingredient names. */
private enum class Kind { BALL, LEAF, CHUNK, FLECK, EGG, CITRUS, GRAIN, CUBE }

private data class Item(val kind: Kind, val color: Color, val x: Float, val y: Float, val size: Float, val angle: Float)

private class ArtPlan(
    val table: Color,
    val plate: Color,
    val base: Color,
    val baseIsBowl: Boolean,
    val items: List<Item>,
    val tableAngle: Float,
) {
    fun draw(d: DrawScope) = with(d) {
        val w = size.width
        val h = size.height
        // Table with a soft light falloff.
        drawRect(Brush.linearGradient(listOf(shade(table, 0.12f), shade(table, -0.18f)), Offset.Zero, Offset(w, h)))
        drawLinesOnTable(this, tableAngle)
        val cx = w / 2
        val cy = h / 2
        val r = min(w, h) * 0.42f
        // Plate shadow, plate, rim.
        drawCircle(Color.Black.copy(alpha = 0.28f), r * 1.02f, Offset(cx + r * 0.05f, cy + r * 0.09f))
        drawCircle(Brush.radialGradient(listOf(shade(plate, 0.1f), plate, shade(plate, -0.12f)), Offset(cx - r * 0.2f, cy - r * 0.25f), r * 1.3f), r, Offset(cx, cy))
        drawCircle(shade(plate, -0.08f), r * 0.8f, Offset(cx, cy), style = Stroke(width = r * 0.02f))
        // The food base: sauce, rice, broth...
        val baseR = r * (if (baseIsBowl) 0.74f else 0.62f)
        drawPath(blob(cx, cy, baseR, items.size), Brush.radialGradient(listOf(shade(base, 0.22f), base, shade(base, -0.2f)), Offset(cx - baseR * 0.3f, cy - baseR * 0.3f), baseR * 1.4f))
        for (it in items) drawItem(this, it, cx + it.x * baseR, cy + it.y * baseR, it.size * r)
    }

    companion object {
        private val tables = listOf(Color(0xFF2B2420), Color(0xFF3A2C22), Color(0xFF243031), Color(0xFF332B3A), Color(0xFF3B3226), Color(0xFF26302A))
        private val plates = listOf(Color(0xFFF1ECE2), Color(0xFFE8E1D3), Color(0xFF2E3A40), Color(0xFFD9D3C7), Color(0xFF3A3532))

        private data class Rule(val words: List<String>, val kind: Kind, val color: Color)

        private val rules = listOf(
            // Seasonings first, so "black pepper" is a fleck, not a bell pepper.
            Rule(listOf("black pepper", "salt", "sesame", "seed", "flakes", "cumin", "spice"), Kind.FLECK, Color(0xFFEDE3C8)),
            Rule(listOf("egg"), Kind.EGG, Color(0xFFF5A516)),
            Rule(listOf("tomato", "chili", "chile", "pepper", "gochujang", "paprika", "strawberr", "raspberr"), Kind.BALL, Color(0xFFD8402A)),
            Rule(listOf("blueberr", "blackberr", "plum", "olive"), Kind.BALL, Color(0xFF3F3A72)),
            Rule(listOf("basil", "cilantro", "parsley", "spinach", "scallion", "spring onion", "mint", "herb", "kale", "lettuce", "dill", "chive", "cucumber", "lime", "pea"), Kind.LEAF, Color(0xFF3E9E52)),
            Rule(listOf("lemon", "orange", "grapefruit"), Kind.CITRUS, Color(0xFFF2C230)),
            Rule(listOf("tofu", "feta", "paneer", "halloumi", "cheese", "mozzarella"), Kind.CUBE, Color(0xFFF2E8D2)),
            Rule(listOf("rice", "orzo", "oat", "quinoa", "couscous", "noodle", "pasta", "flour", "sugar"), Kind.GRAIN, Color(0xFFF1E6CC)),
            Rule(listOf("chicken", "beef", "pork", "lamb", "salmon", "fish", "shrimp", "prawn", "mushroom", "potato", "carrot", "squash", "pumpkin", "onion", "shallot", "garlic", "chocolate", "banana"), Kind.CHUNK, Color(0xFFC98B4F)),
        )

        private val baseColors = listOf(Color(0xFFB8452B), Color(0xFFE2C48A), Color(0xFF7FA35A), Color(0xFFD99A3E), Color(0xFF8F5A3C), Color(0xFFE9DFC8))

        fun of(recipe: Recipe): ArtPlan {
            val rnd = Rng(fnv(recipe.id + recipe.title))
            val names = recipe.ingredients.filter { !it.isHeading }.map { it.name.lowercase() }
            val picked = names.mapNotNull { n -> rules.firstOrNull { rule -> rule.words.any { it in n } } }.distinct().take(5)
            val items = mutableListOf<Item>()
            val kinds = picked.ifEmpty { listOf(rules[4], rules[2], rules[0]) }
            for ((i, rule) in kinds.withIndex()) {
                val count = when (rule.kind) {
                    Kind.EGG -> 3
                    Kind.CITRUS -> 1
                    Kind.FLECK -> 18
                    Kind.GRAIN -> 22
                    Kind.LEAF -> 6
                    else -> 5
                }
                repeat(count) {
                    // Spread items on rings so they rarely pile up.
                    val a = rnd.next() * 2 * PI.toFloat()
                    val dist = sqrt(rnd.next()) * 0.8f
                    val size = when (rule.kind) {
                        Kind.EGG -> 0.17f
                        Kind.CITRUS -> 0.2f
                        Kind.FLECK -> 0.012f + rnd.next() * 0.01f
                        Kind.GRAIN -> 0.022f
                        Kind.LEAF -> 0.085f + rnd.next() * 0.04f
                        Kind.CUBE -> 0.065f
                        else -> 0.06f + rnd.next() * 0.03f
                    }
                    items += Item(rule.kind, shade(rule.color, (rnd.next() - 0.5f) * 0.18f), cos(a) * dist, sin(a) * dist, size, rnd.next() * 360f + i)
                }
            }
            // Big items first, garnish last, so garnish sits on top.
            items.sortBy { if (it.kind == Kind.FLECK) 2 else if (it.kind == Kind.LEAF) 1 else 0 }
            val grainy = kinds.any { it.kind == Kind.GRAIN }
            return ArtPlan(
                table = tables[rnd.int(tables.size)],
                plate = plates[rnd.int(plates.size)],
                base = if (grainy) Color(0xFFEFE4CB) else baseColors[rnd.int(baseColors.size)],
                baseIsBowl = rnd.next() > 0.5f,
                items = items,
                tableAngle = rnd.next() * 30f - 15f,
            )
        }
    }
}

private fun drawLinesOnTable(d: DrawScope, angle: Float) = with(d) {
    rotate(angle) {
        val step = size.height / 7
        var y = -size.height
        while (y < size.height * 2) {
            drawLine(Color.White.copy(alpha = 0.035f), Offset(-size.width, y), Offset(size.width * 2, y), strokeWidth = step * 0.08f)
            y += step
        }
    }
}

private fun drawItem(d: DrawScope, it: Item, x: Float, y: Float, s: Float) = with(d) {
    val shadow = Color.Black.copy(alpha = 0.25f)
    when (it.kind) {
        Kind.BALL -> {
            drawCircle(shadow, s, Offset(x + s * 0.12f, y + s * 0.2f))
            drawCircle(Brush.radialGradient(listOf(shade(it.color, 0.35f), it.color, shade(it.color, -0.38f)), Offset(x - s * 0.35f, y - s * 0.4f), s * 1.3f), s, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.45f), s * 0.18f, Offset(x - s * 0.35f, y - s * 0.35f))
        }
        Kind.EGG -> {
            drawCircle(shadow, s, Offset(x + s * 0.1f, y + s * 0.14f))
            drawPath(blob(x, y, s, (it.angle * 10).toInt()), Brush.radialGradient(listOf(Color.White, Color(0xFFECE3CF)), Offset(x - s * 0.3f, y - s * 0.3f), s * 1.2f))
            drawCircle(Brush.radialGradient(listOf(shade(it.color, 0.45f), it.color, shade(it.color, -0.3f)), Offset(x - s * 0.15f, y - s * 0.18f), s * 0.55f), s * 0.43f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.6f), s * 0.09f, Offset(x - s * 0.14f, y - s * 0.16f))
        }
        Kind.LEAF -> rotate(it.angle, Offset(x, y)) {
            val p = Path().apply {
                moveTo(x - s, y)
                quadraticTo(x, y - s * 0.62f, x + s, y)
                quadraticTo(x, y + s * 0.62f, x - s, y)
                close()
            }
            drawPath(p, Brush.linearGradient(listOf(shade(it.color, 0.18f), shade(it.color, -0.2f)), Offset(x, y - s * 0.4f), Offset(x, y + s * 0.4f)))
            drawLine(shade(it.color, 0.3f), Offset(x - s * 0.85f, y), Offset(x + s * 0.8f, y), strokeWidth = s * 0.06f)
        }
        Kind.CHUNK -> {
            drawPath(blob(x + s * 0.1f, y + s * 0.18f, s, (it.angle * 7).toInt(), 0.28f), shadow)
            drawPath(blob(x, y, s, (it.angle * 7).toInt(), 0.28f), Brush.radialGradient(listOf(shade(it.color, 0.28f), shade(it.color, -0.2f)), Offset(x - s * 0.3f, y - s * 0.3f), s * 1.2f))
        }
        Kind.CUBE -> rotate(it.angle, Offset(x, y)) {
            drawRoundRect(shadow, Offset(x - s + s * 0.15f, y - s * 0.85f + s * 0.2f), Size(s * 2, s * 1.7f), androidx.compose.ui.geometry.CornerRadius(s * 0.35f))
            drawRoundRect(Brush.linearGradient(listOf(shade(it.color, 0.2f), shade(it.color, -0.12f)), Offset(x - s, y - s), Offset(x + s, y + s)), Offset(x - s, y - s * 0.85f), Size(s * 2, s * 1.7f), androidx.compose.ui.geometry.CornerRadius(s * 0.35f))
        }
        Kind.CITRUS -> {
            drawCircle(shadow, s, Offset(x + s * 0.1f, y + s * 0.15f))
            drawCircle(it.color, s, Offset(x, y))
            drawCircle(Color(0xFFFFF6E4), s * 0.9f, Offset(x, y))
            val flesh = shade(it.color, 0.25f)
            for (k in 0 until 9) {
                val a0 = it.angle + k * 40f + 3f
                drawArc(flesh, a0, 34f, useCenter = true, topLeft = Offset(x - s * 0.84f, y - s * 0.84f), size = Size(s * 1.68f, s * 1.68f))
            }
        }
        Kind.GRAIN -> rotate(it.angle, Offset(x, y)) {
            drawOval(it.color.copy(alpha = 0.85f), Offset(x - s * 1.7f, y - s * 0.75f), Size(s * 3.4f, s * 1.5f))
        }
        Kind.FLECK -> drawCircle(it.color, s, Offset(x, y))
    }
}

/** A wobbly circle, like the prototype's blobPath. */
private fun blob(cx: Float, cy: Float, r: Float, seed: Int, irregular: Float = 0.08f): Path {
    val rnd = Rng(seed.toUInt() * 2654435761u + 1u)
    val ph = FloatArray(3) { rnd.next() * 6f }
    val pts = 48
    return Path().apply {
        for (i in 0..pts) {
            val a = i.toFloat() / pts * 2 * PI.toFloat()
            val rr = r * (1 + irregular * (sin(a * 3 + ph[0]) * 0.5f + sin(a * 5 + ph[1]) * 0.3f + sin(a * 8 + ph[2]) * 0.2f))
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

/** Lighten (amt > 0) or darken (amt < 0). */
private fun shade(c: Color, amt: Float): Color = if (amt >= 0) lerp(c, Color.White, amt) else lerp(c, Color.Black, -amt)

private fun fnv(s: String): UInt {
    var h = 2166136261u
    for (ch in s) {
        h = h xor ch.code.toUInt()
        h *= 16777619u
    }
    return h
}

/** Mulberry32, as in the prototype: small, fast and stable across runs. */
private class Rng(seed: UInt) {
    private var a = seed

    fun next(): Float {
        a += 0x6D2B79F5u
        var t = a
        t = (t xor (t shr 15)) * (t or 1u)
        t = t xor (t + (t xor (t shr 7)) * (t or 61u))
        return ((t xor (t shr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }

    fun int(n: Int): Int = (next() * n).toInt().coerceIn(0, n - 1)
}
