package nl.lloydstellar.robotvocal.ui

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

private class Topic(val title: String, val text: String)

private val TOPICS = listOf(
    Topic("WORDS", "Type one line of lyrics per line. Numbers, years and initialisms are read the way rappers say them: 808 is " +
        "eight oh eight, BPM is B P M. When a word comes out wrong, type it in brackets with its syllables, the stressed one in " +
        "capitals: [ro-BOT]. The flow decides how the syllables fall on the beat; every flow names the records it is modelled on."),
    Topic("STEPS", "Every line gets one or more bars of 16 steps, the way a drum machine counts them (12 steps for triplets). " +
        "The vowel of every syllable starts exactly on its step; the consonants before it start a little earlier, so the word " +
        "sounds on the beat. Tap a step to see its syllable, then move it EARLIER or LATER: the rest of the line stays where it is. " +
        "REFLOW lets the flow place the line again. The number of bars sets how much room a line gets."),
    Topic("VOICE", "49 voices from six speech engines: our own formant voices, home computer voices that speak the way the " +
        "software of 1982 did, the retro voices of whackintalk (talking computers of the mid-1980s; some of them sing a tune over " +
        "your notes), the kal voice of Flite, SPELLER on the speech chip of a 1978 talking toy, and the voices of eSpeak NG. " +
        "The pitch sets the note the voice speaks on, and the note the vocoders and talkboxes sing."),
    Topic("EFFECT", "Twenty robot effects: vocoders after the hardware of the late 1970s, talkboxes, a speech chip at 8 kHz, ring " +
        "modulation, combs, phasers, gates in 16ths. Effects that move, move in bars, so they stay in time."),
    Topic("OUT", "SAVE WAV writes the vocal to Music/Robot Vocal: 48 kHz, mono (stereo when a stereo effect is on), exactly a whole number of bars, so it loops without " +
        "a gap. Sound that rings past the end is folded to the start, and a pickup before bar 1 to the end. The example beat is " +
        "never in the file. SEND TO LIVE puts it in Ableton Live with LA2L, warped to your Set's tempo."),
    Topic("TEMPO", "Tap the BPM display to change the tempo in steps, or tap TAP in time. Use the tempo of your Live Set: then the " +
        "vocal needs no stretching at all."),
)

/** How the app works, in a few short pages. */
@Composable
fun ManualScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Key("BACK", height = 40.dp, symbol = KeySymbol.BACK, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text("MANUAL", style = legend(19.sp, C.Legend, FontWeight.Bold, 1.2.sp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TOPICS.forEach { topic ->
                Spacer(Modifier.height(20.dp))
                GroupRule(topic.title)
                Spacer(Modifier.height(8.dp))
                Text(topic.text, style = body(15.sp, C.LegendDim))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
