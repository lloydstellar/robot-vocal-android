package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.voice.Analysis.formants
import nl.lloydstellar.robotvocal.voice.Analysis.pitch
import nl.lloydstellar.robotvocal.voice.Analysis.rms
import nl.lloydstellar.robotvocal.voice.chip.SpellEngine
import nl.lloydstellar.robotvocal.voice.retro.RetroEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Measures the speech chip voice. In the app it speaks Flite's kal, which
 * needs the native library; here it speaks the retro engine's DESKTOP, so
 * what is measured is the chip and its encoder.
 */
class SpellEngineTest {
    private val rate = Analysis.RATE
    private val engine = SpellEngine(RetroEngine(), "retro/desktop")

    /** Sounds of 300 ms with 100 ms between them, each its own syllable. */
    private fun sustained(vararg phs: Ph, hz: Double = 110.0): Pair<Rendered, List<TimedPhone>> {
        var t = 0L
        val phones = phs.mapIndexed { i, ph ->
            TimedPhone(ph, t, t + rate * 3 / 10, stressed = true, pitchHz = hz, syllable = i, lineStart = i == 0)
                .also { t += rate * 4 / 10 }
        }
        return engine.render(phones, "chip/speller", rate, seed = 1) to phones
    }

    private fun Rendered.middle(p: TimedPhone) = ((p.start + p.end) / 2 - origin).toInt()

    @Test fun `ten poles at 8 kHz in the chip's tables keep the formants of the speech`() {
        /* the retro table's formants, scaled by DESKTOP's 0.98 */
        val expected = mapOf(Ph.IY to (270.0 to 2290.0), Ph.EH to (530.0 to 1840.0), Ph.AA to (730.0 to 1090.0), Ph.UW to (300.0 to 870.0))
        val (out, phones) = sustained(*expected.keys.toTypedArray())
        for (p in phones) {
            val (f1, f2) = formants(out.samples, out.middle(p))
            val (e1, e2) = expected.getValue(p.ph).let { (a, b) -> a * 0.98 to b * 0.98 }
            assertTrue("${p.ph} F1 $f1, expected $e1", abs(f1 - e1) / e1 < 0.3)
            assertTrue("${p.ph} F2 $f2, expected $e2", abs(f2 - e2) / e2 < 0.12)
        }
    }

    @Test fun `a note is in tune, low and high`() {
        for (hz in listOf(98.0, 220.0, 392.0)) {
            val (out, phones) = sustained(Ph.AA, hz = hz)
            assertEquals("note at $hz Hz", hz, pitch(out.samples, out.middle(phones[0])), hz * 0.003)
        }
    }

    @Test fun `a vowel after a rest starts on its frame`() {
        val (out, phones) = sustained(Ph.AA, Ph.AA)
        val start = (phones[1].start - out.origin).toInt()
        val before = rms(out.samples, start - rate / 200, rate / 200)
        val after = rms(out.samples, start + rate / 200, rate / 200)
        assertTrue("before ${Analysis.db(before)} dB, after ${Analysis.db(after)} dB", before < after * 0.05)
    }

    @Test fun `every hiss comes out as noise, quieter than a vowel`() {
        val (out, phones) = sustained(Ph.AA, Ph.S, Ph.SH, Ph.F)
        val vowel = rms(out.samples, out.middle(phones[0]))
        for (p in phones.drop(1)) {
            val level = Analysis.db(rms(out.samples, out.middle(p)) / vowel)
            assertTrue("${p.ph} at $level dB", level in -35.0..-3.0)
        }
    }

    @Test fun `every voice sounds every vowel of a song, keeps the rests silent and renders the same twice`() {
        SongCheck.everyVoice(engine, "chip")
    }
}
