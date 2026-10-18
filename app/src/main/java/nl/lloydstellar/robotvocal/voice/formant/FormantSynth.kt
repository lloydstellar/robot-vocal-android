package nl.lloydstellar.robotvocal.voice.formant

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/** A two-pole resonator (Klatt 1980): passes a band around [freq] with bandwidth [bw]. */
internal class Resonator {
    private var a = 0.0
    private var b = 0.0
    private var c = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun set(freq: Double, bw: Double, rate: Int) {
        val r = exp(-PI * bw / rate)
        c = -r * r
        b = 2.0 * r * cos(2.0 * PI * freq / rate)
        a = 1.0 - b - c
    }

    fun process(x: Double): Double {
        val y = a * x + b * y1 + c * y2
        y2 = y1
        y1 = y
        return y
    }
}

/** The matching anti-resonator (a zero pair), for the nasal zero. */
internal class AntiResonator {
    private var a = 1.0
    private var b = 0.0
    private var c = 0.0
    private var x1 = 0.0
    private var x2 = 0.0

    fun set(freq: Double, bw: Double, rate: Int) {
        val r = exp(-PI * bw / rate)
        val c0 = -r * r
        val b0 = 2.0 * r * cos(2.0 * PI * freq / rate)
        val a0 = 1.0 - b0 - c0
        a = 1.0 / a0
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

/** The shape of the voice source: a glottal pulse sounds like a voice, the others like a machine. */
enum class Source { GLOTTAL, SAW, SQUARE, PULSE }

/**
 * One frame of synthesizer parameters, set every [FormantSynth.FRAME] samples
 * and interpolated in between. Amplitudes are linear, 0 to 1.
 */
internal class Frame {
    var f0 = 120.0
    var av = 0.0           // voicing
    var ah = 0.0           // aspiration (noise through the vocal tract)
    var af = 0.0           // frication (noise through the parallel bank)
    var f1 = 500.0; var f2 = 1500.0; var f3 = 2500.0; var f4 = 3300.0
    var b1 = 60.0; var b2 = 90.0; var b3 = 150.0; var b4 = 250.0
    var nasal = 0.0        // 0 oral, 1 fully nasal
    var fricF = 5000.0; var fricB = 1500.0; var fricF2 = 7500.0; var fricMix = 0.0 // second band share
    var bypass = 0.0       // share of flat noise in the frication

    fun copyFrom(o: Frame) {
        f0 = o.f0; av = o.av; ah = o.ah; af = o.af
        f1 = o.f1; f2 = o.f2; f3 = o.f3; f4 = o.f4
        b1 = o.b1; b2 = o.b2; b3 = o.b3; b4 = o.b4
        nasal = o.nasal; fricF = o.fricF; fricB = o.fricB; fricF2 = o.fricF2; fricMix = o.fricMix; bypass = o.bypass
    }

    fun mix(a: Frame, b: Frame, t: Double) {
        fun l(x: Double, y: Double) = x + (y - x) * t
        f0 = l(a.f0, b.f0); av = l(a.av, b.av); ah = l(a.ah, b.ah); af = l(a.af, b.af)
        f1 = l(a.f1, b.f1); f2 = l(a.f2, b.f2); f3 = l(a.f3, b.f3); f4 = l(a.f4, b.f4)
        b1 = l(a.b1, b.b1); b2 = l(a.b2, b.b2); b3 = l(a.b3, b.b3); b4 = l(a.b4, b.b4)
        nasal = l(a.nasal, b.nasal); fricF = l(a.fricF, b.fricF); fricB = l(a.fricB, b.fricB)
        fricF2 = l(a.fricF2, b.fricF2); fricMix = l(a.fricMix, b.fricMix); bypass = l(a.bypass, b.bypass)
    }
}

/**
 * A cascade-parallel formant synthesizer after Klatt (1980): a voice source
 * through five resonators in series for vowels and voiced consonants, noise
 * through a parallel band for the hiss of s, sh and f, aspiration through the
 * vocal tract for h and the breath after p, t, k. Noise comes from a seeded
 * generator, so the same song always renders to the same samples.
 */
internal class FormantSynth(private val rate: Int, private val source: Source, seed: Int) {
    private val r1 = Resonator(); private val r2 = Resonator(); private val r3 = Resonator()
    private val r4 = Resonator(); private val r5 = Resonator()
    private val nasalPole = Resonator(); private val nasalZero = AntiResonator()
    private val fric1 = Resonator(); private val fric2 = Resonator()
    private var noise = seed.toLong() and 0xFFFFFFFFL
    private var phase = 0.0
    private var dcIn = 0.0
    private var dcOut = 0.0
    private var lastNoise = 0.0
    private val frame = Frame()

    init {
        nasalPole.set(270.0, 100.0, rate)
    }

    /** Renders [count] samples into [out] at [offset], moving the parameters from [from] to [to]. */
    fun render(from: Frame, to: Frame, out: FloatArray, offset: Int, count: Int) {
        var k = 0
        while (k < count) {
            val block = minOf(COEF_BLOCK, count - k)
            frame.mix(from, to, (k + block / 2.0) / count)
            setCoefficients(frame)
            for (n in 0 until block) {
                out[offset + k + n] = sample(frame).toFloat()
            }
            k += block
        }
    }

    private fun setCoefficients(f: Frame) {
        r1.set(f.f1, f.b1, rate); r2.set(f.f2, f.b2, rate); r3.set(f.f3, f.b3, rate)
        r4.set(f.f4, f.b4, rate); r5.set(F5, 300.0, rate)
        nasalZero.set(270.0 + 230.0 * f.nasal, 100.0, rate)
        fric1.set(f.fricF, f.fricB, rate)
        fric2.set(f.fricF2, f.fricB * 1.5, rate)
    }

    private fun nextNoise(): Double {
        noise = (noise * 1_103_515_245L + 12_345L) and 0xFFFFFFFFL
        return ((noise ushr 8).toDouble() / (1 shl 24)) * 2.0 - 1.0
    }

    private fun voice(f0: Double): Double {
        phase += f0 / rate
        if (phase >= 1.0) phase -= 1.0
        return when (source) {
            /* the derivative of a smooth glottal flow pulse, open for 60 % of the period (KLGLOTT88 shape) */
            Source.GLOTTAL -> {
                val open = 0.6
                if (phase < open) {
                    val x = phase / open
                    (2.0 * x - 3.0 * x * x) * 1.2
                } else 0.0
            }
            Source.SAW -> (1.0 - 2.0 * phase) * 0.6
            Source.SQUARE -> if (phase < 0.5) 0.45 else -0.45
            Source.PULSE -> if (phase < 0.18) 0.9 else -0.2
        }
    }

    private fun sample(f: Frame): Double {
        val n = nextNoise()
        /* voice and aspiration through the vocal tract */
        var x = voice(f.f0) * f.av + n * f.ah * 0.35
        if (f.nasal > 0.0) {
            val nasalised = nasalZero.process(nasalPole.process(x))
            x = x + (nasalised - x) * f.nasal
        }
        val cascade = r1.process(r2.process(r3.process(r4.process(r5.process(x)))))
        /* frication: noise with a first difference (as in Klatt's parallel branch) through its own bands, plus flat noise for f and th */
        val fn = (n - lastNoise) * f.af
        lastNoise = n
        val band = fric1.process(fn) * (1.0 - f.fricMix) + fric2.process(fn) * f.fricMix
        val fric = band * (1.0 - f.bypass) * 1.6 + n * f.af * f.bypass * 0.5
        val y = cascade + fric
        /* remove any offset: a one-pole high-pass at about 30 Hz */
        dcOut = y - dcIn + 0.996 * dcOut
        dcIn = y
        return dcOut
    }

    companion object {
        /** Parameter frames every 5 ms at 48 kHz. */
        const val FRAME = 240
        private const val COEF_BLOCK = 16
        private const val F5 = 3850.0
    }
}
