package nl.lloydstellar.robotvocal.voice.chip

import nl.lloydstellar.robotvocal.dsp.Resampler
import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.voice.Rendered
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceInfo
import kotlin.math.ceil
import kotlin.math.max

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
     * The rests themselves are silent frames.
     */
    private fun spans(phones: List<TimedPhone>, origin: Long, rate: Int, length: Int): List<ChipEncoder.Span> {
        val longest = rate * FRAME_MS / 1000.0
        val narrow = rate * 0.020
        val wide = rate * 0.030
        val keys = ArrayList<ChipEncoder.Span>()
        fun rest(at: Double, hz: Double) {
            keys += ChipEncoder.Span(at, at, at, narrow, false, hz, silent = true)
        }
        var cursor = 0.0
        var voiced: Boolean? = null
        for (p in phones) {
            val start = max((p.start - origin).toDouble(), cursor)
            val end = (p.end - origin).toDouble()
            if (start > cursor + 1 || keys.isEmpty()) {
                rest(cursor, p.pitchHz)
                voiced = null
            }
            val n = ceil((end - start) / longest).toInt().coerceAtLeast(1)
            val piece = (end - start) / n
            val width = (piece * 1.5).coerceIn(narrow, wide)
            if (voiced != p.ph.voiced) keys += ChipEncoder.Span(start, start, start + piece / 2, width, p.ph.voiced, p.pitchHz)
            for (i in 0 until n) {
                val middle = start + piece * (i + 0.5)
                keys += ChipEncoder.Span(middle, middle, middle, width, p.ph.voiced, p.pitchHz)
            }
            voiced = p.ph.voiced
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
    }
}
