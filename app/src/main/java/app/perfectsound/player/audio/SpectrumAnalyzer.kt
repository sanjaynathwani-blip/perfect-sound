package app.perfectsound.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns mono PCM frames into levels for the ten classic equalizer bands.
 *
 * Levels are normalised to 0..1 over a [floorDb]..0 dBFS range, so they can be drawn directly.
 */
class SpectrumAnalyzer(
    private val sampleRate: Int,
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

    /** Returns one level per band in [BAND_CENTERS_HZ], or null until [samples] has [fftSize] frames. */
    fun analyze(samples: FloatArray): FloatArray? {
        if (samples.size < fftSize) return null
        val offset = samples.size - fftSize
        for (i in 0 until fftSize) {
            re[i] = samples[offset + i] * window[i]
            im[i] = 0f
        }
        fft(re, im)

        // A full-scale sine through a Hann window peaks at fftSize / 4.
        val reference = fftSize / 4f
        return FloatArray(binRanges.size) { band ->
            var peak = 0f
            for (bin in binRanges[band]) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                if (magnitude > peak) peak = magnitude
            }
            val db = 20f * log10(max(peak / reference, 1e-9f))
            ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
        }
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
    }
}
