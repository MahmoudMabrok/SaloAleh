package tools.mo3ta.salo.data.session

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
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

    @Test
    fun getOrSetInstallDate_firstCall_storesAndReturns() {
        val s = MapSettings()
        val store = MohamedLoversSessionStore(s)
        val date = LocalDate(2026, 5, 8)
        val result = store.getOrSetInstallDate(date)
        assertEquals("2026-05-08", result)
        // Second call with different date returns the original
        val result2 = store.getOrSetInstallDate(LocalDate(2026, 5, 9))
        assertEquals("2026-05-08", result2)
    }

    @Test
    fun markRecapShown_getRecapShownRound_roundTrip() {
        val s = MapSettings()
        val store = MohamedLoversSessionStore(s)
        assertNull(store.getRecapShownRound())
        store.markRecapShown("2026-05-09")
        assertEquals("2026-05-09", store.getRecapShownRound())
    }

    @Test
    fun personalBestRank_defaultIsMaxInt() {
        val store = MohamedLoversSessionStore(MapSettings())
        assertEquals(Int.MAX_VALUE, store.getPersonalBestRank())
    }

    @Test
    fun updatePersonalBestRank_onlyImproves() {
        val s = MapSettings()
        val store = MohamedLoversSessionStore(s)
        store.updatePersonalBestRank(5)
        assertEquals(5, store.getPersonalBestRank())
        store.updatePersonalBestRank(8) // worse rank — not saved
        assertEquals(5, store.getPersonalBestRank())
        store.updatePersonalBestRank(2) // better rank — saved
        assertEquals(2, store.getPersonalBestRank())
    }

    @Test
    fun lastRoundTaps_defaultZero_roundTrip() {
        val s = MapSettings()
        val store = MohamedLoversSessionStore(s)
        assertEquals(0, store.getLastRoundTaps())
        store.saveLastRoundTaps(420)
        assertEquals(420, store.getLastRoundTaps())
    }
}
