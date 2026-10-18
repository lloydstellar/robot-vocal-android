package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.score.LinePlacement
import nl.lloydstellar.robotvocal.score.Ticks
import nl.lloydstellar.robotvocal.text.Ph
import nl.lloydstellar.robotvocal.text.Stress
import nl.lloydstellar.robotvocal.text.Syllable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** One phoneme with its place in the song, in frames from the downbeat of bar 1. */
data class TimedPhone(
    val ph: Ph,
    val start: Long,
    val end: Long,
    val stressed: Boolean,
    val pitchHz: Double,
    /** The syllable and the word in the whole song, counted from 0. */
    val syllable: Int,
    val word: Int = syllable,
    /** Part of the last syllable of its line: where an echo throw goes. */
    val lineEnd: Boolean = false,
    /** Part of the first syllable of its line: where a reverse swell leads in. */
    val lineStart: Boolean = false,
    val vowel: Boolean = ph.vowel,
)

/**
 * Turns placed syllables into phonemes with exact start and end frames.
 *
 * The point of a syllable that a listener hears as "on the beat" is close to
 * the onset of its vowel, a little earlier for long consonant clusters (the
 * perceptual centre; docs/research/04, section 2). So the vowel starts at
 * `beat + lead` with `lead = min(feel x onset length, 60 ms)`, and the onset
 * consonants run up to it before the beat. A run-up longer than half of the
 * previous syllable's time, or a vowel squeezed by its coda, is compressed
 * towards Klatt's minimum durations (the auto-fit rule of OpenUtau).
 */
class TimingPlanner(private val sampleRate: Int = SAMPLE_RATE) {

    data class Settings(
        val bpm: Double,
        val swing: Double = 0.5,
        val offsetMs: Double = 0.0,
        val gate: Double = 0.85,
        val feel: Double = 0.25,
    )

    fun plan(
        syllables: List<Syllable>,
        placement: LinePlacement,
        lineStartTick: Int,
        settings: Settings,
        syllableBase: Int = 0,
        wordBase: Int = 0,
        pitchOf: (syllable: Int) -> Double,
    ): List<TimedPhone> {
        val perSlot = Ticks.perSlot(placement.slotsPerBar)
        val offset = ms(settings.offsetMs)
        val beats = placement.placed.map { p ->
            val tick = lineStartTick + p.slot * perSlot.toDouble()
            Ticks.frame(Ticks.swung(tick, settings.swing), settings.bpm, sampleRate) + offset
        }
        val ends = placement.placed.map { p ->
            Ticks.frame(lineStartTick + (p.slot + p.length) * perSlot.toDouble(), settings.bpm, sampleRate) + offset
        }

        val out = ArrayList<TimedPhone>()
        var previousEnd = Long.MIN_VALUE
        for ((k, p) in placement.placed.withIndex()) {
            val syllable = syllables[p.syllable]
            val last = k == placement.placed.lastIndex
            val first = k == 0
            val beat = beats[k]
            val speakEnd = beat + ((ends[k] - beat) * settings.gate).roundToLong()
            val nextStart = if (k + 1 < beats.size) beats[k + 1] - ms(15.0) else Long.MAX_VALUE
            val stressed = syllable.stress != Stress.NONE

            /* onset: natural lengths, run-up at most half of the previous syllable's time */
            var onset = syllable.onset.map { ms(Durations.inherent(it.ph, stressed)) }
            val previousSpan = if (k > 0) beat - beats[k - 1] else ms(250.0)
            val lead0 = lead(onset.sum(), settings.feel)
            val runUp = onset.sum() - lead0
            val runUpLimit = min(previousSpan / 2, if (previousEnd == Long.MIN_VALUE) Long.MAX_VALUE else beat - previousEnd)
            if (runUp > runUpLimit) onset = compress(syllable.onset.map { it.ph }, onset, runUpLimit + lead0)
            val lead = lead(onset.sum(), settings.feel)
            val vowelStart = beat + lead
            var t = vowelStart - onset.sum()
            for ((i, phone) in syllable.onset.withIndex()) {
                out += TimedPhone(phone.ph, t, t + onset[i], stressed, pitchOf(p.syllable), syllableBase + p.syllable, wordBase + syllable.word, last, first)
                t += onset[i]
            }

            /* vowel and coda inside the gated length, never into the next syllable */
            val limit = min(max(speakEnd, vowelStart + ms(Durations.minimum(syllable.vowel.ph))), nextStart)
            var coda = syllable.coda.map { ms(Durations.inherent(it.ph, stressed)) }
            val minVowel = ms(Durations.minimum(syllable.vowel.ph))
            if (limit - vowelStart - coda.sum() < minVowel) {
                coda = compress(syllable.coda.map { it.ph }, coda, max(0L, limit - vowelStart - minVowel))
            }
            val vowelEnd = max(vowelStart + ms(20.0), limit - coda.sum())
            out += TimedPhone(syllable.vowel.ph, vowelStart, vowelEnd, stressed, pitchOf(p.syllable), syllableBase + p.syllable, wordBase + syllable.word, last, first)
            t = vowelEnd
            for ((i, phone) in syllable.coda.withIndex()) {
                out += TimedPhone(phone.ph, t, t + coda[i], stressed, pitchOf(p.syllable), syllableBase + p.syllable, wordBase + syllable.word, last, first)
                t += coda[i]
            }
            previousEnd = t
        }
        return out
    }

