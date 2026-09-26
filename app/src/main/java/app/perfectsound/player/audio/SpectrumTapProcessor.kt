package app.perfectsound.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pass-through audio processor that copies the decoded PCM into ring buffers (mixed to mono, plus
 * left and right for the VU meters) so the visualizer works for local playback without any extra permission.
 */
@OptIn(UnstableApi::class)
class SpectrumTapProcessor : BaseAudioProcessor() {

    private val ring = FloatArray(RING_SIZE)
    private val left = FloatArray(RING_SIZE)
    private val right = FloatArray(RING_SIZE)
    private var written = 0L
    private var channels = 2

    @Volatile
    var sampleRate = 44_100
        private set

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioFormat.NOT_SET // inactive
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val samples = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        synchronized(ring) {
            val frames = samples.remaining() / channels
            for (f in 0 until frames) {
                val idx = ((written + f) and RING_MASK).toInt()
                var sum = 0
                for (c in 0 until channels) {
                    val s = samples.get()
                    sum += s
                    if (c == 0) left[idx] = s / 32768f
                    if (c == 1) right[idx] = s / 32768f
                }
                if (channels == 1) right[idx] = left[idx]
                ring[idx] = sum / (channels * 32768f)
            }
            written += frames
        }
        replaceOutputBuffer(bytes).put(inputBuffer).flip()
    }

    override fun onFlush() {
        synchronized(ring) {
            ring.fill(0f)
            left.fill(0f)
            right.fill(0f)
            written = 0
        }
    }

    override fun onReset() = onFlush()

    /** Total mono frames written since the last flush (i.e. seek or track change). */
    fun framesWritten(): Long = synchronized(ring) { written }

    /** Copies the [out].size frames ending at absolute frame [end] into [out]. */
    fun read(end: Long, out: FloatArray) {
        synchronized(ring) {
            val start = end - out.size
            for (i in out.indices) {
                val idx = start + i
                out[i] = if (idx < 0 || idx >= written || written - idx > RING_SIZE) 0f
                else ring[(idx and RING_MASK).toInt()]
            }
        }
    }

    /** RMS of the left and right channels, in dBFS, over the [frames] frames ending at [end]. */
    fun channelDb(end: Long, frames: Int): FloatArray {
        val sums = DoubleArray(2)
        synchronized(ring) {
            for (idx in end - frames until end) {
                if (idx < 0 || idx >= written || written - idx > RING_SIZE) continue
                val i = (idx and RING_MASK).toInt()
                sums[0] += left[i] * left[i]
                sums[1] += right[i] * right[i]
            }
        }
        return FloatArray(2) { toDb(sums[it], frames) }
    }

    companion object {
        private const val RING_SIZE = 1 shl 17 // ~2.7 s at 48 kHz
        private const val RING_MASK = (RING_SIZE - 1).toLong()
    }
}
