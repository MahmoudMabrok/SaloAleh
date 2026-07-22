package tools.mo3ta.salo.data.reminder

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlKahfReminderStoreTest {

    private fun store(settings: MapSettings = MapSettings()) = AlKahfReminderStore(settings)

    // 2026-07-24 is a Friday; 2026-07-23 a Thursday; 2026-07-31 the next Friday.
    private val friday = LocalDate(2026, 7, 24)
    private val thursday = LocalDate(2026, 7, 23)
    private val nextFriday = LocalDate(2026, 7, 31)

    @Test
    fun shows_on_a_fresh_friday() {
        assertTrue(store().shouldShow(friday))
    }

    @Test
    fun does_not_show_on_a_non_friday() {
        assertFalse(store().shouldShow(thursday))
    }

    @Test
    fun does_not_show_twice_on_the_same_friday() {
        val store = store()
        store.markShown(friday)
        assertFalse(store.shouldShow(friday))
    }

    @Test
    fun shows_again_the_following_friday() {
        val store = store()
        store.markShown(friday)
        assertTrue(store.shouldShow(nextFriday))
    }
}
