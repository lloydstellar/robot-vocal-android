package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sound of a 1978 speech chip such as the TMS5100 in the Speak & Spell:
 * the voice is measured as 10-pole LPC at 8 kHz in 25 ms frames, every value
 * is squeezed into a few bits as the chip stored them (k1 and k2 in 5 bits,
 * k3 to k7 in 4, k8 to k10 in 3, energy in 4), and a lattice filter speaks it
 * again from the chip's chirp for voiced frames and from noise for unvoiced
 * ones, at the song's pitch. No word data of the chip is used, only its way of
 * speaking. The chirp is the one of US patent 4,209,836, as tabulated in MAME
 * (src/devices/sound/tms5110r.hxx, BSD-3-Clause).
 */
class LpcChip(private val bitsOut: Int = 8) : Effect {

    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val rate = ctx.rate
        val low = Resampler.convert(x, rate, CHIP_RATE)
        level(low)
        val frames = (low.size + FRAME - 1) / FRAME
        val window = DoubleArray(FRAME + FRAME / 4) { 0.54 - 0.46 * cos(2 * PI * it / (FRAME + FRAME / 4 - 1)) }
        val ks = Array(frames) { DoubleArray(ORDER + 1) }
        val energy = DoubleArray(frames)
        val voiced = BooleanArray(frames)
        val buf = DoubleArray(window.size)
        for (f in 0 until frames) {
            var zc = 0
            var e = 0.0
            for (i in buf.indices) {
                val at = f * FRAME + i - FRAME / 8
                val v = if (at in low.indices) low[at].toDouble() else 0.0
                val prev = if (at - 1 in low.indices) low[at - 1].toDouble() else 0.0
                buf[i] = (v - 0.9375 * prev) * window[i]
                e += v * v
                if (i > 0 && (v >= 0) != (prev >= 0)) zc++
            }
            val rms = sqrt(e / buf.size)
            val lpc = Lpc.analyze(buf, ORDER)
            /* the phonemes say where the voice is noise, the zero crossings where an engine hisses on its own */
            val noise = ctx.noiseAt(((f * FRAME + FRAME / 2).toLong() * rate / CHIP_RATE).toInt())
            voiced[f] = noise < 1.0 && zc < buf.size * 0.25 && rms > 0.004
            for (j in 1..ORDER) ks[f][j] = quantise(lpc.k[j], K_BITS[j])
            if (!voiced[f]) for (j in 5..ORDER) ks[f][j] = 0.0
            energy[f] = quantiseEnergy(rms)
        }
        /* speak it again: 8 interpolation steps per frame, as the chip did */
        val out = FloatArray(low.size)
        val noise = Noise(ctx.seed)
        val b = DoubleArray(ORDER + 1)
        var chirpPos = CHIRP.size
        var periodLeft = 0.0
        for (i in out.indices) {
            val f = i / FRAME
            val step = ((i % FRAME) * 8 / FRAME)
            val next = (f + 1).coerceAtMost(frames - 1)
            val t = step / 8.0
            val e = energy[f] + (energy[next] - energy[f]) * t
            val excitation = if (voiced[f]) {
                periodLeft -= 1.0
                if (periodLeft <= 0) {
                    chirpPos = 0
                    periodLeft += CHIP_RATE / ctx.pitchAt(i * rate / CHIP_RATE).coerceIn(50.0, 400.0)
                }
                (if (chirpPos < CHIRP.size) CHIRP[chirpPos++] else 0) / 128.0
            } else noise.next() * 0.35
            /* lattice filter */
            var u = excitation * e
            for (j in ORDER downTo 1) {
                val k = ks[f][j] + (ks[next][j] - ks[f][j]) * t
                u -= k * b[j - 1]
                b[j] = b[j - 1] + k * u
            }
            b[0] = u
            val levels = (1 shl (bitsOut - 1)).toDouble()
            out[i] = ((u * 4).coerceIn(-1.0, 1.0) * levels).roundToInt().div(levels).toFloat()
        }
        /* back to the app's rate by holding every sample: the steps of an 8 kHz DAC stay audible */
        val hold = rate / CHIP_RATE
        return followVoice(FloatArray(x.size) { i -> out.getOrElse(i / hold) { 0f } }, x, rate, 25.0)
    }

    /**
     * Sets the loudest frame to energy step 12 of 15, as a recording was
     * levelled before it was encoded: then every voice uses the chip's
     * steps alike and only the loudest vowels touch its top.
     */
    private fun level(low: FloatArray) {
        var loudest = 0.0
        for (start in low.indices step FRAME) {
            val end = minOf(low.size, start + FRAME)
            var e = 0.0
            for (i in start until end) e += low[i].toDouble() * low[i]
            loudest = maxOf(loudest, sqrt(e / (end - start)))
        }
        if (loudest <= 0) return
        val g = (LOUDEST / loudest).toFloat()
        for (i in low.indices) low[i] *= g
    }

    /** A reflection coefficient in [bits] bits, spaced evenly on the arcsine scale where the ear is evenly sensitive. */
    private fun quantise(k: Double, bits: Int): Double {
        val levels = (1 shl bits) - 1
        val u = asin(k.coerceIn(-0.999, 0.999)) / (PI / 2)
        val q = ((u + 1) / 2 * levels).roundToInt().toDouble() / levels * 2 - 1
        return sin(q * PI / 2).coerceIn(-0.995, 0.995)
    }

    /** Energy in 16 steps on a log scale, step 0 silent. */
    private fun quantiseEnergy(rms: Double): Double {
        if (rms < 0.002) return 0.0
        val step = ((ln(rms / 0.002) / ln(2.0)) * 2).roundToInt().coerceIn(1, 15)
        return 0.002 * Math.pow(2.0, step / 2.0) * 3
    }

    companion object {
        private const val CHIP_RATE = 8_000
        /** The RMS of energy step 12: 0.002 times 2 to the 6th. */
        private const val LOUDEST = 0.128
        private const val FRAME = 200
        private const val ORDER = 10
        private val K_BITS = intArrayOf(0, 5, 5, 4, 4, 4, 4, 4, 3, 3, 3)
        private val CHIRP = intArrayOf(
            0x00, 0x2a, 0xd4, 0x32, 0xb2, 0x12, 0x25, 0x14, 0x02, 0xe1, 0xc5, 0x02, 0x5f, 0x5a, 0x05, 0x0f,
            0x26, 0xfc, 0xa5, 0xa5, 0xd6, 0xdd, 0xdc, 0xfc, 0x25, 0x2b, 0x22, 0x21, 0x0f, 0xff, 0xf8, 0xee,
            0xed, 0xef, 0xf7, 0xf6, 0xfa, 0x00, 0x03, 0x02, 0x01,
        ).map { it.toByte().toInt() }.toIntArray()
    }
}
