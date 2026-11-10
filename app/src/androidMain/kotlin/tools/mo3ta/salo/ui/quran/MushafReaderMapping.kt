package tools.mo3ta.salo.ui.quran

import io.github.sherifshabans.mushaf.MushafColors
import io.github.sherifshabans.mushaf.MushafDecoration
import io.github.sherifshabans.mushaf.MushafSpread
import io.github.sherifshabans.mushaf.PageTurn
import tools.mo3ta.salo.data.quran.MushafFrameSpacing
import tools.mo3ta.salo.data.quran.MushafPageNumberSize
import tools.mo3ta.salo.data.quran.MushafStyle

/** The decoration the reader draws for the saved options. */
internal fun mushafDecoration(
    style: MushafStyle,
    divisionMarks: Boolean,
    pageNumberSize: MushafPageNumberSize,
    frameSpacing: MushafFrameSpacing,
): MushafDecoration {
    val base = when (style) {
        MushafStyle.Classic -> MushafDecoration.Classic
        MushafStyle.Modern -> MushafDecoration.Modern
        MushafStyle.Madinah -> MushafDecoration.Madinah
        MushafStyle.Cairo -> MushafDecoration.Cairo
        MushafStyle.Diyanet -> MushafDecoration.Diyanet
        MushafStyle.IndoPak -> MushafDecoration.IndoPak
    }
    return base.copy(
        divisionMarks = divisionMarks,
        pageNumberScale = pageNumberSize.scale,
        textGap = frameSpacing.textGap,
        frameOuterMargin = frameSpacing.outer,
        frameInnerMargin = frameSpacing.inner,
        frameTopMargin = frameSpacing.top,
        frameBottomMargin = frameSpacing.bottom,
    )
}

internal fun mushafColors(darkPaper: Boolean): MushafColors =
    if (darkPaper) MushafColors.Dark else MushafColors.Light

internal fun mushafPageTurn(pageCurl: Boolean): PageTurn =
    if (pageCurl) PageTurn.Curl else PageTurn.Slide

internal fun mushafSpread(twoPageSpread: Boolean): MushafSpread =
    if (twoPageSpread) MushafSpread.Auto else MushafSpread.Single
