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
    /** Semitones above or below the song's note, per syllable: a melody. */
    val notes: Map<Int, Int> = emptyMap(),
    /** Syllables whose start repeats 2, 4 or 8 times: "ro-ro-ro-robot". */
    val stutters: Map<Int, Int> = emptyMap(),
)

/** Everything that makes a song; the rendered sound is always made from this again. */
data class Song(
    val id: String,
    val title: String,
    val bpm: Double,
    val lines: List<SongLine>,
    val flow: String = FlowPreset.DEFAULT.id,
    val voice: String = "formant/unit1",
    /** A robot effect from [nl.lloydstellar.robotvocal.dsp.RobotPresets]. */
    val effect: String = "dry",
    /** Movement and room after the robot effect: [nl.lloydstellar.robotvocal.dsp.RobotPresets.MOTION] and SPACE. */
    val motion: String = "none",
    val space: String = "none",
    /** MIDI note of the monotone voice; null: the voice's own. */
    val note: Int? = null,
    /** Null: the flow preset's swing and gate. */
    val swing: Double? = null,
    val gate: Double? = null,
    /**
     * How far the vowel lands after its step, as a share of the consonants
     * before it (the P-centre lead of docs/research/04, at most 60 ms). 0: the
     * vowel exactly on the step, which is where robot voices are heard.
     */
    val feel: Double = 0.0,
    val beat: String = "electro1",
    val beatLevel: Double = 0.7,
)
