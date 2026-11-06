package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.domain.STREAK_FREEZE_MAX_DAYS
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

    // ── streaks (with freeze window) ──────────────────────────────────────────────────

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
    fun oneMissedDay_keepsStreakInsideFreezeWindow() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // Skips the 12th — still inside freeze window, streak continues on the 13th.
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 13))
        assertEquals(3, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 13)))
    }

    @Test
    fun threeMissedDays_resetsStreak() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        // Skip 11,12,13 — 3 missed days → reset.
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 14))
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 14)))
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
    fun streakAliveTheDayAfterAndOnMissedDaysInsideWindow() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // No activity yet on the 12th, but the streak is still alive (0 missed so far).
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 12)))
        // One full missed day later still alive.
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 13)))
    }

    @Test
    fun streakLapsesAfterThreeMissedDays() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        // 3+ missed days → broken.
        assertEquals(0, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 14)))
    }

    @Test
    fun bestStreak_keepsLongestRun() {
        val s = store()
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 12))
        // Break with 3+ missed, then shorter run.
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 16))
        s.recordActivity(ChallengeType.DHIKR, LocalDate(2026, 7, 17))
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 17)))
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
        s.recordActivity(ChallengeType.DHIKR, day)
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, day))
        assertEquals(0, s.getWinCount(ChallengeType.DHIKR))
    }

    @Test
    fun winningDoesNotByItselfAdvanceStreak() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordWin(ChallengeType.DHIKR, day)
        assertEquals(1, s.getWinCount(ChallengeType.DHIKR))
        assertEquals(0, s.getCurrentStreak(ChallengeType.DHIKR, day))
    }

    @Test
    fun activateFreeze_coversUpcomingDays() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordActivity(ChallengeType.DHIKR, day)
        val snap = s.activateFreeze(ChallengeType.DHIKR, 2, day)
        assertEquals(1, snap.currentStreak)
        assertEquals(STREAK_FREEZE_MAX_DAYS - 0, snap.freezeRemaining) // remaining before freeze; freeze consumes from remaining
        // After freeze, at-risk should be false while covered.
        val after = s.snapshot(ChallengeType.DHIKR, day.plusDays(1))
        assertTrue(after.freezeUntil != null)
        assertFalse(after.isAtRisk)
    }

    // ── today's participation ───────────────────────────────────────────────────────

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

    private fun LocalDate.plusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + n)
}
