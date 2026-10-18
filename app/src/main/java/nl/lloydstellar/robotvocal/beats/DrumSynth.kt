package nl.lloydstellar.robotvocal.beats

import nl.lloydstellar.robotvocal.dsp.Biquad
import nl.lloydstellar.robotvocal.dsp.Noise
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class Drum { KICK, SNARE, CLAP, HAT, OPEN_HAT, COWBELL, RIM, TOM }

/**
 * Drum sounds made in code, in the manner of an analogue drum machine of 1980:
 * a kick that is a sine falling in pitch, a snare of two tones and noise, hats
 * from six square waves at inharmonic pitches, a cowbell from two. No samples,
 * so nothing to license. These sounds are for the preview only.
 */
object DrumSynth {
    /** The six oscillator pitches of the classic analogue cymbal and hat circuit. */
    private val METAL = doubleArrayOf(205.3, 304.4, 369.6, 522.7, 540.0, 800.0)

    fun make(drum: Drum, rate: Int): FloatArray = when (drum) {
        Drum.KICK -> kick(rate)
        Drum.SNARE -> snare(rate)
        Drum.CLAP -> clap(rate)
        Drum.HAT -> metal(rate, 0.045, 1.0)
        Drum.OPEN_HAT -> metal(rate, 0.32, 0.8)
        Drum.COWBELL -> cowbell(rate)
        Drum.RIM -> rim(rate)
        Drum.TOM -> tom(rate)
    }

    private fun kick(rate: Int): FloatArray {
        val n = (rate * 0.6).toInt()
        val noise = Noise(11)
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            val f = 48.0 + 120.0 * exp(-t / 0.03)
            phase += 2 * PI * f / rate
            val body = sin(phase) * exp(-t / 0.42)
            val click = noise.next() * exp(-t / 0.0015) * 0.3
            (tanh((body + click) * 1.6) * 0.9).toFloat()
        }
    }

    private fun snare(rate: Int): FloatArray {
        val n = (rate * 0.3).toInt()
        val noise = Noise(22)
        val hp = Biquad.highPass(1800.0, 0.7, rate)
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            val tone = (sin(2 * PI * 185 * t) * 0.6 + sin(2 * PI * 330 * t) * 0.4) * exp(-t / 0.06)
            val hiss = hp.process(noise.next()) * exp(-t / 0.13)
            ((tone * 0.55 + hiss * 0.8) * 0.8).toFloat()
        }
    }

    private fun clap(rate: Int): FloatArray {
        val n = (rate * 0.35).toInt()
        val noise = Noise(33)
        val bp = Biquad.bandPass(1150.0, 1.2, rate)
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            val bursts = listOf(0.0, 0.011, 0.022).sumOf { s -> if (t >= s) exp(-(t - s) / 0.006) else 0.0 }
            val tail = if (t >= 0.03) exp(-(t - 0.03) / 0.12) * 0.7 else 0.0
            (bp.process(noise.next()) * (bursts + tail) * 2.2).toFloat()
        }
    }

    private fun metal(rate: Int, decay: Double, gain: Double): FloatArray {
        val n = (rate * (decay * 5)).toInt()
        val bp = Biquad.bandPass(7600.0, 1.4, rate)
        val hp = Biquad.highPass(6200.0, 0.7, rate)
        val phases = DoubleArray(METAL.size)
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            var sum = 0.0
            for (k in METAL.indices) {
                phases[k] += METAL[k] * 1.7 / rate
                if (phases[k] >= 1) phases[k] -= 1
                sum += if (phases[k] < 0.5) 1.0 else -1.0
            }
            (hp.process(bp.process(sum / METAL.size)) * exp(-t / decay) * 1.6 * gain).toFloat()
        }
    }

    private fun cowbell(rate: Int): FloatArray {
        val n = (rate * 0.4).toInt()
        val bp = Biquad.bandPass(2640.0, 2.5, rate)
        var p1 = 0.0
        var p2 = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            p1 = (p1 + 540.0 / rate) % 1.0
            p2 = (p2 + 800.0 / rate) % 1.0
            val sq = (if (p1 < 0.5) 1.0 else -1.0) + (if (p2 < 0.5) 1.0 else -1.0)
            val env = 0.6 * exp(-t / 0.012) + 0.4 * exp(-t / 0.11)
            (bp.process(sq) * env * 1.4).toFloat()
        }
    }

    private fun rim(rate: Int): FloatArray {
        val n = (rate * 0.06).toInt()
        val bp = Biquad.bandPass(1700.0, 3.0, rate)
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            (bp.process(if (i < 3) 1.0 else 0.0) * 6.0 * exp(-t / 0.012) + sin(2 * PI * 480 * t) * exp(-t / 0.008) * 0.3).toFloat()
        }
    }

    private fun tom(rate: Int): FloatArray {
        val n = (rate * 0.45).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            phase += 2 * PI * (110.0 + 60.0 * exp(-t / 0.05)) / rate
            (sin(phase) * exp(-t / 0.2) * 0.8).toFloat()
        }
    }
}
