package nl.lloydstellar.robotvocal.voice.formant

import nl.lloydstellar.robotvocal.text.Ph

/** How a phoneme is made, which decides how its frames are built. */
internal enum class Kind { VOWEL, DIPHTHONG, SONORANT, NASAL, FRICATIVE, ASPIRATE, STOP, AFFRICATE }

/**
 * The target of a phoneme: formants (Hz) for an adult male voice, how much
 * voicing, aspiration and frication it has, and the noise band of its hiss.
 * Vowel formants follow Peterson and Barney (1952) and Hillenbrand et al.
 * (1995); consonant targets are the loci used in formant synthesis since
 * Klatt (1980), rounded.
 */
internal class Target(
    val kind: Kind,
    val f1: Double, val f2: Double, val f3: Double,
    val av: Double = 1.0,
    val ah: Double = 0.0,
    val af: Double = 0.0,
    val fricF: Double = 5000.0,
    val fricB: Double = 1500.0,
    val fricF2: Double = 7500.0,
    val fricMix: Double = 0.0,
    val bypass: Double = 0.0,
    /** The end point of a diphthong. */
    val to: Triple<Double, Double, Double>? = null,
    /** For p, t, k, ch: noise after the release before the vowel. */
    val aspirated: Boolean = false,
    val b1: Double = 60.0,
)

internal object FormantTables {
    private fun vowel(f1: Int, f2: Int, f3: Int) = Target(Kind.VOWEL, f1.toDouble(), f2.toDouble(), f3.toDouble())
    private fun diph(f1: Int, f2: Int, f3: Int, t1: Int, t2: Int, t3: Int) =
        Target(Kind.DIPHTHONG, f1.toDouble(), f2.toDouble(), f3.toDouble(), to = Triple(t1.toDouble(), t2.toDouble(), t3.toDouble()))

    val TARGETS: Map<Ph, Target> = mapOf(
        Ph.IY to vowel(270, 2290, 3010), Ph.IH to vowel(390, 1990, 2550), Ph.EH to vowel(530, 1840, 2480),
        Ph.AE to vowel(660, 1720, 2410), Ph.AA to vowel(730, 1090, 2440), Ph.AO to vowel(570, 840, 2410),
        Ph.UH to vowel(440, 1020, 2240), Ph.UW to vowel(300, 870, 2240), Ph.AH to vowel(620, 1200, 2550),
        Ph.ER to vowel(490, 1350, 1690),
        Ph.EY to diph(480, 1950, 2550, 330, 2250, 2900), Ph.AY to diph(720, 1150, 2450, 380, 2050, 2700),
        Ph.AW to diph(720, 1150, 2450, 420, 900, 2300), Ph.OY to diph(560, 860, 2400, 380, 1950, 2600),
        Ph.OW to diph(540, 950, 2400, 380, 800, 2300),

        Ph.W to Target(Kind.SONORANT, 290.0, 610.0, 2150.0, av = 0.8),
        Ph.Y to Target(Kind.SONORANT, 260.0, 2070.0, 3020.0, av = 0.8),
        Ph.L to Target(Kind.SONORANT, 350.0, 1050.0, 2600.0, av = 0.75),
        Ph.R to Target(Kind.SONORANT, 420.0, 1300.0, 1600.0, av = 0.8),

        Ph.M to Target(Kind.NASAL, 280.0, 1000.0, 2200.0, av = 0.55, b1 = 100.0),
        Ph.N to Target(Kind.NASAL, 280.0, 1550.0, 2600.0, av = 0.55, b1 = 100.0),
        Ph.NG to Target(Kind.NASAL, 280.0, 2000.0, 2700.0, av = 0.55, b1 = 100.0),

        Ph.F to Target(Kind.FRICATIVE, 340.0, 1100.0, 2080.0, av = 0.0, af = 0.35, fricF = 6500.0, fricB = 3000.0, bypass = 0.7),
        Ph.TH to Target(Kind.FRICATIVE, 320.0, 1290.0, 2540.0, av = 0.0, af = 0.3, fricF = 6000.0, fricB = 3000.0, bypass = 0.8),
        Ph.S to Target(Kind.FRICATIVE, 320.0, 1390.0, 2530.0, av = 0.0, af = 0.6, fricF = 6200.0, fricB = 1600.0, fricF2 = 8500.0, fricMix = 0.3),
        Ph.SH to Target(Kind.FRICATIVE, 300.0, 1840.0, 2750.0, av = 0.0, af = 0.6, fricF = 2900.0, fricB = 900.0, fricF2 = 4600.0, fricMix = 0.4),
        Ph.V to Target(Kind.FRICATIVE, 220.0, 1100.0, 2080.0, av = 0.45, af = 0.2, fricF = 6500.0, fricB = 3000.0, bypass = 0.7),
        Ph.DH to Target(Kind.FRICATIVE, 270.0, 1290.0, 2540.0, av = 0.45, af = 0.15, fricF = 6000.0, fricB = 3000.0, bypass = 0.8),
        Ph.Z to Target(Kind.FRICATIVE, 240.0, 1390.0, 2530.0, av = 0.4, af = 0.4, fricF = 5800.0, fricB = 1400.0, fricF2 = 8200.0, fricMix = 0.3),
        Ph.ZH to Target(Kind.FRICATIVE, 300.0, 1840.0, 2750.0, av = 0.4, af = 0.4, fricF = 2900.0, fricB = 900.0, fricF2 = 4600.0, fricMix = 0.4),
        Ph.HH to Target(Kind.ASPIRATE, 500.0, 1500.0, 2500.0, av = 0.0, ah = 0.8),

        /* stops: f1 to f3 are the formant loci the neighbouring vowels move towards; the burst is the noise band */
        Ph.P to Target(Kind.STOP, 300.0, 900.0, 2100.0, av = 0.0, af = 0.5, fricF = 1200.0, fricB = 2000.0, bypass = 0.6, aspirated = true),
        Ph.B to Target(Kind.STOP, 250.0, 900.0, 2100.0, av = 0.25, af = 0.4, fricF = 1200.0, fricB = 2000.0, bypass = 0.6),
        Ph.T to Target(Kind.STOP, 300.0, 1750.0, 2700.0, av = 0.0, af = 0.6, fricF = 4500.0, fricB = 2000.0, fricF2 = 6500.0, fricMix = 0.3, aspirated = true),
        Ph.D to Target(Kind.STOP, 250.0, 1750.0, 2700.0, av = 0.25, af = 0.5, fricF = 4500.0, fricB = 2000.0, fricF2 = 6500.0, fricMix = 0.3),
        Ph.K to Target(Kind.STOP, 300.0, 2100.0, 2600.0, av = 0.0, af = 0.6, fricF = 2400.0, fricB = 1000.0, aspirated = true),
        Ph.G to Target(Kind.STOP, 250.0, 2100.0, 2600.0, av = 0.25, af = 0.5, fricF = 2400.0, fricB = 1000.0),
        Ph.CH to Target(Kind.AFFRICATE, 300.0, 1840.0, 2750.0, av = 0.0, af = 0.6, fricF = 2900.0, fricB = 900.0, fricF2 = 4600.0, fricMix = 0.4),
        Ph.JH to Target(Kind.AFFRICATE, 250.0, 1840.0, 2750.0, av = 0.3, af = 0.45, fricF = 2900.0, fricB = 900.0, fricF2 = 4600.0, fricMix = 0.4),
    )

    fun of(ph: Ph): Target = TARGETS.getValue(ph)
}
