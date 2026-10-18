package nl.lloydstellar.robotvocal.voice.retro

/**
 * A voice of the retro engine: the same synthesizer and phoneme table with
 * other numbers. [formantScale] is the length of the vocal tract (above 1 a
 * smaller speaker, which is what makes a child sound like a child, not the
 * pitch); [openQuotient] and [tiltFc] are the quality of the source, from a
 * pressed buzz to a soft breathy one; [breathiness] mixes in breath (dB) and
 * [flutter] a slow wobble of the pitch.
 *
 * A [melody] is sung over the song's notes: semitones, one per syllable,
 * starting again at every line.
 */
internal class RetroVoice(
    val id: String,
    val name: String,
    val description: String,
    /** The MIDI note nearest to the voice's own pitch, used until the song picks another. */
    val note: Int,
    val formantScale: Double,
    val f1Scale: Double = 1.0,
    val openQuotient: Double,
    val tiltFc: Double,
    val breathiness: Double = 0.0,
    val flutter: Double,
    val whisper: Boolean = false,
    val melody: IntArray? = null,
    val vibratoHz: Double = 0.0,
    val vibratoCents: Double = 0.0,
)

/**
 * The voice settings of whackintalk (MIT, see NOTICE), a recreation of the
 * talking computers of the mid-1980s, under names of our own. Tempo and pitch
 * come from the song, so the speaking rates and intonation of the originals
 * are left out; the two voices whose character was a wild pitch range sing a
 * pattern of jumps instead.
 */
internal object RetroVoices {
    val ALL = listOf(
        RetroVoice("desktop", "DESKTOP", "Low, level and a little nasal, like a desktop computer of 1984", 42,
            formantScale = 0.98, openQuotient = 0.46, tiltFc = 3200.0, flutter = 0.22),
        RetroVoice("monotone", "MONOTONE", "A machine: dead flat, no flutter, a pressed buzz", 43,
            formantScale = 1.0, openQuotient = 0.28, tiltFc = 6500.0, flutter = 0.0),
        RetroVoice("gravel", "GRAVEL", "Deep, with some gravel in it", 35,
            formantScale = 0.91, f1Scale = 0.97, openQuotient = 0.4, tiltFc = 2300.0, breathiness = 10.0, flutter = 0.35),
        RetroVoice("kid", "KID", "A short vocal tract, young and bright", 51,
            formantScale = 1.22, f1Scale = 1.04, openQuotient = 0.5, tiltFc = 3900.0, breathiness = 6.0, flutter = 0.3),
        RetroVoice("alto", "ALTO", "A higher register and a breathier source", 54,
            formantScale = 1.15, f1Scale = 1.02, openQuotient = 0.58, tiltFc = 3500.0, breathiness = 20.0, flutter = 0.22),
        RetroVoice("treble", "TREBLE", "Young, high and bright", 60,
            formantScale = 1.3, f1Scale = 1.05, openQuotient = 0.6, tiltFc = 4200.0, breathiness = 22.0, flutter = 0.28),
        RetroVoice("wheeze", "WHEEZE", "Low, wheezy and a little unwell", 37,
            formantScale = 1.06, openQuotient = 0.34, tiltFc = 1700.0, breathiness = 32.0, flutter = 0.65),
        RetroVoice("alien", "ALIEN", "A tiny vocal tract and a hard buzz", 48,
            formantScale = 1.45, f1Scale = 1.1, openQuotient = 0.33, tiltFc = 6800.0, flutter = 0.5),
        RetroVoice("hush", "HUSH", "Breath through the formants, no voice at all", 43,
            formantScale = 1.0, openQuotient = 0.7, tiltFc = 5000.0, flutter = 0.0, whisper = true),
        RetroVoice("underwater", "UNDERWATER", "Muffled, as if under water", 49,
            formantScale = 0.86, openQuotient = 0.66, tiltFc = 850.0, breathiness = 14.0, flutter = 0.7),
        RetroVoice("bleat", "BLEAT", "A sheep: nearly all of it is pitch flutter", 41,
            formantScale = 1.0, openQuotient = 0.42, tiltFc = 2600.0, breathiness = 8.0, flutter = 1.0),
        RetroVoice("unhinged", "UNHINGED", "Jumps all over the place, with a bad wobble", 46,
            formantScale = 1.05, openQuotient = 0.44, tiltFc = 4200.0, breathiness = 10.0, flutter = 0.9,
            melody = intArrayOf(0, 10, -3, 7, -5, 14, 2, -7)),
        RetroVoice("giggle", "GIGGLE", "Very high and far too pleased, jumping up", 55,
            formantScale = 1.28, f1Scale = 1.05, openQuotient = 0.5, tiltFc = 4600.0, breathiness = 12.0, flutter = 0.55,
            melody = intArrayOf(0, 7, 4, 12, 9, 16)),
        RetroVoice("arpeggio", "ARPEGGIO", "Sings a rising arpeggio over your notes", 54,
            formantScale = 1.1, openQuotient = 0.34, tiltFc = 7000.0, flutter = 0.05,
            melody = intArrayOf(0, 4, 7, 12, 7, 4)),
        RetroVoice("fanfare", "FANFARE", "Sings a bright major fanfare, with vibrato", 50,
            formantScale = 1.06, openQuotient = 0.42, tiltFc = 4400.0, breathiness = 4.0, flutter = 0.12,
            melody = intArrayOf(0, 0, 4, 7, 12, 7, 9, 7, 4, 5, 4, 0), vibratoHz = 5.5, vibratoCents = 22.0),
        RetroVoice("mountain", "MOUNTAIN KING", "Sings Grieg's Mountain King figure, dark, with vibrato", 42,
            formantScale = 0.95, openQuotient = 0.4, tiltFc = 2100.0, breathiness = 6.0, flutter = 0.1,
            melody = intArrayOf(0, 2, 3, 5, 7, 3, 7, 5, 1, 2, 3, 5, 3, 2, 3), vibratoHz = 5.2, vibratoCents = 35.0),
        RetroVoice("dirge", "DIRGE", "Sings a funeral march in minor, low and resigned", 39,
            formantScale = 0.93, openQuotient = 0.38, tiltFc = 1900.0, breathiness = 10.0, flutter = 0.15,
            melody = intArrayOf(0, 0, 0, 0, 3, 2, 0, 3, 2, 0, -2, 0), vibratoHz = 4.6, vibratoCents = 28.0),
        RetroVoice("organ", "ORGAN", "Sings a solemn line that steps down and back", 37,
            formantScale = 0.9, openQuotient = 0.36, tiltFc = 2700.0, flutter = 0.04,
            melody = intArrayOf(0, 0, -2, -4, -5, -4, -2, 0, 3, 0)),
    )

    fun byId(id: String): RetroVoice = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
