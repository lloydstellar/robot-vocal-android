package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.render.TimedPhone

/** A voice the user can pick: an engine and one of its settings. */
data class VoiceInfo(val id: String, val name: String, val engine: String, val description: String, val note: Int)

/** Rendered speech: [samples] start at song frame [origin]. */
class Rendered(val origin: Long, val samples: FloatArray)

/**
 * A speech engine. It gets phonemes with exact start and end frames and must
 * put every sound where it is told, so the vowels land on the beat.
 */
interface VoiceEngine {
    val id: String
    val voices: List<VoiceInfo>

    fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered
}
