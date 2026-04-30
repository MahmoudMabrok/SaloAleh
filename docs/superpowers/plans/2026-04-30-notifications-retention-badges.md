# Notifications, Retention & Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After 3 app opens show a rational notification permission dialog, schedule a daily notification and a missed-you retention notification, and award 7-day / 30-day streak badges.

**Architecture:** All engagement tracking (open count, streak, last-open date, badges) lives in `EngagementStore` (commonMain, uses multiplatform-settings + kotlinx-datetime). Notification scheduling and permission handling are Android-only (androidMain + WorkManager). Compose dialogs for rationale and badge celebration are in commonMain and driven from `App.kt` via state passed down from `MainActivity`.

**Tech Stack:** Kotlin Multiplatform Compose, multiplatform-settings, kotlinx-datetime, AndroidX WorkManager 2.9.x, Android POST_NOTIFICATIONS permission, Koin DI.

---

## File Map

| Action | Path |
|--------|------|
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt` |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt` |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/NotificationRationaleDialog.kt` |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/BadgeCelebrationDialog.kt` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/DailyNotificationWorker.kt` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/RetentionCheckWorker.kt` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt` |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt` |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/di/AndroidModule.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt` |
| Modify | `app/src/androidMain/AndroidManifest.xml` |
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |
| Create | `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/EngagementStoreTest.kt` |

---

## Task 1: Add WorkManager dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library entry to libs.versions.toml**

In `[versions]` section add:
```toml
workManager = "2.9.1"
```

In `[libraries]` section add:
```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
```

- [ ] **Step 2: Add dependency to app/build.gradle.kts**

Inside `androidMain.dependencies { }` block add:
```kotlin
implementation(libs.androidx.work.runtime.ktx)
```

- [ ] **Step 3: Sync and verify compile**

```bash
./gradlew :app:assembleDebug --dry-run
```
Expected: task list prints without error.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add WorkManager 2.9.1 dependency"
```

---

## Task 2: EngagementModels (commonMain)

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt`

- [ ] **Step 1: Create the models file**

```kotlin
package tools.mo3ta.salo.domain

data class EngagementData(
    val openCount: Int,
    val currentStreak: Int,
    val newlyEarnedBadge: BadgeType?,
    val shouldRequestNotifPermission: Boolean,
)

