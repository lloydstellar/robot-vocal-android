package nl.lloydstellar.robotvocal.voice.chip

import nl.lloydstellar.robotvocal.dsp.Lpc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Turns speech into frames for the chip, the way talking toys were made from
 * recordings: per frame a ten-pole LPC analysis at 8 kHz, every coefficient
 * rounded to the nearest one the chip knows, and the loudness to the energy
 * code that makes the chip about as loud as the speech. Whether a frame is
 * voiced, and its pitch, come from the phonemes, so they are never guessed.
 */
object ChipEncoder {
    /**
     * A frame to encode, from [start] to [end] in output samples, analysed in
     * a window of [width] around [at], with the voicing and pitch it must get;
     * [silent] frames stay silent.
     */
    class Span(
        val start: Double,
        val end: Double,
        val at: Double,
        val width: Double,
        val voiced: Boolean,
        val hz: Double,
        val silent: Boolean = false,
    )

    /**
     * Encodes [spans] of [speech] (at [ChipTables.RATE]; output sample s is
     * speech sample s * [scale]) into frames with [tables]. [level] sets how
     * loud the chip speaks: the loudest frame's RMS becomes this RMS of full
     * scale.
     */
    fun encode(speech: FloatArray, scale: Double, spans: List<Span>, tables: ChipTables, level: Double = LEVEL): List<ChipFrame> {
        val chirp = chirpPower(tables)
        val analyses = spans.map { analyse(speech, (it.at * scale).toInt(), (it.width * scale).toInt(), if (it.voiced) chirp else null) }
        val loudest = analyses.zip(spans).filter { !it.second.silent }.maxOfOrNull { it.first.rms } ?: 0.0
        return spans.mapIndexed { i, s ->
            val a = analyses[i]
            val k = IntArray(10) { j -> nearest(tables.k[j], a.k[j + 1] * 512) }
            val energy = if (s.silent || loudest <= 0 || a.rms < loudest * QUIET) 0 else energy(tables, k, s, a.rms / loudest * level)
            ChipFrame(s.start, s.end, energy, k, s.voiced, s.hz)
        }
    }

    private class Analysis(val k: DoubleArray, val rms: Double)

