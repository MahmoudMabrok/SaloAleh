package tools.mo3ta.salo.ui

import androidx.compose.ui.graphics.ImageBitmap

expect fun showPlatformToast(message: String)
expect fun copyToClipboard(text: String)
expect fun shareText(text: String)
expect fun areNotificationsEnabled(): Boolean
expect fun openNotificationSettings()
expect fun getAppVersion(): String
expect fun shareBitmap(imageBitmap: ImageBitmap)
