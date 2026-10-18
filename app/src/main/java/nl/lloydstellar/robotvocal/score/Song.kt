package nl.lloydstellar.robotvocal.score

/**
 * A line of lyrics as the user wrote it, with what they chose for it: another
 * flow than the song's, a fixed number of bars, syllables locked to a slot.
 */
data class SongLine(
    val text: String,
    val flow: String? = null,
    val bars: Int? = null,
    val locks: Map<Int, Int> = emptyMap(),
)

/** Everything that makes a song; the rendered sound is always made from this again. */
data class Song(
    val id: String,
    val title: String,
    val bpm: Double,
    val lines: List<SongLine>,
    val flow: String = FlowPreset.DEFAULT.id,
    val voice: String = "formant/unit1",
    /** MIDI note of the monotone voice; null: the voice's own. */
    val note: Int? = null,
    /** Null: the flow preset's swing and gate. */
    val swing: Double? = null,
    val gate: Double? = null,
    val feel: Double = 0.25,
    val beat: String = "electro1",
    val beatLevel: Double = 0.7,
)
