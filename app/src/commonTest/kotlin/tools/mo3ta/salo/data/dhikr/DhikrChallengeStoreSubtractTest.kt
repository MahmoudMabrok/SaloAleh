package tools.mo3ta.salo.data.dhikr

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import kotlin.test.Test
import kotlin.test.assertEquals

class DhikrChallengeStoreSubtractTest {

    private val day = LocalDate(2026, 7, 12)

    @Test fun subtractReducesTodayCount() {
        val store = DhikrChallengeStore(MapSettings())
        store.addToday(day, 100)
        assertEquals(70, store.subtractToday(day, 30))
        assertEquals(70, store.todayCount(day))
    }

    @Test fun subtractFloorsAtZeroNeverNegative() {
        val store = DhikrChallengeStore(MapSettings())
        store.addToday(day, 40)
        assertEquals(0, store.subtractToday(day, 1000))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun subtractBelowSyncedBaselineIsPreservedAfterRemoteRefetch() {
        val store = DhikrChallengeStore(MapSettings())
        // 80 is synced to the server as the baseline.
        store.addToday(day, 80)
        store.onSyncSuccess(day, 80)
        // User corrects down to 50 (below the synced baseline).
        assertEquals(50, store.subtractToday(day, 30))
        // A later remote fetch that re-advances the baseline must not undo the correction.
        store.updateRemoteBaseline(day, 80)
        assertEquals(50, store.todayCount(day))
    }

    @Test fun subtractRefundsManualCapLedger() {
        val store = DhikrChallengeStore(MapSettings())
        store.addToday(day, CHALLENGE_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(day))
        store.subtractToday(day, 200)
        assertEquals(200, store.manualRemainingToday(day))
    }

    @Test fun subtractNonPositiveIsNoOp() {
        val store = DhikrChallengeStore(MapSettings())
        store.addToday(day, 10)
        assertEquals(10, store.subtractToday(day, 0))
        assertEquals(10, store.subtractToday(day, -5))
        assertEquals(10, store.todayCount(day))
    }
}
