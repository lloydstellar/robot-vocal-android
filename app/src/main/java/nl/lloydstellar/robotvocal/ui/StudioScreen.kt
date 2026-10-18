package nl.lloydstellar.robotvocal.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Page(val label: String) { WORDS("WORDS"), STEPS("STEPS"), VOICE("VOICE"), EFFECT("EFFECT"), OUT("OUT") }

/** One placed syllable of the example line, until the real score arrives. */
private class ExampleSyllable(val text: String, val phonemes: String, val stressed: Boolean, val steps: Int, val rhyme: Boolean = false)

private val EXAMPLE_LINES = listOf(
    "I am the voice of the machine",
    "Every word right on the beat",
    "Turn the dial and push the key",
    "Robot vocal, talk to me",
)

/** Line 2 in the Robot 8ths flow: a syllable on every 8th, short and dry, the rhyme held from step 13. */
private val EXAMPLE_STEPS = mapOf(
    0 to ExampleSyllable("EV", "EH V", true, 1),
    2 to ExampleSyllable("RY", "R IY", false, 1),
    4 to ExampleSyllable("WORD", "W ER D", true, 1),
    6 to ExampleSyllable("RIGHT", "R AY T", true, 1),
    8 to ExampleSyllable("ON", "AA N", false, 1),
    10 to ExampleSyllable("THE", "DH AH", false, 1),
    12 to ExampleSyllable("BEAT", "B IY T", true, 3, rhyme = true),
)

private const val EXAMPLE_BPM = 124.0

/**
 * The workplace for one song: displays and transport on top, five pages below.
 * For now a static model of the panel; the score, the voices and the sound
 * follow in the next steps.
 */
@Composable
fun StudioScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf(Page.STEPS) }
    var playing by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(true) }
    var selected by remember { mutableIntStateOf(12) }
    var playStep by remember { mutableIntStateOf(-1) }

    LaunchedEffect(playing) {
        if (!playing) {
            playStep = -1
            return@LaunchedEffect
        }
        val stepMs = (60_000.0 / EXAMPLE_BPM / 4).toLong()
        var s = 0
        while (true) {
            playStep = s
            delay(stepMs)
            s = (s + 1) % 16
            if (s == 0 && !loop) {
                playing = false
            }
        }
    }

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
                Text("TIN VOICE", style = legend(19.sp, C.Legend, FontWeight.Bold, 1.2.sp))
                Text("FLITE KAL", style = legend(11.sp, C.LegendDim, FontWeight.Medium, 1.2.sp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Glass(Modifier.fillMaxWidth()) {
            VfdLine(EXAMPLE_LINES[1], Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Readout("BPM", bpmCells(EXAMPLE_BPM), "888.8")
            Readout("BAR", "1", "8")
            Readout("STEP", if (playStep >= 0) String.format(Locale.ROOT, "%2d", playStep + 1).replace(' ', '!') else "!!", "88")
            Spacer(Modifier.weight(1f))
            Key("TAP", Modifier.width(64.dp), height = 52.dp, onClick = {})
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("PLAY", Modifier.weight(1.5f), style = KeyStyle.AMBER, symbol = KeySymbol.PLAY, onClick = { playing = true })
            Key("STOP", Modifier.weight(1f), symbol = KeySymbol.STOP, onClick = { playing = false })
            Key("LOOP", Modifier.weight(1f), lamp = loop, onClick = { loop = !loop })
        }
        Spacer(Modifier.height(8.dp))
        BeatSelector()

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Page.entries.forEach { p ->
                ModeKey(p.label, active = p == page, modifier = Modifier.weight(1f)) { page = p }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (page) {
                Page.WORDS -> WordsPage()
                Page.STEPS -> StepsPage(selected, playStep) { selected = it }
                else -> Text("This page comes in a later build.", style = body(14.sp, C.LegendDim))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A red LED readout with its legend printed under the window. */
@Composable
private fun Readout(label: String, text: String, ghost: String) {
    Column {
        Glass(color = C.LedGlass) {
            SegmentText(text, ghost, seg7(22.sp), C.SegGhost)
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = legend(10.sp, C.LegendDim, spacing = 1.2.sp))
    }
}

@Composable
private fun BeatSelector() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("", Modifier.width(46.dp), height = 42.dp, symbol = KeySymbol.BACK, onClick = {})
        Glass(Modifier.weight(1f)) {
            VfdLine("BEAT ELECTRO 1", Modifier.fillMaxWidth(), size = 13.sp)
        }
        Key("", Modifier.width(46.dp), height = 42.dp, symbol = KeySymbol.NEXT, onClick = {})
    }
}

@Composable
private fun WordsPage() {
    GroupRule("LYRICS", trailing = "4 LINES")
    Spacer(Modifier.height(6.dp))
    EXAMPLE_LINES.forEachIndexed { i, line ->
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Lamp(i == 1)
            Spacer(Modifier.width(10.dp))
            Text("${i + 1}", style = legend(12.sp, C.LegendDim), modifier = Modifier.width(16.dp))
            Text(line, style = body(17.sp, if (i == 1) C.Legend else C.LegendDim))
        }
    }
}

@Composable
private fun StepsPage(selected: Int, playStep: Int, onSelect: (Int) -> Unit) {
    GroupRule("LINE 2 OF 4", trailing = "FLOW ROBOT 8THS")
    Spacer(Modifier.height(12.dp))
    for (row in 0 until 2) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 8) {
                val s = row * 8 + i
                val syllable = EXAMPLE_STEPS[s]
                StepKey(
                    step = s,
                    syllable = syllable?.text,
                    hold = isHeld(s),
                    lamp = s == playStep,
                    selected = s == selected,
                    stressed = syllable?.stressed == true,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(s) },
                )
            }
        }
        if (row == 0) Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.height(18.dp))
    val chosen = EXAMPLE_STEPS[selected]
    GroupRule("STEP ${selected + 1}")
    Spacer(Modifier.height(8.dp))
    if (chosen == null) {
        Text("No syllable starts on this step.", style = body(14.sp, C.LegendDim))
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(chosen.text, style = body(28.sp, C.Legend, FontWeight.SemiBold))
            Spacer(Modifier.width(12.dp))
            Text(chosen.phonemes, style = legend(14.sp, C.LegendDim, FontWeight.Medium, 1.5.sp), modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (chosen.stressed) Text("STRESSED", style = legend(11.sp, C.LegendDim))
            if (chosen.rhyme) Text("RHYME", style = legend(11.sp, C.LegendDim))
            Text("${chosen.steps} ${if (chosen.steps == 1) "STEP" else "STEPS"} LONG", style = legend(11.sp, C.LegendDim))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("EARLIER", Modifier.weight(1f), height = 42.dp, onClick = {})
        Key("LATER", Modifier.weight(1f), height = 42.dp, onClick = {})
        Key("LONGER", Modifier.weight(1f), height = 42.dp, onClick = {})
        Key("SHORTER", Modifier.weight(1f), height = 42.dp, onClick = {})
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key("REST", Modifier.weight(1f), height = 42.dp, onClick = {})
        Key("STUTTER", Modifier.weight(1f), height = 42.dp, onClick = {})
        Key("LOCK", Modifier.weight(1f), height = 42.dp, lamp = false, onClick = {})
    }
}

/** True when a syllable that started earlier still sounds on step [s]. */
private fun isHeld(s: Int): Boolean =
    EXAMPLE_STEPS.any { (start, syllable) -> s > start && s < start + syllable.steps }
