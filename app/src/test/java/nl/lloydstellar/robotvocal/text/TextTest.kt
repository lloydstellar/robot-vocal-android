package nl.lloydstellar.robotvocal.text

import nl.lloydstellar.robotvocal.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** From typed text to phonemes, syllables and stress. */
class TextTest {
    private val lexicon = TestData.lexicon
    private val lyrics = TestData.lyrics

    private fun spoken(line: String) =
        Normalizer(lexicon).tokens(line).filterIsInstance<WordToken>().joinToString(" ") { it.spoken }

    @Test fun `the lexicon finds words in any case`() {
        assertEquals(126_052, lexicon.size)
        assertEquals("R OW1 B AA2 T", lexicon.lookup("robot")!!.arpabet())
        assertEquals("R OW1 B AA2 T", lexicon.lookup("ROBOT")!!.arpabet())
        assertEquals("D OW1 N T", lexicon.lookup("don't")!!.arpabet())
        assertNull(lexicon.lookup("zzxqv"))
    }

    @Test fun `of the variants the one with the fewest syllables is kept`() {
        assertEquals(2, Syllabifier.split(lexicon.lookup("every")!!).size)
        assertEquals(1, Syllabifier.split(lexicon.lookup("fire")!!).size)
    }

    @Test fun `bass is said as in a bassline, and the text stays as typed`() {
        assertEquals("B EY1 S", lexicon.lookup("bass")!!.arpabet())
        assertEquals("L IY1 D", lexicon.lookup("lead")!!.arpabet())
        assertEquals("B EY1 S L AY2 N", LetterToSound(lexicon).pronounce("bassline").arpabet())
        val bass = lyrics.line("Drop the BASS").syllables.last()
        assertEquals("BASS", bass.text)
        assertEquals(Ph.EY, bass.vowel.ph)
    }

    @Test fun `numbers are said the way lyrics say them`() {
        assertEquals("eight oh eight", spoken("808"))
        assertEquals("nineteen eighty four", spoken("1984"))
        assertEquals("twenty twenty six", spoken("2026"))
        assertEquals("twenty four seven", spoken("24/7"))
        assertEquals("one hundred twenty four", spoken("124"))
        assertEquals("twenty first", spoken("21st"))
        assertEquals("nineties", spoken("90s"))
        assertEquals("five dollars", spoken("$5"))
        assertEquals("twelve thirty", spoken("12:30"))
        assertEquals("fifty percent", spoken("50%"))
        assertEquals("three point five", spoken("3.5"))
    }

    @Test fun `initialisms are spelled, symbols become words`() {
        assertEquals("b p m", spoken("BPM"))
        assertEquals("d j", spoken("DJ"))
        assertEquals("rock and roll", spoken("rock & roll"))
        assertEquals("nasa", spoken("NASA"))
    }

    @Test fun `punctuation becomes pauses, curly apostrophes are read`() {
        val tokens = Normalizer(lexicon).tokens("Turn the dial, push the key. Don’t stop")
        assertEquals(listOf(Pause.COMMA, Pause.STOP), tokens.filterIsInstance<PauseToken>().map { it.pause })
        assertTrue(tokens.filterIsInstance<WordToken>().any { it.spoken == "don't" })
    }

    @Test fun `maximal onset puts str in the second syllable of extra`() {
        val parts = Syllabifier.split(lexicon.lookup("extra")!!)
        assertEquals(listOf("EH1 K", "S T R AH0"), parts.map { it.phones.arpabet() })
    }

    @Test fun `missing words are built from an ending, a compound or the rules`() {
        val l2s = LetterToSound(lexicon)
        assertNull(lexicon.lookup("dancefloor"))
        assertEquals("D AE1 N S F L AO2 R", l2s.pronounce("dancefloor").arpabet())
        val zarvox = l2s.pronounce("zarvox")
        assertEquals(1, zarvox.count { it.stress == Stress.PRIMARY })
        assertTrue(zarvox.first().ph == Ph.Z)
    }

    @Test fun `a line becomes syllables with spelling, stress and word bounds`() {
        val line = lyrics.line("Every word right on the beat")
        val syllables = line.syllables
        assertEquals(listOf("Eve", "ry", "word", "right", "on", "the", "beat"), syllables.map { it.text })
        assertEquals(Stress.PRIMARY, syllables[0].stress)
        assertEquals(Stress.NONE, syllables[5].stress) // "the" is a function word
        assertTrue(syllables[1].wordEnd && !syllables[0].wordEnd)
        assertEquals(Ph.IY, syllables[6].vowel.ph)
    }

    @Test fun `a respelling sets syllables and stress`() {
        val syllables = lyrics.line("[ro-BOT] vocal").syllables
        assertEquals(listOf("ro", "BOT", "vo", "cal"), syllables.map { it.text })
        assertEquals(Stress.NONE, syllables[0].stress)
        assertEquals(Stress.PRIMARY, syllables[1].stress)
    }

    @Test fun `spelling is cut along the vowel groups`() {
        assertEquals(listOf("ro", "bot"), spell("robot", 2))
        assertEquals(listOf("fami", "ly"), spell("family", 2))
        assertEquals(listOf("ma", "chine"), spell("machine", 2))
        assertEquals(listOf("vo", "co", "der"), spell("vocoder", 3))
    }
}
