package tools.mo3ta.salo.data.quran

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class QuranChallengeStoreSubtractTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun subtractReducesTodayCount() {
        val store = QuranChallengeStore(MapSettings())
        store.addToday(day, 8)
        assertEquals(3, store.subtractToday(day, 5))
        assertEquals(3, store.todayCount(day))
    }

    @Test fun subtractFloorsAtZeroNeverNegative() {
        val store = QuranChallengeStore(MapSettings())
        store.addToday(day, 4)
        assertEquals(0, store.subtractToday(day, 100))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun subtractBelowSyncedBaselineIsPreservedAfterRemoteRefetch() {
        val store = QuranChallengeStore(MapSettings())
        store.addToday(day, 6)
        store.onSyncSuccess(day, 6)
        assertEquals(2, store.subtractToday(day, 4))
        store.updateRemoteBaseline(day, 6)
        assertEquals(2, store.todayCount(day))
    }
}
