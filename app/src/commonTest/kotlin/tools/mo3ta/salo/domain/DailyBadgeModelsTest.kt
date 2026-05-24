package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyBadgeModelsTest {

    @Test
    fun fromTapCount_belowFirst_returnsNull() {
        assertNull(DailyBadge.fromTapCount(0))
        assertNull(DailyBadge.fromTapCount(99))
    }

    @Test
    fun fromTapCount_exactThresholds() {
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromTapCount(100))
        assertEquals(DailyBadge.HEART, DailyBadge.fromTapCount(200))
        assertEquals(DailyBadge.TASBIH, DailyBadge.fromTapCount(500))
        assertEquals(DailyBadge.DOME, DailyBadge.fromTapCount(1000))
        assertEquals(DailyBadge.CRESCENT, DailyBadge.fromTapCount(2000))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromTapCount(5000))
    }

    @Test
    fun fromTapCount_betweenThresholds_returnsLowerTier() {
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromTapCount(199))
        assertEquals(DailyBadge.HEART, DailyBadge.fromTapCount(499))
        assertEquals(DailyBadge.TASBIH, DailyBadge.fromTapCount(999))
        assertEquals(DailyBadge.DOME, DailyBadge.fromTapCount(1999))
        assertEquals(DailyBadge.CRESCENT, DailyBadge.fromTapCount(4999))
    }

    @Test
    fun fromTapCount_aboveMax_returnsCrown() {
        assertEquals(DailyBadge.CROWN, DailyBadge.fromTapCount(10000))
    }

    @Test
    fun fromKey_validKeys() {
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromKey("sprout"))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromKey("crown"))
    }

    @Test
    fun fromKey_invalidKey_returnsNull() {
        assertNull(DailyBadge.fromKey("unknown"))
        assertNull(DailyBadge.fromKey(""))
    }
}