enum class BadgeType { STREAK_7, STREAK_30 }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt
git commit -m "feat: add EngagementModels domain types"
```

---

## Task 3: EngagementStore (commonMain) — write failing test first

**Files:**
- Create: `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/EngagementStoreTest.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.BadgeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngagementStoreTest {

    private fun store(settings: MapSettings = MapSettings()) = EngagementStore(settings)

    @Test
    fun firstOpen_openCountIs1_noStreak_notifPermissionNeeded() {
        val data = store().recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(1, data.openCount)
        assertEquals(1, data.currentStreak)
        assertNull(data.newlyEarnedBadge)
        assertFalse(data.shouldRequestNotifPermission)
    }

    @Test
    fun thirdOpen_shouldRequestPermission() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 28))
        store.recordOpen(today = LocalDate(2026, 4, 29))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(3, data.openCount)
        assertTrue(data.shouldRequestNotifPermission)
    }

    @Test
    fun fourthOpen_permissionNotRequestedAgain() {
        val s = MapSettings()
        val store = store(s)
        repeat(3) { i -> store.recordOpen(today = LocalDate(2026, 4, 28 + i)) }
        val data = store.recordOpen(today = LocalDate(2026, 5, 1))
        assertFalse(data.shouldRequestNotifPermission)
    }

    @Test
    fun consecutiveDays_streakIncreases() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 28))
        store.recordOpen(today = LocalDate(2026, 4, 29))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(3, data.currentStreak)
    }

    @Test
    fun skipDay_streakResets() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 28))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(1, data.currentStreak)
    }

    @Test
    fun sameDay_openTwice_streakStaysOne() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 30))
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(1, data.currentStreak)
    }

    @Test
    fun streak7_badge7Earned() {
        val s = MapSettings()
        val store = store(s)
        (24..30).forEach { day ->
            store.recordOpen(today = LocalDate(2026, 4, day))
        }
        val data = store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(BadgeType.STREAK_7, data.newlyEarnedBadge)
    }

    @Test
    fun streak7_badge7NotDuplicated() {
        val s = MapSettings()
        val store = store(s)
        (24..30).forEach { day -> store.recordOpen(today = LocalDate(2026, 4, day)) }
        store.recordOpen(today = LocalDate(2026, 4, 30))
        val data = store.recordOpen(today = LocalDate(2026, 5, 1))
        assertNull(data.newlyEarnedBadge)
    }

    @Test
    fun missedDays_returns2() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 27))
        assertEquals(3, store.missedDays(today = LocalDate(2026, 4, 30)))
    }

    @Test
    fun noMiss_openedToday_returns0() {
        val s = MapSettings()
        val store = store(s)
        store.recordOpen(today = LocalDate(2026, 4, 30))
        assertEquals(0, store.missedDays(today = LocalDate(2026, 4, 30)))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.engagement.EngagementStoreTest" 2>&1 | tail -20
```
Expected: compilation error "Unresolved reference: EngagementStore"

- [ ] **Step 3: Implement EngagementStore**

```kotlin
package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.domain.EngagementData

class EngagementStore(private val settings: Settings) {

    fun recordOpen(today: LocalDate): EngagementData {
        val openCount = settings.getInt(KEY_OPEN_COUNT, 0) + 1
        settings.putInt(KEY_OPEN_COUNT, openCount)

        val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE)
        val lastDate = lastDateStr?.let { LocalDate.parse(it) }

        val streak = when {
            lastDate == null -> 1
            lastDate == today -> settings.getInt(KEY_STREAK, 1)
            lastDate == today.minusDays(1) -> settings.getInt(KEY_STREAK, 1) + 1
            else -> 1
        }

        if (lastDate != today) {
            settings.putString(KEY_LAST_OPEN_DATE, today.toString())
            settings.putInt(KEY_STREAK, streak)
        }

        val badge7Already = settings.getBoolean(KEY_BADGE_7, false)
        val badge30Already = settings.getBoolean(KEY_BADGE_30, false)
        val newBadge = when {
            streak >= 30 && !badge30Already -> {
                settings.putBoolean(KEY_BADGE_30, true)
                BadgeType.STREAK_30
            }
            streak >= 7 && !badge7Already -> {
                settings.putBoolean(KEY_BADGE_7, true)
                BadgeType.STREAK_7
            }
            else -> null
        }

        val notifAskedAt = settings.getInt(KEY_NOTIF_ASKED_AT_OPEN, -1)
        val shouldAskNotif = openCount == 3 && notifAskedAt == -1
        if (shouldAskNotif) settings.putInt(KEY_NOTIF_ASKED_AT_OPEN, openCount)

        return EngagementData(
            openCount = openCount,
            currentStreak = streak,
            newlyEarnedBadge = newBadge,
            shouldRequestNotifPermission = shouldAskNotif,
        )
    }

    fun missedDays(today: LocalDate): Int {
        val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE) ?: return 0
        val lastDate = LocalDate.parse(lastDateStr)
        val days = today.toEpochDays() - lastDate.toEpochDays()
        return if (days > 0) days.toInt() else 0
    }

    private fun LocalDate.minusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - n)

    private companion object {
        const val KEY_OPEN_COUNT = "eng_open_count"
        const val KEY_LAST_OPEN_DATE = "eng_last_open_date"
        const val KEY_STREAK = "eng_streak"
        const val KEY_BADGE_7 = "eng_badge_7"
        const val KEY_BADGE_30 = "eng_badge_30"
        const val KEY_NOTIF_ASKED_AT_OPEN = "eng_notif_asked_at"
    }
}
```

- [ ] **Step 4: Run tests and confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.engagement.EngagementStoreTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/EngagementStoreTest.kt
git commit -m "feat: add EngagementStore with open tracking, streak, and badge logic"
```

---

