package nl.lloydstellar.robotvocal.text

/**
 * Pronunciations for words the dictionary does not have, in this order: a
 * dictionary word plus a common ending ("vocoders" from "vocoder" + S), two
 * dictionary words joined ("dancefloor"), and finally the NRL rules of 1976
 * with a stress guess. Every result is marked as guessed, so the editor can
 * show it.
 */
class LetterToSound(private val lexicon: Lexicon) {

    /** The pronunciation of [word] (lower case, letters and apostrophes). */
    fun pronounce(word: String): List<Phone> =
        withEnding(word) ?: compound(word) ?: byRule(word)

    private fun withEnding(word: String): List<Phone>? {
        for ((ending, add) in ENDINGS) {
            if (word.length <= ending.length + 2 || !word.endsWith(ending)) continue
            val stem = word.dropLast(ending.length)
            val base = lexicon.lookup(stem) ?: lexicon.lookup(stem + "e") ?: continue
            return base + add(base.last().ph)
        }
        return null
    }

    /** Two dictionary words; the first keeps its stress, the second's primary stress becomes secondary. */
    private fun compound(word: String): List<Phone>? {
        var best: List<Phone>? = null
        var bestShorter = 0
        for (split in 3..word.length - 3) {
            val first = lexicon.lookup(word.substring(0, split)) ?: continue
            val second = lexicon.lookup(word.substring(split)) ?: continue
            val shorter = minOf(split, word.length - split)
            if (shorter > bestShorter) {
                bestShorter = shorter
                best = first + second.map { if (it.stress == Stress.PRIMARY) it.copy(stress = Stress.SECONDARY) else it }
            }
        }
        return best
    }

    private fun byRule(word: String): List<Phone> {
        val phones = rulePhonemes(word)
        return guessStress(phones, word)
    }

    companion object {
        private class Rule(val left: String, val match: String, val right: String, val out: List<String>)

        private val RULES: Map<Char, List<Rule>> = NRL_RULES.map(::parseRule).groupBy { it.match.first() }

        private fun parseRule(line: String): Rule {
            val open = line.indexOf('[')
            val close = line.indexOf(']', open)
            val eq = line.indexOf('=', close)
            require(open >= 0 && close > open + 1 && eq > close) { "Bad NRL rule: $line" }
            val out = line.substring(eq + 1).trim().split(' ').filter { it.isNotEmpty() }
            out.forEach { require(it == "AX" || Ph.of(it) != null) { "Unknown phoneme $it in $line" } }
            return Rule(line.substring(0, open), line.substring(open + 1, close), line.substring(close + 1, eq), out)
        }

        private const val VOWELS = "AEIOUY"
        private const val CONSONANTS = "BCDFGHJKLMNPQRSTVWXZ"
        private const val VOICED = "BDVGJLMNRWZ"
        private const val FRONT = "EIY"
        private val SIBILANTS = listOf("CH", "SH", "S", "C", "G", "Z", "X", "J")
        private val PALATALISING = listOf("TH", "CH", "SH", "T", "S", "R", "D", "L", "Z", "N", "J")
        private val SUFFIXES = listOf("ING", "ELY", "ER", "ES", "ED", "E")

        /**
         * The NRL rules: the phoneme names they produce, where "AX" is the
         * reduced vowel (schwa) that becomes an unstressed AH.
         */
        internal fun rulePhonemes(word: String): List<String> {
            val text = " " + word.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '\'' || it == '-' } + " "
            val out = ArrayList<String>()
            var i = 1
            while (i < text.length - 1) {
                val rule = RULES[text[i]]?.firstOrNull {
                    text.startsWith(it.match, i) && matchLeft(text, i, it.left) && matchRight(text, i + it.match.length, it.right)
                }
                if (rule == null) {
                    i++
                } else {
                    out += rule.out
                    i += rule.match.length
                }
            }
            return out
        }

        private fun matchLeft(text: String, pos: Int, pattern: String): Boolean {
            var t = pos - 1
            for (p in pattern.indices.reversed()) {
                when (val c = pattern[p]) {
                    '#' -> {
                        if (t < 0 || text[t] !in VOWELS) return false
                        while (t >= 0 && text[t] in VOWELS) t--
                    }
                    ':' -> while (t >= 0 && text[t] in CONSONANTS) t--
                    '^' -> if (t < 0 || text[t] !in CONSONANTS) return false else t--
                    '.' -> if (t < 0 || text[t] !in VOICED) return false else t--
                    '+' -> if (t < 0 || text[t] !in FRONT) return false else t--
                    '&', '@' -> {
                        val set = if (c == '&') SIBILANTS else PALATALISING
                        t = when {
                            t >= 1 && text.substring(t - 1, t + 1) in set -> t - 2
                            t >= 0 && text[t].toString() in set -> t - 1
                            else -> return false
                        }
                    }
                    else -> if (t < 0 || text[t] != c) return false else t--
                }
            }
            return true
        }

