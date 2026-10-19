package nl.lloydstellar.robotvocal.voice.retro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Speech the way a home computer of 1982 made it in software, without a
 * single filter: three oscillators at the first three formants, two sines and
 * a square, all started again at every pulse of the voice, so each period is
 * a burst of three tones. Their levels, the sine and the sum have [bits] bits;
 * the voice moves in steps of about [stepRate] a second and holds each one;
 * hiss is 1-bit noise. That is the method of S.A.M. (Software Automatic Mouth,
 * 1982) as the README of its C port describes it ("A = A1 * sin(f1 * t) +
 * A2 * sin(f2 * t) + A3 * rect(f3 * t)", 4-bit amplitudes, 4-bit output),
 * with the oscillators restarted every pitch period. Nothing else is taken
 * from S.A.M.: the formants and levels come from the retro engine's phoneme
 * table, the rest from Klatt (1980).
 *
 * Every period is a whole number of steps, as on the old machines, so all the
 * grit of the coarse steps is harmonic and the voice buzzes instead of
 * rattling. To keep a note in tune, the step clock is set per note: 110 Hz is
 * 67 steps at 7370 a second. The steps are averaged into the output samples
 * they cover, so a step that ends between two samples still ends on time.
 *
 * The level of each oscillator is what the Klatt model of the same frame
 * gives at that formant (the cascade's gain there, with the source falling
 * 6 dB per octave), with the range in dB halved so that a weak formant still
 * gets a step of its own.
 */
internal class MicroSynth(
    private val rate: Int,
    seed: Int,
    private val bits: Int,
    private val stepRate: Double,
) : FrameSynth {
    private val amplitudeSteps = (1 shl bits) - 1.0
    private val sineSteps = (1 shl (bits - 1)) - 1.0
    private val sine = DoubleArray(256) { (sin(2 * PI * it / 256) * sineSteps).roundToInt() / sineSteps }

    /* the voice: steps per period, the length of a step in samples, where we are, and the value of this step */
    private var running = false
    private var period = 0
    private var length = 1.0
    private var pos = 0.0
    private var index = 0
    private var value = 0.0
    private var f0 = 0.0
    private val formant = DoubleArray(3)

    /* the hiss: shaped by Klatt's parallel branch, of which only the sign is kept */
    private var noise = if (seed == 0) 0x1E3779B9 else seed
    private val parallel = Array(5) { Peak(rate) }
    private val breath = Array(3) { Peak(rate) }
    private var lips = 0.0

    /* voice levels at F1, F2, F3, then the hiss: where they are, where they go, and the step per sample */
    private val gain = DoubleArray(4)
    private val target = DoubleArray(4)
    private val delta = DoubleArray(4)

    override fun render(frame: DoubleArray, out: FloatArray, offset: Int, count: Int) {
        voicedLevels(frame)
        target[3] = hissLevel(frame)
        for (j in 0 until 4) parallel[j].pole(frame[K.F2 + j], frame[K.B2 + j])
        parallel[4].pole(F6, B6)
        for (j in 0 until 3) breath[j].pole(frame[K.F1 + j], frame[K.B1 + j] * 2)
        for (k in 0 until 3) formant[k] = frame[K.F1 + k]
        for (j in 0 until 4) delta[j] = (target[j] - gain[j]) / count
        val fricative = frame[K.AF] > 0

        for (i in 0 until count) {
            for (j in 0 until 4) gain[j] += delta[j]
            val voice = voice(frame[K.F0])
            val hiss = hiss(frame)
            /* in z, v and zh the hiss rides on the voice, at half strength in the first half of each period (Klatt 1980) */
            val x = when {
                !running -> hiss
                fricative -> voice * VOICE_GAIN + hiss * (if (index < period / 2) 0.5 else 1.0)
                else -> voice * VOICE_GAIN
            }
            out[offset + i] = (dac(x) * OUTPUT).toFloat()
        }
    }

    /** One sample of the voice: the steps it covers, each for as long as it covers them. */
    private fun voice(pitch: Double): Double {
        /* when the voice stops, it fades out on the pitch it had */
        val hz = if (pitch > 0) pitch else f0
        if (hz <= 0 || gain[0] + gain[1] + gain[2] <= 0) {
            running = false
            return 0.0
        }
        if (!running) {
            running = true
            pulse(hz)
        }
        var left = 1.0
        var sum = 0.0
        while (left > 1e-9) {
            val take = minOf(left, length - pos)
            sum += value * take
            pos += take
            left -= take
            if (pos >= length - 1e-9) {
                pos = 0.0
                index++
                if (index >= period) pulse(hz) else value = stepValue()
            }
        }
        return sum
    }

    /** A new pulse: a period of whole steps at [pitch], every oscillator back at the start. */
    private fun pulse(pitch: Double) {
        f0 = pitch
        period = maxOf(2, (stepRate / pitch).roundToInt())
        length = rate / (period * pitch)
        pos = 0.0
        index = 0
        value = stepValue()
    }

    /** The value of the step [index] of the period: the three oscillators where they are at its start. */
    private fun stepValue(): Double {
        val t = index / (period * f0)
        return level(gain[0]) * sine[wrap(formant[0] * t)] + level(gain[1]) * sine[wrap(formant[1] * t)] +
            level(gain[2]) * (if ((formant[2] * t) % 1.0 < 0.5) 1.0 else -1.0)
    }

    /** One sample of hiss: the sign of the shaped noise, at the noise's level in [bits] bits. */
    private fun hiss(frame: DoubleArray): Double {
        val raw = white()
        var h = 0.0
        if (frame[K.AF] > 0) {
            h += raw * K.linear(frame[K.AB]) +
                K.linear(frame[K.A2]) * parallel[0].peak(raw) - K.linear(frame[K.A3]) * parallel[1].peak(raw) +
                K.linear(frame[K.A4]) * parallel[2].peak(raw) - K.linear(frame[K.A5]) * parallel[3].peak(raw) +
                K.linear(frame[K.A6]) * parallel[4].peak(raw)
        }
        if (frame[K.AH] > 0) h += breath[0].peak(raw) + breath[1].peak(raw) * 0.7 + breath[2].peak(raw) * 0.5
        /* the lips: +6 dB per octave, as in the Klatt model; only the sign is kept */
        val x = h - lips
        lips = h
        if (gain[3] <= 0) return 0.0
        return (if (x >= 0) 1.0 else -1.0) * level(gain[3])
    }

    /**
     * How loud the hiss of [frame] is: frication by its strongest band, or
     * breath, with the range in dB halved like the formants', so the faint
     * f and th keep a step of their own.
     */
    private fun hissLevel(frame: DoubleArray): Double {
        var band = K.linear(frame[K.AB])
        for (k in K.A2..K.A6) band = maxOf(band, K.linear(frame[k]))
        val loudest = maxOf(K.linear(frame[K.AF]) * band, K.linear(frame[K.AH]) * ASPIRATION)
        return sqrt(loudest) * HISS_GAIN
    }

    /**
     * The output in [bits] bits, the levels of a DAC: 16 for 4 bits, half a
     * step either side of zero, so the faintest hiss still moves one step.
     * Silence stays silent, as a DAC that is not written to.
     */
    private fun dac(x: Double): Double {
        if (x == 0.0) return 0.0
        val half = (1 shl (bits - 1)).toDouble()
        return (floor(x.coerceIn(-1.0, 1.0 - 1e-9) * half) + 0.5) / half
    }

    /** A level of 0..1 in [bits] bits. */
    private fun level(v: Double) = (v.coerceIn(0.0, 1.0) * amplitudeSteps).roundToInt() / amplitudeSteps

    private fun wrap(cycles: Double) = ((cycles % 1.0) * 256).toInt() and 255

    /** The levels of the three oscillators for [frame], 0..1. */
    private fun voicedLevels(frame: DoubleArray) {
        val av = K.linear(frame[K.AV])
        if (av <= 0) {
            for (k in 0 until 3) target[k] = 0.0
            return
        }
        var top = 0.0
        for (k in 0 until 3) {
            val f = frame[K.F1 + k]
            var g = 1 / f
            for (j in 0 until 5) g *= gainAt(f, frame[K.F1 + j], frame[K.B1 + j])
            g *= gainAt(f, frame[K.FNP], frame[K.BNP]) / gainAt(f, frame[K.FNZ], frame[K.BNZ])
            val db = 20 * log10(g)
            if (k == 0) top = db
            target[k] = av * 10.0.pow((db - top) * 0.5 / 20)
        }
    }

    /** |H(f)| of a Klatt resonator at [fc] with bandwidth [bw], unity at DC. */
    private fun gainAt(f: Double, fc: Double, bw: Double): Double {
        val s = PI * bw
        val w = 2 * PI * fc
        val x = 2 * PI * f
        return (s * s + w * w) / hypot(s * s + w * w - x * x, 2 * s * x)
    }

    private fun white(): Double {
        var x = noise
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noise = x
        return (x.toLong() and 0xFFFFFFFFL) / 2_147_483_648.0 - 1
    }

    private companion object {
        const val VOICE_GAIN = 0.45
        const val HISS_GAIN = 0.2
        const val ASPIRATION = 0.5
        const val OUTPUT = 0.5
        const val F6 = 4900.0
        const val B6 = 1000.0
    }
}

/** A two-pole resonator with unity gain at its peak, for shaping noise. */
private class Peak(private val rate: Int) {
    private var a = 0.0
    private var b = 0.0
    private var c = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    private var norm = 1.0

    fun pole(f: Double, bw: Double) {
        val r = exp(-PI * bw / rate)
        c = -r * r
        b = 2 * r * cos(2 * PI * min(f, rate * 0.45) / rate)
        a = 1 - b - c
        norm = 1 - r
    }

    fun peak(x: Double): Double {
        val y = a * x + b * y1 + c * y2
        y2 = y1
        y1 = y
        return y * norm
    }
}
