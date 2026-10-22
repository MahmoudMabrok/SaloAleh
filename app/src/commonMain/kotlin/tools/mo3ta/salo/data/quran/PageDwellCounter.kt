package tools.mo3ta.salo.data.quran

/**
 * Credits a Mushaf page only after it has stayed rendered for more than
 * [minVisibleMs]. A page is credited at most once; later visits are duplicates.
 */
class PageDwellCounter(
    alreadyCounted: Set<Int> = emptySet(),
    private val minVisibleMs: Long = MIN_VISIBLE_MS,
) {
    private val counted = alreadyCounted.filter { it in PAGE_RANGE }.toMutableSet()
    private var openPage: Int? = null
    private var shownAtMs: Long = 0L

    /** Report the page now on screen. Returns a newly qualified page, if any. */
    fun onPageRendered(page: Int, nowMs: Long): Int? {
        if (page !in PAGE_RANGE) return null
        if (openPage == page) return creditIfDue(nowMs)
        val credited = creditIfDue(nowMs)
        openPage = page
        shownAtMs = nowMs
        return credited
    }

    /** Close the open page (reader dismissed) and credit it if it stayed long enough. */
    fun flush(nowMs: Long): Int? {
        val credited = creditIfDue(nowMs)
        openPage = null
        return credited
    }

    fun countedPages(): Set<Int> = counted.toSet()

    private fun creditIfDue(nowMs: Long): Int? {
        val page = openPage ?: return null
        if (page in counted) return null
        if (nowMs - shownAtMs <= minVisibleMs) return null
        counted.add(page)
        return page
    }

    private companion object {
        const val MIN_VISIBLE_MS = 15_000L
        val PAGE_RANGE = 1..604
    }
}