## Task 4: NotificationRationaleDialog (commonMain Compose)

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/NotificationRationaleDialog.kt`

- [ ] **Step 1: Create the dialog**

```kotlin
package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun NotificationRationaleDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "🔔",
                fontSize = 48.sp,
            )
            Text(
                text = "ابق على تواصل مع النبي ﷺ",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "نرسل لك تذكيراً يومياً بالصلاة على النبي محمد ﷺ\nوتنبيهاً عندما تفوتنا — لأن كل يوم يُحتسب.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.Gold),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("السماح بالإشعارات", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss) {
                Text("ليس الآن", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}
```

- [ ] **Step 2: Check MohamedLoversPalette has Gold and DeepBlue**

Read `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversPalette.kt` and add missing colors if needed.

If `Gold` or `DeepBlue` do not exist, add them inside the `MohamedLoversPalette` object:
```kotlin
val Gold = Color(0xFFFFD700)
val DeepBlue = Color(0xFF0D1B3E)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/NotificationRationaleDialog.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversPalette.kt
git commit -m "feat: add NotificationRationaleDialog UI"
```

---

## Task 5: BadgeCelebrationDialog (commonMain Compose)

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/BadgeCelebrationDialog.kt`

- [ ] **Step 1: Create the dialog**

```kotlin
package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun BadgeCelebrationDialog(
    badge: BadgeType,
    onDismiss: () -> Unit,
) {
    val (emoji, title, subtitle) = when (badge) {
        BadgeType.STREAK_7 -> Triple(
            "🏅",
            "أسبوع من المحبة!",
            "فتحت شارة «المداومة» لفتح تطبيق سالو 7 أيام متتالية. أنت من أهل الوفاء ﷺ",
        )
        BadgeType.STREAK_30 -> Triple(
            "🌟",
            "شهر من الوفاء!",
            "فتحت شارة «الوفي» لفتح تطبيق سالو 30 يوماً متتالياً. بارك الله فيك.",
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = emoji, fontSize = 56.sp)
            Text(
                text = title,
                color = MohamedLoversPalette.Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.Gold),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("رائع! شكراً", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/BadgeCelebrationDialog.kt
git commit -m "feat: add BadgeCelebrationDialog for streak milestones"
```

---

## Task 6: Android notification channels and helper (androidMain)

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt`

- [ ] **Step 1: Create NotificationChannels**

```kotlin
package tools.mo3ta.salo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val CHANNEL_DAILY = "channel_daily"
    const val CHANNEL_RETENTION = "channel_retention"

    const val NOTIF_ID_DAILY = 1001
    const val NOTIF_ID_RETENTION = 1002

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DAILY, "تذكير يومي", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "تذكير يومي بالصلاة على النبي" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RETENTION, "نفتقدك", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "تنبيه عند غيابك عن التطبيق" }
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt
git commit -m "feat: add Android notification channels"
```

---

## Task 7: DailyNotificationWorker (androidMain)

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/DailyNotificationWorker.kt`

- [ ] **Step 1: Create the worker**

```kotlin
package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tools.mo3ta.salo.R

class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اللهم صلِّ على محمد ﷺ")
            .setContentText("تذكيرك اليومي — اضغط لتشارك الصلاة على النبي")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_DAILY, notification)
        return Result.success()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/DailyNotificationWorker.kt
git commit -m "feat: add DailyNotificationWorker"
```

---

## Task 8: RetentionCheckWorker (androidMain)

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/RetentionCheckWorker.kt`

- [ ] **Step 1: Create the worker**

```kotlin
package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.R
import tools.mo3ta.salo.data.engagement.EngagementStore

class RetentionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val settings = SharedPreferencesSettings(
            applicationContext.getSharedPreferences("ml_session", Context.MODE_PRIVATE)
        )
        val store = EngagementStore(settings)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val missed = store.missedDays(today)
        if (missed < 1) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_RETENTION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("نفتقدك 🤍")
            .setContentText("مضى $missed ${if (missed == 1) "يوم" else "أيام"} منذ آخر زيارة — لا تنسَ الصلاة على النبي ﷺ")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_RETENTION, notification)
        return Result.success()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/RetentionCheckWorker.kt
git commit -m "feat: add RetentionCheckWorker with missed-days notification"
```

---

## Task 9: NotificationScheduler (androidMain)

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`

- [ ] **Step 1: Create the scheduler**

```kotlin
package tools.mo3ta.salo.notification

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val TAG_DAILY = "daily_notification"
    private const val TAG_RETENTION = "retention_check"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Daily notification at roughly the same interval each day.
        // Initial delay: 1 day so it fires tomorrow.
        workManager.enqueueUniquePeriodicWork(
            TAG_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(Constraints.NONE)
                .build(),
        )

        // Retention check runs daily.
        workManager.enqueueUniquePeriodicWork(
            TAG_RETENTION,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(Constraints.NONE)
                .build(),
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt
git commit -m "feat: add NotificationScheduler using WorkManager"
```

