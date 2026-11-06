package tools.mo3ta.salo.ui.quran

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** True where [QuranMushafReader] can show the Mushaf Imad reader. Android only. */
expect val mushafReaderAvailable: Boolean

/**
 * Full-screen Mushaf opened from the Quran challenge.
 * [initialPage] is null to restore the library's last page.
 * [onPageQualified] fires for a page that stayed rendered for more than 15 seconds
 * and has not already been counted.
 */
@Composable
expect fun QuranMushafReader(
    initialPage: Int?,
    alreadyCounted: Set<Int>,
    onPageQualified: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)
