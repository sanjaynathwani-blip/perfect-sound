package app.perfectsound.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.ui.theme.PsColors
import kotlin.math.roundToInt

/** What the visualizer draws. Clicking it moves to the next mode. */
enum class VisMode(val label: String) {
    Bands("BANDS"),
    Spectrum("SPECTRUM"),
    Scope("SCOPE"),
    Waterfall("WATERFALL");

    fun next(): VisMode = entries[(ordinal + 1) % entries.size]
}

/** Draws the live [levels] in the chosen [mode]. Everything is drawn at device resolution, so it stays sharp. */
@Composable
fun Visualizer(mode: VisMode, levels: AudioLevels.Snapshot, modifier: Modifier = Modifier) {
    when (mode) {
        VisMode.Bands -> EqLevelBars(levels.bands, modifier)
        VisMode.Spectrum -> EqLevelBars(levels.bars, modifier)
        VisMode.Scope -> Oscilloscope(levels.wave, modifier)
        VisMode.Waterfall -> Waterfall(levels.bars, modifier)
    }
}

@Composable
private fun Oscilloscope(wave: FloatArray, modifier: Modifier) {
    val path = remember { Path() }
    Canvas(modifier) {
        val mid = size.height / 2
        drawLine(PsColors.LcdDim, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
        path.reset()
        if (wave.isEmpty()) {
            path.moveTo(0f, mid)
            path.lineTo(size.width, mid)
        } else {
            val dx = size.width / (wave.size - 1)
            wave.forEachIndexed { i, v ->
                val y = mid - (v * ScopeGain).coerceIn(-1f, 1f) * mid * 0.92f
                if (i == 0) path.moveTo(0f, y) else path.lineTo(i * dx, y)
            }
        }
        val px = density
        // A soft glow under a crisp trace, like a phosphor screen.
        drawPath(path, PsColors.Lcd.copy(alpha = 0.18f), style = Stroke(5f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, PsColors.Lcd, style = Stroke(1.5f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private const val ScopeGain = 1.4f

/**
 * A scrolling spectrogram: time runs right to left, low notes at the bottom. It is kept in a bitmap the
 * size of the canvas in device pixels and drawn 1:1, so it never gets scaled or blurred.
 */
@Composable
private fun Waterfall(bars: FloatArray, modifier: Modifier) {
    val state = remember { WaterfallState() }
    Canvas(modifier) { state.draw(this, bars) }
}

private class WaterfallState {
    private var bitmap: Bitmap? = null
    private var column = IntArray(0)
    /** Next column to write; also the oldest column on screen. */
    private var head = 0
    private var lastBars: FloatArray? = null

    fun draw(scope: DrawScope, bars: FloatArray) = with(scope) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return
        val bmp = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Palette[0])
            bitmap = it
            column = IntArray(h)
            head = 0
        }
        // Only new audio advances the picture; redraws for other reasons leave it still.
        if (bars !== lastBars) {
            lastBars = bars
            fillColumn(bars, h)
            repeat(density.roundToInt().coerceAtLeast(1)) {
                bmp.setPixels(column, 0, 1, head, 0, 1, h)
                head = (head + 1) % w
            }
        }
        val image = bmp.asImageBitmap()
        // Oldest (head..w) on the left, newest (0..head) on the right.
        drawImage(image, IntOffset(head, 0), IntSize(w - head, h), IntOffset.Zero, IntSize(w - head, h), filterQuality = FilterQuality.None)
        if (head > 0) drawImage(image, IntOffset.Zero, IntSize(head, h), IntOffset(w - head, 0), IntSize(head, h), filterQuality = FilterQuality.None)
    }

    /** One pixel column, bottom row = lowest bar, interpolating between bars. */
    private fun fillColumn(bars: FloatArray, h: Int) {
        val n = bars.size
        for (y in 0 until h) {
            val level = if (n == 0) 0f else {
                val pos = (1f - y / (h - 1f).coerceAtLeast(1f)) * (n - 1)
                val i = pos.toInt().coerceAtMost(n - 2).coerceAtLeast(0)
                val t = pos - i
                if (n == 1) bars[0] else bars[i] * (1 - t) + bars[i + 1] * t
            }
            column[y] = Palette[(level * (Palette.size - 1)).roundToInt().coerceIn(0, Palette.size - 1)]
        }
    }
}

/** Heat colours in the LCD palette: dark, green, yellow, amber, then white at full level. */
private val Palette: IntArray = run {
    val stops = listOf(
        0f to PsColors.LcdBackground,
        0.3f to PsColors.LcdBackground,
        0.5f to Color(0xFF0F4A20),
        0.68f to PsColors.LcdMid,
        0.82f to Color(0xFFE8F54A),
        0.93f to PsColors.Amber,
        1f to Color.White,
    )
    IntArray(256) { i ->
        val x = i / 255f
        val k = stops.indexOfLast { it.first <= x }.coerceAtMost(stops.size - 2)
        val (x0, c0) = stops[k]
        val (x1, c1) = stops[k + 1]
        lerp(c0, c1, ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)).toArgb()
    }
}
