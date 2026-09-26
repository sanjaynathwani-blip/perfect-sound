package app.perfectsound.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumAnalyzerTest {
    private val rate = 48_000
    private val analyzer = SpectrumAnalyzer(rate, fftSize = 4096)

    private fun sine(hz: Float, amplitude: Float = 1f) =
        FloatArray(4096) { i -> amplitude * sin(2 * PI * hz * i / rate).toFloat() }

    @Test
    fun `each band center peaks in its own band`() {
        SpectrumAnalyzer.BAND_CENTERS_HZ.forEachIndexed { band, hz ->
            val levels = analyzer.analyze(sine(hz))!!.bands
            val loudest = levels.indices.maxBy { levels[it] }
            assertEquals("tone at $hz Hz", band, loudest)
        }
    }

    @Test
    fun `full scale sine reads near the top`() {
        val levels = analyzer.analyze(sine(1000f))!!.bands
        assertTrue("1 kHz level ${levels[4]}", levels[4] > 0.9f)
    }

    @Test
    fun `quieter signal reads lower`() {
        val loud = analyzer.analyze(sine(1000f, 1f))!!.bands[4]
        val quiet = analyzer.analyze(sine(1000f, 0.1f))!!.bands[4] // -20 dB
        assertEquals(20f / 60f, loud - quiet, 0.03f)
    }

    @Test
    fun `silence is zero and short input is rejected`() {
        val silence = analyzer.analyze(FloatArray(4096))!!
        assertTrue(silence.bands.all { it == 0f } && silence.bars.all { it == 0f })
        assertNull(analyzer.analyze(FloatArray(100)))
    }

    @Test
    fun `bars rise from low to high with the tone`() {
        val peaks = listOf(50f, 200f, 1000f, 5000f, 15000f).map { hz ->
            val bars = analyzer.analyze(sine(hz))!!.bars
            assertEquals(SpectrumAnalyzer.BAR_COUNT, bars.size)
            assertTrue("$hz Hz reads ${bars.max()}", bars.max() > 0.85f)
            bars.indices.maxBy { bars[it] }
        }
        assertEquals(peaks.sorted(), peaks)
        assertEquals(peaks.distinct(), peaks)
    }

    @Test
    fun `scope trace starts on a rising zero crossing`() {
        val phaseShifted = FloatArray(4096) { i -> sin(2 * PI * 440 * i / rate + 1.0).toFloat() }
        val trace = SpectrumAnalyzer.scopeTrace(phaseShifted)
        assertEquals(512, trace.size)
        assertEquals(0f, trace[0], 0.1f)
        assertTrue(trace[5] > trace[0])
    }
}
