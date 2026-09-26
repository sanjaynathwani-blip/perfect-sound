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

private val BarBottom = Color(0xFF1FB84A)
private val BarTop = Color(0xFFE8F54A)
private val PeakColor = Color(0xFFFFC640)
private val TrackColor = Color(0xFF0B1A10)

/**
 * Vertical level bars, one per band, with Winamp-style falling peak markers.
 * Bars rise instantly and fall smoothly so they "bounce" with the music.
 */
@Composable
fun EqLevelBars(levels: FloatArray, modifier: Modifier = Modifier) {
    val target by rememberUpdatedState(levels)
    var shown by remember { mutableStateOf(FloatArray(levels.size)) }
    var peaks by remember { mutableStateOf(FloatArray(levels.size)) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                val t = target
                shown = FloatArray(t.size) { i ->
                    val current = shown.getOrElse(i) { 0f }
                    if (t[i] >= current) t[i] else (current - dt * 1.6f).coerceAtLeast(t[i])
                }
                peaks = FloatArray(t.size) { i ->
                    val p = peaks.getOrElse(i) { 0f }
                    if (shown[i] >= p) shown[i] else (p - dt * 0.35f).coerceAtLeast(0f)
                }
            }
        }
    }

    Canvas(modifier) {
        val n = shown.size
        if (n == 0) return@Canvas
        val gap = size.width * 0.18f / n
        val barWidth = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(barWidth * 0.12f)
        val gradient = Brush.verticalGradient(listOf(BarTop, BarBottom), startY = 0f, endY = size.height)
        for (i in 0 until n) {
            val x = i * (barWidth + gap)
            drawRoundRect(TrackColor, Offset(x, 0f), Size(barWidth, size.height), radius)
            val h = shown[i] * size.height
            drawRoundRect(gradient, Offset(x, size.height - h), Size(barWidth, h), radius)
            val peakY = size.height - peaks[i] * size.height
            drawRect(PeakColor, Offset(x, (peakY - 3f).coerceAtLeast(0f)), Size(barWidth, 3f))
        }
    }
}
