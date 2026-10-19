package nl.lloydstellar.robotvocal.voice.chip

import kotlin.math.roundToInt

/**
 * One stretch of speech for the chip: its loudness and ten reflection
 * coefficients as indices into the chip's tables, voiced (the chirp, at
 * [hz]) or not (noise), from [start] to [end] in output samples.
 */
class ChipFrame(val start: Double, val end: Double, val energy: Int, val k: IntArray, val voiced: Boolean, val hz: Double)

/**
 * The speech chip of the TMS5100 family, sample by sample, after MAME's
 * tms5110.cpp (BSD-3-Clause, see NOTICE): integer arithmetic in the chip's
 * bit widths, a ten-stage lattice, the chirp or a 13-bit noise register as
 * the source, eight interpolation steps per frame with the chip's shifts, and
 * an output of 8 bits that clips the way the chip clips.
 *
 * Two things differ from the chip. A frame's values sound from its start
 * instead of one frame later, so the frames come with the chip's look-ahead
 * built in. And the clock runs at a whole multiple of the note: a period of
 * [ChipTables.RATE] / f0 samples, rounded, with the clock moved to fit, so a
 * note is in tune and every period is the same, as on the chip at its own
 * pitches. 110 Hz is 73 samples at 8030 a second.
 */
class ChipSynth(private val tables: ChipTables, seed: Int) {
    private var energy = 0
    private var previousEnergy = 0
    private val k = IntArray(10)
    private val x = IntArray(10)
    private val u = IntArray(11)
    private var rng = 0x1FFF xor (seed and 0xFFF)
    private var pitchCount = 0
    private var period = 0
    private var clock = ChipTables.RATE.toDouble()

    /**
     * Speaks [frames] into [length] samples at [rate]. Frame j sounds from its
     * own values towards those of frame j + 1, in the chip's eight steps,
     * unless the chip would hold them: when the voicing changes, or after
     * silence. Then frame j + 1 starts at once with its own values, and a
     * voiced one with a new pulse, as the chip zeroes its pitch counter there.
     *
     * Before silence the sound holds too, where the chip would fade it over a
     * whole frame, so the end of a word stays as long as it was planned. In
     * the silence the filter keeps its shape while what still rings in it
     * fades out over [FADE] samples (3 ms); then it is cleared, so the next
     * word starts from nothing. Letting the filter itself go instead moved its
     * resonances while they rang, a short whoop between words.
     */
    fun render(frames: List<ChipFrame>, length: Int, rate: Int): FloatArray {
        val out = FloatArray(length)
        var t = frames.firstOrNull()?.start ?: return out
        var gain = 0
        for ((j, f) in frames.withIndex()) {
            val next = frames.getOrNull(j + 1)
            val previous = frames.getOrNull(j - 1)
            val silent = tables.energy[f.energy] == 0
            val fresh = previous == null || tables.energy[previous.energy] == 0 || previous.voiced != f.voiced
            if (silent) {
                energy = 0
            } else {
                load(f, 0)
                if (fresh && gain < FADE) clear()
                gain = FADE
                if (fresh) pitchCount = Int.MAX_VALUE
            }
            val hold = next == null || next.voiced != f.voiced || tables.energy[next.energy] == 0 || (silent && tables.energy[next.energy] > 0)
            var step = 0
            if (!f.voiced) clock = ChipTables.RATE.toDouble()
            while (t < f.end) {
                val ip = ((t - f.start) / (f.end - f.start) * 8).toInt().coerceIn(0, 7)
                while (!hold && !silent && step < ip) load(next!!, INTERPOLATION[++step])
                if (f.voiced && pitchCount >= period) {
                    /* a new period, on a clock that fits this note in whole samples */
                    pitchCount = 0
                    period = (ChipTables.RATE / f.hz).roundToInt().coerceIn(16, 400)
                    clock = period * f.hz
                }
                val excitation = if (f.voiced) tables.chirp[minOf(pitchCount, tables.chirp.size - 1)] else noise()
                if (pitchCount < Int.MAX_VALUE) pitchCount++
                var value = lattice(excitation)
                if (silent && gain > 0) {
                    gain--
                    if (gain == 0) clear()
                }
                if (gain < FADE) value = value * gain / FADE
                val sample = output(value) / 128.0
                val length = rate / clock
                spread(out, t, length, sample)
                t += length
            }
        }
        return out
    }

    /** Empties the lattice, as after a long silence. */
    private fun clear() {
        x.fill(0)
        u.fill(0)
        previousEnergy = 0
    }

    /**
     * Moves the chip's values towards [target]: by a shift of [shift], 0 for
     * at once. Unvoiced frames have k5 to k10 at zero, as the chip zeroes them.
     */
    private fun load(target: ChipFrame, shift: Int) {
        energy += (tables.energy[target.energy] - energy) shr shift
        for (i in 0 until 10) {
            val value = if (!target.voiced && i >= 4) 0 else tables.k[i][target.k[i]]
            k[i] += (value - k[i]) shr shift
        }
    }

    /** Adds [value] to [out] from [at] for [length] samples, in proportion to how much of each sample it covers. */
    private fun spread(out: FloatArray, at: Double, length: Double, value: Double) {
        var from = at
        val to = at + length
        while (from < to) {
            val i = from.toInt()
            val end = minOf(to, i + 1.0)
            if (i in out.indices) out[i] += (value * (end - from)).toFloat()
            from = end
        }
    }

    /** One pass of the lattice, as the chip computes it, for one sample of excitation. */
    private fun lattice(excitation: Int): Int {
        u[10] = multiply(previousEnergy, excitation shl 6)
        for (i in 9 downTo 0) u[i] = u[i + 1] - multiply(k[i], x[i])
        for (i in 9 downTo 1) x[i] = x[i - 1] + multiply(k[i - 1], u[i - 1])
        x[0] = u[0]
        previousEnergy = energy
        return u[0]
    }

    /** The chip's multiplier: a coefficient of 10 bits times a value of 15 bits, shifted down 9. */
    private fun multiply(a: Int, b: Int): Int = (wrap(a, 10) * wrap(b, 15)) shr 9

    /** [v] as a signed number of [bits] bits, overflowing the way the chip's adders do. */
    private fun wrap(v: Int, bits: Int): Int = (v shl (32 - bits)) shr (32 - bits)

    /** The lattice's 15 bits, clipped to 12, of which the top 8 reach the speaker: -128..127. */
    private fun output(sample: Int): Int = wrap(sample, 15).coerceIn(-2048, 2047) shr 4

    /** The noise source: a 13-bit shift register stepped 20 times a sample, half the chirp's peak either way. */
    private fun noise(): Int {
        repeat(20) {
            val bit = ((rng shr 12) xor (rng shr 3) xor (rng shr 2) xor rng) and 1
            rng = ((rng shl 1) or bit) and 0x1FFF
        }
        return if (rng and 1 == 1) -64 else 64
    }

    private companion object {
        /** The shift of each interpolation step of a frame: step 0 is the frame's own value. */
        val INTERPOLATION = intArrayOf(0, 3, 3, 3, 2, 2, 1, 1)

        /** The fade of what rings on into a rest, in chip samples: 3 ms. */
        const val FADE = 24
    }
}
