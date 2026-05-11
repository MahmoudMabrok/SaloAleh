# Floating Bubble Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Android-only floating overlay bubble that lets users increment their salawat count while in other apps, with a 10-minute reminder tooltip cycle and a close button.

**Architecture:** A `FloatingBubbleService` (foreground service) draws a custom `FloatingBubbleView` via `WindowManager`. Tap events call `MohamedLoversSessionStore.incrementPendingClick()` and `DailyGoalStore.recordTap()` directly (both are Koin singletons). The `roundKey` is passed via Intent extra when starting the service. A `@Composable expect fun FloatingBubbleButton` in `PlatformActions` shows an icon button on Android and nothing on iOS.

**Tech Stack:** Android WindowManager, Koin (KoinComponent), kotlinx-coroutines (timer), Compose expect/actual (PlatformActions), SharedPreferencesSettings (`ml_session`)

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/androidMain/AndroidManifest.xml` |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleView.kt` |
| Create | `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleService.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt` |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt` |
| Modify | `app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt` |

---

## Task 1: AndroidManifest — Permissions + Service Registration

**Files:**
- Modify: `app/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Add permissions and service declaration**

Add after the existing `<uses-permission>` block (after `RECEIVE_BOOT_COMPLETED`):

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

Add after the existing `<service android:name=".notification.SaloFirebaseMessagingService" ...>` block (inside `<application>`):

```xml
<service
    android:name=".ui.FloatingBubbleService"
    android:foregroundServiceType="specialUse"
    android:exported="false" />
```

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/androidMain/AndroidManifest.xml
git commit -m "feat: add SYSTEM_ALERT_WINDOW + FOREGROUND_SERVICE permissions and FloatingBubbleService declaration"
```

---

## Task 2: NotificationChannels — Add Bubble Channel

The foreground service requires an ongoing notification. Add a silent channel for it.

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt`

- [ ] **Step 1: Add bubble channel constant and creation**

Add to the `object NotificationChannels` block:

```kotlin
const val CHANNEL_BUBBLE = "channel_bubble"
const val NOTIF_ID_BUBBLE = 1005
```

In `createAll()`, add after the last `manager.createNotificationChannel(...)` call:

```kotlin
manager.createNotificationChannel(
    NotificationChannel(CHANNEL_BUBBLE, "الفقاعة العائمة", NotificationManager.IMPORTANCE_LOW)
        .apply {
            description = "إشعار نشط أثناء استخدام الفقاعة العائمة"
            setSound(null, null)
            enableVibration(false)
        }
)
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationChannels.kt
git commit -m "feat: add silent CHANNEL_BUBBLE notification channel for overlay service"
```

---

## Task 3: FloatingBubbleView — Android View

