package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.TestData
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine
import nl.lloydstellar.robotvocal.voice.formant.FormantEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Notes and stutters per syllable reach the sound. */
class MelodyTest {
    private val engine = FormantEngine()
    private val rate = 48_000

    private fun f0(x: FloatArray, from: Int, to: Int): Double {
        var best = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in rate / 500..rate / 50) {
            var sum = 0.0
            for (i in from until to - lag) sum += x[i] * x[i + lag]
            if (sum > bestScore) {
                bestScore = sum
                best = lag
            }
        }
        return rate.toDouble() / best
    }

    @Test fun `a note twelve semitones up sings an octave higher`() {
        val base = Song("m", "M", 120.0, listOf(SongLine("go go")), voice = "formant/unit1", note = 48)
        val up = base.copy(lines = listOf(SongLine("go go", notes = mapOf(1 to 12))))
        val arrangement = Arranger.arrange(base, TestData.lyrics)
        val a = Renderer.render(base, arrangement, engine)
        val b = Renderer.render(up, arrangement, engine)
        val vowel = b.phones.filter { it.vowel }[1]
        val from = (vowel.start - b.origin).toInt() + 2400
        val to = from + 4800
        val low = f0(a.dry, from, to)
        val high = f0(b.dry, from, to)
        assertTrue("from $low Hz to $high Hz", abs(high / low - 2.0) < 0.06)
    }

    @Test fun `a stutter repeats the start of the syllable`() {
        val song = Song("s", "S", 100.0, listOf(SongLine("robot", stutters = mapOf(0 to 4))), voice = "formant/unit1")
        val take = Renderer.render(song, Arranger.arrange(song, TestData.lyrics), engine)
        val start = (take.phones.first().start - take.origin).toInt()
        val slice = (0.125 * 60.0 / 100.0 * rate).toInt()
        /* the second slice is a copy of the first, apart from the fades */
        val margin = rate / 400
        var same = 0
        for (i in margin until slice - margin) if (abs(take.dry[start + i] - take.dry[start + slice + i]) < 1e-4f) same++
        assertTrue("$same of ${slice - 2 * margin} samples repeat", same > (slice - 2 * margin) * 0.95)
    }
}
