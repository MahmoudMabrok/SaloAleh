package tools.mo3ta.salo.data.baqiyat

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.BAQIYAT_MANUAL_DAILY_CAP
import kotlin.test.Test
import kotlin.test.assertEquals

class BaqiyatStoreManualTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun addRaisesTodayCount() {
        val store = BaqiyatStore(MapSettings())
        assertEquals(100, store.addToday(day, 100))
        assertEquals(100, store.todayCount(day))
    }

    @Test fun regularCyclesAreNotCountedAgainstManualCap() {
        val store = BaqiyatStore(MapSettings())
        repeat(5) { store.incrementToday(day) }
        assertEquals(BAQIYAT_MANUAL_DAILY_CAP, store.manualRemainingToday(day))
        assertEquals(5, store.todayCount(day))
    }

    @Test fun addIsClampedToDailyCap() {
        val store = BaqiyatStore(MapSettings())
        // Attempting to add above the cap only applies the remaining allowance.
        assertEquals(BAQIYAT_MANUAL_DAILY_CAP, store.addToday(day, BAQIYAT_MANUAL_DAILY_CAP + 500))
        assertEquals(0, store.manualRemainingToday(day))
        // A further add is rejected — cap already exhausted.
        assertEquals(BAQIYAT_MANUAL_DAILY_CAP, store.addToday(day, 100))
    }

    @Test fun manualRemainingTracksUsage() {
        val store = BaqiyatStore(MapSettings())
        store.addToday(day, 300)
        assertEquals(BAQIYAT_MANUAL_DAILY_CAP - 300, store.manualRemainingToday(day))
    }

    @Test fun subtractReducesTodayCount() {
        val store = BaqiyatStore(MapSettings())
        store.addToday(day, 100)
        assertEquals(70, store.subtractToday(day, 30))
        assertEquals(70, store.todayCount(day))
    }

    @Test fun subtractFloorsAtZeroNeverNegative() {
        val store = BaqiyatStore(MapSettings())
        store.addToday(day, 40)
        assertEquals(0, store.subtractToday(day, 1000))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun subtractBelowSyncedBaselineIsPreservedAfterRemoteRefetch() {
        val store = BaqiyatStore(MapSettings())
        store.addToday(day, 80)
        store.onSyncSuccess(day, 80)
        assertEquals(50, store.subtractToday(day, 30))
        store.updateRemoteBaseline(day, 80)
        assertEquals(50, store.todayCount(day))
    }

    @Test fun subtractRefundsManualCapLedger() {
        val store = BaqiyatStore(MapSettings())
        store.addToday(day, BAQIYAT_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(day))
        store.subtractToday(day, 200)
        assertEquals(200, store.manualRemainingToday(day))
    }

    @Test fun addNonPositiveIsNoOp() {
        val store = BaqiyatStore(MapSettings())
        assertEquals(0, store.addToday(day, 0))
        assertEquals(0, store.addToday(day, -5))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun manualCapResetsOnNewDay() {
        val store = BaqiyatStore(MapSettings())
        store.addToday(day, BAQIYAT_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(day))
        val nextDay = LocalDate(2026, 7, 13)
        assertEquals(BAQIYAT_MANUAL_DAILY_CAP, store.manualRemainingToday(nextDay))
        assertEquals(50, store.addToday(nextDay, 50))
    }
}
