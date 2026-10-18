package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * An effect that makes or works on two channels. Its input has one channel
 * (the mono voice) or two; it always returns two, left and right.
 */
fun interface StereoEffect {
    fun process(channels: Array<FloatArray>, ctx: FxContext): Array<FloatArray>
}

/** Both channels, a mono input copied to two. */
internal fun Array<FloatArray>.asStereo(): Array<FloatArray> = if (size >= 2) this else arrayOf(this[0], this[0].copyOf())

/** The middle of a stereo pair, or the mono channel itself. */
internal fun Array<FloatArray>.mid(): FloatArray =
    if (size == 1) this[0] else FloatArray(this[0].size) { (this[0][it] + this[1][it]) * 0.5f }

/**
 * Ping-pong: echoes [beats] beats apart that jump from left to right and back,
 * darker every time, as on a stereo tape echo. The voice itself stays in the middle.
 */
class PingPong(
    private val beats: Double,
    private val feedback: Double = 0.45,
    private val mix: Double = 0.45,
    private val highCut: Double = 4200.0,
    private val lowCut: Double = 300.0,
) : StereoEffect {
    override fun process(channels: Array<FloatArray>, ctx: FxContext): Array<FloatArray> {
        val x = channels.asStereo()
        val n = x[0].size
        val d = ctx.beats(beats).coerceAtLeast(1)
        val lineL = DoubleArray(n)
        val lineR = DoubleArray(n)
        val lp = exp(-2 * PI * highCut / ctx.rate)
        val hp = exp(-2 * PI * lowCut / ctx.rate)
        val toneL = Tone(lp, hp)
        val toneR = Tone(lp, hp)
        val left = FloatArray(n)
        val right = FloatArray(n)
        for (i in 0 until n) {
            val echoL = if (i >= d) lineL[i - d] else 0.0
            val echoR = if (i >= d) lineR[i - d] else 0.0
            val mono = (x[0][i] + x[1][i]) * 0.5
            /* the voice goes into the left line; each line feeds the other */
            lineL[i] = mono + toneR.process(echoR) * feedback
            lineR[i] = toneL.process(echoL)
            left[i] = (x[0][i] + echoL * mix).toFloat()
            right[i] = (x[1][i] + echoR * mix).toFloat()
        }
        return arrayOf(left, right)
    }

    /** A one-pole low-pass followed by a one-pole high-pass. */
    private class Tone(private val lp: Double, private val hp: Double) {
        private var low = 0.0
        private var lastIn = 0.0
        private var high = 0.0

        fun process(x: Double): Double {
            low = (1 - lp) * x + lp * low
            high = hp * (high + low - lastIn)
            lastIn = low
            return high
        }
    }
}

/** A wide chorus: two slowly drifting copies of the voice, one on each side. */
class WideChorus(private val mix: Double = 0.5) : StereoEffect {
    override fun process(channels: Array<FloatArray>, ctx: FxContext): Array<FloatArray> {
        val x = channels.mid()
        fun voice(i: Int, rate: Double, phase: Double): Double {
            val t = i.toDouble() / ctx.rate
            val delayMs = 9.0 + 3.0 * sin(2 * PI * (rate * t + phase))
            val j = i - delayMs * ctx.rate / 1000
            val a = floor(j).toInt()
            return if (a >= 0 && a + 1 < x.size) x[a] + (x[a + 1] - x[a]) * (j - a) else 0.0
        }
        val src = channels.asStereo()
        val left = FloatArray(x.size) { (src[0][it] * (1 - mix * 0.5) + voice(it, 0.67, 0.0) * mix).toFloat() }
        val right = FloatArray(x.size) { (src[1][it] * (1 - mix * 0.5) + voice(it, 0.83, 0.5) * mix).toFloat() }
        return arrayOf(left, right)
    }
}

/** The voice moves from side to side once every [beatsPerCycle] beats, in time with the song. */
class AutoPan(private val beatsPerCycle: Double = 4.0, private val depth: Double = 0.8) : StereoEffect {
    override fun process(channels: Array<FloatArray>, ctx: FxContext): Array<FloatArray> {
        val x = channels.mid()
        val left = FloatArray(x.size)
        val right = FloatArray(x.size)
        for (i in x.indices) {
            val pan = 0.5 + 0.5 * depth * sin(2 * PI * ctx.bars(i) * 4 / beatsPerCycle)
            left[i] = (x[i] * cos(pan * PI / 2) * 1.41).toFloat()
            right[i] = (x[i] * sin(pan * PI / 2) * 1.41).toFloat()
        }
        return arrayOf(left, right)
    }
}

/** The plate in stereo: the right side's combs are 23 samples longer, as in Freeverb. */
class StereoPlate(private val room: Double = 0.75, private val mix: Double = 0.22) : StereoEffect {
    override fun process(channels: Array<FloatArray>, ctx: FxContext): Array<FloatArray> {
        val x = channels.asStereo()
        val mono = channels.mid()
        val left = Plate(room = room, mix = 1.0, spread = 0).process(mono, ctx)
        val right = Plate(room = room, mix = 1.0, spread = 23).process(mono, ctx)
        /* at mix 1 a Plate returns the voice plus its full reverb; keep the reverb and add it at [mix] */
        return arrayOf(
            FloatArray(mono.size) { (x[0][it] + (left[it] - mono[it]) * mix).toFloat() },
            FloatArray(mono.size) { (x[1][it] + (right[it] - mono[it]) * mix).toFloat() },
        )
    }
}
