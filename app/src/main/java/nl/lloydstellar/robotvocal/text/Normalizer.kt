package nl.lloydstellar.robotvocal.text

/** A piece of a lyric line after normalisation. */
sealed interface Token

/**
 * A word to say. [spoken] is the lower-case form looked up in the dictionary;
 * [shown] is how it appears on screen. [respelling] holds the syllables the
 * user typed in brackets, an upper-case one carrying the stress: [ro-BOT].
 */
data class WordToken(val spoken: String, val shown: String, val respelling: List<String>? = null) : Token

/** Punctuation that asks for a rest: a comma (short) or a full stop (a breath). */
data class PauseToken(val pause: Pause) : Token

enum class Pause { COMMA, STOP }

/**
 * Turns a typed lyric line into words to say: numbers become words ("808" is
 * "eight oh eight", 1984 a year), symbols become words, initialisms are
 * spelled ("BPM" as B P M) and punctuation becomes a pause.
 */
class Normalizer(private val lexicon: Lexicon) {

    fun tokens(line: String): List<Token> {
        val out = ArrayList<Token>()
        for (match in PIECE.findAll(line.replace('\u2019', '\''))) {
            val piece = match.value
            when {
                piece.startsWith("[") -> respelled(piece)?.let { out += it }
                piece[0] in ",;:–—" || piece.trim() == "-" -> pause(out, Pause.COMMA)
                piece[0] in ".!?" -> pause(out, Pause.STOP)
                else -> out += words(piece)
            }
        }
        while (out.lastOrNull() is PauseToken) out.removeAt(out.lastIndex)
        return out
    }

    private fun pause(out: MutableList<Token>, pause: Pause) {
        val last = out.lastOrNull()
        if (last is WordToken) out += PauseToken(pause)
        else if (last is PauseToken && pause == Pause.STOP) out[out.lastIndex] = PauseToken(Pause.STOP)
    }

    private fun respelled(piece: String): WordToken? {
        val syllables = piece.trim('[', ']').split('-', ' ', '.').filter { it.any(Char::isLetter) }
        if (syllables.isEmpty()) return null
        val word = syllables.joinToString("") { it.lowercase() }
        return WordToken(word, syllables.joinToString(""), syllables)
    }

    private fun words(piece: String): List<WordToken> {
        symbolWords(piece)?.let { return it }
        if (piece.any(Char::isDigit)) return numberWords(piece)
        val lower = piece.lowercase()
        if (lower in lexicon && !isInitialism(piece)) return listOf(WordToken(lower, piece))
        if ('-' in piece || '/' in piece) return piece.split('-', '/').filter { it.isNotBlank() }.flatMap(::words)
        if (isInitialism(piece)) return piece.map { WordToken(it.lowercaseChar().toString(), it.toString()) }
        return listOf(WordToken(lower.trim('\''), piece))
    }

    /** All capitals, two to five letters, and no vowel, or not a dictionary word: read letter by letter. */
    private fun isInitialism(piece: String): Boolean {
        if (piece.length !in 2..5 || !piece.all { it in 'A'..'Z' }) return false
        return piece.none { it in "AEIOUY" } || piece.lowercase() !in lexicon
    }

    private fun symbolWords(piece: String): List<WordToken>? {
        val spoken = SYMBOLS[piece] ?: return null
        return spoken.split(' ').map { WordToken(it, piece) }
    }

    private fun numberWords(piece: String): List<WordToken> {
        val words = Numbers.say(piece) ?: return piece.filter(Char::isLetterOrDigit).map { WordToken(it.lowercaseChar().toString(), it.toString()) }
        return words.split(' ').filter { it.isNotEmpty() }.map { WordToken(it, piece) }
    }

    companion object {
        /** Brackets, numbers with their signs, words with inner apostrophes and hyphens, symbols, punctuation. */
        private val PIECE = Regex(
            "\\[[^\\]]*\\]|[$£€]?\\d[\\d,.:]*(?:st|nd|rd|th|s|%)?|[A-Za-z]+(?:'[A-Za-z]+)*(?:-[A-Za-z]+)*|[&+@#%°=]|\\.{3}|[,.;:!?–—]| - "
        )
        private val SYMBOLS = mapOf(
            "&" to "and", "+" to "plus", "@" to "at", "#" to "number", "%" to "percent",
            "°" to "degrees", "=" to "equals",
        )
    }
}

