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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.perfectsound.player.audio.AudioLevels
import app.perfectsound.player.ui.theme.PsColors
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** What the visualizer draws. Clicking it moves to the next mode. */
enum class VisMode(val label: String) {
    Bands("BANDS"),
    Spectrum("SPECTRUM"),
    Leds("LED"),
    Curve("CURVE"),
    Scope("SCOPE"),
    Vu("VU METER");

    fun next(): VisMode = entries[(ordinal + 1) % entries.size]
}

/** Draws the live [levels] in the chosen [mode]. Everything is vector drawn, so it stays sharp at any size. */
@Composable
fun Visualizer(mode: VisMode, levels: AudioLevels.Snapshot, modifier: Modifier = Modifier) {
    when (mode) {
        VisMode.Bands -> EqLevelBars(levels.bands, modifier)
        VisMode.Spectrum -> EqLevelBars(levels.bars, modifier)
        VisMode.Leds -> LedBars(levels.bars, modifier)
        VisMode.Curve -> SpectrumCurve(levels.bars, modifier)
        VisMode.Scope -> Oscilloscope(levels.wave, modifier)
        VisMode.Vu -> VuMeters(levels.channelDb, modifier)
    }
}

private val Yellow = Color(0xFFE8F54A)
private val Red = Color(0xFFFF4A3A)

/** Hi-fi style columns of separate blocks: green, then yellow, then red at the top, with a held peak block. */
@Composable
private fun LedBars(bars: FloatArray, modifier: Modifier) {
    // 64 bars are too thin for blocks; group them into 16 columns.
    val columns = remember(bars) {
        val group = (bars.size / LedColumns).coerceAtLeast(1)
        FloatArray(LedColumns) { c -> (0 until group).maxOf { bars.getOrElse(c * group + it) { 0f } } }
    }
    val falling = rememberFallingLevels(columns, fallPerSecond = 1.2f, peakFallPerSecond = 0.25f)
    Canvas(modifier) {
        val n = falling.shown.size
        val segGap = 2.dp.toPx()
        val segments = ((size.height + segGap) / (5.dp.toPx() + segGap)).toInt().coerceAtLeast(8)
        val segH = (size.height - segGap * (segments - 1)) / segments
        val colGap = size.width * 0.2f / n
        val colW = (size.width - colGap * (n - 1)) / n
        val radius = CornerRadius(1.dp.toPx())
        for (c in 0 until n) {
            val x = c * (colW + colGap)
            val lit = (falling.shown[c] * segments).toInt()
            val peak = (falling.peaks[c] * segments).toInt().coerceAtMost(segments - 1)
            for (s in 0 until segments) {
                val fraction = s / (segments - 1f)
                val color = when {
                    fraction >= 0.85f -> Red
                    fraction >= 0.6f -> Yellow
                    else -> PsColors.Lcd
                }
                val on = s < lit || (s == peak && falling.peaks[c] > 0.02f)
                val y = size.height - (s + 1) * segH - s * segGap
                drawRoundRect(if (on) color else color.copy(alpha = 0.08f), Offset(x, y), Size(colW, segH), radius)
            }
        }
    }
}

private const val LedColumns = 16

