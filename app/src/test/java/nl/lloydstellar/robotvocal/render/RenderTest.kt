package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.TestData
import nl.lloydstellar.robotvocal.export.WavFormat
import nl.lloydstellar.robotvocal.export.WavWriter
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine
import nl.lloydstellar.robotvocal.score.Ticks
import nl.lloydstellar.robotvocal.voice.formant.FormantEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Renders songs with the formant engine and checks the timing and the sound.
 * The WAV files go to tools/audio/ (not in git), to check by ear.
 */
class RenderTest {
    private val engine = FormantEngine()
    private val song = Song(
        id = "tin-voice", title = "Tin Voice", bpm = 124.0,
        lines = listOf(
            SongLine("I am the voice of the machine"),
            SongLine("Every word right on the beat"),
            SongLine("Turn the dial, push the key"),
            SongLine("Robot vocal, talk to me"),
        ),
    )

    @Test fun `every vowel starts exactly on its step`() {
        val arrangement = Arranger.arrange(song, TestData.lyrics)
        val take = Renderer.render(song, arrangement, engine)
        val vowels = take.phones.filter { it.vowel }.groupBy { it.syllable }.values.map { it.first() }
        val beats = arrangement.lines.flatMap { line ->
            val perSlot = Ticks.perSlot(line.placement.slotsPerBar)
            line.placement.placed.map { Ticks.frame(line.startTick + it.slot * perSlot.toDouble(), song.bpm, 48_000) }
        }
        assertEquals(beats.size, vowels.size)
        for ((vowel, beat) in vowels.zip(beats)) assertEquals("vowel ${vowel.ph} of syllable ${vowel.syllable}", beat, vowel.start)
    }

    @Test fun `the loop is a whole number of bars and every vowel sounds`() {
        val arrangement = Arranger.arrange(song, TestData.lyrics)
        val take = Renderer.render(song, arrangement, engine)
        val loop = Renderer.loop(take)[0]
        assertEquals(Ticks.loopFrames(arrangement.bars * 4, song.bpm, 48_000), loop.size.toLong())
        assertTrue(loop.all { it.isFinite() })
        for (v in take.phones.filter { it.vowel }) {
            val from = (v.start - take.origin).toInt()
            val to = (v.end - take.origin).toInt()
            assertTrue("vowel ${v.ph} at ${v.start} is silent", rms(take.channels[0], from, to) > 0.02)
        }
    }

    @Test fun `every character renders the same song, the same every time`() {
        val arrangement = Arranger.arrange(song, TestData.lyrics)
        val dir = File(TestData.root, "tools/audio").apply { mkdirs() }
        for (voice in engine.voices) {
            val s = song.copy(voice = voice.id)
            val a = Renderer.loop(Renderer.render(s, arrangement, engine))[0]
            val b = Renderer.loop(Renderer.render(s, arrangement, engine))[0]
            assertTrue("${voice.name} differs between two renders", a.contentEquals(b))
            assertTrue("${voice.name} clips", a.all { it in -1f..1f })
            File(dir, "tin-voice-${voice.id.substringAfter('/')}.wav").outputStream().use {
                WavWriter.write(a, 48_000, WavFormat.PCM24, WavWriter.Meta("Tin Voice", song.bpm, arrangement.bars * 4, loop = true), it)
            }
        }
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        val a = from.coerceIn(0, x.size)
        val b = to.coerceIn(a, x.size)
        for (i in a until b) sum += x[i] * x[i]
        return if (b > a) sqrt(sum / (b - a)) else 0.0
    }
}