A programmatic `FrameLayout` that renders the bubble circle, session count, tooltip, and close button.

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleView.kt`

- [ ] **Step 1: Create the view file**

```kotlin
package tools.mo3ta.salo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBubbleView(context: Context) : LinearLayout(context) {

    private val countText: TextView
    private val tooltipGroup: LinearLayout
    var onTap: () -> Unit = {}
    var onClose: () -> Unit = {}

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        tooltipGroup = buildTooltip(context)
        tooltipGroup.alpha = 0f
        addView(tooltipGroup)

        val bubbleContainer = buildBubble(context)
        addView(bubbleContainer)

        countText = bubbleContainer.findViewWithTag("count")
    }

    fun updateCount(count: Int) {
        countText.text = count.toString()
    }

    fun showTooltip() {
        tooltipGroup.animate().alpha(1f).setDuration(300).start()
    }

    fun hideTooltip() {
        tooltipGroup.animate().alpha(0f).setDuration(300).start()
    }

    private fun buildTooltip(context: Context): LinearLayout {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt() }

        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }

        val card = TextView(context).apply {
            text = "اللهم صل علي محمد وال محمد"
            textDirection = TEXT_DIRECTION_RTL
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(50, 255, 255, 255))
                setStroke(1, Color.argb(65, 255, 255, 255))
            }
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
        }
        container.addView(card)

        val arrow = TextView(context).apply {
            text = "▼"
            setTextColor(Color.argb(65, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        container.addView(arrow)

        return container
    }

    private fun buildBubble(context: Context): FrameLayout {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt() }
        val bubbleSize = dp(68)

        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(bubbleSize + dp(12), bubbleSize + dp(12))
        }

        val circle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#1b5e20"))
            ).apply { shape = GradientDrawable.OVAL }
            elevation = dp(4).toFloat()
            setOnClickListener { onTap() }
        }

        val label = TextView(context).apply {
            text = "صلوات"
            setTextColor(Color.argb(180, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = dp(14)
            }
        }
        circle.addView(label)

        val count = TextView(context).apply {
            tag = "count"
            text = "0"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = dp(6)
            }
        }
        circle.addView(count)

        container.addView(circle)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
            }
            val size = dp(20)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener { onClose() }
        }
        container.addView(closeBtn)

        return container
    }
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleView.kt
git commit -m "feat: add FloatingBubbleView — programmatic Android view for overlay bubble"
```

---

## Task 4: FloatingBubbleService — Foreground Service

Manages the WindowManager overlay, 10-min reminder cycle, drag handling, and count persistence.

**Files:**
- Create: `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleService.kt`

- [ ] **Step 1: Create the service file**

```kotlin
package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.android.ext.android.inject
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.notification.NotificationChannels
import kotlin.math.abs

class FloatingBubbleService : Service() {