/** A smooth glowing line over the spectrum with a fading fill underneath. */
@Composable
private fun SpectrumCurve(bars: FloatArray, modifier: Modifier) {
    val falling = rememberFallingLevels(bars, fallPerSecond = 1.0f)
    val line = remember { Path() }
    val fill = remember { Path() }
    Canvas(modifier) {
        val v = falling.shown
        if (v.size < 2) return@Canvas
        val dx = size.width / (v.size - 1)
        fun point(i: Int) = Offset(i * dx, size.height - v[i] * size.height * 0.95f)
        // Quadratic curves through the midpoints between bars give a smooth line that stays within the levels.
        line.reset()
        line.moveTo(point(0).x, point(0).y)
        for (i in 1 until v.size - 1) {
            val p = point(i)
            val next = point(i + 1)
            line.quadraticTo(p.x, p.y, (p.x + next.x) / 2, (p.y + next.y) / 2)
        }
        line.lineTo(point(v.lastIndex).x, point(v.lastIndex).y)
        fill.reset()
        fill.addPath(line)
        fill.lineTo(size.width, size.height)
        fill.lineTo(0f, size.height)
        fill.close()

        drawPath(fill, Brush.verticalGradient(listOf(PsColors.Lcd.copy(alpha = 0.45f), PsColors.Lcd.copy(alpha = 0.03f))))
        drawPath(line, PsColors.Lcd.copy(alpha = 0.2f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, Yellow, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
        // A soft glow under a crisp trace, like a phosphor screen.
        drawPath(path, PsColors.Lcd.copy(alpha = 0.18f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, PsColors.Lcd, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private const val ScopeGain = 1.4f

/** A pair of analog VU meters, left and right, with needles that swing smoothly like the real thing. */
@Composable
private fun VuMeters(channelDb: FloatArray, modifier: Modifier) {
    val target by rememberUpdatedState(channelDb)
    var needles by remember { mutableStateOf(floatArrayOf(0f, 0f)) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                // A real VU meter takes about 300 ms to settle.
                val k = 1f - exp(-dt / 0.1f)
                val old = needles
                needles = FloatArray(2) { i -> old[i] + (vuPosition(target.getOrElse(i) { -96f } - VuReferenceDbfs) - old[i]) * k }
            }
        }
    }
    val text = rememberTextMeasurer()
    Canvas(modifier) {
        // Each meter is a box of fixed shape, as big as fits, and the pair is centred in the space.
        val gap = 24.dp.toPx()
        val w = minOf((size.width - gap) / 2, size.height * VuAspect)
        val h = w / VuAspect
        val x0 = (size.width - 2 * w - gap) / 2
        val y0 = (size.height - h) / 2
        for (i in 0..1) drawVuMeter(Offset(x0 + i * (w + gap), y0), Size(w, h), needles[i], if (i == 0) "L" else "R", text)
    }
}

/** Width to height of one meter's box. */
private const val VuAspect = 1.9f

/** 0 VU sits at this many dBFS. Streaming services play at around -14 LUFS, so music hovers near 0. */
private const val VuReferenceDbfs = -14f

/** Needle position 0..1 for a level in VU (-20..+3). Like a real meter, it moves with the signal's amplitude, not its decibels. */
private fun vuPosition(vu: Float): Float {
    val low = 10f.pow(-20f / 20f)
    val high = 10f.pow(3f / 20f)
    return ((10f.pow(vu / 20f) - low) / (high - low)).coerceIn(0f, 1.04f)
}

private val VuTicks = listOf(-20, -10, -7, -5, -3, -2, -1, 0, 1, 2, 3)
private val VuLabels = setOf(-20, -10, -5, -3, 0, 3)

/**
 * One meter face. As on a real VU meter, the needle's pivot sits below the face under a dark cover,
 * so the scale is a shallow arc across the top and the whole box is used. Sizes scale with the box.
 */
private fun DrawScope.drawVuMeter(topLeft: Offset, size: Size, needle: Float, channel: String, text: TextMeasurer) {
    // A recessed window in the panel, like the meters on a tape deck: dark above, a highlight along the bottom.
    val bezel = size.height * 0.05f
    val corner = CornerRadius(bezel * 1.2f)
    drawRoundRect(Brush.verticalGradient(listOf(PsColors.BevelDark, Color(0xFF15181D)), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft, size, corner)
    drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, PsColors.BevelLight), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft, size, corner, style = Stroke(1.dp.toPx()))
    val face = Offset(topLeft.x + bezel, topLeft.y + bezel)
    drawVuFace(face, Size(size.width - 2 * bezel, size.height - 2 * bezel), needle, channel, text)
}

private fun DrawScope.drawVuFace(topLeft: Offset, size: Size, needle: Float, channel: String, text: TextMeasurer) {
    val h = size.height
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + h) {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF0C1A10), PsColors.LcdBackground), startY = topLeft.y, endY = topLeft.y + h),
            topLeft, size, CornerRadius(3.dp.toPx()),
        )
        val sweep = 42f // degrees either side of straight up
        val halfSweep = Math.toRadians(sweep.toDouble()).toFloat()
        val radius = (size.width / 2 - size.width * 0.09f) / sin(halfSweep)
        val pivot = Offset(topLeft.x + size.width / 2, topLeft.y + h * 0.24f + radius)
        fun angle(p: Float) = Math.toRadians((-90f - sweep + 2 * sweep * p).toDouble()).toFloat()
        fun at(p: Float, r: Float) = Offset(pivot.x + r * cos(angle(p)), pivot.y + r * sin(angle(p)))

        // Scale: green up to 0 VU, red above.
        val zero = vuPosition(0f)
        val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        drawArc(PsColors.LcdMid, -90f - sweep, 2 * sweep * zero, false, arcTopLeft, arcSize, style = Stroke(h * 0.012f))
        drawArc(Red, -90f - sweep + 2 * sweep * zero, 2 * sweep * (1 - zero), false, arcTopLeft, arcSize, style = Stroke(h * 0.03f))

        val label = TextStyle(fontSize = (h * 0.065f).toSp(), color = PsColors.TextDim)
        for (vu in VuTicks) {
            val p = vuPosition(vu.toFloat())
            val major = vu in VuLabels
            val color = if (vu > 0) Red else PsColors.Lcd
            drawLine(color, at(p, radius), at(p, radius + h * (if (major) 0.06f else 0.035f)), strokeWidth = h * 0.008f)
            if (major) {
                val layout = text.measure(if (vu > 0) "+$vu" else "$vu", label.copy(color = if (vu > 0) Red else PsColors.TextDim))
                val c = at(p, radius + h * 0.12f)
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - layout.size.height / 2))
            }
        }

        val name = text.measure("VU  $channel",
            TextStyle(fontSize = (h * 0.09f).toSp(), color = PsColors.TextDim, fontWeight = FontWeight.Bold, letterSpacing = (h * 0.02f).toSp()))
        val coverTop = topLeft.y + h * 0.8f
        drawText(name, topLeft = Offset(pivot.x - name.size.width / 2, coverTop - h * 0.08f - name.size.height))

        // Peak lamp lights in the red.
        val lamp = Offset(topLeft.x + size.width - h * 0.08f, topLeft.y + h * 0.08f)
        drawCircle(if (needle > zero) Red else Red.copy(alpha = 0.15f), h * 0.025f, lamp)

        // Needle, with a soft shadow, running down under the cover.
        val tip = at(needle, radius + h * 0.05f)
        val shadow = Offset(h * 0.012f, h * 0.012f)
        drawLine(Color.Black.copy(alpha = 0.5f), pivot + shadow, tip + shadow, strokeWidth = h * 0.014f, cap = StrokeCap.Round)
        drawLine(PsColors.Amber, pivot, tip, strokeWidth = h * 0.01f, cap = StrokeCap.Round)

        // The cover over the pivot.
        drawRect(Color(0xFF060A07), Offset(topLeft.x, coverTop), Size(size.width, topLeft.y + h - coverTop))
        drawLine(PsColors.LcdMid.copy(alpha = 0.4f), Offset(topLeft.x, coverTop), Offset(topLeft.x + size.width, coverTop), strokeWidth = 1.dp.toPx())

        // The glass sits a little behind the bezel: a soft shadow along the top.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), startY = topLeft.y, endY = topLeft.y + h * 0.08f),
            topLeft, Size(size.width, h * 0.08f))
    }
}