        private fun matchRight(text: String, pos: Int, pattern: String): Boolean {
            var t = pos
            val n = text.length
            for (c in pattern) {
                when (c) {
                    '#' -> {
                        if (t >= n || text[t] !in VOWELS) return false
                        while (t < n && text[t] in VOWELS) t++
                    }
                    ':' -> while (t < n && text[t] in CONSONANTS) t++
                    '^' -> if (t >= n || text[t] !in CONSONANTS) return false else t++
                    '.' -> if (t >= n || text[t] !in VOICED) return false else t++
                    '+' -> if (t >= n || text[t] !in FRONT) return false else t++
                    '&', '@' -> {
                        val set = if (c == '&') SIBILANTS else PALATALISING
                        t = when {
                            t + 2 <= n && text.substring(t, t + 2) in set -> t + 2
                            t < n && text[t].toString() in set -> t + 1
                            else -> return false
                        }
                    }
                    '%' -> t += SUFFIXES.firstOrNull { text.startsWith(it, t) }?.length ?: return false
                    else -> if (t >= n || text[t] != c) return false else t++
                }
            }
            return true
        }

        private val UNSTRESSED_PREFIX = Regex("^(a|be|de|re|in|un|con|com|ex|pre|pro|per|sur|to)[a-z]{3}")
        private val STRESS_BEFORE_LAST = listOf("tion", "sion", "cian", "ic", "ial", "ious", "ian")
        private val STRESS_THIRD_FROM_END = listOf("ity", "ical", "ify", "ogy", "ometer")
        private val STRESS_LAST = listOf("ee", "eer", "ese", "ette", "oon", "ique", "esque")

        /**
         * A stress guess for a rule-made pronunciation: reduced vowels stay
         * unstressed, known endings and unstressed prefixes move the primary
         * stress, otherwise it goes on the first full vowel. Words of four
         * syllables or more get a secondary stress two syllables from it.
         */
        internal fun guessStress(names: List<String>, word: String): List<Phone> {
            val vowels = names.indices.filter { names[it] == "AX" || Ph.of(names[it])?.vowel == true }
            val full = vowels.filter { names[it] != "AX" }.ifEmpty { vowels }
            val count = vowels.size
            val w = word.lowercase()
            val primaryAt: Int? = when {
                count == 0 -> null
                count == 1 -> vowels[0]
                STRESS_LAST.any { w.endsWith(it) } -> vowels.last()
                STRESS_THIRD_FROM_END.any { w.endsWith(it) } && count >= 3 -> vowels[count - 3]
                STRESS_BEFORE_LAST.any { w.endsWith(it) } -> vowels[count - 2]
                UNSTRESSED_PREFIX.containsMatchIn(w) -> full.firstOrNull { it > vowels[0] } ?: full[0]
                else -> full[0]
            }
            val secondaryAt = primaryAt?.let { p -> vowels.getOrNull(vowels.indexOf(p) + 2) }?.takeIf { count >= 4 }
            return names.mapIndexed { i, name ->
                val ph = Ph.of(if (name == "AX") "AH" else name)!!
                val stress = when {
                    !ph.vowel -> Stress.NONE
                    i == primaryAt -> Stress.PRIMARY
                    i == secondaryAt && name != "AX" -> Stress.SECONDARY
                    else -> Stress.NONE
                }
                Phone(ph, stress)
            }
        }

        private fun sibilant(ph: Ph) = ph in setOf(Ph.S, Ph.Z, Ph.SH, Ph.ZH, Ph.CH, Ph.JH)

        /** Common endings and the phonemes they add, chosen by the last phoneme of the stem. */
        private val ENDINGS: List<Pair<String, (Ph) -> List<Phone>>> = listOf(
            "'s" to { last -> sEnding(last) },
            "s" to { last -> sEnding(last) },
            "es" to { last -> sEnding(last) },
            "ing" to { _ -> listOf(Phone(Ph.IH), Phone(Ph.NG)) },
            "ed" to { last ->
                when {
                    last == Ph.T || last == Ph.D -> listOf(Phone(Ph.IH), Phone(Ph.D))
                    !last.voiced -> listOf(Phone(Ph.T))
                    else -> listOf(Phone(Ph.D))
                }
            },
            "ers" to { _ -> listOf(Phone(Ph.ER), Phone(Ph.Z)) },
            "er" to { _ -> listOf(Phone(Ph.ER)) },
            "est" to { _ -> listOf(Phone(Ph.AH), Phone(Ph.S), Phone(Ph.T)) },
            "ly" to { _ -> listOf(Phone(Ph.L), Phone(Ph.IY)) },
            "y" to { _ -> listOf(Phone(Ph.IY)) },
        )

        private fun sEnding(last: Ph): List<Phone> = when {
            sibilant(last) -> listOf(Phone(Ph.IH), Phone(Ph.Z))
            !last.voiced -> listOf(Phone(Ph.S))
            else -> listOf(Phone(Ph.Z))
        }
    }
}
