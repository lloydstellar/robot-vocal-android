package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The sound a vocoder or talkbox speaks through. */
enum class Carrier { SAW, SQUARE, PULSE, SUPERSAW, CHORD, SUB, NOISE }

/**
 * Band-limited oscillators (polyBLEP) for the carriers: one call per sample,
 * at the pitch the song asks for at that moment.
 */
class CarrierOsc(private val kind: Carrier, private val rate: Int, seed: Int, private val pulseWidth: Double = 0.25) {
    private val phases = DoubleArray(7) { it / 7.0 }
    private val noise = Noise(seed)
    private var sinePhase = 0.0

    fun next(hz: Double): Double = when (kind) {
        Carrier.SAW -> saw(0, hz)
        Carrier.SQUARE -> pulse(0, hz, 0.5)
        Carrier.PULSE -> pulse(0, hz, pulseWidth)
        Carrier.SUPERSAW -> {
            var sum = 0.0
            for (k in 0 until 7) sum += saw(k, hz * DETUNE[k])
            sum / 3.0
        }
        /* a minor triad of two saws per note, as in the Brazen Head's choir */
        Carrier.CHORD -> (saw(0, hz) + saw(1, hz * 1.003) + saw(2, hz * SEMI.pow(3)) + saw(3, hz * SEMI.pow(3) * 0.997) +
            saw(4, hz * SEMI.pow(7)) + saw(5, hz * SEMI.pow(7) * 1.002)) / 3.0
        Carrier.SUB -> {
            sinePhase = (sinePhase + hz / 4 / rate) % 1.0
            pulse(0, hz / 2, 0.5) * 0.5 + saw(1, hz / 2) * 0.5 + sin(2 * PI * sinePhase) * 0.9
        }
        Carrier.NOISE -> noise.next()
    }

    private fun saw(k: Int, hz: Double): Double {
        val dt = hz / rate
        phases[k] = (phases[k] + dt) % 1.0
        val t = phases[k]
        return 2 * t - 1 - blep(t, dt)
    }

    private fun pulse(k: Int, hz: Double, width: Double): Double {
        val dt = hz / rate
        phases[k] = (phases[k] + dt) % 1.0
        val t = phases[k]
        var v = if (t < width) 1.0 else -1.0
        v += blep(t, dt)
        v -= blep((t + 1 - width) % 1.0, dt)
        return v
    }

    /** The polyBLEP correction that takes the aliasing out of a jump. */
    private fun blep(t: Double, dt: Double): Double = when {
        t < dt -> {
            val x = t / dt
            x + x - x * x - 1
        }
        t > 1 - dt -> {
            val x = (t - 1) / dt
            x * x + x + x + 1
        }
        else -> 0.0
    }

    companion object {
        private val SEMI = 2.0.pow(1 / 12.0)
        private val DETUNE = doubleArrayOf(1.0, 1.0045, 0.9955, 1.0092, 0.9908, 1.0151, 0.9849)
    }
}
