package tools.mo3ta.salo

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.di.iosModule
import tools.mo3ta.salo.notification.NotificationScheduler

fun MainViewController() = ComposeUIViewController(
    configure = {
        val koin = startKoin { modules(appModule, iosModule) }.koin
        val store = koin.get<NotificationSettingsStore>()

        println("[MainVC] requesting notification authorization")
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { granted, error ->
                println("[MainVC] auth callback — granted=$granted error=${error?.localizedDescription}")
                if (granted) {
                    NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled)
                } else {
                    println("[MainVC] permission NOT granted — notifications will not be scheduled")
                }
            }
    },
) { App() }
