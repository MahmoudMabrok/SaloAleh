package tools.mo3ta.salo.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AhlAdhikrScreenTest {

    @Test
    fun countryCodeToFlag_withTwoLetterCode_returnsRegionalIndicatorFlag() {
        assertEquals("🇪🇬", countryCodeToFlag("EG"))
        assertEquals("🇸🇦", countryCodeToFlag("sa"))
    }

    @Test
    fun countryCodeToFlag_withMissingOrShortCode_fallsBackToGlobe() {
        assertEquals("🌍", countryCodeToFlag(""))
        assertEquals("🌍", countryCodeToFlag("E"))
    }

    @Test
    fun metaLine_withNicknameAndCountry_showsIdAndCountry() {
        assertEquals("2FD00B · EG", ahlAdhikrMetaLine(name = "محمود", id = "2FD00B", countryCode = "EG"))
    }

    @Test
    fun metaLine_whenNameIsThePublicId_doesNotRepeatIt() {
        assertEquals("EG", ahlAdhikrMetaLine(name = "710C42", id = "710C42", countryCode = "EG"))
    }

    @Test
    fun metaLine_withoutCountry_keepsOnlyTheId() {
        assertEquals("2FD00B", ahlAdhikrMetaLine(name = "محمود", id = "2FD00B", countryCode = ""))
    }

    @Test
    fun metaLine_whenNameIsThePublicIdAndNoCountry_isEmpty() {
        assertNull(ahlAdhikrMetaLine(name = "710C42", id = "710C42", countryCode = ""))
    }
}
