package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.TestData
import nl.lloydstellar.robotvocal.export.WavFormat
import nl.lloydstellar.robotvocal.export.WavWriter
import nl.lloydstellar.robotvocal.render.Arranger
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.math.sqrt

/** TIN VOICE in every voice of an engine: what every voice must do with a whole song. */
object SongCheck {
    private val song = Song(
        id = "tin-voice", title = "Tin Voice", bpm = 124.0,
        lines = listOf(
            SongLine("I am the voice of the machine"),
            SongLine("Every word right on the beat"),
            SongLine("Turn the dial, push the key"),
            SongLine("Robot vocal, talk to me"),
        ),
    )

    /**
     * Every vowel sounds, a rest of more than 80 ms is quiet after 30 ms of
     * ringing out, and two renders are the same. Each take is written to
     * tools/audio as tin-voice-[prefix]-<voice>.wav for listening.
     */
    fun everyVoice(engine: VoiceEngine, prefix: String) {
        val rate = Analysis.RATE
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
            for ((a, b) in take.phones.zipWithNext()) {
                if (b.start - a.end < rate * 80 / 1000) continue
                val gap = level(a.end + rate * 30 / 1000, b.start - rate * 5 / 1000)
                assertTrue("${voice.name}: rest after ${a.ph} at ${a.end} at ${Analysis.db(gap / typical)} dB", gap < typical * 0.03)
            }
            File(dir, "tin-voice-$prefix-${voice.id.substringAfter('/')}.wav").outputStream().use {
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
