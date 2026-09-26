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
            val levels = analyzer.analyze(sine(hz))!!
            val loudest = levels.indices.maxBy { levels[it] }
            assertEquals("tone at $hz Hz", band, loudest)
        }
    }

    @Test
    fun `full scale sine reads near the top`() {
        val levels = analyzer.analyze(sine(1000f))!!
        assertTrue("1 kHz level ${levels[4]}", levels[4] > 0.9f)
    }

    @Test
    fun `quieter signal reads lower`() {
        val loud = analyzer.analyze(sine(1000f, 1f))!![4]
        val quiet = analyzer.analyze(sine(1000f, 0.1f))!![4] // -20 dB
        assertEquals(20f / 60f, loud - quiet, 0.03f)
    }

    @Test
    fun `silence is zero and short input is rejected`() {
        assertTrue(analyzer.analyze(FloatArray(4096))!!.all { it == 0f })
        assertNull(analyzer.analyze(FloatArray(100)))
    }
}
