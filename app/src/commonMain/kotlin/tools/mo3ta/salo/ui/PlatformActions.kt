package tools.mo3ta.salo.ui

expect fun showPlatformToast(message: String)
expect fun copyToClipboard(text: String)
expect fun shareText(text: String)
expect fun areNotificationsEnabled(): Boolean
expect fun openNotificationSettings()