    /**
     * LPC of a Hamming window of [n] samples around [centre]. A voiced frame is
     * analysed with the spectrum of the chirp divided out ([chirp]), so that
     * the chirp through the frame's filter has the spectrum of the speech
     * again: the chip's source is far from flat, and without this the low
     * first formant of the i in "beat" gets lost. A mild lift of the highs
     * (0.7, where speech for these chips was lifted by about 0.94) keeps the
     * coefficients inside the chip's tables, which were made for lifted
     * speech; measured over ten vowels this keeps F1 within 8 % and F2 within
     * 3 % on average. Noise is flat and only gets the lift. The RMS is of the
     * speech itself, so a word keeps its loudness.
     */
    private fun analyse(x: FloatArray, centre: Int, n: Int, chirp: DoubleArray?): Analysis {
        val buf = DoubleArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            val at = centre - n / 2 + i
            val v = x.getOrElse(at) { 0f }.toDouble()
            sum += v * v
            buf[i] = (v - PRE_EMPHASIS * x.getOrElse(at - 1) { 0f }) * (0.54 - 0.46 * cos(2 * PI * i / (n - 1)))
        }
        val rms = sqrt(sum / n)
        val r = if (chirp == null) {
            DoubleArray(11) { lag ->
                var acc = 0.0
                for (i in 0 until n - lag) acc += buf[i] * buf[i + lag]
                acc
            }
        } else {
            val power = power(buf)
            DoubleArray(11) { lag ->
                var acc = 0.0
                for (b in 0 until BINS) acc += power[b] / chirp[b] * CIRCLE[(b * lag) % (2 * BINS)]
                acc
            }
        }
        return Analysis(widen(Lpc.fromCorrelation(r, 10).k), rms)
    }

    /**
     * The filter of [k] (reflection coefficients 1..10) with every resonance
     * at least as wide as in speech: [HIGH_BANDWIDTH] above [HIGH_FROM], and
     * [LOW_BANDWIDTH] below. Speech analysed with the chirp divided out can
     * get a third or fourth formant only 20 to 60 Hz wide, around 2.5 kHz
     * where the chirp is weakest; through the chip such a resonance rings on
     * between the pulses as a whistle. Measured on TIN VOICE at 134 BPM, this
     * takes the peaks above 1.5 kHz narrower than 70 Hz from 58 to none, and
     * the formants of the vowels stay where they were.
     */
    private fun widen(k: DoubleArray): DoubleArray {
        /* reflection coefficients to the prediction polynomial, and its roots: the poles */
        var a = doubleArrayOf(1.0)
        for (i in 1..10) {
            val n = DoubleArray(i + 1)
            for (j in a.indices) n[j] += a[j]
            for (j in a.indices) n[i - j] += k[i] * a[j]
            a = n
        }
        val re = DoubleArray(10)
        val im = DoubleArray(10)
        roots(a, re, im)
        val rate = ChipTables.RATE.toDouble()
        var changed = false
        for (i in 0 until 10) {
            val radius = hypot(re[i], im[i])
            val hz = abs(atan2(im[i], re[i])) * rate / (2 * PI)
            val floor = exp(-PI * (if (hz > HIGH_FROM) HIGH_BANDWIDTH else LOW_BANDWIDTH) / rate)
            if (radius > floor) {
                re[i] *= floor / radius
                im[i] *= floor / radius
                changed = true
            }
        }
        if (!changed) return k
        /* back: the polynomial from its roots, then step down to reflection coefficients */
        val pr = DoubleArray(11).also { it[0] = 1.0 }
        val pi = DoubleArray(11)
        for (i in 0 until 10) {
            for (j in i + 1 downTo 1) {
                val r0 = pr[j] - (re[i] * pr[j - 1] - im[i] * pi[j - 1])
                val i0 = pi[j] - (re[i] * pi[j - 1] + im[i] * pr[j - 1])
                pr[j] = r0
                pi[j] = i0
            }
        }
        val out = DoubleArray(11)
        var c = pr.copyOf()
        for (m in 10 downTo 1) {
            val km = c[m].coerceIn(-0.999, 0.999)
            out[m] = km
            val next = DoubleArray(m)
            next[0] = 1.0
            for (j in 1 until m) next[j] = (c[j] - km * c[m - j]) / (1 - km * km)
            c = next
        }
        return out
    }

    /** The roots of the monic polynomial [a] (a[0] = 1, degree 10), by the Durand-Kerner iteration. */
    private fun roots(a: DoubleArray, re: DoubleArray, im: DoubleArray) {
        val n = a.size - 1
        for (i in 0 until n) {
            /* start on a circle inside the unit circle, at angles that are not symmetric */
            re[i] = 0.9 * cos(2 * PI * i / n + 0.4)
            im[i] = 0.9 * sin(2 * PI * i / n + 0.4)
        }
        repeat(500) {
            var moved = 0.0
            for (i in 0 until n) {
                /* p(z) by Horner */
                var pr = 1.0
                var pim = 0.0
                for (j in 1..n) {
                    val r0 = pr * re[i] - pim * im[i] + a[j]
                    pim = pr * im[i] + pim * re[i]
                    pr = r0
                }
                /* the product of (z - z_j) over the other roots */
                var qr = 1.0
                var qi = 0.0
                for (j in 0 until n) {
                    if (j == i) continue
                    val dr = re[i] - re[j]
                    val di = im[i] - im[j]
                    val r0 = qr * dr - qi * di
                    qi = qr * di + qi * dr
                    qr = r0
                }
                val d = qr * qr + qi * qi
                if (d < 1e-300) continue
                val sr = (pr * qr + pim * qi) / d
                val si = (pim * qr - pr * qi) / d
                re[i] -= sr
                im[i] -= si
                moved = maxOf(moved, hypot(sr, si))
            }
            if (moved < 1e-12) return
        }
    }

    /** |X|² of [x] at BINS frequencies from 0 to just below half the rate. */
    private fun power(x: DoubleArray): DoubleArray = DoubleArray(BINS) { b ->
        var re = 0.0
        var im = 0.0
        for (i in x.indices) {
            val m = (b * i) % (2 * BINS)
            re += x[i] * CIRCLE[m]
            im -= x[i] * CIRCLE[(m + 3 * BINS / 2) % (2 * BINS)]
        }
        re * re + im * im
    }

    /** The chirp's power spectrum, with a floor 20 dB under its peak so its dips are not divided by almost nothing. */
    private fun chirpPower(tables: ChipTables): DoubleArray {
        val p = power(DoubleArray(tables.chirp.size) { tables.chirp[it].toDouble() })
        val floor = p.max() * 0.01
        return DoubleArray(BINS) { maxOf(p[it], floor) }
    }

    /**
     * The energy code whose chip output comes closest to [target] RMS (full
     * scale 1): the frame's filter is run on its own source, the chirp at the
     * frame's pitch or the noise, to see how loud one step of energy makes it.
     */
    private fun energy(tables: ChipTables, k: IntArray, span: Span, target: Double): Int {
        val unit = unitRms(tables, k, span)
        var best = 0
        var error = Double.MAX_VALUE
        for (e in 1 until tables.energy.size - 1) {
            val value = tables.energy[e]
            if (value <= 0) continue
            val d = abs(ln(value * unit / target))
            if (d < error) {
                error = d
                best = e
            }
        }
        return best
    }

    /** The RMS (full scale 1) of the chip's output for this frame at an energy of 1, from the lattice in floating point. */
    private fun unitRms(tables: ChipTables, k: IntArray, span: Span): Double {
        val kv = DoubleArray(10) { j -> if (!span.voiced && j >= 4) 0.0 else tables.k[j][k[j]] / 512.0 }
        val x = DoubleArray(10)
        val u = DoubleArray(11)
        val period = if (span.voiced) Math.round(ChipTables.RATE / span.hz).toInt().coerceIn(16, 400) else 0
        val n = if (span.voiced) period * 4 else 800
        var noise = 0x1FFF
        var sum = 0.0
        for (i in 0 until n) {
            /* the lattice takes energy x source / 8 */
            val source = if (span.voiced) tables.chirp[minOf(i % period, tables.chirp.size - 1)].toDouble() else {
                val bit = ((noise shr 12) xor (noise shr 3) xor (noise shr 2) xor noise) and 1
                noise = ((noise shl 1) or bit) and 0x1FFF
                if (noise and 1 == 1) -64.0 else 64.0
            }
            u[10] = source / 8
            for (j in 9 downTo 0) u[j] = u[j + 1] - kv[j] * x[j]
            for (j in 9 downTo 1) x[j] = x[j - 1] + kv[j - 1] * u[j - 1]
            x[0] = u[0]
            /* the first period (or 200 samples of noise) only fills the filter */
            if (i >= n / 4) sum += u[0] * u[0]
        }
        return sqrt(sum / (n - n / 4)) / 2048
    }

    private fun nearest(table: IntArray, value: Double): Int {
        var best = 0
        for (i in table.indices) if (abs(table[i] - value) < abs(table[best] - value)) best = i
        return best
    }

    private const val PRE_EMPHASIS = 0.7
    private const val HIGH_FROM = 1800.0
    private const val HIGH_BANDWIDTH = 100.0
    private const val LOW_BANDWIDTH = 40.0
    private const val BINS = 256
    /** cos(pi m / BINS) for m from 0 to 2 BINS; the sine is the same a quarter turn later. */
    private val CIRCLE = DoubleArray(2 * BINS) { cos(PI * it / BINS) }
    private const val NOISE_RMS = 64.0
    /** The loudest frame's RMS, of full scale: the loudest vowels just touch the chip's clipping, as on the toy. */
    private const val LEVEL = 0.24
    /** Frames this far below the loudest are silence. */
    private const val QUIET = 0.01
}
