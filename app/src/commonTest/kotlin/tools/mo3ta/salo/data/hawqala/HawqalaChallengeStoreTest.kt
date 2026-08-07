package tools.mo3ta.salo.data.hawqala

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import kotlin.test.Test
import kotlin.test.assertEquals

class HawqalaChallengeStoreTest {
    @Test fun pendingTapsSurviveRemoteBaselineUpdate() {
        val store = HawqalaChallengeStore(MapSettings())
        val day = LocalDate(2026, 8, 7)
        repeat(3) { store.incrementToday(day) }
        store.updateRemoteBaseline(day, 20)
        assertEquals(23, store.todayCount(day))
        store.onSyncSuccess(day, 23)
        assertEquals(0, store.todayPending(day))
    }

    @Test fun manualEntryIsClampedToTheDailyCap() {
        val store = HawqalaChallengeStore(MapSettings())
        val day = LocalDate(2026, 8, 7)
        store.addToday(day, CHALLENGE_MANUAL_DAILY_CAP + 500)
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.todayCount(day))
        assertEquals(0, store.manualRemainingToday(day))
    }

    @Test fun dayRolloverResetsTodayButNotLifetime() {
        val store = HawqalaChallengeStore(MapSettings())
        val first = LocalDate(2026, 8, 7)
        val next = LocalDate(2026, 8, 8)
        repeat(5) { store.incrementToday(first) }
        assertEquals(0, store.todayCount(next))
        assertEquals(5, store.lifetimeCount())
    }

    @Test fun lifetimeAccumulatesAcrossDaysAndIgnoresRemoteBaseline() {
        val store = HawqalaChallengeStore(MapSettings())
        val first = LocalDate(2026, 8, 7)
        val next = LocalDate(2026, 8, 8)
        repeat(5) { store.incrementToday(first) }
        store.addToday(first, 10)
        assertEquals(15, store.lifetimeCount())
        // A higher remote baseline advances today's total but never the local lifetime tally.
        store.updateRemoteBaseline(first, 50)
        assertEquals(15, store.lifetimeCount())
        // A correction lowers today's count but never walks the lifetime tally back.
        store.subtractToday(first, 10)
        assertEquals(15, store.lifetimeCount())
        // Day rollover zeroes the daily count but never the lifetime accumulator.
        store.incrementToday(next)
        assertEquals(1, store.todayCount(next))
        assertEquals(16, store.lifetimeCount())
    }

    @Test fun restoreLifetimeOnlyEverRaisesTheTotal() {
        val store = HawqalaChallengeStore(MapSettings())
        store.restoreLifetime(120)
        assertEquals(120, store.lifetimeCount())
        store.restoreLifetime(40)
        assertEquals(120, store.lifetimeCount())
        store.restoreLifetime(-5)
        assertEquals(120, store.lifetimeCount())
    }

    @Test fun previousDayPendingIsReportedForFlush() {
        val store = HawqalaChallengeStore(MapSettings())
        val first = LocalDate(2026, 8, 7)
        val next = LocalDate(2026, 8, 8)
        repeat(4) { store.incrementToday(first) }
        assertEquals(first.toString() to 4, store.previousEntry(next))
        store.clearPreviousPending()
        assertEquals(null, store.previousEntry(next))
    }
}
