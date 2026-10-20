package nl.lloydstellar.robotvocal.voice.formant

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Manner
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.voice.Rendered
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The character of a voice of the formant engine: its source, the size of the
 * "mouth" (formant scale), how fast it moves between sounds, and how much of a
 * chip it is. [stepMs] above zero holds every setting for that long, the way
 * allophone chips of the 1970s jumped from sound to sound; [crushRate] and
 * [crushBits] give the grain of an early sampler.
 */
data class FormantCharacter(
    val id: String,
    val name: String,
    val description: String,
    val source: Source,
    val formantScale: Double = 1.0,
    val transitionMs: Double = 35.0,
    val stepMs: Double = 0.0,
    val crushRate: Int = 0,
    val crushBits: Int = 0,
    val breath: Double = 0.03,
    /** MIDI note of the monotone voice. */
    val note: Int = 48,
)

/** Our own robot voices: a Klatt formant synthesizer driven phoneme by phoneme, so every sound starts exactly where the grid says. */
class FormantEngine : VoiceEngine {
    override val id = "formant"

    val characters = listOf(
        FormantCharacter("unit1", "UNIT ONE", "A clean synthesizer voice, steady and clear", Source.GLOTTAL),
        FormantCharacter("allophone", "ALLOPHONE", "Jumps from sound to sound like a 1970s speech chip", Source.SAW,
            transitionMs = 0.0, stepMs = 25.0, crushRate = 10_000, crushBits = 8, breath = 0.0, note = 47),
        FormantCharacter("robovox", "ROBOVOX", "A sung monotone with long vowels", Source.GLOTTAL,
            formantScale = 0.95, transitionMs = 50.0, note = 45),
        FormantCharacter("tinman", "TIN MAN", "A small, bright robot", Source.PULSE, formantScale = 1.18, transitionMs = 25.0, note = 57),
        FormantCharacter("deepunit", "DEEP UNIT", "A big, low machine", Source.SAW, formantScale = 0.82, transitionMs = 40.0, note = 40),
    )

    override val voices: List<VoiceInfo> = characters.map { VoiceInfo("$id/${it.id}", it.name, "FORMANT", it.description, it.note) }

    fun character(voiceId: String): FormantCharacter =
        characters.firstOrNull { "$id/${it.id}" == voiceId } ?: characters.first()

    override fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered {
        if (phones.isEmpty()) return Rendered(0, FloatArray(0))
        val c = character(voiceId)
        val margin = sampleRate / 20L
        val origin = phones.first().start - margin
        val length = (phones.last().end + margin - origin).toInt()
        val frames = (length + FormantSynth.FRAME - 1) / FormantSynth.FRAME
        val out = FloatArray(frames * FormantSynth.FRAME)
        val synth = FormantSynth(sampleRate, c.source, seed)
        val track = Track(phones, c, sampleRate)
        val step = if (c.stepMs > 0) max(1, (c.stepMs * sampleRate / 1000.0 / FormantSynth.FRAME).roundToInt()) else 1
        var from = Frame()
        var to = Frame()
        track.at(origin, from)
        for (k in 0 until frames) {
            val t = origin + k.toLong() * FormantSynth.FRAME
            if (step == 1) {
                track.at(t + FormantSynth.FRAME, to)
            } else if (k % step == 0) {
                track.at(t, from)
                to.copyFrom(from)
            }
            synth.render(from, to, out, k * FormantSynth.FRAME, FormantSynth.FRAME)
            if (step == 1) from = to.also { to = from }
        }
        if (c.crushRate > 0 || c.crushBits > 0) crush(out, sampleRate, c.crushRate, c.crushBits)
        return Rendered(origin, out)
    }

