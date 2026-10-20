package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.dsp.Biquad
import nl.lloydstellar.robotvocal.text.Ph
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The chain that makes the words of a vocal clear, on every voice before any
 * effect (docs/research/12): the low end cut and the presence range lifted,
 * then a fast compressor, as in a vocal chain into a vocoder and as in SSDRC,
 * the best known way to make speech clear in noise at the same loudness.
 *
 * The lift moves loudness from the low end, which a bass line covers, to the
 * range above 2 kHz, where consonants are heard. The compressor brings what is
 * soft in the voice (an m, an l, a weak vowel, the end of a syllable) up
 * towards the loudest vowels of the take: [RATIO] to 1, at most [MOST_DB]. It
 * only works where a phoneme sounds, so a rest stays a rest: the gate before
 * the compressor. And it only lifts voice: the burst of a p, t or k, an h or an
 * s are noise, and a compressor that lifts them turns them into hard bursts of
 * hiss between the words, as every producer knows from breaths.
 *
 * The lift and the compressor bring s and sh up the most, so a de-esser
 * follows, as in every vocal chain: the hiss of s, sh, z, ch and j comes down
 * above 2.5 kHz until it is 6 dB under the vowel of its syllable, the softer
 * hiss of f, v and th until it is 10 dB under it. The app knows where those
 * sounds are, so nothing else is touched.
 *
 * A few passes over the take and no buffers beside it, so a long song costs
 * no more memory than a short one.
 */
object Clarity {
    /** Applies the chain to [x], whose sample 0 is song frame [origin]. */
    fun apply(x: FloatArray, origin: Long, phones: List<TimedPhone>, rate: Int) {
        if (phones.isEmpty() || x.isEmpty()) return
        val highPass = Biquad.highPass(LOW_CUT_HZ, 0.7071, rate)
        val shelf = Biquad.highShelf(PRESENCE_HZ, PRESENCE_DB, rate)
        for (i in x.indices) x[i] = shelf.process(highPass.process(x[i].toDouble())).toFloat()

        val spans = spans(phones, origin, x.size, rate)
        var top = 0.0
        val probe = Envelope(x, rate)
        val probeGate = Gate(spans, rate)
        for (i in x.indices) {
            val power = probe.next(i)
            if (probeGate.at(i) > 0f) top = max(top, power)
        }
        if (top <= 0) return
        val envelope = Envelope(x, rate)
        val gate = Gate(spans, rate)
        val noise = Noise(phones, origin, rate)
        val glide = exp(-1000.0 / (NOISE_GLIDE_MS * rate))
        /* the glide looks ahead by its own time, so it is half way where the noise starts */
        val ahead = (NOISE_GLIDE_MS * rate / 1000).toInt()
        var share = 0.0
        for (i in x.indices) {
            val power = envelope.next(i)
            /* a sound that is half noise, as v and z, is not lifted either: its hiss would come up with it */
            share = glide * share + (1 - glide) * min(1.0, 2 * noise.at(i + ahead))
            val open = gate.at(i)
            if (open <= 0f) continue
            val db = 10 * log10(power / top + 1e-30)
            var gain = min(db * (1 / RATIO - 1), MOST_DB)
            /* what is near silence (a stop's closure, the very end of a tail) is not lifted to the level of a consonant */
            if (db < QUIET_DB) gain *= ((db - SILENT_DB) / (QUIET_DB - SILENT_DB)).coerceIn(0.0, 1.0)
            x[i] *= exp(gain * open * (1 - share) * LN10 / 20).toFloat()
        }
        deEss(x, origin, phones, rate)
    }

    /** Brings the hiss of every s and its kind down above [SPLIT_HZ] to its level under its vowel, at most [MOST_CUT_DB]. */
    private fun deEss(x: FloatArray, origin: Long, phones: List<TimedPhone>, rate: Int) {
        val vowels = phones.filter { it.vowel }.associateBy { it.syllable }
        val cuts = ArrayList<LongArray>()
        val amounts = ArrayList<Double>()
        for (p in phones) {
            val most = HISSES[p.ph] ?: continue
            val vowel = vowels[p.syllable] ?: continue
            val hiss = peak(x, p.start - origin, p.end - origin, rate, above = SPLIT_HZ)
            val voice = peak(x, vowel.start - origin, vowel.end - origin, rate)
            if (hiss <= 0 || voice <= 0) continue
            val over = 20 * log10(hiss / voice) - most
            if (over <= 0) continue
            cuts += longArrayOf(p.start - origin, p.end - origin)
            amounts += min(over, MOST_CUT_DB)
        }
        if (cuts.isEmpty()) return
        val lowPass = Biquad.lowPass(SPLIT_HZ, 0.7071, rate)
        val ramp = rate * DE_ESS_RAMP_MS / 1000
        var k = 0
        for (i in x.indices) {
            val low = lowPass.process(x[i].toDouble())
            while (k < cuts.size && cuts[k][1] + ramp <= i) k++
            if (k == cuts.size) continue
            val (a, b) = cuts[k]
            val share = min(i - (a - ramp), b + ramp - i).toDouble() / ramp
            if (share <= 0) continue
            val gain = exp(-amounts[k] * min(share, 1.0) * LN10 / 20)
            x[i] = (low + (x[i] - low) * gain).toFloat()
        }
    }

