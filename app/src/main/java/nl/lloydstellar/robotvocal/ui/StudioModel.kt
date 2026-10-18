package nl.lloydstellar.robotvocal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lloydstellar.robotvocal.App
import nl.lloydstellar.robotvocal.beats.BeatPattern
import nl.lloydstellar.robotvocal.beats.BeatRenderer
import nl.lloydstellar.robotvocal.render.Arrangement
import nl.lloydstellar.robotvocal.render.Arranger
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.render.VocalTake
import nl.lloydstellar.robotvocal.score.FlowPreset
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine
import nl.lloydstellar.robotvocal.score.Ticks

enum class Page(val label: String) { WORDS("WORDS"), STEPS("STEPS"), VOICE("VOICE"), EFFECT("EFFECT"), OUT("OUT") }

/**
 * The state of the Studio for one song. Every change is saved and the song is
 * rendered again in the background; while that runs, the old sound keeps
 * playing, so editing never stops the music.
 */
class StudioModel(initial: Song, private val scope: CoroutineScope) {
    var song by mutableStateOf(initial)
        private set
    var lyricsText by mutableStateOf(initial.lines.joinToString("\n") { it.text })
        private set
    var arrangement by mutableStateOf<Arrangement?>(null)
        private set
    var take by mutableStateOf<VocalTake?>(null)
        private set
    var loopAudio: FloatArray? = null
        private set
    var rendering by mutableStateOf(true)
        private set

    var page by mutableStateOf(if (initial.lines.isEmpty()) Page.WORDS else Page.STEPS)
    var line by mutableIntStateOf(0)
    var selectedSlot by mutableIntStateOf(0)
    var beatOn by mutableStateOf(true)
        private set

    private var renderJob: Job? = null
    private var saveJob: Job? = null

    /** Pairing with LA2L and sending to Live. */
    val live = LiveSession(scope)

    init {
        render(immediately = true)
    }

    val flow: FlowPreset get() = FlowPreset.byId(song.flow)

    /** Any effect on the voice, so a dry stem means something. */
    val hasEffects: Boolean get() = song.effect != "dry" || song.motion != "none" || song.space != "none"
    val swing: Double get() = song.swing ?: flow.swing

    /* ------------------------------------------------------------ edits */

    fun setLyrics(text: String) {
        lyricsText = text
        val texts = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val old = song.lines
        /* a line keeps its own settings while its text stays the same */
        val lines = texts.mapIndexed { i, t -> old.getOrNull(i)?.takeIf { it.text == t } ?: SongLine(t) }
        line = line.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        change(song.copy(lines = lines))
    }

    fun setTitle(title: String) = change(song.copy(title = title.uppercase().take(40)), rerender = false)

    fun setBpm(bpm: Double) = change(song.copy(bpm = (Math.round(bpm * 10) / 10.0).coerceIn(40.0, 240.0)))

    fun setFlow(id: String) = change(song.copy(flow = id, lines = song.lines.map { it.copy(locks = emptyMap(), bars = null) }))

    fun setVoice(id: String) = change(song.copy(voice = id, note = null))

    fun setNote(note: Int) = change(song.copy(note = note.coerceIn(28, 76)))

    fun setEffect(id: String) = change(song.copy(effect = id))

    fun setMotion(id: String) = change(song.copy(motion = id))

    fun setSpace(id: String) = change(song.copy(space = id))

    fun setBeat(id: String) {
        change(song.copy(beat = id))
    }

    fun toggleBeat() {
        beatOn = !beatOn
        App.player.beatLevel = if (beatOn) song.beatLevel.toFloat() else 0f
    }

    /** Moves the syllable on the selected slot by [delta] slots; the rest of the line stays where it is. */
    fun nudge(delta: Int) {
        val arranged = arrangement?.lines?.getOrNull(line) ?: return
        val placed = arranged.placement.placed
        val k = placed.indexOfFirst { it.slot == selectedSlot }
        if (k < 0) return
        val target = selectedSlot + delta
        val min = placed.getOrNull(k - 1)?.slot?.plus(1) ?: -arranged.preset.maxPickup.coerceAtLeast(1)
        val max = placed.getOrNull(k + 1)?.slot?.minus(1) ?: (arranged.placement.bars * arranged.placement.slotsPerBar - 1)
        if (target < min || target > max) return
        val locks = placed.associate { it.syllable to it.slot } + (placed[k].syllable to target)
        selectedSlot = target
        setLine(line) { it.copy(locks = locks, bars = arranged.placement.bars) }
    }

    /** Removes the song from the phone. */
    fun delete() {
        saveJob?.cancel()
        renderJob?.cancel()
        App.songs.delete(song.id)
    }

    /** Lets the flow place the line again. */
    fun reflow() = setLine(line) { it.copy(locks = emptyMap(), bars = null) }

    fun setBars(bars: Int) = setLine(line) { it.copy(bars = bars.coerceIn(1, 4), locks = emptyMap()) }

    private fun setLine(index: Int, edit: (SongLine) -> SongLine) {
        val lines = song.lines.toMutableList()
        if (index !in lines.indices) return
        lines[index] = edit(lines[index])
        change(song.copy(lines = lines))
    }

    private fun change(next: Song, rerender: Boolean = true) {
        song = next
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            withContext(Dispatchers.IO) { App.songs.save(next) }
        }
        if (rerender) render()
    }

    /* ------------------------------------------------------------ sound */

    private fun render(immediately: Boolean = false) {
        renderJob?.cancel()
        rendering = true
        val s = song
        renderJob = scope.launch {
            if (!immediately) delay(180)
            val result = withContext(Dispatchers.Default) {
                val arranged = Arranger.arrange(s, App.lyrics)
                val take = Renderer.render(s, arranged, App.voices)
                val loop = Renderer.loop(take)
                val swing = s.swing ?: FlowPreset.byId(s.flow).swing
                val beat = BeatRenderer.render(BeatPattern.byId(s.beat), arranged.bars, s.bpm, swing, Renderer.RATE)
                Rendered(arranged, take, loop, beat)
            }
            arrangement = result.arrangement
            take = result.take
            loopAudio = result.loop
            App.player.beatLevel = if (beatOn) s.beatLevel.toFloat() else 0f
            App.player.setMaterial(result.loop, result.beat)
            line = line.coerceIn(0, (result.arrangement.lines.size - 1).coerceAtLeast(0))
            rendering = false
        }
    }

    private class Rendered(val arrangement: Arrangement, val take: VocalTake, val loop: FloatArray, val beat: FloatArray)

    /** The line and slot that sound at loop frame [frame]. */
    fun locate(frame: Long): Pair<Int, Int>? {
        val arranged = arrangement ?: return null
        val tick = frame * song.bpm * Ticks.PPQ / (60.0 * Renderer.RATE)
        val index = arranged.lines.indexOfLast { it.startTick <= tick }.takeIf { it >= 0 } ?: return null
        val l = arranged.lines[index]
        val slot = ((tick - l.startTick) / Ticks.perSlot(l.placement.slotsPerBar)).toInt()
        return index to slot
    }
}