/** Numbers as words, the way they are said in lyrics. */
object Numbers {
    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val ORDINAL_WORDS = mapOf(
        "one" to "first", "two" to "second", "three" to "third", "five" to "fifth", "eight" to "eighth",
        "nine" to "ninth", "twelve" to "twelfth",
    )
    private val SHAPE = Regex("([$£€]?)(\\d[\\d,]*)(?:\\.(\\d+))?(st|nd|rd|th|s|%)?")
    private val TIME = Regex("(\\d{1,2}):(\\d{2})")

    /** The words for a number as typed ("808", "1984", "3.5", "21st", "$5", "12:30", "90s"), or null when it is not one. */
    fun say(text: String): String? {
        TIME.matchEntire(text)?.let { m ->
            val (h, min) = m.destructured
            val minutes = min.toInt()
            return cardinal(h.toLong()) + when {
                minutes == 0 -> " o'clock"
                minutes < 10 -> " oh " + cardinal(minutes.toLong())
                else -> " " + cardinal(minutes.toLong())
            }
        }
        val m = SHAPE.matchEntire(text) ?: return null
        val (currency, digits, decimals, suffix) = m.destructured
        val whole = digits.replace(",", "")
        if (whole.length > 12) return whole.map { ONES[it.digitToInt()] }.joinToString(" ")
        val n = whole.toLong()
        var words = when {
            suffix == "st" || suffix == "nd" || suffix == "rd" || suffix == "th" -> ordinal(n)
            decimals.isNotEmpty() -> cardinal(n) + " point " + decimals.map { ONES[it.digitToInt()] }.joinToString(" ")
            currency.isEmpty() && whole.length == 4 && n in 1100..2099 && digits == whole -> year(n.toInt())
            currency.isEmpty() && whole.length == 3 && whole[1] == '0' && whole[0] != '0' && whole[2] != '0' ->
                "${ONES[whole[0].digitToInt()]} oh ${ONES[whole[2].digitToInt()]}"
            else -> cardinal(n)
        }
        if (suffix == "s") words = plural(words)
        if (suffix == "%") words += " percent"
        when (currency) {
            "$" -> words += if (n == 1L) " dollar" else " dollars"
            "£" -> words += if (n == 1L) " pound" else " pounds"
            "€" -> words += if (n == 1L) " euro" else " euros"
        }
        return words
    }

    fun cardinal(n: Long): String = when {
        n < 20 -> ONES[n.toInt()]
        n < 100 -> TENS[(n / 10).toInt()] + if (n % 10 != 0L) " " + ONES[(n % 10).toInt()] else ""
        n < 1000 -> ONES[(n / 100).toInt()] + " hundred" + if (n % 100 != 0L) " " + cardinal(n % 100) else ""
        n < 1_000_000 -> cardinal(n / 1000) + " thousand" + if (n % 1000 != 0L) " " + cardinal(n % 1000) else ""
        n < 1_000_000_000 -> cardinal(n / 1_000_000) + " million" + if (n % 1_000_000 != 0L) " " + cardinal(n % 1_000_000) else ""
        else -> cardinal(n / 1_000_000_000) + " billion" + if (n % 1_000_000_000 != 0L) " " + cardinal(n % 1_000_000_000) else ""
    }

    /** 1984 "nineteen eighty four", 1905 "nineteen oh five", 2000 "two thousand", 2026 "twenty twenty six". */
    fun year(y: Int): String {
        val hi = y / 100
        val lo = y % 100
        return when {
            y in 2000..2009 -> cardinal(y.toLong())
            lo == 0 -> cardinal(hi.toLong()) + " hundred"
            lo < 10 -> cardinal(hi.toLong()) + " oh " + ONES[lo]
            else -> cardinal(hi.toLong()) + " " + cardinal(lo.toLong())
        }
    }

    fun ordinal(n: Long): String {
        val words = cardinal(n).split(' ').toMutableList()
        val last = words.last()
        words[words.lastIndex] = ORDINAL_WORDS[last] ?: when {
            last.endsWith("y") -> last.dropLast(1) + "ieth"
            else -> last + "th"
        }
        return words.joinToString(" ")
    }

    /** "nineties", "eighties": the decade word for "90s". */
    private fun plural(words: String): String {
        val parts = words.split(' ').toMutableList()
        val last = parts.last()
        parts[parts.lastIndex] = if (last.endsWith("y")) last.dropLast(1) + "ies" else last + "s"
        return parts.joinToString(" ")
    }
}
