package nl.lloydstellar.robotvocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A printed group label followed by a rule, the way hardware marks a group of controls. */
@Composable
fun GroupRule(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = legend(11.sp, C.LegendDim, spacing = 1.4.sp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(C.Rule))
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = legend(11.sp, C.LegendDim, spacing = 1.4.sp))
        }
    }
}

enum class KeyStyle { DARK, AMBER }

/** Symbols printed on transport keys, drawn rather than taken from a font so they sit exactly in the middle. */
enum class KeySymbol { PLAY, STOP, LOOP, BACK, NEXT }

/**
 * A hardware key: a cap with a darker lower edge, as if seen slightly from the
 * front. While pressed the cap sinks onto its edge. The legend is printed on
 * the cap; [lamp] adds a lamp beside it (null: no lamp).
 */
@Composable
fun Key(
    label: String,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.DARK,
    height: Dp = 46.dp,
    enabled: Boolean = true,
    lamp: Boolean? = null,
    symbol: KeySymbol? = null,
    textSize: TextUnit = 14.sp,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val amber = style == KeyStyle.AMBER && enabled
    val cap = when {
        amber -> if (pressed) Color(0xFFEBA200) else C.Amber
        pressed -> C.KeyCapPressed
        else -> C.KeyCap
    }
    val edge = if (amber) C.AmberEdge else C.KeyEdge
    val ink = when {
        !enabled -> C.LegendFaint
        amber -> C.Ink
        else -> C.Legend
    }
    Box(
        modifier
            .height(height)
            .clickable(source, null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { contentDescription = label }
            .drawBehind { drawKeyCap(cap, edge, pressed) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.offset(y = if (pressed) 1.dp else (-1).dp).padding(horizontal = if (textSize < 14.sp) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (lamp != null) {
                Lamp(lamp)
                Spacer(Modifier.width(8.dp))
            }
            if (symbol != null) {
                Box(Modifier.size(14.dp).drawBehind { drawSymbol(symbol, ink) })
                if (label.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (label.isNotEmpty()) {
                Text(label, style = legend(textSize, ink, spacing = 1.sp), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

/** The cap and its lower edge; pressed, the cap moves down and less edge shows. */
private fun DrawScope.drawKeyCap(cap: Color, edge: Color, pressed: Boolean) {
    val radius = CornerRadius(3.dp.toPx())
    val sink = if (pressed) 2.dp.toPx() else 0f
    val edgeHeight = 3.dp.toPx()
    drawRoundRect(edge, topLeft = Offset(0f, sink), size = Size(size.width, size.height - sink), cornerRadius = radius)
    drawRoundRect(cap, topLeft = Offset(0f, sink), size = Size(size.width, size.height - edgeHeight), cornerRadius = radius)
    /* the light catches the top of the cap */
    drawRect(
        Color.White.copy(alpha = 0.07f),
        topLeft = Offset(3.dp.toPx(), sink),
        size = Size(size.width - 6.dp.toPx(), 1.dp.toPx()),
    )
}

private fun DrawScope.drawSymbol(symbol: KeySymbol, ink: Color) {
    val w = size.width
    val h = size.height
    when (symbol) {
        KeySymbol.PLAY -> drawPath(
            Path().apply { moveTo(w * 0.16f, 0f); lineTo(w, h / 2f); lineTo(w * 0.16f, h); close() },
            ink,
        )
        KeySymbol.STOP -> drawRect(ink, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f))
        KeySymbol.BACK -> drawPath(
            Path().apply { moveTo(w * 0.7f, 0f); lineTo(w * 0.2f, h / 2f); lineTo(w * 0.7f, h) },
            ink,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Square),
        )
        KeySymbol.NEXT -> drawPath(
            Path().apply { moveTo(w * 0.3f, 0f); lineTo(w * 0.8f, h / 2f); lineTo(w * 0.3f, h) },
            ink,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Square),
        )
        KeySymbol.LOOP -> {
            val stroke = Stroke(width = 2.dp.toPx())
            drawRoundRect(
                ink, Offset(w * 0.05f, h * 0.2f), Size(w * 0.9f, h * 0.6f),
                CornerRadius(h * 0.3f), style = stroke,
            )
            drawPath(
                Path().apply { moveTo(w * 0.62f, h * 0.02f); lineTo(w * 0.82f, h * 0.2f); lineTo(w * 0.62f, h * 0.38f); close() },
                ink,
            )
        }
    }
}

/** A round lamp. Off, it still shows its coloured lens; on, it lights with a small reflection. */
@Composable
fun Lamp(
    on: Boolean,
    modifier: Modifier = Modifier,
    color: Color = C.LedRed,
    off: Color = C.LedRedOff,
    size: Dp = 7.dp,
) {
    Box(
        modifier.size(size).drawBehind {
            drawCircle(if (on) color else off)
            if (on) {
                drawCircle(
                    Color.White.copy(alpha = 0.45f),
                    radius = this.size.minDimension * 0.16f,
                    center = center - Offset(this.size.width * 0.14f, this.size.height * 0.14f),
                )
            }
        }
    )
}

/** A display window: dark glass in a black bezel. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    color: Color = C.Glass,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, Color.Black, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content,
    )
}

/**
 * Segment display text: [ghost] draws every segment faintly, as unlit segments
 * show on real LED and VFD displays, and [text] lights the ones in use. Both
 * strings must have the same number of character cells.
 */
@Composable
fun SegmentText(text: String, ghost: String, style: TextStyle, ghostColor: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(ghost, style = style.copy(color = ghostColor), maxLines = 1, softWrap = false)
        Text(text, style = style, maxLines = 1, softWrap = false)
    }
}

/** Characters the 14-segment font can show; anything else, and spaces, become a blank cell. */
private val VFD_CHARS = (('A'..'Z') + ('0'..'9') + listOf('\'', '-', '?', '/', '&', '+', '*', '(', ')', '<', '>', '=', '%', '$', '"')).toSet()

/**
 * Text for a 14-segment display of [cells] characters, in upper case and padded
 * with blank cells. The blank cell is '!' in the DSEG fonts: a space as wide as
 * a character.
 */
fun vfdCells(text: String, cells: Int): String {
    val out = StringBuilder()
    for (ch in text.uppercase()) {
        if (out.length == cells) break
        out.append(if (ch in VFD_CHARS) ch else '!')
    }
    while (out.length < cells) out.append('!')
    return out.toString()
}

/** A row of [cells] all-segment characters for the unlit layer of a 14-segment display. */
fun vfdGhost(cells: Int) = "~".repeat(cells)

/**
 * A 14-segment display line that fills its window: as many character cells as
 * fit at [size], the text from the left, the rest blank cells. Long text is cut
 * at the last cell, as a real display would.
 */
@Composable
fun VfdLine(text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp) {
    val style = seg14(size)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val cellWidth = remember(style) { measurer.measure("~~~~~~~~~~", style).size.width / 10f }
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val cells = (maxWidthPx / cellWidth).toInt().coerceAtLeast(1)
        SegmentText(vfdCells(text, cells), vfdGhost(cells), style, C.VfdGhost)
    }
}

/**
 * One step key of the step row. The cap has the colour of its beat group. A
 * syllable that starts on the step is printed on the cap; [hold] draws a bar
 * along the bottom when the syllable before still sounds on this step. The lamp
 * above follows the playhead.
 */
@Composable
fun StepKey(
    step: Int,
    syllable: String?,
    hold: Boolean,
    lamp: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    stressed: Boolean = false,
    /** A small mark in the corner of the cap: a note change such as "+2", or a stutter "x4". */
    badge: String? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val group = (step % 16) / 4
    val cap = C.StepKeys[group]
    val edge = C.StepEdges[group]
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Lamp(lamp, size = 6.dp)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(source, null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }
                .semantics { contentDescription = "Step ${step + 1}" + (syllable?.let { ", $it" } ?: "") }
                .drawBehind {
                    drawKeyCap(if (pressed) cap.copy(alpha = 0.85f) else cap, edge, pressed)
                    if (hold) {
                        val y = size.height - 3.dp.toPx() - 7.dp.toPx()
                        drawRect(C.Ink.copy(alpha = 0.75f), Offset(0f, y), Size(size.width, 3.dp.toPx()))
                    }
                    if (selected) {
                        drawRoundRect(
                            C.Ink, Offset(2.dp.toPx(), 2.dp.toPx()),
                            Size(size.width - 4.dp.toPx(), size.height - 7.dp.toPx()),
                            CornerRadius(2.dp.toPx()), style = Stroke(2.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (badge != null) {
                Text(badge, style = legend(9.sp, C.Ink, FontWeight.Bold, 0.sp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 3.dp))
            }
            if (syllable != null) {
                BasicText(
                    syllable.uppercase(),
                    modifier = Modifier.offset(y = if (pressed) 0.dp else (-2).dp).padding(horizontal = 3.dp),
                    style = legend(14.sp, C.Ink, if (stressed) FontWeight.Bold else FontWeight.Medium, 0.2.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${step + 1}", style = legend(10.sp, if (step % 4 == 0) C.LegendDim else C.LegendFaint, FontWeight.Medium, 0.sp))
    }
}

/** A mode key with its lamp above it, as on hardware that switches between pages. */
@Composable
fun ModeKey(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Lamp(active)
        Spacer(Modifier.height(6.dp))
        Key(label, Modifier.fillMaxWidth(), height = 38.dp, textSize = 12.5.sp, onClick = onClick)
    }
}

/**
 * A text field on the panel: a recessed strip with a printed label above it,
 * an amber cursor and the readable body font for what the user types.
 */
@Composable
fun PanelField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
) {
    Column(modifier) {
        Text(label, style = legend(11.sp, C.LegendDim, spacing = 1.4.sp))
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = body(17.sp, C.Legend),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Amber),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = capitalization),
            modifier = Modifier
                .fillMaxWidth()
                .background(C.Glass, RoundedCornerShape(2.dp))
                .border(1.dp, C.Rule, RoundedCornerShape(2.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** A question over the screen with two keys; a tap beside it means no. */
@Composable
fun ConfirmPrompt(title: String, text: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(C.Panel.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onNo),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) { }
                .background(C.PanelHigh, RoundedCornerShape(3.dp))
                .border(1.dp, C.Rule, RoundedCornerShape(3.dp))
                .padding(18.dp),
        ) {
            Text(title, style = legend(18.sp, C.Legend, FontWeight.Bold, 1.2.sp))
            Spacer(Modifier.height(10.dp))
            Text(text, style = body(14.sp, C.LegendDim))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Key("CANCEL", Modifier.weight(1f), onClick = onNo)
                Key(yes, Modifier.weight(1f), lamp = true, onClick = onYes)
            }
        }
    }
}
