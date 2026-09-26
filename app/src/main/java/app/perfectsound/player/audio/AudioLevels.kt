package app.perfectsound.player.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live levels and waveform shared between whatever produces audio and the visualizer. */
object AudioLevels {
    enum class Source { None, Local, Capture }

    data class Snapshot(
        val source: Source = Source.None,
        val bands: FloatArray = FloatArray(SpectrumAnalyzer.BAND_CENTERS_HZ.size),
        /** Overall loudness in dBFS; stays near the floor when capture is blocked or nothing plays. */
        val rmsDb: Float = -96f,
        /** Finer log-spaced levels for the spectrum and waterfall visualizers. */
        val bars: FloatArray = FloatArray(SpectrumAnalyzer.BAR_COUNT),
        /** A short, zero-crossing aligned stretch of the waveform (-1..1) for the oscilloscope. */
        val wave: FloatArray = FloatArray(0),
        /** Recent loudness of the left and right channels in dBFS, for the VU meters. */
        val channelDb: FloatArray = floatArrayOf(-96f, -96f),
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun publish(snapshot: Snapshot) {
        _state.value = snapshot
    }

    fun clear() {
        _state.value = Snapshot()
    }
}

/** RMS in dBFS of [count] samples whose squares add up to [sumSquares]. */
fun toDb(sumSquares: Double, count: Int): Float =
    20f * kotlin.math.log10(kotlin.math.sqrt(sumSquares / count.coerceAtLeast(1)).toFloat().coerceAtLeast(1e-5f))
