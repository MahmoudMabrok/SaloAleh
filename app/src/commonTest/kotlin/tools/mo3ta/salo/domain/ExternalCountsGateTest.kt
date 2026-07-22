package tools.mo3ta.salo.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalCountsGateTest {

    private val install = LocalDate(2026, 7, 22)

    @Test
    fun installDay_hidesEntry() {
        assertFalse(ExternalCountsGate.canShowExternalCountsEntry(install, install))
    }

    @Test
    fun oneAndTwoDaysAfterInstall_stillHidden() {
        assertFalse(ExternalCountsGate.canShowExternalCountsEntry(LocalDate(2026, 7, 23), install))
        assertFalse(ExternalCountsGate.canShowExternalCountsEntry(LocalDate(2026, 7, 24), install))
    }

    @Test
    fun threeDaysAfterInstall_shows() {
        assertTrue(ExternalCountsGate.canShowExternalCountsEntry(LocalDate(2026, 7, 25), install))
    }

    @Test
    fun longAfterInstall_showsForever() {
        assertTrue(ExternalCountsGate.canShowExternalCountsEntry(LocalDate(2027, 1, 1), install))
    }

    @Test
    fun crossesMonthBoundary() {
        val julyInstall = LocalDate(2026, 7, 30)
        assertFalse(ExternalCountsGate.canShowExternalCountsEntry(LocalDate(2026, 8, 1), julyInstall))
        assertTrue(ExternalCountsGate.canShowExternalCountsEntry(LocalDate(2026, 8, 2), julyInstall))
    }
}
