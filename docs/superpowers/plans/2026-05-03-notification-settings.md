# Notification Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a settings screen (⚙️ gear icon, top-right of main screen) with toggles for daily notification and Friday hourly notifications (9am–5pm), working on both Android and iOS.

**Architecture:** New `NotificationSettingsStore` (commonMain) persists two boolean prefs. An `expect object NotificationScheduler` in commonMain lets each platform apply scheduling: Android via WorkManager workers, iOS via `UNUserNotificationCenter` calendar triggers. Settings screen (`SettingsScreen.kt`, commonMain Compose) reads/writes the store and calls the scheduler on toggle.

**Tech Stack:** Kotlin Multiplatform (KMP), Compose Multiplatform, WorkManager (Android), UNUserNotificationCenter (iOS), multiplatform-settings (Russhwolf), Koin DI.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/data/notification/NotificationSettingsStore.kt` | Two boolean prefs: daily + friday enabled |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt` | Register `NotificationSettingsStore` in Koin |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt` | `expect object NotificationScheduler` |
| Replace | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt` | `actual object` — replaces existing plain object |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt` | Add `CHANNEL_FRIDAY` + `NOTIF_ID_FRIDAY` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/FridayNotificationWorker.kt` | 1-hour worker, fires only Friday 9–17 Cairo TZ |
| Create | `app/src/iosMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt` | `actual object` using UNUserNotificationCenter |
| Modify | `app/src/iosMain/kotlin/tools/mo3ta/salo/MainViewController.kt` | Apply scheduling on launch |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt` | Apply scheduling on launch, remove old `schedule()` call |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt` | Settings UI: two toggle rows |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt` | Add `showSettings` state, show `SettingsScreen` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt` | Add `onOpenSettings` param + gear icon button |

---

## Task 1: NotificationSettingsStore + Koin registration

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/notification/NotificationSettingsStore.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`

- [ ] **Step 1: Create NotificationSettingsStore**

Create the file at `app/src/commonMain/kotlin/tools/mo3ta/salo/data/notification/NotificationSettingsStore.kt`:

```kotlin
package tools.mo3ta.salo.data.notification

import com.russhwolf.settings.Settings

class NotificationSettingsStore(private val settings: Settings) {
    var dailyEnabled: Boolean
        get() = settings.getBoolean("notif_daily_enabled", true)
        set(v) = settings.putBoolean("notif_daily_enabled", v)

