# Bubble Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revamp the Mohamed Lovers floating bubble with an Islamic navy/gold aesthetic, long-press drag-to-action targets (close / open app), tap pulse animation, no X button, and 32 dp edge clamping.

**Architecture:** Two Android View files are rewritten in place — `FloatingBubbleView.kt` owns all visual construction and animation, `FloatingBubbleService.kt` owns all gesture logic and WindowManager interaction. No new files, no Compose, no changes outside `androidMain`.

**Tech Stack:** Android View system, `WindowManager`, `GradientDrawable`, `ViewPropertyAnimator`, `GestureDetector`, `HapticFeedbackConstants`

## Global Constraints

- Min SDK 24 — no API calls above 24 without `Build.VERSION.SDK_INT` guard
- All dimensions in dp via `TypedValue.applyDimension`
- No hardcoded Arabic strings in UI — existing tooltip text is already in code (not a `Text()` composable), so it is exempt from the stringResource rule
- Never `git push` without explicit user request
- Bubble margin from screen edges: exactly 32 dp, not padding

---

### Task 1: Rewrite `FloatingBubbleView.kt` — visual + animation

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleView.kt`

**Interfaces:**
- Produces:
  - `fun updateCount(count: Int)` — unchanged signature
  - `fun animateTap()` — pulse scale animation on the bubble circle
  - `fun showActionTargets()` — slide two targets in from below with alpha fade
  - `fun hideActionTargets()` — reverse slide + fade
  - `fun getCloseTargetCenter(): Pair<Float, Float>` — screen coords of close target center
  - `fun getOpenAppTargetCenter(): Pair<Float, Float>` — screen coords of open-app target center
  - `fun highlightCloseTarget(on: Boolean)` — scale close target to 1.2 / 1.0
  - `fun highlightOpenTarget(on: Boolean)` — scale open target to 1.2 / 1.0
  - `var onOpenApp: () -> Unit` — callback (alongside existing `onTap`, `onClose`)
  - Removed: `isCloseButtonHit()` — delete entirely

- [ ] **Step 1: Replace the entire file with the new implementation**

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

    var onTap: () -> Unit = {}
    var onClose: () -> Unit = {}
    var onOpenApp: () -> Unit = {}

    private lateinit var bubbleCircle: FrameLayout
    private lateinit var countText: TextView
    private lateinit var tooltipGroup: LinearLayout
    private lateinit var closeTarget: TextView
    private lateinit var openTarget: TextView
    private lateinit var actionRow: LinearLayout

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        tooltipGroup = buildTooltip(context)
        tooltipGroup.alpha = 0f
        addView(tooltipGroup)

        val bubbleContainer = buildBubble(context)
        addView(bubbleContainer)

        actionRow = buildActionRow(context)
        actionRow.alpha = 0f
        actionRow.translationY = (-8).dp().toFloat()
        addView(actionRow)
    }

    fun updateCount(count: Int) {
        countText.text = count.toString()
    }

    fun animateTap() {
        bubbleCircle.animate().cancel()
        bubbleCircle.animate()
            .scaleX(1.25f).scaleY(1.25f)
            .setDuration(100)
            .withEndAction {
                bubbleCircle.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(150)
                    .start()
            }.start()
    }

    fun showTooltip() {
        tooltipGroup.animate().cancel()
        tooltipGroup.animate().alpha(1f).setDuration(300).start()
    }

    fun hideTooltip() {
        tooltipGroup.animate().cancel()
        tooltipGroup.animate().alpha(0f).setDuration(300).start()
    }

    fun showActionTargets() {
        actionRow.visibility = VISIBLE
        actionRow.animate().cancel()
        actionRow.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()
    }

    fun hideActionTargets() {
        actionRow.animate().cancel()
        actionRow.animate()
            .alpha(0f)
            .translationY((-8).dp().toFloat())
            .setDuration(200)
            .withEndAction { actionRow.visibility = INVISIBLE }
            .start()
    }

    fun getCloseTargetCenter(): Pair<Float, Float> {
        val loc = IntArray(2)
        closeTarget.getLocationOnScreen(loc)
        return Pair(loc[0] + closeTarget.width / 2f, loc[1] + closeTarget.height / 2f)
    }

    fun getOpenAppTargetCenter(): Pair<Float, Float> {
        val loc = IntArray(2)
        openTarget.getLocationOnScreen(loc)
        return Pair(loc[0] + openTarget.width / 2f, loc[1] + openTarget.height / 2f)
    }

    fun highlightCloseTarget(on: Boolean) {
        val scale = if (on) 1.2f else 1.0f
        closeTarget.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }

    fun highlightOpenTarget(on: Boolean) {
        val scale = if (on) 1.2f else 1.0f
        openTarget.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }

    private fun buildTooltip(context: Context): LinearLayout {
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
            setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14.dp().toFloat()
                setColor(Color.argb(50, 255, 255, 255))
                setStroke(1, Color.argb(65, 255, 255, 255))
            }
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                bottomMargin = 4.dp()
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
        val bubbleSize = 72.dp()

        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(bubbleSize + 8.dp(), bubbleSize + 8.dp())
        }

        bubbleCircle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1B5E20"), Color.parseColor("#0D1B4B"))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(3.dp(), Color.parseColor("#FFD700"))
            }
            elevation = 8.dp().toFloat()
            contentDescription = "اضغط للصلاة على النبي"
        }

        val label = TextView(context).apply {
            text = "صلوات"
            textDirection = TEXT_DIRECTION_RTL
            setTextColor(Color.argb(180, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = 16.dp()
            }
        }
        bubbleCircle.addView(label)

        countText = TextView(context).apply {
            text = "0"
            setTextColor(Color.parseColor("#FFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, 4.dp(), 0, 0)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = 6.dp()
            }
        }
        bubbleCircle.addView(countText)

        container.addView(bubbleCircle)
        return container
    }

    private fun buildActionRow(context: Context): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
        }

        closeTarget = buildTarget(context, "✕", Color.parseColor("#B71C1C"))
        openTarget = buildTarget(context, "↗", Color.parseColor("#FFD700"))

        row.addView(closeTarget)
        val spacer = FrameLayout(context).apply {
            layoutParams = LayoutParams(16.dp(), 1)
        }
        row.addView(spacer)
        row.addView(openTarget)

        return row
    }

    private fun buildTarget(context: Context, icon: String, bgColor: Int): TextView {
        val size = 48.dp()
        return TextView(context).apply {
            text = icon
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            elevation = 6.dp().toFloat()
            layoutParams = LayoutParams(size, size)
        }
    }
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileDebugKotlinAndroid 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL` with no errors in `FloatingBubbleView.kt`

