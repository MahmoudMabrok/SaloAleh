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
}
