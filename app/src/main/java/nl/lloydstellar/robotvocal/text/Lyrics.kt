package nl.lloydstellar.robotvocal.text

/**
 * One syllable of a lyric line, with what the flow and the voices need.
 *
 * [stress] is the stress for the rhythm: function words ("the", "to", "my")
 * count as unstressed even where the dictionary marks them, because rappers
 * and listeners treat them that way. The phonemes keep the dictionary stress.
 */
data class Syllable(
    val text: String,
    val phones: List<Phone>,
    val nucleus: Int,
    val stress: Stress,
    val word: Int,
    val wordStart: Boolean,
    val wordEnd: Boolean,
    val pause: Pause?,
    val function: Boolean,
    val guessed: Boolean,
) {
    val vowel: Phone get() = phones[nucleus]
    val onset: List<Phone> get() = phones.subList(0, nucleus)
    val coda: List<Phone> get() = phones.subList(nucleus + 1, phones.size)

    /** A long vowel or a closing consonant: such syllables take more time to say. */
    val heavy: Boolean get() = vowel.ph.long || nucleus < phones.lastIndex
}

data class LyricWord(val shown: String, val syllables: List<Syllable>, val guessed: Boolean)

data class LyricLine(val text: String, val words: List<LyricWord>) {
    val syllables: List<Syllable> get() = words.flatMap { it.syllables }
}

/** From typed lyrics to syllables with phonemes and stress, line by line. */
class Lyrics(private val lexicon: Lexicon) {
    private val normalizer = Normalizer(lexicon)
    private val letterToSound = LetterToSound(lexicon)

    fun parse(text: String): List<LyricLine> = text.lines().filter { it.isNotBlank() }.map(::line)

    fun line(text: String): LyricLine {
        val tokens = normalizer.tokens(text)
        val words = ArrayList<LyricWord>()
        tokens.forEachIndexed { i, token ->
            if (token !is WordToken) return@forEachIndexed
            val pause = (tokens.getOrNull(i + 1) as? PauseToken)?.pause
            words += word(token, words.size, pause)
        }
        /* the last word of a line can carry the rhyme, also "me" in "talk to me" */
        val last = words.lastOrNull()
        if (last != null && last.syllables.size == 1 && last.syllables[0].function) {
            val s = last.syllables[0]
            words[words.lastIndex] = last.copy(syllables = listOf(s.copy(stress = Stress.PRIMARY)))
        }
        return LyricLine(text, words)
    }

    private fun word(token: WordToken, index: Int, pause: Pause?): LyricWord {
        val function = token.spoken in FUNCTION_WORDS
        val parts: List<Pair<String, SyllablePhones>>
        val guessed: Boolean
        if (token.respelling != null) {
            guessed = false
            parts = token.respelling.flatMap { chunk -> respelledChunk(chunk) }
        } else {
            val known = lexicon.lookup(token.spoken)
            guessed = known == null
            val phones = known ?: letterToSound.pronounce(token.spoken)
            val split = Syllabifier.split(phones)
            parts = spell(token.shown, split.size).zip(split)
        }
        val syllables = parts.mapIndexed { s, (text, sp) ->
            val dictionaryStress = sp.vowel.stress
            Syllable(
                text = text,
                phones = sp.phones,
                nucleus = sp.nucleus,
                stress = if (function && parts.size == 1) Stress.NONE else dictionaryStress,
                word = index,
                wordStart = s == 0,
                wordEnd = s == parts.lastIndex,
                pause = if (s == parts.lastIndex) pause else null,
                function = function,
                guessed = guessed,
            )
        }
        return LyricWord(token.shown, syllables, guessed)
    }

