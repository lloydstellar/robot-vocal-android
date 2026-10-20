package nl.lloydstellar.robotvocal.voice.chip

import nl.lloydstellar.robotvocal.dsp.Resampler
import nl.lloydstellar.robotvocal.render.Excitation
import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.voice.Rendered
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceInfo
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The talking spelling toy of 1978 as a voice: speech from [source] (in the
 * app Flite's kal, a recorded male voice like the toy's), encoded into frames
 * for the TMS5100 the way its words were, and spoken by [ChipSynth].
 *
 * The frames follow the phonemes instead of a fixed 25 ms grid: a frame
 * starts where every phoneme starts and none is longer than 25 ms, so a vowel
 * starts on its step as with every other voice. Each phoneme says whether its
 * frames are voiced, and the song gives the pitch.
 */
class SpellEngine(private val source: VoiceEngine, private val sourceVoice: String) : VoiceEngine {
    override val id = "chip"

    override val voices = listOf(
        VoiceInfo("$id/speller", "SPELLER", "LPC CHIP", "A talking spelling toy of 1978: ten poles at 8 kHz, the chirp, 8 bits", 43),
    )

    override fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered {
        if (phones.isEmpty()) return Rendered(0, FloatArray(0))
        /* the source speaks on one steady pitch, so the melody does not bend the filters the chip gets */
        val speech = source.render(phones.map { it.copy(pitchHz = ANALYSIS_HZ) }, sourceVoice, sampleRate, seed)
        val low = Resampler.convert(speech.samples, sampleRate, ChipTables.RATE)
        val spans = spans(phones, speech.origin, sampleRate, speech.samples.size)
        val frames = ChipEncoder.encode(low, ChipTables.RATE.toDouble() / sampleRate, spans, ChipTables.TMS5100)
        return Rendered(speech.origin, ChipSynth(ChipTables.TMS5100, seed).render(frames, speech.samples.size, sampleRate))
    }

    /**
     * The chip's frames for [phones]. Every phoneme is cut into pieces of at
     * most 25 ms, each analysed around its middle, and a frame runs from the
     * middle of one piece to the middle of the next: the chip glides from
     * sound to sound across the border between them, as speech does, and a
     * short consonant at a fast tempo keeps a moment of its own. Where the
     * chip does not glide, when the voicing changes or after a rest, a frame
     * starts exactly where the phoneme starts, so that sound comes on time.
     * The rests themselves are silent frames. A voiced stop ends in an
     * unvoiced burst ([Excitation]): voiced, the chirp would ring through the
     * bright spectrum of the burst, a high whoop at the start of the vowel
     * after it.
     *
     * Where two words would run into each other, the voice keeps them apart
     * the way the toy said its words: a silence of [WORD_GAP_MS] comes off
     * the end of the word before (at most 40 % of its last phoneme), and the
     * next word starts on its step from silence, sharp, as the chip starts
     * after a rest. Only after a word with a stressed syllable, since "the"
     * and "to" belong to the word after them, and only where both sides of
     * the border are voiced or both are not: where the voicing changes the
     * chip already starts the new sound at once.
     */
    internal fun spans(phones: List<TimedPhone>, origin: Long, rate: Int, length: Int): List<ChipEncoder.Span> {
        val longest = rate * FRAME_MS / 1000.0
        val narrow = rate * 0.020
        val wide = rate * 0.030
        val gap = rate * WORD_GAP_MS / 1000.0
        val content = phones.filter { it.stressed }.map { it.word }.toSet()
        val keys = ArrayList<ChipEncoder.Span>()
        fun rest(at: Double, hz: Double) {
            keys += ChipEncoder.Span(at, at, at, narrow, false, hz, silent = true)
        }
        var cursor = 0.0
        var voiced: Boolean? = null
        for ((j, p) in phones.withIndex()) {
            val start = max((p.start - origin).toDouble(), cursor)
            var end = (p.end - origin).toDouble()
            val next = phones.getOrNull(j + 1)
            if (next != null && next.word != p.word && p.word in content && next.ph.voiced == Excitation.endsVoiced(p.ph)) {
                val silence = (next.start - p.end).toDouble()
                if (silence < gap) end -= min(gap - silence, (end - start) * 0.4)
            }
            if (start > cursor + 1 || keys.isEmpty()) {
                rest(cursor, p.pitchHz)
                voiced = null
            }
            for (part in Excitation.parts(p.ph, start, end, rate)) {
                val (a, b) = part
                val v = part.voiced
                val n = ceil((b - a) / longest).toInt().coerceAtLeast(1)
                val piece = (b - a) / n
                val width = (piece * 1.5).coerceIn(narrow, wide)
                if (voiced != v) keys += ChipEncoder.Span(a, a, a + piece / 2, width, v, p.pitchHz)
                for (i in 0 until n) {
                    val middle = a + piece * (i + 0.5)
                    keys += ChipEncoder.Span(middle, middle, middle, width, v, p.pitchHz)
                }
                voiced = v
            }
            cursor = end
        }
        rest(cursor, phones.last().pitchHz)
        /* each frame lasts until the next one starts; the last rest until the end */
        return keys.mapIndexedNotNull { i, k ->
            val end = keys.getOrNull(i + 1)?.start ?: max(length.toDouble(), k.start + 1)
            if (end <= k.start) null else ChipEncoder.Span(k.start, end, k.at, k.width, k.voiced, k.hz, k.silent)
        }
    }

    private companion object {
        const val FRAME_MS = 25
        const val ANALYSIS_HZ = 100.0
        const val WORD_GAP_MS = 10
    }
}
