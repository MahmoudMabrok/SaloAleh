package tools.mo3ta.salo.ui.quran

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sherifshabans.mushaf.MushafColors
import io.github.sherifshabans.mushaf.MushafDecoration
import io.github.sherifshabans.mushaf.MushafPager
import io.github.sherifshabans.mushaf.Quran
import io.github.sherifshabans.mushaf.rememberMushafPagerState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.data.quran.PageDwellCounter
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.quran_back_cd

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

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(MushafColors.Light.paper)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            MushafPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
                colors = MushafColors.Light,
                decoration = MushafDecoration.Classic,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.quran_back_cd),
                    tint = Color(0xFF5B4A2E),
                )
            }
        }
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
