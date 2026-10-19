package com.blackchapel.recipai.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The timer chime from the prototype: three rising sine notes (A5, D6, G6)
 * 0.22 s apart, each with a 20 ms attack and a half-second exponential
 * decay, played [repeats] times. Rendered as 16-bit mono PCM so the app
 * can play it with no sound file.
 */
object Chime {
    private val notes = listOf(0.0 to 880.0, 0.22 to 1175.0, 0.44 to 1568.0)
    private const val NOTE_LENGTH = 0.55
    private const val ATTACK = 0.02
    private const val DECAY_END = 0.5
    private const val PEAK = 0.3
    private const val FLOOR = 0.0001 / 0.18

    /** Seconds between the starts of two repeats. */
    const val REPEAT_GAP = 1.25

    fun durationSeconds(repeats: Int): Double = (repeats - 1) * REPEAT_GAP + notes.last().first + NOTE_LENGTH

    fun pcm(sampleRate: Int = 44_100, repeats: Int = 2): ShortArray {
        require(sampleRate > 0 && repeats > 0)
        val n = (durationSeconds(repeats) * sampleRate).roundToInt()
        val out = DoubleArray(n)
        val decayRate = -ln(FLOOR) / (DECAY_END - ATTACK)
        for (r in 0 until repeats) {
            for ((delay, freq) in notes) {
                val start = ((r * REPEAT_GAP + delay) * sampleRate).roundToInt()
                val len = (NOTE_LENGTH * sampleRate).roundToInt()
                for (i in 0 until len) {
                    val idx = start + i
                    if (idx >= n) break
                    val t = i.toDouble() / sampleRate
                    val env = if (t < ATTACK) t / ATTACK else exp(-decayRate * (t - ATTACK))
                    out[idx] += PEAK * env * sin(2 * PI * freq * t)
                }
            }
        }
        return ShortArray(n) { i -> (out[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort() }
    }
}
