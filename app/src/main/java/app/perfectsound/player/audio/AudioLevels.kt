package app.perfectsound.player.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live band levels shared between whatever produces audio and the equalizer UI. */
object AudioLevels {
    enum class Source { None, Local, Capture }

    data class Snapshot(
        val source: Source = Source.None,
        val bands: FloatArray = FloatArray(SpectrumAnalyzer.BAND_CENTERS_HZ.size),
        /** Overall loudness in dBFS; stays near the floor when capture is blocked or nothing plays. */
        val rmsDb: Float = -96f,
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
