package nl.lloydstellar.robotvocal.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

/** Saving WAV files where music apps and DAWs find them, and sharing them. */
object Gallery {
    const val FOLDER = "Music/Robot Vocal"

    /** Writes [bytes] as `Music/Robot Vocal/[name]` through MediaStore; no storage permission needed. */
    fun saveWav(context: Context, name: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("The phone did not accept a new audio file")
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "Tin Voice 124bpm.wav": the song title and the tempo, safe for every file system. */
    fun fileName(title: String, bpm: Double, suffix: String = ""): String {
        val clean = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Robot Vocal" }
        val tempo = if (bpm % 1.0 == 0.0) "${bpm.toInt()}" else String.format(java.util.Locale.ROOT, "%.1f", bpm)
        return "$clean ${tempo}bpm$suffix.wav"
    }
}
