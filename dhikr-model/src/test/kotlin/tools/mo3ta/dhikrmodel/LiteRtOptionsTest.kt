package tools.mo3ta.dhikrmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiteRtOptionsTest {
    @Test
    fun `uses portable CPU kernels instead of XNNPACK`() {
        val options = createInterpreterOptions(availableProcessors = 8)

        assertFalse(options.useXNNPACK)
        assertEquals(4, options.numThreads)
    }
}