    companion object {
        const val EXTRA_ROUND_KEY = "round_key"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private val sessionStore: MohamedLoversSessionStore by inject()
    private val dailyGoalStore: DailyGoalStore by inject()

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: FloatingBubbleView
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var roundKey: String = ""
    private var sessionCount = 0

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        roundKey = intent?.getStringExtra(EXTRA_ROUND_KEY) ?: ""
        startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification())
        setupBubble()
        startReminderCycle()
        return START_NOT_STICKY
    }

    private fun setupBubble() {
        bubbleView = FloatingBubbleView(this)
        bubbleView.onTap = { handleTap() }
        bubbleView.onClose = { stopSelf() }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 200
        }

        setupDrag()
        windowManager.addView(bubbleView, params)
    }

    private fun setupDrag() {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTap() {
        if (roundKey.isBlank()) return
        sessionCount++
        sessionStore.incrementPendingClick(roundKey, 1)
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        dailyGoalStore.recordTap(today, 1)
        bubbleView.updateCount(sessionCount)
    }

    private fun startReminderCycle() {
        scope.launch {
            while (true) {
                delay(10 * 60 * 1000L)
                bubbleView.showTooltip()
                delay(5_000L)
                bubbleView.hideTooltip()
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_BUBBLE)
            .setContentTitle("صلوات")
            .setContentText("اللهم صل علي محمد وال محمد")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        _isRunning.value = false
        scope.cancel()
        if (::bubbleView.isInitialized) {
            runCatching { windowManager.removeView(bubbleView) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleService.kt
git commit -m "feat: add FloatingBubbleService — foreground overlay service with 10-min reminder cycle"
```

---

## Task 5: PlatformActions — FloatingBubbleButton expect/actual

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt`
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt`
- Modify: `app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt`

- [ ] **Step 1: Add expect to PlatformActions.kt**

Append to `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt`:

```kotlin
@Composable
expect fun FloatingBubbleButton(roundKey: String?)
```

- [ ] **Step 2: Add iOS actual (no-op) to PlatformActions.ios.kt**

Append to `app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt`:

```kotlin
@Composable
actual fun FloatingBubbleButton(roundKey: String?) {}
```

- [ ] **Step 3: Add Android actual to PlatformActions.android.kt**

Add the following imports at the top of `PlatformActions.android.kt` (merge with existing imports):

```kotlin
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import tools.mo3ta.salo.ui.FloatingBubbleService
```

Append to `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt`:

```kotlin
@Composable
actual fun FloatingBubbleButton(roundKey: String?) {
    val context = LocalContext.current
    val isActive by FloatingBubbleService.isRunning.collectAsState()

    IconButton(onClick = {
        if (isActive) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        } else {
            if (!Settings.canDrawOverlays(context)) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                )
            } else {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FloatingBubbleService::class.java)
                        .putExtra(FloatingBubbleService.EXTRA_ROUND_KEY, roundKey ?: "")
                )
            }
        }
    }) {
        Icon(
            imageVector = if (isActive) androidx.compose.material.icons.Icons.Default.Stop
                          else androidx.compose.material.icons.Icons.Default.BubbleChart,
            contentDescription = if (isActive) "إيقاف الفقاعة" else "تشغيل الفقاعة",
            tint = if (isActive) Color.Red
                   else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
    }
}
```

> **Note on icon:** If `Icons.Default.BubbleChart` is not available (requires `material-icons-extended`), replace with `Icons.Default.RadioButtonChecked` or any available icon. Check existing icon usages in the project for what's already imported.

- [ ] **Step 4: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Fix any import issues.

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.kt \
        app/src/androidMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.android.kt \
        app/src/iosMain/kotlin/tools/mo3ta/salo/ui/PlatformActions.ios.kt
git commit -m "feat: add FloatingBubbleButton expect/actual composable to PlatformActions"
```

---

## Task 6: MohamedLoversScreen — Wire the Button

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`

The button goes in the `TopStart` icon row (around line 295) alongside achievements, hadith, and settings icons.

- [ ] **Step 1: Add FloatingBubbleButton to the TopStart row**

Find the `Row` block that contains `IconButton(onClick = onOpenAchievements)`, `IconButton(onClick = onOpenHadithList)`, `IconButton(onClick = onOpenSettings)` (around line 296). Add `FloatingBubbleButton` as the first item in that row:

```kotlin
Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 36.dp)) {
    Row {
        FloatingBubbleButton(roundKey = state.roundKey)   // ← ADD THIS LINE
        IconButton(onClick = onOpenAchievements) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "الإنجازات",
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
            )
        }
        IconButton(onClick = onOpenHadithList) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = "الأحاديث",
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

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Manual test on Android device/emulator**

1. Run on Android device/emulator with API 26+
2. Tap the bubble icon in top-left — system Settings overlay permission page opens
3. Grant overlay permission, return to app
4. Tap bubble icon again — bubble appears floating over the screen
5. Press Home, go to another app — bubble still visible
6. Tap the bubble — count increments, number updates on bubble
7. Tap ✕ close button — bubble disappears
8. Re-open app — session count in app reflects bubble taps (after `flushPendingSession`)
9. Restart service, wait 10 minutes (or temporarily reduce timer to 30s for testing) — tooltip fades in with Arabic text, fades out after 5s

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat: add floating bubble button to MohamedLoversScreen top bar"
```

---

## Final Integration Commit

After all tasks pass build and manual test:

```bash
git log --oneline -6
```

Verify all 6 commits are present. Feature is complete.

---

## Known Constraints

- Koin must be initialized (app must be running) when the service starts. `START_NOT_STICKY` ensures the service won't auto-restart if killed when Koin is not available.
- `SYSTEM_ALERT_WINDOW` cannot be requested via `requestPermissions()` — must use `ACTION_MANAGE_OVERLAY_PERMISSION`.
- Count sync is eventual: bubble taps write to SharedPreferences (`ml_session` via `MohamedLoversSessionStore`). In-app counter reconciles when `flushPendingSession()` is called on next app foreground.
- App icon in the foreground notification uses `android.R.drawable.ic_dialog_info` as a placeholder — replace with the app's actual notification icon resource if available.
