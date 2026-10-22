package tools.mo3ta.salo.ui.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

actual val mushafReaderAvailable: Boolean = false

@Composable
@Suppress("UNUSED_PARAMETER")
actual fun QuranMushafReader(
    alreadyCounted: Set<Int>,
    onPageQualified: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { onClose() }
}
