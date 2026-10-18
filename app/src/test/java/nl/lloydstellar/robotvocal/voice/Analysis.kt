package nl.lloydstellar.robotvocal.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Measurements on rendered speech at 48 kHz, for the voice tests. Windows are given by their centre sample. */
object Analysis {
    const val RATE = 48_000

    fun rms(x: FloatArray, centre: Int, n: Int = 1920): Double {
        var sum = 0.0
        for (i in centre - n / 2 until centre + n / 2) sum += x[i] * x[i]
        return sqrt(sum / n)
    }

    fun db(r: Double) = 20 * log10(r)

    /** Spectral centroid of a 40 ms window, in Hz. */
    fun centroid(x: FloatArray, centre: Int): Double {
        val n = 1920
        var num = 0.0
        var den = 0.0
        for (k in 1 until 400) {
            val f = k * 20.0
            var re = 0.0
            var im = 0.0
            for (i in 0 until n) {
                val v = x[centre - n / 2 + i] * (0.5 - 0.5 * cos(2 * PI * i / n))
                re += v * cos(2 * PI * f * i / RATE)
                im -= v * sin(2 * PI * f * i / RATE)
            }
            val m = hypot(re, im)
            num += f * m
            den += m
        }
        return num / den
    }

    /** F1 and F2 of a 40 ms window: LPC of order 12 after down-sampling to 8 kHz, peaks of its envelope. */
    fun formants(x: FloatArray, centre: Int): Pair<Double, Double> {
        val down = 6
        val n = 320
        val y = DoubleArray(n) { j ->
            var s = 0.0
            for (k in 0 until down) s += x[centre - n * down / 2 + j * down + k]
            s / down
        }
        for (i in n - 1 downTo 1) y[i] -= 0.9 * y[i - 1]
        for (i in 0 until n) y[i] *= 0.54 - 0.46 * cos(2 * PI * i / (n - 1))
        val order = 12
        val r = DoubleArray(order + 1) { lag -> (0 until n - lag).sumOf { y[it] * y[it + lag] } }
        val a = DoubleArray(order + 1).also { it[0] = 1.0 }
        var err = r[0]
        for (i in 1..order) {
            var acc = r[i]
            for (j in 1 until i) acc += a[j] * r[i - j]
            val k = -acc / err
            val prev = a.copyOf()
            for (j in 1 until i) a[j] = prev[j] + k * prev[i - j]
            a[i] = k
            err *= 1 - k * k
        }
        val fs = RATE / down.toDouble()
        val env = (0..390).map { step ->
            val f = 100.0 + step * 10.0
            var re = 0.0
            var im = 0.0
            for (j in 0..order) {
                re += a[j] * cos(2 * PI * f * j / fs)
                im -= a[j] * sin(2 * PI * f * j / fs)
            }
            f to 1.0 / hypot(re, im)
        }
        val peaks = env.indices.filter { it in 1 until env.size - 1 && env[it].second > env[it - 1].second && env[it].second >= env[it + 1].second }
            .map { env[it].first }
        return peaks[0] to peaks[1]
    }

    /**
     * The pitch of a 60 ms window, in Hz, between 50 and 600 Hz: the strongest
     * normalised autocorrelation peak, refined with a parabola. Only the
     * window's first half is shifted, so every lag compares equal lengths.
     */
    fun pitch(x: FloatArray, centre: Int): Double {
        val n = 2880
        val from = centre - n / 2
        val half = n / 2
        var best = 0
        var bestR = -1.0
        val r = DoubleArray(RATE / 50 + 2)
        for (lag in RATE / 600..RATE / 50) {
            var xy = 0.0
            var xx = 0.0
            var yy = 0.0
            for (i in 0 until half) {
                val a = x[from + i].toDouble()
                val b = x[from + i + lag].toDouble()
                xy += a * b
                xx += a * a
                yy += b * b
            }
            r[lag] = xy / sqrt(xx * yy + 1e-30)
        }
        for (lag in RATE / 600 + 1 until RATE / 50) {
            /* the first peak that comes close to the best avoids picking a multiple of the period */
            if (r[lag] > r[lag - 1] && r[lag] >= r[lag + 1] && r[lag] > bestR + 0.02) {
                best = lag
                bestR = r[lag]
            }
        }
        val (a, b, c) = Triple(r[best - 1], r[best], r[best + 1])
        val shift = 0.5 * (a - c) / (a - 2 * b + c)
        return RATE / (best + shift)
    }
}
