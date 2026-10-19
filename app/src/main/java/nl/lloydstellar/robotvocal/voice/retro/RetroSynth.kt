package nl.lloydstellar.robotvocal.voice.retro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The cascade/parallel formant synthesizer of whackintalk (MIT, see NOTICE),
 * ported from TypeScript: Klatt (1980) with the KLGLOTT88 glottal source of
 * Klatt and Klatt (1990).
 *
 *     pulse -> tilt --+--> cascade: nasal pole, nasal zero, F5 .. F1 --+
 *     noise -> breath-+  (aspiration, AH)                              +--> lips --> out
 *     noise ------------> parallel: bypass, F2 .. F6 (frication, AF) --+
 *
 * The cascade carries everything with an all-pole tract, the vowels, nasals
 * and glides; the parallel branch carries frication and bursts.
 *
 * Two changes from the original. The pulse runs on a fractional phase, so a
 * note is in tune instead of rounded to whole samples (at 22 kHz that made a
 * high note up to 12 cents flat). And when voicing stops, the pulse that is
 * open runs to its end first, so the end of a vowel does not click.
 */
internal class RetroSynth(
    private val rate: Int,
    seed: Int,
    private val flutter: Double,
    private val vibratoHz: Double,
    private val vibratoCents: Double,
    /** Song time of the first sample, in seconds: flutter and vibrato follow the song. */
    private val startSeconds: Double,
) : FrameSynth {
    private val glottis = Glottis(rate)
    private var noise = if (seed == 0) 0x1E3779B9 else seed
    private val nasalPole = Resonator(rate)
    private val nasalZero = AntiResonator(rate)
    /* high formant first, so the hottest one (low and narrow) comes last */
    private val cascade = Array(5) { Resonator(rate) }
    private val parallel = Array(5) { Resonator(rate) }
    private val tilt = OnePole(rate)
    private val breath = OnePole(rate).apply { cutoff(ASPIRATION_CUTOFF) }
    private var lips = 0.0

    /* linear gains in the order of K.AMPLITUDES: where the last block ended, where this one goes, and the step per sample */
    private val from = DoubleArray(K.AMPLITUDES.size)
    private val to = DoubleArray(K.AMPLITUDES.size)
    private val step = DoubleArray(K.AMPLITUDES.size)
    private var sample = 0L

    /**
     * Renders [count] samples into [out] from [offset], with the formants and
     * pitch of [frame]; the loudness glides from where the last block ended to
     * [frame]'s, so a block of a millisecond never steps.
     */
    override fun render(frame: DoubleArray, out: FloatArray, offset: Int, count: Int) {
        nasalPole.pole(frame[K.FNP], frame[K.BNP])
        nasalZero.zero(frame[K.FNZ], frame[K.BNZ])
        for (j in 0 until 5) cascade[j].pole(frame[K.F5 - j], frame[K.B5 - j])
        for (j in 0 until 4) parallel[j].pole(frame[K.F2 + j], frame[K.B2 + j])
        parallel[4].pole(F6, B6)
        tilt.cutoff(frame[K.TILT])

        for (j in to.indices) {
            to[j] = K.linear(frame[K.AMPLITUDES[j]]) * TRIM[j]
            step[j] = (to[j] - from[j]) / count
        }
        val f0 = frame[K.F0]
        val oq = frame[K.OQ]
        val voiced = f0 > 0 && to[0] > 0
        val g = from

        for (i in 0 until count) {
            for (j in g.indices) g[j] += step[j]
            val pulse = glottis.next(if (f0 > 0) f0 * wobble() else 0.0, oq)
            val voicing = tilt.process(pulse) * g[0]

            val raw = white()
            /* in z, v and zh the air, and so the noise, pulses with the glottis */
            val modulation = if (voiced) 0.4 + 0.6 * glottis.openPhase else 1.0
            val aspiration = breath.process(raw) * g[1] * modulation
            val frication = raw * g[2] * modulation

            var c = nasalZero.process(nasalPole.process(voicing + aspiration))
            for (r in cascade) c = r.process(c)

            /* alternating signs, so neighbouring resonances add up instead of cancelling on their skirts */
            val p = frication * g[8] +
                g[3] * parallel[0].peak(frication) - g[4] * parallel[1].peak(frication) +
                g[5] * parallel[2].peak(frication) - g[6] * parallel[3].peak(frication) +
                g[7] * parallel[4].peak(frication)

            /* the lips: a first difference, +6 dB per octave */
            val x = c + p
            out[offset + i] = (x - lips).toFloat()
            lips = x
            sample++
        }
        to.copyInto(from)
    }

    /** Flutter (Klatt's three slow sines, so a held vowel is not a test tone) and vibrato, as a factor on the pitch. */
    private fun wobble(): Double {
        if (flutter <= 0 && vibratoHz <= 0) return 1.0
        val t = startSeconds + sample.toDouble() / rate
        var m = 1.0
        if (flutter > 0) m *= 1 + flutter * 0.01 * (sin(2 * PI * 12.7 * t) + sin(2 * PI * 7.1 * t) + sin(2 * PI * 4.7 * t)) / 3
        if (vibratoHz > 0) m *= 2.0.pow(vibratoCents / 1200 * sin(2 * PI * vibratoHz * t))
        return m
    }

    /** Xorshift white noise in -1..1: the same song gives the same hiss every time. */
    private fun white(): Double {
        var x = noise
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noise = x
        return (x.toLong() and 0xFFFFFFFFL) / 2_147_483_648.0 - 1
    }

    private companion object {
        /*
         * Branch balance, from the original: voicing has most of its energy low,
         * where the lips' difference takes it down, and frication sits at 3 to 6
         * kHz, where it lifts it. These trims (-16 dB on aspiration, -14 dB on
         * frication) bring the noise back in line; one per entry of K.AMPLITUDES.
         */
        val TRIM = doubleArrayOf(1.0, 10.0.pow(-16 / 20.0), 10.0.pow(-14 / 20.0), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        const val ASPIRATION_CUTOFF = 2200.0
        /* the sixth parallel resonator only shapes the hiss of sibilants */
        const val F6 = 4900.0
        const val B6 = 1000.0
    }
}

/**
 * KLGLOTT88: over the open part of each period the glottal flow follows
 * 6.75 x² (1 - x), which peaks at 1 when x = 2/3, then it shuts. The open
 * quotient is the main knob for the quality of the voice: about 0.4 is
 * pressed and buzzy, 0.7 breathy.
 */
private class Glottis(private val rate: Int) {
    private var phase = 0.0
    private var open = 0.5
    private var hz = 0.0
    private var running = false

    /** How far the glottis is through its open phase, 0 while it is closed. */
    var openPhase = 0.0
        private set

    fun next(f0: Double, oq: Double): Double {
        if (f0 > 0) hz = f0
        if (!running) {
            if (f0 <= 0) return closed()
            running = true
            phase = 0.0
            open = oq.coerceIn(0.15, 0.9)
        }
        val x = phase / open
        if (x >= 1 && f0 <= 0) {
            running = false
            return closed()
        }
        phase += hz / rate
        if (phase >= 1) {
            phase -= 1
            open = oq.coerceIn(0.15, 0.9)
        }
        if (x >= 1) return closed()
        openPhase = x
        return 6.75 * x * x * (1 - x)
    }

    private fun closed(): Double {
        openPhase = 0.0
        return 0.0
    }
}

/** Klatt's two-pole resonator, with unity gain at DC so a cascade keeps its level as the formants move. */
private class Resonator(private val rate: Int) {
    private var a = 0.0
    private var b = 0.0
    private var c = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    private var peakNorm = 1.0

    fun pole(f: Double, bw: Double) {
        val r = exp(-PI * bw / rate)
        c = -r * r
        b = 2 * r * cos(2 * PI * f / rate)
        a = 1 - b - c
        /* the parallel branch wants unity gain at the peak, so A2 .. A6 mean what they say */
        peakNorm = 1 - r
    }

    fun process(x: Double): Double {
        val y = a * x + b * y1 + c * y2
        y2 = y1
        y1 = y
        return y
    }

    fun peak(x: Double) = process(x) * peakNorm
}

/** The two-zero antiresonator: the exact inverse of a resonator at the same frequency and bandwidth. */
private class AntiResonator(private val rate: Int) {
    private var a = 1.0
    private var b = 0.0
    private var c = 0.0
    private var x1 = 0.0
    private var x2 = 0.0

    fun zero(f: Double, bw: Double) {
        val r = exp(-PI * bw / rate)
        val c0 = -r * r
        val b0 = 2 * r * cos(2 * PI * f / rate)
        val a0 = 1 - b0 - c0
        a = 1 / a0
        b = -b0 / a0
        c = -c0 / a0
    }

    fun process(x: Double): Double {
        val y = a * x + b * x1 + c * x2
        x2 = x1
        x1 = x
        return y
    }
}

private class OnePole(private val rate: Int) {
    private var k = 1.0
    private var y = 0.0

    fun cutoff(fc: Double) {
        k = if (fc >= rate / 2.0) 1.0 else 1 - exp(-2 * PI * fc / rate)
    }

    fun process(x: Double): Double {
        y += k * (x - y)
        return y
    }
}
