package tools.mo3ta.salo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.di.androidModule
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.notification.NotificationChannels
import tools.mo3ta.salo.notification.NotificationScheduler

class MainActivity : ComponentActivity() {

    private val engagementStore: EngagementStore by inject()
    private val notificationSettingsStore: NotificationSettingsStore by inject()
    private val sessionStore: MohamedLoversSessionStore by inject()
    private val firebaseClient: MohamedLoversFirebaseClient by inject()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently; scheduling already done */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule, androidModule)
        }
        enableEdgeToEdge()

        NotificationChannels.createAll(this)

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val engagementData = engagementStore.recordOpen(today)

        // New users: set notification toggles to off by default
        if (engagementData.shouldRequestNotifPermission) {
            notificationSettingsStore.initializeToOff()
        }

        syncNotificationSchedule()

        val hasNotifPerm = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (hasNotifPerm) {
            engagementStore.clearFcmPermDenied()
            FirebaseMessaging.getInstance().subscribeToTopic("general")
        } else {
            engagementStore.saveFcmPermDenied(today)
        }
        if (sessionStore.getSavedFcmToken() == null) {
            fetchAndSendFcmToken()
        }

        val daysSinceDenied = engagementStore.fcmPermDeniedDaysAgo(today)
        val shouldReshowFcmAlert = !hasNotifPerm && daysSinceDenied >= 3
        if (shouldReshowFcmAlert) {
            engagementStore.resetFcmPermDenied(today)
        }

        val finalEngagementData = engagementData.copy(shouldReshowFcmAlert = shouldReshowFcmAlert)

        setContent {
            App(
                engagementData = finalEngagementData,
                onNotificationPermissionRequest = {
                    requestNotificationPermissionIfNeeded()
                },
            )
        }
    }

    private fun fetchAndSendFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            sessionStore.saveLocalFcmToken(token)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                firebaseClient.writeFcmToken(sessionStore.getOrCreateUid(), token)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        syncNotificationSchedule()
        val hasNotifPerm = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (hasNotifPerm) {
            engagementStore.clearFcmPermDenied()
            FirebaseMessaging.getInstance().subscribeToTopic("general")
        }
        if (sessionStore.getSavedFcmToken() == null) {
            fetchAndSendFcmToken()
        }
    }

    private fun syncNotificationSchedule() {
        NotificationScheduler.apply(
            notificationSettingsStore.dailyEnabled,
            notificationSettingsStore.fridayEnabled,
        )
    }
}
