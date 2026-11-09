package tools.mo3ta.salo.ui.quran

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.sherifshabans.mushaf.Ayah
import io.github.sherifshabans.mushaf.MushafFont
import io.github.sherifshabans.mushaf.MushafPage
import io.github.sherifshabans.mushaf.toArabicDigits
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sherifshabans.mushaf.MushafColors
import io.github.sherifshabans.mushaf.MushafDecoration
import io.github.sherifshabans.mushaf.Quran
import io.github.sherifshabans.mushaf.rememberMushafPagerState
import kotlinx.coroutines.delay
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
    val startPage = remember(initialPage) {
        (initialPage ?: MushafReaderPrefs.lastPage(context)).coerceIn(1, Quran.PAGE_COUNT)
    }
    val state = rememberMushafPagerState(initialPage = startPage)
    val counter = remember { PageDwellCounter(alreadyCounted) }
    val credit by rememberUpdatedState(onPageQualified)

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
            HorizontalPager(
                state = state,
                modifier = modifier
                    .fillMaxSize()
                    .background(MushafColors.Light.paper),
                beyondViewportPageCount = 1,
                key = { it },
            ) { index ->
                ReaderPage(page = index + 1)
            }
        }
    }
}

/**
 * One page: surah and juz on top, then the framed page. No footer: the Classic
 * frame already shows the page number in its medallion.
 */
@Composable
private fun ReaderPage(page: Int) {
    val context = LocalContext.current
    val ayahs by produceState<List<Ayah>?>(null, page) {
        value = Quran.load(context).filter { it.page == page }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        val first = ayahs?.firstOrNull()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = first?.let { "سُورَةُ " + Quran.surah(it.surah).nameArabic } ?: "",
                style = TextStyle(fontFamily = MushafFont, fontSize = 15.sp, color = MushafColors.Light.marker),
            )
            BasicText(
                text = first?.let { "الجزء " + toArabicDigits(it.juz) } ?: "",
                style = TextStyle(fontSize = 15.sp, color = MushafColors.Light.marker),
            )
        }
        MushafPage(
            ayahs = ayahs ?: emptyList(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = MushafColors.Light,
            decoration = MushafDecoration.Classic,
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
