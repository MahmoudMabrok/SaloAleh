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
                            dist(bubbleCx, bubbleCy, openCx, openCy) < hitRadius -> {
                                targetsVisible = false
                                bubbleView.hideActionTargets()
                                launchApp()
                            }
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
