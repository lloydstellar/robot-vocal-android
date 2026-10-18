package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.voice.Analysis.centroid
import nl.lloydstellar.robotvocal.voice.Analysis.db
import nl.lloydstellar.robotvocal.voice.Analysis.formants
import nl.lloydstellar.robotvocal.voice.Analysis.rms
import nl.lloydstellar.robotvocal.voice.formant.FormantEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Measures what the formant engine makes: vowel formants by LPC, hiss in the right band. */
class FormantEngineTest {
    private val rate = Analysis.RATE
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
}
