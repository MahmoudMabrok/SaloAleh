package tools.mo3ta.salo.data.heart

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class HeartStoreTest {

    @Test
    fun defaults_are_zero() {
        val store = HeartStore(MapSettings())
        assertEquals(0, store.getScore())
        assertEquals(0L, store.getAnchorTs())
    }

    @Test
    fun roundtrips_score_and_anchor() {
        val store = HeartStore(MapSettings())
        store.save(42, 123_456L)
        assertEquals(42, store.getScore())
        assertEquals(123_456L, store.getAnchorTs())
    }

    @Test
    fun roundtrips_negative_score() {
        val store = HeartStore(MapSettings())
        store.save(-9, 123_456L)
        assertEquals(-9, store.getScore())
        assertEquals(123_456L, store.getAnchorTs())
    }
}
