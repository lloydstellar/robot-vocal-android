package nl.lloydstellar.robotvocal.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** The vocoder keeps what makes words: the noise of an s and the gap of a t. */
class VocoderTest {
    private val rate = 48_000

    /** A vowel-like modulator: the first 30 harmonics of 110 Hz, loudest around 700 and 1200 Hz. */
    private fun vowel(i: Int): Double {
        var v = 0.0
        for (h in 1..30) {
            val f = 110.0 * h
            val weight = 1 / (1 + ((f - 700) / 300).let { it * it }) + 0.6 / (1 + ((f - 1200) / 300).let { it * it })
            v += weight * sin(2 * PI * f * i / rate)
        }
        return v * 0.05
    }

    private fun rms(x: FloatArray, from: Int, to: Int) = sqrt((from until to).sumOf { (x[it] * x[it]).toDouble() } / (to - from))

    /** How alike the signal is to itself one pitch period later, 1 for a steady tone, about 0 for noise. */
    private fun periodicity(x: FloatArray, from: Int, to: Int, lag: Int): Double {
        var xy = 0.0
        var xx = 0.0
        var yy = 0.0
        for (i in from until to) {
            xy += x[i] * x[i + lag]
            xx += x[i] * x[i]
            yy += x[i + lag] * x[i + lag]
        }
        return xy / sqrt(xx * yy)
    }

    @Test fun `where the voice is noise, the carrier is noise`() {
        val second = rate
        val x = FloatArray(second) { vowel(it).toFloat() }
        /* the middle third of the voice is an s, for the phonemes */
        val ctx = FxContext(rate, 120.0, 0, 1, noise = { f -> if (f in second / 3 until 2 * second / 3) 1.0 else 0.0 }) { 110.0 }
        val y = Vocoder(carrier = Carrier.SAW, hiss = 0.0).process(x, ctx)
        val lag = (rate / 110.0).toInt() * 4
        val voiced = periodicity(y, second / 6, second / 6 + 4800, lag)
        val noise = periodicity(y, second / 2, second / 2 + 4800, lag)
        assertTrue("voiced: periodicity $voiced", voiced > 0.8)
        assertTrue("noise: periodicity $noise", noise < 0.3)
        val level = 20 * log10(rms(y, second / 2 - 2400, second / 2 + 2400) / rms(y, second / 6 - 2400, second / 6 + 2400))
        assertTrue("the s is $level dB against the vowel", level > -6 && level < 6)
    }

    @Test fun `the closure of a stop stays a gap`() {
        /* a vowel, 40 ms of silence, the vowel again: from 10 ms into the gap the vocoder is quiet (with the old 25 ms release, -6 dB) */
        val x = FloatArray(rate / 2) { i -> if (i in 12_000 until 13_920) 0f else vowel(i).toFloat() }
        val ctx = FxContext(rate, 120.0, 0, 1) { 110.0 }
        val y = Vocoder(carrier = Carrier.SAW, hiss = 0.0, tightMs = 0.0).process(x, ctx)
        val gap = 20 * log10(rms(y, 12_480, 13_920) / rms(y, 6_000, 12_000))
        assertTrue("10 ms into the gap the vocoder is at $gap dB", gap < -10)
    }
}
