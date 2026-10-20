package nl.lloydstellar.robotvocal.voice

import nl.lloydstellar.robotvocal.dsp.Resampler
import nl.lloydstellar.robotvocal.render.TimedPhone
import kotlin.math.max

/**
 * Flite's kal voice: the diphone voice that read out so much 1990s software,
 * 8 kHz LPC. Flite takes our phone list with our durations and pitch, so the
 * whole song is one render and every phone sits exactly where it is planned.
 */
class FliteEngine : VoiceEngine {
    override val id = "flite"

    override val voices = listOf(
        VoiceInfo("flite/kal", "KAL", "FLITE", "The diphone voice of 1990s speech software, 8 kHz", 46),
    )

    override fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered {
        if (phones.isEmpty()) return Rendered(0, FloatArray(0))
        val rate = NativeVoices.fliteInit()
        val lead = sampleRate / 10L
        val origin = phones.first().start - lead
        val segments = segments(phones, sampleRate)
        val pcm = NativeVoices.fliteRender(segments.joinToString(" ") { it.name },
            FloatArray(segments.size) { segments[it].seconds }, FloatArray(segments.size) { segments[it].hz.toFloat() })
            ?: return Rendered(origin, FloatArray(0))
        return Rendered(origin, Resampler.convert(pcm.toFloats(), rate, sampleRate))
    }

    /** A phone or a pause as Flite gets it: its name, how long it lasts and its pitch. */
    internal class Segment(val name: String, val seconds: Float, val hz: Double)

    internal companion object {
        /** The shortest phone Flite is given, in seconds. */
        private const val SHORTEST = 0.012

        /**
         * The phones as Flite speaks them, one after the other from 100 ms before
         * the first. Every phone ends on its planned end: one that had to take
         * Flite's shortest length makes the next one that much shorter, so a
         * fast line never drifts behind the beat.
         */
        fun segments(phones: List<TimedPhone>, sampleRate: Int): List<Segment> {
            val out = ArrayList<Segment>()
            val lead = sampleRate / 10L
            val shortest = (SHORTEST * sampleRate).toLong()
            fun add(name: String, frames: Long, hz: Double) {
                out += Segment(name, frames.toFloat() / sampleRate, hz)
            }
            add("pau", lead, phones.first().pitchHz)
            var t = phones.first().start
            for (p in phones) {
                if (p.start > t + sampleRate / 1000) {
                    add("pau", p.start - t, p.pitchHz)
                    t = p.start
                }
                val frames = max(shortest, p.end - t)
                add(p.ph.name.lowercase(), frames, p.pitchHz)
                t += frames
            }
            add("pau", lead, phones.last().pitchHz)
            return out
        }
    }
}
