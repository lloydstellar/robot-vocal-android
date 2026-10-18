package nl.lloydstellar.robotvocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** A saved song as the list shows it. */
class SongSummary(val title: String, val bpm: Double, val voice: String)

/** Until songs are stored, the list holds the three example songs. */
private val EXAMPLES = listOf(
    SongSummary("TIN VOICE", 124.0, "FLITE KAL"),
    SongSummary("NIGHT SHIFT", 96.0, "ESPEAK ROBOSOFT 3"),
    SongSummary("SATELLITE CALL", 140.0, "FORMANT CHIP"),
)

@Composable
fun SongsScreen(onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("LLOYD'S", style = legend(13.sp, C.LegendDim, FontWeight.SemiBold, 2.5.sp))
        Text("ROBOT VOCAL", style = legend(36.sp, C.Legend, FontWeight.Bold, 0.5.sp))
        Spacer(Modifier.height(22.dp))
        GroupRule("SONGS", trailing = "${EXAMPLES.size}")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            EXAMPLES.forEach { SongRow(it, onOpen) }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Key("ABOUT", onClick = {})
            Key("NEW SONG", Modifier.weight(1f), style = KeyStyle.AMBER, onClick = onOpen)
        }
    }
}

@Composable
private fun SongRow(song: SongSummary, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = legend(18.sp, C.Legend, FontWeight.Bold, 1.sp))
                Spacer(Modifier.height(2.dp))
                Text(song.voice, style = legend(12.sp, C.LegendDim, FontWeight.Medium, 1.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Glass(color = C.LedGlass) {
                    SegmentText(bpmCells(song.bpm), "888.8", seg7(17.sp), C.SegGhost)
                }
                Spacer(Modifier.height(3.dp))
                Text("BPM", style = legend(10.sp, C.LegendDim, spacing = 1.2.sp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Rule))
    }
}

/** A tempo for a four-digit seven-segment readout with one decimal: 96 BPM shows as " 96.0". */
fun bpmCells(bpm: Double): String =
    String.format(Locale.ROOT, "%5.1f", bpm).replace(' ', '!')
