package tools.mo3ta.salo.data.update

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatePromptStoreTest {

    private fun store(settings: MapSettings = MapSettings()) = UpdatePromptStore(settings)

    @Test
    fun fresh_store_has_no_dismissed_version() {
        assertFalse(store().isDismissedFor("3.9.2"))
    }

    @Test
    fun dismissed_version_is_remembered() {
        val store = store()
        store.markDismissed("3.9.2")
        assertTrue(store.isDismissedFor("3.9.2"))
    }

    @Test
    fun dismissing_one_version_does_not_suppress_another() {
        val store = store()
        store.markDismissed("3.9.2")
        assertFalse(store.isDismissedFor("3.9.3"))
    }

    @Test
    fun latest_dismissal_replaces_previous() {
        val store = store()
        store.markDismissed("3.9.2")
        store.markDismissed("4.0.0")
        assertFalse(store.isDismissedFor("3.9.2"))
        assertTrue(store.isDismissedFor("4.0.0"))
    }
}
