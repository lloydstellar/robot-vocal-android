package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A talkbox: the shape of the mouth (an LPC filter measured from the voice
 * every 8 ms) is put on a synthesizer carrier, as when a keyboard's sound runs
 * through a tube into the mouth. After the design of mda TalkBox: 16 ms Hann
 * frames, half overlapping, a filter of about 24 poles at 48 kHz.
 * [hiss] adds the high-passed voice for the consonants, as the DigiTech Talker did.
 * Where the voice is noise (an s, the burst of a t) the mouth gets noise
 * instead of the keyboard, as a talkbox player makes those sounds with the
 * mouth alone.
 */
class Talkbox(
    private val carrier: Carrier = Carrier.SAW,
    private val order: Int = 24,
    private val hiss: Double = 0.0,
    private val octave: Int = 0,
) : Effect {

    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val rate = ctx.rate
        val n = (rate * 0.016).toInt() and 1.inv()
        val hop = n / 2
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val osc = CarrierOsc(carrier, rate, ctx.seed)
        val shift = Math.pow(2.0, octave.toDouble())
        val noise = Noise(ctx.seed xor 0x5EED)
        val glide = exp(-1.0 / (0.003 * rate))
        var share = 0.0
        val car = DoubleArray(x.size + n) {
            if (it < x.size) {
                share = glide * share + (1 - glide) * ctx.noiseAt(it)
                osc.next(ctx.pitchAt(it) * shift) * (1 - share) + noise.next() * share
            } else 0.0
        }
        val out = DoubleArray(x.size + n)
        val frame = DoubleArray(n)
        val y = DoubleArray(n)
        var start = 0
        while (start < x.size) {
            var last = 0.0
            for (i in 0 until n) {
                val v = if (start + i < x.size) x[start + i].toDouble() else 0.0
                frame[i] = (v - 0.95 * last) * window[i] // pre-emphasis lifts the formants above the low end
                last = v
            }
            val lpc = Lpc.analyze(frame, order)
            val gain = sqrt(lpc.error / n)
            for (i in 0 until n) {
                var v = car[start + i] * gain
                for (j in 1..order) if (i - j >= 0) v -= lpc.a[j] * y[i - j]
                y[i] = v
                out[start + i] += v * window[i]
            }
            start += hop
        }
        /* undo the pre-emphasis and add the hiss of the consonants */
        val hp = Biquad.highPass(5000.0, 0.7, rate)
        val result = FloatArray(x.size)
        var de = 0.0
        for (i in x.indices) {
            de = out[i] + 0.95 * de
            result[i] = (de * 1.5 + hp.process(x[i].toDouble()) * hiss).toFloat()
        }
        return result
    }
}
