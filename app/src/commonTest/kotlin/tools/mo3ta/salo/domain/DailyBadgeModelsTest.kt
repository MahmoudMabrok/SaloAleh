package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyBadgeModelsTest {

    @Test
    fun fromTapCount_belowFirst_returnsNull() {
        assertNull(DailyBadge.fromTapCount(0))
        assertNull(DailyBadge.fromTapCount(9))
    }

    @Test
    fun fromTapCount_exactThresholds() {
        assertEquals(DailyBadge.SPARK, DailyBadge.fromTapCount(10))
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromTapCount(100))
        assertEquals(DailyBadge.HEART, DailyBadge.fromTapCount(200))
        assertEquals(DailyBadge.TASBIH, DailyBadge.fromTapCount(500))
        assertEquals(DailyBadge.DOME, DailyBadge.fromTapCount(1000))
        assertEquals(DailyBadge.CRESCENT, DailyBadge.fromTapCount(2000))
        assertEquals(DailyBadge.LANTERN, DailyBadge.fromTapCount(4000))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromTapCount(5000))
        assertEquals(DailyBadge.MIHRAB, DailyBadge.fromTapCount(8000))
        assertEquals(DailyBadge.STAR, DailyBadge.fromTapCount(10000))
    }

    @Test
    fun fromTapCount_betweenThresholds_returnsLowerTier() {
        assertEquals(DailyBadge.SPARK, DailyBadge.fromTapCount(99))
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromTapCount(199))
        assertEquals(DailyBadge.HEART, DailyBadge.fromTapCount(499))
        assertEquals(DailyBadge.TASBIH, DailyBadge.fromTapCount(999))
        assertEquals(DailyBadge.DOME, DailyBadge.fromTapCount(1999))
        assertEquals(DailyBadge.CRESCENT, DailyBadge.fromTapCount(3999))
        assertEquals(DailyBadge.LANTERN, DailyBadge.fromTapCount(4999))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromTapCount(7999))
        assertEquals(DailyBadge.MIHRAB, DailyBadge.fromTapCount(9999))
    }

    @Test
    fun fromTapCount_aboveMax_returnsStar() {
        assertEquals(DailyBadge.STAR, DailyBadge.fromTapCount(20000))
    }

    @Test
    fun fromKey_validKeys() {
        assertEquals(DailyBadge.SPARK, DailyBadge.fromKey("spark"))
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromKey("sprout"))
        assertEquals(DailyBadge.LANTERN, DailyBadge.fromKey("lantern"))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromKey("crown"))
        assertEquals(DailyBadge.MIHRAB, DailyBadge.fromKey("mihrab"))
        assertEquals(DailyBadge.STAR, DailyBadge.fromKey("star"))
    }

    @Test
    fun fromKey_invalidKey_returnsNull() {
        assertNull(DailyBadge.fromKey("unknown"))
        assertNull(DailyBadge.fromKey(""))
    }
}
