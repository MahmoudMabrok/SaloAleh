package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundStreakStoreTest {

    private val round = "2026-07-17"

    private fun store(settings: MapSettings = MapSettings()) = RoundStreakStore(settings)

    @Test
    fun firstActivity_streakIsOne_noBadge() {
        val result = store().recordActivity(round, LocalDate(2026, 7, 11))
        assertEquals(1, result.currentStreak)
        assertNull(result.newlyEarnedBadge)
    }

    @Test
    fun consecutiveDays_streakIncrements() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val result = s.recordActivity(round, LocalDate(2026, 7, 13))
        assertEquals(3, result.currentStreak)
    }

    @Test
    fun sameDayTwice_streakUnchanged() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        val result = s.recordActivity(round, LocalDate(2026, 7, 11))
        assertEquals(1, result.currentStreak)
        assertNull(result.newlyEarnedBadge)
    }

    @Test
    fun missedDay_streakRestarts() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Skip July 13, resume July 14
        val result = s.recordActivity(round, LocalDate(2026, 7, 14))
        assertEquals(1, result.currentStreak)
    }

    @Test
    fun newRound_streakResets() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Next round begins
        val result = s.recordActivity("2026-07-24", LocalDate(2026, 7, 18))
        assertEquals(1, result.currentStreak)
    }

    @Test
    fun sevenConsecutiveDays_badgeEarned() {
        val s = store()
        var result = s.recordActivity(round, LocalDate(2026, 7, 11))
        for (day in 12..17) {
            result = s.recordActivity(round, LocalDate(2026, 7, day))
        }
        assertEquals(7, result.currentStreak)
        val badge = result.newlyEarnedBadge
        assertNotNull(badge)
        assertEquals(round, badge.roundKey)
    }

    @Test
    fun badgeNotDuplicated_withinSameRound() {
        val s = store()
        for (day in 11..17) { s.recordActivity(round, LocalDate(2026, 7, day)) }
        // 8th consecutive active day — badge already earned
        val result = s.recordActivity(round, LocalDate(2026, 7, 18))
        assertNull(result.newlyEarnedBadge)
        assertTrue(s.hasEarnedForRound(round))
    }

    @Test
    fun missedDay_cannotReachBadgeUntilStreakRebuilds() {
        val s = store()
        for (day in 11..15) { s.recordActivity(round, LocalDate(2026, 7, day)) } // streak 5
        // Skip July 16, resume July 17 → streak restarts at 1, not enough for badge
        val result = s.recordActivity(round, LocalDate(2026, 7, 17))
        assertEquals(1, result.currentStreak)
        assertNull(result.newlyEarnedBadge)
    }

    @Test
    fun getCurrentStreak_zeroAfterMissedDay() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Two days later without activity → streak considered broken for display
        assertEquals(0, s.getCurrentStreak(round, LocalDate(2026, 7, 14)))
    }

    @Test
    fun getCurrentStreak_survivesUntilNextDay() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Streak alive on the following day (grace to record today)
        assertEquals(2, s.getCurrentStreak(round, LocalDate(2026, 7, 13)))
    }

    @Test
    fun getCurrentStreak_zeroForDifferentRound() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        assertEquals(0, s.getCurrentStreak("2026-07-24", LocalDate(2026, 7, 11)))
    }

    @Test
    fun earnedBadges_persistedAcrossRounds() {
        val s = store()
        for (day in 11..17) { s.recordActivity(round, LocalDate(2026, 7, day)) }
        val nextRound = "2026-07-24"
        for (day in 18..24) { s.recordActivity(nextRound, LocalDate(2026, 7, day)) }
        val badges = s.getEarnedBadges()
        assertEquals(2, badges.size)
        assertTrue(badges.any { it.roundKey == round })
        assertTrue(badges.any { it.roundKey == nextRound })
    }
}
