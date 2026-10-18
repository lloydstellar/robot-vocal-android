package nl.lloydstellar.robotvocal.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A channel vocoder in the manner of the hardware of the late 1970s and of the
 * Brazen Head on lloydstellar.nl: the voice is split into [bands] bands, an
 * envelope follower measures each, and the same bands of the carrier are
 * opened by those measurements. The hiss of s, t and sh has no pitch, so it
 * goes past the vocoder through a high-pass, as on every classic vocoder; without
 * that path the consonants disappear.
 */
class Vocoder(
    private val bands: Int = 20,
    private val low: Double = 130.0,
    private val high: Double = 7400.0,
    private val q: Double = 6.5,
    private val attackMs: Double = 3.0,
    private val releaseMs: Double = 25.0,
    private val carrier: Carrier = Carrier.SAW,
    private val octave: Int = 0,
    private val hiss: Double = 0.5,
    private val dry: Double = 0.0,
    /** Release of the gate that follows the voice; 0 lets the bands ring out freely. */
    private val tightMs: Double = 20.0,
) : Effect {

    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val rate = ctx.rate
        val freqs = DoubleArray(bands) { low * (high / low).pow(it / (bands - 1.0)) }
        val analysis = freqs.map { listOf(Biquad.bandPass(it, q, rate), Biquad.bandPass(it, q, rate)) }
        val synthesis = freqs.map { listOf(Biquad.bandPass(it, q, rate), Biquad.bandPass(it, q, rate)) }
        val env = DoubleArray(bands)
        val attack = exp(-1.0 / (attackMs * rate / 1000))
        val release = exp(-1.0 / (releaseMs * rate / 1000))
        val hissFilter = Biquad.highPass(5200.0, 0.7, rate)
        val osc = CarrierOsc(carrier, rate, ctx.seed)
        val shift = 2.0.pow(octave)
        /* the pairs of narrow filters lose level; this brings a full-band signal back to about its own size */
        val makeUp = 6.0 * sqrt(q) * sqrt(20.0 / bands)
        val out = FloatArray(x.size)
        for (i in x.indices) {
            val m = x[i].toDouble()
            val c = osc.next(ctx.pitchAt(i) * shift)
            var sum = 0.0
            for (b in 0 until bands) {
                val level = abs(analysis[b][1].process(analysis[b][0].process(m)))
                env[b] = if (level > env[b]) attack * env[b] + (1 - attack) * level else release * env[b] + (1 - release) * level
                sum += synthesis[b][1].process(synthesis[b][0].process(c)) * env[b]
            }
            out[i] = (sum * makeUp + hissFilter.process(m) * hiss + m * dry).toFloat()
        }
        return if (tightMs > 0) followVoice(out, x, rate, tightMs) else out
    }
}