    /** One typed syllable of a respelling: its own pronunciation, stressed when typed in capitals. */
    private fun respelledChunk(chunk: String): List<Pair<String, SyllablePhones>> {
        val lower = chunk.lowercase()
        val stressed = chunk.any(Char::isUpperCase) && chunk.none(Char::isLowerCase)
        val phones = lexicon.lookup(lower) ?: letterToSound.pronounce(lower)
        val split = Syllabifier.split(phones).map { sp ->
            val vowel = sp.vowel.copy(stress = if (stressed) Stress.PRIMARY else Stress.NONE)
            sp.copy(phones = sp.phones.toMutableList().also { it[sp.nucleus] = vowel })
        }
        return spell(chunk, split.size).zip(split)
    }

    companion object {
        /** Words that carry no stress in a rap line unless the flow puts them on a beat. */
        val FUNCTION_WORDS = setOf(
            "a", "an", "the", "and", "or", "but", "nor", "so", "yet", "if", "as", "than", "that",
            "of", "to", "in", "on", "at", "by", "for", "from", "with", "up", "out", "off", "into", "onto", "upon",
            "i", "me", "my", "you", "your", "he", "him", "his", "she", "her", "it", "its", "we", "us", "our",
            "they", "them", "their", "this", "these", "those", "who", "whom", "whose", "which", "what",
            "is", "am", "are", "was", "were", "be", "been", "being", "do", "does", "did", "have", "has", "had",
            "can", "could", "will", "would", "shall", "should", "may", "might", "must",
            "i'm", "you're", "we're", "they're", "it's", "i'll", "you'll", "don't", "can't", "won't", "ain't", "gonna", "wanna",
        )
    }
}

/**
 * The letters of a word cut into [n] pieces for the step keys: "robot" into
 * RO and BOT, "every" into EV and ERY. It follows the vowel groups of the
 * spelling, skips a silent final e, and puts one consonant before each
 * following vowel group. Only for display; the sound comes from the phonemes.
 */
fun spell(word: String, n: Int): List<String> {
    if (n <= 1 || word.length < 2) return listOf(word) + List((n - 1).coerceAtLeast(0)) { "" }
    val letters = word.lowercase()
    fun isVowel(i: Int): Boolean {
        val c = letters[i]
        return c in "aeiou" || (c == 'y' && i > 0 && letters[i - 1] !in "aeiou")
    }
    val groups = ArrayList<IntRange>()
    var i = 0
    while (i < letters.length) {
        if (isVowel(i)) {
            var j = i
            while (j + 1 < letters.length && isVowel(j + 1)) j++
            groups += i..j
            i = j + 1
        } else {
            i++
        }
    }
    if (groups.size > n && letters.endsWith("e") && groups.last() == letters.lastIndex..letters.lastIndex) groups.removeAt(groups.lastIndex)
    while (groups.size > n) {
        val pair = (0 until groups.size - 1).minWith(compareBy<Int> { groups[it + 1].first - groups[it].last }.thenBy { it })
        groups[pair] = groups[pair].first..groups[pair + 1].last
        groups.removeAt(pair + 1)
    }
    while (groups.size in 1 until n) {
        val longest = groups.indices.maxBy { groups[it].count() }
        val g = groups[longest]
        if (g.count() < 2) break
        groups[longest] = g.first..g.first
        groups.add(longest + 1, (g.first + 1)..g.last)
    }
    if (groups.size < 2) return listOf(word) + List(n - 1) { "" }
    val cuts = (1 until groups.size).map { k ->
        val between = groups[k].first - groups[k - 1].last - 1
        when {
            between <= 1 -> groups[k].first - between
            DIGRAPHS.any { letters.startsWith(it, groups[k].first - 2) } -> groups[k].first - 2
            else -> groups[k].first - 1
        }
    }
    val pieces = (listOf(0) + cuts + word.length).zipWithNext { a, b -> word.substring(a, b) }
    return pieces + List((n - pieces.size).coerceAtLeast(0)) { "" }
}

private val DIGRAPHS = listOf("ch", "sh", "th", "ph", "wh", "ck", "ng", "qu")