    /**
     * The RMS of the loudest 10 ms between samples [from] and [to] of [x], of
     * the part above [above] Hz if it is given: the hiss of a consonant, not
     * the end of the vowel that may ring on into it.
     */
    private fun peak(x: FloatArray, from: Long, to: Long, rate: Int, above: Double = 0.0): Double {
        val a = from.coerceIn(0, x.size.toLong()).toInt()
        val b = to.coerceIn(a.toLong(), x.size.toLong()).toInt()
        if (b <= a) return 0.0
        val v = DoubleArray(b - a)
        if (above > 0) {
            /* the band under [above], settled over the 5 ms before, taken off */
            val lowPass = Biquad.lowPass(above, 0.7071, rate)
            for (i in max(0, a - rate / 200) until a) lowPass.process(x[i].toDouble())
            for (i in a until b) v[i - a] = x[i] - lowPass.process(x[i].toDouble())
        } else for (i in a until b) v[i - a] = x[i].toDouble()
        val n = min(rate / 100, v.size)
        var sum = 0.0
        for (i in 0 until n) sum += v[i] * v[i]
        var best = sum
        for (i in n until v.size) {
            sum += v[i] * v[i] - v[i - n] * v[i - n]
            best = max(best, sum)
        }
        return sqrt(max(0.0, best) / n)
    }

    /** Where phonemes sound, in samples, a little wider, joined where the rest between them is shorter than a ramp. */
    private fun spans(phones: List<TimedPhone>, origin: Long, n: Int, rate: Int): List<LongArray> {
        val reach = rate * REACH_MS / 1000L
        val ramp = rate * RAMP_MS / 1000L
        val out = ArrayList<LongArray>()
        for (p in phones.sortedBy { it.start }) {
            val a = (p.start - origin - reach).coerceIn(0, n.toLong())
            val b = (p.end - origin + reach).coerceIn(a, n.toLong())
            val last = out.lastOrNull()
            if (last != null && a <= last[1] + ramp) last[1] = max(last[1], b) else out += longArrayOf(a, b)
        }
        return out
    }

    /**
     * How much of the voice is noise, sample after sample, from the [Excitation]
     * of the phoneme there: all of a p, t or k, wherever its burst falls, and
     * none in a rest.
     */
    private class Noise(phones: List<TimedPhone>, origin: Long, rate: Int) {
        private val parts = phones.flatMap { Excitation.parts(it.ph, (it.start - origin).toDouble(), (it.end - origin).toDouble(), rate) }
        private var k = 0

        fun at(i: Int): Double {
            while (k < parts.size && parts[k].end <= i) k++
            val part = parts.getOrNull(k) ?: return 0.0
            return if (i >= part.start) part.noise else 0.0
        }
    }

    /** 1 inside the spans, 0 between them, with a ramp of [RAMP_MS] across every edge; asked for one sample after the other. */
    private class Gate(private val spans: List<LongArray>, rate: Int) {
        private val ramp = rate * RAMP_MS / 1000
        private var k = 0

        fun at(i: Int): Float {
            while (k < spans.size && spans[k][1] + ramp / 2 <= i) k++
            if (k == spans.size) return 0f
            val (a, b) = spans[k]
            val up = (i - (a - ramp / 2)).toFloat() / ramp
            val down = (b + ramp / 2 - i).toFloat() / ramp
            return min(up, down).coerceIn(0f, 1f)
        }
    }

    /**
     * The power of the voice around one sample after the other: the mean square
     * over [WINDOW_MS] centred on it, followed with [ATTACK_MS] attack and
     * [RELEASE_MS] release. It reads ahead of the sample it is asked for and
     * keeps what it read, so the samples behind may change meanwhile.
     */
    private class Envelope(private val x: FloatArray, rate: Int) {
        private val n = rate * WINDOW_MS / 1000
        private val window = DoubleArray(n)
        private val attack = exp(-1000.0 / (ATTACK_MS * rate))
        private val release = exp(-1000.0 / (RELEASE_MS * rate))
        private var sum = 0.0
        private var power = 0.0

        init {
            for (j in 0 until min(n / 2, x.size)) add(j)
        }

        private fun add(j: Int) {
            val v = if (j < x.size) x[j].toDouble() * x[j] else 0.0
            sum += v - window[j % n]
            window[j % n] = v
        }

        fun next(i: Int): Double {
            add(i + n / 2)
            val p = max(0.0, sum) / n
            val k = if (p > power) attack else release
            power = k * power + (1 - k) * p
            return power
        }
    }

    /** How loud each hiss may be next to its vowel, in dB. */
    private val HISSES = mapOf(
        Ph.S to -6.0, Ph.SH to -6.0, Ph.Z to -6.0, Ph.ZH to -6.0, Ph.CH to -6.0, Ph.JH to -6.0,
        Ph.F to -10.0, Ph.TH to -10.0, Ph.V to -10.0, Ph.DH to -10.0,
    )
    private const val MOST_CUT_DB = 12.0
    private const val SPLIT_HZ = 2500.0
    private const val DE_ESS_RAMP_MS = 5
    private const val LOW_CUT_HZ = 90.0
    private const val PRESENCE_HZ = 2000.0
    private const val PRESENCE_DB = 6.0
    private const val RATIO = 2.0
    private const val MOST_DB = 6.0
    private const val QUIET_DB = -40.0
    private const val SILENT_DB = -55.0
    private const val WINDOW_MS = 5
    private const val ATTACK_MS = 1.0
    private const val RELEASE_MS = 15.0
    private const val REACH_MS = 15
    private const val NOISE_GLIDE_MS = 5.0
    private const val RAMP_MS = 10
    private val LN10 = ln(10.0)
}
