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
import kotlin.math.sqrt

/**
 * Every robot effect on the example song: it must sound, stay finite and in
 * range, and give the same samples every time. The WAVs go to
 * tools/audio/presets/ to check by ear.
 */
class PresetsTest {
    private val engine = FormantEngine()
    private val song = SongStore.EXAMPLES.first()
    private val arrangement = Arranger.arrange(song, TestData.lyrics)

    @Test fun `every preset sounds, stays in range and repeats exactly`() {
        val dir = File(TestData.root, "tools/audio/presets").apply { mkdirs() }
        val report = StringBuilder()
        for (preset in RobotPresets.ALL) {
            val s = song.copy(effect = preset.id)
            val started = System.nanoTime()
            val loop = Renderer.loop(Renderer.render(s, arrangement, engine))[0]
            val ms = (System.nanoTime() - started) / 1_000_000
            val again = Renderer.loop(Renderer.render(s, arrangement, engine))[0]
            val rms = sqrt(loop.sumOf { it.toDouble() * it } / loop.size)
            report.append("${preset.name}: ${ms} ms, rms %.3f\n".format(rms))
            assertTrue("${preset.name} has NaN", loop.all { it.isFinite() })
            assertTrue("${preset.name} is out of range", loop.all { it in -1f..1f })
            assertTrue("${preset.name} is too quiet: $rms", rms > 0.03)
            assertTrue("${preset.name} differs between renders", loop.contentEquals(again))
            File(dir, "${preset.id}.wav").outputStream().use {
                WavWriter.write(loop, 48_000, WavFormat.PCM24, WavWriter.Meta(preset.name, song.bpm, arrangement.bars * 4, true), it)
            }
        }
        File(dir, "report.txt").writeText(report.toString())
    }
}
