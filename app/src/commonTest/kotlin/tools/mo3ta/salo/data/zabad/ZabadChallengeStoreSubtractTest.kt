package tools.mo3ta.salo.data.zabad

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ZabadChallengeStoreSubtractTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun subtractReducesTodayCount() {
        val store = ZabadChallengeStore(MapSettings())
        store.addToday(day, 100)
        assertEquals(60, store.subtractToday(day, 40))
        assertEquals(60, store.todayCount(day))
    }

    @Test fun subtractFloorsAtZeroNeverNegative() {
        val store = ZabadChallengeStore(MapSettings())
        store.addToday(day, 30)
        assertEquals(0, store.subtractToday(day, 500))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun subtractBelowSyncedBaselineIsPreservedAfterRemoteRefetch() {
        val store = ZabadChallengeStore(MapSettings())
        store.addToday(day, 80)
        store.onSyncSuccess(day, 80)
        assertEquals(50, store.subtractToday(day, 30))
        store.updateRemoteBaseline(day, 80)
        assertEquals(50, store.todayCount(day))
    }
}
