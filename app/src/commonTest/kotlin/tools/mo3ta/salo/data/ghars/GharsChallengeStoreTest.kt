package tools.mo3ta.salo.data.ghars

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
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

    @Test fun manualAddIsCappedPerDay() {
        val store = GharsChallengeStore(MapSettings())
        val day = LocalDate(2026, 7, 12)
        // A single over-cap batch is clamped to the cap.
        store.addToday(day, CHALLENGE_MANUAL_DAILY_CAP + 5000)
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.todayCount(day))
        assertEquals(0, store.manualRemainingToday(day))
        // Further manual entry adds nothing once the cap is used up.
        store.addToday(day, 100)
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.todayCount(day))
    }

    @Test fun manualAddCapAccumulatesAcrossBatches() {
        val store = GharsChallengeStore(MapSettings())
        val day = LocalDate(2026, 7, 12)
        store.addToday(day, 6000)
        assertEquals(4000, store.manualRemainingToday(day))
        // The second batch is clamped to the remaining 4000.
        store.addToday(day, 9000)
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.todayCount(day))
        assertEquals(0, store.manualRemainingToday(day))
    }

    @Test fun tapsAreNotCountedAgainstManualCap() {
        val store = GharsChallengeStore(MapSettings())
        val day = LocalDate(2026, 7, 12)
        // Taps are uncapped and never consume the manual allowance.
        repeat(20) { store.incrementToday(day) }
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.manualRemainingToday(day))
        store.addToday(day, CHALLENGE_MANUAL_DAILY_CAP)
        // Total = 20 taps + full manual cap; manual is spent, taps still counted.
        assertEquals(20 + CHALLENGE_MANUAL_DAILY_CAP, store.todayCount(day))
        assertEquals(0, store.manualRemainingToday(day))
    }

    @Test fun manualCapResetsNextDay() {
        val store = GharsChallengeStore(MapSettings())
        val first = LocalDate(2026, 7, 12)
        val next = LocalDate(2026, 7, 13)
        store.addToday(first, CHALLENGE_MANUAL_DAILY_CAP)
        assertEquals(0, store.manualRemainingToday(first))
        // A fresh day restores the full allowance.
        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.manualRemainingToday(next))
        store.addToday(next, 300)
        assertEquals(300, store.todayCount(next))
    }
}
