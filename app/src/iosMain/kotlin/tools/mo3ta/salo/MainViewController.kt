package tools.mo3ta.salo

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.language.LanguageStore
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.di.iosModule
import tools.mo3ta.salo.notification.NotificationScheduler

fun MainViewController() = ComposeUIViewController(
    configure = {
        val koin = startKoin { modules(appModule, iosModule) }.koin
        val languageStore = koin.get<LanguageStore>()
        val lang = languageStore.language
        platform.Foundation.NSUserDefaults.standardUserDefaults.setObject(listOf(lang), forKey = "AppleLanguages")
        platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()

        val store = koin.get<NotificationSettingsStore>()

        syncFcmTokenIfNeeded(koin.get(), koin.get())

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

private fun syncFcmTokenIfNeeded(
    sessionStore: MohamedLoversSessionStore,
    firebaseClient: MohamedLoversFirebaseClient,
) {
    if (sessionStore.isFcmTokenSynced()) return
    val token = sessionStore.getSavedFcmToken() ?: return
    CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
        firebaseClient.writeFcmToken(sessionStore.getOrCreateUid(), token).onSuccess {
            sessionStore.setFcmTokenSynced(true)
        }
    }
}
