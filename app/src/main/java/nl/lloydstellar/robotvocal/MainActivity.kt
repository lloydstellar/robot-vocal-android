package nl.lloydstellar.robotvocal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import nl.lloydstellar.robotvocal.ui.C
import nl.lloydstellar.robotvocal.ui.IntroScreen
import nl.lloydstellar.robotvocal.ui.RobotVocalTheme
import nl.lloydstellar.robotvocal.ui.SongsScreen
import nl.lloydstellar.robotvocal.ui.StudioScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        /* light system bar icons on the dark panel, also with three-button navigation */
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { RobotVocalTheme { Root() } }
    }
}

enum class Screen { INTRO, SONGS, STUDIO }

@Composable
private fun Root() {
    var screen by rememberSaveable { mutableStateOf(Screen.INTRO) }
    if (screen == Screen.STUDIO) BackHandler { screen = Screen.SONGS }

    Box(Modifier.fillMaxSize().background(C.Panel)) {
        AnimatedContent(
            screen,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.INTRO -> IntroScreen(onDone = { screen = Screen.SONGS })
                Screen.SONGS -> SongsScreen(onOpen = { screen = Screen.STUDIO })
                Screen.STUDIO -> StudioScreen(onBack = { screen = Screen.SONGS })
            }
        }
    }
}
