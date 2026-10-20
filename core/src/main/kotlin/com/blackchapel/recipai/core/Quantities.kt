package com.blackchapel.recipai.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/** Parsing and kitchen-friendly formatting of amounts like "1 ½" or "2/3". */
object Quantities {
    private val vulgarFractions = mapOf(
        '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75,
        '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8, '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )
    // Whitespace is only absorbed between a whole number and its fraction ("1 ½").
    private val vulgarRe = Regex("(?:(\\d+)[ \\t]*)?([½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞])")
    private val mixedRe = Regex("^(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)$")
    private val simpleFractionRe = Regex("^(\\d+)\\s*/\\s*(\\d+)$")
    private val decimalRe = Regex("^\\d*[.,]?\\d+$")

    /** The fractions shown to cooks, nearest wins. */
    private val displayFractions = listOf(
        0.0 to "", 1.0 / 8 to "⅛", 1.0 / 4 to "¼", 1.0 / 3 to "⅓", 3.0 / 8 to "⅜", 1.0 / 2 to "½",
        5.0 / 8 to "⅝", 2.0 / 3 to "⅔", 3.0 / 4 to "¾", 7.0 / 8 to "⅞", 1.0 to "",
    )

    /** Rewrites vulgar fractions as decimals: "1½ cups" -> "1.5 cups". */
    fun normalizeFractions(s: String): String = vulgarRe.replace(s) { m ->
        val whole = m.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
        plain(Math.round((whole + vulgarFractions.getValue(m.groupValues[2][0])) * 1000) / 1000.0)
    }

    /** Parses "2", "1.5", "1,5", "1/2", "1 1/2", "1½". Null if it isn't a plain amount. */
    fun parse(raw: String?): Double? {
        if (raw == null) return null
        val s = normalizeFractions(raw).trim()
        if (s.isEmpty()) return null
        mixedRe.find(s)?.let {
            val (w, n, d) = it.destructured
            return if (d.toDouble() > 0) w.toDouble() + n.toDouble() / d.toDouble() else null
        }
        simpleFractionRe.find(s)?.let {
            val (n, d) = it.destructured
            return if (d.toDouble() > 0) n.toDouble() / d.toDouble() else null
        }
        if (!decimalRe.matches(s)) return null
        return s.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }

    /** 1.5 -> "1½", 0.333 -> "⅓", 2.0 -> "2". Anything above zero shows at least "⅛". */
    fun toFraction(x: Double): String {
        var whole = floor(x + 1e-9).toLong()
        val rest = x - whole
        var best = displayFractions.first()
        var bestDistance = Double.MAX_VALUE
        for (f in displayFractions) {
            val d = abs(rest - f.first)
            if (d < bestDistance) { bestDistance = d; best = f }
        }
        if (best.first == 1.0) { whole += 1; best = displayFractions.first() }
        if (whole == 0L && best.second.isEmpty()) return if (x > 0) "⅛" else "0"
        return (if (whole != 0L) whole.toString() else "") + best.second
    }

    /** Shortest plain decimal, like JavaScript's String(n): 2.0 -> "2", 1.5 -> "1.5". */
    fun plain(v: Double): String =
        if (v == floor(v) && abs(v) < 1e15) v.toLong().toString()
        else BigDecimal(v.toString()).stripTrailingZeros().toPlainString()

    /** Rounds to at most [places] decimals and drops trailing zeros. */
    fun trimmed(v: Double, places: Int): String =
        BigDecimal(v).setScale(places, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
