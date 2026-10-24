package tools.mo3ta.salo.data.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MushafPageTest {
    @Test
    fun acceptsAPageInsideTheMushaf() {
        assertEquals(14, parseMushafPage("14"))
        assertEquals(604, parseMushafPage("٦٠٤"))
        assertEquals(2, parseMushafPage(" ۲ "))
    }

    @Test
    fun rejectsBlankOutOfRangeAndJunk() {
        assertNull(parseMushafPage(""))
        assertNull(parseMushafPage("0"))
        assertNull(parseMushafPage("605"))
        assertNull(parseMushafPage("page"))
    }
}
