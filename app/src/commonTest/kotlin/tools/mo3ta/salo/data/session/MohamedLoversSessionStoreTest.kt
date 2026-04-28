package tools.mo3ta.salo.data.session

import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MohamedLoversSessionStoreTest {

    private lateinit var store: MohamedLoversSessionStore

    @BeforeTest
    fun setup() {
        store = MohamedLoversSessionStore(MapSettings())
    }

    @Test
    fun pendingSession_initially_empty() {
        val session = store.getPendingSession()
        assertNull(session.roundKey)
        assertEquals(0, session.clickCount)
    }

    @Test
    fun incrementPendingClick_accumulates_in_same_round() {
        store.incrementPendingClick("2026-05-01", 1)
        store.incrementPendingClick("2026-05-01", 2)
        val session = store.getPendingSession()
        assertEquals("2026-05-01", session.roundKey)
        assertEquals(3, session.clickCount)
    }

    @Test
    fun incrementPendingClick_resets_on_new_round() {
        store.incrementPendingClick("2026-05-01", 5)
        store.incrementPendingClick("2026-05-08", 1)
        val session = store.getPendingSession()
        assertEquals("2026-05-08", session.roundKey)
        assertEquals(1, session.clickCount)
    }

    @Test
    fun clearPendingSession_removes_stored_session() {
        store.incrementPendingClick("2026-05-01", 3)
        store.clearPendingSession()
        val session = store.getPendingSession()
        assertNull(session.roundKey)
        assertEquals(0, session.clickCount)
    }

    @Test
    fun getOrCreateAlias_stable_across_calls() {
        val first = store.getOrCreateAlias()
        val second = store.getOrCreateAlias()
        assertEquals(first, second)
        assertTrue(first.startsWith("محب محمد "))
    }
}
