package nl.lloydstellar.robotvocal.score

import nl.lloydstellar.robotvocal.TestData
import nl.lloydstellar.robotvocal.text.Stress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

/** Musical time and the placement of syllables on the grid. */
class ScoreTest {
    private val lyrics = TestData.lyrics

    @Test fun `frames come from the absolute tick and do not drift`() {
        val beats = 1270
        var added = 0L
        repeat(beats) { added += (48_000 * 60.0 / 127.0).roundToLong() }
        val exact = Ticks.frame(beats * Ticks.PPQ.toDouble(), 127.0, 48_000)
        assertEquals(28_800_000L, exact) // 1270 beats at 127 BPM are exactly 600 seconds
        assertEquals("rounded beats drift by 4.4 ms", -210L, added - exact)
    }

    @Test fun `a whole number of beats gives the frame count LA2L expects`() {
        var bpm = 60.0
        while (bpm <= 200.0) {
            for (beats in listOf(4, 8, 16, 32, 64)) {
                assertEquals("bpm $bpm beats $beats",
                    Ticks.loopFrames(beats, bpm, 48_000),
                    Ticks.frame(beats * Ticks.PPQ.toDouble(), bpm, 48_000))
            }
            bpm += 0.1
        }
    }

    @Test fun `swing moves only the second and fourth 16th of a beat`() {
        assertEquals(0.0, Ticks.swung(0.0, 0.6), 1e-9)
        assertEquals(120.0 + 24.0, Ticks.swung(120.0, 0.6), 1e-9)
        assertEquals(240.0, Ticks.swung(240.0, 0.6), 1e-9)
        assertEquals(360.0 + 24.0, Ticks.swung(360.0, 0.6), 1e-9)
    }

    @Test fun `robot 8ths puts a line on the 8ths with the rhyme on step 13`() {
        val syllables = lyrics.line("Every word right on the beat").syllables
        val placement = FlowPlacer.place(syllables, FlowPreset.byId("robot8"), 124.0)
        assertEquals(1, placement.bars)
        val slots = placement.placed.map { it.slot }
        assertEquals("rhyme on step 13", 12, slots.last())
        syllables.indices.filter { syllables[it].stress == Stress.PRIMARY }.forEach {
            assertEquals("stressed syllable ${syllables[it].text} on an 8th", 0, Math.floorMod(slots[it], 2))
        }
        assertEquals(slots.sorted(), slots)
    }

    @Test fun `a comma gets a rest and a long line takes two bars`() {
        val syllables = lyrics.line("Turn the dial, push the key, robot vocal talk to me").syllables
        val placement = FlowPlacer.place(syllables, FlowPreset.byId("robot8"), 120.0)
        assertEquals(2, placement.bars)
        val dial = syllables.indexOfFirst { it.text.equals("dial", ignoreCase = true) || it.text.startsWith("di", true) }
        val afterComma = placement.placed.first { syllables[it.syllable].pause != null }
        assertTrue("rest after the comma", afterComma.length >= 3)
        assertTrue(dial >= 0)
    }

    @Test fun `locked syllables stay where the user put them`() {
        val syllables = lyrics.line("Every word right on the beat").syllables
        val placement = FlowPlacer.place(syllables, FlowPreset.byId("robot8"), 124.0, locks = mapOf(2 to 5))
        assertEquals(5, placement.placed[2].slot)
        assertTrue(placement.placed.zipWithNext().all { (a, b) -> b.slot > a.slot })
    }

    @Test fun `rapid fire fills the 16ths and triplets use twelve slots`() {
        val line = "I got the rhythm in the circuit and the bass in the machine"
        val rapid = FlowPlacer.place(lyrics.line(line).syllables, FlowPreset.byId("rapid16"), 92.0)
        val gaps = rapid.placed.zipWithNext().map { (a, b) -> b.slot - a.slot }
        assertTrue("mostly one 16th apart: $gaps", gaps.count { it == 1 } >= gaps.size / 2)
        val triplet = FlowPlacer.place(lyrics.line(line).syllables, FlowPreset.byId("triplet"), 140.0)
        assertEquals(12, triplet.slotsPerBar)
        assertEquals(160, Ticks.perSlot(12))
    }

    @Test fun `every word starts on a beat in word per beat`() {
        val syllables = lyrics.line("one two three four").syllables
        val placement = FlowPlacer.place(syllables, FlowPreset.byId("wordbeat"), 127.0)
        placement.placed.forEach { assertEquals(0, Math.floorMod(it.slot, 4)) }
    }

    @Test fun `robot 8ths keeps to the 8ths and takes an 8th pickup`() {
        val syllables = lyrics.line("I am the voice of the machine").syllables
        val placement = FlowPlacer.place(syllables, FlowPreset.byId("robot8"), 120.0)
        val slots = placement.placed.map { it.slot }
        assertEquals(listOf(-2, 0, 2, 4, 6, 8, 10, 12), slots)
    }

    @Test fun `electro and half-time flows keep their 16ths up to 140 BPM`() {
        for ((flow, line) in listOf("electro332" to "Computer love on the dance floor tonight", "halftime" to "Breaking the code of the synthesizer")) {
            val syllables = lyrics.line(line).syllables
            val slow = FlowPlacer.place(syllables, FlowPreset.byId(flow), 120.0)
            val fast = FlowPlacer.place(syllables, FlowPreset.byId(flow), 140.0)
            assertEquals("$flow: bars", slow.bars, fast.bars)
            val sixteenths = fast.placed.zipWithNext().count { (a, b) -> b.slot - a.slot == 1 }
            assertTrue("$flow at 140 BPM: $sixteenths syllables a 16th apart", sixteenths >= 2)
        }
    }

    @Test fun `the last word of a line can carry the rhyme`() {
        val syllables = lyrics.line("Turn the dial, push the key, robot vocal talk to me").syllables
        val placement = FlowPlacer.place(syllables, FlowPreset.byId("robot8"), 120.0)
        assertEquals(2, placement.bars)
        assertEquals("ME on step 13 of bar 2", 16 + 12, placement.placed.last().slot)
    }
}
