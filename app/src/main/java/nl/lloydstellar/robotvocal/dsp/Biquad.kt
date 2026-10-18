package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A second-order filter with the coefficients of Robert Bristow-Johnson's audio EQ cookbook. */
class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun lowPass(freq: Double, q: Double, rate: Int): Biquad {
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun highPass(freq: Double, q: Double, rate: Int): Biquad {
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        /** Band-pass with 0 dB at the centre. */
        fun bandPass(freq: Double, q: Double, rate: Int): Biquad {
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return make(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
        }
    }
}

/** White noise from a seeded linear congruential generator: the same seed, the same noise. */
class Noise(seed: Int) {
    private var state = seed.toLong() and 0xFFFFFFFFL

    fun next(): Double {
        state = (state * 1_103_515_245L + 12_345L) and 0xFFFFFFFFL
        return ((state ushr 8).toDouble() / (1 shl 24)) * 2.0 - 1.0
    }
}
