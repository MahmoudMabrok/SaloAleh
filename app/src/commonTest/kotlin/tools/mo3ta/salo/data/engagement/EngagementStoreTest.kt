package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngagementStoreTest {

    private fun store(settings: MapSettings = MapSettings()) = EngagementStore(settings)

    @Test
    fun firstOpen_openCountIs1_streakIs1_showsWelcomeDialog() {
        val data = store().recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(1, data.openCount)
        assertEquals(1, data.currentStreak)
        assertNull(data.newlyEarnedBadge)
        assertTrue(data.shouldRequestNotifPermission)
    }

    @Test
    fun thirdOpen_dialogNotShownAgain() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 28))
        store.recordOpen(today = LocalDate(2026, 4, 29))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(3, data.openCount)
        assertFalse(data.shouldRequestNotifPermission)
    }

    @Test
    fun fourthOpen_permissionNotRequestedAgain() {
        val s = MapSettings()
        val store = store(s)
        repeat(3) { i -> store.recordOpen(today = LocalDate(2026, 4, 28 + i)) }
        val data = store.recordOpen(today = LocalDate(2026, 5, 1))
        assertFalse(data.shouldRequestNotifPermission)
    }

    @Test
    fun consecutiveDays_streakIncreases() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 28))
        store.recordOpen(today = LocalDate(2026, 4, 29))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(3, data.currentStreak)
    }

    @Test
    fun skipDay_streakResets() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 28))
        // Miss 2 days (Apr 29 + Apr 30); grace only covers 1 missed day
        val data = store.recordOpen(today = LocalDate(2026, 5, 1))
        assertEquals(1, data.currentStreak)
    }

    @Test
    fun sameDay_openTwice_streakStaysOne() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 30))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(1, data.currentStreak)
    }

    @Test
    fun streak7_badge7Earned_onSeventhDay() {
        val s = MapSettings()
        val store = store(s)
        var data = store.recordOpen(today = LocalDate(2026, 4, 24))
        for (day in 25..30) {
            data = store.recordOpen(today = LocalDate(2026, 4, day))
        }
        // data is from the 7th distinct day (April 30)
        assertEquals(BadgeType.STREAK_7, data.newlyEarnedBadge)
    }

    @Test
    fun streak7_badge7NotDuplicated_nextDay() {
        val s = MapSettings()
        val store = store(s)
        for (day in 24..30) { store.recordOpen(today = LocalDate(2026, 4, day)) }
        // day 31 = 8th consecutive, badge already awarded
        val data = store.recordOpen(today = LocalDate(2026, 5, 1))
        assertNull(data.newlyEarnedBadge)
    }

    @Test
    fun streak30_badge30Earned_onThirtiethDay() {
        val s = MapSettings()
        val store = store(s)
        for (day in 1..29) {
            store.recordOpen(today = LocalDate(2026, 1, day))
        }
        val data = store.recordOpen(today = LocalDate(2026, 1, 30))
        assertEquals(BadgeType.STREAK_30, data.newlyEarnedBadge)
    }

    @Test
    fun streak30_badge30NotDuplicated() {
        val s = MapSettings()
        val store = store(s)
        for (day in 1..30) { store.recordOpen(today = LocalDate(2026, 1, day)) }
        val data = store.recordOpen(today = LocalDate(2026, 1, 31))
        assertNull(data.newlyEarnedBadge)
    }

    @Test
    fun missedDays_returns3() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 27))
        assertEquals(3, store.missedDays(today = LocalDate(2026, 4, 30)))
    }

    @Test
    fun noMiss_openedToday_returns0() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(0, store.missedDays(today = LocalDate(2026, 4, 30)))
    }

    @Test
    fun rankAchievement_savedOnce_returnedOnFirst() {
        val s = MapSettings()
        val store = store(s)
        val today = LocalDate(2026, 4, 30)
        val result = store.checkAndSaveRankAchievement("round-2026-04-30", 3, today)
        assertEquals(3, result?.rank)
        assertEquals("round-2026-04-30", result?.roundKey)
    }

    @Test
    fun rankAchievement_notDuplicated_sameRound() {
        val s = MapSettings()
        val store = store(s)
        val today = LocalDate(2026, 4, 30)
        store.checkAndSaveRankAchievement("round-2026-04-30", 3, today)
        val second = store.checkAndSaveRankAchievement("round-2026-04-30", 3, today)
        assertNull(second)
    }

    @Test
    fun getAllAchievements_returnsStreakBadgeAndRank() {
        val s = MapSettings()
        val store = store(s)
        val today = LocalDate(2026, 4, 30)
        for (day in 24..30) { store.recordOpen(today = LocalDate(2026, 4, day)) }
        store.checkAndSaveRankAchievement("round-2026-04-30", 1, today)
        val all = store.getAllAchievements()
        assertTrue(all.any { it is Achievement.StreakBadge })
        assertTrue(all.any { it is Achievement.RankAchievement })
    }

    @Test
    fun rankAchievements_malformedStorageEntry_validEntriesStillReturned() {
        val s = MapSettings()
        // Pre-seed one garbage entry and one valid entry
        s.putString("eng_rank_achievements", "garbage;round-2026-04-30:5:2026-04-30")
        val store = store(s)
        val ranks = store.getAllAchievements().filterIsInstance<Achievement.RankAchievement>()
        assertEquals(1, ranks.size)
        assertEquals(5, ranks[0].rank)
    }

    @Test
    fun missOneDay_graceAvailable_streakPreserved() {
        val s = MapSettings()
        val store = store(s)
        // Build streak of 5
        for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
        // Skip May 1 (miss exactly 1 day), open May 2
        val data = store.recordOpen(today = LocalDate(2026, 5, 2))
        assertEquals(6, data.currentStreak)
        assertTrue(data.graceConsumedNow)
    }

    @Test
    fun missOneDay_graceAlreadyUsed_streakBreaks() {
        val s = MapSettings()
        val store = store(s)
        for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
        // First miss — grace consumed
        store.recordOpen(today = LocalDate(2026, 5, 2))
        // Build streak again within same 7-day window
        store.recordOpen(today = LocalDate(2026, 5, 3))
        store.recordOpen(today = LocalDate(2026, 5, 4))
        // Second miss within same 7-day window — no grace
        val data = store.recordOpen(today = LocalDate(2026, 5, 6))
        assertEquals(1, data.currentStreak)
        assertFalse(data.graceConsumedNow)
    }

    @Test
    fun missOneDay_graceResets_after7Days() {
        val s = MapSettings()
        val store = store(s)
        for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
        // Grace consumed May 2
        store.recordOpen(today = LocalDate(2026, 5, 2))
        // 8 days later — new 7-day window; miss 1 day
        store.recordOpen(today = LocalDate(2026, 5, 9))
        store.recordOpen(today = LocalDate(2026, 5, 10))
        val data = store.recordOpen(today = LocalDate(2026, 5, 12))
        assertTrue(data.graceConsumedNow)
        assertEquals(3, data.currentStreak)
    }

    @Test
    fun missTwoDays_noGrace_streakBreaks() {
        val s = MapSettings()
        val store = store(s)
        for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
        // Miss 2 days — grace only covers 1 missed day
        val data = store.recordOpen(today = LocalDate(2026, 5, 3))
        assertEquals(1, data.currentStreak)
        assertFalse(data.graceConsumedNow)
    }

    @Test
    fun wasGraceConsumedToday_trueAfterGrace() {
        val s = MapSettings()
        val store = store(s)
        for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
        store.recordOpen(today = LocalDate(2026, 5, 2))
        assertTrue(store.wasGraceConsumedToday(LocalDate(2026, 5, 2)))
        assertFalse(store.wasGraceConsumedToday(LocalDate(2026, 5, 3)))
    }

    @Test
    fun missOneDay_graceConsumed_streakReachesBadgeThreshold() {
        val s = MapSettings()
        val store = store(s)
        // Build streak of 6 days
        for (day in 25..30) store.recordOpen(today = LocalDate(2026, 4, day))
        // Miss May 1, open May 2 — grace saves streak → streak becomes 7 → badge earned
        val data = store.recordOpen(today = LocalDate(2026, 5, 2))
        assertEquals(7, data.currentStreak)
        assertTrue(data.graceConsumedNow)
        assertEquals(BadgeType.STREAK_7, data.newlyEarnedBadge)
    }
}
