package app.perfectsound.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.perfectsound.player.ui.theme.PsColors

// Segments per digit, in order a (top), b, c, d (bottom), e, f, g (middle).
private val DIGITS = mapOf(
    '0' to 0b1111110, '1' to 0b0110000, '2' to 0b1101101, '3' to 0b1111001, '4' to 0b0110011,
    '5' to 0b1011011, '6' to 0b1011111, '7' to 0b1110000, '8' to 0b1111111, '9' to 0b1111011,
    '-' to 0b0000001, ' ' to 0,
)

/**
 * Seven-segment LCD text drawn as vector shapes, so it stays sharp at any size.
 * Supports digits, '-', ' ' and ':'. Unlit segments are drawn faintly, like a real LCD.
 */
@Composable
fun SevenSegment(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PsColors.Lcd,
    offColor: Color = PsColors.LcdDim,
) {
    Canvas(modifier) {
        val colons = text.count { it == ':' }
        val digits = text.length - colons
        // digit cell = 0.62 h wide, colon cell = 0.28 h wide
        val h = size.height
        val digitW = h * 0.62f
        val colonW = h * 0.28f
        val scale = minOf(1f, size.width / (digits * digitW + colons * colonW))
        var x = 0f
        for (ch in text) {
            if (ch == ':') {
                val d = h * 0.1f * scale
                val cx = x + colonW * scale / 2
                drawRect(color, Offset(cx - d / 2, h * 0.3f), Size(d, d))
                drawRect(color, Offset(cx - d / 2, h * 0.65f), Size(d, d))
                x += colonW * scale
            } else {
                drawDigit(DIGITS[ch] ?: 0, Offset(x + digitW * 0.08f * scale, 0f), digitW * 0.84f * scale, h, color, offColor)
                x += digitW * scale
            }
        }
    }
}

private fun DrawScope.drawDigit(mask: Int, origin: Offset, w: Float, h: Float, on: Color, off: Color) {
    val t = w * 0.2f // segment thickness
    val g = t * 0.12f // gap between segments
    val mid = h / 2
    fun horizontal(y: Float) = Path().apply {
        moveTo(origin.x + t / 2 + g, origin.y + y)
        lineTo(origin.x + t + g, origin.y + y - t / 2)
        lineTo(origin.x + w - t - g, origin.y + y - t / 2)
        lineTo(origin.x + w - t / 2 - g, origin.y + y)
        lineTo(origin.x + w - t - g, origin.y + y + t / 2)
        lineTo(origin.x + t + g, origin.y + y + t / 2)
        close()
    }
    fun vertical(x: Float, top: Float, bottom: Float) = Path().apply {
        moveTo(origin.x + x, origin.y + top + g)
        lineTo(origin.x + x + t / 2, origin.y + top + t / 2 + g)
        lineTo(origin.x + x + t / 2, origin.y + bottom - t / 2 - g)
        lineTo(origin.x + x, origin.y + bottom - g)
        lineTo(origin.x + x - t / 2, origin.y + bottom - t / 2 - g)
        lineTo(origin.x + x - t / 2, origin.y + top + t / 2 + g)
        close()
    }
    val left = t / 2
    val right = w - t / 2
    val segments = listOf(
        horizontal(t / 2),              // a
        vertical(right, t / 2, mid),     // b
        vertical(right, mid, h - t / 2), // c
        horizontal(h - t / 2),           // d
        vertical(left, mid, h - t / 2),  // e
        vertical(left, t / 2, mid),      // f
        horizontal(mid),                 // g
    )
    segments.forEachIndexed { i, path ->
        val lit = mask and (1 shl (6 - i)) != 0
        drawPath(path, if (lit) on else off)
    }
}
