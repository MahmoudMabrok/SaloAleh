package tools.mo3ta.salo.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

actual val dhikrModelTestAvailable: Boolean = false

@Composable
actual fun DhikrModelTestScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}
