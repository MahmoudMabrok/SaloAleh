package tools.mo3ta.salo.data.quran

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MushafReaderPreferencesTest {

    @Test
    fun defaultsMatchTheLibraryAndTheCurrentReader() {
        val prefs = MushafReaderPreferences(MapSettings())

        assertEquals(MushafStyle.Classic, prefs.style)
        assertFalse(prefs.darkPaper)
        assertTrue(prefs.pageCurl)
        assertTrue(prefs.pageCurlFlow.value)
        assertFalse(prefs.twoPageSpread)
        assertFalse(prefs.divisionMarks)
        assertEquals(MushafPageNumberSize.Default, prefs.pageNumberSize)
        assertEquals(0.72f, prefs.pageNumberSize.scale)
        assertEquals(MushafFrameSpacing.Default, prefs.frameSpacing)
        assertEquals(8f, prefs.frameSpacing.textGap)
        assertEquals(5f, prefs.frameSpacing.outer)
        assertEquals(2f, prefs.frameSpacing.inner)
        assertEquals(1.5f, prefs.frameSpacing.top)
        assertEquals(14f, prefs.frameSpacing.bottom)
    }

    @Test
    fun roundTripAndUnknownKeysFallBack() {
        val settings = MapSettings()
        val prefs = MushafReaderPreferences(settings)

        prefs.style = MushafStyle.Diyanet
        prefs.darkPaper = true
        prefs.pageCurl = true
        prefs.twoPageSpread = true
        prefs.divisionMarks = true
        prefs.pageNumberSize = MushafPageNumberSize.Large
        prefs.frameSpacing = MushafFrameSpacing.Wide

        val again = MushafReaderPreferences(settings)
        assertEquals(MushafStyle.Diyanet, again.style)
        assertEquals(true, again.darkPaper)
        assertEquals(true, again.pageCurl)
        assertEquals(true, again.twoPageSpread)
        assertEquals(true, again.divisionMarks)
        assertEquals(MushafPageNumberSize.Large, again.pageNumberSize)
        assertEquals(1f, again.pageNumberSize.scale)
        assertEquals(MushafFrameSpacing.Wide, again.frameSpacing)
        assertEquals(9f, again.frameSpacing.outer)
        assertEquals(19f, again.frameSpacing.bottom)
        assertEquals(5f, again.frameSpacing.textGap)

        settings.putString("mushaf_style", "nope")
        settings.putString("mushaf_page_number", "nope")
        settings.putString("mushaf_frame_spacing", "nope")
        assertEquals(MushafStyle.Classic, prefs.style)
        assertEquals(MushafPageNumberSize.Default, prefs.pageNumberSize)
        assertEquals(MushafFrameSpacing.Default, prefs.frameSpacing)
    }

    @Test
    fun storedCurlOffStaysOffAndTheFlowFollowsWrites() {
        val settings = MapSettings()
        settings.putBoolean("mushaf_page_curl", false)
        val prefs = MushafReaderPreferences(settings)

        assertFalse(prefs.pageCurl)
        assertFalse(prefs.pageCurlFlow.value)

        prefs.pageCurl = true
        assertTrue(prefs.pageCurlFlow.value)
        assertTrue(settings.getBoolean("mushaf_page_curl", false))

        prefs.pageCurl = false
        assertFalse(prefs.pageCurlFlow.value)
        assertFalse(settings.getBoolean("mushaf_page_curl", true))
    }

    @Test
    fun printStylesTurnDivisionMarksOnByDefault() {
        assertFalse(MushafStyle.Classic.divisionMarksByDefault)
        assertFalse(MushafStyle.Modern.divisionMarksByDefault)
        assertEquals(true, MushafStyle.Madinah.divisionMarksByDefault)
        assertEquals(true, MushafStyle.Cairo.divisionMarksByDefault)
        assertEquals(true, MushafStyle.Diyanet.divisionMarksByDefault)
        assertEquals(true, MushafStyle.IndoPak.divisionMarksByDefault)
    }
}
