package nl.lloydstellar.robotvocal.export

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

enum class WavFormat(val bits: Int, val float: Boolean) { PCM16(16, false), PCM24(24, false), FLOAT32(32, true) }

/**
 * Writes mono WAV files: PCM 16 or 24 bit, or 32-bit float. A loop gets an
 * `acid` chunk with its tempo and number of beats (REAPER and others read it;
 * the rest skip it), and every file a LIST INFO chunk with its title and the
 * name of the app.
 */
object WavWriter {

    class Meta(val title: String, val bpm: Double? = null, val beats: Int? = null, val loop: Boolean = false)

    fun write(samples: FloatArray, sampleRate: Int, format: WavFormat, meta: Meta, out: OutputStream) {
        val bytesPerSample = format.bits / 8
        val dataSize = samples.size * bytesPerSample
        val info = infoChunk(meta.title)
        val acid = if (meta.bpm != null && meta.beats != null) acidChunk(meta.bpm, meta.beats, meta.loop) else ByteArray(0)
        val fmtSize = 16
        val riffSize = 4 + (8 + fmtSize) + acid.size + info.size + (8 + dataSize + dataSize % 2)

        val head = ByteBuffer.allocate(12 + 8 + fmtSize).order(ByteOrder.LITTLE_ENDIAN)
        head.put("RIFF".toByteArray()).putInt(riffSize).put("WAVE".toByteArray())
        head.put("fmt ".toByteArray()).putInt(fmtSize)
        head.putShort((if (format.float) 3 else 1).toShort()).putShort(1)
        head.putInt(sampleRate).putInt(sampleRate * bytesPerSample).putShort(bytesPerSample.toShort()).putShort(format.bits.toShort())
        out.write(head.array())
        out.write(acid)
        out.write(info)

        val dataHead = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).put("data".toByteArray()).putInt(dataSize)
        out.write(dataHead.array())
        val chunk = ByteBuffer.allocate(4096 * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val v = s.coerceIn(-1f, 1f)
            when (format) {
                WavFormat.PCM16 -> chunk.putShort((v * 32767f).roundToInt().toShort())
                WavFormat.PCM24 -> {
                    val x = (v * 8_388_607f).roundToInt()
                    chunk.put((x and 0xFF).toByte()).put((x shr 8 and 0xFF).toByte()).put((x shr 16 and 0xFF).toByte())
                }
                WavFormat.FLOAT32 -> chunk.putFloat(v)
            }
            if (!chunk.hasRemaining()) {
                out.write(chunk.array(), 0, chunk.position())
                chunk.clear()
            }
        }
        out.write(chunk.array(), 0, chunk.position())
        if (dataSize % 2 == 1) out.write(0)
    }

    fun bytes(samples: FloatArray, sampleRate: Int, format: WavFormat, meta: Meta): ByteArray =
        ByteArrayOutputStream().also { write(samples, sampleRate, format, meta, it) }.toByteArray()

    /** acid: flags, root note, two unknown fields, beats, meter 4/4, tempo. */
    private fun acidChunk(bpm: Double, beats: Int, loop: Boolean): ByteArray {
        val b = ByteBuffer.allocate(8 + 24).order(ByteOrder.LITTLE_ENDIAN)
        b.put("acid".toByteArray()).putInt(24)
        b.putInt(if (loop) 0x00 else 0x01)  // bit 0: one-shot
        b.putShort(60).putShort(0x8000.toShort()).putFloat(0f)
        b.putInt(beats).putShort(4).putShort(4).putFloat(bpm.toFloat())
        return b.array()
    }

    private fun infoChunk(title: String): ByteArray {
        val fields = listOf("INAM" to title.take(120), "ISFT" to "Robot Vocal")
        val parts = fields.map { (id, text) ->
            val value = (text + "\u0000").toByteArray(Charsets.UTF_8)
            val padded = value.size + value.size % 2
            ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN).put(id.toByteArray()).putInt(value.size).put(value).array()
        }
        val body = parts.sumOf { it.size }
        val b = ByteBuffer.allocate(12 + body).order(ByteOrder.LITTLE_ENDIAN)
        b.put("LIST".toByteArray()).putInt(4 + body).put("INFO".toByteArray())
        parts.forEach { b.put(it) }
        return b.array()
    }
}
