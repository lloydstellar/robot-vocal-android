package nl.lloydstellar.robotvocal.dsp

import kotlin.math.exp
import kotlin.math.tanh

/**
 * A delay on the beat: echoes [beats] beats apart (0.75 is a dotted 8th),
 * each one darker and thinner than the one before, as on a tape echo, so the
 * repeats stay behind the voice. With [onlyLineEnds] only the last syllable
 * of every line is sent: the echo throw of rap records.
 */
class BeatDelay(
    private val beats: Double,
    private val feedback: Double = 0.4,
    private val mix: Double = 0.35,
    private val highCut: Double = 4500.0,
    private val lowCut: Double = 250.0,
    private val saturate: Boolean = false,
    private val onlyLineEnds: Boolean = false,
) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val d = ctx.beats(beats).coerceAtLeast(1)
        val send = if (onlyLineEnds) lineEndMask(x.size, ctx) else null
        val line = DoubleArray(x.size)
        val lp = exp(-2 * Math.PI * highCut / ctx.rate)
        val hp = exp(-2 * Math.PI * lowCut / ctx.rate)
        var low = 0.0
        var highIn = 0.0
        var highOut = 0.0
        val out = FloatArray(x.size)
        for (i in x.indices) {
            val echo = if (i >= d) line[i - d] else 0.0
            /* the feedback path: a one-pole low-pass and high-pass, as worn tape does */
            low = (1 - lp) * echo + lp * low
            highOut = hp * (highOut + low - highIn)
            highIn = low
            var back = highOut * feedback
            if (saturate) back = tanh(back * 1.5) / 1.5
            val input = x[i] * (send?.get(i) ?: 1f)
            line[i] = input + back
            out[i] = (x[i] + echo * mix).toFloat()
        }
        return out
    }
}

/** 1 on the last syllable of every line, 0 elsewhere, with 5 ms edges. */
internal fun lineEndMask(size: Int, ctx: FxContext): FloatArray {
    val mask = FloatArray(size)
    val edge = (ctx.rate * 0.005).toInt().coerceAtLeast(1)
    for (range in ctx.lineEnds) {
        val from = (range.first - ctx.origin).toInt().coerceIn(0, size)
        val to = (range.last - ctx.origin).toInt().coerceIn(0, size)
        for (i in from until to) {
            val inFade = ((i - from).toFloat() / edge).coerceAtMost(1f)
            val outFade = ((to - i).toFloat() / edge).coerceAtMost(1f)
            mask[i] = maxOf(mask[i], minOf(inFade, outFade))
        }
    }
    return mask
}

/**
 * A plate reverb after Freeverb (Jezar, public domain): eight damped combs in
 * parallel, four all-passes in a row, tuned for 48 kHz. [gateBeats] above zero
 * cuts the tail that long after the voice stops: the gated reverb of the 1980s.
 */
class Plate(
    private val room: Double = 0.6,
    private val damping: Double = 0.45,
    private val mix: Double = 0.2,
    private val preDelayMs: Double = 12.0,
    private val gateBeats: Double = 0.0,
) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val scale = ctx.rate / 44_100.0
        val combs = COMBS.map { DampedComb((it * scale).toInt(), room * 0.28 + 0.7, damping * 0.4) }
        val allpasses = ALLPASSES.map { AllPass((it * scale).toInt()) }
        val pre = (preDelayMs * ctx.rate / 1000).toInt()
        val wet = FloatArray(x.size)
        for (i in x.indices) {
            val input = (if (i >= pre) x[i - pre] else 0f) * 0.015
            var sum = 0.0
            for (c in combs) sum += c.process(input)
            for (a in allpasses) sum = a.process(sum)
            wet[i] = sum.toFloat()
        }
        val tail = if (gateBeats > 0) gated(wet, x, ctx) else wet
        return FloatArray(x.size) { (x[it] + tail[it] * mix * 3).toFloat() }
    }

    /** The reverb sounds while the voice does and [gateBeats] beats longer, then closes in 10 ms. */
    private fun gated(wet: FloatArray, voice: FloatArray, ctx: FxContext): FloatArray {
        var peak = 1e-9f
        for (v in voice) peak = maxOf(peak, kotlin.math.abs(v))
        val hold = ctx.beats(gateBeats)
        val close = 1.0 / (ctx.rate * 0.01)
        var left = 0
        var level = 0.0
        return FloatArray(wet.size) { i ->
            if (kotlin.math.abs(voice[i]) > peak * 0.02) left = hold else if (left > 0) left--
            level = if (left > 0) 1.0 else (level - close).coerceAtLeast(0.0)
            (wet[i] * level).toFloat()
        }
    }

    private class DampedComb(size: Int, private val feedback: Double, private val damp: Double) {
        private val buffer = DoubleArray(size.coerceAtLeast(1))
        private var index = 0
        private var store = 0.0

        fun process(x: Double): Double {
            val y = buffer[index]
            store = y * (1 - damp) + store * damp
            buffer[index] = x + store * feedback
            index = (index + 1) % buffer.size
            return y
        }
    }

    private class AllPass(size: Int) {
        private val buffer = DoubleArray(size.coerceAtLeast(1))
        private var index = 0

        fun process(x: Double): Double {
            val b = buffer[index]
            buffer[index] = x + b * 0.5
            index = (index + 1) % buffer.size
            return b - x
        }
    }

    companion object {
        private val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASSES = intArrayOf(556, 441, 341, 225)
    }
}
