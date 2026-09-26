package app.perfectsound.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


/** Levels as shown on screen: they rise instantly and fall smoothly, with slower falling peaks. */
class FallingLevels(val shown: FloatArray, val peaks: FloatArray)

/** Animates [levels] every frame so bars "bounce" with the music instead of flickering. */
@Composable
fun rememberFallingLevels(levels: FloatArray, fallPerSecond: Float = 1.6f, peakFallPerSecond: Float = 0.35f): FallingLevels {
    val target by rememberUpdatedState(levels)
    var state by remember { mutableStateOf(FallingLevels(FloatArray(levels.size), FloatArray(levels.size))) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                val t = target
                val old = state
                val shown = FloatArray(t.size) { i ->
                    val current = old.shown.getOrElse(i) { 0f }
                    if (t[i] >= current) t[i] else (current - dt * fallPerSecond).coerceAtLeast(t[i])
                }
                val peaks = FloatArray(t.size) { i ->
                    val p = old.peaks.getOrElse(i) { 0f }
                    if (shown[i] >= p) shown[i] else (p - dt * peakFallPerSecond).coerceAtLeast(0f)
                }
                state = FallingLevels(shown, peaks)
            }
        }
    }
    return state
}

/**
 * Vertical level bars, one per band, with Winamp-style falling peak markers.
 * Bars rise instantly and fall smoothly so they "bounce" with the music.
 */
@Composable
fun EqLevelBars(levels: FloatArray, modifier: Modifier = Modifier, theme: VisTheme = VisTheme.Green) {
    val falling = rememberFallingLevels(levels)
    Canvas(modifier) {
        val shown = falling.shown
        val peaks = falling.peaks
        val n = shown.size
        if (n == 0) return@Canvas
        val gap = size.width * 0.18f / n
        val barWidth = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(barWidth * 0.12f)
        for (i in 0 until n) {
            val x = i * (barWidth + gap)
            drawRoundRect(theme.track, Offset(x, 0f), Size(barWidth, size.height), radius)
            val h = shown[i] * size.height
            drawRoundRect(theme.barBrush((i + 0.5f) / n, 0f, size.height), Offset(x, size.height - h), Size(barWidth, h), radius)
            val peakY = size.height - peaks[i] * size.height
            drawRect(theme.peak, Offset(x, (peakY - 3f).coerceAtLeast(0f)), Size(barWidth, 3f))
        }
    }
}
