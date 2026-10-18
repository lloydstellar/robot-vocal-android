package nl.lloydstellar.robotvocal.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * A look-ahead peak limiter for stereo blocks, for the preview, where the
 * vocal and the beat are added and a vowel on a kick could pass full scale.
 *
 * It looks [lookaheadMs] ahead: the gain is the lowest one needed anywhere in
 * the window ahead, averaged over that window, so it ramps down in a straight
 * line and is already low enough when the peak arrives. Then it comes back up
 * over [releaseMs]. Nothing passes the ceiling and nothing clicks; the price
 * is a delay of the look-ahead, two milliseconds.
 */
class Limiter(rate: Int, private val ceiling: Float = CEILING, lookaheadMs: Double = 2.0, releaseMs: Double = 80.0) {
    private val n = max(1, (rate * lookaheadMs / 1000).toInt())
    private val release = (1 - exp(-1000.0 / (releaseMs * rate))).toFloat()

    /* the last n inputs, the last n window minimums with their sum, and the gain */
    private val left = FloatArray(n)
    private val right = FloatArray(n)
    private val held = FloatArray(n) { 1f }
    private var sum = n.toDouble()
    private var gain = 1f
    private var pos = 0
    private var t = 0L

    /* the window minimum: a deque of (time, gain) with rising gains */
    private val cap = n + 1
    private val qTime = LongArray(cap)
    private val qGain = FloatArray(cap)
    private var head = 0
    private var size = 0

    /** Limits the first [frames] frames of [block], interleaved left and right, in place. */
    fun process(block: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val l = block[2 * i]
            val r = block[2 * i + 1]
            val peak = max(abs(l), abs(r))
            val lowest = windowMin(if (peak > ceiling) ceiling / peak else 1f)
            sum += lowest - held[pos]
            held[pos] = lowest
            val target = minOf(1f, (sum / n).toFloat())
            gain = if (target < gain) target else gain + (target - gain) * release
            left[pos] = l
            right[pos] = r
            val out = (pos + 1) % n
            block[2 * i] = left[out] * gain
            block[2 * i + 1] = right[out] * gain
            pos = out
            t++
        }
    }

    /** The lowest gain needed in the last n samples, the newest being [need]. */
    private fun windowMin(need: Float): Float {
        while (size > 0 && qGain[(head + size - 1) % cap] >= need) size--
        val tail = (head + size) % cap
        qTime[tail] = t
        qGain[tail] = need
        size++
        if (qTime[head] <= t - n) {
            head = (head + 1) % cap
            size--
        }
        return qGain[head]
    }

    companion object {
        /** -0.1 dBFS. */
        val CEILING = 10.0.pow(-0.1 / 20).toFloat()
    }
}
