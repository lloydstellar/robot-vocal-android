package nl.lloydstellar.robotvocal.render

import nl.lloydstellar.robotvocal.dsp.FxContext
import nl.lloydstellar.robotvocal.dsp.RobotPresets
import nl.lloydstellar.robotvocal.score.FlowPlacer
import nl.lloydstellar.robotvocal.score.FlowPreset
import nl.lloydstellar.robotvocal.score.LinePlacement
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.Ticks
import nl.lloydstellar.robotvocal.text.LyricLine
import nl.lloydstellar.robotvocal.text.Lyrics
import nl.lloydstellar.robotvocal.voice.VoiceEngine
import nl.lloydstellar.robotvocal.voice.VoiceRegistry
import java.util.Arrays
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
 * The rendered vocal: one channel (mono) or two (stereo, when a stereo effect
 * is on), from song frame [origin] on (negative when a pickup, a consonant or a
 * reverse swell starts before bar 1). The song itself is exactly [loopFrames]
 * long, a whole number of bars.
 */
class VocalTake(
    val channels: Array<FloatArray>,
    /** The voice before every effect, mono, the same length and origin: the dry stem. */
    val dry: FloatArray,
    val origin: Long,
    val loopFrames: Long,
    val bpm: Double,
    val bars: Int,
    val phones: List<TimedPhone>,
) {
    val stereo: Boolean get() = channels.size == 2
}

object Renderer {
    const val RATE = TimingPlanner.SAMPLE_RATE
    const val PEAK = 0.8f

    fun render(song: Song, arrangement: Arrangement, voices: VoiceRegistry, sampleRate: Int = TimingPlanner.SAMPLE_RATE): VocalTake =
        render(song, arrangement, voices.engineFor(song.voice), sampleRate)

    fun render(song: Song, arrangement: Arrangement, engine: VoiceEngine, sampleRate: Int = TimingPlanner.SAMPLE_RATE): VocalTake {
        val planner = TimingPlanner(sampleRate)
        val note = song.note ?: engine.voices.firstOrNull { it.id == song.voice }?.note ?: 48
        var syllableBase = 0
        var wordBase = 0
        val stutters = ArrayList<Pair<Int, Int>>() // syllable in the song, repeats
        val phones = arrangement.lines.flatMap { line ->
            val settings = TimingPlanner.Settings(
                bpm = song.bpm,
                swing = song.swing ?: line.preset.swing,
                offsetMs = line.preset.offsetMs,
                gate = song.gate ?: line.preset.gate,
                feel = song.feel,
            )
            val songLine = song.lines.getOrNull(line.index)
            val notes = songLine?.notes.orEmpty()
            songLine?.stutters?.forEach { (syllable, repeats) -> stutters += (syllableBase + syllable) to repeats }
            planner.plan(line.lyric.syllables, line.placement, line.startTick, settings, syllableBase, wordBase) { syllable ->
                hz(note + (notes[syllable] ?: 0))
            }.also {
                syllableBase += line.lyric.syllables.size
                wordBase += line.lyric.words.size
            }
        }
        val loopFrames = Ticks.loopFrames(arrangement.bars * 4, song.bpm, sampleRate)
        if (phones.isEmpty()) {
            val silence = FloatArray(loopFrames.toInt())
            return VocalTake(arrayOf(silence), silence, 0, loopFrames, song.bpm, arrangement.bars, phones)
        }
        val timed = endBeforeTheLoopComesAround(phones, loopFrames, sampleRate)
        val rendered = engine.render(timed, song.voice, sampleRate, seed = song.id.hashCode())
        /* room before the voice for reverse swells and after it for what rings on; both fold into the loop */
        val lead = if (song.space != "none" || song.motion != "none") sampleRate else 0
        val ringOut = if (song.space != "none") sampleRate * 4 else sampleRate / 2
        val dry = FloatArray(lead + rendered.samples.size + ringOut)
        System.arraycopy(rendered.samples, 0, dry, lead, rendered.samples.size)
        val origin = rendered.origin - lead
        if (stutters.isNotEmpty()) stutter(dry, origin, timed, stutters, song.bpm, sampleRate)
        fun ranges(pick: (TimedPhone) -> Boolean) = timed.filter(pick).groupBy { it.syllable }.values.map { it.first().start..it.last().end }
        val ctx = FxContext(sampleRate, song.bpm, origin, song.id.hashCode(), ranges { it.lineEnd }, ranges { it.lineStart },
            noiseTrack(timed, sampleRate)) { frame -> pitchAt(timed, frame) }
        var channels = arrayOf(dry.copyOf())
        for (preset in listOf(RobotPresets.byId(song.effect), RobotPresets.motion(song.motion), RobotPresets.space(song.space))) {
            channels = preset.apply(channels, ctx)
        }
        normalise(dry)
        normalise(*channels)
        return VocalTake(channels, dry, origin, loopFrames, song.bpm, arrangement.bars, timed)
    }

    fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /**
     * Stutters: the start of a syllable repeated [repeats] times in 32nds (16ths
     * for two), "ro-ro-ro-robot", after which the syllable goes on where the
     * time has got to. Short fades keep the cuts from clicking.
     */
    private fun stutter(x: FloatArray, origin: Long, phones: List<TimedPhone>, stutters: List<Pair<Int, Int>>, bpm: Double, rate: Int) {
        val fade = rate / 500
        for ((syllable, repeats) in stutters) {
            val first = phones.firstOrNull { it.syllable == syllable } ?: continue
            val slice = ((if (repeats <= 2) 0.25 else 0.125) * 60.0 / bpm * rate).toInt()
            val start = (first.start - origin).toInt()
            if (start < 0 || start + slice * repeats >= x.size) continue
            val source = x.copyOfRange(start, start + slice)
            for (r in 0 until repeats) for (i in 0 until slice) {
                val w = minOf(1f, i.toFloat() / fade, (slice - i).toFloat() / fade)
                x[start + r * slice + i] = source[i] * w
            }
        }
    }

    /** The planned pitch at a song frame: the phone sounding there, else the one before. */
    private fun pitchAt(phones: List<TimedPhone>, frame: Long): Double {
        var lo = 0
        var hi = phones.size - 1
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (phones[mid].start <= frame) {
                found = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        return phones[found].pitchHz
    }

    /** How much of the voice is noise at a song frame: the [Excitation] of the phone sounding there, none in a rest. */
    private fun noiseTrack(phones: List<TimedPhone>, rate: Int): (Long) -> Double {
        val parts = phones.flatMap { Excitation.parts(it.ph, it.start.toDouble(), it.end.toDouble(), rate) }
        val starts = DoubleArray(parts.size) { parts[it].start }
        return { frame ->
            val found = Arrays.binarySearch(starts, frame.toDouble())
            val i = if (found >= 0) found else -found - 2
            parts.getOrNull(i)?.takeIf { frame < it.end }?.noise ?: 0.0
        }
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

    /** Scales all [channels] together so their highest peak is [PEAK]. */
    private fun normalise(vararg channels: FloatArray) {
        var peak = 0f
        for (x in channels) for (v in x) peak = maxOf(peak, abs(v))
        if (peak > 1e-6f) {
            val g = PEAK / peak
            for (x in channels) for (i in x.indices) x[i] *= g
        }
    }

    /**
     * The take as a loop of exactly [VocalTake.loopFrames] frames: what rings
     * past the end is added to the start, and what starts before bar 1 (a
     * pickup, the consonants before the first beat) is added to the end. Looped,
     * every vowel stays on its beat.
     */
    fun loop(take: VocalTake, dry: Boolean = false): Array<FloatArray> {
        val out = fold(take, if (dry) arrayOf(take.dry) else take.channels)
        normalise(*out)
        return out
    }

    /**
     * The take for the preview: as a loop, and as it sounds played once, from
     * its first sample at song frame [VocalTake.origin], with what starts
     * before bar 1 in front and what rings past the end after it (at least as
     * long as the song). Both with one gain, so the level does not jump when
     * looping is switched.
     */
    fun preview(take: VocalTake): Pair<Array<FloatArray>, Array<FloatArray>> {
        val loop = fold(take, take.channels)
        val n = maxOf(take.channels[0].size.toLong(), take.loopFrames - take.origin).toInt()
        val once = Array(take.channels.size) { take.channels[it].copyOf(n) }
        normalise(*loop, *once)
        return loop to once
    }

    private fun fold(take: VocalTake, sources: Array<FloatArray>): Array<FloatArray> {
        val out = Array(sources.size) { FloatArray(take.loopFrames.toInt()) }
        for ((c, samples) in sources.withIndex()) {
            for (i in samples.indices) {
                out[c][Math.floorMod(take.origin + i, take.loopFrames).toInt()] += samples[i]
            }
        }
        return out
    }
}
