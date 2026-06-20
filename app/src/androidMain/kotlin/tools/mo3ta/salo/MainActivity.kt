package tools.mo3ta.salo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.billing.AndroidBillingManager
import tools.mo3ta.salo.data.billing.BillingManager
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.notification.NotificationChannels
import tools.mo3ta.salo.notification.NotificationScheduler

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ml_session", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "ar") ?: "ar"
        val locale = java.util.Locale.forLanguageTag(lang)
        java.util.Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private val engagementStore: EngagementStore by inject()
    private val notificationSettingsStore: NotificationSettingsStore by inject()
    private val sessionStore: MohamedLoversSessionStore by inject()
    private val firebaseClient: MohamedLoversFirebaseClient by inject()
    private val billingManager: BillingManager by inject()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently; scheduling already done */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidAppContext.setActivity(this)

        NotificationChannels.createAll(this)

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val engagementData = engagementStore.recordOpen(today)

        // New users: set notification toggles to off by default
        if (engagementData.shouldRequestNotifPermission) {
            notificationSettingsStore.initializeToOff()
        }

        syncNotificationSchedule()

        (billingManager as? AndroidBillingManager)?.setActivity(this)
        billingManager.initialize()

        val hasNotifPerm = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (hasNotifPerm) {
            engagementStore.clearFcmPermDenied()
            FirebaseMessaging.getInstance().subscribeToTopic("general")
            if (notificationSettingsStore.leaderboardNotifsEnabled)
                FirebaseMessaging.getInstance().subscribeToTopic("leaderboard_notifs")
            else
                FirebaseMessaging.getInstance().unsubscribeFromTopic("leaderboard_notifs")
        } else {
            engagementStore.saveFcmPermDenied(today)
        }
        ensureFcmTokenSynced()

        val daysSinceDenied = engagementStore.fcmPermDeniedDaysAgo(today)
        val shouldReshowFcmAlert = !hasNotifPerm && daysSinceDenied >= 3
        if (shouldReshowFcmAlert) {
            engagementStore.resetFcmPermDenied(today)
        }

        val finalEngagementData = engagementData.copy(shouldReshowFcmAlert = shouldReshowFcmAlert)

        val newVersionFromNotification = extractNewVersionFromIntent(intent)

        setContent {
            App(
                engagementData = finalEngagementData,
                onNotificationPermissionRequest = {
                    requestNotificationPermissionIfNeeded()
                },
                newVersionAvailable = newVersionFromNotification,
            )
        }
    }

    private fun extractNewVersionFromIntent(intent: Intent?): String? {
        if (intent?.getStringExtra(EXTRA_NOTIFICATION_TYPE) != NOTIFICATION_TYPE_VERSION_UPDATE) return null
        return intent.getStringExtra(EXTRA_NEW_VERSION) ?: ""
    }

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_NEW_VERSION = "new_version"
        const val NOTIFICATION_TYPE_VERSION_UPDATE = "version_update"
    }

    // Validates the FCM token at app start. After an Android Auto Backup restore,
    // the saved token is stale (it belongs to the previous device) while
    // isFcmTokenSynced is true — without this check the app would never push
    // the new device's token to Firebase and notifications would silently fail.
    private fun ensureFcmTokenSynced() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val alreadyInSync =
                sessionStore.getSavedFcmToken() == token && sessionStore.isFcmTokenSynced()
            if (alreadyInSync) return@addOnSuccessListener
            sessionStore.saveLocalFcmToken(token)
            sessionStore.setFcmTokenSynced(false)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                firebaseClient.writeFcmToken(sessionStore.getOrCreateUid(), token).onSuccess {
                    sessionStore.setFcmTokenSynced(true)
                }
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
            if (notificationSettingsStore.leaderboardNotifsEnabled)
                FirebaseMessaging.getInstance().subscribeToTopic("leaderboard_notifs")
            else
                FirebaseMessaging.getInstance().unsubscribeFromTopic("leaderboard_notifs")
        }
        ensureFcmTokenSynced()
    }

    private fun syncNotificationSchedule() {
        NotificationScheduler.apply(
            notificationSettingsStore.dailyEnabled,
            notificationSettingsStore.fridayEnabled,
        )
    }
}
