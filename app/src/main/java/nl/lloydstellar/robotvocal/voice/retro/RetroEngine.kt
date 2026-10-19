package nl.lloydstellar.robotvocal.voice.retro

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.voice.Rendered
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceInfo

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
        val voice = RetroVoices.byId(voiceId.substringAfter('/'))
        return Track.render(phones, voice, sampleRate) { origin ->
            RetroSynth(RATE, seed, voice.flutter, voice.vibratoHz, voice.vibratoCents, origin)
        }
    }

    companion object {
        const val RATE = 22_050
    }
}
