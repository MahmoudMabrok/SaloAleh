package tools.mo3ta.salo.ui.settings

import androidx.compose.runtime.Composable

expect val dhikrModelTestAvailable: Boolean

@Composable
expect fun DhikrModelTestScreen(onBack: () -> Unit)
