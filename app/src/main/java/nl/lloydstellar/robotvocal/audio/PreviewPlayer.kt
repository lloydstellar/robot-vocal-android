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
 * The beat exists only here: export code never sees it.
 *
 * New material (after an edit) replaces the old at the same loop position,
 * so playback runs on while the song changes.
 */
class PreviewPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var vocal = arrayOf(FloatArray(0))
    @Volatile private var beat = FloatArray(0)
    @Volatile var beatLevel = 0.7f
    @Volatile var loop = true

    var playing by mutableStateOf(false)
        private set

    private var track: AudioTrack? = null
    private var writer: Thread? = null
    @Volatile private var generation = 0
    @Volatile private var startFrame = 0L

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ATTRIBUTES)
        .setOnAudioFocusChangeListener { if (it < 0) main.post { stop() } }
        .build()

    /** Vocal (one or two channels) and beat of the same length, a whole number of bars. */
    fun setMaterial(vocal: Array<FloatArray>, beat: FloatArray) {
        this.vocal = vocal
        this.beat = beat
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
        startFrame = fromFrame
        val gen = ++generation
        t.play()
        playing = true
        writer = Thread({ write(t, gen, fromFrame) }, "preview").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun write(t: AudioTrack, gen: Int, from: Long) {
        val block = FloatArray(BLOCK * 2)
        var pos = from
        while (gen == generation) {
            val v = vocal
            val b = beat
            val length = maxOf(v[0].size, b.size)
            if (length == 0) break
            val level = beatLevel
            val left = v[0]
            val right = v[v.size - 1]
            var n = 0
            while (n < BLOCK) {
                if (pos >= length) {
                    if (!loop) break
                    pos = 0
                }
                val i = pos.toInt()
                /* the beat sits in the middle; a stereo vocal keeps its sides */
                val drums = if (i < b.size) b[i] * level else 0f
                block[2 * n] = (if (i < left.size) left[i] else 0f) + drums
                block[2 * n + 1] = (if (i < right.size) right[i] else 0f) + drums
                n++
                pos++
            }
            if (n > 0 && t.write(block, 0, 2 * n, AudioTrack.WRITE_BLOCKING) < 0) break
            if (n < BLOCK) {
                /* the end without looping: let the buffer play out, then stop */
                Thread.sleep((t.bufferSizeInFrames * 1000L / RATE) + 50)
                main.post { if (gen == generation) stop() }
                break
            }
        }
    }

    /** The loop position the listener hears now, in frames; null when stopped. */
    fun position(): Long? {
        val t = track ?: return null
        val length = maxOf(vocal[0].size, beat.size)
        if (length == 0) return null
        return (startFrame + (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL)) % length
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
        private val ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
