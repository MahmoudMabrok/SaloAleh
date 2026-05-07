package tools.mo3ta.salo.ui

import tools.mo3ta.salo.ui.components.ShareCardData

expect fun showPlatformToast(message: String)
expect fun copyToClipboard(text: String)
expect fun shareText(text: String)
expect fun areNotificationsEnabled(): Boolean
expect fun openNotificationSettings()
expect fun getAppVersion(): String
expect fun shareImage(data: ShareCardData)
