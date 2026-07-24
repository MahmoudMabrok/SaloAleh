package tools.mo3ta.salo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
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
import tools.mo3ta.salo.data.referral.InstallReferrerHelper
import tools.mo3ta.salo.data.referral.ReferralStore
import tools.mo3ta.salo.data.security.AutoClickGuardStore
import tools.mo3ta.salo.input.isSyntheticTap
import tools.mo3ta.salo.notification.NotificationAction
import tools.mo3ta.salo.notification.NotificationChannels
import tools.mo3ta.salo.notification.NotificationMessage
import tools.mo3ta.salo.notification.NotificationScheduler
import tools.mo3ta.salo.ui.FloatingBubbleService
import tools.mo3ta.salo.ui.TouchDiagnostics

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withStoredAppLocale())
    }

    private val engagementStore: EngagementStore by inject()
    private val notificationSettingsStore: NotificationSettingsStore by inject()
    private val sessionStore: MohamedLoversSessionStore by inject()
    private val firebaseClient: MohamedLoversFirebaseClient by inject()
    private val billingManager: BillingManager by inject()
    private val referralStore: ReferralStore by inject()
    private val autoClickGuardStore: AutoClickGuardStore by inject()

    private val notificationMessageState = mutableStateOf<NotificationMessage?>(null)
    private val newVersionState = mutableStateOf<String?>(null)
    private val referralCodeState = mutableStateOf<String?>(null)
    // Challenge screen requested by dragging a challenge bubble onto "open app".
    private val openChallengeState = mutableStateOf<NotificationAction?>(null)
    private val autoClickDetectedState = mutableStateOf(false)

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
            FirebaseMessaging.getInstance().subscribeToTopic("challenges")
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

        InstallReferrerHelper.checkInstallReferrer(this, referralStore)
        newVersionState.value = extractNewVersionFromIntent(intent)
        notificationMessageState.value = extractNotificationMessageFromIntent(intent)
        openChallengeState.value = extractOpenChallengeFromIntent(intent)
        handleReferralIntent(intent)

        setContent {
            App(
                engagementData = finalEngagementData,
                onNotificationPermissionRequest = {
                    requestNotificationPermissionIfNeeded()
                },
                newVersionAvailable = newVersionState.value,
                notificationMessage = notificationMessageState.value,
                referralCode = referralCodeState.value,
                openChallenge = openChallengeState.value,
                onOpenChallengeHandled = { openChallengeState.value = null },
                autoClickDetected = autoClickDetectedState.value,
                onAutoClickWarningDismissed = { autoClickDetectedState.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newVersionState.value = extractNewVersionFromIntent(intent)
        notificationMessageState.value = extractNotificationMessageFromIntent(intent)
        openChallengeState.value = extractOpenChallengeFromIntent(intent)
        handleReferralIntent(intent)
    }

    private fun extractOpenChallengeFromIntent(intent: Intent?): NotificationAction? {
        val value = intent?.getStringExtra(FloatingBubbleService.EXTRA_OPEN_CHALLENGE) ?: return null
        return NotificationAction.from(value)
    }

    private fun extractNewVersionFromIntent(intent: Intent?): String? {
        if (intent?.getStringExtra(EXTRA_NOTIFICATION_TYPE) != NOTIFICATION_TYPE_VERSION_UPDATE) return null
        return intent.getStringExtra(EXTRA_NEW_VERSION) ?: ""
    }

    private fun extractNotificationMessageFromIntent(intent: Intent?): NotificationMessage? {
        if (intent?.getStringExtra(EXTRA_NOTIFICATION_TYPE) == NOTIFICATION_TYPE_VERSION_UPDATE) return null
        // Two delivery paths land here with different extra keys:
        //  - Foreground: SaloFirebaseMessagingService renames FCM data into EXTRA_NOTIF_* keys.
        //  - Background/killed: FCM shows the tray notification itself and copies the raw `data`
        //    payload verbatim onto the launch intent, so keys are "title"/"body"/"notification_action".
        // Read the app keys first, then fall back to the raw FCM data keys.
        val title = intent?.getStringExtra(EXTRA_NOTIF_TITLE)
            ?: intent?.getStringExtra(FCM_DATA_TITLE) ?: return null
        val body = intent?.getStringExtra(EXTRA_NOTIF_BODY)
            ?: intent?.getStringExtra(FCM_DATA_BODY) ?: return null
        val action = NotificationAction.from(
            intent?.getStringExtra(EXTRA_NOTIF_ACTION) ?: intent?.getStringExtra(FCM_DATA_ACTION)
        )
        return NotificationMessage(title, body, action)
    }

    companion object {
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_NEW_VERSION = "new_version"
        const val NOTIFICATION_TYPE_VERSION_UPDATE = "version_update"
        const val EXTRA_NOTIF_TITLE = "notif_title"
        const val EXTRA_NOTIF_BODY = "notif_body"
        const val EXTRA_NOTIF_ACTION = "notif_action"

        // Raw FCM `data` keys copied onto the launch intent when the system tray
        // handles the notification (app in background/killed).
        private const val FCM_DATA_TITLE = "title"
        private const val FCM_DATA_BODY = "body"
        private const val FCM_DATA_ACTION = "notification_action"
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
            FirebaseMessaging.getInstance().subscribeToTopic("challenges")
            if (notificationSettingsStore.leaderboardNotifsEnabled)
                FirebaseMessaging.getInstance().subscribeToTopic("leaderboard_notifs")
            else
                FirebaseMessaging.getInstance().unsubscribeFromTopic("leaderboard_notifs")
        }
        ensureFcmTokenSynced()
    }

    /**
     * Every touch the app receives passes through here before reaching Compose, which makes it
     * the one place the auto-click guard can see them all. Injected taps are swallowed here
     * rather than un-counted later, so no counter, streak or badge ever observes them.
     *
     * Screen readers are unaffected: TalkBack activates controls through Compose's semantics
     * `onClick`, which never produces a [MotionEvent]. Tools that tap via `dispatchGesture`
     * — including Voice Access and Switch Access — are blocked along with auto-clickers.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let(TouchDiagnostics::log)
        if (ev != null && ev.isSyntheticTap()) {
            onSyntheticTouchBlocked()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Warns once per install, then stays quiet while continuing to drop the taps. */
    private fun onSyntheticTouchBlocked() {
        if (autoClickGuardStore.hasWarned()) return
        autoClickGuardStore.markWarned()
        autoClickDetectedState.value = true
    }

    private fun handleReferralIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val isCustomScheme = uri.scheme == "saloaleh" && uri.host == "refer"
        val isWebLink = uri.scheme == "https" &&
            uri.host == "kamapp-3b3ac.web.app" &&
            uri.path == "/refer"
        if (isCustomScheme || isWebLink) {
            val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return
            if (!referralStore.isReferralApplied()) {
                referralStore.savePendingReferralCode(code)
                referralCodeState.value = code
            }
        }
    }

    private fun syncNotificationSchedule() {
        NotificationScheduler.apply(
            notificationSettingsStore.dailyEnabled,
            notificationSettingsStore.fridayEnabled,
            notificationSettingsStore.eveningEnabled,
        )
    }
}