    /** Sample-and-hold to [rate] and rounding to [bits]: the grain of an early digital voice. */
    private fun crush(out: FloatArray, sampleRate: Int, rate: Int, bits: Int) {
        var peak = 1e-9f
        for (v in out) peak = max(peak, kotlin.math.abs(v))
        val levels = if (bits > 0) 2.0.pow(bits - 1) else 0.0
        val hold = if (rate > 0) sampleRate.toDouble() / rate else 1.0
        var held = 0f
        var next = 0.0
        for (i in out.indices) {
            if (i >= next) {
                held = out[i]
                next += hold
            }
            var v = held / peak
            if (levels > 0) v = ((v * levels).roundToInt() / levels).toFloat()
            out[i] = v * peak
        }
    }
}

/** The synthesizer settings at any moment of a list of timed phonemes. */
private class Track(private val phones: List<TimedPhone>, private val c: FormantCharacter, private val rate: Int) {
    private val starts = LongArray(phones.size) { phones[it].start }
    private val transition = c.transitionMs * rate / 1000.0
    private val scale = c.formantScale

    fun at(t: Long, f: Frame) {
        val i = index(t)
        if (i < 0) {
            silent(t, f)
            return
        }
        val p = phones[i]
        val target = FormantTables.of(p.ph)
        val prev = phones.getOrNull(i - 1)?.takeIf { it.end >= p.start - rate / 100 }
        val next = phones.getOrNull(i + 1)?.takeIf { it.start <= p.end + rate / 100 }
        val dur = (p.end - p.start).toDouble().coerceAtLeast(1.0)
        val x = (t - p.start) / dur

        formants(p, target, prev, next, t, f)
        f.f0 = p.pitchHz * if (c.source == Source.GLOTTAL && !p.stressed) 0.98 else 1.0
        f.av = 0.0; f.ah = 0.0; f.af = 0.0; f.nasal = 0.0; f.bypass = 0.0
        f.fricF = target.fricF; f.fricB = target.fricB; f.fricF2 = target.fricF2; f.fricMix = target.fricMix
        val gain = if (p.stressed) 1.0 else 0.8
        when (target.kind) {
            Kind.VOWEL, Kind.DIPHTHONG -> {
                f.av = gain
                f.ah = c.breath
            }
            Kind.SONORANT -> f.av = target.av * gain
            Kind.NASAL -> {
                f.av = target.av * gain
                f.nasal = 1.0
            }
            Kind.FRICATIVE -> {
                f.av = target.av * gain
                f.af = target.af
                f.bypass = target.bypass
            }
            Kind.ASPIRATE -> f.ah = target.ah
            Kind.STOP -> stop(target, next, x, dur, f)
            Kind.AFFRICATE -> {
                if (x < 0.4) {
                    f.av = target.av * 0.5
                } else {
                    f.av = target.av * gain
                    f.af = target.af
                }
            }
        }
        /* soft edges where the voice starts or stops, so nothing clicks */
        val edge = rate * 0.006
        if (prev == null) f.scale(min(1.0, (t - p.start) / edge))
        if (next == null) f.scale(min(1.0, (p.end - t) / edge))
    }

    /**
     * A stop: closure (a low voice bar for b, d, g), a short burst of noise,
     * then for p, t and k breath in the shape of what follows: a vowel, or the
     * r, l, w or y of "cry", "play" and "twin", which come out without voice
     * after them.
     */
    private fun stop(target: Target, next: TimedPhone?, x: Double, dur: Double, f: Frame) {
        val burst = max(0.12, rate * 0.010 / dur)
        val breath = next?.takeIf { target.aspirated && (it.vowel || it.ph.manner == Manner.LIQUID || it.ph.manner == Manner.GLIDE) }
        val closure = if (breath != null) 0.55 else 0.75
        when {
            x < closure -> {
                f.av = target.av * 0.5
                f.f1 = 200.0 * scale
            }
            x < closure + burst -> {
                f.af = target.af
                f.bypass = target.bypass
            }
            breath != null -> {
                f.ah = 0.55
                val v = FormantTables.of(breath.ph)
                f.f1 = v.f1 * scale; f.f2 = v.f2 * scale; f.f3 = v.f3 * scale
            }
            else -> f.av = target.av
        }
    }

