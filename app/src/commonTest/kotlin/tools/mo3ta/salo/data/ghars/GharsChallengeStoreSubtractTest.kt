package tools.mo3ta.salo.data.ghars

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class GharsChallengeStoreSubtractTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun subtractReducesTodayCount() {
        val store = GharsChallengeStore(MapSettings())
        store.addToday(day, 50)
        assertEquals(30, store.subtractToday(day, 20))
        assertEquals(30, store.todayCount(day))
    }

    @Test fun subtractFloorsAtZeroNeverNegative() {
        val store = GharsChallengeStore(MapSettings())
        store.addToday(day, 25)
        assertEquals(0, store.subtractToday(day, 999))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun subtractBelowSyncedBaselineIsPreservedAfterRemoteRefetch() {
        val store = GharsChallengeStore(MapSettings())
        store.addToday(day, 50)
        store.onSyncSuccess(day, 50)
        assertEquals(30, store.subtractToday(day, 20))
        store.updateRemoteBaseline(day, 50)
        assertEquals(30, store.todayCount(day))
    }
}
