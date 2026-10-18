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
        val names = ArrayList<String>()
        val durations = ArrayList<Float>()
        val pitch = ArrayList<Float>()
        val lead = sampleRate / 10L
        val origin = phones.first().start - lead
        fun add(name: String, frames: Long, hz: Double) {
            names += name
            durations += max(0.012f, frames.toFloat() / sampleRate)
            pitch += hz.toFloat()
        }
        add("pau", lead, phones.first().pitchHz)
        var t = phones.first().start
        for (p in phones) {
            if (p.start > t + sampleRate / 1000) add("pau", p.start - t, p.pitchHz)
            add(p.ph.name.lowercase(), p.end - max(p.start, t).coerceAtMost(p.end - 1), p.pitchHz)
            t = p.end
        }
        add("pau", lead, phones.last().pitchHz)
        val pcm = NativeVoices.fliteRender(names.joinToString(" "), durations.toFloatArray(), pitch.toFloatArray())
            ?: return Rendered(origin, FloatArray(0))
        return Rendered(origin, Resampler.convert(pcm.toFloats(), rate, sampleRate))
    }
}
