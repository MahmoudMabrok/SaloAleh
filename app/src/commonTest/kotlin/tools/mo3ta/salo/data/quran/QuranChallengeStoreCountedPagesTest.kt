package tools.mo3ta.salo.data.quran

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuranChallengeStoreCountedPagesTest {

    private val day = LocalDate(2026, 10, 22)
    private val nextDay = LocalDate(2026, 10, 23)

    @Test fun renderedPageCountsOnceAndADuplicateDoesNot() {
        val store = QuranChallengeStore(MapSettings())
        assertEquals(1, store.countRenderedPage(day, 12))
        assertNull(store.countRenderedPage(day, 12))
        assertEquals(1, store.todayCount(day))
        assertEquals(setOf(12), store.countedPages(day))
        assertEquals(1, store.lifetimeCount())
    }

    @Test fun distinctPagesEachCount() {
        val store = QuranChallengeStore(MapSettings())
        assertEquals(1, store.countRenderedPage(day, 1))
        assertEquals(2, store.countRenderedPage(day, 604))
        assertEquals(setOf(1, 604), store.countedPages(day))
    }

    @Test fun pagesOutsideTheMushafAreRejected() {
        val store = QuranChallengeStore(MapSettings())
        assertNull(store.countRenderedPage(day, 0))
        assertNull(store.countRenderedPage(day, 605))
        assertEquals(0, store.todayCount(day))
    }

    @Test fun countedPagesResetOnTheNextDay() {
        val store = QuranChallengeStore(MapSettings())
        store.countRenderedPage(day, 9)
        assertEquals(emptySet(), store.countedPages(nextDay))
        assertEquals(1, store.countRenderedPage(nextDay, 9))
        assertEquals(0, store.todayCount(day))
        assertEquals(1, store.todayCount(nextDay))
    }
}
