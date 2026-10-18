package nl.lloydstellar.robotvocal.beats

import nl.lloydstellar.robotvocal.score.Ticks
import kotlin.math.tanh

/**
 * A one-bar drum pattern: per drum 16 steps, `x` a hit, `o` a soft hit,
 * `.` nothing. The example beats are for checking the timing of the vocal
 * and never end up in a file: nothing in the export code can reach them.
 */
class BeatPattern(val id: String, val name: String, val tracks: Map<Drum, String>, val bpm: IntRange) {
    init {
        require(tracks.values.all { it.length == 16 }) { "$id: every track needs 16 steps" }
    }

    companion object {
        val ALL = listOf(
            BeatPattern("electro1", "ELECTRO 1", mapOf(
                Drum.KICK to "x.....x...x.....",
                Drum.CLAP to "....x.......x...",
                Drum.HAT to "x.o.x.o.x.o.x.o.",
                Drum.COWBELL to "..o.....o.o...o.",
            ), 110..130),
            BeatPattern("electro2", "ELECTRO 2", mapOf(
                Drum.KICK to "x..x..x...x..x..",
                Drum.SNARE to "....x.......x...",
                Drum.HAT to "xoxoxoxoxoxoxo..",
                Drum.OPEN_HAT to "..............x.",
            ), 115..135),
            BeatPattern("boombap", "BOOM BAP", mapOf(
                Drum.KICK to "x.......x.x.....",
                Drum.SNARE to "....x.......x...",
                Drum.HAT to "x.x.x.x.x.x.x.x.",
                Drum.RIM to ".......o......o.",
            ), 84..100),
            BeatPattern("halftime", "HALF TIME", mapOf(
                Drum.KICK to "x......x..x.....",
                Drum.SNARE to "........x.......",
                Drum.HAT to "xoxoxoxoxoxoxxxx",
            ), 130..160),
            BeatPattern("house", "HOUSE", mapOf(
                Drum.KICK to "x...x...x...x...",
                Drum.CLAP to "....x.......x...",
                Drum.OPEN_HAT to "..x...x...x...x.",
                Drum.HAT to "o.o.o.o.o.o.o.o.",
            ), 118..128),
            BeatPattern("techno", "TECHNO", mapOf(
                Drum.KICK to "x...x...x...x...",
                Drum.HAT to "oxooxoxooxoxoxox",
                Drum.RIM to "......o.......o.",
                Drum.TOM to "...........o....",
            ), 125..140),
            BeatPattern("click", "CLICK", mapOf(
                Drum.RIM to "x...o...o...o...",
            ), 40..240),
        )

        fun byId(id: String): BeatPattern = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}

/** Renders a pattern as an exact loop of whole bars, the same length as the vocal loop. */
object BeatRenderer {
    private val samples = HashMap<Pair<Drum, Int>, FloatArray>()

    private fun sound(drum: Drum, rate: Int): FloatArray = synchronized(samples) {
        samples.getOrPut(drum to rate) { DrumSynth.make(drum, rate) }
    }

    fun render(pattern: BeatPattern, bars: Int, bpm: Double, swing: Double, rate: Int): FloatArray {
        val length = Ticks.loopFrames(bars * 4, bpm, rate)
        val out = FloatArray(length.toInt())
        for (bar in 0 until bars) for ((drum, steps) in pattern.tracks) {
            val sound = sound(drum, rate)
            steps.forEachIndexed { step, c ->
                val gain = when (c) {
                    'x' -> 1.0f
                    'o' -> 0.5f
                    else -> return@forEachIndexed
                } * LEVELS.getValue(drum)
                val tick = Ticks.swung(bar * Ticks.BAR + step * Ticks.SIXTEENTH.toDouble(), swing)
                val start = Ticks.frame(tick, bpm, rate)
                for (i in sound.indices) {
                    val at = Math.floorMod(start + i, length).toInt()
                    out[at] += sound[i] * gain
                }
            }
        }
        for (i in out.indices) out[i] = tanh(out[i] * 0.9f)
        return out
    }

    private val LEVELS = mapOf(
        Drum.KICK to 0.9f, Drum.SNARE to 0.55f, Drum.CLAP to 0.5f, Drum.HAT to 0.28f, Drum.OPEN_HAT to 0.25f,
        Drum.COWBELL to 0.3f, Drum.RIM to 0.5f, Drum.TOM to 0.5f,
    )
}
