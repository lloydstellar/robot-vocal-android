package nl.lloydstellar.robotvocal.voice

import android.content.Context
import java.io.File

/**
 * The speech engines written in C: Flite with its kal voice and eSpeak NG.
 * One native library; calls are serialised inside it, because both engines
 * keep global state.
 */
object NativeVoices {
    init {
        System.loadLibrary("robotvocal")
    }

    external fun fliteInit(): Int
    external fun fliteRender(phones: String, durations: FloatArray, f0: FloatArray): ShortArray?
    external fun espeakInit(dataPath: String): Int
    /** [events] gets one line per phoneme: its eSpeak name and its first sample. */
    external fun espeakRender(voice: String, text: String, speed: Int, pitch: Int, range: Int, events: Array<String?>): ShortArray?

    @Volatile private var espeakRate = 0

    /**
     * eSpeak NG reads its data from files: the English data and the voice
     * variants are copied from the assets once (1.3 MB). Returns the sample rate.
     */
    fun espeak(context: Context): Int {
        if (espeakRate > 0) return espeakRate
        synchronized(this) {
            if (espeakRate > 0) return espeakRate
            val root = File(context.noBackupFilesDir, "espeak-ng-data-1.52.0")
            if (!File(root, "phondata").exists()) copyData(context, root)
            espeakRate = espeakInit(root.absolutePath)
            return espeakRate
        }
    }

    private fun copyData(context: Context, root: File) {
        val tmp = File(root.parentFile, root.name + ".tmp").apply { deleteRecursively(); mkdirs() }
        fun copy(asset: String, target: File) {
            val children = context.assets.list(asset).orEmpty()
            if (children.isEmpty()) {
                target.parentFile?.mkdirs()
                context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
            } else children.forEach { copy("$asset/$it", File(target, it)) }
        }
        copy("espeak-ng-data", tmp)
        /* the variants live in voices/!v in eSpeak's tree; the asset folder has a plainer name */
        File(tmp, "variants").renameTo(File(tmp, "voices/!v").apply { parentFile?.mkdirs() })
        root.deleteRecursively()
        tmp.renameTo(root)
    }
}

fun ShortArray.toFloats(): FloatArray = FloatArray(size) { this[it] / 32768f }
