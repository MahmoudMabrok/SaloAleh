package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.SystemClock
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

class FloatingBubbleService : Service() {

    companion object {
        const val EXTRA_ROUND_KEY = "round_key"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        private const val DRAG_THRESHOLD_PX = 10
        private const val TAP_THRESHOLD = 10
        private const val TAP_DURATION_MS = 300L
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
        if (!::bubbleView.isInitialized) {
            startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification())
        }
        intent?.getStringExtra(EXTRA_ROUND_KEY)?.let { roundKey = it }
        if (roundKey.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::bubbleView.isInitialized) {
            sessionCount = sessionStore.getPendingSession(roundKey).clickCount
            setupBubble()
            bubbleView.updateCount(sessionCount)
            startReminderCycle()
        }
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
        var touchDownTime = 0L

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    touchDownTime = SystemClock.elapsedRealtime()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (distX > DRAG_THRESHOLD_PX || distY > DRAG_THRESHOLD_PX) {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    val duration = SystemClock.elapsedRealtime() - touchDownTime
                    if (distX < TAP_THRESHOLD && distY < TAP_THRESHOLD && duration < TAP_DURATION_MS) {
                        if (bubbleView.isCloseButtonHit(event.rawX, event.rawY)) {
                            stopSelf()
                        } else {
                            handleTap()
                        }
                    }
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
