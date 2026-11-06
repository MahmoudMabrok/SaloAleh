package tools.mo3ta.salo.data.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageDwellCounterTest {

    @Test fun pageVisibleForFifteenSecondsDoesNotCount() {
        val counter = PageDwellCounter()
        counter.onPageRendered(page = 2, nowMs = 0L)
        assertNull(counter.onPageRendered(page = 2, nowMs = 15_000L))
        assertEquals(emptySet(), counter.countedPages())
    }

    @Test fun pageVisibleForMoreThanFifteenSecondsCountsOnce() {
        val counter = PageDwellCounter()
        counter.onPageRendered(page = 2, nowMs = 0L)
        assertEquals(2, counter.onPageRendered(page = 2, nowMs = 15_001L))
        assertNull(counter.onPageRendered(page = 2, nowMs = 40_000L))
        assertEquals(setOf(2), counter.countedPages())
    }

    @Test fun revisitingACountedPageIsADuplicate() {
        val counter = PageDwellCounter()
        counter.onPageRendered(page = 5, nowMs = 0L)
        assertEquals(5, counter.flush(nowMs = 20_000L))
        counter.onPageRendered(page = 5, nowMs = 30_000L)
        assertNull(counter.flush(nowMs = 60_000L))
        assertEquals(setOf(5), counter.countedPages())
    }

    @Test fun flickingPastAPageDoesNotCountIt() {
        val counter = PageDwellCounter()
        counter.onPageRendered(page = 1, nowMs = 0L)
        assertNull(counter.onPageRendered(page = 2, nowMs = 4_000L))
        assertEquals(2, counter.flush(nowMs = 20_000L))
        assertEquals(setOf(2), counter.countedPages())
    }

    @Test fun leavingBeforeTheThresholdDoesNotCount() {
        val counter = PageDwellCounter()
        counter.onPageRendered(page = 3, nowMs = 1_000L)
        assertNull(counter.flush(nowMs = 10_000L))
        assertEquals(emptySet(), counter.countedPages())
    }

    @Test fun pagesAlreadyCountedTodayAreNotCountedAgain() {
        val counter = PageDwellCounter(alreadyCounted = setOf(7))
        counter.onPageRendered(page = 7, nowMs = 0L)
        assertNull(counter.flush(nowMs = 30_000L))
        assertEquals(setOf(7), counter.countedPages())
    }

    @Test fun pageNumbersOutsideTheMushafAreIgnored() {
        val counter = PageDwellCounter()
        assertNull(counter.onPageRendered(page = 0, nowMs = 0L))
        assertNull(counter.onPageRendered(page = 605, nowMs = 0L))
        assertNull(counter.flush(nowMs = 30_000L))
        assertEquals(emptySet(), counter.countedPages())
    }
}
