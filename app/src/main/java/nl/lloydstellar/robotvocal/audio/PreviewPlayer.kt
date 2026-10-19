package nl.lloydstellar.robotvocal.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Plays the vocal loop with the example beat under it. Both go through one
 * AudioTrack and are mixed block by block, so they can never drift apart.
 * The beat exists only here: export code never sees it. A limiter at the end
 * keeps the sum of the two under full scale.
 *
 * Without [loop] it plays the vocal once, as it sounds unfolded: a pickup runs
 * up before bar 1 and what rings past the end rings out after the beat stops.
 * The loop played once would end in the middle of the pickup folded onto its
 * end, with a click.
 *
 * New material (after an edit) replaces the old at the same loop position,
 * so playback runs on while the song changes.
 */
class PreviewPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var vocal = arrayOf(FloatArray(0))
    @Volatile private var once = arrayOf(FloatArray(0))
    @Volatile private var origin = 0L
    @Volatile private var beat = FloatArray(0)
    @Volatile var beatLevel = 0.7f
    @Volatile var loop = true

    var playing by mutableStateOf(false)
        private set

    private var track: AudioTrack? = null
    private var writer: Thread? = null
    @Volatile private var generation = 0
    @Volatile private var startFrame = 0L
    /* how many times the loop went round since the start: the song frame of what is heard is the frames played minus these */
    @Volatile private var turns = 0L

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ATTRIBUTES)
        .setOnAudioFocusChangeListener { if (it < 0) main.post { stop() } }
        .build()

    /**
     * The song: [vocal] (one or two channels) as a loop and [beat] under it, of
     * the same length, a whole number of bars; and [once], the vocal as it
     * sounds played once, from song frame [origin] on.
     */
    fun setMaterial(vocal: Array<FloatArray>, beat: FloatArray, once: Array<FloatArray> = vocal, origin: Long = 0) {
        this.vocal = vocal
        this.beat = beat
        this.once = once
        this.origin = origin
    }

    fun play(fromFrame: Long = 0) {
        stop()
        if (vocal[0].isEmpty()) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(ATTRIBUTES)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(maxOf(min * 2, BLOCK * 2 * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        /* played once from the top, the pickup comes first */
        val from = if (!loop && fromFrame == 0L) minOf(0L, origin) else fromFrame
        startFrame = from
        turns = 0
        val gen = ++generation
        t.play()
        playing = true
        writer = Thread({ write(t, gen, from) }, "preview").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun write(t: AudioTrack, gen: Int, from: Long) {
        val block = FloatArray(BLOCK * 2)
        val limiter = Limiter(RATE)
        /* the song frame: inside the loop while looping; from before bar 1 to past the end when played once */
        var pos = from
        while (gen == generation) {
            val v = vocal
            val o = once
            val start = origin
            val b = beat
            val length = maxOf(v[0].size, b.size).toLong()
            if (length == 0L) break
            val level = beatLevel
            var n = 0
            var done = false
            while (n < BLOCK) {
                var l: Float
                var r: Float
                if (loop) {
                    if (pos >= length || pos < 0) {
                        turns += Math.floorDiv(pos, length)
                        pos = Math.floorMod(pos, length)
                    }
                    val i = pos.toInt()
                    l = v[0].getOrElse(i) { 0f }
                    r = v[v.size - 1].getOrElse(i) { 0f }
                } else {
                    val j = pos - start
                    if (j >= o[0].size && pos >= b.size) {
                        done = true
                        break
                    }
                    l = if (j >= 0 && j < o[0].size) o[0][j.toInt()] else 0f
                    r = if (j >= 0 && j < o[o.size - 1].size) o[o.size - 1][j.toInt()] else 0f
                }
                /* the beat sits in the middle; a stereo vocal keeps its sides. Played once, its last 5 ms fade, so its end does not click */
                if (pos >= 0 && pos < b.size) {
                    val fade = if (loop) 1f else minOf(1f, (b.size - pos) / FADE.toFloat())
                    l += b[pos.toInt()] * level * fade
                    r += b[pos.toInt()] * level * fade
                }
                block[2 * n] = l
                block[2 * n + 1] = r
                n++
                pos++
            }
            limiter.process(block, n)
            if (n > 0 && t.write(block, 0, 2 * n, AudioTrack.WRITE_BLOCKING) < 0) break
            if (done) {
                /* the end without looping: let the buffer play out, then stop */
                Thread.sleep((t.bufferSizeInFrames * 1000L / RATE) + 50)
                main.post { if (gen == generation) stop() }
                break
            }
        }
    }

    /**
     * The song frame the listener hears now; null when stopped, and when played
     * once, before bar 1 and after the end of the song, where no step sounds.
     */
    fun position(): Long? {
        val t = track ?: return null
        val length = maxOf(vocal[0].size, beat.size).toLong()
        if (length == 0L) return null
        val heard = startFrame + (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
        if (loop) return Math.floorMod(heard, length)
        return (heard - turns * length).takeIf { it in 0 until length }
    }

    fun stop() {
        generation++
        track?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
        writer = null
        if (playing) audio.abandonAudioFocusRequest(focus)
        playing = false
    }

    companion object {
        const val RATE = 48_000
        private const val BLOCK = 1024
        private const val FADE = 240
        private val ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