    private fun formants(p: TimedPhone, target: Target, prev: TimedPhone?, next: TimedPhone?, t: Long, f: Frame) {
        var f1 = target.f1; var f2 = target.f2; var f3 = target.f3
        if (target.kind == Kind.ASPIRATE) {
            /* h takes the shape of the vowel that follows */
            val v = next?.let { FormantTables.of(it.ph) } ?: target
            f1 = v.f1; f2 = v.f2; f3 = v.f3
        }
        target.to?.let { (t1, t2, t3) ->
            val glide = ((t - p.start).toDouble() / (p.end - p.start).coerceAtLeast(1)).let { ((it - 0.25) / 0.65).coerceIn(0.0, 1.0) }
            f1 += (t1 - f1) * glide; f2 += (t2 - f2) * glide; f3 += (t3 - f3) * glide
        }
        if (transition > 0) {
            val sinceStart = (t - p.start).toDouble()
            val untilEnd = (p.end - t).toDouble()
            val tr = min(transition, (p.end - p.start) * 0.45)
            if (prev != null && sinceStart < tr) {
                val (b1, b2, b3) = boundary(prev, p)
                val w = 0.5 + 0.5 * sinceStart / tr
                f1 = b1 + (f1 - b1) * w; f2 = b2 + (f2 - b2) * w; f3 = b3 + (f3 - b3) * w
            } else if (next != null && untilEnd < tr) {
                val (b1, b2, b3) = boundary(p, next)
                val w = 0.5 + 0.5 * untilEnd / tr
                f1 = b1 + (f1 - b1) * w; f2 = b2 + (f2 - b2) * w; f3 = b3 + (f3 - b3) * w
            }
        }
        f.f1 = f1 * scale; f.f2 = f2 * scale; f.f3 = f3 * scale; f.f4 = 3400.0 * scale
        f.b1 = target.b1; f.b2 = 90.0; f.b3 = 150.0; f.b4 = 250.0
    }

    /** Where the formants meet between two sounds; consonants pull harder than vowels. */
    private fun boundary(a: TimedPhone, b: TimedPhone): Triple<Double, Double, Double> {
        val ta = end(FormantTables.of(a.ph))
        val tb = FormantTables.of(b.ph)
        val w = when {
            a.vowel == b.vowel -> 0.5
            a.vowel -> 0.65 // towards the consonant b
            else -> 0.35
        }
        return Triple(ta.first + (tb.f1 - ta.first) * w, ta.second + (tb.f2 - ta.second) * w, ta.third + (tb.f3 - ta.third) * w)
    }

    private fun end(t: Target) = t.to ?: Triple(t.f1, t.f2, t.f3)

    private fun silent(t: Long, f: Frame) {
        f.av = 0.0; f.ah = 0.0; f.af = 0.0; f.nasal = 0.0
        val near = phones.getOrNull(index(t).let { if (it < 0) nearest(t) else it }) ?: return
        val target = FormantTables.of(near.ph)
        f.f0 = near.pitchHz
        f.f1 = target.f1 * scale; f.f2 = target.f2 * scale; f.f3 = target.f3 * scale
    }

    /** The phoneme sounding at [t], or -1 in a gap. */
    private fun index(t: Long): Int {
        var lo = 0
        var hi = starts.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= t) {
                found = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        return if (found >= 0 && t < phones[found].end) found else -1
    }

    private fun nearest(t: Long): Int {
        var best = 0
        var dist = Long.MAX_VALUE
        for (i in phones.indices) {
            val d = min(kotlin.math.abs(phones[i].start - t), kotlin.math.abs(phones[i].end - t))
            if (d < dist) {
                dist = d
                best = i
            }
        }
        return best
    }
}

private fun Frame.scale(v: Double) {
    av *= v; ah *= v; af *= v
}
