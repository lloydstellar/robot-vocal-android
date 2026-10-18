package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.TestData
import nl.lloydstellar.robotvocal.export.WavFormat
import nl.lloydstellar.robotvocal.export.WavWriter
import nl.lloydstellar.robotvocal.render.Arranger
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.voice.Analysis.centroid
import nl.lloydstellar.robotvocal.voice.Analysis.formants
import nl.lloydstellar.robotvocal.voice.Analysis.pitch
import nl.lloydstellar.robotvocal.voice.Analysis.rms
import nl.lloydstellar.robotvocal.voice.retro.RetroEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Measures the retro engine: formants, hiss, tuning, the tune of a singing voice, and a whole song in every voice. */
class RetroEngineTest {
    private val rate = Analysis.RATE
    private val engine = RetroEngine()

    /** Sounds of 300 ms with 100 ms between them, each its own syllable. */
    private fun sustained(voice: String, vararg phs: Ph, hz: Double = 120.0, lineStarts: Set<Int> = setOf(0)): Pair<Rendered, List<TimedPhone>> {
        var t = 0L
        val phones = phs.mapIndexed { i, ph ->
            TimedPhone(ph, t, t + rate * 3 / 10, stressed = true, pitchHz = hz, syllable = i, lineStart = i in lineStarts)
                .also { t += rate * 4 / 10 }
        }
        return engine.render(phones, voice, rate, seed = 1) to phones
    }

    private fun Rendered.middle(p: TimedPhone) = ((p.start + p.end) / 2 - origin).toInt()

    @Test fun `vowels have the formants of the table, scaled by the voice`() {
        val expected = mapOf(Ph.IY to (270.0 to 2290.0), Ph.AA to (730.0 to 1090.0), Ph.UW to (300.0 to 870.0), Ph.AE to (660.0 to 1720.0))
        val (out, phones) = sustained("retro/desktop", *expected.keys.toTypedArray())
        for (p in phones) {
            val (f1, f2) = formants(out.samples, out.middle(p))
            val (e1, e2) = expected.getValue(p.ph).let { (a, b) -> a * 0.98 to b * 0.98 }
            assertTrue("${p.ph} F1 $f1, expected $e1", abs(f1 - e1) / e1 < 0.2)
            assertTrue("${p.ph} F2 $f2, expected $e2", abs(f2 - e2) / e2 < 0.15)
        }
    }

    @Test fun `s hisses high, sh lower, and both are quieter than a vowel`() {
        val (out, phones) = sustained("retro/desktop", Ph.S, Ph.SH, Ph.AA)
        val s = centroid(out.samples, out.middle(phones[0]))
        val sh = centroid(out.samples, out.middle(phones[1]))
        assertTrue("s centroid $s", s > 4000)
        assertTrue("sh centroid $sh below s", sh in 2000.0..s)
        val vowel = rms(out.samples, out.middle(phones[2]))
        val hiss = rms(out.samples, out.middle(phones[0]))
        assertTrue("s at ${Analysis.db(hiss / vowel)} dB from the vowel", hiss < vowel && hiss > vowel * 0.03)
    }

    @Test fun `a note is in tune, low and high`() {
        for (hz in listOf(98.0, 220.0, 392.0)) {
            val (out, phones) = sustained("retro/monotone", Ph.AA, hz = hz)
            assertEquals("note at $hz Hz", hz, pitch(out.samples, out.middle(phones[0])), hz * 0.003)
        }
    }

    @Test fun `a singing voice puts its tune over the notes, from the start of every line`() {
        /* ARPEGGIO sings 0, 4, 7, 12 ...; the third syllable starts a new line */
        val (out, phones) = sustained("retro/arpeggio", Ph.AA, Ph.AA, Ph.AA, Ph.AA, hz = 110.0, lineStarts = setOf(0, 2))
        for ((p, semitones) in phones.zip(listOf(0, 4, 0, 4))) {
            val hz = 110.0 * 2.0.pow(semitones / 12.0)
            assertEquals("syllable ${p.syllable}", hz, pitch(out.samples, out.middle(p)), hz * 0.004)
        }
    }

    @Test fun `every voice sounds every vowel of a song, keeps the rests silent and renders the same twice`() {
        val song = Song(
            id = "tin-voice", title = "Tin Voice", bpm = 124.0,
            lines = listOf(
                SongLine("I am the voice of the machine"),
                SongLine("Every word right on the beat"),
                SongLine("Turn the dial, push the key"),
                SongLine("Robot vocal, talk to me"),
            ),
        )
        val arrangement = Arranger.arrange(song, TestData.lyrics)
        val dir = File(TestData.root, "tools/audio").apply { mkdirs() }
        for (voice in engine.voices) {
            val s = song.copy(voice = voice.id)
            val take = Renderer.render(s, arrangement, engine)
            val x = take.channels[0]
            assertTrue("${voice.name} differs between two renders", x.contentEquals(Renderer.render(s, arrangement, engine).channels[0]))
            assertTrue("${voice.name} is not finite", x.all { it.isFinite() })
            fun level(from: Long, to: Long) = level(x, (from - take.origin).toInt(), (to - take.origin).toInt())
            val vowels = take.phones.filter { it.vowel }.map { it to level(it.start, it.end) }
            val typical = vowels.map { it.second }.sorted()[vowels.size / 2]
            for ((v, l) in vowels) assertTrue("${voice.name}: vowel ${v.ph} at ${v.start} is silent", l > typical * 0.2)
            /* a rest of more than 80 ms: after 30 ms of ringing out it is quiet until the next sound */
            for ((a, b) in take.phones.zipWithNext()) {
                if (b.start - a.end < rate * 80 / 1000) continue
                val gap = level(a.end + rate * 30 / 1000, b.start - rate * 5 / 1000)
                assertTrue("${voice.name}: rest after ${a.ph} at ${a.end} at ${Analysis.db(gap / typical)} dB", gap < typical * 0.03)
            }
            File(dir, "tin-voice-retro-${voice.id.substringAfter('/')}.wav").outputStream().use {
                WavWriter.write(Renderer.loop(take)[0], rate, WavFormat.PCM24, WavWriter.Meta("Tin Voice", song.bpm, arrangement.bars * 4, loop = true), it)
            }
        }
    }

    private fun level(x: FloatArray, from: Int, to: Int): Double {
        val a = from.coerceIn(0, x.size)
        val b = to.coerceIn(a, x.size)
        var sum = 0.0
        for (i in a until b) sum += x[i] * x[i]
        return if (b > a) sqrt(sum / (b - a)) else 0.0
    }
}
