package tools.mo3ta.dhikrmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RealFftTest {
    @Test
    fun sineEnergyLandsInExpectedBin() {
        val size = 512
        val expectedBin = 16
        val input = FloatArray(size) { index ->
            sin(2.0 * PI * expectedBin * index / size).toFloat()
        }
        val output = FloatArray(size / 2 + 1)

        RealFft(size).powerSpectrum(input, output)

        val peak = output.indices.maxBy { output[it] }
        assertEquals(expectedBin, peak)
        assertTrue(output[peak] > output.sum() * 0.99f)
    }
}
