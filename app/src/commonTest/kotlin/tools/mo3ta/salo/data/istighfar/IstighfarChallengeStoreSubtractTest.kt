package tools.mo3ta.salo.data.istighfar

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IstighfarChallengeStoreSubtractTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun subtractReducesTodayCount() {
        val store = IstighfarChallengeStore(MapSettings())
        store.addToday(day, 70)
        assertEquals(50, store.subtractToday(day, 20))
        assertEquals(50, store.todayCount(day))
    }

    @Test fun subtractFloorsAtZeroNeverNegative() {
        val store = IstighfarChallengeStore(MapSettings())
        store.addToday(day, 30)
        assertEquals(0, store.subtractToday(day, 999))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun subtractBelowSyncedBaselineIsPreservedAfterRemoteRefetch() {
        val store = IstighfarChallengeStore(MapSettings())
        store.addToday(day, 60)
        store.onSyncSuccess(day, 60)
        assertEquals(40, store.subtractToday(day, 20))
        store.updateRemoteBaseline(day, 60)
        assertEquals(40, store.todayCount(day))
    }
}
