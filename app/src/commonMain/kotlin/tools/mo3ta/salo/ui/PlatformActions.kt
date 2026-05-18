package tools.mo3ta.salo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

expect fun showPlatformToast(message: String)
expect fun copyToClipboard(text: String)
expect fun shareText(text: String)
expect fun getStoreUrl(): String
expect fun canScheduleExactAlarms(): Boolean
expect fun areNotificationsEnabled(): Boolean
expect fun openNotificationSettings()
expect fun requestExactAlarmPermission()
expect fun openStorePage()
expect fun getAppVersion(): String
expect fun shareBitmap(imageBitmap: ImageBitmap)

expect fun launchQrScanner(onResult: (String?) -> Unit)

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun FloatingBubbleButton(roundKey: String?)

@Composable
expect fun BubbleFeaturePromo(roundKey: String?)

@Composable
expect fun TakbeerOverlayButton(autoRemind: Boolean)
