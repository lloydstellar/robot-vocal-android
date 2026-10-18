package nl.lloydstellar.robotvocal

import nl.lloydstellar.robotvocal.text.Lexicon
import nl.lloydstellar.robotvocal.text.Lyrics
import java.io.File

/** The real lexicon asset, read from the source tree the way the app reads it from its assets. */
object TestData {
    val root: File = File(System.getProperty("robotvocal.root") ?: ".")
    val lexicon: Lexicon by lazy { Lexicon.read(File(root, "app/src/main/assets/${Lexicon.ASSET}").inputStream()) }
    val lyrics: Lyrics by lazy { Lyrics(lexicon) }
}
