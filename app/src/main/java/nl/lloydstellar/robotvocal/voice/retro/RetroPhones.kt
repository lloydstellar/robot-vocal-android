package nl.lloydstellar.robotvocal.voice.retro

import nl.lloydstellar.robotvocal.text.Ph

internal enum class Kind {
    VOWEL, DIPHTHONG, NASAL, LIQUID, GLIDE, STOP, AFFRICATE, FRICATIVE, ASPIRATE;

    /** Sounds that carry the voice through the tract; an obstruent after one gets a coda transition. */
    val sonorant get() = this == VOWEL || this == DIPHTHONG || this == NASAL || this == LIQUID || this == GLIDE
    val obstruent get() = this == STOP || this == AFFRICATE || this == FRICATIVE
}

/**
 * A phoneme for the retro engine. [tran] is how long (ms) the formants take
 * to glide in from the sound before. Stops and affricates have a [closure], a
 * release [burst], and then a voice onset time [vot] (stops) or a stretch of
 * frication [fric] (affricates), all in ms; the engine fits these into the
 * time the grid gives the phoneme, in the same proportions.
 */
internal class PhoneSpec(
    val kind: Kind,
    val voiced: Boolean,
    val tran: Int,
    val target: Partial,
    val endTarget: Partial? = null,
    val closure: Int = 0,
    val vot: Int = 0,
    val fric: Int = 0,
    val burst: Partial? = null,
)

/**
 * The phoneme table of whackintalk (MIT, see NOTICE): vowels from Peterson
 * and Barney (1952), consonants from Klatt (1980) and the KLSYN88 listings,
 * tuned by ear by its author. It describes a neutral speaker; the voices
 * scale it.
 */
internal object RetroPhones {
    private const val AV = 60   // vowels, a little under unity to leave room for bursts
    private const val AVC = 57  // sonorant consonants sit a touch lower

    private fun vowel(f1: Int, f2: Int, f3: Int, b1: Int, b2: Int, b3: Int, av: Int = AV) =
        part(av = av, f1 = f1, f2 = f2, f3 = f3, b1 = b1, b2 = b2, b3 = b3)

    private fun v(tran: Int, target: Partial, end: Partial? = null) =
        PhoneSpec(if (end == null) Kind.VOWEL else Kind.DIPHTHONG, true, tran, target, end)

    /** The voice bar heard through the closure of b, d and g. */
    val VOICE_BAR = part(av = 44, f1 = 200, b1 = 120, tiltFc = 700)
    val SILENCE = part(av = 0, af = 0, ah = 0)
    val NEUTRAL = part()

    /**
     * Schwa. CMUdict writes it AH0; it is shorter, quieter and more central
     * than a stressed AH, and treating the two alike sounds like a robot
     * reading (whackintalk's words).
     */
    val SCHWA = v(40, vowel(500, 1500, 2500, 70, 100, 180, av = 55))

