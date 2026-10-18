package nl.lloydstellar.robotvocal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import nl.lloydstellar.robotvocal.R

/**
 * The colours of the front panel. Legends are printed in warm off-white on a
 * near-black panel; lamp and display colours appear only where hardware has
 * lamps and glass. Amber marks the one main action on a screen.
 */
object C {
    val Panel = Color(0xFF131312)
    val PanelHigh = Color(0xFF1B1B19)
    val Rule = Color(0xFF3A3934)
    val Legend = Color(0xFFEAE4D4)
    val LegendDim = Color(0xFF948F82)
    val LegendFaint = Color(0xFF5A574F)

    val KeyCap = Color(0xFF2B2A27)
    val KeyCapPressed = Color(0xFF242320)
    val KeyEdge = Color(0xFF0A0A09)
    val Ink = Color(0xFF16140F)

    val Amber = Color(0xFFFFB000)
    val AmberEdge = Color(0xFF9E6A00)

    val LedRed = Color(0xFFFF4433)
    val LedRedOff = Color(0xFF3A1612)
    val LedGreen = Color(0xFF4FE07F)
    val LedGreenOff = Color(0xFF12301D)

    /** A vacuum fluorescent display: teal segments behind dark glass, unlit segments faintly visible. */
    val Glass = Color(0xFF060F0D)
    val Vfd = Color(0xFF66F2D3)
    val VfdGhost = Color(0xFF10261F)

    /** Red seven-segment LEDs. */
    val LedGlass = Color(0xFF110605)
    val Seg = Color(0xFFFF4B38)
    val SegGhost = Color(0xFF301009)

    /** The step keys, in four groups of four like the step row of a classic drum machine. */
    val StepKeys = listOf(Color(0xFFC9482D), Color(0xFFDC7E2B), Color(0xFFDCB53A), Color(0xFFDCD4C1))
    val StepEdges = listOf(Color(0xFF6E2516), Color(0xFF7A4214), Color(0xFF7A621A), Color(0xFF77715F))
}

val LegendFont = FontFamily(
    Font(R.font.barlow_semicondensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semicondensed_bold, FontWeight.Bold),
)
val BodyFont = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)
val Seg7Font = FontFamily(Font(R.font.dseg7_classic_bold))
val Seg14Font = FontFamily(Font(R.font.dseg14_classic_regular))

/** Printed panel legends: upper case in the source, condensed, a little tracking. */
fun legend(
    size: TextUnit = 12.sp,
    color: Color = C.Legend,
    weight: FontWeight = FontWeight.SemiBold,
    spacing: TextUnit = 0.8.sp,
) = TextStyle(fontFamily = LegendFont, fontWeight = weight, fontSize = size, color = color, letterSpacing = spacing)

/** Sentences and lyrics. */
fun body(size: TextUnit = 15.sp, color: Color = C.Legend, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = BodyFont, fontWeight = weight, fontSize = size, color = color, lineHeight = size * 1.35f)

fun seg7(size: TextUnit, color: Color = C.Seg) = TextStyle(fontFamily = Seg7Font, fontSize = size, color = color)

fun seg14(size: TextUnit, color: Color = C.Vfd) = TextStyle(fontFamily = Seg14Font, fontSize = size, color = color)

@Composable
fun RobotVocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Amber, onPrimary = C.Ink, secondary = C.Legend,
            background = C.Panel, surface = C.Panel, onSurface = C.Legend, onBackground = C.Legend, error = C.LedRed,
        ),
        content = content,
    )
}
