package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.score.FlowPlacer
import nl.lloydstellar.robotvocal.score.FlowPreset
import nl.lloydstellar.robotvocal.score.LinePlacement
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.Ticks
import nl.lloydstellar.robotvocal.text.LyricLine
import nl.lloydstellar.robotvocal.text.Lyrics
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import kotlin.math.abs
import kotlin.math.pow

/** A line of the song with its syllables, where they go and the bar it starts in. */
class ArrangedLine(val index: Int, val lyric: LyricLine, val preset: FlowPreset, val placement: LinePlacement, val startBar: Int) {
    val startTick: Int get() = startBar * Ticks.BAR
}

/** The whole song on the grid: the lines one after the other, each in its own whole bars. */
class Arrangement(val lines: List<ArrangedLine>, val bars: Int)

object Arranger {
    fun arrange(song: Song, lyrics: Lyrics): Arrangement {
        val out = ArrayList<ArrangedLine>()
        var bar = 0
        /* the first tick after the previous line's last syllable has had one slot, counted from this line's start */
        var previousEndTick: Int? = null
        song.lines.forEachIndexed { i, line ->
            val lyric = lyrics.line(line.text)
            val preset = FlowPreset.byId(line.flow ?: song.flow)
            val perSlot = Ticks.perSlot(preset.slotsPerBar)
            val firstFree = previousEndTick?.let { Math.floorDiv(it + perSlot - 1, perSlot) } ?: Int.MIN_VALUE
            val placement = FlowPlacer.place(lyric.syllables, preset, song.bpm, line.bars, line.locks, firstFree)
            out += ArrangedLine(i, lyric, preset, placement, bar)
            val last = placement.placed.lastOrNull()
            previousEndTick = last?.let { (it.slot + 1) * perSlot - placement.bars * Ticks.BAR }
            bar += placement.bars
        }
        return Arrangement(out, bar.coerceAtLeast(1))
    }
}

/**
 * The rendered vocal. [samples] hold the song from frame [origin] on (origin
 * is negative when a pickup or a consonant starts before bar 1); the song
 * itself is exactly [loopFrames] long, a whole number of bars.
 */
class VocalTake(
    val samples: FloatArray,
    val origin: Long,
    val loopFrames: Long,
    val bpm: Double,
    val bars: Int,
    val phones: List<TimedPhone>,
)

object Renderer {
    const val PEAK = 0.8f

    fun render(song: Song, arrangement: Arrangement, engine: VoiceEngine, sampleRate: Int = TimingPlanner.SAMPLE_RATE): VocalTake {
        val planner = TimingPlanner(sampleRate)
        val note = song.note ?: engine.voices.firstOrNull { it.id == song.voice }?.note ?: 48
        val hz = 440.0 * 2.0.pow((note - 69) / 12.0)
        val phones = arrangement.lines.flatMap { line ->
            val settings = TimingPlanner.Settings(
                bpm = song.bpm,
                swing = song.swing ?: line.preset.swing,
                offsetMs = line.preset.offsetMs,
                gate = song.gate ?: line.preset.gate,
                feel = song.feel,
            )
            planner.plan(line.lyric.syllables, line.placement, line.startTick, settings) { hz }
        }
        val loopFrames = Ticks.loopFrames(arrangement.bars * 4, song.bpm, sampleRate)
        if (phones.isEmpty()) return VocalTake(FloatArray(loopFrames.toInt()), 0, loopFrames, song.bpm, arrangement.bars, phones)
        val timed = endBeforeTheLoopComesAround(phones, loopFrames, sampleRate)
        val rendered = engine.render(timed, song.voice, sampleRate, seed = song.id.hashCode())
        normalise(rendered.samples)
        return VocalTake(rendered.samples, rendered.origin, loopFrames, song.bpm, arrangement.bars, timed)
    }

    /**
     * In a loop the song's first sound follows its last one: the last syllable
     * stops 10 ms before the first consonant of the next time round, so the two
     * never sound at once.
     */
    private fun endBeforeTheLoopComesAround(phones: List<TimedPhone>, loopFrames: Long, sampleRate: Int): List<TimedPhone> {
        val limit = loopFrames + minOf(0L, phones.first().start) - sampleRate / 100
        return phones.mapNotNull {
            when {
                it.start >= limit -> null
                it.end > limit -> it.copy(end = limit)
                else -> it
            }
        }
    }

    private fun normalise(x: FloatArray) {
        var peak = 0f
        for (v in x) peak = maxOf(peak, abs(v))
        if (peak > 1e-6f) {
            val g = PEAK / peak
            for (i in x.indices) x[i] *= g
        }
    }

    /**
     * The take as a loop of exactly [VocalTake.loopFrames] frames: what rings
     * past the end is added to the start, and what starts before bar 1 (a
     * pickup, the consonants before the first beat) is added to the end. Looped,
     * every vowel stays on its beat.
     */
    fun loop(take: VocalTake): FloatArray {
        val n = take.loopFrames.toInt()
        val out = FloatArray(n)
        for (i in take.samples.indices) {
            val frame = take.origin + i
            out[Math.floorMod(frame, take.loopFrames).toInt()] += take.samples[i]
        }
        normalise(out)
        return out
    }
}
