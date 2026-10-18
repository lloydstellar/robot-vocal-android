package nl.lloydstellar.robotvocal.text

/** The phonemes of one syllable; [nucleus] is the index of its vowel. */
data class SyllablePhones(val phones: List<Phone>, val nucleus: Int) {
    val onset: List<Phone> get() = phones.subList(0, nucleus)
    val vowel: Phone get() = phones[nucleus]
    val coda: List<Phone> get() = phones.subList(nucleus + 1, phones.size)
}

/**
 * Splits a pronunciation into syllables by the maximal onset rule: of the
 * consonants between two vowels, the longest tail that can start an English
 * syllable goes to the second one ("ex-tra" is EH K . S T R AH).
 */
object Syllabifier {

    fun split(phones: List<Phone>): List<SyllablePhones> {
        if (phones.isEmpty()) return emptyList()
        val vowels = phones.indices.filter { phones[it].ph.vowel }
        if (vowels.isEmpty()) {
            /* "hmm", "shh": one syllable around its most sonorous sound */
            val core = phones.indexOfFirst { it.ph.manner in SONORANT }.coerceAtLeast(0)
            return listOf(SyllablePhones(phones, core))
        }
        val starts = IntArray(vowels.size)
        starts[0] = 0
        for (v in 1 until vowels.size) {
            val clusterStart = vowels[v - 1] + 1
            val cluster = phones.subList(clusterStart, vowels[v]).map { it.ph }
            val coda = (0..cluster.size).first { k -> isOnset(cluster.subList(k, cluster.size)) }
            starts[v] = clusterStart + coda
        }
        return vowels.indices.map { v ->
            val end = if (v + 1 < vowels.size) starts[v + 1] else phones.size
            SyllablePhones(phones.subList(starts[v], end), vowels[v] - starts[v])
        }
    }

    private val SONORANT = setOf(Manner.NASAL, Manner.LIQUID, Manner.GLIDE)

    fun isOnset(cluster: List<Ph>): Boolean = when (cluster.size) {
        0 -> true
        1 -> cluster[0] != Ph.NG
        else -> cluster in ONSETS
    }

    private val ONSETS: Set<List<Ph>> = listOf(
        "P R", "P L", "B R", "B L", "T R", "D R", "K R", "K L", "G R", "G L", "F R", "F L", "TH R", "SH R",
        "T W", "D W", "K W", "G W", "S W", "TH W",
        "P Y", "B Y", "K Y", "G Y", "F Y", "V Y", "M Y", "HH Y",
        "S P", "S T", "S K", "S M", "S N", "S L", "S F",
        "S P R", "S P L", "S T R", "S K R", "S K W", "S K L", "S P Y", "S K Y",
    ).map { o -> o.split(' ').map { Ph.of(it)!! } }.toSet()
}
