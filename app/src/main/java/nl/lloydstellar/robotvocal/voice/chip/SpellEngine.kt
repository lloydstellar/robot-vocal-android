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

    /** The phonemes in frames of at most 25 ms, a new frame at every phoneme, silence in between. */
    private fun spans(phones: List<TimedPhone>, origin: Long, rate: Int, length: Int): List<ChipEncoder.Span> {
        val longest = rate * FRAME_MS / 1000.0
        val out = ArrayList<ChipEncoder.Span>()
        var cursor = 0.0
        var voiced = false
        for (p in phones) {
            val start = (p.start - origin).toDouble()
            val end = (p.end - origin).toDouble()
            /* a rest takes the voicing of what came before it, so the sound before it dies away instead of stopping */
            if (start > cursor + 1) out += ChipEncoder.Span(cursor, start, voiced, p.pitchHz, silent = true)
            val from = max(start, cursor)
            val n = ceil((end - from) / longest).toInt().coerceAtLeast(1)
            for (i in 0 until n) {
                out += ChipEncoder.Span(from + (end - from) * i / n, from + (end - from) * (i + 1) / n, p.ph.voiced, p.pitchHz)
            }
            voiced = p.ph.voiced
            cursor = end
        }
        if (length > cursor) out += ChipEncoder.Span(cursor, length.toDouble(), voiced, phones.last().pitchHz, silent = true)
        return out
    }

    private companion object {
        const val FRAME_MS = 25
        const val ANALYSIS_HZ = 100.0
    }
}
