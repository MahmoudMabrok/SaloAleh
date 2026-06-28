package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
        private const val TARGET_HIT_RADIUS_DP = 44
    }

    private val sessionStore: MohamedLoversSessionStore by inject()
    private val dailyGoalStore: DailyGoalStore by inject()
    private val analyticsManager: tools.mo3ta.salo.analytics.AnalyticsManager by inject()

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: FloatingBubbleView
    private lateinit var bubbleParams: WindowManager.LayoutParams

    // Separate overlay for action targets — fixed at top of screen, not a child of bubble
    private var actionOverlayView: LinearLayout? = null
    private var closeTargetView: TextView? = null
    private var openTargetView: TextView? = null

    // Separate overlay for tooltip — FLAG_NOT_TOUCHABLE, positioned above bubble
    private var tooltipView: LinearLayout? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var roundKey: String = ""
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var targetsVisible = false
    private val longPressRunnable = Runnable {
        targetsVisible = true
        bubbleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showActionOverlay()
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

    // ── WindowManager helpers ─────────────────────────────────────────────────

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun screenSize(): Pair<Int, Int> {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }

    private fun clampBubble() {
        val (screenW, screenH) = screenSize()
        val margin = 32.dp()
        // bubbleParams.x = distance from RIGHT edge (Gravity.END); bubbleParams.y from TOP
        val bw = bubbleView.width.let { if (it > 0) it else 72.dp() }
        val bh = bubbleView.height.let { if (it > 0) it else 72.dp() }
        bubbleParams.x = bubbleParams.x.coerceIn(margin, screenW - margin - bw)
        bubbleParams.y = bubbleParams.y.coerceIn(margin, screenH - margin - bh)
    }

    // ── Bubble setup ──────────────────────────────────────────────────────────

    private fun setupBubble() {
        bubbleView = FloatingBubbleView(this)

        val bubbleSize = 72.dp()
        bubbleParams = overlayParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 200
        }

        setupDrag()
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun setupDrag() {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var touchDownTime = 0L
        var isDragging = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x; initialY = bubbleParams.y
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
                        bubbleParams.x = initialX - (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)

                        if (targetsVisible) {
                            checkTargetHighlight(event.rawX, event.rawY)
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
                        handleTargetRelease(event.rawX, event.rawY)
                    } else if (distX < TAP_THRESHOLD && distY < TAP_THRESHOLD && duration < TAP_DURATION_MS) {
                        handleTap()
                    } else {
                        clampBubble()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    isDragging = false
                    if (targetsVisible) dismissActionOverlay()
                    true
                }
                else -> false
            }
        }
    }

    // ── Action overlay (separate WM view, top of screen) ─────────────────────

    private fun showActionOverlay() {
        if (actionOverlayView != null) return
        val (screenW, _) = screenSize()
        val targetSize = 56.dp()
        val gap = 32.dp()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        closeTargetView = buildTarget("✕", Color.parseColor("#B71C1C"), targetSize)
        openTargetView = buildTarget("↗", Color.parseColor("#FFD700"), targetSize)

        row.addView(buildTargetWithLabel(closeTargetView!!, "إغلاق"))
        row.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(gap, 1)
        })
        row.addView(buildTargetWithLabel(openTargetView!!, "فتح"))

        row.alpha = 0f
        row.translationY = (-16).dp().toFloat()

        val params = overlayParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 48.dp()
        }

        windowManager.addView(row, params)
        actionOverlayView = row

        row.animate().alpha(1f).translationY(0f).setDuration(250).start()
    }

    private fun dismissActionOverlay() {
        targetsVisible = false
        val overlay = actionOverlayView ?: return
        overlay.animate().alpha(0f).translationY((-16).dp().toFloat()).setDuration(200)
            .withEndAction {
                runCatching { windowManager.removeView(overlay) }
                actionOverlayView = null
                closeTargetView = null
                openTargetView = null
            }.start()
    }

    private fun buildTarget(icon: String, bgColor: Int, size: Int): TextView =
        TextView(this).apply {
            text = icon
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            elevation = 8.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

    private fun buildTargetWithLabel(target: TextView, labelText: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            addView(target)
            addView(TextView(this@FloatingBubbleService).apply {
                text = labelText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    topMargin = 4.dp()
                }
            })
        }

    private fun targetCenter(view: TextView): Pair<Float, Float> {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return Pair(loc[0] + view.width / 2f, loc[1] + view.height / 2f)
    }

    private fun checkTargetHighlight(rawX: Float, rawY: Float) {
        val hitRadius = TARGET_HIT_RADIUS_DP.dp().toFloat()
        closeTargetView?.let { cv ->
            val (cx, cy) = targetCenter(cv)
            val scale = if (dist(rawX, rawY, cx, cy) < hitRadius) 1.2f else 1f
            cv.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
        }
        openTargetView?.let { ov ->
            val (ox, oy) = targetCenter(ov)
            val scale = if (dist(rawX, rawY, ox, oy) < hitRadius) 1.2f else 1f
            ov.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
        }
    }

    private fun handleTargetRelease(rawX: Float, rawY: Float) {
        val hitRadius = TARGET_HIT_RADIUS_DP.dp().toFloat()
        val hitClose = closeTargetView?.let { dist(rawX, rawY, targetCenter(it).first, targetCenter(it).second) < hitRadius } == true
        val hitOpen = openTargetView?.let { dist(rawX, rawY, targetCenter(it).first, targetCenter(it).second) < hitRadius } == true
        when {
            hitClose -> { dismissActionOverlay(); stopSelf() }
            hitOpen -> { dismissActionOverlay(); launchApp() }
            else -> { dismissActionOverlay(); clampBubble(); windowManager.updateViewLayout(bubbleView, bubbleParams) }
        }
    }

    // ── Tooltip (separate WM view, FLAG_NOT_TOUCHABLE) ────────────────────────

    private fun showTooltip() {
        if (tooltipView != null) return
        val card = TextView(this).apply {
            text = "اللهم صل علي محمد وال محمد"
            textDirection = android.view.View.TEXT_DIRECTION_RTL
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
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        row.addView(card)
        row.addView(TextView(this).apply {
            text = "▼"
            setTextColor(Color.argb(65, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        })
        row.alpha = 0f

        val p = overlayParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = bubbleParams.x
            y = (bubbleParams.y - 80.dp()).coerceAtLeast(8.dp())
        }

        windowManager.addView(row, p)
        tooltipView = row
        row.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideTooltip() {
        val tv = tooltipView ?: return
        tv.animate().alpha(0f).setDuration(300)
            .withEndAction {
                runCatching { windowManager.removeView(tv) }
                tooltipView = null
            }.start()
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

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
            showTooltip()
            delay(5_000L)
            hideTooltip()
            while (true) {
                delay(10 * 60 * 1000L)
                showTooltip()
                delay(5_000L)
                hideTooltip()
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
        runCatching { if (::bubbleView.isInitialized) windowManager.removeView(bubbleView) }
        actionOverlayView?.let { runCatching { windowManager.removeView(it) } }
        tooltipView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
