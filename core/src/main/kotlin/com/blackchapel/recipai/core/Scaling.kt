package com.blackchapel.recipai.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * An ingredient amount as shown at some scale factor.
 *
 * @property text the amount, e.g. "1½" (empty when the ingredient has none)
 * @property unit the unit to show, possibly converted and pluralized
 * @property toStoredUnit multiply a number typed in [unit] by this to get the
 *   ingredient's stored unit (needed when the user edits a converted amount)
 */
data class ScaledAmount(
    val text: String,
    val unit: String,
    val toStoredUnit: Double,
    val value: Double?,
)

object Scaling {
    private val volumeInTsp = mapOf("tsp" to 1.0, "tbsp" to 3.0, "cup" to 48.0)
    private val metric = mapOf("g" to ("g" to 1.0), "kg" to ("g" to 1000.0), "ml" to ("ml" to 1.0), "l" to ("ml" to 1000.0))
    private val plurals = mapOf(
        "cup" to "cups", "clove" to "cloves", "can" to "cans", "bunch" to "bunches", "pinch" to "pinches",
        "sprig" to "sprigs", "handful" to "handfuls", "slice" to "slices", "stick" to "sticks", "sheet" to "sheets",
        "block" to "blocks", "piece" to "pieces", "dash" to "dashes", "leaf" to "leaves", "cube" to "cubes", "wedge" to "wedges",
    )

    /** The singular unit for a possibly pluralized one ("cups" -> "cup"). */
    fun singular(unit: String): String =
        plurals.entries.firstOrNull { it.value == unit.lowercase() }?.key ?: unit.lowercase()

    private fun nice(q: Double, unit: String): String = when (unit) {
        "g", "ml" -> when {
            q < 10 -> Quantities.plain(Math.round(q * 2) / 2.0)
            q < 100 -> Math.round(q).toString()
            else -> (Math.round(q / 5) * 5).toString()
        }
        "kg", "l" -> Quantities.trimmed(q, 2)
        else -> Quantities.toFraction(q)
    }

    /** Formats [q] for [unit] the same way scaled amounts are shown. */
    fun format(q: Double, unit: String): String = nice(q, unit.lowercase())

    /**
     * The amount of [ingredient] at [factor]. When scaled, spoons and cups step
     * up or down (3 tsp -> 1 tbsp, 12 tsp -> ¼ cup) and metric crosses
     * g/kg and ml/l at 1000. Unscaled amounts keep the unit they were entered in.
     */
    fun amount(ingredient: Ingredient, factor: Double): ScaledAmount {
        val unit = ingredient.unit.trim()
        val stored = ingredient.quantity ?: return ScaledAmount("", unit, 1.0, null)
        var q = stored * factor
        var u = unit
        val lower = unit.lowercase()
        var mult = 1.0
        if (abs(factor - 1) > 1e-6) {
            val tsp = volumeInTsp[lower]
            val metricBase = metric[lower]
            if (tsp != null) {
                val inTsp = q * tsp
                u = when { inTsp >= 12 -> "cup"; inTsp >= 3 -> "tbsp"; else -> "tsp" }
                val per = volumeInTsp.getValue(u)
                q = inTsp / per
                mult = per / tsp
            } else if (metricBase != null) {
                val (base, k) = metricBase
                val inBase = q * k
                if (inBase >= 1000) {
                    u = if (base == "g") "kg" else "l"; q = inBase / 1000; mult = 1000 / k
                } else {
                    u = base; q = inBase; mult = 1 / k
                }
            }
        }
        val text = nice(q, u.lowercase())
        val shown = Quantities.parse(text) ?: q
        val plural = plurals[u.lowercase()]
        return ScaledAmount(text, if (shown > 1.06 && plural != null) plural else u, mult, q)
    }

    /** The factor that makes [ingredient] come out as [typed] [shownIn] units. */
    fun factorFor(ingredient: Ingredient, typed: Double, shownIn: ScaledAmount): Double? {
        val stored = ingredient.quantity ?: return null
        if (stored <= 0 || typed <= 0) return null
        return typed * shownIn.toStoredUnit / stored
    }

    /** Minutes for a step at [factor]: halves under 10 minutes, whole minutes above. */
    fun minutes(minutes: Double, scale: TimeScale, factor: Double): Double {
        if (minutes <= 0) return 0.0
        val v = when (scale) {
            TimeScale.LINEAR -> minutes * factor
            TimeScale.PARTIAL -> minutes * sqrt(factor)
            TimeScale.FIXED -> minutes
        }
        return if (v < 10) Math.round(v * 2) / 2.0 else Math.round(v).toDouble()
    }

    fun totalMinutes(recipe: Recipe, factor: Double = 1.0): Double =
        recipe.steps.sumOf { minutes(it.minutes, it.timeScale, factor) }

    /** "6½ min", "1 h 16 min", "30 s"; empty for no time. */
    fun formatMinutes(m: Double): String {
        if (m <= 0) return ""
        if (m < 1) return "${Math.round(m * 60)} s"
        val v = if (m < 10) Math.round(m * 2) / 2.0 else Math.round(m).toDouble()
        if (v < 60) return "${Quantities.toFraction(v)} min"
        val h = floor(v / 60).toLong()
        val mm = Math.round(v - h * 60)
        return if (mm != 0L) "$h h $mm min" else "$h h"
    }

    /** Countdown text: "9:05", "1:02:30". */
    fun formatClock(seconds: Double): String {
        val s = maxOf(0L, ceil(seconds).toLong())
        val h = s / 3600
        val m = (s % 3600) / 60
        val x = s % 60
        val mins = if (h > 0) "$h:" + m.toString().padStart(2, '0') else m.toString()
        return mins + ":" + x.toString().padStart(2, '0')
    }

    /** Servings at a factor, to the nearest quarter: "4", "4½". */
    fun formatServings(servings: Int, factor: Double): String =
        Quantities.toFraction(Math.round(servings * factor * 4) / 4.0)

    /** The factor for one more (+1) or one fewer (-1) whole serving. */
    fun stepServings(servings: Int, factor: Double, direction: Int): Double {
        val current = servings * factor
        val next = if (direction > 0) floor(current + 1e-6) + 1 else ceil(current - 1e-6) - 1
        return maxOf(1.0, next) / servings
    }
}