    private val TABLE: Map<Ph, PhoneSpec> = mapOf(
        Ph.IY to v(50, vowel(270, 2290, 3010, 45, 200, 400)),
        Ph.IH to v(45, vowel(400, 1990, 2550, 50, 100, 140)),
        Ph.EH to v(45, vowel(530, 1840, 2480, 60, 90, 140)),
        Ph.AE to v(50, vowel(660, 1720, 2410, 70, 100, 180)),
        Ph.AA to v(50, vowel(730, 1090, 2440, 80, 90, 160)),
        Ph.AO to v(50, vowel(570, 840, 2410, 70, 80, 160)),
        Ph.UH to v(45, vowel(440, 1020, 2240, 55, 90, 160)),
        Ph.UW to v(50, vowel(300, 870, 2240, 45, 80, 160)),
        Ph.AH to v(45, vowel(640, 1190, 2390, 80, 90, 160)),
        Ph.ER to v(55, vowel(490, 1350, 1690, 60, 80, 70)),

        Ph.EY to v(50, vowel(500, 1800, 2500, 60, 90, 140), vowel(330, 2200, 2900, 45, 150, 300)),
        Ph.AY to v(50, vowel(730, 1090, 2440, 80, 90, 160), vowel(340, 2100, 2900, 45, 150, 300)),
        Ph.OW to v(50, vowel(500, 900, 2400, 70, 80, 160), vowel(330, 800, 2200, 45, 80, 160)),
        Ph.AW to v(50, vowel(730, 1090, 2440, 80, 90, 160), vowel(320, 900, 2200, 45, 80, 160)),
        Ph.OY to v(50, vowel(570, 840, 2410, 70, 80, 160), vowel(340, 2100, 2900, 45, 150, 300)),

        /* a low murmur plus an antiformant where the mouth is closed: the zero makes a nasal sound nasal */
        Ph.M to PhoneSpec(Kind.NASAL, true, 25, part(av = AVC, f1 = 250, f2 = 1100, f3 = 2150, b1 = 90, b2 = 110, b3 = 200,
            fnp = 270, bnp = 90, fnz = 450, bnz = 120, tiltFc = 2200)),
        Ph.N to PhoneSpec(Kind.NASAL, true, 25, part(av = AVC, f1 = 250, f2 = 1600, f3 = 2700, b1 = 90, b2 = 110, b3 = 200,
            fnp = 270, bnp = 90, fnz = 1450, bnz = 200, tiltFc = 2200)),
        Ph.NG to PhoneSpec(Kind.NASAL, true, 25, part(av = AVC, f1 = 250, f2 = 2000, f3 = 2600, b1 = 90, b2 = 110, b3 = 200,
            fnp = 270, bnp = 90, fnz = 3000, bnz = 250, tiltFc = 2200)),

        /* r is its low, narrow F3 */
        Ph.R to PhoneSpec(Kind.LIQUID, true, 45, part(av = AVC, f1 = 310, f2 = 1060, f3 = 1380, b1 = 70, b2 = 100, b3 = 80)),
        Ph.L to PhoneSpec(Kind.LIQUID, true, 30, part(av = AVC, f1 = 310, f2 = 1050, f3 = 2880, b1 = 60, b2 = 100, b3 = 280)),
        Ph.W to PhoneSpec(Kind.GLIDE, true, 55, part(av = AVC, f1 = 290, f2 = 610, f3 = 2150, b1 = 55, b2 = 80, b3 = 160)),
        Ph.Y to PhoneSpec(Kind.GLIDE, true, 50, part(av = AVC, f1 = 260, f2 = 2070, f3 = 3020, b1 = 45, b2 = 180, b3 = 300)),

        /* sibilants get their spectrum from the parallel branch: only the cavity in front of the tongue counts */
        Ph.S to PhoneSpec(Kind.FRICATIVE, false, 25, part(av = 0, af = 60, f2 = 1400, f3 = 2700, f4 = 4400, f5 = 5500,
            b3 = 200, b4 = 300, b5 = 400, a4 = 44, a5 = 52, a6 = 50)),
        Ph.Z to PhoneSpec(Kind.FRICATIVE, true, 25, part(av = 50, af = 50, f1 = 250, f2 = 1400, f3 = 2700, f4 = 4400, f5 = 5500,
            b1 = 70, b3 = 200, b4 = 300, b5 = 400, a4 = 47, a5 = 55, a6 = 51, tiltFc = 2000)),
        Ph.SH to PhoneSpec(Kind.FRICATIVE, false, 25, part(av = 0, af = 60, f2 = 1700, f3 = 2400, f4 = 3300, f5 = 4200,
            b3 = 250, b4 = 300, b5 = 400, a3 = 58, a4 = 61, a5 = 54, a6 = 32)),
        Ph.ZH to PhoneSpec(Kind.FRICATIVE, true, 25, part(av = 50, af = 50, f1 = 250, f2 = 1700, f3 = 2400, f4 = 3300, f5 = 4200,
            b1 = 70, b3 = 250, b4 = 300, b5 = 400, a3 = 53, a4 = 57, a5 = 49, tiltFc = 2000)),
        /* the weak ones are near flat; the bypass is the only route for noise across the whole band */
        Ph.F to PhoneSpec(Kind.FRICATIVE, false, 25, part(av = 0, af = 58, f2 = 1000, f3 = 2200, ab = 34, a5 = 28, a6 = 30)),
        Ph.V to PhoneSpec(Kind.FRICATIVE, true, 25, part(av = 51, af = 44, f1 = 250, f2 = 1000, f3 = 2200, b1 = 70, ab = 30, a5 = 24, tiltFc = 2000)),
        Ph.TH to PhoneSpec(Kind.FRICATIVE, false, 25, part(av = 0, af = 54, f2 = 1600, f3 = 2600, ab = 36, a5 = 26, a6 = 28)),
        Ph.DH to PhoneSpec(Kind.FRICATIVE, true, 25, part(av = 51, af = 40, f1 = 250, f2 = 1600, f3 = 2600, b1 = 70, ab = 28, tiltFc = 2000)),
        /* h has no place of its own: the engine gives it the shape of the sound after it */
        Ph.HH to PhoneSpec(Kind.ASPIRATE, false, 30, part(av = 0, ah = 49)),

        /* the target of a stop is the posture at its release, the locus the vowels around it glide to */
        Ph.P to PhoneSpec(Kind.STOP, false, 20, part(av = 0, f1 = 250, f2 = 900, f3 = 2100, b1 = 80), closure = 70, vot = 45,
            burst = part(af = 56, ab = 42, a2 = 38, a3 = 34, a4 = 30)),
        Ph.B to PhoneSpec(Kind.STOP, true, 20, part(av = 0, f1 = 220, f2 = 900, f3 = 2100, b1 = 80), closure = 60, vot = 12,
            burst = part(af = 52, ab = 38, a2 = 40, a3 = 36)),
        Ph.T to PhoneSpec(Kind.STOP, false, 20, part(av = 0, f1 = 250, f2 = 1750, f3 = 2600, f4 = 4000, b1 = 80), closure = 65, vot = 50,
            burst = part(af = 60, a4 = 50, a5 = 56, a6 = 54)),
        Ph.D to PhoneSpec(Kind.STOP, true, 20, part(av = 0, f1 = 220, f2 = 1750, f3 = 2600, f4 = 4000, b1 = 80), closure = 55, vot = 15,
            burst = part(af = 56, a4 = 51, a5 = 55, a6 = 51)),
        /* velar bursts sit where the lips help least, so they are written louder */
        Ph.K to PhoneSpec(Kind.STOP, false, 20, part(av = 0, f1 = 250, f2 = 1900, f3 = 2400, b1 = 80), closure = 70, vot = 55,
            burst = part(af = 60, a2 = 54, a3 = 64, a4 = 58)),
        Ph.G to PhoneSpec(Kind.STOP, true, 20, part(av = 0, f1 = 220, f2 = 1900, f3 = 2400, b1 = 80), closure = 55, vot = 18,
            burst = part(af = 58, a2 = 54, a3 = 62, a4 = 56)),

        Ph.CH to PhoneSpec(Kind.AFFRICATE, false, 20, part(av = 0, af = 60, f2 = 1700, f3 = 2400, f4 = 3300, f5 = 4200,
            b3 = 250, b4 = 300, b5 = 400, a3 = 58, a4 = 61, a5 = 54), closure = 65, fric = 65,
            burst = part(af = 60, a3 = 60, a4 = 62, a5 = 54)),
        Ph.JH to PhoneSpec(Kind.AFFRICATE, true, 20, part(av = 50, af = 50, f1 = 250, f2 = 1700, f3 = 2400, f4 = 3300, f5 = 4200,
            b1 = 70, b3 = 250, b4 = 300, b5 = 400, a3 = 53, a4 = 57, a5 = 49, tiltFc = 2000), closure = 50, fric = 50,
            burst = part(af = 58, a3 = 57, a4 = 60, a5 = 52)),
    )

    /** The spec of [ph]; an unstressed AH is a schwa. */
    fun of(ph: Ph, stressed: Boolean): PhoneSpec =
        if (ph == Ph.AH && !stressed) SCHWA else TABLE.getValue(ph)
}
