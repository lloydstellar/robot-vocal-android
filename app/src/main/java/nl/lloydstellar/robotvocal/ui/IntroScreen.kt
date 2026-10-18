package nl.lloydstellar.robotvocal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.lloydstellar.robotvocal.App
import nl.lloydstellar.robotvocal.render.Arranger
import nl.lloydstellar.robotvocal.render.Renderer
import nl.lloydstellar.robotvocal.score.Song
import nl.lloydstellar.robotvocal.score.SongLine

private const val TITLE = "LLOYD'S ROBOT VOCAL"

/** A syllable of the title: the step it starts on, and how much of the title the display shows from then on. */
private class TitleSyllable(val step: Int, val text: String, val shownChars: Int)

/** "Lloyd's Robot Vocal" on one bar at 120 BPM. */
private val TITLE_SYLLABLES = listOf(
    TitleSyllable(0, "LLOYD'S", 7),
    TitleSyllable(2, "RO", 10),
    TitleSyllable(4, "BOT", 13),
    TitleSyllable(8, "VO", 16),
    TitleSyllable(10, "CAL", 19),
).associateBy { it.step }

private const val STEP_MS = 125L // a 16th note at 120 BPM

/** "Lloyd's Robot Vocal" by UNIT ONE, its syllables locked to the title steps, as one bar at 120 BPM. */
private fun titleLoop(): FloatArray {
    val locks = TITLE_SYLLABLES.keys.sorted().withIndex().associate { (i, step) -> i to step }
    val song = Song("intro", "Intro", 120.0, listOf(SongLine("Lloyd's Robot Vocal", bars = 1, locks = locks)), voice = "formant/unit1")
    val arrangement = Arranger.arrange(song, App.lyrics)
    return Renderer.loop(Renderer.render(song, arrangement, App.voices))
}

/**
 * The title, played once on the step row: every syllable of "Lloyd's Robot
 * Vocal" lights its step and appears on the display, the way the app places
 * lyrics. A tap anywhere goes straight on.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(-1) }
    var typed by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    val player = App.player

    LaunchedEffect(Unit) {
        delay(450)
        /* the robot says the title in time with the steps; without sound the steps run on their own */
        val loop = runCatching { withContext(Dispatchers.Default) { titleLoop() } }.getOrNull()
        val stepFrames = Renderer.RATE * STEP_MS / 1000
        if (loop != null) {
            player.loop = false
            player.setMaterial(loop, FloatArray(0))
            player.play()
        }
        val start = System.nanoTime()
        while (true) {
            val frame = player.position() ?: ((System.nanoTime() - start) / 1_000_000 * Renderer.RATE / 1000)
            val s = (frame / stepFrames).toInt()
            if (s >= 16 || (loop != null && !player.playing && frame > 0)) break
            if (s != step) {
                step = s
                TITLE_SYLLABLES[s]?.let { typed = TITLE.substring(0, it.shownChars) }
            }
            withFrameNanos { }
        }
        player.loop = true
        step = -1
        typed = TITLE
        finished = true
    }
    DisposableEffect(Unit) { onDispose { player.stop(); player.loop = true } }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDone)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text("LLOYD'S", style = legend(15.sp, C.LegendDim, FontWeight.SemiBold, 2.5.sp))
            Text("ROBOT VOCAL", style = legend(50.sp, C.Legend, FontWeight.Bold, 0.5.sp))
            Spacer(Modifier.height(22.dp))
            Glass(Modifier.fillMaxWidth()) {
                VfdLine(typed, Modifier.fillMaxWidth(), size = 19.sp)
            }
            Spacer(Modifier.height(24.dp))
            for (row in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until 8) {
                        val s = row * 8 + i
                        StepKey(
                            step = s,
                            syllable = TITLE_SYLLABLES[s]?.text,
                            hold = false,
                            lamp = s == step,
                            selected = false,
                            modifier = Modifier.weight(1f),
                            stressed = s == 2 || s == 8,
                            onClick = onDone,
                        )
                    }
                }
                if (row == 0) Spacer(Modifier.height(10.dp))
            }
        }
        AnimatedVisibility(
            finished,
            Modifier.align(Alignment.BottomCenter).padding(20.dp),
            enter = fadeIn(tween(200)),
        ) {
            Key("START", Modifier.widthIn(min = 200.dp), style = KeyStyle.AMBER, height = 54.dp, onClick = onDone)
        }
    }
}
