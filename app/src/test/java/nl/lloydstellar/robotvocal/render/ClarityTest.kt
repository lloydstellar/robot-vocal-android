package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.text.Ph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** The vocal chain on every voice: the low end down, presence up, soft sounds up towards the vowels, rests left alone. */
class ClarityTest {
    private val rate = 48_000
    private val ms = rate / 1000
    private val origin = -100L * ms

    private fun phone(ph: Ph, from: Int, to: Int, syllable: Int) =
        TimedPhone(ph, from.toLong() * ms, to.toLong() * ms, true, 110.0, syllable, syllable)

    /** 700 ms from 100 ms before bar 1, with tones painted in by song time in ms: (from, to, Hz, amplitude). */
    private fun buffer(vararg tones: List<Double>): FloatArray {
        val x = FloatArray(700 * ms)
        for ((from, to, hz, amplitude) in tones) {
            for (i in ((from + 100) * ms).toInt() until ((to + 100) * ms).toInt()) x[i] += (amplitude * sin(2 * PI * hz * i / rate)).toFloat()
        }
        return x
    }

    private fun db(x: FloatArray, from: Int, to: Int): Double =
        20 * log10(sqrt(((from + 100) * ms until (to + 100) * ms).sumOf { (x[it] * x[it]).toDouble() } / ((to - from) * ms)))

    @Test fun `a soft voiced consonant comes up towards the vowels, and the loudest vowel keeps its level`() {
        /* at 400 Hz, where the filters do next to nothing: an m 20 dB under its vowel comes up the most there is, 6 dB */
        val x = buffer(listOf(0.0, 80.0, 400.0, 0.05), listOf(80.0, 300.0, 400.0, 0.5))
        val y = x.copyOf()
        Clarity.apply(y, origin, listOf(phone(Ph.M, 0, 80, 0), phone(Ph.AA, 80, 300, 0)), rate)
        assertEquals(-14.0, db(y, 20, 70) - db(y, 150, 290), 0.5)
        assertEquals(0.0, db(y, 150, 290) - db(x, 150, 290), 0.2)
    }

    @Test fun `the burst of a t stays as soft as it was`() {
        /* the compressor lifts voice, not noise: a burst lifted as far as an m would be a hard click of hiss between the words */
        val x = buffer(listOf(0.0, 200.0, 400.0, 0.5), listOf(270.0, 290.0, 400.0, 0.05), listOf(290.0, 500.0, 400.0, 0.5))
        val y = x.copyOf()
        Clarity.apply(y, origin, listOf(phone(Ph.AA, 0, 200, 0), phone(Ph.T, 200, 290, 1), phone(Ph.AA, 290, 500, 1)), rate)
        assertEquals(-20.0, peak(y, 272, 288) - peak(y, 20, 190), 1.0)
    }

    @Test fun `the low end goes down and the presence range up`() {
        /* three steady tones, one phoneme at one level: only the filters change them */
        for ((hz, change) in listOf(60.0 to -7.5, 400.0 to 0.0, 5000.0 to 5.5)) {
            val x = buffer(listOf(0.0, 500.0, hz, 0.3))
            val y = x.copyOf()
            Clarity.apply(y, origin, listOf(phone(Ph.AA, 0, 500, 0)), rate)
            assertEquals("$hz Hz", change, db(y, 100, 450) - db(x, 100, 450), 1.0)
        }
    }

    /** The RMS of the loudest 10 ms between song times [from] and [to] in ms, in dB. */
    private fun peak(x: FloatArray, from: Int, to: Int): Double =
        (from until to - 10).maxOf { start -> db(x, start, start + 10) }

    @Test fun `an s as loud as its vowel comes down to 6 dB under it, and a soft one stays soft`() {
        /*
         * The de-esser takes a 6 kHz hiss as loud as its vowel down to 6 dB under it, and a few dB further, since it
         * measures and blends the hiss against the band under 2.5 kHz rather than cutting it off. An s 30 dB down
         * stays where it is: the shelf and the compressor are for the voice, not for its noise.
         */
        for ((amplitude, expected) in listOf(0.5 to -9.0, 0.016 to -30.0)) {
            val x = buffer(listOf(0.0, 200.0, 400.0, 0.5), listOf(200.0, 300.0, 6000.0, amplitude))
            val y = x.copyOf()
            Clarity.apply(y, origin, listOf(phone(Ph.AA, 0, 200, 0), phone(Ph.S, 200, 300, 0)), rate)
            assertEquals("an s at $amplitude", expected, peak(y, 210, 290) - peak(y, 20, 190), 1.5)
        }
    }

    @Test fun `a rest stays a rest`() {
        /* a soft tail rings on into a rest: the compressor does not lift it */
        val x = buffer(listOf(0.0, 200.0, 400.0, 0.5), listOf(200.0, 600.0, 400.0, 0.01))
        val y = x.copyOf()
        Clarity.apply(y, origin, listOf(phone(Ph.AA, 0, 200, 0)), rate)
        assertEquals(0.0, db(y, 300, 580) - db(x, 300, 580), 0.2)
        assertTrue(y.all { abs(it) < 1f })
    }
}