- [ ] **Step 3: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleView.kt
git commit -m "feat(bubble): redesign view — gold-ring gradient, action targets, tap animation"
```

---

### Task 2: Rewrite gesture logic in `FloatingBubbleService.kt`

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleService.kt`

**Interfaces:**
- Consumes from Task 1:
  - `bubbleView.animateTap()`
  - `bubbleView.showActionTargets()` / `hideActionTargets()`
  - `bubbleView.getCloseTargetCenter()` / `getOpenAppTargetCenter()`
  - `bubbleView.highlightCloseTarget(Boolean)` / `highlightOpenTarget(Boolean)`
  - `bubbleView.onOpenApp`
- Removes: `isCloseButtonHit()` call (deleted from view)

- [ ] **Step 1: Replace `setupBubble` and `setupDrag` in `FloatingBubbleService.kt`**

Replace the existing `setupBubble()`, `setupDrag()`, and `handleTap()` methods, and update imports. The full updated service file:

```kotlin
package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
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

class FloatingBubbleService : Service() {

    companion object {
        const val EXTRA_ROUND_KEY = "round_key"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        private const val DRAG_THRESHOLD_PX = 10
        private const val TAP_THRESHOLD = 10
        private const val TAP_DURATION_MS = 300L
        private const val LONG_PRESS_MS = 400L
        private const val TARGET_HIT_RADIUS_DP = 40
    }

    private val sessionStore: MohamedLoversSessionStore by inject()
    private val dailyGoalStore: DailyGoalStore by inject()
    private val analyticsManager: tools.mo3ta.salo.analytics.AnalyticsManager by inject()

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: FloatingBubbleView
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var roundKey: String = ""
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var targetsVisible = false
    private val longPressRunnable = Runnable {
        targetsVisible = true
        bubbleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        bubbleView.showActionTargets()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == pendingCountKey() && ::bubbleView.isInitialized) {
            mainHandler.post { bubbleView.updateCount(currentPendingCount()) }
        }
    }

    private fun pendingCountKey() = "pending_count_$roundKey"
    private fun currentPendingCount() = prefs.getInt(pendingCountKey(), 0)

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("ml_session", Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::bubbleView.isInitialized) {
            startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification())
        }
        intent?.getStringExtra(EXTRA_ROUND_KEY)?.let { roundKey = it }
        if (roundKey.isBlank()) { stopSelf(); return START_NOT_STICKY }
        if (!::bubbleView.isInitialized) {
            setupBubble()
            bubbleView.updateCount(currentPendingCount())
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            startReminderCycle()
        }
        return START_NOT_STICKY
    }

    private fun screenSize(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    private fun clampParams() {
        val (screenW, screenH) = screenSize()
        val margin = 32.dp()
        // params.x is distance from RIGHT edge (gravity END), params.y from TOP
        params.x = params.x.coerceIn(margin, screenW - margin - bubbleView.width)
        params.y = params.y.coerceIn(margin, screenH - margin - bubbleView.height)
    }

    private fun setupBubble() {
        bubbleView = FloatingBubbleView(this)
        bubbleView.onTap = { handleTap() }
        bubbleView.onClose = { stopSelf() }
        bubbleView.onOpenApp = { launchApp() }

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
        var touchDownTime = 0L
        var isDragging = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    touchDownTime = SystemClock.elapsedRealtime()
                    isDragging = false
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (distX > DRAG_THRESHOLD_PX || distY > DRAG_THRESHOLD_PX) {
                        if (!isDragging) {
                            isDragging = true
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)

                        if (targetsVisible) {
                            val bubbleCx = event.rawX
                            val bubbleCy = event.rawY
                            val hitRadius = TARGET_HIT_RADIUS_DP.dp().toFloat()
                            val (closeCx, closeCy) = bubbleView.getCloseTargetCenter()
                            val (openCx, openCy) = bubbleView.getOpenAppTargetCenter()
                            val nearClose = dist(bubbleCx, bubbleCy, closeCx, closeCy) < hitRadius
                            val nearOpen = dist(bubbleCx, bubbleCy, openCx, openCy) < hitRadius
                            bubbleView.highlightCloseTarget(nearClose)
                            bubbleView.highlightOpenTarget(nearOpen)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    val duration = SystemClock.elapsedRealtime() - touchDownTime

                    if (targetsVisible) {
                        val bubbleCx = event.rawX
                        val bubbleCy = event.rawY
                        val hitRadius = TARGET_HIT_RADIUS_DP.dp().toFloat()
                        val (closeCx, closeCy) = bubbleView.getCloseTargetCenter()
                        val (openCx, openCy) = bubbleView.getOpenAppTargetCenter()
                        when {
                            dist(bubbleCx, bubbleCy, closeCx, closeCy) < hitRadius -> stopSelf()
                            dist(bubbleCx, bubbleCy, openCx, openCy) < hitRadius -> launchApp()
                            else -> {
                                targetsVisible = false
                                bubbleView.hideActionTargets()
                                clampParams()
                                windowManager.updateViewLayout(bubbleView, params)
                            }
                        }
                    } else if (distX < TAP_THRESHOLD && distY < TAP_THRESHOLD && duration < TAP_DURATION_MS) {
                        handleTap()
                    } else {
                        clampParams()
                        windowManager.updateViewLayout(bubbleView, params)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun handleTap() {
        if (roundKey.isBlank()) return
        val pending = sessionStore.incrementPendingClick(roundKey, 1)
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        dailyGoalStore.recordTap(today, 1)
        bubbleView.updateCount(pending.clickCount)
        bubbleView.animateTap()
        analyticsManager.logAction("bubble_tap", mapOf("count" to pending.clickCount.toString()))
    }

    private fun launchApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        intent?.let { startActivity(it) }
        analyticsManager.logAction("bubble_open_app", emptyMap())
    }

    private fun startReminderCycle() {
        scope.launch {
            bubbleView.showTooltip()
            delay(5_000L)
            bubbleView.hideTooltip()
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
        mainHandler.removeCallbacks(longPressRunnable)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scope.cancel()
        if (::bubbleView.isInitialized) {
            runCatching { windowManager.removeView(bubbleView) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileDebugKotlinAndroid 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Build and install**

```bash
./gradlew installDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, APK installed on connected device/emulator.

- [ ] **Step 4: Manual verification checklist**

Test on a device with overlay permission granted:

1. Launch bubble from Mohamed Lovers screen
2. **Visual:** Bubble is green-to-navy circle with gold ring and gold count number — no red ✕ button visible
3. **Tap:** Each tap increments count AND the circle briefly scales up then back (pulse)
4. **Long press (hold ~0.5s without moving):** Two circular targets appear below — dark red ✕ on left, gold ↗ on right; haptic fires
5. **Drag into close target:** Service stops, bubble disappears
6. **Repeat, drag into open-app target:** App opens / comes to foreground
7. **Drag away from targets then release:** Targets hide, bubble snaps to within 32 dp of screen edge
8. **Drag bubble to corner:** Bubble stops ~32 dp from any edge, never goes off-screen

- [ ] **Step 5: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/ui/FloatingBubbleService.kt
git commit -m "feat(bubble): long-press action targets, edge clamping, open-app action"
```
