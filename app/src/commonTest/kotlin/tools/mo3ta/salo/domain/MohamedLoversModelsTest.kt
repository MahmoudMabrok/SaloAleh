package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MohamedLoversModelsTest {

    @Test
    fun displayTag_uses_last6_of_uid_and_uppercased_country() {
        assertEquals("EG • ABCD12", buildMohamedLoversDisplayTag("xxxxxABCD12", "eg"))
    }

    @Test
    fun displayTag_blank_uid_uses_dashes() {
        assertEquals("EG • ------", buildMohamedLoversDisplayTag("", "eg"))
    }

    @Test
    fun displayTag_blank_country_uses_NA() {
        assertEquals("NA • ABCDEF", buildMohamedLoversDisplayTag("xxxxxxABCDEF", ""))
    }
}
