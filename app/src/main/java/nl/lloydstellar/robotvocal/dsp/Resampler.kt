package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Sample-rate conversion with a windowed sinc (16 zero crossings, Blackman
 * window). Going down, the cutoff follows the target rate, so nothing folds
 * back. The speech engines deliver 8 to 22.05 kHz and the app works at 48 kHz.
 * The kernel is tabulated once, 512 points per zero crossing, and read with
 * linear interpolation.
 */
object Resampler {
    private const val ZEROS = 16
    private const val STEPS = 512
    private val TABLE = FloatArray(ZEROS * STEPS + 2) { i -> kernel(i.toDouble() / STEPS).toFloat() }

    fun convert(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input.copyOf()
        val ratio = to.toDouble() / from
        val cutoff = min(1.0, ratio)
        val half = ZEROS / cutoff
        val out = FloatArray(ceil(input.size * ratio).toInt())
        for (i in out.indices) {
            val x = i / ratio
            val first = ceil(x - half).toInt().coerceAtLeast(0)
            val last = floor(x + half).toInt().coerceAtMost(input.size - 1)
            var sum = 0.0
            for (j in first..last) {
                val pos = abs(x - j) * cutoff * STEPS
                val k = pos.toInt()
                if (k >= ZEROS * STEPS) continue
                val f = (pos - k).toFloat()
                sum += input[j] * (TABLE[k] + (TABLE[k + 1] - TABLE[k]) * f)
            }
            out[i] = (sum * cutoff).toFloat()
        }
        return out
    }

    /** The windowed sinc at distance [t] (in zero crossings) from the centre. */
    private fun kernel(t: Double): Double {
        if (t >= ZEROS) return 0.0
        val sinc = if (t == 0.0) 1.0 else sin(PI * t) / (PI * t)
        val w = (t / ZEROS + 1.0) * 0.5
        return sinc * (0.42 - 0.5 * cos(2 * PI * w) + 0.08 * cos(4 * PI * w))
    }
}
