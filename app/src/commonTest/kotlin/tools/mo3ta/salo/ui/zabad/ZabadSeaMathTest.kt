package tools.mo3ta.salo.ui.zabad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ZabadSeaMathTest {
    @Test fun freshSeaStartsAtFloor() {
        val sea = calculateZabadSea(0.hours)
        assertEquals(SEA_FLOOR, sea.waterLevel)
        assertEquals(FOAM_MIN, sea.foamCount)
        assertEquals(0f, sea.murk)
    }

    @Test fun seaCapsAtTwelveHours() {
        assertEquals(calculateZabadSea(12.hours), calculateZabadSea(24.hours))
        assertEquals(SEA_CEIL, calculateZabadSea(12.hours).waterLevel)
        assertEquals(FOAM_MAX, calculateZabadSea(12.hours).foamCount)
    }

    @Test fun accumulationIsMonotonic() {
        val early = calculateZabadSea(2.hours)
        val late = calculateZabadSea(9.hours)
        assertTrue(late.waterLevel < early.waterLevel)
        assertTrue(late.foamCount >= early.foamCount)
        assertTrue(late.murk > early.murk)
    }
}
