package tools.mo3ta.salo.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun areSystemAnimationsEnabled(): Boolean = !UIAccessibilityIsReduceMotionEnabled()
