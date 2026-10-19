package nl.lloydstellar.robotvocal.data

import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Songs as JSON files in the app's own storage, one file per song. Only the
 * choices are stored; the sound is rendered from them again.
 */
class SongStore(private val dir: File) {

    fun list(): List<Song> {
        seedExamples()
        return dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { runCatching { fromJson(JSONObject(it.readText())) }.getOrNull() }
    }

    fun load(id: String): Song? = runCatching { fromJson(JSONObject(file(id).readText())) }.getOrNull()

    fun save(song: Song) {
        dir.mkdirs()
        val tmp = File(dir, "${song.id}.tmp")
        tmp.writeText(toJson(song).toString(2))
        tmp.renameTo(file(song.id))
    }

    fun delete(id: String) {
        file(id).delete()
    }

    fun newSong(): Song {
        val number = list().size + 1
        return Song(id = UUID.randomUUID().toString(), title = "SONG $number", bpm = 120.0, lines = emptyList())
    }

    private fun file(id: String) = File(dir, "$id.json")

    /** The first time, three example songs to start from. */
    private fun seedExamples() {
        if (dir.exists()) return
        dir.mkdirs()
        EXAMPLES.reversed().forEach {
            save(it)
            Thread.sleep(5) // distinct modification times keep the order
        }
    }

    companion object {
        /** Voices that were replaced, and the voice a song with one of them gets now. */
        private val RENAMED = mapOf("formant/chip8" to "micro/home")

        val EXAMPLES = listOf(
            Song("example-tin-voice", "TIN VOICE", 124.0, listOf(
                SongLine("I am the voice of the machine"),
                SongLine("Every word right on the beat"),
                SongLine("Turn the dial, push the key"),
                SongLine("Robot vocal, talk to me"),
            ), flow = "robot8", voice = "formant/unit1", beat = "electro1"),
            Song("example-night-shift", "NIGHT SHIFT", 96.0, listOf(
                SongLine("Night shift on the factory floor"),
                SongLine("I count the sparks and then some more"),
                SongLine("The motor hums a minor key"),
                SongLine("And every bolt remembers me"),
            ), flow = "boombap", voice = "formant/deepunit", beat = "boombap"),
            Song("example-satellite-call", "SATELLITE CALL", 140.0, listOf(
                SongLine("Calling from the satellite"),
                SongLine("Seven hundred miles of night"),
                SongLine("Can you read me, can you hear"),
                SongLine("Every signal crystal clear"),
            ), flow = "halftime", voice = "micro/home", beat = "halftime"),
        )

        fun toJson(s: Song): JSONObject = JSONObject().apply {
            put("version", 1)
            put("id", s.id); put("title", s.title); put("bpm", s.bpm)
            put("flow", s.flow); put("voice", s.voice); put("effect", s.effect); put("motion", s.motion); put("space", s.space)
            put("beat", s.beat); put("beatLevel", s.beatLevel)
            s.note?.let { put("note", it) }
            s.swing?.let { put("swing", it) }
            s.gate?.let { put("gate", it) }
            put("lines", JSONArray().apply {
                s.lines.forEach { l ->
                    put(JSONObject().apply {
                        put("text", l.text)
                        l.flow?.let { put("flow", it) }
                        l.bars?.let { put("bars", it) }
                        if (l.locks.isNotEmpty()) put("locks", JSONObject().apply { l.locks.forEach { (k, v) -> put(k.toString(), v) } })
                        if (l.notes.isNotEmpty()) put("notes", JSONObject().apply { l.notes.forEach { (k, v) -> put(k.toString(), v) } })
                        if (l.stutters.isNotEmpty()) put("stutters", JSONObject().apply { l.stutters.forEach { (k, v) -> put(k.toString(), v) } })
                    })
                }
            })
        }

        private fun intMap(o: JSONObject?): Map<Int, Int> =
            o?.keys()?.asSequence()?.associate { it.toInt() to o.getInt(it) }.orEmpty()

        fun fromJson(o: JSONObject): Song {
            val lines = o.optJSONArray("lines") ?: JSONArray()
            return Song(
                id = o.getString("id"),
                title = o.optString("title", "SONG"),
                bpm = o.optDouble("bpm", 120.0).coerceIn(40.0, 240.0),
                lines = (0 until lines.length()).map { i ->
                    val l = lines.getJSONObject(i)
                    SongLine(
                        text = l.optString("text"),
                        flow = l.optString("flow").ifEmpty { null },
                        bars = if (l.has("bars")) l.getInt("bars").coerceIn(1, 8) else null,
                        locks = intMap(l.optJSONObject("locks")),
                        notes = intMap(l.optJSONObject("notes")),
                        stutters = intMap(l.optJSONObject("stutters")),
                    )
                },
                flow = o.optString("flow", "robot8"),
                voice = o.optString("voice", "formant/unit1").let { RENAMED[it] ?: it },
                effect = o.optString("effect", "dry"),
                motion = o.optString("motion", "none"),
                space = o.optString("space", "none"),
                note = if (o.has("note")) o.getInt("note") else null,
                swing = if (o.has("swing")) o.getDouble("swing") else null,
                gate = if (o.has("gate")) o.getDouble("gate") else null,
                beat = o.optString("beat", "electro1"),
                beatLevel = o.optDouble("beatLevel", 0.7),
            )
        }
    }
}