---

## Task 10: Wire EngagementStore into DI

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`

- [ ] **Step 1: Add EngagementStore to appModule**

Add the import and single binding:

```kotlin
import tools.mo3ta.salo.data.engagement.EngagementStore
```

Inside `val appModule = module { ... }` add:
```kotlin
single { EngagementStore(get()) }
```

The `get()` resolves `Settings` which is already provided by `androidModule`.

- [ ] **Step 2: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt
git commit -m "feat: register EngagementStore in DI"
```

---

## Task 11: Update App.kt with engagement state and dialogs

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt`

- [ ] **Step 1: Rewrite App.kt**

```kotlin
package tools.mo3ta.salo

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.ui.BadgeCelebrationDialog
import tools.mo3ta.salo.ui.MohamedLoversScreen
import tools.mo3ta.salo.ui.NotificationRationaleDialog

@Composable
fun App(
    engagementData: EngagementData? = null,
    onNotificationPermissionRequest: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MohamedLoversScreen()

        var showRationale by remember {
            mutableStateOf(engagementData?.shouldRequestNotifPermission == true)
        }
        var pendingBadge by remember {
            mutableStateOf(engagementData?.newlyEarnedBadge)
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
            BadgeCelebrationDialog(
                badge = badge,
                onDismiss = { pendingBadge = null },
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt
git commit -m "feat: wire engagement dialogs into App composable"
```

---

## Task 12: Update MainActivity — record open, permission, schedule workers

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt`
- Modify: `app/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Add POST_NOTIFICATIONS permission to AndroidManifest.xml**

Inside `<manifest>` element, before `<application>`, add:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Also add `WorkManager` initialization provider (WorkManager auto-initializes via manifest provider via the library itself — no extra tag needed if using default initialization).

- [ ] **Step 2: Rewrite MainActivity.kt**

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
import tools.mo3ta.salo.di.androidModule
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.notification.NotificationChannels
import tools.mo3ta.salo.notification.NotificationScheduler

class MainActivity : ComponentActivity() {

    private val engagementStore: EngagementStore by inject()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently; WorkManager already scheduled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.init(this)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule, androidModule)
        }
        enableEdgeToEdge()

        NotificationChannels.createAll(this)

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val engagementData: EngagementData = engagementStore.recordOpen(today)

        setContent {
            App(
                engagementData = engagementData,
                onNotificationPermissionRequest = {
                    requestNotificationPermissionIfNeeded()
                    NotificationScheduler.schedule(this)
                },
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationScheduler.schedule(this)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/MainActivity.kt \
        app/src/androidMain/AndroidManifest.xml
git commit -m "feat: wire engagement, notification permission, and WorkManager in MainActivity"
```

---

## Task 13: Build and smoke-test

- [ ] **Step 1: Build debug APK**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 3: Install on device/emulator and verify flows**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Manual verification checklist:
- Open app 1×, 2× — no dialog.
- Open app 3× — NotificationRationaleDialog appears with Arabic text and two buttons.
- Tap "السماح بالإشعارات" — system permission dialog appears (Android 13+).
- After granting — WorkManager jobs enqueued (verify via `adb shell dumpsys jobscheduler | grep tools.mo3ta`).
- Reset EngagementStore manually via `adb shell am clear-data tools.mo3ta.salo` and reopen 7 days (use `EngagementStore.recordOpen` with mocked dates in a test to verify badge logic).

- [ ] **Step 4: Final commit if any fixes applied**

```bash
git add -p
git commit -m "fix: post-integration fixes"
```

---

---

## Scope Update (post-plan user request)

User added: **Achievements screen** — a persistent full-screen view showing all earned achievements. Two types:
1. **Streak badges** — 7-day and 30-day streaks (already planned, now also persisted with earned date)
2. **Rank achievements** — saved each time user finishes in top 10 of a round

The `BadgeCelebrationDialog` becomes `AchievementCelebrationDialog` (handles both types). A new `AchievementsScreen` composable shows all achievements as a grid/list.

**Rank detection:** `MohamedLoversViewModel` already exposes `selfInTop`, `selfEntry.rank`, and `roundKey`. The ViewModel will be extended to call `EngagementStore.checkAndSaveRankAchievement()` when `selfInTop` becomes true for a new round, and surface a `newlyEarnedRankAchievement` in UiState.

**Changed files vs original plan:**
- Task 2: `EngagementModels.kt` — add `Achievement` sealed class + `RankAchievement`
- Task 3: `EngagementStore` — add rank achievement storage and retrieval
- Task 5: rename `BadgeCelebrationDialog` → `AchievementCelebrationDialog`, handle `Achievement` type
- Task 11: `App.kt` — add route to AchievementsScreen
- Task 12: `MohamedLoversViewModel` — detect top-10 placement and surface in UiState
- **New Task 14**: `AchievementsScreen.kt` — full screen showing all achievements
- **New Task 15**: Wire rank detection into ViewModel + UiState

---

## Task 14: AchievementsScreen (commonMain Compose)

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/AchievementsScreen.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.salo.data.engagement.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun AchievementsScreen(
    achievements: List<Achievement>,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MohamedLoversPalette.DeepBlue)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color.White,
                )
            }
            Text(
                text = "إنجازاتي",
                color = MohamedLoversPalette.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        if (achievements.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد إنجازات بعد\nاستمر في المشاركة! 🌟",
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(achievements) { achievement ->
                    AchievementCard(achievement)
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: Achievement) {
    val (emoji, title, subtitle) = when (achievement) {
        is Achievement.StreakBadge -> when (achievement.type) {
            BadgeType.STREAK_7 -> Triple("🏅", "أسبوع من المحبة", "7 أيام متتالية · ${achievement.earnedDate}")
            BadgeType.STREAK_30 -> Triple("🌟", "شهر من الوفاء", "30 يوماً متتالياً · ${achievement.earnedDate}")
        }
        is Achievement.RankAchievement -> Triple(
            if (achievement.rank == 1) "🥇" else if (achievement.rank == 2) "🥈" else if (achievement.rank == 3) "🥉" else "🏆",
            "المركز ${achievement.rank} في الترتيب",
            "جولة ${achievement.roundKey} · ${achievement.earnedDate}",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = emoji, fontSize = 36.sp)
        Column {
            Text(text = title, color = MohamedLoversPalette.Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/AchievementsScreen.kt
git commit -m "feat: add AchievementsScreen showing streak badges and rank achievements"
```

---

## Task 15: Extend EngagementModels + EngagementStore for rank achievements + update ViewModel

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt`

- [ ] **Step 1: Add Achievement sealed class to EngagementModels.kt**

Append to existing `EngagementModels.kt`:
```kotlin
import kotlinx.datetime.LocalDate

sealed class Achievement {
    data class StreakBadge(val type: BadgeType, val earnedDate: LocalDate) : Achievement()
    data class RankAchievement(val roundKey: String, val rank: Int, val earnedDate: LocalDate) : Achievement()
}
```

- [ ] **Step 2: Add rank achievement storage to EngagementStore**

Add these methods and keys to `EngagementStore`:

```kotlin
// In companion object:
const val KEY_RANK_ACHIEVEMENTS = "eng_rank_achievements"
const val KEY_BADGE_7_DATE = "eng_badge_7_date"
const val KEY_BADGE_30_DATE = "eng_badge_30_date"

// New method:
fun checkAndSaveRankAchievement(roundKey: String, rank: Int, today: LocalDate): Achievement.RankAchievement? {
    val existing = getRankAchievementsRaw()
    if (existing.any { it.first == roundKey }) return null
    val updated = existing + Pair(roundKey, rank)
    settings.putString(KEY_RANK_ACHIEVEMENTS, encodeRankAchievements(updated))
    return Achievement.RankAchievement(roundKey = roundKey, rank = rank, earnedDate = today)
}

fun getAllAchievements(): List<Achievement> {
    val streakBadges = buildList {
        settings.getStringOrNull(KEY_BADGE_7_DATE)?.let {
            add(Achievement.StreakBadge(BadgeType.STREAK_7, LocalDate.parse(it)))
        }
        settings.getStringOrNull(KEY_BADGE_30_DATE)?.let {
            add(Achievement.StreakBadge(BadgeType.STREAK_30, LocalDate.parse(it)))
        }
    }
    val rankAchievements = getRankAchievementsRaw().map { (rk, rank) ->
        Achievement.RankAchievement(roundKey = rk, rank = rank, earnedDate = LocalDate.parse(rk.take(10).replace("-", "-")))
    }
    return (streakBadges + rankAchievements).sortedByDescending {
        when (it) {
            is Achievement.StreakBadge -> it.earnedDate.toString()
            is Achievement.RankAchievement -> it.earnedDate.toString()
        }
    }
}

private fun getRankAchievementsRaw(): List<Pair<String, Int>> {
    val raw = settings.getStringOrNull(KEY_RANK_ACHIEVEMENTS) ?: return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size == 2) Pair(parts[0], parts[1].toIntOrNull() ?: return@mapNotNull null) else null
    }
}

private fun encodeRankAchievements(list: List<Pair<String, Int>>): String =
    list.joinToString("|") { "${it.first}:${it.second}" }
```

Also update `recordOpen` to save the earned date when a badge is first awarded:
- When `newBadge == BadgeType.STREAK_7`, save `settings.putString(KEY_BADGE_7_DATE, today.toString())`
- When `newBadge == BadgeType.STREAK_30`, save `settings.putString(KEY_BADGE_30_DATE, today.toString())`

- [ ] **Step 3: Add `newlyEarnedRankAchievement` to MohamedLoversUiState**

```kotlin
val newlyEarnedRankAchievement: Achievement.RankAchievement? = null,
```

Add import: `import tools.mo3ta.salo.data.engagement.Achievement`

- [ ] **Step 4: Inject EngagementStore into MohamedLoversViewModel and detect top-10**

Add constructor param: `private val engagementStore: EngagementStore`

In `applyLeaderboard()`, after `_state.update { ... }`, add:
```kotlin
val currentSelfInTop = selfInTop
val topEntry = topEntries.firstOrNull { it.isCurrentUser }
val roundKey = state.value.roundKey
if (currentSelfInTop && topEntry != null && roundKey != null) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val newAchievement = engagementStore.checkAndSaveRankAchievement(roundKey, topEntry.rank, today)
    if (newAchievement != null) {
        _state.update { it.copy(newlyEarnedRankAchievement = newAchievement) }
    }
}
```

Required imports:
```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.Achievement
```

- [ ] **Step 5: Update DI to inject EngagementStore into ViewModel**

In `AppModule.kt`, change:
```kotlin
viewModel { MohamedLoversViewModel(get()) }
```
to:
```kotlin
viewModel { MohamedLoversViewModel(get(), get()) }
```

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt
git commit -m "feat: add rank achievement detection and achievements data model"
```

---

## Self-Review Checklist

- [x] **3-open rationale flow** — Task 3 (EngagementStore.shouldRequestNotifPermission), Task 4 (dialog), Task 12 (MainActivity triggers it)
- [x] **Daily notification** — Task 7 (DailyNotificationWorker), Task 9 (NotificationScheduler)
- [x] **Missed-you retention** — Task 8 (RetentionCheckWorker reads missedDays), Task 3 (missedDays impl)
- [x] **7-day badge** — Task 3 (streak >= 7, badge7 flag), Task 5 (BadgeCelebrationDialog STREAK_7), Task 11 (App.kt shows dialog)
- [x] **30-day badge** — Task 3 (streak >= 30, badge30 flag), Task 5 (BadgeCelebrationDialog STREAK_30)
- [x] **No duplicate badge** — Task 3 tests badge not shown twice
- [x] **WorkManager dep** — Task 1
- [x] **POST_NOTIFICATIONS permission** — Task 12 manifest + runtime request
- [x] **Content placeholders** — DailyNotificationWorker text is placeholder; user stated they'll fill later ✓
- [x] **Type consistency** — `EngagementData`, `BadgeType`, `EngagementStore` names consistent across all tasks
- [x] **Achievements screen** — Task 14 (AchievementsScreen), Task 15 (rank detection + storage)
- [x] **Rank achievement saved only once per round** — Task 15 `checkAndSaveRankAchievement` checks existing list
- [x] **Both achievement types on screen** — Task 14 renders `Achievement.StreakBadge` and `Achievement.RankAchievement`
- [x] **ViewModel rank detection** — Task 15 injects EngagementStore into ViewModel, calls on `selfInTop`
