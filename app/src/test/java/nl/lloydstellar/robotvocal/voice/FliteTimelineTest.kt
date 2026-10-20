package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Ph
import org.junit.Assert.assertEquals
import org.junit.Test

/** Flite speaks its phones one after the other; they must still end where they were planned. */
class FliteTimelineTest {
    private val rate = 48_000
    private val ms = rate / 1000L

    private fun phone(ph: Ph, from: Int, to: Int) = TimedPhone(ph, from * ms, to * ms, true, 110.0, 0)

    /** Where each phone of [phones] ends in Flite's timeline, in ms from the first phone's start. */
    private fun ends(phones: List<TimedPhone>): List<Double> {
        var t = -100.0
        return FliteEngine.segments(phones, rate).map { t += it.seconds * 1000.0; t }.drop(1).dropLast(1)
    }

    @Test fun `a phone shorter than Flite allows does not push the rest of the line later`() {
        /* the 5 ms r gets Flite's shortest 12 ms; the vowel after it is that much shorter and ends on its plan */
        val phones = listOf(phone(Ph.T, 0, 100), phone(Ph.R, 100, 105), phone(Ph.IY, 105, 300))
        val ends = ends(phones)
        assertEquals(100.0, ends[0], 0.01)
        assertEquals(112.0, ends[1], 0.01)
        assertEquals(300.0, ends[2], 0.01)
    }

    @Test fun `after a rest the next phone starts on its plan`() {
        val phones = listOf(phone(Ph.AA, 0, 100), phone(Ph.T, 160, 200))
        val ends = ends(phones)
        assertEquals(listOf(100.0, 160.0, 200.0), ends.map { Math.round(it * 100) / 100.0 })
    }
}
