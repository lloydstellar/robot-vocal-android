package nl.lloydstellar.robotvocal.dsp

import nl.lloydstellar.robotvocal.TestData
import nl.lloydstellar.robotvocal.data.SongStore
import nl.lloydstellar.robotvocal.export.WavFormat
import nl.lloydstellar.robotvocal.export.WavWriter
import nl.lloydstellar.robotvocal.render.Arranger
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.voice.formant.FormantEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Delays land on the beat, echo throws only on line ends, and every motion and space preset renders cleanly. */
class SpaceTest {
    private val rate = 48_000

    private fun ctx(bpm: Double, lineEnds: List<LongRange> = emptyList()) = FxContext(rate, bpm, 0, 1, lineEnds) { 110.0 }

    @Test fun `echoes come exactly a dotted 8th apart at any tempo`() {
        for (bpm in listOf(90.0, 124.0, 127.0, 140.0)) {
            val x = FloatArray(rate * 3).also { it[1000] = 1f }
            val y = BeatDelay(beats = 0.75, feedback = 0.5, mix = 1.0, highCut = 20_000.0, lowCut = 1.0).process(x, ctx(bpm))
            val gap = Math.round(0.75 * 60.0 / bpm * rate).toInt()
            val first = (1000 + gap - 3..1000 + gap + 3).maxBy { abs(y[it]) }
            val second = (1000 + 2 * gap - 3..1000 + 2 * gap + 3).maxBy { abs(y[it]) }
            assertTrue("first echo at $bpm BPM: $first, not ${1000 + gap}", abs(first - (1000 + gap)) <= 1)
            assertTrue("second echo at $bpm BPM: $second, not ${1000 + 2 * gap}", abs(second - (1000 + 2 * gap)) <= 2)
        }
    }

    @Test fun `an echo throw repeats only the line ends`() {
        val x = FloatArray(rate * 2) { if (it in 1000..2000 || it in 30_000..31_000) 0.5f else 0f }
        val y = BeatDelay(beats = 1.0, feedback = 0.0, mix = 1.0, onlyLineEnds = true).process(x, ctx(120.0, listOf(30_000L..31_000L)))
        val beat = rate / 2
        assertTrue("no echo of the first sound", (1000 + beat..2000 + beat).all { abs(y[it]) < 1e-3 })
        assertTrue("an echo of the line end", (30_200 + beat..30_800 + beat).any { abs(y[it]) > 0.05 })
    }

    @Test fun `every motion and space preset renders, in range and repeatable`() {
        val engine = FormantEngine()
        val song = SongStore.EXAMPLES.first().copy(effect = "monotone")
        val arrangement = Arranger.arrange(song, TestData.lyrics)
        val dir = File(TestData.root, "tools/audio/presets").apply { mkdirs() }
        val variants = RobotPresets.MOTION.map { song.copy(motion = it.id) to "motion-${it.id}" } +
            RobotPresets.SPACE.map { song.copy(space = it.id) to "space-${it.id}" }
        for ((s, name) in variants) {
            val a = Renderer.loop(Renderer.render(s, arrangement, engine))
            val b = Renderer.loop(Renderer.render(s, arrangement, engine))
            assertTrue("$name has NaN or is out of range", a.all { it.isFinite() && it in -1f..1f })
            assertTrue("$name differs between renders", a.contentEquals(b))
            assertTrue("$name is silent", a.any { abs(it) > 0.1f })
            File(dir, "$name.wav").outputStream().use {
                WavWriter.write(a, rate, WavFormat.PCM24, WavWriter.Meta(name, song.bpm, arrangement.bars * 4, true), it)
            }
        }
    }
}
