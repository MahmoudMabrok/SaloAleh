package tools.mo3ta.salo.data.session

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MohamedLoversSessionStoreTest {

    private lateinit var store: MohamedLoversSessionStore

    @BeforeTest
    fun setup() {
        store = MohamedLoversSessionStore(MapSettings())
    }

    @Test
    fun pendingSession_initially_empty() {
        val session = store.getPendingSession("2026-05-01")
        assertEquals(0, session.clickCount)
    }

    @Test
    fun incrementPendingClick_accumulates_in_same_round() {
        store.incrementPendingClick("2026-05-01", 1)
        store.incrementPendingClick("2026-05-01", 2)
        val session = store.getPendingSession("2026-05-01")
        assertEquals(3, session.clickCount)
    }

    @Test
    fun incrementPendingClick_separate_rounds_independent() {
        store.incrementPendingClick("2026-05-01", 5)
        store.incrementPendingClick("2026-05-08", 1)
        assertEquals(5, store.getPendingSession("2026-05-01").clickCount)
        assertEquals(1, store.getPendingSession("2026-05-08").clickCount)
    }

    @Test
    fun clearPendingRound_removes_only_that_round() {
        store.incrementPendingClick("2026-05-01", 3)
        store.incrementPendingClick("2026-05-08", 7)
        store.clearPendingRound("2026-05-01")
        assertEquals(0, store.getPendingSession("2026-05-01").clickCount)
        assertEquals(7, store.getPendingSession("2026-05-08").clickCount)
    }

    @Test
    fun getAllPendingRounds_returns_all_nonzero() {
        store.incrementPendingClick("2026-05-01", 3)
        store.incrementPendingClick("2026-05-08", 7)
        val all = store.getAllPendingRounds()
        assertEquals(2, all.size)
        assertEquals(3, all["2026-05-01"])
        assertEquals(7, all["2026-05-08"])
    }

    @Test
    fun getAllPendingRounds_excludes_cleared() {
        store.incrementPendingClick("2026-05-01", 3)
        store.clearPendingRound("2026-05-01")
        val all = store.getAllPendingRounds()
        assertTrue(all.isEmpty())
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
        val date = LocalDate(2026, 5, 8)
        val result = store.getOrSetInstallDate(date)
        assertEquals("2026-05-08", result)
        val result2 = store.getOrSetInstallDate(LocalDate(2026, 5, 9))
        assertEquals("2026-05-08", result2)
    }

    @Test
    fun markRecapShown_getRecapShownRound_roundTrip() {
        store.markRecapShown("2026-05-09")
        assertEquals("2026-05-09", store.getRecapShownRound())
    }

    @Test
    fun personalBestRank_defaultIsMaxInt() {
        assertEquals(Int.MAX_VALUE, store.getPersonalBestRank())
    }

    @Test
    fun updatePersonalBestRank_onlyImproves() {
        store.updatePersonalBestRank(5)
        assertEquals(5, store.getPersonalBestRank())
        store.updatePersonalBestRank(8)
        assertEquals(5, store.getPersonalBestRank())
        store.updatePersonalBestRank(2)
        assertEquals(2, store.getPersonalBestRank())
    }

    @Test
    fun lastRoundTaps_defaultZero_roundTrip() {
        assertEquals(0, store.getLastRoundTaps())
        store.saveLastRoundTaps(420)
        assertEquals(420, store.getLastRoundTaps())
    }

    // --- Score reliability: snapshot-aware decrement ---

    @Test
    fun decrementPendingClick_subtracts_snapshotted_count() {
        store.incrementPendingClick("R1", 5)
        store.decrementPendingClick("R1", 5)
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun decrementPendingClick_preserves_taps_added_after_snapshot() {
        store.incrementPendingClick("R1", 5)
        // Simulate: snapshot = 5, then user taps 3 more during flush
        store.incrementPendingClick("R1", 3)
        // Decrement by snapshot amount (5), expect 3 remaining
        store.decrementPendingClick("R1", 5)
        assertEquals(3, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun decrementPendingClick_floors_at_zero() {
        store.incrementPendingClick("R1", 2)
        store.decrementPendingClick("R1", 10)
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun decrementPendingClick_removes_from_index_when_zero() {
        store.incrementPendingClick("R1", 5)
        store.decrementPendingClick("R1", 5)
        assertTrue(store.getAllPendingRounds().isEmpty())
    }

    @Test
    fun decrementPendingClick_keeps_in_index_when_nonzero() {
        store.incrementPendingClick("R1", 5)
        store.incrementPendingClick("R1", 3)
        store.decrementPendingClick("R1", 5)
        val pending = store.getAllPendingRounds()
        assertEquals(1, pending.size)
        assertEquals(3, pending["R1"])
    }

    // --- Legacy migration ---

    @Test
    fun legacy_migration_moves_old_pending_to_new_format() {
        val s = MapSettings()
        s.putString("pending_round_key", "2026-05-01")
        s.putInt("pending_click_count", 10)
        val store = MohamedLoversSessionStore(s)
        val session = store.getPendingSession("2026-05-01")
        assertEquals(10, session.clickCount)
    }
}
