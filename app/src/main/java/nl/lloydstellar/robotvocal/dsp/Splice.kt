package nl.lloydstellar.robotvocal.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * Making a vowel longer or shorter without touching the consonants around it.
 * Longer: the steady middle is repeated in whole pitch periods, joined with
 * short crossfades. Shorter: the middle is cut out, start and end are joined.
 * Monotone robot voices take this without audible seams.
 */
object Splice {
    private const val FADE = 192 // 4 ms at 48 kHz

    fun fit(vowel: FloatArray, target: Int, rate: Int): FloatArray {
        if (target <= 0) return FloatArray(0)
        if (vowel.isEmpty()) return FloatArray(target)
        return when {
            target == vowel.size -> vowel
            target < vowel.size -> shorten(vowel, target)
            else -> lengthen(vowel, target, rate)
        }
    }

    private fun shorten(v: FloatArray, target: Int): FloatArray {
        val head = target / 2
        val tail = target - head
        val out = FloatArray(target)
        System.arraycopy(v, 0, out, 0, head)
        System.arraycopy(v, v.size - tail, out, head, tail)
        crossfade(out, head, v, head, min(FADE, min(head, tail)))
        return out
    }

    private fun lengthen(v: FloatArray, target: Int, rate: Int): FloatArray {
        val from = (v.size * 0.3).toInt()
        val to = (v.size * 0.7).toInt()
        if (to - from < FADE * 2) return v.copyOf(target)
        val period = period(v, from, to, rate)
        val loop = max(period, ((to - from) / period) * period)
        val tail = v.size - to
        val out = FloatArray(target)
        var n = to
        System.arraycopy(v, 0, out, 0, to)
        var end = to // where in v the sound written so far would naturally go on
        while (n + tail < target) {
            val count = min(loop, target - tail - n)
            join(out, n, v, end, to - loop, count)
            n += count
            end = to - loop + count
        }
        join(out, n, v, end, to, min(tail, target - n))
        return out
    }

    /**
     * Writes [count] samples of [src] from [start] at [at] in [out]. The first
     * few milliseconds fade over from [continuation], where the sound before the
     * jump would have gone on, so the jump has no seam.
     */
    private fun join(out: FloatArray, at: Int, src: FloatArray, continuation: Int, start: Int, count: Int) {
        val fade = min(FADE, count)
        for (i in 0 until count) {
            val next = src[start + i]
            out[at + i] = if (i < fade && continuation + i < src.size) {
                val w = i.toFloat() / fade
                src[continuation + i] * (1 - w) + next * w
            } else next
        }
    }

    /** The pitch period in samples of the steady part, found by autocorrelation (2.5 to 15 ms). */
    fun period(v: FloatArray, from: Int, to: Int, rate: Int): Int {
        val minLag = rate / 400
        val maxLag = min(rate / 66, (to - from) / 2)
        if (maxLag <= minLag) return max(1, to - from)
        var best = minLag
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var sum = 0.0
            var energy = 1e-9
            for (i in from until to - lag) {
                sum += v[i] * v[i + lag]
                energy += v[i + lag] * v[i + lag]
            }
            val score = sum / energy
            if (score > bestScore) {
                bestScore = score
                best = lag
            }
        }
        return best
    }

    /** Smooths the join at [at] in [out] by fading from what [src] had there before the cut. */
    private fun crossfade(out: FloatArray, at: Int, src: FloatArray, srcAt: Int, length: Int) {
        for (i in 0 until length) {
            val w = i.toFloat() / length
            val k = at + i
            if (k < out.size && srcAt + i < src.size) out[k] = src[srcAt + i] * (1 - w) + out[k] * w
        }
    }
}
