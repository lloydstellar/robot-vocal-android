package nl.lloydstellar.robotvocal.voice

/** All voices of all engines, and the engine that renders a voice. */
class VoiceRegistry(val engines: List<VoiceEngine>) {
    val all: List<VoiceInfo> = engines.flatMap { it.voices }

    fun engineFor(voiceId: String): VoiceEngine =
        engines.firstOrNull { voiceId.startsWith(it.id + "/") } ?: engines.first()

    fun info(voiceId: String): VoiceInfo? = all.firstOrNull { it.id == voiceId }
}
