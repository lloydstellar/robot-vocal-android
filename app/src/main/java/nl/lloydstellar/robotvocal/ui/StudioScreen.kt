package nl.lloydstellar.robotvocal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lloydstellar.robotvocal.App
import nl.lloydstellar.robotvocal.beats.BeatPattern
import nl.lloydstellar.robotvocal.dsp.RobotPresets
import nl.lloydstellar.robotvocal.export.Gallery
import nl.lloydstellar.robotvocal.export.WavFormat
import nl.lloydstellar.robotvocal.export.WavWriter
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.score.FlowPreset
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.text.Stress
import nl.lloydstellar.robotvocal.text.arpabet
import java.util.Locale

/** The workplace for one song: displays and transport on top, five pages below. */
@Composable
fun StudioScreen(initial: Song, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(initial.id) { StudioModel(initial, scope) }
    val player = App.player
    var playing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tempoOpen by remember { mutableStateOf(false) }

    LaunchedEffect(player.playing) {
        while (player.playing) {
            withFrameNanos { }
            val located = player.position()?.let { model.locate(it) }
            playing = located
            if (located != null && located.first != model.line) model.line = located.first
        }
        playing = null
    }
    val view = LocalView.current
    DisposableEffect(player.playing) {
        view.keepScreenOn = player.playing
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    val arranged = model.arrangement?.lines?.getOrNull(model.line)
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Key("SONGS", height = 40.dp, symbol = KeySymbol.BACK, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(model.song.title.ifEmpty { "UNTITLED" }, style = legend(19.sp, C.Legend, FontWeight.Bold, 1.2.sp), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(App.voices.info(model.song.voice)?.name ?: "", style = legend(11.sp, C.LegendDim, FontWeight.Medium, 1.2.sp))
                    Spacer(Modifier.width(10.dp))
                    Lamp(model.rendering, size = 6.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("BUSY", style = legend(11.sp, C.LegendDim, FontWeight.Medium, 1.2.sp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Glass(Modifier.fillMaxWidth()) {
            val text = arranged?.lyric?.text ?: if (model.song.lines.isEmpty()) "TYPE YOUR LYRICS" else ""
            VfdLine(text, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Readout("BPM", bpmCells(model.song.bpm), "888.8", Modifier.clickable { tempoOpen = !tempoOpen })
            val bar = playing?.let { (l, slot) -> (model.arrangement?.lines?.getOrNull(l)?.startBar ?: 0) + slot.floorDiv(16) + 1 }
                ?: ((arranged?.startBar ?: 0) + 1)
            Readout("BAR", String.format(Locale.ROOT, "%2d", bar.coerceIn(0, 99)).replace(' ', '!'), "88")
            val step = playing?.second?.let { Math.floorMod(it, arranged?.placement?.slotsPerBar ?: 16) + 1 }
            Readout("STEP", step?.let { String.format(Locale.ROOT, "%2d", it).replace(' ', '!') } ?: "!!", "88")
            Spacer(Modifier.weight(1f))
            TapTempo { model.setBpm(it) }
        }
        if (tempoOpen) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-5.0 to "-5", -1.0 to "-1", -0.1 to "-0.1", 0.1 to "+0.1", 1.0 to "+1", 5.0 to "+5").forEach { (d, label) ->
                    Key(label, Modifier.weight(1f), height = 38.dp, textSize = 12.5.sp) { model.setBpm(model.song.bpm + d) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("PLAY", Modifier.weight(1.5f), style = KeyStyle.AMBER, symbol = KeySymbol.PLAY,
                enabled = model.song.lines.isNotEmpty()) { player.play() }
            Key("STOP", Modifier.weight(1f), symbol = KeySymbol.STOP) { player.stop() }
            Key("LOOP", Modifier.weight(1f), lamp = player.loop) { player.loop = !player.loop }
        }
        Spacer(Modifier.height(8.dp))
        BeatSelector(model)

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Page.entries.forEach { p -> ModeKey(p.label, active = p == model.page, modifier = Modifier.weight(1f)) { model.page = p } }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (model.page) {
                Page.WORDS -> WordsPage(model)
                Page.STEPS -> StepsPage(model, playing)
                Page.VOICE -> VoicePage(model)
                Page.EFFECT -> EffectPage(model)
                Page.OUT -> OutPage(model)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A red LED readout with its legend printed under the window. */
@Composable
private fun Readout(label: String, text: String, ghost: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Glass(color = C.LedGlass) { SegmentText(text, ghost, seg7(22.sp), C.SegGhost) }
        Spacer(Modifier.height(3.dp))
        Text(label, style = legend(10.sp, C.LegendDim, spacing = 1.2.sp))
    }
}

/** Tap tempo: the average of the last taps, after two or more. */
@Composable
private fun TapTempo(onTempo: (Double) -> Unit) {
    val taps = remember { mutableStateListOf<Long>() }
    Key("TAP", Modifier.width(64.dp), height = 52.dp) {
        val now = System.nanoTime()
        if (taps.isNotEmpty() && now - taps.last() > 2_000_000_000L) taps.clear()
        taps += now
        while (taps.size > 5) taps.removeAt(0)
        if (taps.size >= 2) {
            val beat = (taps.last() - taps.first()) / (taps.size - 1) / 1e9
            onTempo(60.0 / beat)
        }
    }
}

@Composable
private fun BeatSelector(model: StudioModel) {
    val patterns = BeatPattern.ALL
    val index = patterns.indexOfFirst { it.id == model.song.beat }.coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("", Modifier.width(46.dp), height = 42.dp, symbol = KeySymbol.BACK) {
            model.setBeat(patterns[Math.floorMod(index - 1, patterns.size)].id)
        }
        Glass(Modifier.weight(1f)) { VfdLine("BEAT " + patterns[index].name, Modifier.fillMaxWidth(), size = 13.sp) }
        Key("", Modifier.width(46.dp), height = 42.dp, symbol = KeySymbol.NEXT) {
            model.setBeat(patterns[Math.floorMod(index + 1, patterns.size)].id)
        }
        Key("BEAT", height = 42.dp, lamp = model.beatOn, textSize = 12.5.sp) { model.toggleBeat() }
    }
}

@Composable
private fun WordsPage(model: StudioModel) {
    PanelField("TITLE", model.song.title, model::setTitle, capitalization = KeyboardCapitalization.Characters)
    Spacer(Modifier.height(14.dp))
    PanelField("LYRICS, ONE LINE PER LINE", model.lyricsText, model::setLyrics, singleLine = false, minLines = 5)
    Spacer(Modifier.height(6.dp))
    Text("Type a word in brackets to set its syllables and stress: [ro-BOT].", style = body(13.sp, C.LegendDim))
    Spacer(Modifier.height(18.dp))
    GroupRule("FLOW", trailing = "${model.flow.bpm.first} TO ${model.flow.bpm.last} BPM")
    Spacer(Modifier.height(8.dp))
    val presets = FlowPreset.ALL
    val index = presets.indexOf(model.flow).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("", Modifier.width(46.dp), height = 42.dp, symbol = KeySymbol.BACK) { model.setFlow(presets[Math.floorMod(index - 1, presets.size)].id) }
        Glass(Modifier.weight(1f)) { VfdLine(model.flow.name, Modifier.fillMaxWidth(), size = 13.sp) }
        Key("", Modifier.width(46.dp), height = 42.dp, symbol = KeySymbol.NEXT) { model.setFlow(presets[Math.floorMod(index + 1, presets.size)].id) }
    }
    Spacer(Modifier.height(6.dp))
    Text("In the style of ${model.flow.echoes}.", style = body(13.sp, C.LegendDim))
}

@Composable
private fun StepsPage(model: StudioModel, playing: Pair<Int, Int>?) {
    val arrangement = model.arrangement
    val arranged = arrangement?.lines?.getOrNull(model.line)
    if (arranged == null) {
        Text(if (model.rendering) "Placing the syllables." else "Type your lyrics on the WORDS page first.", style = body(14.sp, C.LegendDim))
        return
    }
    val placement = arranged.placement
    val slots = placement.slotsPerBar
    val syllables = arranged.lyric.syllables
    val byStart = placement.placed.associateBy { it.slot }
    val locked = model.song.lines.getOrNull(model.line)?.locks?.isNotEmpty() == true
    val gate = model.song.gate ?: arranged.preset.gate
    /* a step shows a hold bar when the syllable before it still sounds for most of the step */
    fun held(slot: Int) = byStart[slot] == null && placement.placed.any { slot > it.slot && it.length * gate - (slot - it.slot) >= 0.75 }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("", Modifier.width(46.dp), height = 40.dp, symbol = KeySymbol.BACK, enabled = model.line > 0) { model.line--; model.selectedSlot = 0 }
        Text("LINE ${model.line + 1} OF ${arrangement.lines.size}", style = legend(13.sp, C.Legend, spacing = 1.4.sp), modifier = Modifier.weight(1f))
        Text("${placement.bars} ${if (placement.bars == 1) "BAR" else "BARS"}", style = legend(13.sp, C.LegendDim, spacing = 1.4.sp))
        Key("", Modifier.width(46.dp), height = 40.dp, symbol = KeySymbol.NEXT, enabled = model.line < arrangement.lines.size - 1) { model.line++; model.selectedSlot = 0 }
    }
    Spacer(Modifier.height(12.dp))

    val pickups = placement.placed.filter { it.slot < 0 }
    if (pickups.isNotEmpty()) {
        GroupRule("PICKUP")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (slot in -4 until 0) {
                val p = byStart[slot]
                StepKey(
                    step = slots + slot,
                    syllable = p?.let { syllables[it.syllable].text },
                    hold = false,
                    lamp = playing?.let { it.first == model.line && it.second == slot } == true,
                    selected = slot == model.selectedSlot,
                    stressed = p?.let { syllables[it.syllable].stress == Stress.PRIMARY } == true,
                    modifier = Modifier.weight(1f),
                ) { model.selectedSlot = slot }
            }
            repeat(4) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(12.dp))
    }

    for (bar in 0 until placement.bars) {
        if (placement.bars > 1) {
            GroupRule("BAR ${bar + 1}")
            Spacer(Modifier.height(8.dp))
        }
        val perRow = if (slots == 12) 6 else 8
        for (row in 0 until slots / perRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until perRow) {
                    val slot = bar * slots + row * perRow + i
                    val p = byStart[slot]
                    StepKey(
                        step = if (slots == 12) (row * perRow + i) * 4 / 3 else row * perRow + i,
                        syllable = p?.let { syllables[it.syllable].text },
                        hold = held(slot),
                        lamp = playing?.let { it.first == model.line && it.second == slot } == true,
                        selected = slot == model.selectedSlot,
                        stressed = p?.let { syllables[it.syllable].stress == Stress.PRIMARY } == true,
                        modifier = Modifier.weight(1f),
                    ) { model.selectedSlot = slot }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    Spacer(Modifier.height(8.dp))
    GroupRule(if (model.selectedSlot < 0) "PICKUP STEP" else "STEP ${Math.floorMod(model.selectedSlot, slots) + 1}")
    Spacer(Modifier.height(8.dp))
    val chosen = byStart[model.selectedSlot]
    if (chosen == null) {
        Text("No syllable starts on this step.", style = body(14.sp, C.LegendDim))
    } else {
        val s = syllables[chosen.syllable]
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s.text.uppercase(), style = body(28.sp, C.Legend, FontWeight.SemiBold))
            Spacer(Modifier.width(12.dp))
            Text(s.phones.arpabet().replace(Regex("\\d"), ""), style = legend(14.sp, C.LegendDim, FontWeight.Medium, 1.5.sp),
                modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(when (s.stress) { Stress.PRIMARY -> "STRESSED"; Stress.SECONDARY -> "HALF STRESSED"; Stress.NONE -> "UNSTRESSED" },
                style = legend(11.sp, C.LegendDim))
            Text("${chosen.length} ${if (chosen.length == 1) "STEP" else "STEPS"}", style = legend(11.sp, C.LegendDim))
            if (s.guessed) Text("SPELLING GUESSED", style = legend(11.sp, C.Amber))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("EARLIER", Modifier.weight(1f), height = 42.dp, enabled = chosen != null) { model.nudge(-1) }
        Key("LATER", Modifier.weight(1f), height = 42.dp, enabled = chosen != null) { model.nudge(1) }
        Key("REFLOW", Modifier.weight(1f), height = 42.dp, lamp = locked) { model.reflow() }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (bars in 1..4) Key("$bars ${if (bars == 1) "BAR" else "BARS"}", Modifier.weight(1f), height = 40.dp, textSize = 12.5.sp,
            lamp = placement.bars == bars) { model.setBars(bars) }
    }
}

@Composable
private fun VoicePage(model: StudioModel) {
    App.voices.all.groupBy { it.engine }.forEach { (engine, voices) ->
        GroupRule(engine, trailing = "${voices.size}")
        Spacer(Modifier.height(6.dp))
        voices.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clickable { model.setVoice(v.id) }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Lamp(v.id == model.song.voice)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(v.name, style = legend(15.sp, C.Legend, FontWeight.Bold, 1.sp))
                    Text(v.description, style = body(13.sp, C.LegendDim))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    val note = model.song.note ?: App.voices.info(model.song.voice)?.note ?: 48
    GroupRule("PITCH", trailing = noteName(note))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("-12", Modifier.weight(1f), height = 42.dp) { model.setNote(note - 12) }
        Key("-1", Modifier.weight(1f), height = 42.dp) { model.setNote(note - 1) }
        Key("+1", Modifier.weight(1f), height = 42.dp) { model.setNote(note + 1) }
        Key("+12", Modifier.weight(1f), height = 42.dp) { model.setNote(note + 12) }
    }
}

@Composable
private fun EffectPage(model: StudioModel) {
    GroupRule("ROBOT EFFECT", trailing = "${RobotPresets.ALL.size}")
    Spacer(Modifier.height(6.dp))
    RobotPresets.ALL.forEach { p ->
        Row(
            Modifier.fillMaxWidth().clickable { model.setEffect(p.id) }.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Lamp(p.id == model.song.effect)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(p.name, style = legend(15.sp, C.Legend, FontWeight.Bold, 1.sp))
                Text(p.description, style = body(13.sp, C.LegendDim))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("The vocoders and talkboxes sing on the note set on the VOICE page.", style = body(13.sp, C.LegendDim))
}

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** MIDI note 48 is C3. */
fun noteName(midi: Int) = NOTE_NAMES[Math.floorMod(midi, 12)] + (midi / 12 - 1)

@Composable
private fun OutPage(model: StudioModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(WavFormat.PCM24) }
    var withDry by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val take = model.take

    GroupRule("WAV FILE", trailing = take?.let { "${it.bars * 4} BEATS" })
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(WavFormat.PCM24 to "24-BIT", WavFormat.PCM16 to "16-BIT", WavFormat.FLOAT32 to "32 FLOAT").forEach { (f, label) ->
            Key(label, Modifier.weight(1f), height = 40.dp, textSize = 12.5.sp, lamp = format == f) { format = f }
        }
    }
    Spacer(Modifier.height(8.dp))
    Key("DRY STEM TOO", Modifier.fillMaxWidth(), height = 40.dp, textSize = 12.5.sp, lamp = withDry,
        enabled = model.song.effect != "dry") { withDry = !withDry }
    Spacer(Modifier.height(8.dp))
    Text("48 kHz, mono, exactly ${take?.bars ?: 0} ${if (take?.bars == 1) "bar" else "bars"} at ${bpmText(model.song.bpm)} BPM. " +
        "It loops without a gap; the example beat is not in the file.", style = body(13.sp, C.LegendDim))
    Spacer(Modifier.height(12.dp))

    fun export(share: Boolean) {
        val audio = model.loopAudio ?: return
        val t = model.take ?: return
        busy = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val meta = WavWriter.Meta(model.song.title, model.song.bpm, t.bars * 4, loop = true)
                    val name = Gallery.fileName(model.song.title, model.song.bpm)
                    val uri = Gallery.saveWav(context, name, WavWriter.bytes(audio, Renderer.RATE, format, meta))
                    if (withDry && model.song.effect != "dry") {
                        val dryName = Gallery.fileName(model.song.title, model.song.bpm, " dry")
                        Gallery.saveWav(context, dryName, WavWriter.bytes(Renderer.loop(t, dry = true), Renderer.RATE, format, meta))
                    }
                    name to uri
                }
            }
            busy = false
            result.onSuccess { (name, uri) ->
                status = "Saved in ${Gallery.FOLDER} as \"$name\"."
                if (share) Gallery.share(context, uri)
            }.onFailure { status = "Saving failed: ${it.message}" }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("SAVE WAV", Modifier.weight(1f), enabled = !busy && !model.rendering && take != null) { export(share = false) }
        Key("SHARE", Modifier.weight(1f), enabled = !busy && !model.rendering && take != null) { export(share = true) }
    }
    status?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = body(13.sp, if (it.startsWith("Saving failed")) C.LedRed else C.LegendDim))
    }
    Spacer(Modifier.height(22.dp))
    LiveSection(model, withDry)
}

/** Send to Live with LA2L: pair once with the code LA2L shows in Live, then one tap per send. */
@Composable
private fun LiveSection(model: StudioModel, withDry: Boolean) {
    val live = model.live
    var code by remember { mutableStateOf("") }
    GroupRule("SEND TO LIVE")
    Spacer(Modifier.height(8.dp))
    Text("Sends your vocal to Ableton Live with LA2L, as a loop at ${bpmText(model.song.bpm)} BPM.", style = body(13.sp, C.LegendDim))
    Spacer(Modifier.height(10.dp))
    when (live.paired) {
        null -> Unit
        false -> {
            Text("In Live, click Pair app in LA2L and type the code it shows.", style = body(14.sp, C.Legend))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelField("CODE", code, { code = it.uppercase().take(9) }, Modifier.weight(1f), capitalization = KeyboardCapitalization.Characters)
                Key("PAIR", Modifier.width(96.dp), enabled = !live.busy && code.count { it.isLetterOrDigit() } == 6) { live.pair(code) }
            }
        }
        true -> {
            Text("Paired with ${live.device ?: "your computer"}.", style = body(14.sp, C.Legend))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("SEND TO LIVE", Modifier.weight(1f), enabled = !live.busy && !model.rendering && model.take != null) {
                    model.take?.let { live.send(model.song.title, it, withDry && model.song.effect != "dry") }
                }
                Key("UNPAIR", enabled = !live.busy, textSize = 12.5.sp) { live.unpair() }
            }
        }
    }
    live.status?.let {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lamp(it.startsWith("In Live"), color = C.LedGreen, off = C.LedGreenOff)
            Spacer(Modifier.width(8.dp))
            Text(it, style = body(14.sp, C.Legend))
        }
    }
    live.error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = body(14.sp, C.LedRed))
    }
    Spacer(Modifier.height(14.dp))
    Text("Ableton and Live are trademarks of Ableton AG, and LA2L is not made by Ableton.", style = body(12.sp, C.LegendFaint))
}

private fun bpmText(bpm: Double) = if (bpm % 1.0 == 0.0) "${bpm.toInt()}" else String.format(Locale.ROOT, "%.1f", bpm)
