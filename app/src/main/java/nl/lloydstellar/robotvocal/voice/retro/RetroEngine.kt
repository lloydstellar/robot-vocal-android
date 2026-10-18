package nl.lloydstellar.robotvocal.voice.retro

import nl.lloydstellar.robotvocal.dsp.Resampler
import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.voice.Rendered
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceInfo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Retro voices: the synthesizer, phoneme table and voices of whackintalk
 * (MIT, see NOTICE), driven by our timing instead of its own.
 *
 * Every phoneme becomes one or more elements with an exact start and end (a
 * stop becomes closure, burst and voice onset), and the frames in between are
 * blended the way whackintalk blends them: the formants glide in over the
 * phoneme's transition time, the loudness within 8 ms. The synthesizer runs
 * at 22.05 kHz, as the original does, with a new frame every millisecond, and
 * the result is resampled to the song's rate.
 */
class RetroEngine : VoiceEngine {
    override val id = "retro"

    override val voices: List<VoiceInfo> =
        RetroVoices.ALL.map { VoiceInfo("$id/${it.id}", it.name, "WHACKINTALK", it.description, it.note) }

    override fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered {
        if (phones.isEmpty()) return Rendered(0, FloatArray(0))
        val voice = RetroVoices.byId(voiceId.substringAfter('/'))
        val margin = sampleRate / 20L
        val origin = phones.first().start - margin
        val length = (phones.maxOf { it.end } + margin - origin).toInt()
        val scale = RATE.toDouble() / sampleRate
        val inner = FloatArray(ceil(length * scale).toInt())
        val track = Track(voice, phones, origin, sampleRate, inner.size.toDouble())
        val synth = RetroSynth(RATE, seed, voice.flutter, voice.vibratoHz, voice.vibratoCents, origin.toDouble() / sampleRate)
        val frame = DoubleArray(K.SIZE)
        var i = 0
        while (i < inner.size) {
            val n = min(BLOCK, inner.size - i)
            track.frameAt((i + n).toDouble(), frame)
            synth.render(frame, inner, i, n)
            i += n
        }
        return Rendered(origin, Resampler.convert(inner, RATE, sampleRate).copyOf(length))
    }

    companion object {
        const val RATE = 22_050
        private const val BLOCK = 22 // a millisecond
    }
}

/** A stretch of the voice with one target: a phoneme or a part of one. Times in samples at [RetroEngine.RATE]. */
private class Element(
    val start: Double,
    var end: Double,
    var tran: Double,
    val target: DoubleArray,
    val endTarget: DoubleArray?,
    val voiced: Boolean,
    val f0: Double,
) {
    /** Where the glide in starts: the exit of the element before. */
    var from: DoubleArray = target
    val exit: DoubleArray get() = endTarget ?: target
}

