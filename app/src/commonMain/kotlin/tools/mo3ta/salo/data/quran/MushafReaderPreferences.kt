package tools.mo3ta.salo.data.quran

import com.russhwolf.settings.Settings

/**
 * Reader options for the text Mushaf. Stored on the device (not the account):
 * [tools.mo3ta.salo.data.session.LocalAccountReset] keeps these keys.
 *
 * Defaults match mushaf-text 1.6.0 and the look the
 * reader already shipped: Classic frame, light paper, slide, one page,
 * division marks off, page-number scale 0.72, library frame margins.
 */
class MushafReaderPreferences(private val settings: Settings) {
    var style: MushafStyle
        get() = MushafStyle.fromKey(settings.getString(KEY_STYLE, MushafStyle.Classic.key))
        set(value) = settings.putString(KEY_STYLE, value.key)

    var darkPaper: Boolean
        get() = settings.getBoolean(KEY_DARK, false)
        set(value) = settings.putBoolean(KEY_DARK, value)

    var pageCurl: Boolean
        get() = settings.getBoolean(KEY_CURL, false)
        set(value) = settings.putBoolean(KEY_CURL, value)

    var twoPageSpread: Boolean
        get() = settings.getBoolean(KEY_SPREAD, false)
        set(value) = settings.putBoolean(KEY_SPREAD, value)

    /** Margin medallions and the running head inside the frame. */
    var divisionMarks: Boolean
        get() = settings.getBoolean(KEY_MARKS, false)
        set(value) = settings.putBoolean(KEY_MARKS, value)

    var pageNumberSize: MushafPageNumberSize
        get() = MushafPageNumberSize.fromKey(settings.getString(KEY_PAGE_NUMBER, MushafPageNumberSize.Default.key))
        set(value) = settings.putString(KEY_PAGE_NUMBER, value.key)

    var frameSpacing: MushafFrameSpacing
        get() = MushafFrameSpacing.fromKey(settings.getString(KEY_FRAME_SPACING, MushafFrameSpacing.Default.key))
        set(value) = settings.putString(KEY_FRAME_SPACING, value.key)

    private companion object {
        const val KEY_STYLE = "mushaf_style"
        const val KEY_DARK = "mushaf_dark"
        const val KEY_CURL = "mushaf_page_curl"
        const val KEY_SPREAD = "mushaf_spread"
        const val KEY_MARKS = "mushaf_division_marks"
        const val KEY_PAGE_NUMBER = "mushaf_page_number"
        const val KEY_FRAME_SPACING = "mushaf_frame_spacing"
    }
}

/** Printed look. Page breaks stay Madinah for every value. */
enum class MushafStyle(val key: String, val divisionMarksByDefault: Boolean) {
    Classic("classic", false),
    Modern("modern", false),
    Madinah("madinah", true),
    Cairo("cairo", true),
    Diyanet("diyanet", true),
    IndoPak("indopak", true),
    ;

    companion object {
        fun fromKey(key: String): MushafStyle = entries.firstOrNull { it.key == key } ?: Classic
    }
}

/**
 * How much of the frame medallion the page number fills.
 * [Default] is the library's `DEFAULT_PAGE_NUMBER_SCALE` (0.72).
 * [Large] is 1, which fills the disk. [Compact] is smaller than the default.
 */
enum class MushafPageNumberSize(val key: String, val scale: Float) {
    Compact("compact", 0.5f),
    Default("default", 0.72f),
    Large("large", 1f),
    ;

    companion object {
        fun fromKey(key: String): MushafPageNumberSize = entries.firstOrNull { it.key == key } ?: Default
    }
}

/**
 * Frame insets on [MushafDecoration].
 * [Default] is the library default (text gap 8, outer 5, inner 2, top 1.5, bottom 14).
 * [Wide] is the spacing from before those defaults changed (5 / 9 / 3 / 2 / 19).
 */
enum class MushafFrameSpacing(
    val key: String,
    val textGap: Float,
    val outer: Float,
    val inner: Float,
    val top: Float,
    val bottom: Float,
) {
    Default("default", textGap = 8f, outer = 5f, inner = 2f, top = 1.5f, bottom = 14f),
    Wide("wide", textGap = 5f, outer = 9f, inner = 3f, top = 2f, bottom = 19f),
    ;

    companion object {
        fun fromKey(key: String): MushafFrameSpacing = entries.firstOrNull { it.key == key } ?: Default
    }
}
