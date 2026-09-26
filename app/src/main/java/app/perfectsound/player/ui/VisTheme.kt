package app.perfectsound.player.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.perfectsound.player.ui.theme.PsColors

/**
 * The visualizer's colours. Positions are 0..1: [x] across the display (bass on the left), and
 * height from the bottom. RAINBOW colours each column by where it sits instead of by height.
 */
enum class VisTheme(
    val label: String,
    /** Normal level, the lower part of a bar and the LED blocks' first zone. */
    private val low: Color,
    /** Getting loud: the top of a bar and the LEDs' second zone. */
    private val mid: Color,
    /** Near the top: the LEDs' last zone and the VU meter's red area. */
    val high: Color,
    /** The bottom of a bar. */
    private val base: Color,
    val peak: Color,
    /** The trace of the scope and the curve's glow. */
    private val trace: Color,
    /** Unlit track behind the bars. */
    val track: Color,
    /** Top of the VU meter's face. */
    val faceTint: Color,
    val needle: Color,
) {
    Green("GREEN", PsColors.Lcd, Color(0xFFE8F54A), Color(0xFFFF4A3A), PsColors.LcdMid, PsColors.Amber, PsColors.Lcd,
        Color(0xFF0B1A10), Color(0xFF0C1A10), PsColors.Amber),
    Rainbow("RAINBOW", PsColors.Lcd, Color(0xFFE8F54A), Color(0xFFFF4A3A), PsColors.LcdMid, Color.White, PsColors.Lcd,
        Color(0xFF111318), Color(0xFF14141A), Color.White),
    Red("RED", Color(0xFFD8232F), Color(0xFFFF6A3D), Color(0xFFFFD2B8), Color(0xFF6E0C12), Color(0xFFFFE3D6), Color(0xFFFF3B30),
        Color(0xFF1C0B0C), Color(0xFF1C0C0D), Color(0xFFFFE3D6)),
    Ice("ICE", Color(0xFF2A8CFF), Color(0xFF8BE6FF), Color(0xFFF2FBFF), Color(0xFF10357A), Color.White, Color(0xFF4FC3FF),
        Color(0xFF0B1220), Color(0xFF0C131E), Color.White);

    fun next(): VisTheme = entries[(ordinal + 1) % entries.size]

    private fun hue(x: Float, value: Float = 1f): Color = Color.hsv((x.coerceIn(0f, 1f) * 290f), 0.85f, value)

    /** A bar at [x], from [top] to [bottom] in pixels: brighter at the top. */
    fun barBrush(x: Float, top: Float, bottom: Float): Brush =
        if (this == Rainbow) Brush.verticalGradient(listOf(hue(x), hue(x, 0.55f)), startY = top, endY = bottom)
        else Brush.verticalGradient(listOf(mid, base), startY = top, endY = bottom)

    /** An LED block at [x], [height] of the way up the column. */
    fun led(x: Float, height: Float): Color = when {
        this == Rainbow -> if (height >= 0.85f) lerp(hue(x), Color.White, 0.35f) else hue(x)
        height >= 0.85f -> high
        height >= 0.6f -> mid
        else -> low
    }

    /** A line across the whole display, [width] pixels wide. */
    fun traceBrush(width: Float, alpha: Float = 1f): Brush =
        if (this == Rainbow) Brush.horizontalGradient(List(8) { hue(it / 7f).copy(alpha = alpha) }, startX = 0f, endX = width)
        else Brush.horizontalGradient(listOf(trace.copy(alpha = alpha), trace.copy(alpha = alpha)))

    /** The curve's bright top line. */
    fun curveBrush(width: Float): Brush = if (this == Rainbow) traceBrush(width) else Brush.horizontalGradient(listOf(mid, mid))

    /** The curve's fill, fading to nothing at the bottom. */
    fun fillBrush(width: Float, height: Float): Brush =
        if (this == Rainbow) Brush.horizontalGradient(List(8) { hue(it / 7f).copy(alpha = 0.3f) }, startX = 0f, endX = width)
        else Brush.verticalGradient(listOf(trace.copy(alpha = 0.45f), trace.copy(alpha = 0.03f)), startY = 0f, endY = height)

    /** The VU scale up to 0 VU, drawn [left]..[right]. */
    fun scaleBrush(left: Float, right: Float): Brush =
        if (this == Rainbow) Brush.horizontalGradient(List(6) { hue(it / 5f * 0.75f + 0.1f) }, startX = left, endX = right)
        else Brush.horizontalGradient(listOf(base, base))

    val scaleTick: Color get() = if (this == Rainbow) Color.White.copy(alpha = 0.8f) else low

    /** A small sample of the theme, for the dots under the COLOR button. */
    fun swatch(): Brush =
        if (this == Rainbow) Brush.sweepGradient(List(7) { hue(it / 6f) })
        else Brush.linearGradient(listOf(mid, low))
}
