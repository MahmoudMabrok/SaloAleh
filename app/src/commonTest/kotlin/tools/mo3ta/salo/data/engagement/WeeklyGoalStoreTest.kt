package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklyGoalStoreTest {

    private fun store(s: MapSettings = MapSettings()) = WeeklyGoalStore(s)

    private val saturday = LocalDate(2026, 9, 12)
    private val sunday = LocalDate(2026, 9, 13)
    private val friday = LocalDate(2026, 9, 18)
    private val nextSaturday = LocalDate(2026, 9, 19)

    @Test
    fun unsetGoalIsZero() {
        assertEquals(0, store().goal(ChallengeType.DHIKR))
        assertFalse(store().snapshot(ChallengeType.DHIKR, saturday).hasGoal)
    }

    @Test
    fun setGoalPersists() {
        val s = store()
        s.setGoal(ChallengeType.DHIKR, 500)
        assertEquals(500, s.goal(ChallengeType.DHIKR))
        assertEquals(0, s.goal(ChallengeType.ISTIGHFAR))
    }

    @Test
    fun setGoalZeroClears() {
        val s = store()
        s.setGoal(ChallengeType.DHIKR, 1000)
        s.setGoal(ChallengeType.DHIKR, 0)
        assertEquals(0, s.goal(ChallengeType.DHIKR))
    }

    @Test
    fun sameDayReplacesInsteadOfDoubling() {
        val s = store()
        assertEquals(10, s.syncFromTodayCount(ChallengeType.DHIKR, saturday, 10))
        assertEquals(25, s.syncFromTodayCount(ChallengeType.DHIKR, saturday, 25))
        assertEquals(25, s.weekCount(ChallengeType.DHIKR, saturday))
    }

    @Test
    fun laterDayInSameWeekAddsToday() {
        val s = store()
        s.syncFromTodayCount(ChallengeType.DHIKR, saturday, 100)
        s.syncFromTodayCount(ChallengeType.DHIKR, sunday, 40)
        assertEquals(140, s.weekCount(ChallengeType.DHIKR, sunday))
        s.syncFromTodayCount(ChallengeType.DHIKR, sunday, 41)
        assertEquals(141, s.weekCount(ChallengeType.DHIKR, sunday))
    }

    @Test
    fun fridayStillSameWeekAsSaturday() {
        val s = store()
        s.syncFromTodayCount(ChallengeType.GHARS, saturday, 10)
        s.syncFromTodayCount(ChallengeType.GHARS, friday, 5)
        assertEquals(15, s.weekCount(ChallengeType.GHARS, friday))
    }

    @Test
    fun newWeekResetsCountsAndKeepsGoal() {
        val s = store()
        s.setGoal(ChallengeType.DHIKR, 1000)
        s.syncFromTodayCount(ChallengeType.DHIKR, friday, 800)
        assertEquals(800, s.weekCount(ChallengeType.DHIKR, friday))
        assertEquals(0, s.syncFromTodayCount(ChallengeType.DHIKR, nextSaturday, 0))
        assertEquals(0, s.weekCount(ChallengeType.DHIKR, nextSaturday))
        assertEquals(1000, s.goal(ChallengeType.DHIKR))
    }

    @Test
    fun typesAreIndependent() {
        val s = store()
        s.syncFromTodayCount(ChallengeType.DHIKR, saturday, 10)
        s.syncFromTodayCount(ChallengeType.ZABAD, saturday, 3)
        assertEquals(10, s.weekCount(ChallengeType.DHIKR, saturday))
        assertEquals(3, s.weekCount(ChallengeType.ZABAD, saturday))
    }

    @Test
    fun subtractOnSameDayFoldsCorrectly() {
        val s = store()
        s.syncFromTodayCount(ChallengeType.ISTIGHFAR, saturday, 70)
        s.syncFromTodayCount(ChallengeType.ISTIGHFAR, saturday, 50)
        assertEquals(50, s.weekCount(ChallengeType.ISTIGHFAR, saturday))
    }

    @Test
    fun completedWhenCountReachesGoal() {
        val s = store()
        s.setGoal(ChallengeType.QURAN, 7)
        s.syncFromTodayCount(ChallengeType.QURAN, saturday, 7)
        val snap = s.snapshot(ChallengeType.QURAN, saturday)
        assertTrue(snap.completed)
        assertEquals(1f, snap.fraction)
    }

    @Test
    fun negativeTodayCountIsFloored() {
        val s = store()
        assertEquals(0, s.syncFromTodayCount(ChallengeType.DHIKR, saturday, -5))
    }
}
