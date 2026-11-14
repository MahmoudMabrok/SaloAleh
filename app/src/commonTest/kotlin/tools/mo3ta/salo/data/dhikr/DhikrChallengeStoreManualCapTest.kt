package tools.mo3ta.salo.data.dhikr

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.domain.MILESTONE_MANUAL_DAILY_CAP
import kotlin.test.Test
import kotlin.test.assertEquals

class DhikrChallengeStoreManualCapTest {

    private val day = LocalDate(2026, 11, 14)

    @Test
    fun milestoneCapAllowsTenThousandAcrossBatches() {
        val store = DhikrChallengeStore(MapSettings())

        store.addToday(day, 6_000, MILESTONE_MANUAL_DAILY_CAP)
        assertEquals(6_000, store.todayCount(day))
        assertEquals(4_000, store.manualRemainingToday(day, MILESTONE_MANUAL_DAILY_CAP))

        // A second batch may only use what is left, so the day still cannot exceed 10,000.
        store.addToday(day, 6_000, MILESTONE_MANUAL_DAILY_CAP)
        assertEquals(10_000, store.todayCount(day))
        assertEquals(0, store.manualRemainingToday(day, MILESTONE_MANUAL_DAILY_CAP))

        store.addToday(day, 1, MILESTONE_MANUAL_DAILY_CAP)
        assertEquals(10_000, store.todayCount(day))
    }

    @Test
    fun baseCapStillAppliesBelowTheMilestone() {
        val store = DhikrChallengeStore(MapSettings())

        store.addToday(day, 6_000, CHALLENGE_MANUAL_DAILY_CAP)

        assertEquals(CHALLENGE_MANUAL_DAILY_CAP, store.todayCount(day))
        assertEquals(0, store.manualRemainingToday(day, CHALLENGE_MANUAL_DAILY_CAP))
    }

    @Test
    fun usedLedgerSurvivesAFallBackToTheBaseCap() {
        val store = DhikrChallengeStore(MapSettings())
        store.addToday(day, 5_000, MILESTONE_MANUAL_DAILY_CAP)

        // The streak broke later the same day; 5,000 already used is above the 2,000 base cap.
        assertEquals(0, store.manualRemainingToday(day, CHALLENGE_MANUAL_DAILY_CAP))
        store.addToday(day, 100, CHALLENGE_MANUAL_DAILY_CAP)
        assertEquals(5_000, store.todayCount(day))
    }

    @Test
    fun inAppTapsDoNotConsumeTheManualAllowance() {
        val store = DhikrChallengeStore(MapSettings())

        repeat(5) { store.incrementToday(day) }

        assertEquals(5, store.todayCount(day))
        assertEquals(MILESTONE_MANUAL_DAILY_CAP, store.manualRemainingToday(day, MILESTONE_MANUAL_DAILY_CAP))
    }
}
