package nl.lloydstellar.robotvocal.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/** What an effect knows about the song: the rate, the tempo, the pitch at every frame, where the buffer starts. */
class FxContext(
    val rate: Int,
    val bpm: Double,
    /** Song frame of sample 0 of the buffer (negative when the take starts before bar 1). */
    val origin: Long,
    val seed: Int,
    /** Song frames of the last syllable of every line, for echo throws. */
    val lineEnds: List<LongRange> = emptyList(),
    private val pitch: (Long) -> Double,
) {
    fun pitchAt(sample: Int): Double = pitch(origin + sample)

    /** Samples in [beats] beats at the song's tempo. */
    fun beats(beats: Double): Int = (beats * 60.0 / bpm * rate).roundToInt()

    /** Position in bars (0.0 = bar 1 beat 1) of a sample of the buffer. */
    fun bars(sample: Int): Double = (origin + sample) * bpm / (60.0 * rate * 4)
}

/** An effect renders a whole buffer at once, the same way every time. */
fun interface Effect {
    fun process(x: FloatArray, ctx: FxContext): FloatArray
}

/** Ring modulation with a low sine and a lift in the middle: the tin voice of television robots. */
class RingMod(private val hz: Double = 30.0, private val drive: Double = 2.0) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val mid = Biquad.peaking(1100.0, 0.8, 6.0, ctx.rate)
        return FloatArray(x.size) { i ->
            val v = mid.process(x[i].toDouble()) * sin(2 * PI * hz * i / ctx.rate)
            (tanh(v * drive) / tanh(drive)).toFloat()
        }
    }
}

/** A feedback comb: a very short echo that rings at 1 / [delayMs], metallic, plus an optional slapback echo. */
class Comb(private val delayMs: Double = 4.0, private val feedback: Double = 0.6, private val mix: Double = 0.6,
           private val slapSixteenths: Int = 0, private val slapMix: Double = 0.3) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val d = (delayMs * ctx.rate / 1000).roundToInt().coerceAtLeast(1)
        val y = DoubleArray(x.size)
        for (i in x.indices) y[i] = x[i] + if (i >= d) feedback * y[i - d] else 0.0
        val slap = if (slapSixteenths > 0) (slapSixteenths * 60.0 / ctx.bpm / 4 * ctx.rate).roundToInt() else 0
        return FloatArray(x.size) { i ->
            val wet = y[i] * (1 - feedback)
            val echo = if (slap > 0 && i >= slap) x[i - slap] * slapMix else 0.0
            (x[i] * (1 - mix) + wet * mix + echo).toFloat()
        }
    }
}

/** Fewer samples and fewer steps: the grain of early digital audio. */
class Crusher(private val rate: Int = 11_025, private val bits: Int = 8) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val hold = ctx.rate.toDouble() / rate
        val levels = 2.0.pow(bits - 1)
        var held = 0f
        var next = 0.0
        return FloatArray(x.size) { i ->
            if (i >= next) {
                held = x[i]
                next += hold
            }
            ((held * levels).roundToInt() / levels).toFloat()
        }
    }
}

/** A phaser of six all-pass stages whose sweep takes [barsPerCycle] bars. */
class Phaser(private val barsPerCycle: Double = 1.0, private val low: Double = 300.0, private val high: Double = 2600.0,
             private val feedback: Double = 0.55, private val mix: Double = 0.5) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val state = DoubleArray(6)
        var fb = 0.0
        return FloatArray(x.size) { i ->
            val lfo = 0.5 - 0.5 * kotlin.math.cos(2 * PI * ctx.bars(i) / barsPerCycle)
            val f = low * (high / low).pow(lfo)
            val t = kotlin.math.tan(PI * f / ctx.rate)
            val a = (t - 1) / (t + 1)
            var v = x[i] + fb * feedback
            for (s in 0 until 6) {
                val y = a * v + state[s]
                state[s] = v - a * y
                v = y
            }
            fb = v
            (x[i] * (1 - mix) + v * mix).toFloat()
        }
    }
}

