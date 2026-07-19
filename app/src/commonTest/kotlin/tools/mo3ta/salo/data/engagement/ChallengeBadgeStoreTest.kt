package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeBadgeStoreTest {

    private fun store(settings: MapSettings = MapSettings()) = ChallengeBadgeStore(settings)

    @Test
    fun firstWin_countIsOne() {
        val s = store()
        val result = s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        assertEquals(1, result)
        assertEquals(1, s.getWinCount(ChallengeType.DHIKR))
    }

    @Test
    fun sameDayTwice_secondWinIgnored() {
        val s = store()
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        val result = s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        assertNull(result)
        assertEquals(1, s.getWinCount(ChallengeType.DHIKR))
    }

    @Test fun zabadMultipleRoundsAwardOnlyOneDailyWin() {
        val s = store()
        val day = LocalDate(2026, 7, 12)
        s.recordWin(ChallengeType.ZABAD, day)
        assertNull(s.recordWin(ChallengeType.ZABAD, day))
        assertEquals(1, s.getWinCount(ChallengeType.ZABAD))
    }

    @Test
    fun winsAcrossDays_countIncrementsByOnePerDay() {
        val s = store()
        s.recordWin(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 11))
        val result = s.recordWin(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 13))
        assertEquals(3, result)
        assertEquals(3, s.getWinCount(ChallengeType.ISTIGHFAR))
    }

    @Test
    fun challengesTrackedIndependently() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordWin(ChallengeType.DHIKR, day)
        s.recordWin(ChallengeType.BAQIYAT, day)
        assertEquals(1, s.getWinCount(ChallengeType.DHIKR))
        assertEquals(1, s.getWinCount(ChallengeType.BAQIYAT))
        assertEquals(0, s.getWinCount(ChallengeType.ISTIGHFAR))
    }

    @Test
    fun noWins_countIsZero() {
        assertEquals(0, store().getWinCount(ChallengeType.DHIKR))
    }

    @Test
    fun getWinCounts_coversAllChallenges() {
        val s = store()
        s.recordWin(ChallengeType.BAQIYAT, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.BAQIYAT, LocalDate(2026, 7, 11))
        val counts = s.getWinCounts()
        assertEquals(ChallengeType.entries.toSet(), counts.keys)
        assertEquals(2, counts[ChallengeType.BAQIYAT])
        assertEquals(0, counts[ChallengeType.DHIKR])
    }

    // ── streaks ──────────────────────────────────────────────────────────────────
    // Streaks are driven by any activity (recordActivity), not by reaching the goal (recordWin).

    @Test
    fun firstActivity_streakIsOne() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordActivity(ChallengeType.DHIKR, day)
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, day))
    }

    @Test
    fun consecutiveActivity_streakGrows() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 12))
        assertEquals(3, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 12)))
    }

    @Test
    fun missedDay_streakRestartsAtOne() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // Skips the 12th — the 13th restarts the streak.
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 13))
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 13)))
    }

    @Test
    fun sameDayTwice_streakUnchanged() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        assertEquals(1, s.recordActivity(ChallengeType.DHIKR, day))
        assertNull(s.recordActivity(ChallengeType.DHIKR, day))
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, day))
    }

    @Test
    fun streakStillCountedTheDayAfterLastActiveDay() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // No activity yet on the 12th, but the streak is still alive.
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 12)))
    }

    @Test
    fun streakLapsesWhenLastActiveDayOlderThanYesterday() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // Two days later with no activity — the streak has lapsed.
        assertEquals(0, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 13)))
    }

    @Test
    fun bestStreak_keepsLongestRun() {
        val s = store()
        // Run of 3.
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 12))
        // Break, then a shorter run of 2.
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 15))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 16))
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 16)))
        assertEquals(3, s.getBestStreak(ChallengeType.DHIKR))
    }

    @Test
    fun streaksTrackedIndependentlyPerChallenge() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordActivity(ChallengeType.BAQIYAT, LocalDate(2026, 7, 11))
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 11)))
        assertEquals(1, s.getCurrentStreak(ChallengeType.BAQIYAT, LocalDate(2026, 7, 11)))
        assertEquals(0, s.getCurrentStreak(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 11)))
    }

    @Test
    fun getCurrentStreaks_coversAllChallenges() {
        val s = store()
        val day = LocalDate(2026, 7, 11)
        s.recordActivity(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.ISTIGHFAR, day)
        val streaks = s.getCurrentStreaks(day)
        assertEquals(ChallengeType.entries.toSet(), streaks.keys)
        assertEquals(2, streaks[ChallengeType.ISTIGHFAR])
        assertEquals(0, streaks[ChallengeType.DHIKR])
    }

    @Test
    fun activityDrivesStreakWithoutWinning() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        // Activity but no win: streak advances, badge count stays 0.
        s.recordActivity(ChallengeType.DHIKR, day)
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, day))
        assertEquals(0, s.getWinCount(ChallengeType.DHIKR))
    }

    @Test
    fun winningDoesNotByItselfAdvanceStreak() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        // recordWin only touches the badge count, never the streak.
        s.recordWin(ChallengeType.DHIKR, day)
        assertEquals(1, s.getWinCount(ChallengeType.DHIKR))
        assertEquals(0, s.getCurrentStreak(ChallengeType.DHIKR, day))
    }

    // ── today's participation ────────────────────────────────────────────────────
    // Card border highlight: a challenge counts as participated only on its last active day.

    @Test
    fun activityToday_marksChallengeActiveToday() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordActivity(ChallengeType.DHIKR, day)
        assertTrue(s.wasActiveOn(ChallengeType.DHIKR, day))
        assertEquals(setOf(ChallengeType.DHIKR), s.getActiveChallenges(day))
    }

    @Test
    fun activityYesterday_notActiveToday() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        val today = LocalDate(2026, 7, 11)
        assertFalse(s.wasActiveOn(ChallengeType.DHIKR, today))
        assertEquals(emptySet(), s.getActiveChallenges(today))
        // The streak is still alive even though today has no activity yet.
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, today))
    }

    @Test
    fun noActivityEver_nothingActiveToday() {
        val s = store()
        assertFalse(s.wasActiveOn(ChallengeType.DHIKR, LocalDate(2026, 7, 10)))
        assertEquals(emptySet(), s.getActiveChallenges(LocalDate(2026, 7, 10)))
    }

    @Test
    fun getActiveChallenges_collectsOnlyTodaysParticipants() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordActivity(ChallengeType.DHIKR, day)
        s.recordActivity(ChallengeType.ZABAD, day)
        s.recordActivity(ChallengeType.BAQIYAT, LocalDate(2026, 7, 9))
        assertEquals(setOf(ChallengeType.DHIKR, ChallengeType.ZABAD), s.getActiveChallenges(day))
    }
}
