package nl.lloydstellar.robotvocal.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import nl.lloydstellar.robotvocal.App
import nl.lloydstellar.robotvocal.BuildConfig

/** Who made it, what it does with your data, and the licences of everything inside. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var licences by remember { mutableStateOf(false) }
    var spokenIntro by remember { mutableStateOf(App.spokenIntro) }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Key("BACK", height = 40.dp, symbol = KeySymbol.BACK, onClick = onBack)
                Spacer(Modifier.weight(1f))
                Text("ABOUT", style = legend(19.sp, C.Legend, FontWeight.Bold, 1.2.sp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(18.dp))
                Text("Lloyd's Robot Vocal ${BuildConfig.VERSION_NAME}", style = body(17.sp, C.Legend, FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
                Paragraph("Type lyrics, pick a tempo and a voice, and a machine raps them with every syllable on a step of the beat. " +
                    "Save the vocal as a WAV file or send it to Ableton Live.")
                Paragraph("I'm Lloyd Stellar, an electro producer from Hilversum. On lloydstellar.nl you'll find more free music tools, " +
                    "games and my music.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key("LLOYDSTELLAR.NL", Modifier.weight(1f), textSize = 12.5.sp) { open("https://lloydstellar.nl/") }
                    Key("BANDCAMP", Modifier.weight(1f), textSize = 12.5.sp) { open("https://lloydstellar.bandcamp.com/") }
                }

                Section("SETTINGS")
                Row(
                    Modifier.fillMaxWidth().clickable {
                        spokenIntro = !spokenIntro
                        App.spokenIntro = spokenIntro
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Lamp(spokenIntro)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("SPOKEN INTRO", style = legend(15.sp, C.Legend, FontWeight.Bold, 1.sp))
                        Text("The robot says the name of the app at the start", style = body(13.sp, C.LegendDim))
                    }
                }

                Section("PRIVACY")
                Paragraph("No ads, no account, no tracking. Your lyrics and songs stay on this phone.")
                Paragraph("The app goes online for one thing only: when you tap SEND TO LIVE, LA2L uploads the vocal with its title " +
                    "to lloydstellar.nl for your own computer, and removes it there after the import in Live, or after 24 hours.")
                Key("LA2L PRIVACY", Modifier.fillMaxWidth(), textSize = 12.5.sp) { open("https://lloydstellar.nl/la2l/developers/privacy") }

                Section("OPEN SOURCE")
                Paragraph("Robot Vocal is free software under the GNU General Public License, version 3 or later. " +
                    "The source code is on GitHub. The name, the icon and the name Lloyd Stellar are not part of that licence.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key("SOURCE CODE", Modifier.weight(1f), textSize = 12.5.sp) { open("https://github.com/lloydstellar/robot-vocal-android") }
                    Key("LICENCES", Modifier.weight(1f), textSize = 12.5.sp) { licences = true }
                }

                Section("CREDITS")
                Paragraph("Made by Lloyd Stellar. Voices: eSpeak NG by Jonathan Duddington, Reece Dunn and others; Flite and its kal voice " +
                    "by Carnegie Mellon University; the formant voices are our own. Pronunciations from CMUdict (Carnegie Mellon " +
                    "University) and the NRL letter-to-sound rules of 1976. The chirp of the spelling chip comes from US patent " +
                    "4,209,836, as tabulated in MAME. Fonts: Barlow by Jeremy Tribby and DSEG by keshikan, under the SIL Open Font License.")
                Paragraph("Ableton and Live are trademarks of Ableton AG. LA2L is Lloyd's App2Live and is not made by Ableton.")
                Spacer(Modifier.height(24.dp))
            }
        }
        if (licences) LicenceViewer { licences = false }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    GroupRule(title)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = body(14.sp, C.LegendDim))
    Spacer(Modifier.height(8.dp))
}

/** Every licence text in assets/licenses, one after the other, over the screen. */
@Composable
private fun LicenceViewer(onClose: () -> Unit) {
    val context = LocalContext.current
    val texts = remember {
        context.assets.list("licenses").orEmpty().sorted().map { name ->
            name.removeSuffix(".txt") to context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
        }
    }
    Box(
        Modifier.fillMaxSize().background(C.Panel.copy(alpha = 0.98f))
            .clickable(remember { MutableInteractionSource() }, null) { },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LICENCES", style = legend(19.sp, C.Legend, FontWeight.Bold, 1.2.sp), modifier = Modifier.weight(1f))
                Key("CLOSE", height = 40.dp, onClick = onClose)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                texts.forEach { (name, text) ->
                    Spacer(Modifier.height(14.dp))
                    GroupRule(name.uppercase())
                    Spacer(Modifier.height(8.dp))
                    Text(text, style = body(11.sp, C.LegendDim))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
