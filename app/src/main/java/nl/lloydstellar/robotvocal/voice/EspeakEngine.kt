package nl.lloydstellar.robotvocal.voice

import android.content.Context
import nl.lloydstellar.robotvocal.dsp.Resampler
import nl.lloydstellar.robotvocal.dsp.Splice
import nl.lloydstellar.robotvocal.render.TimedPhone
import nl.lloydstellar.robotvocal.text.Ph
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * eSpeak NG and its voice variants, robosoft and klatt among them. eSpeak
 * takes no durations, so every word is rendered from our phonemes, its vowel
 * onsets are found from eSpeak's phoneme events, each syllable is moved so its
 * vowel starts on its beat, and only the middle of each vowel is made longer
 * or shorter. The consonants stay as eSpeak made them.
 */
class EspeakEngine(private val context: Context) : VoiceEngine {
    override val id = "espeak"

    private class Variant(val id: String, val name: String, val description: String, val note: Int = 45)

    private val variants = listOf(
        Variant("robosoft", "ROBOSOFT", "A buzzing sawtooth formant voice"),
        Variant("robosoft2", "ROBOSOFT 2", "Robosoft, rounder"),
        Variant("robosoft3", "ROBOSOFT 3", "Robosoft, higher and thinner", 50),
        Variant("robosoft4", "ROBOSOFT 4", "Robosoft, rough"),
        Variant("robosoft5", "ROBOSOFT 5", "Robosoft, deep", 40),
        Variant("robosoft6", "ROBOSOFT 6", "Robosoft, nasal"),
        Variant("robosoft7", "ROBOSOFT 7", "Robosoft, bright", 48),
        Variant("robosoft8", "ROBOSOFT 8", "Robosoft, hard"),
        Variant("UniRobot", "UNIROBOT", "A flat machine voice"),
        Variant("anikaRobot", "ANIKA ROBOT", "A female robot", 57),
        Variant("klatt", "KLATT 1", "The Klatt synthesizer, impulse source"),
        Variant("klatt2", "KLATT 2", "Klatt with a natural glottal source"),
        Variant("klatt3", "KLATT 3", "Klatt with a sampled source"),
        Variant("klatt4", "KLATT 4", "Klatt with a sampled source, brighter"),
        Variant("klatt5", "KLATT 5", "Klatt with a sawtooth source"),
        Variant("whisper", "WHISPER", "Only breath, no voice"),
        Variant("croak", "CROAK", "A rough, broken voice", 40),
        Variant("m3", "STANDARD M", "eSpeak's plain male voice"),
        Variant("f2", "STANDARD F", "eSpeak's plain female voice", 57),
    )

    override val voices = variants.map { VoiceInfo("$id/${it.id}", it.name, "ESPEAK NG", it.description, it.note) }

    override fun render(phones: List<TimedPhone>, voiceId: String, sampleRate: Int, seed: Int): Rendered {
        if (phones.isEmpty()) return Rendered(0, FloatArray(0))
        val rate = NativeVoices.espeak(context)
        val variant = variants.firstOrNull { "$id/${it.id}" == voiceId } ?: variants.first()
        val pitch = (50 + (phones.first().pitchHz.toMidi() - variant.note) * 4).coerceIn(0, 99)
        val pieces = ArrayList<Pair<Long, FloatArray>>()
        for (word in phones.groupBy { it.word }.values) {
            renderWord(word, "en-us+${variant.id}", pitch, rate, sampleRate)?.let { pieces += it }
        }
        if (pieces.isEmpty()) return Rendered(phones.first().start, FloatArray(0))
        val origin = pieces.minOf { it.first }
        val end = pieces.maxOf { it.first + it.second.size }
        val out = FloatArray((end - origin).toInt())
        for ((start, audio) in pieces) {
            val at = (start - origin).toInt()
            for (i in audio.indices) out[at + i] += audio[i]
        }
        return Rendered(origin, out)
    }