    private fun lead(onsetFrames: Long, feel: Double): Long = min((feel * onsetFrames).roundToLong(), ms(60.0))

    /** Shrinks [lengths] to fit [target] frames in total, no phoneme below its minimum unless even that does not fit. */
    private fun compress(phs: List<Ph>, lengths: List<Long>, target: Long): List<Long> {
        val total = lengths.sum()
        if (total <= target || total == 0L) return lengths
        val minimums = phs.map { ms(Durations.minimum(it)) }
        val minTotal = minimums.sum()
        if (target <= minTotal) {
            val scale = max(0.3, target.toDouble() / minTotal)
            return minimums.map { (it * scale).roundToLong() }
        }
        val room = (target - minTotal).toDouble() / (total - minTotal)
        return lengths.indices.map { minimums[it] + ((lengths[it] - minimums[it]) * room).roundToLong() }
    }

    private fun ms(ms: Double): Long = (ms * sampleRate / 1000.0).roundToLong()

    companion object {
        const val SAMPLE_RATE = 48_000
    }
}

/**
 * Inherent and minimum phoneme durations in milliseconds, after the values of
 * Klatt's duration rules (1979), rounded. Unstressed vowels are shorter.
 */
object Durations {
    private val TABLE: Map<Ph, Pair<Int, Int>> = mapOf(
        Ph.AA to (240 to 100), Ph.AE to (230 to 80), Ph.AH to (140 to 60), Ph.AO to (240 to 100),
        Ph.AW to (260 to 100), Ph.AY to (250 to 150), Ph.EH to (150 to 70), Ph.ER to (180 to 80),
        Ph.EY to (190 to 100), Ph.IH to (135 to 40), Ph.IY to (155 to 55), Ph.OW to (220 to 80),
        Ph.OY to (280 to 150), Ph.UH to (160 to 60), Ph.UW to (210 to 70),
        Ph.B to (85 to 60), Ph.CH to (70 to 50), Ph.D to (75 to 50), Ph.DH to (50 to 30), Ph.F to (100 to 80),
        Ph.G to (80 to 60), Ph.HH to (80 to 20), Ph.JH to (70 to 50), Ph.K to (80 to 60), Ph.L to (80 to 40),
        Ph.M to (70 to 60), Ph.N to (60 to 50), Ph.NG to (95 to 60), Ph.P to (90 to 50), Ph.R to (80 to 30),
        Ph.S to (105 to 60), Ph.SH to (105 to 80), Ph.T to (75 to 50), Ph.TH to (90 to 60), Ph.V to (60 to 40),
        Ph.W to (80 to 60), Ph.Y to (80 to 40), Ph.Z to (75 to 40), Ph.ZH to (70 to 40),
    )

    fun inherent(ph: Ph, stressed: Boolean): Double {
        val (inherent, minimum) = TABLE.getValue(ph)
        return if (ph.vowel && !stressed) minimum + (inherent - minimum) * 0.6 else inherent.toDouble()
    }

    fun minimum(ph: Ph): Double = TABLE.getValue(ph).second.toDouble()
}
