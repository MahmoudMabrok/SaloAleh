package tools.mo3ta.salo.ui.quran

import androidx.compose.runtime.Composable

/** Whether the Mushaf reader can open, from the challenge screen's point of view. */
enum class MushafStatus {
    /** This platform has no Mushaf reader. */
    Unavailable,

    /** Installed app does not contain the pages yet. */
    NeedsDownload,
    Downloading,

    /** Just installed. The new code loads only after the process starts again. */
    NeedsRestart,
    Ready,
    Failed,
}

data class MushafAccess(
    val status: MushafStatus,
    val progress: Float?,
    val startDownload: () -> Unit,
    val restartApp: () -> Unit = {},
)

@Composable
expect fun rememberMushafAccess(): MushafAccess
