package tools.mo3ta.salo.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SalawatManualCapTest {

    private val install = LocalDate(2026, 7, 22)

    @Test
    fun installDay_capsAt1000() {
        assertEquals(1_000, SalawatManualCap.dailyCap(install, install))
    }

    @Test
    fun rampGrowsBy1000EachDay() {
        assertEquals(2_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 23), install))
        assertEquals(3_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 24), install))
        assertEquals(9_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 30), install))
    }

    @Test
    fun dayTen_reachesMaxCap() {
        // Install day is day 1, so day 10 is nine days later.
        assertEquals(SalawatManualCap.MAX_DAILY_CAP, SalawatManualCap.dailyCap(LocalDate(2026, 7, 31), install))
        assertEquals(10_000, SalawatManualCap.MAX_DAILY_CAP)
    }

    @Test
    fun afterDayTen_staysFlatAtMax() {
        assertEquals(SalawatManualCap.MAX_DAILY_CAP, SalawatManualCap.dailyCap(LocalDate(2026, 8, 15), install))
        assertEquals(SalawatManualCap.MAX_DAILY_CAP, SalawatManualCap.dailyCap(LocalDate(2027, 1, 1), install))
    }

    @Test
    fun clockSkewBeforeInstall_treatedAsInstallDay() {
        assertEquals(1_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 20), install))
    }

    @Test
    fun crossesMonthBoundary() {
        val julyInstall = LocalDate(2026, 7, 30)
        assertEquals(1_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 30), julyInstall))
        assertEquals(2_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 31), julyInstall))
        assertEquals(3_000, SalawatManualCap.dailyCap(LocalDate(2026, 8, 1), julyInstall))
    }

    @Test
    fun streakOfThree_skipsRampToMaxOnInstallDay() {
        // A brand-new user who already has a 3-day streak gets the full cap immediately.
        assertEquals(SalawatManualCap.MAX_DAILY_CAP, SalawatManualCap.dailyCap(install, install, currentStreak = 3))
    }

    @Test
    fun streakAboveThree_alsoUnlocksMax() {
        assertEquals(SalawatManualCap.MAX_DAILY_CAP, SalawatManualCap.dailyCap(LocalDate(2026, 7, 24), install, currentStreak = 7))
    }

    @Test
    fun streakBelowThree_stillRamps() {
        assertEquals(1_000, SalawatManualCap.dailyCap(install, install, currentStreak = 2))
        assertEquals(3_000, SalawatManualCap.dailyCap(LocalDate(2026, 7, 24), install, currentStreak = 2))
    }

    @Test
    fun streakDefaultsToRamp_whenOmitted() {
        assertEquals(1_000, SalawatManualCap.dailyCap(install, install))
    }
}
