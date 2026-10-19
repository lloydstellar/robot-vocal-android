package nl.lloydstellar.robotvocal.voice.retro

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.voice.Rendered
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceInfo

/**
 * A voice of the home computer engine. [mouth] and [throat] are the two knobs
 * the software voices of 1982 had besides pitch and speed. Here the mouth
 * moves the first formant, which follows how far the mouth opens, and the
 * throat the second and higher ones, which follow the length of the tract:
 * above 1 a smaller mouth or a shorter throat.
 */
internal class MicroVoice(
    val id: String,
    val name: String,
    val description: String,
    val note: Int,
    val mouth: Double = 1.0,
    val throat: Double = 1.0,
    val bits: Int = 4,
    val stepRate: Double = 7350.0,
) {
    /** The retro engine's settings that the track needs: tract size from the throat, the first formant from the mouth. */
    val track = RetroVoice(id, name, description, note, formantScale = throat, f1Scale = mouth / throat,
        openQuotient = 0.5, tiltFc = 5000.0, flutter = 0.0)
}

/**
 * Home computer voices: the phonemes and timing of the retro engine, spoken
 * by [MicroSynth], the three tones of the software speech of 1982.
 */
class MicroEngine : VoiceEngine {
    override val id = "micro"

    private val all = listOf(
        MicroVoice("home", "HOME MICRO", "Three tones restarted on every pulse, 4-bit: the software voice of the early home computers", 45),
        MicroVoice("stuffed", "STUFFED", "A low mouth and a blocked nose", 41, mouth = 0.84, throat = 0.88),
        MicroVoice("tinybot", "TINY BOT", "A very small robot", 50, mouth = 1.45, throat = 1.4),
        MicroVoice("visitor", "VISITOR", "A long throat and a tiny mouth, from far away", 47, mouth = 1.5, throat = 1.15),
        MicroVoice("crisp", "CRISP MICRO", "The same three tones in 8 bits and twice the steps: cleaner, still a machine", 43, bits = 8, stepRate = 14_700.0),
    )

    override val voices: List<VoiceInfo> = all.map { VoiceInfo("$id/${it.id}", it.name, "8-BIT", it.description, it.note) }

    override fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered {
        val voice = all.firstOrNull { it.id == voiceId.substringAfter('/') } ?: all.first()
        return Track.render(phones, voice.track, sampleRate) { MicroSynth(RetroEngine.RATE, seed, voice.bits, voice.stepRate) }
    }
}
