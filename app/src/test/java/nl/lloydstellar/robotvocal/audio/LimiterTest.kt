package nl.lloydstellar.robotvocal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class LimiterTest {
    private val rate = 48_000
    private val delay = rate * 2 / 1000 - 1

    /** Runs [signal] (interleaved stereo) through a limiter in blocks of uneven size, as the player does. */
    private fun limit(signal: FloatArray): FloatArray {
        val limiter = Limiter(rate)
        val out = signal.copyOf()
        var frame = 0
        val sizes = intArrayOf(1024, 7, 333, 1)
        var k = 0
        while (frame < out.size / 2) {
            val n = minOf(sizes[k++ % sizes.size], out.size / 2 - frame)
            val block = out.copyOfRange(2 * frame, 2 * (frame + n))
            limiter.process(block, n)
            block.copyInto(out, 2 * frame)
            frame += n
        }
        return out
    }

    @Test fun `nothing passes the ceiling`() {
        val random = Random(7)
        val signal = FloatArray(2 * rate) {
            val i = it / 2
            val tone = sin(2 * PI * 110 * i / rate).toFloat() * if (it % 2 == 0) 1.6f else 0.3f
            tone + if (random.nextInt(500) == 0) random.nextFloat() * 6 - 3 else 0f
        }
        val out = limit(signal)
        assertTrue("peak ${out.maxOf { abs(it) }}", out.all { abs(it) <= Limiter.CEILING * 1.000001f })
    }

    @Test fun `a quiet signal comes out unchanged, two milliseconds late`() {
        val signal = FloatArray(2 * rate / 2) { (sin(2 * PI * 440 * (it / 2) / rate) * 0.5).toFloat() }
        val out = limit(signal)
        for (i in delay until signal.size / 2) {
            assertEquals(signal[2 * (i - delay)], out[2 * i], 0f)
        }
    }

    @Test fun `the gain is down before a peak arrives and moves without jumps`() {
        /* DC, so the gain is out / in: 0.5 for 100 ms, then 2.0 */
        val frames = rate / 5
        val input = FloatArray(frames) { if (it < rate / 10) 0.5f else 2f }
        val out = limit(FloatArray(2 * frames) { input[it / 2] })
        /* gains[j] is the gain on input frame j */
        val gains = (delay until frames).map { out[2 * it] / input[it - delay] }
        val step = Limiter.CEILING / 2f
        assertTrue("peak passes", (delay until frames).all { abs(out[2 * it]) <= Limiter.CEILING * 1.000001f })
        assertTrue("gain at the step ${gains[rate / 10]}", gains[rate / 10] <= step * 1.000001f)
        assertEquals("gain before the ramp", 1f, gains[rate / 10 - delay - 2], 0f)
        val biggest = gains.zipWithNext().maxOf { (a, b) -> abs(b - a) }
        assertTrue("largest gain jump $biggest", biggest <= (1 - step) / (delay + 1) * 1.01f)
    }
}
