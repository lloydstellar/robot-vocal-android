package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * An ensemble: three copies of the voice, each a few milliseconds late and
 * drifting at its own slow rate, with a faint fast shimmer, as the string and
 * choir ensembles of 1979 did with their bucket-brigade chips.
 */
class Ensemble(private val mix: Double = 0.5) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val rates = doubleArrayOf(0.61, 0.83, 1.07)
        val out = FloatArray(x.size)
        for (i in x.indices) {
            val t = i.toDouble() / ctx.rate
            var sum = 0.0
            for ((k, r) in rates.withIndex()) {
                val delayMs = 7.0 + 2.5 * sin(2 * PI * (r * t + k / 3.0)) + 0.12 * sin(2 * PI * 6.1 * t + k)
                val j = i - delayMs * ctx.rate / 1000
                val a = floor(j).toInt()
                if (a >= 0 && a + 1 < x.size) sum += x[a] + (x[a + 1] - x[a]) * (j - a)
            }
            out[i] = (x[i] * (1 - mix) + sum / 3 * mix * 1.4).toFloat()
        }
        return out
    }
}

/**
 * A resonant low-pass that opens and closes once every [barsPerCycle] bars,
 * between [low] and [high] Hz: the wah of a synthesizer filter on a voice. A
 * state-variable filter (topology-preserving), so the sweep has no clicks.
 */
class FilterSweep(
    private val barsPerCycle: Double = 2.0,
    private val low: Double = 350.0,
    private val high: Double = 6000.0,
    private val q: Double = 3.5,
) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        var ic1 = 0.0
        var ic2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var a3 = 0.0
        val k = 1 / q
        return FloatArray(x.size) { i ->
            if (i % 32 == 0) {
                val lfo = 0.5 - 0.5 * cos(2 * PI * ctx.bars(i) / barsPerCycle)
                val f = low * (high / low).pow(lfo)
                val g = tan(PI * f / ctx.rate)
                a1 = 1 / (1 + g * (g + k))
                a2 = g * a1
                a3 = g * a2
            }
            val v3 = x[i] - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2 * v1 - ic1
            ic2 = 2 * v2 - ic2
            v2.toFloat()
        }
    }
}

/**
 * Beat repeat: at the end of every [everyBars] bars, the last [fillSixteenths]
 * 16ths repeat the first 16th of that stretch, a stutter fill as a drum
 * machine's roll. Short crossfades keep the cuts clean.
 */
class BeatRepeat(private val everyBars: Int = 2, private val fillSixteenths: Int = 4) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val sixteenth = ctx.beats(0.25)
        val fade = (ctx.rate * 0.002).toInt()
        val out = x.copyOf()
        for (i in x.indices) {
            val pos = ctx.bars(i) / everyBars
            val inGroup = (pos - floor(pos)) * everyBars * 16 // 16ths since the start of the group
            val fillStart = everyBars * 16 - fillSixteenths
            if (inGroup < fillStart) continue
            val intoFill = ((inGroup - fillStart) * sixteenth).toInt()
            val start = i - intoFill
            val source = start + intoFill % sixteenth
            if (start < 0 || source >= x.size) continue
            val inSlice = intoFill % sixteenth
            val w = when {
                inSlice < fade -> inSlice.toFloat() / fade
                sixteenth - inSlice < fade -> (sixteenth - inSlice).toFloat() / fade
                else -> 1f
            }
            out[i] = x[source] * w
        }
        return out
    }
}

/**
 * A tape stop on the last word of every line: the tape slows to a standstill
 * over the word, so it drops in pitch and stretches, then the line is silent
 * until the next one starts.
 */
class TapeStop : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val out = x.copyOf()
        val silence = (ctx.rate * 0.06).toInt()
        for (range in ctx.lineEnds) {
            val from = (range.first - ctx.origin).toInt()
            val to = (range.last - ctx.origin).toInt()
            if (from < 0 || to >= x.size || to - from < 2) continue
            val d = (to - from).toDouble()
            for (t in 0 until to - from) {
                val pos = from + t - t * t / (2 * d) // speed falls from 1 to 0
                val a = pos.toInt()
                out[from + t] = (x[a] + (x[(a + 1).coerceAtMost(x.size - 1)] - x[a]) * (pos - a)).toFloat()
            }
            /* the tape has stopped: what rang on after the word is gone */
            for (i in to until minOf(x.size, to + silence)) out[i] = 0f
        }
        return out
    }
}
