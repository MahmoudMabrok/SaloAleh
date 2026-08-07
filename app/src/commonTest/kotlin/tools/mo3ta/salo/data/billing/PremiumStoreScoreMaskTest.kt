package tools.mo3ta.salo.data.billing

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremiumStoreScoreMaskTest {

    @Test
    fun mask_persists_within_the_same_round() {
        val store = PremiumStore(MapSettings())
        store.isScoreMasked = true
        store.scoreMaskedRoundKey = "2026-05-15"

        store.clearScoreMaskOnNewRound("2026-05-15")

        assertTrue(store.isScoreMasked)
        assertEquals("2026-05-15", store.scoreMaskedRoundKey)
    }

    @Test
    fun mask_is_cleared_when_a_new_round_starts() {
        val store = PremiumStore(MapSettings())
        store.isScoreMasked = true
        store.scoreMaskedRoundKey = "2026-05-15"

        store.clearScoreMaskOnNewRound("2026-05-22")

        assertFalse(store.isScoreMasked)
        assertNull(store.scoreMaskedRoundKey)
    }

    @Test
    fun legacy_mask_without_round_adopts_current_round_then_clears_next() {
        val store = PremiumStore(MapSettings())
        // Masking enabled before round tracking existed: flag on, no round recorded.
        store.isScoreMasked = true
        assertNull(store.scoreMaskedRoundKey)

        store.clearScoreMaskOnNewRound("2026-05-15")
        assertTrue(store.isScoreMasked)
        assertEquals("2026-05-15", store.scoreMaskedRoundKey)

        store.clearScoreMaskOnNewRound("2026-05-22")
        assertFalse(store.isScoreMasked)
        assertNull(store.scoreMaskedRoundKey)
    }

    @Test
    fun disabled_mask_clears_stale_round_key() {
        val store = PremiumStore(MapSettings())
        store.isScoreMasked = false
        store.scoreMaskedRoundKey = "2026-05-15"

        store.clearScoreMaskOnNewRound("2026-05-22")

        assertFalse(store.isScoreMasked)
        assertNull(store.scoreMaskedRoundKey)
    }

    @Test
    fun blank_round_key_is_ignored() {
        val store = PremiumStore(MapSettings())
        store.isScoreMasked = true
        store.scoreMaskedRoundKey = "2026-05-15"

        store.clearScoreMaskOnNewRound("")

        assertTrue(store.isScoreMasked)
        assertEquals("2026-05-15", store.scoreMaskedRoundKey)
    }
}
