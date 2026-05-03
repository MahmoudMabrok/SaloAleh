package tools.mo3ta.salo

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.di.iosModule
import tools.mo3ta.salo.notification.NotificationScheduler

fun MainViewController() = ComposeUIViewController(
    configure = {
        val koin = startKoin { modules(appModule, iosModule) }.koin
        val store = koin.get<NotificationSettingsStore>()
        NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled)
    },
) { App() }
