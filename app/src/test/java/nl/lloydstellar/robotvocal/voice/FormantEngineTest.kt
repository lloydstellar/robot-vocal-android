package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.voice.formant.FormantEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Measures what the formant engine makes: vowel formants by LPC, hiss in the right band. */
class FormantEngineTest {
    private val rate = 48_000
    private val engine = FormantEngine()

    private fun sustained(vararg phs: Ph): Pair<Rendered, List<TimedPhone>> {
        var t = 0L
        val phones = phs.map { ph ->
            TimedPhone(ph, t, t + rate * 3 / 10, stressed = true, pitchHz = 120.0, syllable = 0).also { t += rate * 4 / 10 }
        }
        return engine.render(phones, "formant/unit1", rate, seed = 1) to phones
    }

    @Test fun `vowels have their formants where the table puts them`() {
        val expected = mapOf(Ph.IY to (270.0 to 2290.0), Ph.AA to (730.0 to 1090.0), Ph.UW to (300.0 to 870.0), Ph.AE to (660.0 to 1720.0))
        val (out, phones) = sustained(*expected.keys.toTypedArray())
        for (p in phones) {
            val mid = ((p.start + p.end) / 2 - out.origin).toInt()
            val (f1, f2) = formants(out.samples, mid)
            val (e1, e2) = expected.getValue(p.ph)
            assertTrue("${p.ph} F1 $f1, expected $e1", abs(f1 - e1) / e1 < 0.2)
            assertTrue("${p.ph} F2 $f2, expected $e2", abs(f2 - e2) / e2 < 0.15)
        }
    }

    @Test fun `s hisses high, sh lower, and both are quieter than a vowel`() {
        val (out, phones) = sustained(Ph.S, Ph.SH, Ph.AA)
        fun window(i: Int) = ((phones[i].start + phones[i].end) / 2 - out.origin).toInt()
        val sCentroid = centroid(out.samples, window(0))
        val shCentroid = centroid(out.samples, window(1))
        assertTrue("s centroid $sCentroid", sCentroid > 4500)
        assertTrue("sh centroid $shCentroid below s", shCentroid in 2000.0..sCentroid)
        val vowel = rms(out.samples, window(2))
        val s = rms(out.samples, window(0))
        assertTrue("s ${db(s / vowel)} dB under the vowel", s < vowel && s > vowel * 0.05)
    }

    /* ---------------------------------------------------------------- analysis */

    private fun rms(x: FloatArray, centre: Int, n: Int = 1920): Double {
        var sum = 0.0
        for (i in centre - n / 2 until centre + n / 2) sum += x[i] * x[i]
        return sqrt(sum / n)
    }

    private fun db(r: Double) = 20 * kotlin.math.log10(r)

    /** Spectral centroid of a 40 ms window, in Hz. */
    private fun centroid(x: FloatArray, centre: Int): Double {
        val n = 1920
        var num = 0.0
        var den = 0.0
        for (k in 1 until 400) {
            val f = k * 20.0
            var re = 0.0
            var im = 0.0
            for (i in 0 until n) {
                val v = x[centre - n / 2 + i] * (0.5 - 0.5 * cos(2 * PI * i / n))
                re += v * cos(2 * PI * f * i / rate)
                im -= v * sin(2 * PI * f * i / rate)
            }
            val m = hypot(re, im)
            num += f * m
            den += m
        }
        return num / den
    }

    /** F1 and F2 of a 40 ms window: LPC of order 12 after down-sampling to 8 kHz, peaks of its envelope. */
    private fun formants(x: FloatArray, centre: Int): Pair<Double, Double> {
        val down = 6
        val n = 320
        val y = DoubleArray(n) { j ->
            var s = 0.0
            for (k in 0 until down) s += x[centre - n * down / 2 + j * down + k]
            s / down
        }
        for (i in n - 1 downTo 1) y[i] -= 0.9 * y[i - 1]
        for (i in 0 until n) y[i] *= 0.54 - 0.46 * cos(2 * PI * i / (n - 1))
        val order = 12
        val r = DoubleArray(order + 1) { lag -> (0 until n - lag).sumOf { y[it] * y[it + lag] } }
        val a = DoubleArray(order + 1).also { it[0] = 1.0 }
        var err = r[0]
        for (i in 1..order) {
            var acc = r[i]
            for (j in 1 until i) acc += a[j] * r[i - j]
            val k = -acc / err
            val prev = a.copyOf()
            for (j in 1 until i) a[j] = prev[j] + k * prev[i - j]
            a[i] = k
            err *= 1 - k * k
        }
        val fs = rate / down.toDouble()
        val env = (0..390).map { step ->
            val f = 100.0 + step * 10.0
            var re = 0.0
            var im = 0.0
            for (j in 0..order) {
                re += a[j] * cos(2 * PI * f * j / fs)
                im -= a[j] * sin(2 * PI * f * j / fs)
            }
            f to 1.0 / hypot(re, im)
        }
        val peaks = env.indices.filter { it in 1 until env.size - 1 && env[it].second > env[it - 1].second && env[it].second >= env[it + 1].second }
            .map { env[it].first }
        return peaks[0] to peaks[1]
    }
}
