package nl.lloydstellar.robotvocal.text

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * CMUdict, packed by tools/make_lexicon.py: 126,052 words with their
 * pronunciation. The whole file stays in one byte array (2.7 MB) and words are
 * found by binary search, so loading costs one read and no parsing.
 */
class Lexicon private constructor(private val data: ByteArray) {
    private val buf = ByteBuffer.wrap(data)
    val size: Int
    private val wordOffsets: Int
    private val phoneOffsets: Int
    private val wordBlob: Int
    private val phoneBlob: Int

    init {
        require(data.size > 9 && String(data, 0, 4, Charsets.US_ASCII) == "RVLX") { "Not a Robot Vocal lexicon" }
        require(data[4].toInt() == 1) { "Unknown lexicon version ${data[4]}" }
        size = buf.getInt(5)
        wordOffsets = 9
        phoneOffsets = wordOffsets + (size + 1) * 4
        wordBlob = phoneOffsets + (size + 1) * 4
        phoneBlob = wordBlob + buf.getInt(wordOffsets + size * 4)
    }

    /** The pronunciation of [word] (any case), or null when the dictionary does not have it. */
    fun lookup(word: String): List<Phone>? {
        val key = word.lowercase().toByteArray(Charsets.US_ASCII)
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(mid, key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return phones(mid)
            }
        }
        return null
    }

    operator fun contains(word: String) = lookup(word) != null

    private fun compare(index: Int, key: ByteArray): Int {
        val start = wordBlob + buf.getInt(wordOffsets + index * 4)
        val end = wordBlob + buf.getInt(wordOffsets + (index + 1) * 4)
        val n = minOf(end - start, key.size)
        for (i in 0 until n) {
            val a = data[start + i].toInt() and 0xFF
            val b = key[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return (end - start) - key.size
    }

    private fun phones(index: Int): List<Phone> {
        val start = phoneBlob + buf.getInt(phoneOffsets + index * 4)
        val end = phoneBlob + buf.getInt(phoneOffsets + (index + 1) * 4)
        return (start until end).map { i ->
            val code = data[i].toInt() and 0xFF
            val ph = Ph.entries[code and 0x3F]
            val stress = (code ushr 6) - 1
            Phone(ph, if (ph.vowel && stress >= 0) Stress.of(stress) else Stress.NONE)
        }
    }

    companion object {
        const val ASSET = "lexicon/cmudict.bin"

        fun read(input: InputStream): Lexicon = Lexicon(input.use { it.readBytes() })
    }
}
