package nl.lloydstellar.robotvocal.score

import kotlin.math.roundToLong

/**
 * Musical time. Positions are ticks, 480 to a quarter note, so straight 16ths
 * (120 ticks) and triplets (160 or 80 ticks) both land on whole numbers.
 *
 * Frames are always computed from the absolute tick, never by adding up beat
 * lengths: at 127 BPM a beat is 22,677.165 frames, and adding rounded beats
 * would drift by 4.4 ms after 1,270 beats.
 */
object Ticks {
    const val PPQ = 480
    const val BEAT = PPQ
    const val BAR = PPQ * 4
    const val SIXTEENTH = PPQ / 4

    /** Ticks per slot for a bar of [slotsPerBar] slots: 120 for 16ths, 160 for 8th triplets. */
    fun perSlot(slotsPerBar: Int): Int {
        require(BAR % slotsPerBar == 0) { "A bar of 1920 ticks cannot hold $slotsPerBar slots" }
        return BAR / slotsPerBar
    }

    fun seconds(tick: Double, bpm: Double): Double = tick * 60.0 / (bpm * PPQ)

    /** The audio frame where [tick] falls at [bpm]. */
    fun frame(tick: Double, bpm: Double, sampleRate: Int): Long = (tick * 60.0 * sampleRate / (bpm * PPQ)).roundToLong()

    /**
     * Frames in a loop of [beats] beats, written exactly as LA2L computes it
     * (rate x 60 x beats / bpm, rounded), so Live reads the same length.
     */
    fun loopFrames(beats: Int, bpm: Double, sampleRate: Int): Long = (sampleRate * 60.0 * beats / bpm).roundToLong()

    /**
     * Swing moves the second and fourth 16th of every beat later by
     * (swing - 0.5) x 2 sixteenths: 50 % is straight, 66.7 % a triplet shuffle.
     * Other positions keep their place.
     */
    fun swung(tick: Double, swing: Double): Double {
        if (swing == 0.5) return tick
        val inBeat = ((tick % BEAT) + BEAT) % BEAT
        val offbeat = inBeat == SIXTEENTH.toDouble() || inBeat == 3.0 * SIXTEENTH
        return if (offbeat) tick + (swing - 0.5) * 2 * SIXTEENTH else tick
    }
}
