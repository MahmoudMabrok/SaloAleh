package tools.mo3ta.salo.ui.quran

import io.github.sherifshabans.mushaf.DEFAULT_PAGE_NUMBER_SCALE
import io.github.sherifshabans.mushaf.MushafColors
import io.github.sherifshabans.mushaf.MushafSpread
import io.github.sherifshabans.mushaf.PageFrames
import io.github.sherifshabans.mushaf.PageTurn
import io.github.sherifshabans.mushaf.SurahHeader
import io.github.sherifshabans.mushaf.SurahHeaderStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import tools.mo3ta.salo.data.quran.MushafFrameSpacing
import tools.mo3ta.salo.data.quran.MushafPageNumberSize
import tools.mo3ta.salo.data.quran.MushafStyle

class MushafReaderMappingTest {

    @Test
    fun classicDefaultsMatchTheLibrary() {
        val decoration = mushafDecoration(
            style = MushafStyle.Classic,
            divisionMarks = false,
            pageNumberSize = MushafPageNumberSize.Default,
            frameSpacing = MushafFrameSpacing.Default,
        )

        assertTrue(decoration.pageFrame)
        assertEquals(SurahHeaderStyle.CLASSIC, decoration.surahHeaderStyle)
        assertSame(PageFrames.Ornate, decoration.frame)
        assertFalse(decoration.divisionMarks)
        assertEquals(DEFAULT_PAGE_NUMBER_SCALE, decoration.pageNumberScale)
        assertEquals(DEFAULT_PAGE_NUMBER_SCALE, MushafPageNumberSize.Default.scale)
        assertEquals(8f, decoration.textGap)
        assertEquals(5f, decoration.frameOuterMargin)
        assertEquals(2f, decoration.frameInnerMargin)
        assertEquals(1.5f, decoration.frameTopMargin)
        assertEquals(14f, decoration.frameBottomMargin)
        assertEquals(PageTurn.Slide, mushafPageTurn(false))
        assertEquals(PageTurn.Curl, mushafPageTurn(true))
        assertEquals(MushafSpread.Single, mushafSpread(false))
        assertEquals(MushafSpread.Auto, mushafSpread(true))
        assertEquals(MushafColors.Light, mushafColors(false))
        assertEquals(MushafColors.Dark, mushafColors(true))
    }

    @Test
    fun overridesAndPrintFramesArePassedThrough() {
        val madinah = mushafDecoration(
            style = MushafStyle.Madinah,
            divisionMarks = false,
            pageNumberSize = MushafPageNumberSize.Large,
            frameSpacing = MushafFrameSpacing.Wide,
        )
        assertSame(PageFrames.Ornate, madinah.frame)
        assertFalse(madinah.divisionMarks)
        assertEquals(1f, madinah.pageNumberScale)
        assertEquals(5f, madinah.textGap)
        assertEquals(9f, madinah.frameOuterMargin)
        assertEquals(3f, madinah.frameInnerMargin)
        assertEquals(2f, madinah.frameTopMargin)
        assertEquals(19f, madinah.frameBottomMargin)

        val cairo = mushafDecoration(
            MushafStyle.Cairo, true, MushafPageNumberSize.Compact, MushafFrameSpacing.Default,
        )
        assertSame(PageFrames.Cairo, cairo.frame)
        assertEquals(SurahHeader.Cairo, cairo.surahHeader)
        assertEquals(0.5f, cairo.pageNumberScale)

        val diyanet = mushafDecoration(
            MushafStyle.Diyanet, true, MushafPageNumberSize.Default, MushafFrameSpacing.Default,
        )
        assertSame(PageFrames.Diyanet, diyanet.frame)
        assertEquals(SurahHeader.Diyanet, diyanet.surahHeader)

        val indoPak = mushafDecoration(
            MushafStyle.IndoPak, true, MushafPageNumberSize.Default, MushafFrameSpacing.Default,
        )
        assertSame(PageFrames.IndoPak, indoPak.frame)
        assertEquals(SurahHeader.IndoPak, indoPak.surahHeader)
        assertTrue(indoPak.divisionMarks)

        val modern = mushafDecoration(
            MushafStyle.Modern, true, MushafPageNumberSize.Default, MushafFrameSpacing.Default,
        )
        assertEquals(SurahHeaderStyle.MODERN, modern.surahHeaderStyle)
        assertTrue(modern.divisionMarks)
    }
}
