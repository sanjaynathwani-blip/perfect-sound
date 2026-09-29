package app.perfectsound.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.perfectsound.player.ui.theme.PsColors

val LabelStyle = TextStyle(color = PsColors.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
val LcdTextStyle = TextStyle(color = PsColors.Lcd, fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/** The strip across the top of each panel, with grip lines either side of the title. */
@Composable
fun TitleStrip(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(18.dp).background(PsColors.TitleBar),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y1 = size.height * 0.38f
            val y2 = size.height * 0.62f
            val gap = 70.dp.toPx()
            val cx = size.width / 2
            for (y in listOf(y1, y2)) {
                drawLine(PsColors.BevelLight, Offset(6.dp.toPx(), y), Offset(cx - gap, y), 1.dp.toPx())
                drawLine(PsColors.BevelLight, Offset(cx + gap, y), Offset(size.width - 6.dp.toPx(), y), 1.dp.toPx())
            }
        }
        BasicText(title, style = LabelStyle.copy(color = PsColors.TitleText, letterSpacing = 3.sp))
    }
}

/** Bevelled push button that draws a vector [icon]. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    height: Dp = 30.dp,
    icon: DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(width, height)
            .background(if (pressed) PsColors.ButtonFacePressed else PsColors.ButtonFace, RoundedCornerShape(3.dp))
            .border(1.dp, if (pressed) PsColors.BevelDark else PsColors.BevelLight, RoundedCornerShape(3.dp))
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize().padding(horizontal = width * 0.3f, vertical = height * 0.3f)) {
            icon(if (hovered) Color.White else PsColors.Text)
        }
    }
}

/** Small text button with an LED that lights when [on], and an optional little [icon] before the label. */
@Composable
fun LedToggle(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    led: Boolean = true,
    icon: (DrawScope.(Color) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .height(20.dp)
            .background(PsColors.ButtonFace, RoundedCornerShape(3.dp))
            .border(1.dp, if (hovered && enabled) PsColors.TextDim else PsColors.BevelLight, RoundedCornerShape(3.dp))
            .hoverable(interaction)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centred, for when the button is given more width than its label needs.
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        if (led) Box(Modifier.size(6.dp).background(if (on) PsColors.Lcd else PsColors.LcdDim, CircleShape))
        if (icon != null) Canvas(Modifier.size(11.dp)) {
            icon(if (!enabled) PsColors.TextDim else if (on) PsColors.Lcd else PsColors.Text)
        }
        BasicText(label, style = LabelStyle.copy(color = if (enabled) PsColors.Text else PsColors.TextDim))
    }
}

/**
 * Horizontal slider drawn as a groove with a thumb; click to jump, drag to scrub.
 * [onChangeFinished] fires when the drag or click ends (e.g. to seek once).
 */
@Composable
fun HSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
    fill: Brush? = null,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    Canvas(
        modifier
            .height(14.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)); finished() }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { change((it.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { finished() },
                    onDragCancel = { finished() },
                ) { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val grooveH = 4.dp.toPx()
        val y = (size.height - grooveH) / 2
        drawRoundRect(PsColors.BevelDark, Offset(0f, y), Size(size.width, grooveH), CornerRadius(grooveH / 2))
        val v = value.coerceIn(0f, 1f)
        if (fill != null) drawRoundRect(fill, Offset(0f, y), Size(size.width * v, grooveH), CornerRadius(grooveH / 2))
        if (!enabled) return@Canvas
        val thumbW = 22.dp.toPx()
        val x = (size.width - thumbW) * v
        drawRoundRect(PsColors.ButtonFace, Offset(x, 1f), Size(thumbW, size.height - 2f), CornerRadius(2.dp.toPx()))
        drawRoundRect(PsColors.BevelLight, Offset(x, 1f), Size(thumbW, size.height - 2f), CornerRadius(2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawLine(PsColors.Lcd, Offset(x + thumbW / 2, 4.dp.toPx()), Offset(x + thumbW / 2, size.height - 4.dp.toPx()), 1.5.dp.toPx())
    }
}

/** Vertical EQ-style slider; [value] is -1 (bottom) .. +1 (top), 0 in the centre. */
@Composable
fun VSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onChange)
    var heightPx by remember { mutableStateOf(1f) }
    fun toValue(y: Float) = (1f - 2f * (y / heightPx)).coerceIn(-1f, 1f)
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(0f) }) { change(toValue(it.y)) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { change(toValue(it.y)) }) { c, _ -> change(toValue(c.position.y)) }
            },
    ) {
        heightPx = size.height
        val grooveW = 4.dp.toPx()
        val cx = size.width / 2
        drawRoundRect(PsColors.BevelDark, Offset(cx - grooveW / 2, 0f), Size(grooveW, size.height), CornerRadius(grooveW / 2))
        drawLine(PsColors.TextDim.copy(alpha = 0.5f), Offset(cx - 8.dp.toPx(), size.height / 2), Offset(cx + 8.dp.toPx(), size.height / 2), 1f)
        val thumbH = 12.dp.toPx()
        val thumbW = minOf(size.width, 20.dp.toPx())
        val y = (1f - (value.coerceIn(-1f, 1f) + 1f) / 2f) * (size.height - thumbH)
        drawRoundRect(PsColors.ButtonFace, Offset(cx - thumbW / 2, y), Size(thumbW, thumbH), CornerRadius(2.dp.toPx()))
        drawLine(PsColors.Amber, Offset(cx - thumbW / 2 + 3.dp.toPx(), y + thumbH / 2), Offset(cx + thumbW / 2 - 3.dp.toPx(), y + thumbH / 2), 1.5.dp.toPx())
    }
}
