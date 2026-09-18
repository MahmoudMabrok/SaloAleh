package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.STREAK_FREEZE_MAX_DAYS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun missedOneDay_freezeKeepsStreak() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Skip July 13, resume July 14 — one missed day is frozen
        val result = s.recordActivity(round, LocalDate(2026, 7, 14))
        assertEquals(3, result.currentStreak)
    }

    @Test
    fun missedTwoDays_freezeKeepsStreak() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Skip July 13 and 14, resume July 15 — two missed days still inside the freeze window
        val result = s.recordActivity(round, LocalDate(2026, 7, 15))
        assertEquals(3, result.currentStreak)
    }

    @Test
    fun missedThreeDays_streakResets() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        // Skip July 13, 14, 15 — three consecutive days without zikr
        val result = s.recordActivity(round, LocalDate(2026, 7, 16))
        assertEquals(1, result.currentStreak)
    }

    @Test
    fun newRound_streakContinues() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val result = s.recordActivity("2026-07-24", LocalDate(2026, 7, 13))
        assertEquals(3, result.currentStreak)
    }

    @Test
    fun newRound_threeMissedDays_streakStillResets() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val result = s.recordActivity("2026-07-24", LocalDate(2026, 7, 16))
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
        val result = s.recordActivity(round, LocalDate(2026, 7, 18))
        assertNull(result.newlyEarnedBadge)
        assertTrue(s.hasEarnedForRound(round))
    }

    @Test
    fun threeMissedDays_cannotReachBadgeUntilStreakRebuilds() {
        val s = store()
        for (day in 11..15) { s.recordActivity(round, LocalDate(2026, 7, day)) } // streak 5
        // Skip July 16, 17, 18 — three misses reset the streak
        val result = s.recordActivity(round, LocalDate(2026, 7, 19))
        assertEquals(1, result.currentStreak)
        assertNull(result.newlyEarnedBadge)
    }

    @Test
    fun getCurrentStreak_aliveAfterOneMissedDay() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        assertEquals(2, s.getCurrentStreak(round, LocalDate(2026, 7, 14)))
        val snap = s.snapshot(LocalDate(2026, 7, 14))
        assertEquals(1, snap.missedDays)
        assertEquals(2, snap.freezeRemaining)
        assertTrue(snap.isAtRisk)
    }

    @Test
    fun getCurrentStreak_aliveAfterTwoMissedDays() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val snap = s.snapshot(LocalDate(2026, 7, 15))
        assertEquals(2, snap.currentStreak)
        assertEquals(2, snap.missedDays)
        assertEquals(1, snap.freezeRemaining)
        assertTrue(snap.isAtRisk)
    }

    @Test
    fun getCurrentStreak_zeroAfterThreeMissedDays() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        assertEquals(0, s.getCurrentStreak(round, LocalDate(2026, 7, 16)))
        val snap = s.snapshot(LocalDate(2026, 7, 16))
        assertEquals(3, snap.missedDays)
        assertEquals(0, snap.freezeRemaining)
        assertFalse(snap.isAtRisk)
    }

    @Test
    fun getCurrentStreak_survivesUntilNextDay() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        assertEquals(2, s.getCurrentStreak(round, LocalDate(2026, 7, 13)))
        val snap = s.snapshot(LocalDate(2026, 7, 13))
        assertEquals(0, snap.missedDays)
        assertEquals(STREAK_FREEZE_MAX_DAYS, snap.freezeRemaining)
        assertFalse(snap.isAtRisk)
    }

    @Test
    fun getCurrentStreak_survivesRoundChange() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        assertEquals(1, s.getCurrentStreak("2026-07-24", LocalDate(2026, 7, 11)))
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

    @Test
    fun reconcile_resetsStoredCountAfterThreeMisses() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val first = s.reconcile(LocalDate(2026, 7, 16))
        assertEquals(0, first.currentStreak)
        assertTrue(first.justReset)
        val second = s.reconcile(LocalDate(2026, 7, 16))
        assertFalse(second.justReset)
        assertEquals(1, s.recordActivity(round, LocalDate(2026, 7, 16)).currentStreak)
    }

    @Test
    fun activateFreeze_coversRemainingDays_clearsAtRisk() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val today = LocalDate(2026, 7, 14) // missed July 13
        assertTrue(s.snapshot(today).isAtRisk)
        val frozen = s.activateFreeze(2, today)
        assertEquals(2, frozen.currentStreak)
        assertEquals(LocalDate(2026, 7, 15), frozen.freezeUntil)
        assertFalse(frozen.isAtRisk)
        assertEquals(2, frozen.freezeRemaining)
    }

    @Test
    fun activateFreeze_cappedByRemainingWindow() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.recordActivity(round, LocalDate(2026, 7, 12))
        val today = LocalDate(2026, 7, 15) // two misses, 1 freeze day left
        val frozen = s.activateFreeze(3, today)
        assertEquals(1, frozen.freezeRemaining)
        assertEquals(LocalDate(2026, 7, 15), frozen.freezeUntil)
    }

    @Test
    fun activateFreeze_ignoredWhenStreakAlreadyBroken() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        val frozen = s.activateFreeze(3, LocalDate(2026, 7, 16))
        assertEquals(0, frozen.currentStreak)
        assertNull(frozen.freezeUntil)
    }

    @Test
    fun freezeThenZikr_clearsFreezeUntil() {
        val s = store()
        s.recordActivity(round, LocalDate(2026, 7, 11))
        s.activateFreeze(2, LocalDate(2026, 7, 12))
        s.recordActivity(round, LocalDate(2026, 7, 13))
        assertNull(s.snapshot(LocalDate(2026, 7, 13)).freezeUntil)
        assertEquals(STREAK_FREEZE_MAX_DAYS, s.snapshot(LocalDate(2026, 7, 13)).freezeRemaining)
    }
}
