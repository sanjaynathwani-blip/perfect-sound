package app.perfectsound.player.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns mono PCM frames into levels for the ten classic equalizer bands, plus [BAR_COUNT] finer
 * log-spaced bars for the spectrum visualizers.
 *
 * Levels are normalised to 0..1 over a [floorDb]..0 dBFS range, so they can be drawn directly.
 */
class SpectrumAnalyzer(
    val sampleRate: Int,
    private val fftSize: Int = 2048,
    private val floorDb: Float = -60f,
) {
    init {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) { "fftSize must be a power of two" }
    }

    private val window = FloatArray(fftSize) { i -> (0.5 - 0.5 * cos(2 * PI * i / (fftSize - 1))).toFloat() }
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)

    // Each band covers the bins between the geometric midpoints to its neighbours.
    private val binRanges: List<IntRange> = run {
        val centers = BAND_CENTERS_HZ
        centers.indices.map { i ->
            val lowHz = if (i == 0) centers[0] / 1.5f else sqrt(centers[i - 1] * centers[i])
            val highHz = if (i == centers.lastIndex) minOf(centers[i] * 1.2f, sampleRate / 2f) else sqrt(centers[i] * centers[i + 1])
            val low = max(1, (lowHz * fftSize / sampleRate).toInt())
            val high = max(low, (highHz * fftSize / sampleRate).toInt())
            low..high
        }
    }

    // Log-spaced bars from BAR_LOW_HZ to BAR_HIGH_HZ, as fractional FFT bins. Bars narrower than a bin
    // (the low end) read the magnitude interpolated at their centre instead of a max over a range.
    private val barEdges: FloatArray = FloatArray(BAR_COUNT + 1) { i ->
        val hz = BAR_LOW_HZ * Math.pow((BAR_HIGH_HZ / BAR_LOW_HZ).toDouble(), i.toDouble() / BAR_COUNT).toFloat()
        minOf(hz, sampleRate / 2f) * fftSize / sampleRate
    }
    private val magnitudes = FloatArray(fftSize / 2 + 1)

    /** Levels from one FFT: [bands] matches [BAND_CENTERS_HZ], [bars] has [BAR_COUNT] entries. */
    class Analysis(val bands: FloatArray, val bars: FloatArray)

    /** Analyses the last [fftSize] frames of [samples], or returns null until it has that many. */
    fun analyze(samples: FloatArray): Analysis? {
        if (samples.size < fftSize) return null
        val offset = samples.size - fftSize
        for (i in 0 until fftSize) {
            re[i] = samples[offset + i] * window[i]
            im[i] = 0f
        }
        fft(re, im)

        for (bin in magnitudes.indices) magnitudes[bin] = sqrt(re[bin] * re[bin] + im[bin] * im[bin])

        val bands = FloatArray(binRanges.size) { band ->
            var peak = 0f
            for (bin in binRanges[band]) peak = max(peak, magnitudes[bin])
            level(peak)
        }
        val bars = FloatArray(BAR_COUNT) { bar ->
            val low = barEdges[bar]
            val high = barEdges[bar + 1]
            val first = ceil(low).toInt()
            val last = minOf(floor(high).toInt(), magnitudes.lastIndex)
            if (last >= first) {
                var peak = 0f
                for (bin in first..last) peak = max(peak, magnitudes[bin])
                level(peak)
            } else {
                val center = (low + high) / 2
                val i = center.toInt().coerceAtMost(magnitudes.lastIndex - 1)
                val t = center - i
                level(magnitudes[i] * (1 - t) + magnitudes[i + 1] * t)
            }
        }
        return Analysis(bands, bars)
    }

    /** Maps an FFT magnitude to 0..1. A full-scale sine through a Hann window peaks at fftSize / 4. */
    private fun level(magnitude: Float): Float {
        val db = 20f * log10(max(magnitude / (fftSize / 4f), 1e-9f))
        return ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var start = 0
            while (start < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val a = start + k
                    val b = a + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += len
            }
            len = len shl 1
        }
    }

    companion object {
        /** The ten classic equalizer bands. */
        val BAND_CENTERS_HZ = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
        val BAND_LABELS = listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K")

        /** Bars for the fine spectrum and waterfall visualizers. */
        const val BAR_COUNT = 64
        private const val BAR_LOW_HZ = 40f
        private const val BAR_HIGH_HZ = 16_000f
        /** One-pole low-pass for the scope, about 1.5 kHz at 48 kHz. */
        private const val SCOPE_SMOOTHING = 0.18f

        /**
         * A steady oscilloscope trace: [span] samples starting at a rising zero crossing near the end
         * of [frame] (so a held note doesn't jitter), low-passed so the line stays calm, and averaged
         * down to [points] values.
         */
        fun scopeTrace(frame: FloatArray, span: Int = 1024, points: Int = 256): FloatArray {
            if (frame.size < span) return FloatArray(points)
            val latest = frame.size - span
            var start = latest
            for (i in latest downTo maxOf(1, frame.size - 2 * span)) {
                if (frame[i - 1] < 0f && frame[i] >= 0f) { start = i; break }
            }
            val step = span / points
            var smooth = frame[start]
            return FloatArray(points) { p ->
                var sum = 0f
                for (k in 0 until step) {
                    smooth += (frame[start + p * step + k] - smooth) * SCOPE_SMOOTHING
                    sum += smooth
                }
                sum / step
            }
        }
    }
}
