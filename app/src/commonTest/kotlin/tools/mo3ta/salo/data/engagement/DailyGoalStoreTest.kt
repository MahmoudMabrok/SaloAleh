package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyGoalStoreTest {

    private fun store(s: MapSettings = MapSettings()) = DailyGoalStore(s)

    @Test
    fun mondayTarget_is33() {
        val store = store()
        // 2026-04-27 is a Monday
        assertEquals(33, store.todayTarget(LocalDate(2026, 4, 27)))
    }

    @Test
    fun fridayTarget_is200() {
        val store = store()
        // 2026-05-01 is a Friday
        assertEquals(200, store.todayTarget(LocalDate(2026, 5, 1)))
    }

    @Test
    fun progressStartsAtZero() {
        val store = store()
        assertEquals(0, store.todayProgress(LocalDate(2026, 4, 27)))
    }

    @Test
    fun recordTap_accumulatesProgress() {
        val store = store()
        val today = LocalDate(2026, 4, 27)
        store.recordTap(today, 1)
        store.recordTap(today, 2)
        assertEquals(3, store.todayProgress(today))
    }

    @Test
    fun progressResetsOnNewDay() {
        val s = MapSettings()
        val store = store(s)
        store.recordTap(LocalDate(2026, 4, 27), 50)
        assertEquals(0, store.todayProgress(LocalDate(2026, 4, 28)))
    }

    @Test
    fun goalNotComplete_belowTarget() {
        val store = store()
        val today = LocalDate(2026, 4, 27) // Monday, target=33
        store.recordTap(today, 32)
        assertFalse(store.isGoalComplete(today))
    }

    @Test
    fun goalComplete_atTarget() {
        val store = store()
        val today = LocalDate(2026, 4, 27) // Monday, target=33
        store.recordTap(today, 33)
        assertTrue(store.isGoalComplete(today))
    }

    @Test
    fun recordTap_newDayClearsProgress() {
        val s = MapSettings()
        val store = store(s)
        store.recordTap(LocalDate(2026, 4, 27), 100)
        store.recordTap(LocalDate(2026, 4, 28), 5) // Tuesday
        assertEquals(5, store.todayProgress(LocalDate(2026, 4, 28)))
    }
}
