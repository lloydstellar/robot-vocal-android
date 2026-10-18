package nl.lloydstellar.robotvocal.text

/**
 * The 39 phonemes of CMUdict (ARPAbet), in the order of the codes in the
 * packed lexicon. Every engine of the app gets its phonemes in this set.
 */
enum class Ph(val vowel: Boolean = false, val voiced: Boolean = true, val manner: Manner) {
    AA(true, manner = Manner.VOWEL), AE(true, manner = Manner.VOWEL), AH(true, manner = Manner.VOWEL),
    AO(true, manner = Manner.VOWEL), AW(true, manner = Manner.VOWEL), AY(true, manner = Manner.VOWEL),
    B(manner = Manner.STOP), CH(voiced = false, manner = Manner.AFFRICATE), D(manner = Manner.STOP),
    DH(manner = Manner.FRICATIVE), EH(true, manner = Manner.VOWEL), ER(true, manner = Manner.VOWEL),
    EY(true, manner = Manner.VOWEL), F(voiced = false, manner = Manner.FRICATIVE), G(manner = Manner.STOP),
    HH(voiced = false, manner = Manner.ASPIRATE), IH(true, manner = Manner.VOWEL), IY(true, manner = Manner.VOWEL),
    JH(manner = Manner.AFFRICATE), K(voiced = false, manner = Manner.STOP), L(manner = Manner.LIQUID),
    M(manner = Manner.NASAL), N(manner = Manner.NASAL), NG(manner = Manner.NASAL),
    OW(true, manner = Manner.VOWEL), OY(true, manner = Manner.VOWEL), P(voiced = false, manner = Manner.STOP),
    R(manner = Manner.LIQUID), S(voiced = false, manner = Manner.FRICATIVE), SH(voiced = false, manner = Manner.FRICATIVE),
    T(voiced = false, manner = Manner.STOP), TH(voiced = false, manner = Manner.FRICATIVE), UH(true, manner = Manner.VOWEL),
    UW(true, manner = Manner.VOWEL), V(manner = Manner.FRICATIVE), W(manner = Manner.GLIDE),
    Y(manner = Manner.GLIDE), Z(manner = Manner.FRICATIVE), ZH(manner = Manner.FRICATIVE);

    /** Long vowels and diphthongs; a syllable with one of these, or with a coda, counts as heavy. */
    val long: Boolean get() = this in LONG

    companion object {
        private val LONG = setOf(AA, AO, AW, AY, ER, EY, IY, OW, OY, UW)
        private val BY_NAME = entries.associateBy { it.name }

        fun of(name: String): Ph? = BY_NAME[name]
    }
}

enum class Manner { VOWEL, STOP, AFFRICATE, FRICATIVE, ASPIRATE, NASAL, LIQUID, GLIDE }

/** Stress as CMUdict marks it on vowels; the ordinal is CMUdict's digit (0 none, 1 primary, 2 secondary). */
enum class Stress { NONE, PRIMARY, SECONDARY;

    companion object {
        fun of(digit: Int): Stress = entries.getOrElse(digit) { NONE }
    }
}

/** One phoneme of a pronunciation; [stress] only means something on vowels. */
data class Phone(val ph: Ph, val stress: Stress = Stress.NONE) {
    override fun toString() = if (ph.vowel) "${ph.name}${stress.ordinal}" else ph.name

    companion object {
        /** Parses "AH0", "B", "IY1"; null for anything that is not an ARPAbet phoneme. */
        fun parse(token: String): Phone? {
            val digit = token.lastOrNull()?.takeIf { it.isDigit() }?.digitToInt()
            val ph = Ph.of(if (digit != null) token.dropLast(1) else token) ?: return null
            return Phone(ph, if (ph.vowel) Stress.of(digit ?: 0) else Stress.NONE)
        }

        /** Parses a pronunciation such as "R OW1 B AA2 T". */
        fun parseAll(text: String): List<Phone>? =
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { parse(it) ?: return null }
    }
}

fun List<Phone>.arpabet(): String = joinToString(" ")
