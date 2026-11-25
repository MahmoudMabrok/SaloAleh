package tools.mo3ta.salo.ui

import androidx.compose.runtime.Composable

/**
 * False when the platform has disabled motion: Android's animator duration scale
 * (also how "remove animations" is applied) or iOS Reduce Motion.
 */
@Composable
internal expect fun areSystemAnimationsEnabled(): Boolean
