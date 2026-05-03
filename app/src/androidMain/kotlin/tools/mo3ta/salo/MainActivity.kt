package tools.mo3ta.salo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.di.androidModule
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.notification.NotificationChannels
import tools.mo3ta.salo.notification.NotificationScheduler

class MainActivity : ComponentActivity() {

    private val engagementStore: EngagementStore by inject()
    private val notificationSettingsStore: NotificationSettingsStore by inject()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently; scheduling already done */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.init(this)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule, androidModule)
        }
        enableEdgeToEdge()

        NotificationChannels.createAll(this)
        NotificationScheduler.apply(
            notificationSettingsStore.dailyEnabled,
            notificationSettingsStore.fridayEnabled,
        )

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val engagementData = engagementStore.recordOpen(today)

        setContent {
            App(
                engagementData = engagementData,
                onNotificationPermissionRequest = {
                    requestNotificationPermissionIfNeeded()
                },
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