    /** Renders one word and fits each of its syllables to its planned phonemes. */
    private fun renderWord(word: List<TimedPhone>, voice: String, pitch: Int, rate: Int, sampleRate: Int): List<Pair<Long, FloatArray>>? {
        val events = arrayOfNulls<String>(1)
        val pcm = NativeVoices.espeakRender(voice, "[[" + espeakPhonemes(word) + "]]", SPEED, pitch, 0, events) ?: return null
        val audio = Resampler.convert(pcm.toFloats(), rate, sampleRate)
        val scale = sampleRate.toDouble() / rate
        val marks = events[0].orEmpty().lines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size == 2) Mark(parts[0], (parts[1].toLong() * scale).roundToInt()) else null
        }.filter { it.name.isNotEmpty() && !it.name.startsWith("_") }
        val vowelMarks = marks.filter { it.vowel }
        val syllables = word.groupBy { it.syllable }.values.toList()
        if (vowelMarks.size != syllables.size) {
            /* eSpeak changed the vowels: place the whole word by its first vowel */
            val first = word.first { it.vowel }
            val onset = vowelMarks.firstOrNull()?.at ?: 0
            return listOf((first.start - onset) to faded(audio))
        }
        val pieces = ArrayList<Pair<Long, FloatArray>>()
        for ((k, syllable) in syllables.withIndex()) {
            val vowel = syllable.first { it.vowel }
            val mark = vowelMarks[k]
            val index = marks.indexOf(mark)
            val onsetCount = syllable.indexOf(vowel)
            /* the syllable starts at its first onset consonant, or a little before its vowel */
            val start = (if (onsetCount > 0 && index - onsetCount >= 0 && marks.subList(index - onsetCount, index).none { it.vowel })
                marks[index - onsetCount].at else max(0, mark.at - (sampleRate * 0.03).roundToInt())).coerceAtMost(mark.at)
            val nextStart = if (k + 1 < syllables.size) {
                val nextVowelIndex = marks.indexOf(vowelMarks[k + 1])
                val nextOnset = syllables[k + 1].indexOfFirst { it.vowel }
                if (nextOnset > 0 && nextVowelIndex - nextOnset > index) marks[nextVowelIndex - nextOnset].at else vowelMarks[k + 1].at
            } else audio.size
            val vowelEnd = marks.getOrNull(index + 1)?.at?.coerceAtMost(nextStart) ?: nextStart
            if (vowelEnd <= mark.at) continue
            val head = audio.copyOfRange(start, mark.at)
            val body = Splice.fit(audio.copyOfRange(mark.at, vowelEnd), (vowel.end - vowel.start).toInt(), sampleRate)
            val plannedEnd = syllable.last().end
            val tailRoom = (plannedEnd - vowel.end).toInt().coerceAtLeast(0) + sampleRate / 100
            val tail = audio.copyOfRange(vowelEnd, min(nextStart, vowelEnd + tailRoom).coerceAtLeast(vowelEnd))
            pieces += (vowel.start - head.size) to faded(head + body + tail)
        }
        return pieces
    }

    /** A phoneme event of eSpeak; stressed vowels come with their stress mark in front ("'i:"). */
    private class Mark(val name: String, val at: Int) {
        val vowel: Boolean get() = name.trimStart('\'', ',').firstOrNull()?.let { it in "@3aAeEiIoOuUV0" } == true
    }

    private fun faded(x: FloatArray): FloatArray {
        val fade = min(96, x.size / 4)
        for (i in 0 until fade) {
            val w = i.toFloat() / fade
            x[i] *= w
            x[x.size - 1 - i] *= w
        }
        return x
    }

    private fun Double.toMidi(): Int = (69 + 12 * kotlin.math.ln(this / 440.0) / kotlin.math.ln(2.0)).roundToLong().toInt()

    companion object {
        private const val SPEED = 190

        /** Our phonemes in eSpeak's English notation, with ' before stressed vowels. */
        fun espeakPhonemes(phones: List<TimedPhone>): String = buildString {
            for (p in phones) {
                if (p.vowel && p.stressed) append('\'')
                append(SYMBOLS.getValue(p.ph).let { if (p.vowel && !p.stressed) UNSTRESSED[p.ph] ?: it else it })
            }
        }

        private val SYMBOLS = mapOf(
            Ph.AA to "A:", Ph.AE to "a", Ph.AH to "V", Ph.AO to "O:", Ph.AW to "aU", Ph.AY to "aI", Ph.EH to "E",
            Ph.ER to "3:", Ph.EY to "eI", Ph.IH to "I", Ph.IY to "i:", Ph.OW to "oU", Ph.OY to "OI", Ph.UH to "U",
            Ph.UW to "u:", Ph.B to "b", Ph.CH to "tS", Ph.D to "d", Ph.DH to "D", Ph.F to "f", Ph.G to "g",
            Ph.HH to "h", Ph.JH to "dZ", Ph.K to "k", Ph.L to "l", Ph.M to "m", Ph.N to "n", Ph.NG to "N",
            Ph.P to "p", Ph.R to "r", Ph.S to "s", Ph.SH to "S", Ph.T to "t", Ph.TH to "T", Ph.V to "v",
            Ph.W to "w", Ph.Y to "j", Ph.Z to "z", Ph.ZH to "Z",
        )
        private val UNSTRESSED = mapOf(Ph.AH to "@", Ph.ER to "3", Ph.IY to "i")
    }
}
