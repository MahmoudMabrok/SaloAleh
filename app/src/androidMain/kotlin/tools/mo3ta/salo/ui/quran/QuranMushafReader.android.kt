package tools.mo3ta.salo.ui.quran

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sherifshabans.mushaf.Ayah
import io.github.sherifshabans.mushaf.MushafColors
import io.github.sherifshabans.mushaf.MushafFont
import io.github.sherifshabans.mushaf.MushafPager
import io.github.sherifshabans.mushaf.Quran
import io.github.sherifshabans.mushaf.rememberMushafPagerState
import io.github.sherifshabans.mushaf.toArabicDigits
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tools.mo3ta.salo.data.quran.MushafReaderPreferences
import tools.mo3ta.salo.data.quran.PageDwellCounter

actual val mushafReaderAvailable: Boolean = true

/** The Mushaf is drawn from text inside the app, so it is always ready: nothing to download. */
@Composable
actual fun rememberMushafAccess(): MushafAccess =
    remember { MushafAccess(MushafStatus.Ready, null, startDownload = {}) }

@Composable
actual fun QuranMushafReader(
    initialPage: Int?,
    alreadyCounted: Set<Int>,
    onPageQualified: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val prefs: MushafReaderPreferences = koinInject()
    val startPage = remember(initialPage) {
        (initialPage ?: MushafReaderPrefs.lastPage(context)).coerceIn(1, Quran.PAGE_COUNT)
    }
    val decoration = remember(prefs.style, prefs.divisionMarks, prefs.pageNumberSize, prefs.frameSpacing) {
        mushafDecoration(prefs.style, prefs.divisionMarks, prefs.pageNumberSize, prefs.frameSpacing)
    }
    val colors = mushafColors(prefs.darkPaper)
    // Collected, so a change in Settings reaches an open reader. Absent key is curl.
    val pageCurl by prefs.pageCurlFlow.collectAsState()
    val pageTurn = mushafPageTurn(pageCurl)
    val spread = mushafSpread(prefs.twoPageSpread)
    val state = rememberMushafPagerState(initialPage = startPage)
    val counter = remember { PageDwellCounter(alreadyCounted) }
    val credit by rememberUpdatedState(onPageQualified)
    // The frame draws the surah/juz running head when division marks are on.
    val frameOwnsChrome = decoration.pageFrame && decoration.divisionMarks

    // A page counts after it has been on screen for a while (PageDwellCounter).
    LaunchedEffect(state, counter) {
        snapshotFlow { state.currentPage + 1 }.collect { page ->
            MushafReaderPrefs.setLastPage(context, page)
            counter.onPageRendered(page, SystemClock.elapsedRealtime())?.let(credit)
        }
    }
    LaunchedEffect(state, counter) {
        while (true) {
            delay(1_000)
            counter.onPageRendered(state.currentPage + 1, SystemClock.elapsedRealtime())?.let(credit)
        }
    }
    DisposableEffect(counter) {
        onDispose { counter.flush(SystemClock.elapsedRealtime())?.let(credit) }
    }

    // Closes with the system back button; no on-screen close button. The dialog
    // window already sits between the status and navigation bars, so no extra insets.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The next page comes in from the left, like a printed Mushaf, whatever the app language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier
                    .fillMaxSize()
                    .background(colors.paper),
            ) {
                if (!frameOwnsChrome) {
                    ReaderRunningHeader(page = state.currentPage + 1, colors = colors)
                }
                MushafPager(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = state,
                    colors = colors,
                    decoration = decoration,
                    // The frame medallion is the page number. A library footer would repeat it.
                    showHeaders = false,
                    pageTurn = pageTurn,
                    spread = spread,
                    pagePadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Surah and juz above the page, when the frame is not drawing that head itself. */
@Composable
private fun ReaderRunningHeader(page: Int, colors: MushafColors) {
    val context = LocalContext.current
    val ayahs by produceState<List<Ayah>?>(null, page) {
        value = Quran.load(context).filter { it.page == page }
    }
    val first = ayahs?.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = first?.let { "سُورَةُ " + Quran.surah(it.surah).nameArabic } ?: "",
            style = TextStyle(fontFamily = MushafFont, fontSize = 15.sp, color = colors.marker),
        )
        BasicText(
            text = first?.let { "الجزء " + toArabicDigits(it.juz) } ?: "",
            style = TextStyle(fontSize = 15.sp, color = colors.marker),
        )
    }
}

/** Remembers the last page read so "continue" opens it again. */
private object MushafReaderPrefs {
    private const val FILE = "mushaf_text_reader"
    private const val KEY_LAST_PAGE = "last_page"

    fun lastPage(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_LAST_PAGE, 1)

    fun setLastPage(context: Context, page: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_LAST_PAGE, page).apply()
    }
}
