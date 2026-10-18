package nl.lloydstellar.robotvocal.voice.retro

/*
 * The control frame of the retro engine: one snapshot of every setting of the
 * synthesizer. Ported from whackintalk (MIT, see NOTICE), where it is the
 * object `Frame`; here a frame is a DoubleArray indexed by the constants of K,
 * so blending and merging are plain loops.
 *
 * Amplitudes are in dB the way Klatt wrote them: 0 or less is off, 60 is unity.
 */
internal object K {
    const val F0 = 0
    const val AV = 1    // voicing
    const val AH = 2    // aspiration, noise into the cascade branch
    const val AF = 3    // frication, noise into the parallel branch
    const val F1 = 4
    const val F2 = 5
    const val F3 = 6
    const val F4 = 7
    const val F5 = 8
    const val B1 = 9
    const val B2 = 10
    const val B3 = 11
    const val B4 = 12
    const val B5 = 13
    const val FNP = 14  // nasal pole and zero; in the same place they cancel out
    const val BNP = 15
    const val FNZ = 16
    const val BNZ = 17
    const val A2 = 18   // parallel formant amplitudes, for fricatives and bursts
    const val A3 = 19
    const val A4 = 20
    const val A5 = 21
    const val A6 = 22
    const val AB = 23   // the flat bypass of the parallel branch, for f and th
    const val OQ = 24   // glottal open quotient: low is pressed and buzzy, high is breathy
    const val TILT = 25 // corner of the source's spectral tilt, Hz
    const val SIZE = 26

    /** Loudness settings, which glide much faster than the formants. */
    val AMPLITUDES = intArrayOf(AV, AH, AF, A2, A3, A4, A5, A6, AB)
    private val IS_AMPLITUDE = BooleanArray(SIZE).also { a -> AMPLITUDES.forEach { a[it] = true } }

    /** A neutral, schwa-like frame; every phoneme target overrides part of it. */
    val DEFAULT = DoubleArray(SIZE).also {
        it[F1] = 500.0; it[F2] = 1500.0; it[F3] = 2500.0; it[F4] = 3300.0; it[F5] = 3750.0
        it[B1] = 60.0; it[B2] = 90.0; it[B3] = 150.0; it[B4] = 200.0; it[B5] = 200.0
        it[FNP] = 270.0; it[BNP] = 100.0; it[FNZ] = 270.0; it[BNZ] = 100.0
        it[OQ] = 0.5; it[TILT] = 5000.0
    }

    /** Moves [out] from [a] towards [b]: formants by [spectral], loudness by [amplitude], both 0..1. */
    fun blend(a: DoubleArray, b: DoubleArray, spectral: Double, amplitude: Double, out: DoubleArray) {
        for (k in 0 until SIZE) {
            val t = if (IS_AMPLITUDE[k]) amplitude else spectral
            out[k] = a[k] + (b[k] - a[k]) * t
        }
    }

    /** Klatt's dB: 0 or less is silence, 60 is unity gain. */
    fun linear(db: Double): Double = if (db <= 0) 0.0 else Math.pow(10.0, (db - 60) / 20)
}

/** Part of a frame: the settings a phoneme cares about, NaN for the rest. */
internal class Partial(val values: DoubleArray) {
    /** This partial with every setting of [over] on top. */
    operator fun plus(over: Partial) = Partial(DoubleArray(K.SIZE) { if (over.values[it].isNaN()) values[it] else over.values[it] })

    operator fun get(k: Int) = values[k]
}

/** A partial frame with named settings, so the phoneme table reads like the original. */
internal fun part(
    av: Int? = null, ah: Int? = null, af: Int? = null,
    f1: Int? = null, f2: Int? = null, f3: Int? = null, f4: Int? = null, f5: Int? = null,
    b1: Int? = null, b2: Int? = null, b3: Int? = null, b4: Int? = null, b5: Int? = null,
    fnp: Int? = null, bnp: Int? = null, fnz: Int? = null, bnz: Int? = null,
    a2: Int? = null, a3: Int? = null, a4: Int? = null, a5: Int? = null, a6: Int? = null, ab: Int? = null,
    tiltFc: Int? = null,
): Partial {
    val v = DoubleArray(K.SIZE) { Double.NaN }
    fun put(k: Int, x: Int?) { if (x != null) v[k] = x.toDouble() }
    put(K.AV, av); put(K.AH, ah); put(K.AF, af)
    put(K.F1, f1); put(K.F2, f2); put(K.F3, f3); put(K.F4, f4); put(K.F5, f5)
    put(K.B1, b1); put(K.B2, b2); put(K.B3, b3); put(K.B4, b4); put(K.B5, b5)
    put(K.FNP, fnp); put(K.BNP, bnp); put(K.FNZ, fnz); put(K.BNZ, bnz)
    put(K.A2, a2); put(K.A3, a3); put(K.A4, a4); put(K.A5, a5); put(K.A6, a6); put(K.AB, ab)
    put(K.TILT, tiltFc)
    return Partial(v)
}
