package tools.mo3ta.salo.ui.quran

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mushafimad.ui.mushaf.MushafView
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.data.quran.PageDwellCounter
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.quran_mushaf_close

actual val mushafReaderAvailable: Boolean = true

@Composable
actual fun QuranMushafReader(
    alreadyCounted: Set<Int>,
    onPageQualified: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val counter = remember { PageDwellCounter(alreadyCounted) }
    var page by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onClose)
    DisposableEffect(counter) {
        onDispose {
            counter.flush(SystemClock.elapsedRealtime())?.let(onPageQualified)
        }
    }
    LaunchedEffect(counter) {
        while (true) {
            delay(1_000)
            val current = page
            if (current in 1..604) {
                counter.onPageRendered(current, SystemClock.elapsedRealtime())?.let(onPageQualified)
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        MushafView(
            onPageChanged = { next ->
                page = next
                counter.onPageRendered(next, SystemClock.elapsedRealtime())?.let(onPageQualified)
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.quran_mushaf_close),
                tint = Color(0xFF1A4035),
            )
        }
    }
}
