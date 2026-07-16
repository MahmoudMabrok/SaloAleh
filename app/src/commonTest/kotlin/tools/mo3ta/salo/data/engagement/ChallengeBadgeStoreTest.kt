package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun firstWin_streakIsOne() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordWin(ChallengeType.DHIKR, day)
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, day))
    }

    @Test
    fun consecutiveWins_streakGrows() {
        val s = store()
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 12))
        assertEquals(3, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 12)))
    }

    @Test
    fun missedDay_streakRestartsAtOne() {
        val s = store()
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // Skips the 12th — the 13th restarts the streak.
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 13))
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 13)))
    }

    @Test
    fun sameDayTwice_streakUnchanged() {
        val s = store()
        val day = LocalDate(2026, 7, 10)
        s.recordWin(ChallengeType.DHIKR, day)
        s.recordWin(ChallengeType.DHIKR, day)
        assertEquals(1, s.getCurrentStreak(ChallengeType.DHIKR, day))
    }

    @Test
    fun streakStillCountedTheDayAfterLastWin() {
        val s = store()
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // Not yet won on the 12th, but the streak is still alive.
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 12)))
    }

    @Test
    fun streakLapsesWhenLastWinOlderThanYesterday() {
        val s = store()
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        // Two days later with no win — the streak has lapsed.
        assertEquals(0, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 13)))
    }

    @Test
    fun bestStreak_keepsLongestRun() {
        val s = store()
        // Run of 3.
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 12))
        // Break, then a shorter run of 2.
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 15))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 16))
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 16)))
        assertEquals(3, s.getBestStreak(ChallengeType.DHIKR))
    }

    @Test
    fun streaksTrackedIndependentlyPerChallenge() {
        val s = store()
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.DHIKR, LocalDate(2026, 7, 11))
        s.recordWin(ChallengeType.BAQIYAT, LocalDate(2026, 7, 11))
        assertEquals(2, s.getCurrentStreak(ChallengeType.DHIKR, LocalDate(2026, 7, 11)))
        assertEquals(1, s.getCurrentStreak(ChallengeType.BAQIYAT, LocalDate(2026, 7, 11)))
        assertEquals(0, s.getCurrentStreak(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 11)))
    }

    @Test
    fun getCurrentStreaks_coversAllChallenges() {
        val s = store()
        val day = LocalDate(2026, 7, 11)
        s.recordWin(ChallengeType.ISTIGHFAR, LocalDate(2026, 7, 10))
        s.recordWin(ChallengeType.ISTIGHFAR, day)
        val streaks = s.getCurrentStreaks(day)
        assertEquals(ChallengeType.entries.toSet(), streaks.keys)
        assertEquals(2, streaks[ChallengeType.ISTIGHFAR])
        assertEquals(0, streaks[ChallengeType.DHIKR])
    }
}
