package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.voice.Analysis.centroid
import nl.lloydstellar.robotvocal.voice.Analysis.db
import nl.lloydstellar.robotvocal.voice.Analysis.formants
import nl.lloydstellar.robotvocal.voice.Analysis.pitch
import nl.lloydstellar.robotvocal.voice.Analysis.rms
import nl.lloydstellar.robotvocal.voice.retro.K
import nl.lloydstellar.robotvocal.voice.retro.MicroEngine
import nl.lloydstellar.robotvocal.voice.retro.MicroSynth
import nl.lloydstellar.robotvocal.voice.retro.RetroEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Measures the home computer engine: formants, hiss in 4 bits, tuning, its 16 levels, and a whole song in every voice. */
class MicroEngineTest {
    private val rate = Analysis.RATE
    private val engine = MicroEngine()

    /** Sounds of 300 ms with 100 ms between them, each its own syllable. */
    private fun sustained(voice: String, vararg phs: Ph, hz: Double = 110.0): Pair<Rendered, List<TimedPhone>> {
        var t = 0L
        val phones = phs.mapIndexed { i, ph ->
            TimedPhone(ph, t, t + rate * 3 / 10, stressed = true, pitchHz = hz, syllable = i, lineStart = i == 0)
                .also { t += rate * 4 / 10 }
        }
        return engine.render(phones, voice, rate, seed = 1) to phones
    }

    private fun Rendered.middle(p: TimedPhone) = ((p.start + p.end) / 2 - origin).toInt()

    @Test fun `the first two formants are those of the table`() {
        /* IY is left out: next to its F2 sits the mirror image of the square on F3, a peak the old machines had too */
        val expected = mapOf(Ph.AA to (730.0 to 1090.0), Ph.UW to (300.0 to 870.0), Ph.AE to (660.0 to 1720.0), Ph.IH to (390.0 to 1990.0))
        val (out, phones) = sustained("micro/home", *expected.keys.toTypedArray())
        for (p in phones) {
            val (f1, f2) = formants(out.samples, out.middle(p))
            val (e1, e2) = expected.getValue(p.ph)
            assertTrue("${p.ph} F1 $f1, expected $e1", abs(f1 - e1) / e1 < 0.2)
            assertTrue("${p.ph} F2 $f2, expected $e2", abs(f2 - e2) / e2 < 0.15)
        }
    }

    @Test fun `every hiss sounds in 4 bits, below the vowel`() {
        val (out, phones) = sustained("micro/home", Ph.AA, Ph.S, Ph.SH, Ph.F, Ph.TH, Ph.HH)
        val vowel = rms(out.samples, out.middle(phones[0]))
        for (p in phones.drop(1)) {
            val level = db(rms(out.samples, out.middle(p)) / vowel)
            assertTrue("${p.ph} at $level dB", level in -25.0..-1.0)
        }
        assertTrue("s brighter than sh", centroid(out.samples, out.middle(phones[1])) > centroid(out.samples, out.middle(phones[2])))
    }

    @Test fun `a note is in tune, low and high`() {
        for (hz in listOf(98.0, 220.0, 392.0)) {
            val (out, phones) = sustained("micro/home", Ph.AA, hz = hz)
            assertEquals("note at $hz Hz", hz, pitch(out.samples, out.middle(phones[0])), hz * 0.003)
        }
    }

    @Test fun `a 4-bit voice has 16 levels and silence`() {
        val synth = MicroSynth(RetroEngine.RATE, 1, bits = 4, stepRate = 7350.0)
        val out = FloatArray(RetroEngine.RATE)
        val vowel = K.DEFAULT.copyOf().also { it[K.F0] = 110.0; it[K.AV] = 60.0 }
        val hiss = K.DEFAULT.copyOf().also { it[K.AF] = 60.0; it[K.A5] = 50.0; it[K.A6] = 55.0 }
        var i = 0
        while (i < out.size) {
            val n = minOf(22, out.size - i)
            synth.render(if (i < out.size / 2) vowel else hiss, out, i, n)
            i += n
        }
        val levels = out.toSet()
        assertTrue("${levels.size} levels", levels.size <= 17)
    }

    @Test fun `every voice sounds every vowel of a song, keeps the rests silent and renders the same twice`() {
        SongCheck.everyVoice(engine, "micro")
    }
}
