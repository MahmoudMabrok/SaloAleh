package tools.mo3ta.salo.data.zabad

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ZabadChallengeStoreTest {
    @Test fun pendingTapsSurviveRemoteBaselineUpdate() {
        val store = ZabadChallengeStore(MapSettings())
        val day = LocalDate(2026, 7, 12)
        repeat(3) { store.incrementToday(day) }
        store.updateRemoteBaseline(day, 20)
        assertEquals(23, store.todayCount(day))
        store.onSyncSuccess(day, 23)
        assertEquals(0, store.todayPending(day))
    }

    @Test fun dayRolloverResetsCountButNotSeaClock() {
        val store = ZabadChallengeStore(MapSettings())
        val first = LocalDate(2026, 7, 12)
        val next = LocalDate(2026, 7, 13)
        store.incrementToday(first)
        store.recordWash(first, 1234L)
        assertEquals(0, store.todayCount(next))
        assertEquals(1234L, store.lastWashTimestamp())
        assertEquals(0, store.roundsToday(next))
    }

    @Test fun lifetimeAccumulatesAcrossDaysAndIgnoresRemoteBaseline() {
        val store = ZabadChallengeStore(MapSettings())
        val first = LocalDate(2026, 7, 12)
        val next = LocalDate(2026, 7, 13)
        repeat(5) { store.incrementToday(first) }
        store.addToday(first, 10)
        assertEquals(15, store.lifetimeCount())
        // A higher remote baseline advances today's total but never the local lifetime tally.
        store.updateRemoteBaseline(first, 50)
        assertEquals(15, store.lifetimeCount())
        // Day rollover zeroes the daily count but never the lifetime accumulator.
        assertEquals(0, store.todayCount(next))
        assertEquals(15, store.lifetimeCount())
        repeat(3) { store.incrementToday(next) }
        assertEquals(18, store.lifetimeCount())
    }
}
