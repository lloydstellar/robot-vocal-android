package nl.lloydstellar.robotvocal.export

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.lloydstellar.la2l.Delivery
import nl.lloydstellar.la2l.Kind
import nl.lloydstellar.la2l.La2l
import nl.lloydstellar.la2l.Role
import nl.lloydstellar.la2l.Storage
import nl.lloydstellar.la2l.Wav
import nl.lloydstellar.la2l.ktx.pair
import nl.lloydstellar.la2l.ktx.send
import nl.lloydstellar.la2l.ktx.unpair
import nl.lloydstellar.robotvocal.BuildConfig
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.render.VocalTake

/**
 * Send to Live: the rendered vocal goes to Ableton Live through LA2L
 * (Lloyd's App2Live) as a loop of whole bars at the song's tempo, so Live
 * warps it onto its grid. Only vocal stems are sent; the example beat has no
 * way in here. The pairing token stays in LA2L's own private file and is
 * never logged.
 */
class LiveSender(private val context: Context) {
    private val client: La2l by lazy {
        val server = BuildConfig.LA2L_SERVER
        if (server.isEmpty()) La2l(context, APP_NAME)
        /* a test receiver gets its own token in memory, so testing never replaces the real pairing */
        else La2l(La2l.Options(APP_NAME).server(server).storage(Storage.memory()).appVersion(BuildConfig.VERSION_NAME))
    }

    suspend fun isPaired(): Boolean = withContext(Dispatchers.IO) { client.isPaired }

    suspend fun deviceName(): String? = withContext(Dispatchers.IO) { client.deviceName }

    /** Pairs with the computer that shows [code] in LA2L; returns its name. */
    suspend fun pair(code: String): String = client.pair(code)

    suspend fun unpair() = client.unpair()

    /** Sends the vocal (and the dry voice when [withDry]) as one package. */
    suspend fun send(title: String, take: VocalTake, withDry: Boolean, onProgress: (Double) -> Unit): Delivery {
        val beats = take.bars * 4.0
        val wet = withContext(Dispatchers.Default) { wav(Renderer.loop(take)) }
        val dry = if (withDry) withContext(Dispatchers.Default) { wav(Renderer.loop(take, dry = true)) } else null
        val name = soundName(title)
        return client.send(title = soundName(title, 60), bpm = take.bpm, onProgress = onProgress) {
            sound(name, wet, kind = Kind.LOOP, role = Role.VOCAL, lengthBeats = beats)
            if (dry != null) sound(soundName("$title dry"), dry, kind = Kind.LOOP, role = Role.VOCAL, lengthBeats = beats)
        }
    }

    /** A 32-bit float WAV in memory, mono or stereo. */
    private fun wav(channels: Array<FloatArray>): ByteArray {
        val n = channels[0].size
        val interleaved = FloatArray(n * channels.size) { channels[it % channels.size][it / channels.size] }
        return Wav.fromFloat(interleaved, Renderer.RATE, channels.size)
    }

    companion object {
        /** Live shows "Paired with Robot Vocal" and adds it to every track name. */
        const val APP_NAME = "Robot Vocal"

        /** LA2L keeps letters, digits, space and _ - ( ) . ' & + # ! in names, at most 40 characters. */
        fun soundName(text: String, max: Int = 40): String =
            text.replace('’', '\'').replace(Regex("[^\\p{L}\\p{N} _\\-().'&+#!]"), " ")
                .replace(Regex("\\s+"), " ").trim().take(max).trim().ifEmpty { "Robot vocal" }
    }
}
