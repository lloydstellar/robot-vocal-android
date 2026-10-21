package nl.lloydstellar.robotvocal.render

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How late the vowels of a take sound after their step. Every engine starts a
 * vowel where it is planned, but its sound takes a moment to come up: the
 * first pulse of the voice can come most of a period later, filters ring in,
 * a synthesizer fades in its frames. Within a few milliseconds a producer
 * hears it against a kick, and in a DAW it shows on the grid.
 *
 * Measured on the vowels that start out of silence or after a hiss, where
 * the start can be seen: the time until the vowel first reaches a tenth of
 * the loudest of its first 50 ms. The median of those is how much earlier
 * the take must start for its vowels to be heard on their steps. At most
 * [MOST_MS]; nothing when the take has fewer than two such vowels.
 */
object Onsets {
    /** The latency of the vowels of [x] (sample 0 is song frame [origin]), in frames. */
    fun latency(x: FloatArray, origin: Long, phones: List<TimedPhone>, rate: Int): Long {
        val before = rate * QUIET_MS / 1000
        val after = rate * LOOK_MS / 1000
        val late = ArrayList<Int>()
        for (p in phones) {
            if (!p.vowel) continue
            val at = (p.start - origin).toInt()
            if (at - before < 0 || at + after > x.size) continue
            val peak = loudest(x, at, at + after)
            if (peak <= 0f || loudest(x, at - before, at) > peak * SHARE) continue
            var i = at
            while (abs(x[i]) < peak * SHARE) i++
            late += i - at
        }
        if (late.size < 2) return 0
        late.sort()
        return min(late[late.size / 2], rate * MOST_MS / 1000).toLong()
    }

    private fun loudest(x: FloatArray, from: Int, to: Int): Float {
        var m = 0f
        for (i in from until to) m = max(m, abs(x[i]))
        return m
    }

    /** The share of a vowel's first loudness where it counts as started. */
    private const val SHARE = 0.1f
    private const val QUIET_MS = 10
    private const val LOOK_MS = 50
    private const val MOST_MS = 20
}