    var fridayEnabled: Boolean
        get() = settings.getBoolean("notif_friday_enabled", true)
        set(v) = settings.putBoolean("notif_friday_enabled", v)
}
```

- [ ] **Step 2: Register in AppModule**

In `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`, add the import and single binding:

Add import:
```kotlin
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
```

Add inside `val appModule = module {`:
```kotlin
single { NotificationSettingsStore(get()) }
```

Full file after change:
```kotlin
package tools.mo3ta.salo.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.NoOpAnalyticsManager
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.presentation.MohamedLoversViewModel

val appModule = module {
    single { MohamedLoversFirebaseClient(get()) }
    single { MohamedLoversSessionStore(get()) }
    single { EngagementStore(get()) }
    single { NotificationSettingsStore(get()) }
    single<AnalyticsManager> { NoOpAnalyticsManager() }
    single { MohamedLoversRepository(get(), get(), get(), get()) }
    viewModel { MohamedLoversViewModel(get(), get()) }
    viewModel { AchievementsViewModel(get()) }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/notification/NotificationSettingsStore.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt
git commit -m "feat: add NotificationSettingsStore with daily/friday prefs"
```

---

## Task 2: expect NotificationScheduler (commonMain)

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`

- [ ] **Step 1: Create the expect object**

Create `app/src/commonMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`:

```kotlin
package tools.mo3ta.salo.notification

expect object NotificationScheduler {
    fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt
git commit -m "feat: add expect NotificationScheduler for cross-platform scheduling"
```

---

## Task 3: Android — NotificationChannels + NotificationScheduler actual + FridayNotificationWorker

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt`
- Replace: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/FridayNotificationWorker.kt`

- [ ] **Step 1: Add CHANNEL_FRIDAY to NotificationChannels**

Replace the full contents of `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt`:

```kotlin
package tools.mo3ta.salo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_DAILY = "channel_daily"
    const val CHANNEL_RETENTION = "channel_retention"
    const val CHANNEL_FRIDAY = "channel_friday"

    const val NOTIF_ID_DAILY = 1001
    const val NOTIF_ID_RETENTION = 1002
    const val NOTIF_ID_FRIDAY = 1003

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DAILY, "تذكير يومي", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تذكير يومي بالصلاة على النبي" }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RETENTION, "نفتقدك", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تنبيه عند غيابك عن التطبيق" }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FRIDAY, "إشعارات الجمعة", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تذكير بالصلاة على النبي كل ساعة يوم الجمعة" }
            )
        }
    }
}
```

- [ ] **Step 2: Replace NotificationScheduler with actual object**

Replace the full contents of `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`:

```kotlin
package tools.mo3ta.salo.notification

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import tools.mo3ta.salo.AndroidAppContext
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {

    private const val TAG_DAILY = "daily_notification"
    private const val TAG_FRIDAY = "friday_notification"
    private const val TAG_RETENTION = "retention_check"

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
        val workManager = WorkManager.getInstance(AndroidAppContext.get())

        if (dailyEnabled) {
            workManager.enqueueUniquePeriodicWork(
                TAG_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(TAG_DAILY)
        }

        if (fridayEnabled) {
            workManager.enqueueUniquePeriodicWork(
                TAG_FRIDAY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FridayNotificationWorker>(1, TimeUnit.HOURS)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(TAG_FRIDAY)
        }

        // Retention worker always runs regardless of user settings
        workManager.enqueueUniquePeriodicWork(
            TAG_RETENTION,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build(),
        )
    }
}
```

- [ ] **Step 3: Create FridayNotificationWorker**

Create `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/FridayNotificationWorker.kt`:

```kotlin
package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.R

class FridayNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val cairoTz = TimeZone.of("Africa/Cairo")
        val now = Clock.System.now().toLocalDateTime(cairoTz)

        if (now.dayOfWeek != DayOfWeek.FRIDAY) return Result.success()
        if (now.hour !in 9..17) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_FRIDAY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اللهم صلِّ على محمد ﷺ")
            .setContentText("يوم الجمعة المبارك — صلّ على النبي الكريم")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_FRIDAY, notification)

        return Result.success()
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt \
        app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt \
        app/src/androidMain/kotlin/tools/mo3ta/salo/notification/FridayNotificationWorker.kt
git commit -m "feat: add Android actual NotificationScheduler, FridayNotificationWorker, and friday channel"
```

---

## Task 4: iOS — NotificationScheduler actual + MainViewController update

**Files:**
- Create: `app/src/iosMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`
- Modify: `app/src/iosMain/kotlin/tools/mo3ta/salo/MainViewController.kt`

- [ ] **Step 1: Create iOS actual NotificationScheduler**

Create `app/src/iosMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`:

```kotlin
package tools.mo3ta.salo.notification

import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual object NotificationScheduler {

    private const val ID_DAILY = "notif_daily"
    private val FRIDAY_IDS = (9..17).map { "notif_friday_$it" }

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
        val center = UNUserNotificationCenter.currentNotificationCenter()

        if (dailyEnabled) {
            val content = UNMutableNotificationContent().apply {
                title = "اللهم صلِّ على محمد ﷺ"
                body = "تذكيرك اليومي — اضغط لتشارك الصلاة على النبي"
                sound = UNNotificationSound.defaultSound()
            }
            val components = NSDateComponents().apply { hour = 9L }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
            val request = UNNotificationRequest.requestWithIdentifier(ID_DAILY, content, trigger)
            center.addNotificationRequest(request) { _ -> }
        } else {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_DAILY))
        }

        if (fridayEnabled) {
            (9..17).forEach { hour ->
                val content = UNMutableNotificationContent().apply {
                    title = "اللهم صلِّ على محمد ﷺ"
                    body = "يوم الجمعة المبارك — صلّ على النبي الكريم"
                    sound = UNNotificationSound.defaultSound()
                }
                val components = NSDateComponents().apply {
                    weekday = 6L   // 1=Sun, 2=Mon, …, 6=Fri
                    this.hour = hour.toLong()
                }
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
                val request = UNNotificationRequest.requestWithIdentifier("notif_friday_$hour", content, trigger)
                center.addNotificationRequest(request) { _ -> }
            }
        } else {
            center.removePendingNotificationRequestsWithIdentifiers(FRIDAY_IDS)
        }
    }
}
```

- [ ] **Step 2: Update MainViewController to apply scheduling on launch**

Replace the full contents of `app/src/iosMain/kotlin/tools/mo3ta/salo/MainViewController.kt`:

```kotlin
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
```

- [ ] **Step 3: Commit**

```bash
git add app/src/iosMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt \
        app/src/iosMain/kotlin/tools/mo3ta/salo/MainViewController.kt
git commit -m "feat: add iOS actual NotificationScheduler and apply scheduling on launch"
```

---

## Task 5: Android — MainActivity update

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt`

- [ ] **Step 1: Update MainActivity**

Replace the full contents of `app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt`:

```kotlin
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
    ) { /* result handled silently */ }

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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt
git commit -m "feat: apply notification scheduling on every Android launch"
```

---

## Task 6: UI — SettingsScreen + App.kt + MohamedLoversScreen

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`

- [ ] **Step 1: Create SettingsScreen**

Create `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt`:

```kotlin
package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.notification.NotificationScheduler
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store: NotificationSettingsStore = koinInject()
    var dailyEnabled by remember { mutableStateOf(store.dailyEnabled) }
    var fridayEnabled by remember { mutableStateOf(store.fridayEnabled) }

    Scaffold(
        containerColor = Color(0xFF0f0f1a),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات",
                        color = MohamedLoversPalette.GoldGlow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MohamedLoversPalette.GoldGlow,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF16213e),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "الإشعارات",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SettingToggleRow(
                label = "تذكير يومي",
                subtitle = "مرة واحدة يوميًا",
                checked = dailyEnabled,
                onToggle = { checked ->
                    dailyEnabled = checked
                    store.dailyEnabled = checked
                    NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled)
                },
            )

            SettingToggleRow(
                label = "إشعارات الجمعة",
                subtitle = "كل ساعة من 9ص – 5م",
                checked = fridayEnabled,
                onToggle = { checked ->
                    fridayEnabled = checked
                    store.fridayEnabled = checked
                    NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled)
                },
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.padding(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}
```

- [ ] **Step 2: Add gear icon to MohamedLoversScreen**

In `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`:

Add `onOpenSettings: () -> Unit = {}` parameter to `MohamedLoversScreen`:

```kotlin
@Composable
fun MohamedLoversScreen(
    onOpenAchievements: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: MohamedLoversViewModel = koinViewModel(),
) {
```

Add the `Icons.Default.Settings` import at the top with the other icon imports:
```kotlin
import androidx.compose.material.icons.filled.Settings
```

Find the `TopEnd` Box containing the Info `IconButton` (around line 138–145). Change it from a single icon to a Column with Info + Settings:

```kotlin
Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 36.dp)) {
    Column {
        IconButton(onClick = { infoSheetOpen = true }) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = infoCd,
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "الإعدادات",
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
            )
        }
    }
}
```

- [ ] **Step 3: Wire SettingsScreen into App.kt**

Replace the full contents of `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt`:

```kotlin
package tools.mo3ta.salo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.ui.AchievementCelebrationDialog
import tools.mo3ta.salo.ui.AchievementsScreen
import tools.mo3ta.salo.ui.MohamedLoversScreen
import tools.mo3ta.salo.ui.NotificationRationaleDialog
import tools.mo3ta.salo.ui.settings.SettingsScreen

@Composable
fun App(
    engagementData: EngagementData? = null,
    onNotificationPermissionRequest: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var showRationale by remember {
            mutableStateOf(engagementData?.shouldRequestNotifPermission == true)
        }
        var pendingBadge by remember {
            mutableStateOf(
                engagementData?.newlyEarnedBadge?.let {
                    Achievement.StreakBadge(it, Clock.System.todayIn(TimeZone.currentSystemDefault()))
                }
            )
        }
        var showAchievements by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        when {
            showSettings -> SettingsScreen(onBack = { showSettings = false })
            showAchievements -> AchievementsScreen(onBack = { showAchievements = false })
            else -> MohamedLoversScreen(
                onOpenAchievements = { showAchievements = true },
                onOpenSettings = { showSettings = true },
            )
        }

        if (showRationale) {
            NotificationRationaleDialog(
                onAllow = {
                    showRationale = false
                    onNotificationPermissionRequest?.invoke()
                },
                onDismiss = { showRationale = false },
            )
        }

        pendingBadge?.let { badge ->
            AchievementCelebrationDialog(
                achievement = badge,
                onDismiss = { pendingBadge = null },
            )
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat: add SettingsScreen with daily/friday notification toggles"
```

---

## Task 7: Build verification

- [ ] **Step 1: Build Android debug**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Fix any compile errors before proceeding.

- [ ] **Step 2: Build iOS framework**

```bash
./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```

Expected: `BUILD SUCCESSFUL`. If `UNUserNotificationCenter` import fails, verify `platform.UserNotifications` is available (it's part of the default iOS SDK — no extra dependency needed).

- [ ] **Step 3: Manual smoke test — Android**

Install on device/emulator:
```bash
./gradlew :app:installDebug
```

1. Open app → tap ⚙️ icon (below ℹ️, top-right) → Settings screen appears
2. Toggle "تذكير يومي" off → toggle back on
3. Toggle "إشعارات الجمعة" off → toggle back on
4. Kill app → reopen → toggles remember their state
5. Tap back arrow → returns to main screen

- [ ] **Step 4: Commit (if any fixes were needed)**

```bash
git add -p
git commit -m "fix: build corrections after notification settings integration"
```
