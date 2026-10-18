package nl.lloydstellar.robotvocal

import android.app.Application
import android.content.Context
import nl.lloydstellar.robotvocal.audio.PreviewPlayer
import nl.lloydstellar.robotvocal.data.SongStore
import nl.lloydstellar.robotvocal.text.Lexicon
import nl.lloydstellar.robotvocal.text.Lyrics
import nl.lloydstellar.robotvocal.voice.EspeakEngine
import nl.lloydstellar.robotvocal.voice.FliteEngine
import nl.lloydstellar.robotvocal.voice.VoiceRegistry
import nl.lloydstellar.robotvocal.voice.formant.FormantEngine
import java.io.File
import kotlin.concurrent.thread

/** What the whole app shares: the dictionary, the voices, the player and the songs. */
object App {
    lateinit var context: Context
        private set

    val lexicon: Lexicon by lazy { Lexicon.read(context.assets.open(Lexicon.ASSET)) }
    val lyrics: Lyrics by lazy { Lyrics(lexicon) }
    val voices: VoiceRegistry by lazy { VoiceRegistry(listOf(FormantEngine(), FliteEngine(), EspeakEngine(context))) }
    val songs: SongStore by lazy { SongStore(File(context.filesDir, "songs")) }
    val player: PreviewPlayer by lazy { PreviewPlayer(context) }

    fun init(context: Context) {
        this.context = context.applicationContext
        /* read the 2.7 MB dictionary while the intro plays */
        thread(name = "lexicon") { lyrics }
    }
}

class RobotVocalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }
}
