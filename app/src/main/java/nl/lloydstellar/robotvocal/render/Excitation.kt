package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.text.Manner
import nl.lloydstellar.robotvocal.text.Ph
import kotlin.math.min

/**
 * What sets a phoneme sounding, part by part: the voice, noise, or both. A
 * robot that replaces the voice with its own sound (a vocoder's carrier, a
 * speech chip's buzz) needs this to keep its consonants: an s spoken on a
 * pitch is a buzz, an s on noise is an s.
 */
object Excitation {
    /** A stretch of a phoneme and how much of its sound is noise: 0 voice only, 1 noise only. */
    data class Part(val start: Double, val end: Double, val noise: Double) {
        val voiced: Boolean get() = noise < 1.0
    }

    /**
     * The parts of [ph] sounding from [start] to [end] (in frames at [rate]).
     * A voiced stop (b, d, g) is a voiced closure and then a burst of noise,
     * the last [BURST_MS] (at most 40 %); the j of "judge" a voiced closure and
     * then its hiss. A voiced fricative (v, z) is voice and noise at once.
     */
    fun parts(ph: Ph, start: Double, end: Double, rate: Int): List<Part> {
        val split = when (ph) {
            Ph.B, Ph.D, Ph.G -> end - min(rate * BURST_MS / 1000.0, (end - start) * 0.4)
            Ph.JH -> (start + end) / 2
            else -> return listOf(Part(start, end, noise(ph)))
        }
        return listOf(Part(start, split, 0.0), Part(split, end, 1.0))
    }

    /**
     * What a vocoder or a talkbox should put under [ph]: its [parts], except
     * that p, t, k and ch say nothing until their burst. Their closure is
     * silence, and the sound before it still rings in the bands for a few
     * milliseconds; switched to noise, that ring would be a burst of hiss.
     */
    fun carrier(ph: Ph, start: Double, end: Double, rate: Int): List<Part> = when (ph) {
        Ph.P, Ph.T, Ph.K -> listOf(Part(end - min(rate * BURST_MS / 1000.0, (end - start) * 0.4), end, 1.0))
        Ph.CH -> listOf(Part((start + end) / 2, end, 1.0))
        else -> parts(ph, start, end, rate)
    }

    /** Whether [ph] ends voiced: a voiced stop ends in its burst. */
    fun endsVoiced(ph: Ph): Boolean = ph.voiced && ph !in setOf(Ph.B, Ph.D, Ph.G, Ph.JH)

    private fun noise(ph: Ph): Double = when {
        !ph.voiced -> 1.0
        ph.manner == Manner.FRICATIVE -> 0.5
        else -> 0.0
    }

    private const val BURST_MS = 20
}
