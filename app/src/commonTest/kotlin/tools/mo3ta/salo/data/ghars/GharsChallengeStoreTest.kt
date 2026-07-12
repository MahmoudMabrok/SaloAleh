package tools.mo3ta.salo.data.ghars

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GharsChallengeStoreTest {
    @Test fun pendingTapsSurviveRemoteBaselineUpdate() {
        val store = GharsChallengeStore(MapSettings())
        val day = LocalDate(2026, 7, 12)
        repeat(3) { store.incrementToday(day) }
        store.updateRemoteBaseline(day, 20)
        assertEquals(23, store.todayCount(day))
        store.onSyncSuccess(day, 23)
        assertEquals(0, store.todayPending(day))
    }

    @Test fun dayRolloverResetsCount() {
        val store = GharsChallengeStore(MapSettings())
        val first = LocalDate(2026, 7, 12)
        val next = LocalDate(2026, 7, 13)
        store.incrementToday(first)
        assertEquals(0, store.todayCount(next))
    }

    @Test fun previousEntrySurfacesUnsyncedPending() {
        val store = GharsChallengeStore(MapSettings())
        val first = LocalDate(2026, 7, 12)
        val next = LocalDate(2026, 7, 13)
        repeat(5) { store.incrementToday(first) }
        // A new day begins with un-flushed taps from the previous day.
        val prev = store.previousEntry(next)
        assertEquals("2026-07-12" to 5, prev)
        store.clearPreviousPending()
        assertNull(store.previousEntry(next))
    }

    @Test fun manualAddAccumulatesPending() {
        val store = GharsChallengeStore(MapSettings())
        val day = LocalDate(2026, 7, 12)
        store.addToday(day, 25)
        store.incrementToday(day)
        assertEquals(26, store.todayCount(day))
        assertEquals(26, store.todayPending(day))
    }
}