/** The phonemes as a row of elements, and the frame at any moment. */
private class Track(
    private val voice: RetroVoice,
    phones: List<TimedPhone>,
    origin: Long,
    sampleRate: Int,
    total: Double,
) {
    private val ms = RetroEngine.RATE / 1000.0
    private val elements = ArrayList<Element>()
    private val silence = resolve(RetroPhones.SILENCE)
    private val starts: DoubleArray

    init {
        val scale = RetroEngine.RATE.toDouble() / sampleRate
        val touch = sampleRate / 1000
        val pitch = pitches(phones)
        var cursor = 0.0
        for ((i, p) in phones.withIndex()) {
            val prev = phones.getOrNull(i - 1)?.takeIf { p.start - it.end <= touch }
            val next = phones.getOrNull(i + 1)?.takeIf { it.start - p.end <= touch }
            val start = if (prev != null) cursor else max(cursor, (p.start - origin) * scale)
            val end = max(start, (p.end - origin) * scale)
            if (start > cursor) add(cursor, start, if (elements.isEmpty()) 0.0 else PAUSE_MS * ms, silence, null, false, 0.0)
            phone(p, prev, next, start, end, pitch[i])
            cursor = end
        }
        add(cursor, max(total, cursor), TAIL_MS * ms, silence, null, false, 0.0)

        /* every element glides in from where the one before it ended; the first from its own target, silent */
        elements[0].from = elements[0].target.copyOf().also { f -> K.AMPLITUDES.forEach { f[it] = 0.0 } }
        for (k in 1 until elements.size) elements[k].from = elements[k - 1].exit
        starts = DoubleArray(elements.size) { elements[it].start }
    }

    /** The settings at [t] (samples): gliding in, holding, or moving through a diphthong. */
    fun frameAt(t: Double, out: DoubleArray) {
        val e = elements[index(t)]
        val pos = t - e.start
        when {
            pos < e.tran -> {
                val amp = min(e.tran, AMP_MS * ms)
                K.blend(e.from, e.target, pos / e.tran, min(1.0, pos / amp), out)
            }
            e.endTarget != null -> {
                val u = ((pos - e.tran) / max(1e-6, e.end - e.start - e.tran)).coerceIn(0.0, 1.0)
                K.blend(e.target, e.endTarget, u, u, out)
            }
            else -> e.target.copyInto(out)
        }
        out[K.F0] = if (e.voiced) e.f0 else 0.0
    }

    private fun phone(p: TimedPhone, prev: TimedPhone?, next: TimedPhone?, start: Double, end: Double, f0: Double) {
        val spec = RetroPhones.of(p.ph, p.stressed)
        if (spec.kind.obstruent && prev != null && RetroPhones.of(prev.ph, prev.stressed).kind.sonorant) coda(spec, start)
        when (spec.kind) {
            Kind.STOP -> stop(spec, next, start, end, f0)
            Kind.AFFRICATE -> affricate(spec, start, end, f0)
            Kind.ASPIRATE -> {
                /* h takes the shape of what follows it */
                val shape = resolve(next?.let { RetroPhones.of(it.ph, it.stressed).target } ?: RetroPhones.NEUTRAL)
                val t = resolve(spec.target)
                for (k in intArrayOf(K.F1, K.F2, K.F3, K.F4, K.F5, K.B1, K.B2, K.B3)) t[k] = shape[k]
                add(start, end, spec.tran * ms, t, null, false, f0)
            }
            else -> add(start, end, spec.tran * ms, resolve(spec.target), spec.endTarget?.let { resolve(it) }, spec.voiced, f0)
        }
    }

    /**
     * Before an obstruent, the sonorant before it bends towards the
     * consonant's formants while it still sounds: that bend is the main cue
     * for which consonant it is. The original inserts 32 ms; here they come
     * off the end of the sonorant, so the grid's timing stays exact.
     */
    private fun coda(spec: PhoneSpec, at: Double) {
        val last = elements.last()
        val length = min(CODA_MS * ms, (last.end - last.start) * 0.35)
        if (length < ms) return
        val t = last.exit.copyOf()
        val locus = resolve(spec.target)
        for (k in intArrayOf(K.F1, K.F2, K.F3, K.B1, K.B2, K.B3)) t[k] = locus[k]
        last.end = at - length
        last.tran = min(last.tran, last.end - last.start)
        add(at - length, at, length, t, null, true, last.f0)
    }

    /** A stop: closure (with a voice bar for b, d and g), a burst, then breath gliding into the next sound. */
    private fun stop(spec: PhoneSpec, next: TimedPhone?, start: Double, end: Double, f0: Double) {
        val d = end - start
        val burst = min(BURST_MS * ms, d * 0.2)
        val rest = d - burst
        val vot = min(spec.vot * ms, rest * spec.vot / (spec.closure + spec.vot)).takeIf { it > ms } ?: 0.0
        val closure = rest - vot
        val bar = if (spec.voiced) RetroPhones.VOICE_BAR else RetroPhones.SILENCE
        add(start, start + closure, 12 * ms, resolve(spec.target + bar), null, spec.voiced, f0)
        add(start + closure, start + closure + burst, 2 * ms, resolve(spec.target + spec.burst!!), null, false, f0)
        if (vot > 0) {
            val breath = resolve(next?.let { RetroPhones.of(it.ph, it.stressed).target } ?: spec.target)
            breath[K.AV] = 0.0
            breath[K.AF] = 0.0
            breath[K.AH] = if (spec.voiced) 40.0 else 52.0
            for (k in K.A2..K.AB) breath[k] = 0.0
            add(start + closure + burst, end, vot, breath, null, false, f0)
        }
    }

    /** An affricate: a stop closure released into frication. */
    private fun affricate(spec: PhoneSpec, start: Double, end: Double, f0: Double) {
        val d = end - start
        val burst = min(BURST_MS * ms, d * 0.2)
        val closure = (d - burst) * spec.closure / (spec.closure + spec.fric)
        val bar = if (spec.voiced) RetroPhones.VOICE_BAR else RetroPhones.SILENCE
        add(start, start + closure, 12 * ms, resolve(spec.target + bar), null, spec.voiced, f0)
        add(start + closure, start + closure + burst, 2 * ms, resolve(spec.target + spec.burst!!), null, false, f0)
        add(start + closure + burst, end, 6 * ms, resolve(spec.target), null, spec.voiced, f0)
    }

    private fun add(start: Double, end: Double, tran: Double, target: DoubleArray, endTarget: DoubleArray?, voiced: Boolean, f0: Double) {
        if (end <= start) return
        elements += Element(start, end, min(tran, end - start), target, endTarget, voiced, f0)
    }

    /** A partial frame on the neutral one, with the voice's tract length, source and breath. */
    private fun resolve(p: Partial): DoubleArray {
        val f = K.DEFAULT.copyOf()
        for (k in 0 until K.SIZE) if (!p[k].isNaN()) f[k] = p[k]
        val s = voice.formantScale
        f[K.F1] *= s * voice.f1Scale
        for (k in K.F2..K.F5) f[k] *= s
        f[K.FNP] *= s
        f[K.FNZ] *= s
        /* bandwidths follow their formants, or a small tract rings */
        for (k in K.B1..K.B3) f[k] *= s
        f[K.OQ] = voice.openQuotient
        if (p[K.TILT].isNaN()) f[K.TILT] = voice.tiltFc
        if (f[K.AV] > 0 && voice.breathiness > 0) f[K.AH] = max(f[K.AH], voice.breathiness)
        /* a whisper keeps the glottis open: breath drives the same formants instead of the voice */
        if (voice.whisper && f[K.AV] > 0) {
            f[K.AH] = max(f[K.AH], f[K.AV] - 4)
            f[K.AV] = 0.0
        }
        return f
    }

    /** The pitch of every phoneme: its own, plus the voice's melody counted from the first syllable of the line. */
    private fun pitches(phones: List<TimedPhone>): DoubleArray {
        val melody = voice.melody ?: return DoubleArray(phones.size) { phones[it].pitchHz }
        var first = phones.first().syllable
        return DoubleArray(phones.size) {
            val p = phones[it]
            if (p.lineStart) first = p.syllable
            p.pitchHz * 2.0.pow(melody[Math.floorMod(p.syllable - first, melody.size)] / 12.0)
        }
    }

    private fun index(t: Double): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= t) lo = mid else hi = mid - 1
        }
        return lo
    }

    private companion object {
        const val AMP_MS = 8.0
        const val CODA_MS = 32.0
        const val BURST_MS = 8.0
        const val PAUSE_MS = 20.0
        const val TAIL_MS = 25.0
    }
}
