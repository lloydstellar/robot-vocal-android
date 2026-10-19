package nl.lloydstellar.robotvocal.dsp

/** Linear prediction: the all-pole filter that describes a short stretch of speech. */
object Lpc {
    /** Prediction filter a[0..order] (a[0] = 1), reflection coefficients k[1..order], and the remaining error energy. */
    class Result(val a: DoubleArray, val k: DoubleArray, val error: Double)

    /** Autocorrelation method with the Levinson-Durbin recursion; the result is always a stable filter. */
    fun analyze(frame: DoubleArray, order: Int): Result = fromCorrelation(DoubleArray(order + 1) { lag ->
        var s = 0.0
        for (i in 0 until frame.size - lag) s += frame[i] * frame[i + lag]
        s
    }, order)

    /** The same from an autocorrelation [r] (lags 0 to [order]) worked out elsewhere, for instance from a reshaped spectrum. */
    fun fromCorrelation(r: DoubleArray, order: Int): Result {
        r[0] *= 1.0001 // a touch of white noise keeps the recursion well conditioned
        r[0] += 1e-12
        val a = DoubleArray(order + 1).also { it[0] = 1.0 }
        val k = DoubleArray(order + 1)
        var err = r[0]
        val prev = DoubleArray(order + 1)
        for (i in 1..order) {
            var acc = r[i]
            for (j in 1 until i) acc += a[j] * r[i - j]
            val ki = (-acc / err).coerceIn(-0.9999, 0.9999)
            k[i] = ki
            System.arraycopy(a, 0, prev, 0, order + 1)
            for (j in 1 until i) a[j] = prev[j] + ki * prev[i - j]
            a[i] = ki
            err *= 1 - ki * ki
        }
        return Result(a, k, err)
    }
}
