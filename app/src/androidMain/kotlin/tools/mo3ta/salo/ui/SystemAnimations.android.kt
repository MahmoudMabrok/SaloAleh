package tools.mo3ta.salo.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun areSystemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    // Read once per visit. The pulse recomposes every frame, and this setting
    // is a content-resolver lookup.
    val scale = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    return scale > 0f
}