/** A short delay swept by a slow wave and fed back: the jet sound of a flanger. */
class Flanger(private val barsPerCycle: Double = 2.0, private val depthMs: Double = 3.0, private val feedback: Double = 0.6) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val y = DoubleArray(x.size)
        return FloatArray(x.size) { i ->
            val lfo = 0.5 - 0.5 * kotlin.math.cos(2 * PI * ctx.bars(i) / barsPerCycle)
            val d = (0.4 + depthMs * lfo) * ctx.rate / 1000
            val j = i - d
            val k = j.toInt()
            val delayed = if (k >= 1) y[k] + (y[(k + 1).coerceAtMost(i)] - y[k]) * (j - k) else 0.0
            y[i] = x[i] + delayed * feedback
            ((x[i] + delayed) * 0.6).toFloat()
        }
    }
}

/** A gate in 16th notes: `x` open, `.` closed, with 3 ms edges; the pattern repeats every bar. */
class Gate(private val pattern: String = "xx.xx.x.xx.xx.x.") : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        var level = 0.0
        val step = 1.0 / (ctx.rate * 0.003)
        return FloatArray(x.size) { i ->
            val sixteenth = Math.floorMod((ctx.bars(i) * 16).toLong(), 16L).toInt()
            val open = pattern[sixteenth % pattern.length] == 'x'
            level = if (open) (level + step).coerceAtMost(1.0) else (level - step).coerceAtLeast(0.0)
            (x[i] * level).toFloat()
        }
    }
}

/** Soft saturation. */
class Drive(private val amount: Double = 3.0) : Effect {
    override fun process(x: FloatArray, ctx: FxContext) = FloatArray(x.size) { (tanh(x[it] * amount) / tanh(amount)).toFloat() }
}

/** A chain of filters set once: high-pass, low-pass and bells. */
class Eq(private val highPass: Double = 0.0, private val lowPass: Double = 0.0, private val bells: List<Triple<Double, Double, Double>> = emptyList()) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val filters = buildList {
            if (highPass > 0) add(Biquad.highPass(highPass, 0.7, ctx.rate))
            if (lowPass > 0) add(Biquad.lowPass(lowPass, 0.7, ctx.rate))
            bells.forEach { (f, q, g) -> add(Biquad.peaking(f, q, g, ctx.rate)) }
        }
        return FloatArray(x.size) { i ->
            var v = x[i].toDouble()
            for (f in filters) v = f.process(v)
            v.toFloat()
        }
    }
}

/** A compressor: levels above [thresholdDb] grow only 1 / [ratio] as fast; keeps robot words even. */
class Compressor(private val thresholdDb: Double = -20.0, private val ratio: Double = 4.0,
                 private val attackMs: Double = 5.0, private val releaseMs: Double = 80.0) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray {
        val attack = exp(-1.0 / (attackMs * ctx.rate / 1000))
        val release = exp(-1.0 / (releaseMs * ctx.rate / 1000))
        var env = 0.0
        return FloatArray(x.size) { i ->
            val p = x[i].toDouble() * x[i]
            env = if (p > env) attack * env + (1 - attack) * p else release * env + (1 - release) * p
            val db = 10 * log10(env + 1e-12)
            val over = db - thresholdDb
            val gainDb = if (over > 0) -over * (1 - 1 / ratio) else 0.0
            (x[i] * 10.0.pow(gainDb / 20)).toFloat()
        }
    }
}

/**
 * Closes [out] where the voice [voice] is silent: an envelope with a fast
 * attack and a [releaseMs] release, so a vocoder or a chip cannot smear one
 * syllable into the rest before the next. Keeps robot rap tight.
 */
fun followVoice(out: FloatArray, voice: FloatArray, rate: Int, releaseMs: Double = 20.0): FloatArray {
    val attack = exp(-1.0 / (0.001 * rate))
    val release = exp(-1.0 / (releaseMs * rate / 1000))
    val hold = (0.004 * rate).toInt()
    var env = 0.0
    var held = 0
    var peak = 1e-9
    for (v in voice) peak = maxOf(peak, kotlin.math.abs(v.toDouble()))
    val open = peak * 0.01
    for (i in out.indices) {
        val level = kotlin.math.abs(voice.getOrElse(i) { 0f }.toDouble())
        if (level > open) held = hold else if (held > 0) held--
        val target = if (held > 0) 1.0 else 0.0
        env = if (target > env) attack * env + (1 - attack) * target else release * env + (1 - release) * target
        out[i] = (out[i] * env).toFloat()
    }
    return out
}

/** Applies effects one after the other. */
class Chain(private val effects: List<Effect>) : Effect {
    override fun process(x: FloatArray, ctx: FxContext): FloatArray = effects.fold(x) { buffer, fx -> fx.process(buffer, ctx) }
}
